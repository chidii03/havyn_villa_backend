package com.havyn.support.web;

import com.havyn.support.domain.SupportTicket;
import java.time.Instant;
import java.util.UUID;

public record SupportTicketSummary(
        UUID id,
        UUID userId,
        String bookingReferenceId,
        String summary,
        String sourceMessage,
        String status,
        Instant createdAt) {

    public static SupportTicketSummary from(SupportTicket ticket) {
        return new SupportTicketSummary(
                ticket.getId(),
                ticket.getUserId(),
                ticket.getBookingReferenceId(),
                ticket.getSummary(),
                ticket.getSourceMessage(),
                ticket.getStatus().name(),
                ticket.getCreatedAt());
    }
}
