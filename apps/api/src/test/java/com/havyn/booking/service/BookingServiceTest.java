package com.havyn.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.havyn.booking.domain.Booking;
import com.havyn.booking.domain.BookingStatus;
import com.havyn.booking.repo.BookingRepository;
import com.havyn.booking.web.CreateBookingRequest;
import com.havyn.common.error.BadRequestException;
import com.havyn.common.error.ConflictException;
import com.havyn.common.error.ForbiddenException;
import com.havyn.common.error.ServiceUnavailableException;
import com.havyn.pricing.domain.PlatformSetting;
import com.havyn.pricing.repo.PlatformSettingRepository;
import com.havyn.pricing.service.PricingBreakdown;
import com.havyn.pricing.service.PricingService;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyType;
import com.havyn.properties.domain.event.PropertyChangedEvent;
import com.havyn.properties.repo.AvailabilityRepository;
import com.havyn.properties.repo.PropertyRepository;
import com.havyn.properties.service.PropertyService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class BookingServiceTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final PropertyService propertyService = mock(PropertyService.class);
    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final AvailabilityRepository availabilityRepository = mock(AvailabilityRepository.class);
    private final PricingService pricingService = mock(PricingService.class);
    private final PlatformSettingRepository platformSettingRepository = mock(PlatformSettingRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final BookingService service = new BookingService(
            bookingRepository, propertyService, propertyRepository, availabilityRepository, pricingService, platformSettingRepository,
            eventPublisher, clock, 15, meterRegistry);

    private final UUID guestId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private Property property;

    @BeforeEach
    void setUp() {
        PropertyType villa = mock(PropertyType.class);
        property = new Property(
                UUID.randomUUID(), villa, "Sunset Villa", "Description", "1 Beach Rd", "Lagos", "Lagos", "Nigeria",
                BigDecimal.valueOf(10000), 4, 2, 2, BigDecimal.valueOf(2));
        when(propertyService.getActive(propertyId)).thenReturn(property);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(availabilityRepository.findAllByProperty_IdAndDateBetweenOrderByDateAsc(any(), any(), any())).thenReturn(List.of());
        when(availabilityRepository.findByProperty_IdAndDate(nullable(UUID.class), any())).thenReturn(Optional.empty());
        when(availabilityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateBookingRequest request(LocalDate checkIn, LocalDate checkOut, int guests, BigDecimal expectedTotal) {
        return new CreateBookingRequest(propertyId, checkIn, checkOut, guests, expectedTotal);
    }

    @Test
    void create_rejectsWhenGuestsExceedCapacity() {
        CreateBookingRequest req = request(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 5, BigDecimal.TEN);

        assertThatThrownBy(() -> service.create(guestId, null, req))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("EXCEEDS_CAPACITY");
    }

    @Test
    void create_rejectsAnInvertedDateRange() {
        CreateBookingRequest req = request(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 1), 2, BigDecimal.TEN);

        assertThatThrownBy(() -> service.create(guestId, null, req))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void create_rejectsACheckInInThePast() {
        CreateBookingRequest req = request(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5), 2, BigDecimal.TEN);

        assertThatThrownBy(() -> service.create(guestId, null, req))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void create_rejectsWhenDatesAreAlreadyBooked() {
        when(bookingRepository.existsOverlapping(eq(propertyId), any(), any(), anySet())).thenReturn(true);
        CreateBookingRequest req = request(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 2, BigDecimal.TEN);

        assertThatThrownBy(() -> service.create(guestId, null, req))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("DATES_UNAVAILABLE");
        assertThat(meterRegistry.counter("havyn.booking.create.failed", "reason", "dates_unavailable").count()).isEqualTo(1.0);
    }

    @Test
    void create_rejectsWhenTheClientsExpectedTotalDoesNotMatchTheServerRecomputation() {
        when(bookingRepository.existsOverlapping(eq(propertyId), any(), any(), anySet())).thenReturn(false);
        when(pricingService.quote(eq(property), any(), any())).thenReturn(
                new PricingBreakdown(2, BigDecimal.valueOf(20000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.valueOf(20000), BigDecimal.ZERO, "NGN"));
        CreateBookingRequest req = request(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 2, BigDecimal.valueOf(999));

        assertThatThrownBy(() -> service.create(guestId, null, req))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("PRICE_CHANGED");
        assertThat(meterRegistry.counter("havyn.booking.create.failed", "reason", "price_changed").count()).isEqualTo(1.0);
    }

    @Test
    void create_savesAPendingBookingWithAHoldExpiryAndPublishesAChangeEvent() {
        when(bookingRepository.existsOverlapping(eq(propertyId), any(), any(), anySet())).thenReturn(false);
        PricingBreakdown pricing = new PricingBreakdown(
                2, BigDecimal.valueOf(20000), BigDecimal.valueOf(2000), BigDecimal.valueOf(1000), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(23000), BigDecimal.valueOf(500), "NGN");
        when(pricingService.quote(eq(property), any(), any())).thenReturn(pricing);
        CreateBookingRequest req = request(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 2, BigDecimal.valueOf(23000));

        Booking booking = service.create(guestId, "idem-key-1", req);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getGrandTotal()).isEqualByComparingTo("23000");
        assertThat(booking.getGuestId()).isEqualTo(guestId);
        assertThat(booking.getIdempotencyKey()).isEqualTo("idem-key-1");
        assertThat(booking.getHoldExpiresAt()).isEqualTo(clock.instant().plusSeconds(15 * 60));
        verify(eventPublisher).publishEvent(any(PropertyChangedEvent.class));
        assertThat(meterRegistry.counter("havyn.booking.created").count()).isEqualTo(1.0);
    }

    @Test
    void create_rejectsWithServiceUnavailableWhenTheBookingsEnabledKillSwitchIsOff() {
        when(platformSettingRepository.findById("bookings_enabled"))
                .thenReturn(Optional.of(new PlatformSetting("bookings_enabled", "false")));
        CreateBookingRequest req = request(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 2, BigDecimal.TEN);

        assertThatThrownBy(() -> service.create(guestId, null, req))
                .isInstanceOf(ServiceUnavailableException.class)
                .extracting(ex -> ((ServiceUnavailableException) ex).getCode())
                .isEqualTo("BOOKINGS_DISABLED");
        verifyNoInteractions(pricingService);
    }

    @Test
    void create_proceedsWhenTheBookingsEnabledSettingIsMissing_failsOpenNotClosed() {
        when(platformSettingRepository.findById("bookings_enabled")).thenReturn(Optional.empty());
        when(bookingRepository.existsOverlapping(eq(propertyId), any(), any(), anySet())).thenReturn(false);
        PricingBreakdown pricing = new PricingBreakdown(
                2, BigDecimal.valueOf(20000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(20000), BigDecimal.ZERO, "NGN");
        when(pricingService.quote(eq(property), any(), any())).thenReturn(pricing);
        CreateBookingRequest req = request(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 2, BigDecimal.valueOf(20000));

        Booking booking = service.create(guestId, null, req);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void cancel_throwsForbiddenWhenCallerIsNotTheGuest() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancel(UUID.randomUUID(), booking.getId())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancel_aPendingBookingCancelsWithNoRefundOwed() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(any())).thenReturn(Optional.of(booking));

        CancellationOutcome outcome = service.cancel(guestId, booking.getId());

        assertThat(outcome.booking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(outcome.refundPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(outcome.refundAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(eventPublisher).publishEvent(any(PropertyChangedEvent.class));
        assertThat(meterRegistry.counter("havyn.booking.cancelled", "refunded", "false").count()).isEqualTo(1.0);
    }

    @Test
    void cancel_aConfirmedFlexibleBookingWellBeforeCheckInRefundsInFull() {
        Booking booking = confirmedBooking();
        when(bookingRepository.findById(any())).thenReturn(Optional.of(booking));
        Property flexible = propertyWithPolicy("FLEXIBLE");
        when(propertyRepository.findById(booking.getPropertyId())).thenReturn(Optional.of(flexible));

        CancellationOutcome outcome = service.cancel(guestId, booking.getId());

        assertThat(outcome.booking().getStatus()).isEqualTo(BookingStatus.REFUNDED);
        assertThat(outcome.refundPercentage()).isEqualByComparingTo("100");
        assertThat(outcome.refundAmount()).isEqualByComparingTo(booking.getGrandTotal());
        assertThat(meterRegistry.counter("havyn.booking.cancelled", "refunded", "true").count()).isEqualTo(1.0);
    }

    @Test
    void cancel_aConfirmedStrictBookingWellBeforeCheckInRefundsHalf() {
        // checkIn 2026-08-01, "now" (fixed clock) is 2026-07-20 -> 12 days out, clears STRICT's 7-day minimum.
        Booking booking = confirmedBooking();
        when(bookingRepository.findById(any())).thenReturn(Optional.of(booking));
        Property strict = propertyWithPolicy("STRICT");
        when(propertyRepository.findById(booking.getPropertyId())).thenReturn(Optional.of(strict));

        CancellationOutcome outcome = service.cancel(guestId, booking.getId());

        assertThat(outcome.refundPercentage()).isEqualByComparingTo("50");
        assertThat(outcome.booking().getStatus()).isEqualTo(BookingStatus.REFUNDED);
    }

    @Test
    void cancel_aConfirmedStrictBookingTooCloseToCheckInGetsNoRefundAndStaysCancelled() {
        // checkIn 2026-07-24, "now" is 2026-07-20 -> only 4 days out, misses STRICT's 7-day minimum.
        Booking booking = new Booking(
                propertyId, guestId, LocalDate.of(2026, 7, 24), LocalDate.of(2026, 7, 26), 2, 2,
                BigDecimal.valueOf(20000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(20000), "NGN");
        booking.transitionTo(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(any())).thenReturn(Optional.of(booking));
        Property strict = propertyWithPolicy("STRICT");
        when(propertyRepository.findById(booking.getPropertyId())).thenReturn(Optional.of(strict));

        CancellationOutcome outcome = service.cancel(guestId, booking.getId());

        assertThat(outcome.refundPercentage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(outcome.refundAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        // No refund owed -> straight to CANCELLED, not REFUNDED (nothing to refund).
        assertThat(outcome.booking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancel_rejectsAnAlreadyCancelledBooking() {
        Booking booking = pendingBooking();
        booking.transitionTo(BookingStatus.CANCELLED);
        when(bookingRepository.findById(any())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.cancel(guestId, booking.getId()))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("BOOKING_NOT_CANCELLABLE");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void confirmPayment_assignsAReadableReferenceIdAndPublishesConfirmationEvent() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookingRepository.nextBookingReferenceSequence()).thenReturn(482L);

        boolean confirmed = service.confirmPayment(booking.getId());

        assertThat(confirmed).isTrue();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getReferenceId()).isEqualTo("HV-2026-000482");
        verify(eventPublisher).publishEvent(any(com.havyn.booking.domain.event.BookingConfirmedEvent.class));
    }

    @Test
    void confirmPayment_isIdempotentForAlreadyConfirmedBookingsAndDoesNotAssignANewReference() {
        Booking booking = confirmedBooking();
        booking.assignReferenceId("HV-2026-000123");
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        boolean confirmed = service.confirmPayment(booking.getId());

        assertThat(confirmed).isFalse();
        assertThat(booking.getReferenceId()).isEqualTo("HV-2026-000123");
        verify(bookingRepository, never()).nextBookingReferenceSequence();
        verify(eventPublisher, never()).publishEvent(any(com.havyn.booking.domain.event.BookingConfirmedEvent.class));
    }

    private Booking pendingBooking() {
        return new Booking(
                propertyId, guestId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 2, 2,
                BigDecimal.valueOf(20000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(20000), "NGN");
    }

    private Booking confirmedBooking() {
        Booking booking = pendingBooking();
        booking.transitionTo(BookingStatus.CONFIRMED);
        return booking;
    }

    private Property propertyWithPolicy(String policy) {
        PropertyType villa = mock(PropertyType.class);
        Property p = new Property(
                UUID.randomUUID(), villa, "Sunset Villa", "Description", "1 Beach Rd", "Lagos", "Lagos", "Nigeria",
                BigDecimal.valueOf(10000), 4, 2, 2, BigDecimal.valueOf(2));
        p.setCancellationPolicy(policy);
        return p;
    }
}
