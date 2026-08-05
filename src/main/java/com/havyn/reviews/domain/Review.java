package com.havyn.reviews.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A guest's review of a completed stay — see project-docs/prompts/15-reviews-favorites.md.
 * {@code propertyId}/{@code guestId} are plain UUID columns (not JPA associations),
 * mirroring {@code Booking}'s rationale: a review should stay a readable historical
 * record even if the referenced property/profile later changes. {@code bookingId} is a
 * real, unique FK — a review without a genuine completed booking behind it isn't a valid
 * state, mirroring {@code Payment.bookingId}'s rationale.
 */
@Entity
@Table(name = "review")
public class Review extends BaseEntity {

    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment")
    private String comment;

    protected Review() {
        // JPA
    }

    public Review(UUID bookingId, UUID propertyId, UUID guestId, int rating, String comment) {
        this.bookingId = bookingId;
        this.propertyId = propertyId;
        this.guestId = guestId;
        this.rating = rating;
        this.comment = comment;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getGuestId() {
        return guestId;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }
}
