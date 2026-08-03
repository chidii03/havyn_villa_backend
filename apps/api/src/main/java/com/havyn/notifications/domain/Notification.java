package com.havyn.notifications.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An in-app notification record — see project-docs/prompts/16-messaging-notifications.md.
 * {@code userId} has a real FK (ON DELETE CASCADE — deleting a user removes their
 * notifications, same reasoning as {@code favorite.user_id}). {@code linkId} is a plain,
 * untyped UUID (e.g. a bookingId or conversationId) for client-side deep-linking,
 * deliberately not a jsonb payload — see database/01-data-model.md's session 7 notes on
 * why this schema doesn't introduce jsonb for a single column ahead of a real convention.
 */
@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "link_id")
    private UUID linkId;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
        // JPA
    }

    public Notification(UUID userId, NotificationType type, String title, String body, UUID linkId) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.linkId = linkId;
    }

    public void markRead(Instant when) {
        this.readAt = when;
    }

    public UUID getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public UUID getLinkId() {
        return linkId;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
