package com.havyn.messaging.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A single message within a {@link Conversation} — see
 * project-docs/prompts/16-messaging-notifications.md. {@code conversationId} is a real
 * FK (ON DELETE CASCADE — a message is meaningless without its conversation, same
 * category as {@code property_media.property_id}). {@code senderId} is a plain UUID
 * column, same historical-record reasoning as {@code Conversation.hostId}/{@code guestId}.
 */
@Entity
@Table(name = "message")
public class Message extends BaseEntity {

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "read_at")
    private Instant readAt;

    protected Message() {
        // JPA
    }

    public Message(UUID conversationId, UUID senderId, String body) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.body = body;
    }

    public void markRead(Instant when) {
        this.readAt = when;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public String getBody() {
        return body;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
