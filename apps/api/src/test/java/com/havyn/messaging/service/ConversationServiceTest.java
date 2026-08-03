package com.havyn.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.havyn.booking.domain.Booking;
import com.havyn.booking.repo.BookingRepository;
import com.havyn.common.error.BadRequestException;
import com.havyn.common.error.ForbiddenException;
import com.havyn.common.error.NotFoundException;
import com.havyn.messaging.domain.Conversation;
import com.havyn.messaging.domain.Message;
import com.havyn.messaging.domain.event.MessageSentEvent;
import com.havyn.messaging.repo.ConversationRepository;
import com.havyn.messaging.repo.MessageRepository;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyType;
import com.havyn.properties.repo.PropertyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class ConversationServiceTest {

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final ConversationService service =
            new ConversationService(conversationRepository, messageRepository, propertyRepository, bookingRepository, eventPublisher);

    private final UUID hostId = UUID.randomUUID();
    private final UUID guestId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private Property property;

    @BeforeEach
    void setUp() {
        PropertyType villa = mock(PropertyType.class);
        property = new Property(
                hostId, villa, "Sunset Villa", "Description", "1 Beach Rd", "Lagos", "Lagos", "Nigeria",
                BigDecimal.valueOf(10000), 4, 2, 2, BigDecimal.valueOf(2));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void startOrContinue_rejectsWhenHostMessagesOwnListing() {
        assertThatThrownBy(() -> service.startOrContinue(hostId, propertyId, null, "Hello"))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("HOST_CANNOT_MESSAGE_OWN_LISTING");
    }

    @Test
    void startOrContinue_createsANewConversationAndPublishesAMessageSentEventToTheHost() {
        when(conversationRepository.findByPropertyIdAndGuestId(propertyId, guestId)).thenReturn(Optional.empty());

        Conversation conversation = service.startOrContinue(guestId, propertyId, null, "Is this available in June?");

        assertThat(conversation.getHostId()).isEqualTo(hostId);
        assertThat(conversation.getGuestId()).isEqualTo(guestId);
        assertThat(conversation.getLastMessageAt()).isNotNull();
        MessageSentEvent event = capturePublishedEvent();
        assertThat(event.senderId()).isEqualTo(guestId);
        assertThat(event.recipientId()).isEqualTo(hostId);
    }

    @Test
    void startOrContinue_reusesTheExistingThreadForTheSamePropertyAndGuest() {
        Conversation existing = new Conversation(propertyId, hostId, guestId);
        when(conversationRepository.findByPropertyIdAndGuestId(propertyId, guestId)).thenReturn(Optional.of(existing));

        Conversation result = service.startOrContinue(guestId, propertyId, null, "Following up");

        assertThat(result).isSameAs(existing);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void startOrContinue_rejectsABookingThatBelongsToSomeoneElse() {
        UUID bookingId = UUID.randomUUID();
        Booking othersBooking = new Booking(
                propertyId, UUID.randomUUID(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 2, 2,
                BigDecimal.valueOf(20000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(20000), "NGN");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(othersBooking));
        when(conversationRepository.findByPropertyIdAndGuestId(propertyId, guestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startOrContinue(guestId, propertyId, bookingId, "About my stay"))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("BOOKING_PROPERTY_MISMATCH");
    }

    @Test
    void startOrContinue_linksAValidBooking() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking(
                propertyId, guestId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), 2, 2,
                BigDecimal.valueOf(20000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(20000), "NGN");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(conversationRepository.findByPropertyIdAndGuestId(propertyId, guestId)).thenReturn(Optional.empty());

        Conversation conversation = service.startOrContinue(guestId, propertyId, bookingId, "About my confirmed stay");

        assertThat(conversation.getBookingId()).isEqualTo(bookingId);
    }

    @Test
    void sendMessage_rejectsANonParticipant() {
        Conversation conversation = new Conversation(propertyId, hostId, guestId);
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.sendMessage(UUID.randomUUID(), conversation.getId(), "Hi"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sendMessage_rejectsAMissingConversation() {
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage(guestId, conversationId, "Hi")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void sendMessage_fromTheHostNotifiesTheGuest() {
        Conversation conversation = new Conversation(propertyId, hostId, guestId);
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        service.sendMessage(hostId, conversation.getId(), "Sure, June is open!");

        MessageSentEvent event = capturePublishedEvent();
        assertThat(event.senderId()).isEqualTo(hostId);
        assertThat(event.recipientId()).isEqualTo(guestId);
    }

    @Test
    void markRead_marksOnlyMessagesNotSentByTheCaller() {
        Conversation conversation = new Conversation(propertyId, hostId, guestId);
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        Message unread = new Message(conversation.getId(), hostId, "From the host");
        when(messageRepository.findAllByConversationIdAndSenderIdNotAndReadAtIsNull(conversation.getId(), guestId))
                .thenReturn(List.of(unread));

        service.markRead(guestId, conversation.getId());

        assertThat(unread.getReadAt()).isNotNull();
    }

    private MessageSentEvent capturePublishedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return (MessageSentEvent) captor.getValue();
    }
}
