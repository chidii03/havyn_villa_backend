package com.havyn.booking.domain.event;

import com.havyn.common.events.DomainEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published when a payment webhook moves a booking {@code PENDING -> CONFIRMED} — see
 * {@code BookingService#confirmPayment}. The {@code notifications} module (prompt 16)
 * listens for this to notify the guest, so booking/ never needs to know notifications/
 * exists — same cross-module pattern as {@code PropertyChangedEvent}/
 * {@code BookingRefundDueEvent}.
 */
public record BookingConfirmedEvent(UUID bookingId, UUID guestId, UUID propertyId, LocalDate checkIn, LocalDate checkOut, Instant occurredAt)
        implements DomainEvent {

    public static BookingConfirmedEvent of(UUID bookingId, UUID guestId, UUID propertyId, LocalDate checkIn, LocalDate checkOut) {
        return new BookingConfirmedEvent(bookingId, guestId, propertyId, checkIn, checkOut, Instant.now());
    }
}
