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
        BigDecimal approximateLat = approximate(lat);
        BigDecimal approximateLng = approximate(lng);
        if (approximateLat == null || approximateLng == null) {
            ApproximateLocation fallback = ApproximateLocation.forPlace(city, state);
            if (fallback != null) {
                approximateLat = fallback.lat();
                approximateLng = fallback.lng();
            }
        }
        return new SearchResultItem(
                id, title, city, state, country, approximateLat, approximateLng, propertyType, currency,
                basePrice, capacity, bedrooms, bathrooms, ratingAvg, ratingCount, photoUrls);
    }

    private static BigDecimal approximate(BigDecimal coordinate) {
        return coordinate == null ? null : coordinate.setScale(2, RoundingMode.HALF_UP);
    }

    private record ApproximateLocation(BigDecimal lat, BigDecimal lng) {
        private static ApproximateLocation forPlace(String city, String state) {
            String place = normalize(city) + "|" + normalize(state);
            return switch (place) {
                case "uyo|akwa ibom" -> of("5.04", "7.91");
                case "lagos|lagos" -> of("6.52", "3.38");
                case "ikeja|lagos" -> of("6.60", "3.35");
                case "lekki|lagos" -> of("6.47", "3.59");
                case "abuja|fct", "abuja|federal capital territory" -> of("9.08", "7.49");
                case "port harcourt|rivers" -> of("4.82", "7.05");
                default -> switch (normalize(state)) {
                    case "akwa ibom" -> of("5.04", "7.91");
                    case "lagos" -> of("6.52", "3.38");
                    case "fct", "federal capital territory" -> of("9.08", "7.49");
                    case "rivers" -> of("4.82", "7.05");
                    default -> null;
                };
            };
        }

        private static ApproximateLocation of(String lat, String lng) {
            return new ApproximateLocation(new BigDecimal(lat), new BigDecimal(lng));
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim().toLowerCase();
        }
    }
}
