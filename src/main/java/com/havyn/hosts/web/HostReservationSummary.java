package com.havyn.hosts.web;

import com.havyn.booking.domain.Booking;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HostReservationSummary(
        UUID id,
        UUID propertyId,
        String propertyTitle,
        UUID guestId,
        String guestName,
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        int guestsCount,
        BigDecimal grandTotal,
        String currency,
        String status,
        Instant createdAt) {

    public static HostReservationSummary from(Booking booking, String propertyTitle, String guestName) {
        return new HostReservationSummary(
                booking.getId(), booking.getPropertyId(), propertyTitle, booking.getGuestId(), guestName, booking.getCheckIn(),
                booking.getCheckOut(), booking.getNights(), booking.getGuestsCount(), booking.getGrandTotal(), booking.getCurrency(),
                booking.getStatus().name(), booking.getCreatedAt());
    }
}
