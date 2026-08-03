package com.havyn.reviews.web;

import com.havyn.reviews.domain.Review;
import java.time.Instant;
import java.util.UUID;

public record ReviewSummary(UUID id, UUID propertyId, UUID guestId, int rating, String comment, Instant createdAt) {

    public static ReviewSummary from(Review review) {
        return new ReviewSummary(
                review.getId(), review.getPropertyId(), review.getGuestId(), review.getRating(), review.getComment(),
                review.getCreatedAt());
    }
}
