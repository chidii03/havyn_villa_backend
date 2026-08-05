package com.havyn.booking.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code expectedTotal} is the grand total the client displayed from its last {@code
 * POST /quote} call — the server recomputes independently and rejects the request
 * (409 {@code PRICE_CHANGED}) if they don't match, per US-B1/US-B2. It is never
 * trusted as the authoritative price.
 */
public record CreateBookingRequest(
        @NotNull UUID propertyId,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @NotNull @Positive Integer guests,
        @NotNull @PositiveOrZero BigDecimal expectedTotal) {
}
