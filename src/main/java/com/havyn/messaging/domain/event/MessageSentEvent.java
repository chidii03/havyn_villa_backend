package com.havyn.messaging.domain.event;

import com.havyn.common.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published whenever a message is appended to a conversation — see
 * {@code ConversationService#appendMessage}. The {@code notifications} module (prompt
 * 16) listens for this to notify the other participant, so messaging/ never needs to
 * know notifications/ exists — same cross-module pattern as
 * {@code BookingConfirmedEvent}/{@code BookingCancelledEvent}.
 */
public record MessageSentEvent(UUID messageId, UUID conversationId, UUID senderId, UUID recipientId, UUID propertyId, Instant occurredAt)
        implements DomainEvent {

    public static MessageSentEvent of(UUID messageId, UUID conversationId, UUID senderId, UUID recipientId, UUID propertyId) {
        return new MessageSentEvent(messageId, conversationId, senderId, recipientId, propertyId, Instant.now());
    }
}
