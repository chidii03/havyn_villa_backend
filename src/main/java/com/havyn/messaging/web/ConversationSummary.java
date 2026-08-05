package com.havyn.messaging.web;

import com.havyn.messaging.domain.Conversation;
import java.time.Instant;
import java.util.UUID;

public record ConversationSummary(
        UUID id, UUID propertyId, String propertyTitle, UUID hostId, UUID guestId, UUID bookingId, Instant lastMessageAt,
        Instant createdAt) {

    public static ConversationSummary from(Conversation conversation, String propertyTitle) {
        return new ConversationSummary(
                conversation.getId(), conversation.getPropertyId(), propertyTitle, conversation.getHostId(), conversation.getGuestId(),
                conversation.getBookingId(), conversation.getLastMessageAt(), conversation.getCreatedAt());
    }
}
