package com.havyn.media.web;

import com.havyn.auth.domain.AuthenticatedUser;
import com.havyn.media.domain.PropertyMedia;
import com.havyn.media.service.MediaService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Host-scoped media management — see project-docs/prompts/14-media-storage.md. Mirrors HostListingController's ownership-enforcement pattern. */
@RestController
@RequestMapping("/api/v1/host/listings/{propertyId}/media")
@PreAuthorize("hasRole('HOST')")
public class HostMediaController {

    private final MediaService mediaService;

    public HostMediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/signature")
    public MediaSignatureResponse signature(Authentication authentication, @PathVariable UUID propertyId) {
        return MediaSignatureResponse.from(mediaService.generateUploadSignature(hostId(authentication), propertyId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyMediaSummary add(Authentication authentication, @PathVariable UUID propertyId, @Valid @RequestBody AddMediaRequest request) {
        PropertyMedia media = mediaService.addMedia(hostId(authentication), propertyId, request);
        return PropertyMediaSummary.from(media);
    }

    @GetMapping
    public List<PropertyMediaSummary> list(Authentication authentication, @PathVariable UUID propertyId) {
        return mediaService.list(hostId(authentication), propertyId).stream().map(PropertyMediaSummary::from).toList();
    }

    @PutMapping("/order")
    public List<PropertyMediaSummary> reorder(
            Authentication authentication, @PathVariable UUID propertyId, @Valid @RequestBody ReorderMediaRequest request) {
        return mediaService.reorder(hostId(authentication), propertyId, request.orderedMediaIds()).stream()
                .map(PropertyMediaSummary::from)
                .toList();
    }

    @PatchMapping("/{mediaId}")
    public PropertyMediaSummary update(
            Authentication authentication,
            @PathVariable UUID propertyId,
            @PathVariable UUID mediaId,
            @Valid @RequestBody UpdateMediaRequest request) {
        return PropertyMediaSummary.from(mediaService.update(hostId(authentication), propertyId, mediaId, request));
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable UUID propertyId, @PathVariable UUID mediaId) {
        mediaService.delete(hostId(authentication), propertyId, mediaId);
    }

    private UUID hostId(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
