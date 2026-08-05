package com.havyn.properties.web;

import com.havyn.properties.domain.Property;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record PropertySummary(
        UUID id,
        String title,
        String city,
        String state,
        String country,
        BigDecimal lat,
        BigDecimal lng,
        String propertyType,
        String currency,
        BigDecimal basePrice,
        int capacity,
        int bedrooms,
        BigDecimal ratingAvg,
        int ratingCount,
        String status) {

    public static PropertySummary from(Property property) {
        return new PropertySummary(
                property.getId(),
                property.getTitle(),
                property.getCity(),
                property.getState(),
                property.getCountry(),
                property.getLat(),
                property.getLng(),
                property.getType().getCode(),
                property.getCurrency(),
                property.getBasePrice(),
                property.getCapacity(),
                property.getBedrooms(),
                property.getRatingAvg(),
                property.getRatingCount(),
                property.getStatus().name());
    }

    /**
     * project-docs/roadmap/02-launch-checklist.md#3 — same reasoning as {@code
     * PropertyDetail#withApproximateLocation}. Deliberately NOT applied inside {@link
     * #from}: this type is also used for a host's own listings ({@code
     * HostListingController}) and admin's moderation view ({@code
     * AdminPropertyController}), both of which need exact coordinates. Only the truly
     * public catalog ({@code PropertyController#list}) opts into this.
     */
    public PropertySummary withApproximateLocation() {
        return new PropertySummary(
                id, title, city, state, country, approximate(lat), approximate(lng), propertyType, currency,
                basePrice, capacity, bedrooms, ratingAvg, ratingCount, status);
    }

    private static BigDecimal approximate(BigDecimal coordinate) {
        return coordinate == null ? null : coordinate.setScale(2, RoundingMode.HALF_UP);
    }
}
