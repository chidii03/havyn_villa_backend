package com.havyn.admin.web;

import com.havyn.admin.domain.Dispute;
import java.time.Instant;
import java.util.UUID;

public record DisputeSummary(
        UUID id,
        UUID bookingId,
        UUID raisedBy,
        String reason,
        String status,
        String resolutionNotes,
        UUID resolvedBy,
        Instant resolvedAt,
        Instant createdAt) {

    public static DisputeSummary from(Dispute dispute) {
        return new DisputeSummary(
                dispute.getId(), dispute.getBookingId(), dispute.getRaisedBy(), dispute.getReason(), dispute.getStatus().name(),
                dispute.getResolutionNotes(), dispute.getResolvedBy(), dispute.getResolvedAt(), dispute.getCreatedAt());
    }
}
