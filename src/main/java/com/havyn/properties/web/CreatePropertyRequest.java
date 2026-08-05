package com.havyn.properties.web;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;

public record CreatePropertyRequest(
        @NotBlank String typeCode,
        @NotBlank @Size(max = 150) String title,
        @NotBlank String description,
        @NotBlank String address,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String state,
        @NotBlank @Size(max = 100) String country,
        @Digits(integer = 3, fraction = 6) BigDecimal lat,
        @Digits(integer = 3, fraction = 6) BigDecimal lng,
        @Size(min = 3, max = 3) String currency,
        @NotNull @PositiveOrZero BigDecimal basePrice,
        @NotNull @Positive Integer capacity,
        @NotNull @PositiveOrZero Integer bedrooms,
        @NotNull @PositiveOrZero Integer beds,
        @NotNull @PositiveOrZero BigDecimal bathrooms,
        @PositiveOrZero BigDecimal cleaningFee,
        @Min(0) @Max(100) BigDecimal serviceFeePct,
        String houseRules,
        String cancellationPolicy,
        Set<String> amenityCodes) {
}
