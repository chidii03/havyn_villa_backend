package com.havyn.payments.service;

import com.havyn.booking.domain.Booking;
import com.havyn.booking.domain.BookingStatus;
import com.havyn.booking.domain.event.BookingRefundDueEvent;
import com.havyn.booking.repo.BookingRepository;
import com.havyn.booking.service.BookingService;
import com.havyn.common.error.ConflictException;
import com.havyn.common.error.ForbiddenException;
import com.havyn.common.error.NotFoundException;
import com.havyn.payments.domain.Payment;
import com.havyn.payments.domain.PaymentStatus;
import com.havyn.payments.domain.Payout;
import com.havyn.payments.domain.Refund;
import com.havyn.payments.domain.Transaction;
import com.havyn.payments.domain.TransactionType;
import com.havyn.payments.provider.PaymentIntentRequest;
import com.havyn.payments.provider.PaymentIntentResult;
import com.havyn.payments.provider.PaymentProvider;
import com.havyn.payments.provider.RefundRequest;
import com.havyn.payments.provider.RefundResult;
import com.havyn.payments.provider.WebhookEvent;
import com.havyn.payments.repo.PaymentRepository;
import com.havyn.payments.repo.PayoutRepository;
import com.havyn.payments.repo.RefundRepository;
import com.havyn.payments.repo.TransactionRepository;
import com.havyn.payments.web.PaymentIntentResponse;
import com.havyn.properties.domain.Property;
import com.havyn.properties.repo.PropertyRepository;
import com.havyn.users.domain.User;
import com.havyn.users.repo.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Orchestrates the payment lifecycle — see project-docs/prompts/13-payments.md.
 * Consumes {@code booking}/{@code properties}/{@code users} read-only (established
 * cross-module pattern, e.g. search/booking already read properties this way); the
 * only booking/ *write* is the single {@code confirmPayment}/event-publish hook added
 * for this prompt — see backend/02-domain-modules.md's session 7 notes.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String NO_REFUND_YET_REASON = "Booking cancelled";

    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final RefundRepository refundRepository;
    private final PayoutRepository payoutRepository;
    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final BookingService bookingService;
    private final List<PaymentProvider> providers;
    private final Clock clock;
    private final String defaultProviderName;
    private final MeterRegistry meterRegistry;

    public PaymentService(
            PaymentRepository paymentRepository,
            TransactionRepository transactionRepository,
            RefundRepository refundRepository,
            PayoutRepository payoutRepository,
            BookingRepository bookingRepository,
            PropertyRepository propertyRepository,
            UserRepository userRepository,
            BookingService bookingService,
            List<PaymentProvider> providers,
            Clock clock,
            @Value("${havyn.payments.provider:paystack}") String defaultProviderName,
            MeterRegistry meterRegistry) {
        this.paymentRepository = paymentRepository;
        this.transactionRepository = transactionRepository;
        this.refundRepository = refundRepository;
        this.payoutRepository = payoutRepository;
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.bookingService = bookingService;
        this.providers = providers;
        this.clock = clock;
        this.defaultProviderName = defaultProviderName;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public PaymentIntentResponse createIntent(UUID guestId, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> NotFoundException.of("Booking", bookingId));
        if (!booking.getGuestId().equals(guestId)) {
            throw new ForbiddenException("You do not have access to this booking");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ConflictException("BOOKING_NOT_PAYABLE", "This booking isn't awaiting payment");
        }
        if (booking.getHoldExpiresAt() != null && booking.getHoldExpiresAt().isBefore(clock.instant())) {
            throw new ConflictException("HOLD_EXPIRED", "The hold on these dates has expired — please reserve again");
        }

        User guest = userRepository.findById(guestId).orElseThrow(() -> NotFoundException.of("User", guestId));
        PaymentProvider provider = resolveProvider(defaultProviderName);

        String reference = UUID.randomUUID().toString();
        Payment payment = paymentRepository.save(
                new Payment(bookingId, provider.name(), reference, booking.getGrandTotal(), booking.getCurrency()));

        PaymentIntentResult result = provider.createIntent(
                new PaymentIntentRequest(payment.getId(), booking.getGrandTotal(), booking.getCurrency(), guest.getEmail(), reference));

        transactionRepository.save(new Transaction(
                payment.getId(), TransactionType.CHARGE_INTENT_CREATED, reference, booking.getGrandTotal(), booking.getCurrency(), null));

        return new PaymentIntentResponse(payment.getId(), provider.name(), result.checkoutUrl());
    }

    @Transactional
    public void handleWebhook(String providerName, String rawBody, HttpHeaders headers) {
        PaymentProvider provider = resolveProvider(providerName);
        WebhookEvent event = provider.parseWebhook(rawBody, headers);
        log.info("Received {} webhook type={} ref={}", providerName, event.type(), event.providerRef());

        if (event.providerRef() == null) {
            return; // nothing to correlate this event to
        }
        Optional<Payment> maybePayment = paymentRepository.findByProviderAndProviderRef(providerName, event.providerRef());
        if (maybePayment.isEmpty()) {
            log.warn("Webhook ref={} doesn't match any known payment — ignoring", event.providerRef());
            meterRegistry.counter("havyn.payment.webhook", "provider", providerName, "outcome", "unmatched_ref").increment();
            return; // unknown reference — not one of ours (or already-deleted test data); nothing to do
        }
        Payment payment = maybePayment.get();

        transactionRepository.save(new Transaction(
                payment.getId(),
                transactionTypeFor(event),
                event.providerRef(),
                event.amount() != null ? event.amount() : payment.getAmount(),
                payment.getCurrency(),
                rawBody));

        if (payment.isTerminal()) {
            log.info("Ignoring webhook for already-terminal paymentId={} (retried/duplicate delivery)", payment.getId());
            meterRegistry.counter("havyn.payment.webhook", "provider", providerName, "outcome", "already_terminal").increment();
            return; // idempotent no-op — a retried/duplicate webhook delivery for an already-resolved payment
        }

        switch (event.type()) {
            case CHARGE_SUCCEEDED -> {
                payment.markSucceeded();
                boolean bookingWasConfirmed = bookingService.confirmPayment(payment.getBookingId());
                if (bookingWasConfirmed) {
                    accruePayout(payment);
                }
                meterRegistry.counter("havyn.payment.webhook", "provider", providerName, "outcome", "charge_succeeded").increment();
            }
            case CHARGE_FAILED -> {
                payment.markFailed();
                meterRegistry.counter("havyn.payment.webhook", "provider", providerName, "outcome", "charge_failed").increment();
            }
            default -> {
                // REFUND_PROCESSED/UNKNOWN — recorded above for audit; refund state itself is
                // updated synchronously from the provider's refund response (see
                // onBookingRefundDue below), not from this async webhook.
                meterRegistry.counter("havyn.payment.webhook", "provider", providerName, "outcome", "other").increment();
            }
        }
    }

    /**
     * {@code fallbackExecution = true}/{@code AFTER_COMMIT} — same reasoning as
     * {@code SearchCacheService}'s listener (prompt 11): don't call out to a real
     * payment provider for a cancellation that ends up rolling back.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingRefundDue(BookingRefundDueEvent event) {
        List<Payment> payments = paymentRepository.findAllByBookingIdOrderByCreatedAtDesc(event.bookingId());
        Payment succeededPayment = payments.stream().filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED).findFirst().orElse(null);
        if (succeededPayment == null) {
            return; // defensive — a CONFIRMED booking should always have one, but never assume
        }

        Refund refund = refundRepository.save(new Refund(succeededPayment.getId(), event.refundAmount(), NO_REFUND_YET_REASON));
        PaymentProvider provider = resolveProvider(succeededPayment.getProvider());
        RefundResult result = provider.refund(new RefundRequest(succeededPayment.getProviderRef(), event.refundAmount(), NO_REFUND_YET_REASON));

        TransactionType transactionType;
        if (result.succeeded()) {
            refund.markSucceeded(result.providerRefundRef());
            transactionType = TransactionType.REFUND_SUCCEEDED;
        } else {
            refund.markFailed();
            transactionType = TransactionType.REFUND_FAILED;
        }
        transactionRepository.save(new Transaction(
                succeededPayment.getId(), transactionType, result.providerRefundRef(), event.refundAmount(), succeededPayment.getCurrency(), null));
    }

    /** Paginated payout history for a host — see project-docs/prompts/17-host-dashboard.md. */
    @Transactional(readOnly = true)
    public Page<Payout> listPayoutsForHost(UUID hostId, Pageable pageable) {
        return payoutRepository.findAllByHostIdOrderByPeriodDesc(hostId, pageable);
    }

    /** Unpaged — for {@code hosts/}'s dashboard summary to fold into a total-by-currency figure itself. */
    @Transactional(readOnly = true)
    public List<Payout> listAllPayoutsForHost(UUID hostId) {
        return payoutRepository.findAllByHostId(hostId);
    }

    private void accruePayout(Payment payment) {
        Booking booking = bookingRepository.findById(payment.getBookingId()).orElse(null);
        if (booking == null) {
            return;
        }
        Property property = propertyRepository.findById(booking.getPropertyId()).orElse(null);
        if (property == null) {
            return; // can't determine which host to credit — nothing sensible to do
        }

        BigDecimal netAmount = booking.getGrandTotal().subtract(booking.getCommissionAmount());
        String period = YearMonth.now(clock).toString();
        Payout payout = payoutRepository.findByHostIdAndPeriodAndCurrency(property.getHostId(), period, booking.getCurrency())
                .orElseGet(() -> payoutRepository.save(new Payout(property.getHostId(), period, booking.getCurrency())));
        payout.accrue(netAmount);
    }

    private TransactionType transactionTypeFor(WebhookEvent event) {
        return switch (event.type()) {
            case CHARGE_SUCCEEDED -> TransactionType.CHARGE_SUCCEEDED;
            case CHARGE_FAILED -> TransactionType.CHARGE_FAILED;
            case REFUND_PROCESSED -> TransactionType.REFUND_SUCCEEDED;
            case UNKNOWN -> TransactionType.WEBHOOK_UNRECOGNIZED;
        };
    }

    private PaymentProvider resolveProvider(String name) {
        return providers.stream()
                .filter(provider -> provider.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No PaymentProvider registered for: " + name));
    }
}
