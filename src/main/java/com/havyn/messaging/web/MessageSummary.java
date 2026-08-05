package com.havyn.messaging.web;

import com.havyn.messaging.domain.Message;
import java.time.Instant;
import java.util.UUID;

public record MessageSummary(UUID id, UUID conversationId, UUID senderId, String body, Instant readAt, Instant createdAt) {

    public static MessageSummary from(Message message) {
        return new MessageSummary(
                message.getId(), message.getConversationId(), message.getSenderId(), message.getBody(), message.getReadAt(),
                message.getCreatedAt());
    }
}
