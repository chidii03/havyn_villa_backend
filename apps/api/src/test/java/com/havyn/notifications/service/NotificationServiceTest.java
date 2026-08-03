package com.havyn.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.havyn.properties.domain.PropertyType;
import com.havyn.properties.repo.PropertyRepository;
import com.havyn.users.domain.User;
import com.havyn.users.repo.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationServiceTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EmailSender emailSender = mock(EmailSender.class);

    private final NotificationService service = new NotificationService(notificationRepository, propertyRepository, userRepository, emailSender);

    private final UUID guestId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PropertyType villa = mock(PropertyType.class);
        Property property = new Property(
                UUID.randomUUID(), villa, "Sunset Villa", "Description", "1 Beach Rd", "Lagos", "Lagos", "Nigeria",
                BigDecimal.valueOf(10000), 4, 2, 2, BigDecimal.valueOf(2));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("guest@example.com");
        when(userRepository.findById(guestId)).thenReturn(Optional.of(user));
    }

    @Test
    void onBookingConfirmed_persistsAnInAppNotificationAndSendsEmail() {
        service.onBookingConfirmed(
                BookingConfirmedEvent.of(bookingId, guestId, propertyId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4)));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(guestId);
        assertThat(saved.getType()).isEqualTo(NotificationType.BOOKING_CONFIRMED);
        assertThat(saved.getLinkId()).isEqualTo(bookingId);
        assertThat(saved.getTitle()).isNotBlank();
        assertThat(saved.getBody()).contains("Sunset Villa");

        verify(emailSender).send(eq("guest@example.com"), anyString(), anyString());
    }

    @Test
    void onBookingCancelled_notesTheRefundWhenOneWasIssued() {
        service.onBookingCancelled(BookingCancelledEvent.of(bookingId, guestId, propertyId, true));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.BOOKING_CANCELLED);
        assertThat(captor.getValue().getBody()).contains("refund");
    }

    @Test
    void onMessageSent_notifiesTheRecipientNotTheSender() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        service.onMessageSent(MessageSentEvent.of(UUID.randomUUID(), conversationId, senderId, guestId, propertyId));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(guestId);
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.MESSAGE_RECEIVED);
        assertThat(captor.getValue().getLinkId()).isEqualTo(conversationId);
    }

    @Test
    void aFailedEmailSendDoesNotPreventTheInAppNotificationFromBeingRecorded() {
        doThrow(new RuntimeException("SMTP down")).when(emailSender).send(anyString(), anyString(), anyString());

        service.onBookingConfirmed(
                BookingConfirmedEvent.of(bookingId, guestId, propertyId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4)));

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void markRead_rejectsANotificationBelongingToSomeoneElse() {
        Notification notification = new Notification(guestId, NotificationType.MESSAGE_RECEIVED, "New message", "body", null);
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.markRead(UUID.randomUUID(), notification.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void markRead_rejectsAMissingNotification() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.markRead(guestId, notificationId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void markRead_setsReadAtForTheOwningUser() {
        Notification notification = new Notification(guestId, NotificationType.MESSAGE_RECEIVED, "New message", "body", null);
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        service.markRead(guestId, notification.getId());

        assertThat(notification.getReadAt()).isNotNull();
    }

    @Test
    void unreadCount_delegatesToTheRepository() {
        when(notificationRepository.countByUserIdAndReadAtIsNull(guestId)).thenReturn(3L);

        assertThat(service.unreadCount(guestId)).isEqualTo(3L);
        verify(notificationRepository, never()).findAllByUserIdOrderByCreatedAtDesc(any(), any());
    }
}
