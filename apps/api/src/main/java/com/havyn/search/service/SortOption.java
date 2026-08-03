package com.havyn.search.service;

import com.havyn.common.error.BadRequestException;

/** Default is {@link #NEWEST} — every listing starts at rating 0, so sorting by rating first would just be createdAt in disguise until reviews exist (prompt 15). */
public enum SortOption {
    PRICE_ASC,
    PRICE_DESC,
    RATING_DESC,
    NEWEST;

    public static SortOption fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return NEWEST;
        }
        try {
            return SortOption.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("INVALID_SORT", "Unknown sort option: " + raw);
        }
    }
}
