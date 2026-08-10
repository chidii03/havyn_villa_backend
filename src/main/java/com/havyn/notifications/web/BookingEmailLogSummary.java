package com.havyn.notifications.web;

import com.havyn.notifications.domain.BookingEmailLog;
import java.time.Instant;
import java.util.UUID;

public record BookingEmailLogSummary(
        UUID id,
        UUID bookingId,
        String bookingReferenceId,
        String recipientEmail,
        String status,
        String failureReason,
        int retryAttempts,
        Instant createdAt) {

    public static BookingEmailLogSummary from(BookingEmailLog log) {
        return new BookingEmailLogSummary(
                log.getId(),
                log.getBookingId(),
                log.getBookingReferenceId(),
                log.getRecipientEmail(),
                log.getStatus().name(),
                log.getFailureReason(),
                log.getRetryAttempts(),
                log.getCreatedAt());
    }
}
