package com.havyn.payments.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One payment attempt against a booking. {@code bookingId} is a plain UUID (not a JPA
 * association) — same reasoning as {@code Booking.propertyId}/{@code guestId}: a
 * financial record should stay stable and independently queryable, not entangled with
 * the booking module's object graph. There is a real FK at the DB layer
 * ({@code V6__payments.sql}) even though the Java side doesn't model it as one.
 */
@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_ref", nullable = false, length = 100)
    private String providerRef;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    protected Payment() {
        // JPA
    }

    public Payment(UUID bookingId, String provider, String providerRef, BigDecimal amount, String currency) {
        this.bookingId = bookingId;
        this.provider = provider;
        this.providerRef = providerRef;
        this.amount = amount;
        this.currency = currency;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void markSucceeded() {
        this.status = PaymentStatus.SUCCEEDED;
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public boolean isTerminal() {
        return status == PaymentStatus.SUCCEEDED || status == PaymentStatus.FAILED;
    }
}
