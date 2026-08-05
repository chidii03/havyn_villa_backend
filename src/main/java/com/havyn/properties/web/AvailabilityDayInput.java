package com.havyn.properties.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code blocked} left null means "leave the existing block state unchanged" (defaults to unblocked for a new day). */
public record AvailabilityDayInput(
        @NotNull LocalDate date,
        Boolean blocked,
        @PositiveOrZero BigDecimal priceOverride) {
}
