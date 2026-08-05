package com.havyn.properties.web;

import com.havyn.properties.domain.Availability;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AvailabilityDay(LocalDate date, boolean blocked, BigDecimal priceOverride) {

    public static AvailabilityDay from(Availability availability) {
        return new AvailabilityDay(availability.getDate(), availability.isBlocked(), availability.getPriceOverride());
    }
}
