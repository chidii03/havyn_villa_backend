package com.havyn.support.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "support_ticket")
public class SupportTicket extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "booking_reference_id", length = 20)
    private String bookingReferenceId;

    @Column(name = "summary", nullable = false)
    private String summary;

    @Column(name = "source_message", nullable = false)
    private String sourceMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupportTicketStatus status = SupportTicketStatus.OPEN;

    protected SupportTicket() {
        // JPA
    }

    public SupportTicket(UUID userId, String bookingReferenceId, String summary, String sourceMessage) {
        this.userId = userId;
        this.bookingReferenceId = bookingReferenceId;
        this.summary = summary;
        this.sourceMessage = sourceMessage;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBookingReferenceId() {
        return bookingReferenceId;
    }

    public String getSummary() {
        return summary;
    }

    public String getSourceMessage() {
        return sourceMessage;
    }

    public SupportTicketStatus getStatus() {
        return status;
    }
}
