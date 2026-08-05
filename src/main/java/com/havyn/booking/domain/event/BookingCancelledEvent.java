package com.havyn.booking.domain.event;

import com.havyn.common.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published whenever {@code BookingService#cancel} moves a booking to {@code CANCELLED}
 * or {@code REFUNDED} — see that method's Javadoc. The {@code notifications} module
 * (prompt 16) listens for this to notify the guest of the status change; unlike
 * {@code BookingRefundDueEvent} (which only fires when a real refund is owed, for
 * {@code payments/} to act on), this fires for every cancellation so a notification
 * always goes out, refunded or not.
 */
public record BookingCancelledEvent(UUID bookingId, UUID guestId, UUID propertyId, boolean refunded, Instant occurredAt)
        implements DomainEvent {

    public static BookingCancelledEvent of(UUID bookingId, UUID guestId, UUID propertyId, boolean refunded) {
        return new BookingCancelledEvent(bookingId, guestId, propertyId, refunded, Instant.now());
    }
}
