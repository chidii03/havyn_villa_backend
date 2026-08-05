package com.havyn.admin.web;

import com.havyn.admin.domain.VerificationRequest;
import java.time.Instant;
import java.util.UUID;

public record VerificationRequestSummary(
        UUID id,
        UUID userId,
        String documentUrl,
        String notes,
        String status,
        String reviewNotes,
        UUID reviewedBy,
        Instant reviewedAt,
        Instant createdAt) {

    public static VerificationRequestSummary from(VerificationRequest request) {
        return new VerificationRequestSummary(
                request.getId(), request.getUserId(), request.getDocumentUrl(), request.getNotes(), request.getStatus().name(),
                request.getReviewNotes(), request.getReviewedBy(), request.getReviewedAt(), request.getCreatedAt());
    }
}
