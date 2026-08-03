package com.havyn.messaging.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A guest↔host message thread about a property, optionally linked to a real booking —
 * see project-docs/prompts/16-messaging-notifications.md. One conversation per
 * (property, guest) pair (enforced by a DB unique constraint) — a guest inquiring about
 * a listing before booking and a guest messaging about their confirmed stay share the
 * same thread, {@link #linkBooking} just attaches the booking once one exists.
 *
 * {@code propertyId}/{@code hostId}/{@code guestId} are plain UUID columns (not JPA
 * associations), mirroring {@code Booking}'s rationale: a conversation should stay a
 * readable historical record even if the referenced property/profile later changes.
 */
@Entity
@Table(name = "conversation")
public class Conversation extends BaseEntity {

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    protected Conversation() {
        // JPA
    }

    public Conversation(UUID propertyId, UUID hostId, UUID guestId) {
        this.propertyId = propertyId;
        this.hostId = hostId;
        this.guestId = guestId;
    }

    public boolean isParticipant(UUID userId) {
        return hostId.equals(userId) || guestId.equals(userId);
    }

    public UUID otherParticipant(UUID userId) {
        return hostId.equals(userId) ? guestId : hostId;
    }

    public void linkBooking(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public void touch(Instant when) {
        this.lastMessageAt = when;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getHostId() {
        return hostId;
    }

    public UUID getGuestId() {
        return guestId;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }
}
