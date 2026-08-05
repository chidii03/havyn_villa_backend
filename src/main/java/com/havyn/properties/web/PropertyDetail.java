package com.havyn.properties.web;

import com.havyn.amenities.web.AmenitySummary;
import com.havyn.properties.domain.Property;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PropertyDetail(
        UUID id,
        UUID hostId,
        PropertyTypeSummary type,
        String title,
        String description,
        String address,
        String city,
        String state,
        String country,
        BigDecimal lat,
        BigDecimal lng,
        String currency,
        BigDecimal basePrice,
        int capacity,
        int bedrooms,
        int beds,
        BigDecimal bathrooms,
        BigDecimal cleaningFee,
        BigDecimal serviceFeePct,
        String houseRules,
        String cancellationPolicy,
        String status,
        BigDecimal ratingAvg,
        int ratingCount,
        List<AmenitySummary> amenities,
        Instant createdAt,
        Instant updatedAt,
        List<String> photoUrls) {

    /** No photos wired in — used by admin/host-management flows that don't load media alongside the property. */
    public static PropertyDetail from(Property property) {
        return from(property, List.of());
    }

    public static PropertyDetail from(Property property, List<String> photoUrls) {
        return new PropertyDetail(
                property.getId(),
                property.getHostId(),
                PropertyTypeSummary.from(property.getType()),
                property.getTitle(),
                property.getDescription(),
                property.getAddress(),
                property.getCity(),
                property.getState(),
                property.getCountry(),
                property.getLat(),
                property.getLng(),
                property.getCurrency(),
                property.getBasePrice(),
                property.getCapacity(),
                property.getBedrooms(),
                property.getBeds(),
                property.getBathrooms(),
                property.getCleaningFee(),
                property.getServiceFeePct(),
                property.getHouseRules(),
                property.getCancellationPolicy(),
                property.getStatus().name(),
                property.getRatingAvg(),
                property.getRatingCount(),
                property.getAmenities().stream().map(AmenitySummary::from).toList(),
                property.getCreatedAt(),
                property.getUpdatedAt(),
                photoUrls);
    }

    /**
     * project-docs/roadmap/02-launch-checklist.md#3: the public, unauthenticated view
     * of this record used to include the exact street address and precise
     * coordinates of every listing, pre-booking. Callers without a privileged reason
     * to know exactly where a property is (see {@code PropertyService#getActiveDetail}
     * for who qualifies) get this instead — {@code address} withheld, {@code lat}/
     * {@code lng} rounded to 2 decimal places (~1.1km at the equator), matching
     * architecture/04-integrations.md's original "show a circle/area, not the exact
     * pin, until booked" intent, now actually enforced server-side.
     */
    public PropertyDetail withApproximateLocation() {
        return new PropertyDetail(
                id, hostId, type, title, description, null, city, state, country,
                approximate(lat), approximate(lng), currency, basePrice, capacity, bedrooms, beds, bathrooms,
                cleaningFee, serviceFeePct, houseRules, cancellationPolicy, status, ratingAvg, ratingCount,
                amenities, createdAt, updatedAt, photoUrls);
    }

    private static BigDecimal approximate(BigDecimal coordinate) {
        return coordinate == null ? null : coordinate.setScale(2, RoundingMode.HALF_UP);
    }
}
