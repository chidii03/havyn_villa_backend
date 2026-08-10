package com.havyn.notifications.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "booking_email_log")
public class BookingEmailLog extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "booking_reference_id", length = 20)
    private String bookingReferenceId;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingEmailStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "retry_attempts", nullable = false)
    private int retryAttempts;

    protected BookingEmailLog() {
        // JPA
    }

    public BookingEmailLog(UUID bookingId, String bookingReferenceId, String recipientEmail) {
        this.bookingId = bookingId;
        this.bookingReferenceId = bookingReferenceId;
        this.recipientEmail = recipientEmail;
        this.status = BookingEmailStatus.ATTEMPTED;
    }

    public void markSuccessful(int retryAttempts) {
        this.status = BookingEmailStatus.SUCCESSFUL;
        this.retryAttempts = retryAttempts;
        this.failureReason = null;
    }

    public void markFailed(int retryAttempts, String failureReason) {
        this.status = BookingEmailStatus.FAILED;
        this.retryAttempts = retryAttempts;
        this.failureReason = failureReason;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public String getBookingReferenceId() {
        return bookingReferenceId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public BookingEmailStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }
}
