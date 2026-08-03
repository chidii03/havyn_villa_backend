package com.havyn.properties.web;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;

/** Partial update — every field is optional; a null field leaves the current value unchanged. */
public record UpdatePropertyRequest(
        String typeCode,
        @Size(max = 150) String title,
        String description,
        String address,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 100) String country,
        @Digits(integer = 3, fraction = 6) BigDecimal lat,
        @Digits(integer = 3, fraction = 6) BigDecimal lng,
        @Size(min = 3, max = 3) String currency,
        @PositiveOrZero BigDecimal basePrice,
        @Positive Integer capacity,
        @PositiveOrZero Integer bedrooms,
        @PositiveOrZero Integer beds,
        @PositiveOrZero BigDecimal bathrooms,
        @PositiveOrZero BigDecimal cleaningFee,
        @Min(0) @Max(100) BigDecimal serviceFeePct,
        String houseRules,
        String cancellationPolicy,
        Set<String> amenityCodes) {
}
