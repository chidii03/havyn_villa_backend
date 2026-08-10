package com.havyn.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import com.havyn.properties.domain.PropertyType;
import com.havyn.properties.repo.PropertyRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class MediaServiceTest {

    private final PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);
    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final MediaStorage mediaStorage = mock(MediaStorage.class);
    private final MediaProperties properties = new MediaProperties();
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final MediaService service = new MediaService(propertyMediaRepository, propertyRepository, mediaStorage, properties, eventPublisher);

    private final UUID hostId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private Property property;

    @BeforeEach
    void setUp() {
        PropertyType villa = mock(PropertyType.class);
        property = new Property(
                hostId, villa, "Sunset Villa", "Description", "1 Beach Rd", "Lagos", "Lagos", "Nigeria",
                BigDecimal.valueOf(10000), 4, 2, 2, BigDecimal.valueOf(2));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(mediaStorage.isValidAssetUrl(any())).thenReturn(true);
    }

    @Test
    void generateUploadSignature_rejectsWhenCallerIsNotTheOwningHost() {
        assertThatThrownBy(() -> service.generateUploadSignature(UUID.randomUUID(), propertyId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void generateUploadSignature_rejectsAtTheConfiguredLimit() {
        properties.setMaxPerListing(5);
        when(propertyMediaRepository.countByPropertyId(propertyId)).thenReturn(5L);

        assertThatThrownBy(() -> service.generateUploadSignature(hostId, propertyId))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("MEDIA_LIMIT_REACHED");
    }

    @Test
    void generateUploadSignature_returnsWhateverTheStorageAdapterProduces() {
        when(propertyMediaRepository.countByPropertyId(propertyId)).thenReturn(0L);
        String folder = "hosts/" + hostId + "/properties/" + propertyId;
        SignedUpload upload = new SignedUpload("havyn", "key", 123L, "sig", folder);
        when(mediaStorage.createSignedUpload(folder)).thenReturn(upload);

        assertThat(service.generateUploadSignature(hostId, propertyId)).isSameAs(upload);
    }

    @Test
    void addMedia_rejectsAnUnsupportedFormat() {
        AddMediaRequest request = mediaRequest(MediaResourceType.IMAGE, "bmp", 1000L);

        assertThatThrownBy(() -> service.addMedia(hostId, propertyId, request))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("UNSUPPORTED_MEDIA_FORMAT");
    }

    @Test
    void addMedia_rejectsAnOversizedImageAndDeletesTheAlreadyUploadedAsset() {
        properties.setMaxImageBytes(1000);
        AddMediaRequest request = mediaRequest(MediaResourceType.IMAGE, "jpg", 5000L);

        assertThatThrownBy(() -> service.addMedia(hostId, propertyId, request))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("MEDIA_TOO_LARGE");
        verify(mediaStorage).deleteAsset(request.publicId(), MediaResourceType.IMAGE);
    }

    @Test
    void addMedia_rejectsAssetsNotFromTheConfiguredStorage() {
        when(mediaStorage.isValidAssetUrl(any())).thenReturn(false);
        AddMediaRequest request = mediaRequest(MediaResourceType.IMAGE, "jpg", 1000L);

        assertThatThrownBy(() -> service.addMedia(hostId, propertyId, request))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("INVALID_MEDIA_SOURCE");
    }

    @Test
    void addMedia_savesAtTheNextAvailablePosition() {
        when(propertyMediaRepository.countByPropertyId(propertyId)).thenReturn(3L);
        when(propertyMediaRepository.save(any(PropertyMedia.class))).thenAnswer(inv -> inv.getArgument(0));
        AddMediaRequest request = mediaRequest(MediaResourceType.IMAGE, "jpg", 1000L);

        PropertyMedia saved = service.addMedia(hostId, propertyId, request);

        assertThat(saved.getPosition()).isEqualTo(3);
        assertThat(saved.getPropertyId()).isEqualTo(propertyId);
    }

    @Test
    void reorder_rejectsAMismatchedIdSet() {
        UUID existingId = UUID.randomUUID();
        PropertyMedia existing = mock(PropertyMedia.class);
        when(existing.getId()).thenReturn(existingId);
        when(propertyMediaRepository.findAllByPropertyIdOrderByPositionAsc(propertyId)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.reorder(hostId, propertyId, List.of(UUID.randomUUID())))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("INVALID_MEDIA_ORDER");
    }

    @Test
    void reorder_appliesTheRequestedPositions() {
        // Real `new PropertyMedia(...)` instances never get a Hibernate-generated id
        // in a pure unit test (same trap documented in PropertyServiceTest/
        // BookingServiceTest) — mocked here specifically to give each a real,
        // distinct id so the position-by-id logic under test has something to match.
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        PropertyMedia first = mock(PropertyMedia.class);
        when(first.getId()).thenReturn(firstId);
        PropertyMedia second = mock(PropertyMedia.class);
        when(second.getId()).thenReturn(secondId);
        when(propertyMediaRepository.findAllByPropertyIdOrderByPositionAsc(propertyId))
                .thenReturn(List.of(first, second))
                .thenReturn(List.of(second, first)); // re-fetch after reorder reflects new order

        List<PropertyMedia> result = service.reorder(hostId, propertyId, List.of(secondId, firstId));

        verify(second).setPosition(0);
        verify(first).setPosition(1);
        assertThat(result).containsExactly(second, first);
    }

    @Test
    void delete_rejectsWhenCallerIsNotTheOwningHost() {
        assertThatThrownBy(() -> service.delete(UUID.randomUUID(), propertyId, UUID.randomUUID())).isInstanceOf(ForbiddenException.class);
        verify(mediaStorage, never()).deleteAsset(any(), any());
    }

    @Test
    void delete_deletesFromStorageAndTheRepository() {
        PropertyMedia media = realMedia(0);
        when(propertyMediaRepository.findByIdAndPropertyId(eq(media.getId()), eq(propertyId))).thenReturn(Optional.of(media));

        service.delete(hostId, propertyId, media.getId());

        verify(mediaStorage).deleteAsset(media.getPublicId(), media.getResourceType());
        verify(propertyMediaRepository).delete(media);
    }

    @Test
    void listPublic_throwsNotFoundForANonActiveProperty_noInfoLeak() {
        // property defaults to DRAFT
        assertThatThrownBy(() -> service.listPublic(propertyId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listPublic_returnsMediaForAnActiveProperty() {
        property.transitionTo(PropertyStatus.PENDING);
        property.transitionTo(PropertyStatus.ACTIVE);
        PropertyMedia media = realMedia(0);
        when(propertyMediaRepository.findAllByPropertyIdOrderByPositionAsc(propertyId)).thenReturn(List.of(media));

        assertThat(service.listPublic(propertyId)).containsExactly(media);
    }

    private AddMediaRequest mediaRequest(MediaResourceType type, String format, long bytes) {
        return new AddMediaRequest("public-id-" + UUID.randomUUID(), "https://res.cloudinary.com/havyn/x/upload/v1/y." + format,
                type, format, 800, 600, null, bytes, "A lovely view");
    }

    private PropertyMedia realMedia(int position) {
        return new PropertyMedia(
                propertyId, "https://res.cloudinary.com/havyn/image/upload/v1/x.jpg", "public-" + UUID.randomUUID(),
                MediaResourceType.IMAGE, "jpg", 800, 600, null, 1000L, position, null);
    }
}
