package com.havyn.messaging.service;

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
import com.havyn.properties.repo.PropertyRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guest↔host messaging — see project-docs/prompts/16-messaging-notifications.md.
 * Object-level authorization (only conversation participants may read/write) is
 * enforced here, mirroring {@code BookingService}/{@code PropertyService}'s pattern.
 * One conversation per (property, guest) pair — {@link #startOrContinue} finds an
 * existing thread or creates one, rather than ever spawning parallel threads for the
 * same pairing.
 */
@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final PropertyRepository propertyRepository;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ConversationService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            PropertyRepository propertyRepository,
            BookingRepository bookingRepository,
            ApplicationEventPublisher eventPublisher) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.propertyRepository = propertyRepository;
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Conversation startOrContinue(UUID guestId, UUID propertyId, UUID bookingId, String body) {
        Property property = propertyRepository.findById(propertyId).orElseThrow(() -> NotFoundException.of("Property", propertyId));
        if (property.getHostId().equals(guestId)) {
            throw new BadRequestException("HOST_CANNOT_MESSAGE_OWN_LISTING", "You cannot start a conversation on your own listing");
        }

        Conversation conversation = conversationRepository.findByPropertyIdAndGuestId(propertyId, guestId)
                .orElseGet(() -> conversationRepository.save(new Conversation(propertyId, property.getHostId(), guestId)));

        if (bookingId != null) {
            Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> NotFoundException.of("Booking", bookingId));
            if (!booking.getGuestId().equals(guestId) || !booking.getPropertyId().equals(propertyId)) {
                throw new BadRequestException("BOOKING_PROPERTY_MISMATCH", "This booking is not yours for this property");
            }
            conversation.linkBooking(bookingId);
        }

        appendMessage(conversation, guestId, body);
        return conversation;
    }

    @Transactional(readOnly = true)
    public Page<Conversation> listForUser(UUID userId, Pageable pageable) {
        return conversationRepository.findAllByHostIdOrGuestIdOrderByLastMessageAtDescCreatedAtDesc(userId, userId, pageable);
    }

    @Transactional(readOnly = true)
    public Conversation getParticipant(UUID userId, UUID conversationId) {
        return findParticipant(userId, conversationId);
    }

    @Transactional
    public Message sendMessage(UUID senderId, UUID conversationId, String body) {
        Conversation conversation = findParticipant(senderId, conversationId);
        return appendMessage(conversation, senderId, body);
    }

    @Transactional(readOnly = true)
    public Page<Message> listMessages(UUID userId, UUID conversationId, Pageable pageable) {
        findParticipant(userId, conversationId);
        return messageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversationId, pageable);
    }

    @Transactional
    public void markRead(UUID userId, UUID conversationId) {
        findParticipant(userId, conversationId);
        List<Message> unread = messageRepository.findAllByConversationIdAndSenderIdNotAndReadAtIsNull(conversationId, userId);
        Instant now = Instant.now();
        unread.forEach(message -> message.markRead(now));
    }

    private Message appendMessage(Conversation conversation, UUID senderId, String body) {
        Message message = messageRepository.save(new Message(conversation.getId(), senderId, body));
        conversation.touch(Instant.now());
        eventPublisher.publishEvent(MessageSentEvent.of(
                message.getId(), conversation.getId(), senderId, conversation.otherParticipant(senderId), conversation.getPropertyId()));
        return message;
    }

    private Conversation findParticipant(UUID userId, UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> NotFoundException.of("Conversation", conversationId));
        if (!conversation.isParticipant(userId)) {
            throw new ForbiddenException("You do not have access to this conversation");
        }
        return conversation;
    }
}
