package com.havyn.search.web;

import com.havyn.properties.domain.Property;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/** One result row — includes lat/lng so the frontend map view can place a price pin per prompt 11's deliverables. */
public record SearchResultItem(
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
        BigDecimal bathrooms,
        BigDecimal ratingAvg,
        int ratingCount,
        List<String> photoUrls) {

    public static SearchResultItem from(Property property, List<String> photoUrls) {
        return new SearchResultItem(
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
                property.getBathrooms(),
                property.getRatingAvg(),
                property.getRatingCount(),
                photoUrls);
    }

    /**
     * project-docs/roadmap/02-launch-checklist.md#3 — same reasoning as {@code
     * PropertyDetail#withApproximateLocation}. {@code GET /search} is always public
     * with no privileged variant (a host/admin needing exact coordinates for their own
     * listings has other endpoints for that), so {@code SearchService} applies this
     * unconditionally, before the result page is cached.
     */
    public SearchResultItem withApproximateLocation() {
        return new SearchResultItem(
                id, title, city, state, country, approximate(lat), approximate(lng), propertyType, currency,
                basePrice, capacity, bedrooms, bathrooms, ratingAvg, ratingCount, photoUrls);
    }

    private static BigDecimal approximate(BigDecimal coordinate) {
        return coordinate == null ? null : coordinate.setScale(2, RoundingMode.HALF_UP);
    }
}
