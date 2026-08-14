package com.havyn.properties.service;

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
import com.havyn.media.domain.PropertyMedia;
import com.havyn.media.repo.PropertyMediaRepository;
import com.havyn.properties.cache.PropertyCacheService;
import com.havyn.properties.domain.event.PropertyChangedEvent;
import com.havyn.properties.repo.AvailabilityRepository;
import com.havyn.properties.repo.PropertyRepository;
import com.havyn.properties.repo.PropertyTypeRepository;
import com.havyn.properties.web.AvailabilityDayInput;
import com.havyn.properties.web.CreatePropertyRequest;
import com.havyn.properties.web.PropertyDetail;
import com.havyn.properties.web.SetAvailabilityRequest;
import com.havyn.properties.web.UpdatePropertyRequest;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listing CRUD, status lifecycle, and availability — see
 * project-docs/backend/02-domain-modules.md#properties--media--amenities. Object-level
 * authorization (a host may only touch their own listings) is enforced here, not just
 * at the controller, per {@link ForbiddenException}'s documented purpose.
 */
@Service
public class PropertyService {

    /** Who gets the exact address/coordinates on a detail read — see {@link #getActiveDetail}. */
    private static final Set<BookingStatus> LOCATION_REVEAL_STATUSES = Set.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED);

    private final PropertyRepository propertyRepository;
    private final PropertyTypeRepository propertyTypeRepository;
    private final AmenityRepository amenityRepository;
    private final AvailabilityRepository availabilityRepository;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PropertyCacheService propertyCacheService;
    // Cross-module read (media/ owns PropertyMedia) — same established pattern as
    // NotificationService/SearchService reading other modules' repositories directly.
    private final PropertyMediaRepository propertyMediaRepository;

    public PropertyService(
            PropertyRepository propertyRepository,
            PropertyTypeRepository propertyTypeRepository,
            AmenityRepository amenityRepository,
            AvailabilityRepository availabilityRepository,
            BookingRepository bookingRepository,
            ApplicationEventPublisher eventPublisher,
            PropertyCacheService propertyCacheService,
            PropertyMediaRepository propertyMediaRepository) {
        this.propertyRepository = propertyRepository;
        this.propertyTypeRepository = propertyTypeRepository;
        this.amenityRepository = amenityRepository;
        this.availabilityRepository = availabilityRepository;
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
        this.propertyCacheService = propertyCacheService;
        this.propertyMediaRepository = propertyMediaRepository;
    }

    @Transactional
    public Property create(UUID hostId, CreatePropertyRequest request) {
        PropertyType type = findType(request.typeCode());
        Property property = new Property(
                hostId,
                type,
                request.title().trim(),
                request.description().trim(),
                request.address().trim(),
                request.city().trim(),
                request.state().trim(),
                request.country().trim(),
                request.basePrice(),
                request.capacity(),
                request.bedrooms(),
                request.beds(),
                request.bathrooms());
        property.setLat(request.lat());
        property.setLng(request.lng());
        if (request.currency() != null) {
            property.setCurrency(request.currency().toUpperCase());
        }
        if (request.cleaningFee() != null) {
            property.setCleaningFee(request.cleaningFee());
        }
        if (request.serviceFeePct() != null) {
            property.setServiceFeePct(request.serviceFeePct());
        }
        property.setHouseRules(request.houseRules());
        if (request.cancellationPolicy() != null) {
            property.setCancellationPolicy(request.cancellationPolicy());
        }
        property.setAmenities(resolveAmenities(request.amenityCodes()));

        Property saved = propertyRepository.save(property);
        publishChanged(saved.getId());
        return saved;
    }

    @Transactional
    public Property update(UUID hostId, UUID propertyId, UpdatePropertyRequest request) {
        Property property = findOwned(hostId, propertyId);

        if (request.typeCode() != null) {
            property.setType(findType(request.typeCode()));
        }
        if (request.title() != null) {
            property.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            property.setDescription(request.description().trim());
        }
        if (request.address() != null) {
            property.setAddress(request.address().trim());
        }
        if (request.city() != null) {
            property.setCity(request.city().trim());
        }
        if (request.state() != null) {
            property.setState(request.state().trim());
        }
        if (request.country() != null) {
            property.setCountry(request.country().trim());
        }
        if (request.lat() != null) {
            property.setLat(request.lat());
        }
        if (request.lng() != null) {
            property.setLng(request.lng());
        }
        if (request.currency() != null) {
            property.setCurrency(request.currency().toUpperCase());
        }
        if (request.basePrice() != null) {
            property.setBasePrice(request.basePrice());
        }
        if (request.capacity() != null) {
            property.setCapacity(request.capacity());
        }
        if (request.bedrooms() != null) {
            property.setBedrooms(request.bedrooms());
        }
        if (request.beds() != null) {
            property.setBeds(request.beds());
        }
        if (request.bathrooms() != null) {
            property.setBathrooms(request.bathrooms());
        }
        if (request.cleaningFee() != null) {
            property.setCleaningFee(request.cleaningFee());
        }
        if (request.serviceFeePct() != null) {
            property.setServiceFeePct(request.serviceFeePct());
        }
        if (request.houseRules() != null) {
            property.setHouseRules(request.houseRules());
        }
        if (request.cancellationPolicy() != null) {
            property.setCancellationPolicy(request.cancellationPolicy());
        }
        if (request.amenityCodes() != null) {
            property.setAmenities(resolveAmenities(request.amenityCodes()));
        }

        publishChanged(property.getId());
        return property;
    }

    @Transactional
    public void deleteDraft(UUID hostId, UUID propertyId) {
        Property property = findOwned(hostId, propertyId);
        if (property.getStatus() != PropertyStatus.DRAFT) {
            throw new BadRequestException("LISTING_NOT_DRAFT", "Only draft listings can be deleted");
        }
        propertyRepository.delete(property);
        publishChanged(propertyId);
    }

    @Transactional
    public Property transition(UUID hostId, UUID propertyId, PropertyStatus target) {
        Property property = findOwned(hostId, propertyId);
        try {
            property.transitionTo(target);
        } catch (IllegalStateException invalidTransition) {
            throw new BadRequestException("INVALID_STATUS_TRANSITION", invalidTransition.getMessage());
        }
        publishChanged(property.getId());
        return property;
    }

    /**
     * Admin moderation — see project-docs/prompts/18-admin-platform.md. Deliberately
     * additive, not a replacement for {@link #transition}: hosts keep their existing
     * self-publish flow (PENDING -&gt; ACTIVE) unchanged; this exists alongside it for
     * admin-initiated transitions that skip the ownership check (e.g. suspending any
     * listing, or rejecting a pending submission back to DRAFT). {@code admin/}
     * (session 19) is responsible for authz (only reachable via
     * {@code @PreAuthorize("hasRole('ADMIN')")}) and audit logging around this call —
     * this method itself only knows how to change status, same as {@link #transition}.
     */
    @Transactional
    public Property transitionAsAdmin(UUID propertyId, PropertyStatus target) {
        Property property = propertyRepository.findById(propertyId).orElseThrow(() -> NotFoundException.of("Property", propertyId));
        try {
            property.transitionTo(target);
        } catch (IllegalStateException invalidTransition) {
            throw new BadRequestException("INVALID_STATUS_TRANSITION", invalidTransition.getMessage());
        }
        publishChanged(property.getId());
        return property;
    }

    /** Admin-scoped read — unlike {@link #getActive}, not limited to ACTIVE listings. */
    @Transactional(readOnly = true)
    public Property getAny(UUID propertyId) {
        return propertyRepository.findById(propertyId).orElseThrow(() -> NotFoundException.of("Property", propertyId));
    }

    /** Admin-scoped list — every listing regardless of host or status. */
    @Transactional(readOnly = true)
    public Page<Property> listAll(Pageable pageable) {
        return propertyRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Property getOwned(UUID hostId, UUID propertyId) {
        return findOwned(hostId, propertyId);
    }

    @Transactional(readOnly = true)
    public PropertyDetail getOwnedDetail(UUID hostId, UUID propertyId) {
        Property property = propertyRepository.findDetailByIdAndHostId(propertyId, hostId)
                .orElseThrow(() -> NotFoundException.of("Property", propertyId));
        return PropertyDetail.from(property);
    }

    @Transactional
    public PropertyDetail createDetail(UUID hostId, CreatePropertyRequest request) {
        return PropertyDetail.from(create(hostId, request));
    }

    @Transactional
    public PropertyDetail updateDetail(UUID hostId, UUID propertyId, UpdatePropertyRequest request) {
        return PropertyDetail.from(update(hostId, propertyId, request));
    }

    @Transactional
    public PropertyDetail transitionDetail(UUID hostId, UUID propertyId, PropertyStatus target) {
        return PropertyDetail.from(transition(hostId, propertyId, target));
    }

    @Transactional(readOnly = true)
    public Page<Property> listOwned(UUID hostId, Pageable pageable) {
        return propertyRepository.findAllByHostId(hostId, pageable);
    }

    @Transactional(readOnly = true)
    public Property getActive(UUID propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> NotFoundException.of("Property", propertyId));
        if (property.getStatus() != PropertyStatus.ACTIVE) {
            // Same 404 as "doesn't exist" — a draft/suspended listing isn't publicly visible.
            throw NotFoundException.of("Property", propertyId);
        }
        return property;
    }

    /**
     * Cached read path for the public {@code GET /properties/{id}} detail page —
     * prompt 25's "caching for popular properties." {@link #getActive(UUID)} above
     * stays uncached deliberately (booking/quote read the property through it, and
     * those reads must never see stale data — see {@link PropertyCacheService}'s
     * class doc).
     *
     * <p>The cached value is always the full-precision {@link PropertyDetail} —
     * caching a viewer-redacted copy would either leak the exact address to the next
     * anonymous visitor to hit a warm cache entry, or hide it from a privileged viewer
     * behind someone else's cached anonymous view. Redaction happens here, per call,
     * after the cache lookup, based on who's actually asking: the property's own host,
     * an ADMIN, and a guest with a {@code CONFIRMED}/{@code COMPLETED} booking for this
     * property see the real thing; everyone else — including a guest with only a
     * {@code PENDING} hold, which anyone can create — gets {@link
     * PropertyDetail#withApproximateLocation()}. See
     * project-docs/roadmap/02-launch-checklist.md#3.
     */
    @Transactional(readOnly = true)
    public PropertyDetail getActiveDetail(UUID propertyId, UUID viewerId, boolean viewerIsAdmin) {
        PropertyDetail detail = propertyCacheService.get(propertyId).orElseGet(() -> {
            List<String> photoUrls = propertyMediaRepository.findAllByPropertyIdOrderByPositionAsc(propertyId).stream()
                    .map(PropertyMedia::getSecureUrl)
                    .toList();
            PropertyDetail fresh = PropertyDetail.from(getActive(propertyId), photoUrls);
            propertyCacheService.put(propertyId, fresh);
            return fresh;
        });
        return canViewExactLocation(detail, viewerId, viewerIsAdmin) ? detail : detail.withApproximateLocation();
    }

    private boolean canViewExactLocation(PropertyDetail detail, UUID viewerId, boolean viewerIsAdmin) {
        if (viewerIsAdmin) {
            return true;
        }
        if (viewerId == null) {
            return false;
        }
        if (detail.hostId().equals(viewerId)) {
            return true;
        }
        return bookingRepository.existsByGuestIdAndPropertyIdAndStatusIn(viewerId, detail.id(), LOCATION_REVEAL_STATUSES);
    }

    @Transactional(readOnly = true)
    public Page<Property> listActive(Pageable pageable) {
        return propertyRepository.findAllByStatus(PropertyStatus.ACTIVE, pageable);
    }

    @Transactional
    public List<Availability> setAvailability(UUID hostId, UUID propertyId, SetAvailabilityRequest request) {
        Property property = findOwned(hostId, propertyId);
        List<Availability> result = request.days().stream()
                .map(day -> upsertAvailabilityDay(property, day))
                .toList();
        publishChanged(property.getId());
        return result;
    }

    @Transactional(readOnly = true)
    public List<Availability> getAvailability(UUID hostId, UUID propertyId, LocalDate from, LocalDate to) {
        findOwned(hostId, propertyId);
        if (from.isAfter(to)) {
            throw new BadRequestException("INVALID_DATE_RANGE", "'from' must not be after 'to'");
        }
        return availabilityRepository.findAllByProperty_IdAndDateBetweenOrderByDateAsc(propertyId, from, to);
    }

    private Availability upsertAvailabilityDay(Property property, AvailabilityDayInput day) {
        Availability availability = availabilityRepository
                .findByProperty_IdAndDate(property.getId(), day.date())
                .orElseGet(() -> availabilityRepository.save(new Availability(property, day.date(), false, null)));
        if (day.blocked() != null) {
            availability.setBlocked(day.blocked());
        }
        if (day.priceOverride() != null) {
            availability.setPriceOverride(day.priceOverride());
        }
        return availability;
    }

    private Property findOwned(UUID hostId, UUID propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> NotFoundException.of("Property", propertyId));
        if (!property.getHostId().equals(hostId)) {
            throw new ForbiddenException("Host does not own this property");
        }
        return property;
    }

    private PropertyType findType(String code) {
        return propertyTypeRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new BadRequestException("UNKNOWN_PROPERTY_TYPE", "Unknown property type: " + code));
    }

    private Set<Amenity> resolveAmenities(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<Amenity> found = amenityRepository.findAllByCodeInIgnoreCase(codes);
        if (found.size() != codes.size()) {
            throw new BadRequestException("UNKNOWN_AMENITY", "One or more amenity codes are unknown");
        }
        return new LinkedHashSet<>(found);
    }

    private void publishChanged(UUID propertyId) {
        eventPublisher.publishEvent(PropertyChangedEvent.of(propertyId));
    }
}