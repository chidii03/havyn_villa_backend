package com.havyn.booking.web;

import com.havyn.properties.domain.Property;
import java.util.UUID;

/**
 * A minimal, always-resolvable property snapshot for a booking's display — looked up
 * directly by ID (not through {@code PropertyService.getActive()}), so a guest's trip
 * history stays readable even if the listing is later suspended.
 */
public record BookingPropertySummary(UUID id, String title, String city, String state, String country) {

    public static BookingPropertySummary from(Property property) {
        return new BookingPropertySummary(property.getId(), property.getTitle(), property.getCity(), property.getState(), property.getCountry());
    }

    public static BookingPropertySummary unavailable(UUID propertyId) {
        return new BookingPropertySummary(propertyId, "Listing no longer available", "", "", "");
    }
}
