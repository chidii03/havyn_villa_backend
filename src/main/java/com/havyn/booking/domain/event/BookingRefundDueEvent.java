package com.havyn.booking.domain.event;

import com.havyn.common.events.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when cancelling a {@code CONFIRMED} booking computes a non-zero refund —
 * see {@code BookingService#cancel}. The {@code payments} module (prompt 13) listens
 * for this to call the real payment provider's refund API, so booking/ never needs to
 * know payments/ exists — same cross-module pattern as {@code PropertyChangedEvent}
 * (prompt 10/11).
 */
public record BookingRefundDueEvent(UUID bookingId, BigDecimal refundAmount, Instant occurredAt) implements DomainEvent {

    public static BookingRefundDueEvent of(UUID bookingId, BigDecimal refundAmount) {
        return new BookingRefundDueEvent(bookingId, refundAmount, Instant.now());
    }
}
