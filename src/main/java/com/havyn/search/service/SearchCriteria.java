package com.havyn.search.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;

/**
 * Normalized/validated search filters. Fields are canonicalized in the compact
 * constructor (trimmed/lowercased/sorted) so two logically-identical requests always
 * produce the same {@code toString()} — used verbatim as the Redis cache key material
 * by {@code SearchCacheService}.
 */
public record SearchCriteria(
        String destination,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer guests,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String typeCode,
        Integer bedrooms,
        Set<String> amenityCodes,
        BigDecimal minRating,
        SortOption sort) {

    public SearchCriteria {
        destination = (destination == null || destination.isBlank()) ? null : destination.trim().toLowerCase();
        typeCode = (typeCode == null || typeCode.isBlank()) ? null : typeCode.trim().toUpperCase();
        amenityCodes = (amenityCodes == null || amenityCodes.isEmpty())
                ? Set.of()
                : new TreeSet<>(amenityCodes.stream().map(String::toUpperCase).toList());
        sort = sort == null ? SortOption.NEWEST : sort;
    }
}
