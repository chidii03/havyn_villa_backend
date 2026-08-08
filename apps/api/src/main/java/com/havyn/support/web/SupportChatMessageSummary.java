package com.havyn.support.web;

import com.havyn.support.domain.SupportChatMessage;
import java.time.Instant;
import java.util.UUID;

public record SupportChatMessageSummary(UUID id, String role, String body, Instant createdAt) {

    public static SupportChatMessageSummary from(SupportChatMessage message) {
        return new SupportChatMessageSummary(message.getId(), message.getRole().name(), message.getBody(), message.getCreatedAt());
    }

    public static SupportChatMessageSummary greeting() {
        return new SupportChatMessageSummary(null, "ASSISTANT", "Hi, I'm Havyn Villa's AI Assistant. How can I help you today?", null);
    }
}
