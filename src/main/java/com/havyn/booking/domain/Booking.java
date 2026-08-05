package com.havyn.booking.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A guest's reservation — see project-docs/database/01-data-model.md#3.
 * {@code propertyId}/{@code guestId} are plain UUID columns (not JPA associations,
 * unlike {@code Property.type}) — a booking should stay a readable historical record
 * even if the referenced property is later suspended or the guest's profile changes,
 * so lazily-loaded associations that could 404/complicate things aren't worth it here.
 */
@Entity
@Table(name = "booking")
public class Booking extends BaseEntity {

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "nights", nullable = false)
    private int nights;

    @Column(name = "guests_count", nullable = false)
    private int guestsCount;

    @Column(name = "base_total", nullable = false)
    private BigDecimal baseTotal;

    @Column(name = "cleaning_fee", nullable = false)
    private BigDecimal cleaningFee;

    @Column(name = "service_fee", nullable = false)
    private BigDecimal serviceFee;

    @Column(name = "discount_total", nullable = false)
    private BigDecimal discountTotal;

    @Column(name = "tax_total", nullable = false)
    private BigDecimal taxTotal;

    @Column(name = "commission_amount", nullable = false)
    private BigDecimal commissionAmount;

    @Column(name = "grand_total", nullable = false)
    private BigDecimal grandTotal;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    protected Booking() {
        // JPA
    }

    public Booking(
            UUID propertyId,
            UUID guestId,
            LocalDate checkIn,
            LocalDate checkOut,
            int nights,
            int guestsCount,
            BigDecimal baseTotal,
            BigDecimal cleaningFee,
            BigDecimal serviceFee,
            BigDecimal discountTotal,
            BigDecimal taxTotal,
            BigDecimal commissionAmount,
            BigDecimal grandTotal,
            String currency) {
        this.propertyId = propertyId;
        this.guestId = guestId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.nights = nights;
        this.guestsCount = guestsCount;
        this.baseTotal = baseTotal;
        this.cleaningFee = cleaningFee;
        this.serviceFee = serviceFee;
        this.discountTotal = discountTotal;
        this.taxTotal = taxTotal;
        this.commissionAmount = commissionAmount;
        this.grandTotal = grandTotal;
        this.currency = currency;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getGuestId() {
        return guestId;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    public int getNights() {
        return nights;
    }

    public int getGuestsCount() {
        return guestsCount;
    }

    public BigDecimal getBaseTotal() {
        return baseTotal;
    }

    public BigDecimal getCleaningFee() {
        return cleaningFee;
    }

    public BigDecimal getServiceFee() {
        return serviceFee;
    }

    public BigDecimal getDiscountTotal() {
        return discountTotal;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public BigDecimal getCommissionAmount() {
        return commissionAmount;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public String getCurrency() {
        return currency;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void transitionTo(BookingStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Cannot transition booking from " + status + " to " + target);
        }
        this.status = target;
    }

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public void setHoldExpiresAt(Instant holdExpiresAt) {
        this.holdExpiresAt = holdExpiresAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
