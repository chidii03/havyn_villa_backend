package com.havyn.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.havyn.booking.domain.Booking;
import com.havyn.booking.domain.BookingStatus;
import com.havyn.booking.domain.event.BookingRefundDueEvent;
import com.havyn.booking.repo.BookingRepository;
import com.havyn.booking.service.BookingService;
import com.havyn.common.error.ConflictException;
import com.havyn.common.error.ForbiddenException;
import com.havyn.payments.domain.Payment;
import com.havyn.payments.domain.PaymentStatus;
import com.havyn.payments.domain.Payout;
import com.havyn.payments.domain.Refund;
import com.havyn.payments.provider.PaymentIntentResult;
import com.havyn.payments.provider.PaymentProvider;
import com.havyn.payments.provider.RefundResult;
import com.havyn.payments.provider.WebhookEvent;
import com.havyn.payments.provider.WebhookEventType;
import com.havyn.payments.repo.PaymentRepository;
import com.havyn.payments.repo.PayoutRepository;
import com.havyn.payments.repo.RefundRepository;
import com.havyn.payments.repo.TransactionRepository;
import com.havyn.payments.web.PaymentIntentResponse;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyType;
import com.havyn.properties.repo.PropertyRepository;
import com.havyn.users.domain.User;
import com.havyn.users.repo.UserRepository;
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
import org.springframework.http.HttpHeaders;

class PaymentServiceTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final RefundRepository refundRepository = mock(RefundRepository.class);
    private final PayoutRepository payoutRepository = mock(PayoutRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final BookingService bookingService = mock(BookingService.class);
    private final PaymentProvider paystack = mock(PaymentProvider.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final PaymentService service = new PaymentService(
            paymentRepository, transactionRepository, refundRepository, payoutRepository, bookingRepository,
            propertyRepository, userRepository, bookingService, List.of(paystack), clock, "paystack", meterRegistry);

    private final UUID guestId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(paystack.name()).thenReturn("paystack");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Booking pendingBooking(Instant holdExpiresAt) {
        Booking booking = new Booking(
                propertyId, guestId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 2, 2,
                BigDecimal.valueOf(30000), BigDecimal.valueOf(2000), BigDecimal.valueOf(3000), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(4200), BigDecimal.valueOf(35000), "NGN");
        booking.setHoldExpiresAt(holdExpiresAt);
        return booking;
    }

    @Test
    void createIntent_rejectsWhenCallerIsNotTheGuest() {
        Booking booking = pendingBooking(clock.instant().plusSeconds(600));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.createIntent(UUID.randomUUID(), bookingId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createIntent_rejectsWhenTheBookingIsNotPending() {
        Booking booking = pendingBooking(clock.instant().plusSeconds(600));
        booking.transitionTo(BookingStatus.CANCELLED);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.createIntent(guestId, bookingId))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("BOOKING_NOT_PAYABLE");
    }

    @Test
    void createIntent_rejectsWhenTheHoldHasExpired() {
        Booking booking = pendingBooking(clock.instant().minusSeconds(60));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.createIntent(guestId, bookingId))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("HOLD_EXPIRED");
    }

    @Test
    void createIntent_createsAPendingPaymentAndReturnsTheCheckoutUrl() {
        Booking booking = pendingBooking(clock.instant().plusSeconds(600));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        User guest = new User("guest@example.com", "hash");
        when(userRepository.findById(guestId)).thenReturn(Optional.of(guest));
        when(paystack.createIntent(any())).thenReturn(new PaymentIntentResult("ref-123", "https://checkout.paystack.com/abc"));

        PaymentIntentResponse response = service.createIntent(guestId, bookingId);

        assertThat(response.provider()).isEqualTo("paystack");
        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.paystack.com/abc");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void handleWebhook_ignoresAnEventForAnUnknownReference() {
        when(paystack.parseWebhook(any(), any(HttpHeaders.class)))
                .thenReturn(new WebhookEvent(WebhookEventType.CHARGE_SUCCEEDED, "unknown-ref", BigDecimal.TEN, "NGN", "{}"));
        when(paymentRepository.findByProviderAndProviderRef("paystack", "unknown-ref")).thenReturn(Optional.empty());

        service.handleWebhook("paystack", "{}", new HttpHeaders());

        verify(bookingService, never()).confirmPayment(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void handleWebhook_isIdempotentForAnAlreadyTerminalPayment() {
        Payment payment = new Payment(bookingId, "paystack", "ref-123", BigDecimal.valueOf(35000), "NGN");
        payment.markSucceeded();
        when(paystack.parseWebhook(any(), any(HttpHeaders.class)))
                .thenReturn(new WebhookEvent(WebhookEventType.CHARGE_SUCCEEDED, "ref-123", BigDecimal.valueOf(35000), "NGN", "{}"));
        when(paymentRepository.findByProviderAndProviderRef("paystack", "ref-123")).thenReturn(Optional.of(payment));

        service.handleWebhook("paystack", "{}", new HttpHeaders());

        // Still recorded for audit...
        verify(transactionRepository, times(1)).save(any());
        // ...but never re-applied.
        verify(bookingService, never()).confirmPayment(any());
    }

    @Test
    void handleWebhook_onChargeSucceeded_confirmsTheBookingAndAccruesAPayout() {
        Payment payment = new Payment(bookingId, "paystack", "ref-123", BigDecimal.valueOf(35000), "NGN");
        when(paystack.parseWebhook(any(), any(HttpHeaders.class)))
                .thenReturn(new WebhookEvent(WebhookEventType.CHARGE_SUCCEEDED, "ref-123", BigDecimal.valueOf(35000), "NGN", "{}"));
        when(paymentRepository.findByProviderAndProviderRef("paystack", "ref-123")).thenReturn(Optional.of(payment));
        when(bookingService.confirmPayment(bookingId)).thenReturn(true);

        Booking booking = pendingBooking(clock.instant().plusSeconds(600));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        Property property = property();
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(payoutRepository.findByHostIdAndPeriodAndCurrency(any(), eq("2026-07"), eq("NGN"))).thenReturn(Optional.empty());
        when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handleWebhook("paystack", "{}", new HttpHeaders());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(bookingService).confirmPayment(bookingId);
        verify(payoutRepository).save(any(Payout.class));
    }

    @Test
    void handleWebhook_skipsPayoutAccrualWhenTheBookingWasAlreadyConfirmed() {
        Payment payment = new Payment(bookingId, "paystack", "ref-123", BigDecimal.valueOf(35000), "NGN");
        when(paystack.parseWebhook(any(), any(HttpHeaders.class)))
                .thenReturn(new WebhookEvent(WebhookEventType.CHARGE_SUCCEEDED, "ref-123", BigDecimal.valueOf(35000), "NGN", "{}"));
        when(paymentRepository.findByProviderAndProviderRef("paystack", "ref-123")).thenReturn(Optional.of(payment));
        when(bookingService.confirmPayment(bookingId)).thenReturn(false); // e.g. a duplicate successful payment attempt

        service.handleWebhook("paystack", "{}", new HttpHeaders());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED); // still financially honest
        verify(payoutRepository, never()).save(any());
    }

    @Test
    void handleWebhook_onChargeFailed_marksThePaymentFailed() {
        Payment payment = new Payment(bookingId, "paystack", "ref-123", BigDecimal.valueOf(35000), "NGN");
        when(paystack.parseWebhook(any(), any(HttpHeaders.class)))
                .thenReturn(new WebhookEvent(WebhookEventType.CHARGE_FAILED, "ref-123", BigDecimal.valueOf(35000), "NGN", "{}"));
        when(paymentRepository.findByProviderAndProviderRef("paystack", "ref-123")).thenReturn(Optional.of(payment));

        service.handleWebhook("paystack", "{}", new HttpHeaders());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(bookingService, never()).confirmPayment(any());
    }

    @Test
    void onBookingRefundDue_callsTheProviderAndRecordsASucceededRefund() {
        Payment succeededPayment = new Payment(bookingId, "paystack", "ref-123", BigDecimal.valueOf(35000), "NGN");
        succeededPayment.markSucceeded();
        when(paymentRepository.findAllByBookingIdOrderByCreatedAtDesc(bookingId)).thenReturn(List.of(succeededPayment));
        when(paystack.refund(any())).thenReturn(new RefundResult("refund-ref-1", true));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        service.onBookingRefundDue(BookingRefundDueEvent.of(bookingId, BigDecimal.valueOf(35000)));

        verify(paystack).refund(any());
        verify(refundRepository).save(any(Refund.class));
    }

    @Test
    void onBookingRefundDue_doesNothingWhenNoPaymentEverSucceeded() {
        when(paymentRepository.findAllByBookingIdOrderByCreatedAtDesc(bookingId)).thenReturn(List.of());

        service.onBookingRefundDue(BookingRefundDueEvent.of(bookingId, BigDecimal.valueOf(35000)));

        verify(paystack, never()).refund(any());
    }

    private Property property() {
        PropertyType villa = mock(PropertyType.class);
        return new Property(
                UUID.randomUUID(), villa, "Sunset Villa", "Description", "1 Beach Rd", "Lagos", "Lagos", "Nigeria",
                BigDecimal.valueOf(10000), 4, 2, 2, BigDecimal.valueOf(2));
    }
}
