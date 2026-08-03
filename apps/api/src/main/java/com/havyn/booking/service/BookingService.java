package com.havyn.booking.service;

import com.havyn.booking.domain.Booking;
import com.havyn.booking.domain.BookingStatus;
import com.havyn.booking.domain.CancellationPolicyCalculator;
import com.havyn.booking.domain.event.BookingCancelledEvent;
import com.havyn.booking.domain.event.BookingConfirmedEvent;
import com.havyn.booking.domain.event.BookingRefundDueEvent;
import com.havyn.booking.repo.BookingRepository;
import com.havyn.booking.web.CreateBookingRequest;
import com.havyn.common.error.BadRequestException;
import com.havyn.common.error.ConflictException;
import com.havyn.common.error.ForbiddenException;
import com.havyn.common.error.NotFoundException;
import com.havyn.common.error.ServiceUnavailableException;
import com.havyn.pricing.repo.PlatformSettingRepository;
import com.havyn.pricing.service.PricingBreakdown;
import com.havyn.pricing.service.PricingService;
import com.havyn.properties.domain.Availability;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.event.PropertyChangedEvent;
import com.havyn.properties.repo.AvailabilityRepository;
import com.havyn.properties.repo.PropertyRepository;
import com.havyn.properties.service.PropertyService;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The authoritative booking engine — see project-docs/prompts/12-booking-engine.md.
 * Object-level authorization (a guest may only see/cancel their own bookings) is
 * enforced here, mirroring {@code PropertyService}'s pattern from prompt 10.
 */
@Service
public class BookingService {

