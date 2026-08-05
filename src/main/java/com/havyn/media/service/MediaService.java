package com.havyn.media.service;

import com.havyn.common.error.BadRequestException;
import com.havyn.common.error.ForbiddenException;
import com.havyn.common.error.NotFoundException;
import com.havyn.media.domain.MediaResourceType;
import com.havyn.media.domain.PropertyMedia;
import com.havyn.media.repo.PropertyMediaRepository;
import com.havyn.media.storage.MediaStorage;
import com.havyn.media.storage.SignedUpload;
import com.havyn.media.web.AddMediaRequest;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyStatus;
import com.havyn.properties.repo.PropertyRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signed uploads, metadata persistence, reordering, and deletion — see
 * project-docs/prompts/14-media-storage.md. Object-level authorization (only the
 * owning host) mirrors {@code PropertyService}'s pattern; {@code PropertyRepository}
 * is a cross-module read (same established pattern as booking/search/payments).
 */
@Service
public class MediaService {

    private final PropertyMediaRepository propertyMediaRepository;
    private final PropertyRepository propertyRepository;
    private final MediaStorage mediaStorage;
    private final MediaProperties properties;

    public MediaService(
            PropertyMediaRepository propertyMediaRepository,
            PropertyRepository propertyRepository,
            MediaStorage mediaStorage,
            MediaProperties properties) {
        this.propertyMediaRepository = propertyMediaRepository;
        this.propertyRepository = propertyRepository;
        this.mediaStorage = mediaStorage;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public SignedUpload generateUploadSignature(UUID hostId, UUID propertyId) {
        findOwned(hostId, propertyId);
        if (propertyMediaRepository.countByPropertyId(propertyId) >= properties.getMaxPerListing()) {
            throw mediaLimitReached();
        }
        return mediaStorage.createSignedUpload("properties/" + propertyId);
    }

    @Transactional
    public PropertyMedia addMedia(UUID hostId, UUID propertyId, AddMediaRequest request) {
        findOwned(hostId, propertyId);

        validateFormat(request.resourceType(), request.format());

        long maxBytes = request.resourceType() == MediaResourceType.VIDEO ? properties.getMaxVideoBytes() : properties.getMaxImageBytes();
        if (request.bytes() > maxBytes) {
            mediaStorage.deleteAsset(request.publicId(), request.resourceType());
            throw new BadRequestException("MEDIA_TOO_LARGE", "That file exceeds the " + maxBytes + " byte limit");
        }

        if (!mediaStorage.isValidAssetUrl(request.secureUrl())) {
            throw new BadRequestException("INVALID_MEDIA_SOURCE", "This file wasn't uploaded to the configured storage");
        }

        long currentCount = propertyMediaRepository.countByPropertyId(propertyId);
        if (currentCount >= properties.getMaxPerListing()) {
            mediaStorage.deleteAsset(request.publicId(), request.resourceType());
            throw mediaLimitReached();
        }

        PropertyMedia media = new PropertyMedia(
                propertyId,
                request.secureUrl(),
                request.publicId(),
                request.resourceType(),
                request.format().toLowerCase(),
                request.width(),
                request.height(),
                request.duration(),
                request.bytes(),
                (int) currentCount,
                request.alt());
        return propertyMediaRepository.save(media);
    }

    @Transactional
    public List<PropertyMedia> reorder(UUID hostId, UUID propertyId, List<UUID> orderedMediaIds) {
        findOwned(hostId, propertyId);
        List<PropertyMedia> existing = propertyMediaRepository.findAllByPropertyIdOrderByPositionAsc(propertyId);

        Set<UUID> existingIds = new HashSet<>();
        existing.forEach(media -> existingIds.add(media.getId()));
        Set<UUID> requestedIds = new HashSet<>(orderedMediaIds);
        if (!existingIds.equals(requestedIds) || orderedMediaIds.size() != existing.size()) {
            throw new BadRequestException(
                    "INVALID_MEDIA_ORDER", "orderedMediaIds must list exactly this listing's media, each exactly once");
        }

        for (PropertyMedia media : existing) {
            media.setPosition(orderedMediaIds.indexOf(media.getId()));
        }
        return propertyMediaRepository.findAllByPropertyIdOrderByPositionAsc(propertyId);
    }

    @Transactional
    public void delete(UUID hostId, UUID propertyId, UUID mediaId) {
        findOwned(hostId, propertyId);
        PropertyMedia media = propertyMediaRepository.findByIdAndPropertyId(mediaId, propertyId)
                .orElseThrow(() -> NotFoundException.of("Media", mediaId));
        mediaStorage.deleteAsset(media.getPublicId(), media.getResourceType());
        propertyMediaRepository.delete(media);
    }

    @Transactional(readOnly = true)
    public List<PropertyMedia> list(UUID hostId, UUID propertyId) {
        findOwned(hostId, propertyId);
        return propertyMediaRepository.findAllByPropertyIdOrderByPositionAsc(propertyId);
    }

    /** Public read — only for listings the public can already see (matches PropertyController's ACTIVE-only 404 story, no info leak on draft/suspended). */
    @Transactional(readOnly = true)
    public List<PropertyMedia> listPublic(UUID propertyId) {
        Property property = propertyRepository.findById(propertyId).orElseThrow(() -> NotFoundException.of("Property", propertyId));
        if (property.getStatus() != PropertyStatus.ACTIVE) {
            throw NotFoundException.of("Property", propertyId);
        }
        return propertyMediaRepository.findAllByPropertyIdOrderByPositionAsc(propertyId);
    }

    private Property findOwned(UUID hostId, UUID propertyId) {
        Property property = propertyRepository.findById(propertyId).orElseThrow(() -> NotFoundException.of("Property", propertyId));
        if (!property.getHostId().equals(hostId)) {
            throw new ForbiddenException("You do not have access to this listing's media");
        }
        return property;
    }

    private void validateFormat(MediaResourceType resourceType, String format) {
        String normalized = format.toLowerCase();
        List<String> allowed = resourceType == MediaResourceType.VIDEO
                ? properties.getAllowedVideoFormats()
                : properties.getAllowedImageFormats();
        if (!allowed.contains(normalized)) {
            throw new BadRequestException(
                    "UNSUPPORTED_MEDIA_FORMAT", "\"" + format + "\" isn't an allowed " + resourceType.name().toLowerCase() + " format");
        }
    }

    private BadRequestException mediaLimitReached() {
        return new BadRequestException(
                "MEDIA_LIMIT_REACHED", "This listing already has the maximum of " + properties.getMaxPerListing() + " photos/videos");
    }
}
