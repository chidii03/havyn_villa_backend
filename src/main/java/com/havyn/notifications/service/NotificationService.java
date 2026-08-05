package com.havyn.notifications.service;

import com.havyn.booking.domain.event.BookingCancelledEvent;
import com.havyn.booking.domain.event.BookingConfirmedEvent;
import com.havyn.common.error.ForbiddenException;
import com.havyn.common.error.NotFoundException;
import com.havyn.messaging.domain.event.MessageSentEvent;
import com.havyn.notifications.domain.EmailSender;
import com.havyn.notifications.domain.Notification;
import com.havyn.notifications.domain.NotificationType;
import com.havyn.notifications.repo.NotificationRepository;
import com.havyn.properties.domain.Property;
import com.havyn.properties.repo.PropertyRepository;
import com.havyn.users.repo.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Channel-agnostic notifications (in-app persistence + best-effort email) — see
 * project-docs/prompts/16-messaging-notifications.md. Subscribes to the three domain
 * events named in this prompt's own deliverable list (booking confirmed, message
 * received, status changes — the latter covered by {@link BookingCancelledEvent}),
 * mirroring {@code PaymentService#onBookingRefundDue}'s exact
 * {@code @TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)} pattern:
 * never notify about a change that ends up rolling back.
 *
 * Reads {@code properties/}'s and {@code users/}'s repositories directly (read-only,
 * just for a human-readable title and a delivery email address) — the same established
 * cross-module read pattern used throughout this codebase (e.g. {@code ReviewService}
 * reading {@code booking/}'s repository).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;

    public NotificationService(
            NotificationRepository notificationRepository,
            PropertyRepository propertyRepository,
            UserRepository userRepository,
            EmailSender emailSender) {
        this.notificationRepository = notificationRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        String propertyTitle = propertyTitle(event.propertyId());
        create(
                event.guestId(),
                NotificationType.BOOKING_CONFIRMED,
                "Booking confirmed",
                "Your stay at " + propertyTitle + " (" + event.checkIn() + " to " + event.checkOut() + ") is confirmed.",
                event.bookingId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingCancelled(BookingCancelledEvent event) {
        String propertyTitle = propertyTitle(event.propertyId());
        String refundNote = event.refunded() ? " A refund has been issued." : "";
        create(
                event.guestId(),
                NotificationType.BOOKING_CANCELLED,
                "Booking cancelled",
                "Your booking at " + propertyTitle + " has been cancelled." + refundNote,
                event.bookingId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMessageSent(MessageSentEvent event) {
        String propertyTitle = propertyTitle(event.propertyId());
        create(
                event.recipientId(),
                NotificationType.MESSAGE_RECEIVED,
                "New message",
                "You have a new message about " + propertyTitle + ".",
                event.conversationId());
    }

    @Transactional(readOnly = true)
    public Page<Notification> listForUser(UUID userId, Pageable pageable) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> NotFoundException.of("Notification", notificationId));
        if (!notification.getUserId().equals(userId)) {
            throw new ForbiddenException("You do not have access to this notification");
        }
        notification.markRead(Instant.now());
    }

    private void create(UUID userId, NotificationType type, String title, String body, UUID linkId) {
        notificationRepository.save(new Notification(userId, type, title, body, linkId));
        sendEmailBestEffort(userId, title, body);
    }

    /**
     * Email is a best-effort side channel — the in-app row above is the source of
     * truth, so a flaky SMTP/provider failure must never roll back a real notification
     * that was otherwise successfully recorded.
     */
    private void sendEmailBestEffort(UUID userId, String subject, String body) {
        userRepository.findById(userId).ifPresent(user -> {
            try {
                emailSender.send(user.getEmail(), subject, body);
            } catch (RuntimeException e) {
                log.warn("Failed to send notification email for user {}: {}", userId, e.getMessage());
            }
        });
    }

    private String propertyTitle(UUID propertyId) {
        return propertyRepository.findById(propertyId).map(Property::getTitle).orElse("your listing");
    }
}