    private static final Set<BookingStatus> ACTIVE_STATUSES = Set.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.COMPLETED);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String BOOKINGS_ENABLED_SETTING_KEY = "bookings_enabled";

    private final BookingRepository bookingRepository;
    private final PropertyService propertyService;
    private final PropertyRepository propertyRepository;
    private final AvailabilityRepository availabilityRepository;
    private final PricingService pricingService;
    private final PlatformSettingRepository platformSettingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Duration holdDuration;
    private final MeterRegistry meterRegistry;

    public BookingService(
            BookingRepository bookingRepository,
            PropertyService propertyService,
            PropertyRepository propertyRepository,
            AvailabilityRepository availabilityRepository,
            PricingService pricingService,
            PlatformSettingRepository platformSettingRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            @Value("${havyn.booking.hold-duration-minutes:15}") long holdDurationMinutes,
            MeterRegistry meterRegistry) {
        this.bookingRepository = bookingRepository;
        this.propertyService = propertyService;
        this.propertyRepository = propertyRepository;
        this.availabilityRepository = availabilityRepository;
        this.pricingService = pricingService;
        this.platformSettingRepository = platformSettingRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.holdDuration = Duration.ofMinutes(holdDurationMinutes);
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public PricingBreakdown quote(UUID propertyId, LocalDate checkIn, LocalDate checkOut, int guests) {
        Property property = propertyService.getActive(propertyId);
        validateDates(checkIn, checkOut);
        validateCapacity(property, guests);
        if (!isAvailable(propertyId, checkIn, checkOut)) {
            throw new ConflictException("DATES_UNAVAILABLE", "These dates are no longer available");
        }
        return pricingService.quote(property, checkIn, checkOut);
    }

    @Transactional(readOnly = true)
    public Optional<Booking> findByIdempotencyKey(UUID guestId, String idempotencyKey) {
        return bookingRepository.findByGuestIdAndIdempotencyKey(guestId, idempotencyKey);
    }

    /**
     * Callers (the controller) are expected to have already checked {@link
     * #findByIdempotencyKey} and to hold {@code BookingHoldLock} for {@code
     * request.propertyId()} around this call.
     */
    @Transactional
    public Booking create(UUID guestId, String idempotencyKey, CreateBookingRequest request) {
        if (!bookingsEnabled()) {
            throw new ServiceUnavailableException("BOOKINGS_DISABLED", "Bookings are temporarily disabled. Please try again later.");
        }

        UUID propertyId = request.propertyId();
        expireStaleHolds(propertyId);

        Property property = propertyService.getActive(propertyId);
        validateDates(request.checkIn(), request.checkOut());
        validateCapacity(property, request.guests());

        if (!isAvailable(propertyId, request.checkIn(), request.checkOut())) {
            meterRegistry.counter("havyn.booking.create.failed", "reason", "dates_unavailable").increment();
            throw new ConflictException("DATES_UNAVAILABLE", "These dates are no longer available");
        }

        PricingBreakdown pricing = pricingService.quote(property, request.checkIn(), request.checkOut());
        if (pricing.grandTotal().compareTo(request.expectedTotal()) != 0) {
            meterRegistry.counter("havyn.booking.create.failed", "reason", "price_changed").increment();
            throw new ConflictException("PRICE_CHANGED", "The price for these dates has changed — please refresh and try again");
        }

        Booking booking = new Booking(
                propertyId,
                guestId,
                request.checkIn(),
                request.checkOut(),
                pricing.nights(),
                request.guests(),
                pricing.baseTotal(),
                pricing.cleaningFee(),
                pricing.serviceFee(),
                pricing.discountTotal(),
                pricing.taxTotal(),
                pricing.commissionAmount(),
                pricing.grandTotal(),
                pricing.currency());
        booking.setHoldExpiresAt(clock.instant().plus(holdDuration));
        booking.setIdempotencyKey(idempotencyKey);

        Booking saved = bookingRepository.save(booking);
        markAvailabilityBooked(property, request.checkIn(), request.checkOut(), saved.getId());
        eventPublisher.publishEvent(PropertyChangedEvent.of(propertyId));
        meterRegistry.counter("havyn.booking.created").increment();

        return saved;
    }

    /**
     * The application-level kill switch named in
     * project-docs/roadmap/02-launch-checklist.md#7 — before this, the only way to
     * stop new bookings mid-incident was a full redeploy or scaling the app to zero.
     * Read fresh on every attempt, same "no caching" reasoning as {@code
     * PricingService#commissionPct} for the sibling {@code commission_pct} setting —
     * this is exactly the kind of value an admin needs to take effect immediately, not
     * after a TTL. Defaults to enabled if the row is ever somehow missing (fail open,
     * not fail closed — a missing setting shouldn't itself take the platform down).
     */
    private boolean bookingsEnabled() {
        return platformSettingRepository.findById(BOOKINGS_ENABLED_SETTING_KEY)
                .map(setting -> !"false".equalsIgnoreCase(setting.getValue()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public Booking getOwned(UUID guestId, UUID bookingId) {
        return findOwned(guestId, bookingId);
    }

    @Transactional(readOnly = true)
    public Page<Booking> listOwned(UUID guestId, Pageable pageable) {
        return bookingRepository.findAllByGuestIdOrderByCreatedAtDesc(guestId, pageable);
    }

    /**
     * Host-scoped reservations — see project-docs/prompts/17-host-dashboard.md. When
     * {@code propertyId} is given, object-level authz confirms the host actually owns
     * that listing before scoping to it; when null, lists across every listing the
     * host owns.
     */
    @Transactional(readOnly = true)
    public Page<Booking> listForHost(UUID hostId, UUID propertyId, Pageable pageable) {
        List<UUID> propertyIds = propertyId != null ? List.of(ownedPropertyId(hostId, propertyId)) : hostPropertyIds(hostId);
        if (propertyIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return bookingRepository.findAllByPropertyIdInOrderByCheckInDesc(propertyIds, pageable);
    }

    @Transactional(readOnly = true)
    public long countUpcomingForHost(UUID hostId) {
        List<UUID> propertyIds = hostPropertyIds(hostId);
        if (propertyIds.isEmpty()) {
            return 0;
        }
        return bookingRepository.countByPropertyIdInAndStatusInAndCheckInGreaterThanEqual(
                propertyIds, Set.of(BookingStatus.PENDING, BookingStatus.CONFIRMED), LocalDate.now(clock));
    }

    private List<UUID> hostPropertyIds(UUID hostId) {
        return propertyRepository.findAllByHostId(hostId, Pageable.unpaged()).map(Property::getId).getContent();
    }

    private UUID ownedPropertyId(UUID hostId, UUID propertyId) {
        Property property = propertyRepository.findById(propertyId).orElseThrow(() -> NotFoundException.of("Property", propertyId));
        if (!property.getHostId().equals(hostId)) {
            throw new ForbiddenException("You do not have access to this listing");
        }
        return property.getId();
    }

    /**
     * A {@code PENDING} booking cancels for free (nothing was ever charged). A {@code
     * CONFIRMED} one computes a real, policy-based refund percentage/amount — correct
     * today, just unreachable until prompt 13 can actually get a booking to {@code
     * CONFIRMED}. Executing the refund via the payment provider is prompt 13's job;
     * this only records the state/amount owed.
     */
    @Transactional
    public CancellationOutcome cancel(UUID guestId, UUID bookingId) {
        Booking booking = findOwned(guestId, bookingId);

        if (booking.getStatus() == BookingStatus.PENDING) {
            booking.transitionTo(BookingStatus.CANCELLED);
            clearAvailabilityBooking(booking);
            eventPublisher.publishEvent(PropertyChangedEvent.of(booking.getPropertyId()));
            eventPublisher.publishEvent(BookingCancelledEvent.of(booking.getId(), booking.getGuestId(), booking.getPropertyId(), false));
            meterRegistry.counter("havyn.booking.cancelled", "refunded", "false").increment();
            return new CancellationOutcome(booking, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            String cancellationPolicy = propertyRepository.findById(booking.getPropertyId())
                    .map(Property::getCancellationPolicy)
                    .orElse("STRICT"); // conservative fallback if the listing was somehow deleted
            long daysUntilCheckIn = ChronoUnit.DAYS.between(LocalDate.now(clock), booking.getCheckIn());
            BigDecimal refundPercentage = CancellationPolicyCalculator.refundPercentage(cancellationPolicy, daysUntilCheckIn);
            BigDecimal refundAmount = booking.getGrandTotal()
                    .multiply(refundPercentage)
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);

            booking.transitionTo(refundPercentage.signum() > 0 ? BookingStatus.REFUNDED : BookingStatus.CANCELLED);
            clearAvailabilityBooking(booking);
            eventPublisher.publishEvent(PropertyChangedEvent.of(booking.getPropertyId()));
            eventPublisher.publishEvent(
                    BookingCancelledEvent.of(booking.getId(), booking.getGuestId(), booking.getPropertyId(), refundAmount.signum() > 0));
            if (refundAmount.signum() > 0) {
                // payments/ (prompt 13) listens for this to call the real provider refund —
                // booking/ never needs to know payments/ exists. See BookingRefundDueEvent's Javadoc.
                eventPublisher.publishEvent(BookingRefundDueEvent.of(booking.getId(), refundAmount));
            }
            meterRegistry.counter("havyn.booking.cancelled", "refunded", String.valueOf(refundAmount.signum() > 0)).increment();
            return new CancellationOutcome(booking, refundPercentage, refundAmount);
        }

        throw new BadRequestException("BOOKING_NOT_CANCELLABLE", "This booking can no longer be cancelled");
    }

    /**
     * Called by payments/ (prompt 13) once a webhook has verified a successful charge
     * — the only place a booking can move {@code PENDING -> CONFIRMED}. Idempotent:
     * a booking that's already past {@code PENDING} (e.g. a duplicate webhook, or two
     * payment attempts both succeeding) is left untouched rather than double-applied.
     */
    @Transactional
    public boolean confirmPayment(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> NotFoundException.of("Booking", bookingId));
        if (booking.getStatus() != BookingStatus.PENDING) {
            return false;
        }
        booking.transitionTo(BookingStatus.CONFIRMED);
        // notifications/ (prompt 16) listens for this to notify the guest — booking/
        // never needs to know notifications/ exists. See BookingConfirmedEvent's Javadoc.
        eventPublisher.publishEvent(
                BookingConfirmedEvent.of(booking.getId(), booking.getGuestId(), booking.getPropertyId(), booking.getCheckIn(), booking.getCheckOut()));
        return true;
    }

    /** Global hygiene sweep so an expired hold frees up even if nobody else tries to book that specific property again. */
    @Scheduled(fixedRateString = "${havyn.booking.hold-sweep-interval-ms:60000}")
    @Transactional
    public void sweepExpiredHolds() {
        List<Booking> expired = bookingRepository.findAllByStatusAndHoldExpiresAtBefore(BookingStatus.PENDING, clock.instant());
        for (Booking booking : expired) {
            booking.transitionTo(BookingStatus.CANCELLED);
            clearAvailabilityBooking(booking);
            eventPublisher.publishEvent(PropertyChangedEvent.of(booking.getPropertyId()));
        }
        // devops/02-observability.md: "alerts on... booking-hold anomalies" — a
        // sustained high rate here means holds are being created and abandoned (or
        // payment is broken) faster than guests are completing checkout.
        if (!expired.isEmpty()) {
            meterRegistry.counter("havyn.booking.hold_expired", "trigger", "scheduled_sweep").increment(expired.size());
        }
    }

    private void expireStaleHolds(UUID propertyId) {
        List<Booking> expired = bookingRepository
                .findAllByPropertyIdAndStatusAndHoldExpiresAtBefore(propertyId, BookingStatus.PENDING, clock.instant());
        for (Booking booking : expired) {
            booking.transitionTo(BookingStatus.CANCELLED);
            clearAvailabilityBooking(booking);
        }
        if (!expired.isEmpty()) {
            meterRegistry.counter("havyn.booking.hold_expired", "trigger", "lazy_on_create").increment(expired.size());
        }
    }

    private boolean isAvailable(UUID propertyId, LocalDate checkIn, LocalDate checkOut) {
        boolean blocked = availabilityRepository
                .findAllByProperty_IdAndDateBetweenOrderByDateAsc(propertyId, checkIn, checkOut.minusDays(1))
                .stream()
                .anyMatch(Availability::isBlocked);
        if (blocked) {
            return false;
        }
        return !bookingRepository.existsOverlapping(propertyId, checkIn, checkOut, ACTIVE_STATUSES);
    }

    private void markAvailabilityBooked(Property property, LocalDate checkIn, LocalDate checkOut, UUID bookingId) {
        for (LocalDate date = checkIn; date.isBefore(checkOut); date = date.plusDays(1)) {
            LocalDate currentDate = date;
            Availability availability = availabilityRepository
                    .findByProperty_IdAndDate(property.getId(), currentDate)
                    .orElseGet(() -> availabilityRepository.save(new Availability(property, currentDate, false, null)));
            availability.setBookingId(bookingId);
        }
    }

    private void clearAvailabilityBooking(Booking booking) {
        for (LocalDate date = booking.getCheckIn(); date.isBefore(booking.getCheckOut()); date = date.plusDays(1)) {
            availabilityRepository.findByProperty_IdAndDate(booking.getPropertyId(), date)
                    .filter(availability -> booking.getId().equals(availability.getBookingId()))
                    .ifPresent(availability -> availability.setBookingId(null));
        }
    }

    private void validateDates(LocalDate checkIn, LocalDate checkOut) {
        if (!checkIn.isBefore(checkOut)) {
            throw new BadRequestException("INVALID_DATE_RANGE", "checkIn must be before checkOut");
        }
        if (checkIn.isBefore(LocalDate.now(clock))) {
            throw new BadRequestException("INVALID_DATE_RANGE", "checkIn cannot be in the past");
        }
    }

    private void validateCapacity(Property property, int guests) {
        if (guests > property.getCapacity()) {
            throw new BadRequestException("EXCEEDS_CAPACITY", "This property sleeps at most " + property.getCapacity() + " guests");
        }
    }

    private Booking findOwned(UUID guestId, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> NotFoundException.of("Booking", bookingId));
        if (!booking.getGuestId().equals(guestId)) {
            throw new ForbiddenException("You do not have access to this booking");
        }
        return booking;
    }
}
