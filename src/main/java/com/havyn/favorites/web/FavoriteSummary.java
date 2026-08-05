package com.havyn.favorites.web;

import com.havyn.favorites.domain.Favorite;
import java.time.Instant;
import java.util.UUID;

public record FavoriteSummary(UUID propertyId, Instant createdAt) {

    public static FavoriteSummary from(Favorite favorite) {
        return new FavoriteSummary(favorite.getPropertyId(), favorite.getCreatedAt());
    }
}
