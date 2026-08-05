package com.havyn.booking.web;

import com.havyn.booking.domain.Booking;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Guest-facing booking detail — like {@link QuoteResponse}, no commission line. */
public record BookingDetail(
        UUID id,
        BookingPropertySummary property,
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        int guests,
        BigDecimal baseTotal,
        BigDecimal cleaningFee,
        BigDecimal serviceFee,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        String currency,
        String status,
        Instant holdExpiresAt,
        Instant createdAt) {

    public static BookingDetail from(Booking booking, BookingPropertySummary property) {
        return new BookingDetail(
                booking.getId(),
                property,
                booking.getCheckIn(),
                booking.getCheckOut(),
                booking.getNights(),
                booking.getGuestsCount(),
                booking.getBaseTotal(),
                booking.getCleaningFee(),
                booking.getServiceFee(),
                booking.getDiscountTotal(),
                booking.getTaxTotal(),
                booking.getGrandTotal(),
                booking.getCurrency(),
                booking.getStatus().name(),
                booking.getHoldExpiresAt(),
                booking.getCreatedAt());
    }
}
