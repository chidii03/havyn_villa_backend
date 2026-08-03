package com.havyn.admin.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A guest or host dispute over a booking — see project-docs/prompts/18-admin-platform.md.
 * {@code bookingId} is a real FK (a dispute without a genuine booking behind it isn't
 * a valid state, same reasoning as {@code payment.booking_id}/{@code review.booking_id}).
 * {@code raisedBy} is a plain UUID column — could be either the booking's guest or the
 * property's host (validated at raise-time in {@code DisputeService}, not by a DB
 * constraint), same historical-record reasoning as {@code booking.guest_id}.
 */
@Entity
@Table(name = "dispute")
public class Dispute extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "raised_by", nullable = false)
    private UUID raisedBy;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DisputeStatus status = DisputeStatus.OPEN;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Dispute() {
        // JPA
    }

    public Dispute(UUID bookingId, UUID raisedBy, String reason) {
        this.bookingId = bookingId;
        this.raisedBy = raisedBy;
        this.reason = reason;
    }

    public void resolve(UUID adminId, String resolutionNotes, Instant when) {
        this.status = DisputeStatus.RESOLVED;
        this.resolutionNotes = resolutionNotes;
        this.resolvedBy = adminId;
        this.resolvedAt = when;
    }

    public void dismiss(UUID adminId, String resolutionNotes, Instant when) {
        this.status = DisputeStatus.DISMISSED;
        this.resolutionNotes = resolutionNotes;
        this.resolvedBy = adminId;
        this.resolvedAt = when;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getRaisedBy() {
        return raisedBy;
    }

    public String getReason() {
        return reason;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
