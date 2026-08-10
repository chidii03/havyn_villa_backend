package com.havyn.notifications.service;

import com.havyn.booking.domain.Booking;
import com.havyn.booking.domain.event.BookingConfirmedEvent;
import com.havyn.booking.repo.BookingRepository;
import com.havyn.common.error.NotFoundException;
import com.havyn.media.repo.PropertyMediaRepository;
import com.havyn.notifications.domain.BookingEmailLog;
import com.havyn.notifications.domain.EmailSender;
import com.havyn.notifications.repo.BookingEmailLogRepository;
import com.havyn.payments.domain.Payment;
import com.havyn.payments.domain.PaymentStatus;
import com.havyn.payments.repo.PaymentRepository;
import com.havyn.properties.domain.Property;
import com.havyn.properties.repo.PropertyRepository;
import com.havyn.users.domain.Profile;
import com.havyn.users.domain.User;
import com.havyn.users.repo.ProfileRepository;
import com.havyn.users.repo.UserRepository;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class BookingConfirmationEmailService {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmationEmailService.class);
    private static final int MAX_SEND_ATTEMPTS = 3;

    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PaymentRepository paymentRepository;
    private final PropertyMediaRepository mediaRepository;
    private final BookingEmailLogRepository emailLogRepository;
    private final EmailSender emailSender;
    private final Clock clock;
    private final String timezone;

    public BookingConfirmationEmailService(
            BookingRepository bookingRepository,
            PropertyRepository propertyRepository,
            UserRepository userRepository,
            ProfileRepository profileRepository,
            PaymentRepository paymentRepository,
            PropertyMediaRepository mediaRepository,
            BookingEmailLogRepository emailLogRepository,
            EmailSender emailSender,
            Clock clock,
            @Value("${havyn.notification-timezone:Africa/Lagos}") String timezone) {
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.paymentRepository = paymentRepository;
        this.mediaRepository = mediaRepository;
        this.emailLogRepository = emailLogRepository;
        this.emailSender = emailSender;
        this.clock = clock;
        this.timezone = timezone;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        Booking booking = bookingRepository.findById(event.bookingId()).orElseThrow(() -> NotFoundException.of("Booking", event.bookingId()));
        User guest = userRepository.findById(booking.getGuestId()).orElseThrow(() -> NotFoundException.of("User", booking.getGuestId()));
        Property property = propertyRepository.findById(booking.getPropertyId()).orElseThrow(() -> NotFoundException.of("Property", booking.getPropertyId()));
        Payment payment = paymentRepository.findAllByBookingIdOrderByCreatedAtDesc(booking.getId()).stream()
                .filter(candidate -> candidate.getStatus() == PaymentStatus.SUCCEEDED)
                .findFirst()
                .orElse(null);

        BookingEmailLog emailLog = emailLogRepository.save(new BookingEmailLog(booking.getId(), booking.getReferenceId(), guest.getEmail()));
        String subject = "Booking confirmed: " + booking.getReferenceId();
        String html = buildEmail(booking, property, guest, payment);
        RuntimeException lastFailure = null;
        int retryAttempts = 0;

        for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
            try {
                emailSender.sendHtml(guest.getEmail(), subject, html);
                emailLog.markSuccessful(retryAttempts);
                log.info("Booking confirmation email sent bookingId={} referenceId={} recipient={}", booking.getId(), booking.getReferenceId(), guest.getEmail());
                return;
            } catch (RuntimeException e) {
                lastFailure = e;
                retryAttempts = attempt - 1;
                log.warn(
                        "Booking confirmation email attempt failed bookingId={} referenceId={} attempt={} error={}",
                        booking.getId(),
                        booking.getReferenceId(),
                        attempt,
                        e.getMessage());
            }
        }

        emailLog.markFailed(retryAttempts, lastFailure == null ? "Unknown send failure" : lastFailure.getMessage());
    }

    private String buildEmail(Booking booking, Property property, User guest, Payment payment) {
        String guestName = profileRepository.findByUser_Id(guest.getId()).map(Profile::getFullName).orElse(guest.getEmail());
        String thumbnailUrl = mediaRepository.findAllByPropertyIdOrderByPositionAsc(property.getId()).stream()
                .findFirst()
                .map(media -> media.getSecureUrl())
                .orElse("");
        String issuedAt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")
                .withZone(ZoneId.of(timezone))
                .format(clock.instant());
        String location = property.getCity() + ", " + property.getState() + ", " + property.getCountry();
        String paymentMethod = payment == null ? "Recorded payment" : payment.getProvider();
        String transactionId = payment == null ? "Unavailable" : payment.getProviderRef();
        String paymentDate = payment == null || payment.getUpdatedAt() == null
                ? issuedAt
                : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.of(timezone)).format(payment.getUpdatedAt());

        return """
                <!doctype html>
                <html>
                  <body style="margin:0;background:#f4f8ff;font-family:Arial,sans-serif;color:#172033">
                    <div style="max-width:640px;margin:0 auto;padding:24px">
                      <div style="background:#003da6;color:#fff;padding:22px;border-radius:8px 8px 0 0">
                        <h1 style="margin:0;font-size:26px">Havyn Villa</h1>
                        <p style="margin:6px 0 0">Stay beautiful, live better.</p>
                      </div>
                      <div style="background:#fff;border:1px solid #dfe8f5;border-top:0;padding:24px;border-radius:0 0 8px 8px">
                        <h2 style="margin:0 0 12px">Your booking is confirmed</h2>
                        <p>Hello %s, your stay is confirmed. Keep this reference for support: <strong>%s</strong>.</p>
                        %s
                        <h3>Stay details</h3>
                        <p><strong>%s</strong><br>%s</p>
                        <p>Check-in: %s<br>Check-out: %s<br>Guests: %d</p>
                        <h3>Payment receipt</h3>
                        <p>Amount paid: <strong>%s</strong><br>Method: %s<br>Transaction ID: %s<br>Paid at: %s</p>
                        <p>Issued at: %s</p>
                        <h3>Host contact</h3>
                        <p>Contact the host from your Havyn Villa trip details. Support can also help using your booking reference.</p>
                        <h3>Cancellation and refunds</h3>
                        <p>%s</p>
                        <p style="border-top:1px solid #dfe8f5;margin-top:24px;padding-top:16px;color:#5d6b82">
                          Havyn Villa<br>Need help? Reply to this email or visit Support in your account.
                        </p>
                      </div>
                    </div>
                  </body>
                </html>
                """
                .formatted(
                        escape(guestName),
                        escape(booking.getReferenceId()),
                        thumbnailUrl.isBlank()
                                ? ""
                                : "<img src=\"" + escape(thumbnailUrl) + "\" alt=\"" + escape(property.getTitle())
                                        + "\" style=\"width:100%;max-height:260px;object-fit:cover;border-radius:8px;margin:12px 0\">",
                        escape(property.getTitle()),
                        escape(location),
                        booking.getCheckIn(),
                        booking.getCheckOut(),
                        booking.getGuestsCount(),
                        escape(formatMoney(booking.getGrandTotal(), booking.getCurrency())),
                        escape(paymentMethod),
                        escape(transactionId),
                        escape(paymentDate),
                        escape(issuedAt),
                        escape(policySummary(property.getCancellationPolicy())));
    }

    private String policySummary(String policy) {
        return switch ((policy == null ? "" : policy).toUpperCase(Locale.ROOT)) {
            case "FLEXIBLE" -> "Flexible policy: eligible cancellations may receive a larger refund before check-in.";
            case "MODERATE" -> "Moderate policy: refund eligibility depends on how far before check-in you cancel.";
            default -> "Strict policy: refunds are limited and depend on timing before check-in.";
        };
    }

    private String formatMoney(BigDecimal amount, String currency) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("en", "NG"));
        formatter.setCurrency(java.util.Currency.getInstance(currency));
        return formatter.format(amount);
    }

    private String escape(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
