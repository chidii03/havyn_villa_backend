package com.havyn.properties.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A per-date exception to a property's default-available calendar. No row for a date
 * means "available at the base price" — rows only exist for blocks/overrides. {@code
 * bookingId} is a plain UUID (no FK — {@code booking} lives in a different module) set
 * by {@code booking.service.BookingService} on create/cancel/hold-expiry (prompt 12).
 */
@Entity
@Table(name = "availability")
public class Availability extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "is_blocked", nullable = false)
    private boolean blocked;

    @Column(name = "price_override")
    private BigDecimal priceOverride;

    @Column(name = "booking_id")
    private UUID bookingId;

    protected Availability() {
        // JPA
    }

    public Availability(Property property, LocalDate date, boolean blocked, BigDecimal priceOverride) {
        this.property = property;
        this.date = date;
        this.blocked = blocked;
        this.priceOverride = priceOverride;
    }

    public Property getProperty() {
        return property;
    }

    public LocalDate getDate() {
        return date;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public BigDecimal getPriceOverride() {
        return priceOverride;
    }

    public void setPriceOverride(BigDecimal priceOverride) {
        this.priceOverride = priceOverride;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    /** Set by prompt 12's BookingService on create/cancel/hold-expiry — this column was reserved for exactly this. */
    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }
}
