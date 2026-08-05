package com.havyn.properties.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.havyn.amenities.domain.Amenity;
import com.havyn.amenities.repo.AmenityRepository;
import com.havyn.booking.domain.BookingStatus;
import com.havyn.booking.repo.BookingRepository;
import com.havyn.common.error.BadRequestException;
import com.havyn.common.error.ForbiddenException;
import com.havyn.common.error.NotFoundException;
import com.havyn.properties.domain.Availability;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyStatus;
import com.havyn.properties.domain.PropertyType;
import com.havyn.media.repo.PropertyMediaRepository;
import com.havyn.properties.cache.PropertyCacheService;
import com.havyn.properties.domain.event.PropertyChangedEvent;
import com.havyn.properties.repo.AvailabilityRepository;
import com.havyn.properties.repo.PropertyRepository;
import com.havyn.properties.repo.PropertyTypeRepository;
import com.havyn.properties.web.AvailabilityDayInput;
import com.havyn.properties.web.CreatePropertyRequest;
import com.havyn.properties.web.SetAvailabilityRequest;
import com.havyn.properties.web.UpdatePropertyRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class PropertyServiceTest {

    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final PropertyTypeRepository propertyTypeRepository = mock(PropertyTypeRepository.class);
    private final AmenityRepository amenityRepository = mock(AmenityRepository.class);
    private final AvailabilityRepository availabilityRepository = mock(AvailabilityRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final PropertyCacheService propertyCacheService = mock(PropertyCacheService.class);
    private final PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);

    private final PropertyService service = new PropertyService(
            propertyRepository, propertyTypeRepository, amenityRepository, availabilityRepository, bookingRepository, eventPublisher,
            propertyCacheService, propertyMediaRepository);

    private final UUID hostId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private PropertyType villa;

    @BeforeEach
    void setUp() {
        villa = mock(PropertyType.class);
        when(villa.getCode()).thenReturn("VILLA");
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_resolvesTypeAndAmenitiesThenSavesAndPublishesEvent() {
        when(propertyTypeRepository.findByCodeIgnoreCase("VILLA")).thenReturn(Optional.of(villa));
        Amenity wifi = mock(Amenity.class);
        when(amenityRepository.findAllByCodeInIgnoreCase(Set.of("WIFI"))).thenReturn(List.of(wifi));

        CreatePropertyRequest request = new CreatePropertyRequest(
                "VILLA", "Sunset Villa", "A lovely villa", "1 Beach Rd", "Lagos", "Lagos", "Nigeria",
                null, null, null, BigDecimal.valueOf(50000), 4, 2, 2, BigDecimal.valueOf(2), null, null, null, null,
                Set.of("WIFI"));

        Property created = service.create(hostId, request);

        assertThat(created.getHostId()).isEqualTo(hostId);
        assertThat(created.getTitle()).isEqualTo("Sunset Villa");
        assertThat(created.getStatus()).isEqualTo(PropertyStatus.DRAFT);
        assertThat(created.getAmenities()).containsExactly(wifi);
        verify(eventPublisher).publishEvent(any(PropertyChangedEvent.class));
    }

    @Test
    void create_rejectsUnknownPropertyType() {
        when(propertyTypeRepository.findByCodeIgnoreCase("NOT_A_TYPE")).thenReturn(Optional.empty());
        CreatePropertyRequest request = new CreatePropertyRequest(
                "NOT_A_TYPE", "Title", "Desc", "Addr", "City", "State", "Country",
                null, null, null, BigDecimal.TEN, 2, 1, 1, BigDecimal.ONE, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(hostId, request))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("UNKNOWN_PROPERTY_TYPE");
    }

    @Test
    void create_rejectsUnknownAmenityCode() {
        when(propertyTypeRepository.findByCodeIgnoreCase("VILLA")).thenReturn(Optional.of(villa));
        when(amenityRepository.findAllByCodeInIgnoreCase(Set.of("NOT_REAL"))).thenReturn(List.of());
        CreatePropertyRequest request = new CreatePropertyRequest(
                "VILLA", "Title", "Desc", "Addr", "City", "State", "Country",
                null, null, null, BigDecimal.TEN, 2, 1, 1, BigDecimal.ONE, null, null, null, null, Set.of("NOT_REAL"));

        assertThatThrownBy(() -> service.create(hostId, request))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("UNKNOWN_AMENITY");
    }

    @Test
    void update_throwsForbiddenWhenCallerIsNotTheOwningHost() {
        Property owned = existingProperty(hostId);
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(owned));
        UUID someoneElse = UUID.randomUUID();

        assertThatThrownBy(() -> service.update(someoneElse, propertyId, blankUpdate()))
                .isInstanceOf(ForbiddenException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void update_throwsNotFoundWhenListingDoesNotExist() {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(hostId, propertyId, blankUpdate()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_appliesOnlyProvidedFieldsAndPublishesEvent() {
        Property owned = existingProperty(hostId);
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(owned));

        UpdatePropertyRequest request = new UpdatePropertyRequest(
                null, // typeCode
                "New Title", // title
                null, // description
                null, // address
                null, // city
                null, // state
                null, // country
                null, // lat
                null, // lng
                null, // currency
                null, // basePrice
                null, // capacity
                null, // bedrooms
                null, // beds
                null, // bathrooms
                null, // cleaningFee
                null, // serviceFeePct
                null, // houseRules
                null, // cancellationPolicy
                null); // amenityCodes
        Property updated = service.update(hostId, propertyId, request);

        assertThat(updated.getTitle()).isEqualTo("New Title");
        assertThat(updated.getCity()).isEqualTo("Lagos"); // unchanged
        verify(eventPublisher).publishEvent(any(PropertyChangedEvent.class));
    }

    @Test
    void transition_appliesValidTransition() {
        Property owned = existingProperty(hostId); // starts DRAFT
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(owned));

        Property result = service.transition(hostId, propertyId, PropertyStatus.PENDING);

        assertThat(result.getStatus()).isEqualTo(PropertyStatus.PENDING);
        verify(eventPublisher).publishEvent(any(PropertyChangedEvent.class));
    }

    @Test
    void transition_rejectsAnInvalidTransition() {
        Property owned = existingProperty(hostId); // DRAFT
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> service.transition(hostId, propertyId, PropertyStatus.ACTIVE))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("INVALID_STATUS_TRANSITION");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void transition_rejectsWhenCallerDoesNotOwnTheListing() {
        Property owned = existingProperty(hostId);
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> service.transition(UUID.randomUUID(), propertyId, PropertyStatus.PENDING))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getActive_returnsAnActiveListing() {
        Property active = existingProperty(hostId);
        active.transitionTo(PropertyStatus.PENDING);
        active.transitionTo(PropertyStatus.ACTIVE);
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(active));

        assertThat(service.getActive(propertyId)).isSameAs(active);
    }

    @Test
    void getActive_throwsNotFoundForADraftListing_noInfoLeak() {
        Property draft = existingProperty(hostId);
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.getActive(propertyId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getActive_throwsNotFoundWhenNoSuchListing() {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActive(propertyId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getActiveDetail_onACacheHit_neverTouchesTheRepository() {
        var cached = com.havyn.properties.web.PropertyDetail.from(activeProperty());
        when(propertyCacheService.get(propertyId)).thenReturn(Optional.of(cached));

        // Privileged viewer (the owning host) so the full, unredacted cached object
        // comes back unchanged — proves the cache mechanics, not the redaction rule.
        assertThat(service.getActiveDetail(propertyId, hostId, false)).isSameAs(cached);

        verify(propertyRepository, never()).findById(any());
    }

    @Test
    void getActiveDetail_onACacheMiss_queriesAndThenPopulatesTheCache() {
        Property active = activeProperty();
        when(propertyCacheService.get(propertyId)).thenReturn(Optional.empty());
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(active));

        var detail = service.getActiveDetail(propertyId, hostId, false);

        assertThat(detail.title()).isEqualTo(active.getTitle());
        // put() must receive the full-precision detail, even though the privileged
        // caller here happens to get the same object back — a later anonymous caller
        // hitting this same warm cache entry must never see a pre-redacted copy.
        verify(propertyCacheService).put(eq(propertyId), eq(detail));
    }

    @Test
    void getActiveDetail_stillThrowsNotFoundOnACacheMissForANonActiveListing() {
        when(propertyCacheService.get(propertyId)).thenReturn(Optional.empty());
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(existingProperty(hostId)));

        assertThatThrownBy(() -> service.getActiveDetail(propertyId, null, false)).isInstanceOf(NotFoundException.class);
        verify(propertyCacheService, never()).put(any(), any());
    }

    @Test
    void getActiveDetail_redactsAddressAndRoundsCoordinatesForAnAnonymousViewer() {
        Property active = activeProperty();
        active.setLat(BigDecimal.valueOf(6.123456));
        active.setLng(BigDecimal.valueOf(3.987654));
        when(propertyCacheService.get(propertyId)).thenReturn(Optional.empty());
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(active));

        var detail = service.getActiveDetail(propertyId, null, false);

        assertThat(detail.address()).isNull();
        assertThat(detail.lat()).isEqualByComparingTo("6.12");
        assertThat(detail.lng()).isEqualByComparingTo("3.99");
    }

    @Test
    void getActiveDetail_redactsForAGuestWithOnlyAPendingHold_notYetPaid() {
        Property active = activeProperty();
        UUID guestId = UUID.randomUUID();
        when(propertyCacheService.get(propertyId)).thenReturn(Optional.empty());
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(active));
        when(bookingRepository.existsByGuestIdAndPropertyIdAndStatusIn(
                eq(guestId), any(), eq(Set.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED))))
                .thenReturn(false);

        var detail = service.getActiveDetail(propertyId, guestId, false);

        assertThat(detail.address()).isNull();
    }

    @Test
    void getActiveDetail_showsExactLocationToAGuestWithAConfirmedBooking() {
        Property active = activeProperty();
        UUID guestId = UUID.randomUUID();
        when(propertyCacheService.get(propertyId)).thenReturn(Optional.empty());
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(active));
        when(bookingRepository.existsByGuestIdAndPropertyIdAndStatusIn(
                eq(guestId), any(), eq(Set.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED))))
                .thenReturn(true);

        var detail = service.getActiveDetail(propertyId, guestId, false);

        assertThat(detail.address()).isEqualTo(active.getAddress());
    }

    @Test
    void getActiveDetail_showsExactLocationToAnAdmin_regardlessOfOwnershipOrBooking() {
        Property active = activeProperty();
        when(propertyCacheService.get(propertyId)).thenReturn(Optional.empty());
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(active));

        var detail = service.getActiveDetail(propertyId, UUID.randomUUID(), true);

        assertThat(detail.address()).isEqualTo(active.getAddress());
        verify(bookingRepository, never()).existsByGuestIdAndPropertyIdAndStatusIn(any(), any(), any());
    }

    private Property activeProperty() {
        Property active = existingProperty(hostId);
        active.transitionTo(PropertyStatus.PENDING);
        active.transitionTo(PropertyStatus.ACTIVE);
        return active;
    }

    @Test
    void setAvailability_createsANewDayAndUpdatesAnExistingOne() {
        Property owned = existingProperty(hostId);
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(owned));

        LocalDate newDay = LocalDate.of(2026, 8, 1);
        LocalDate existingDay = LocalDate.of(2026, 8, 2);
        Availability existing = new Availability(owned, existingDay, false, null);

        // `owned` is a plain `new Property(...)` with no real Hibernate behind it, so
        // its BaseEntity#id is null — the service reads property.getId() as null, and
        // any(UUID.class) (unlike any()) does NOT match null, so that stub would
        // silently never fire and mask a real bug behind Mockito's Optional-returning
        // default (empty()). nullable(UUID.class) matches null or any UUID, which is
        // what a real, Hibernate-managed property.getId() would actually be.
        when(availabilityRepository.findByProperty_IdAndDate(nullable(UUID.class), eq(newDay))).thenReturn(Optional.empty());
        when(availabilityRepository.findByProperty_IdAndDate(nullable(UUID.class), eq(existingDay))).thenReturn(Optional.of(existing));
        when(availabilityRepository.save(any(Availability.class))).thenAnswer(inv -> inv.getArgument(0));

        SetAvailabilityRequest request = new SetAvailabilityRequest(List.of(
                new AvailabilityDayInput(newDay, true, BigDecimal.valueOf(75000)),
                new AvailabilityDayInput(existingDay, true, null)));

        List<Availability> result = service.setAvailability(hostId, propertyId, request);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isBlocked()).isTrue();
        assertThat(result.get(0).getPriceOverride()).isEqualByComparingTo("75000");
        assertThat(existing.isBlocked()).isTrue(); // mutated in place, not replaced
        verify(eventPublisher).publishEvent(any(PropertyChangedEvent.class));
    }

    @Test
    void getAvailability_rejectsAnInvertedRange() {
        Property owned = existingProperty(hostId);
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(owned));

        LocalDate from = LocalDate.of(2026, 8, 10);
        LocalDate to = LocalDate.of(2026, 8, 1);

        assertThatThrownBy(() -> service.getAvailability(hostId, propertyId, from, to))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("INVALID_DATE_RANGE");
    }

    private Property existingProperty(UUID owningHostId) {
        return new Property(owningHostId, villa, "Sunset Villa", "Description", "1 Beach Rd", "Lagos", "Lagos",
                "Nigeria", BigDecimal.valueOf(50000), 4, 2, 2, BigDecimal.valueOf(2));
    }

    private UpdatePropertyRequest blankUpdate() {
        return new UpdatePropertyRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
