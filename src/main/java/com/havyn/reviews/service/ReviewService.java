package com.havyn.reviews.service;

import com.havyn.booking.domain.Booking;
import com.havyn.booking.domain.BookingStatus;
import com.havyn.booking.repo.BookingRepository;
import com.havyn.common.error.BadRequestException;
import com.havyn.common.error.ConflictException;
import com.havyn.common.error.ForbiddenException;
import com.havyn.common.error.NotFoundException;
import com.havyn.properties.domain.Property;
import com.havyn.properties.repo.PropertyRepository;
import com.havyn.reviews.domain.Review;
import com.havyn.reviews.repo.ReviewRepository;
import com.havyn.reviews.web.CreateReviewRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Eligibility-gated reviews — see project-docs/prompts/15-reviews-favorites.md. Only the
 * guest of a {@code COMPLETED} booking for the exact property being reviewed may post
 * one, and only once per booking (enforced here, on top of {@code review.booking_id}'s
 * DB-level UNIQUE constraint). The aggregate rating on {@code Property} is recomputed
 * from source (AVG/COUNT over all reviews) rather than incrementally mutated, to avoid
 * floating-point drift — there's no edit/delete-review capability in this prompt's scope
 * to make that a performance concern.
 *
 * Reads {@code booking/}'s repository directly (read-only, never writes a booking row)
 * to check eligibility — the same cross-module read pattern {@code BookingService}
 * itself already uses against {@code properties/}'s repositories.
 *
 * Known structural gap, not fixed here (out of this prompt's file scope — see
 * backend/02-domain-modules.md's prompt 15 notes): nothing in {@code booking/} ever
 * transitions a booking to {@code COMPLETED} yet, so this eligibility check is real and
 * correct but currently unreachable through any live product flow.
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;

    public ReviewService(ReviewRepository reviewRepository, BookingRepository bookingRepository, PropertyRepository propertyRepository) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
    }

    @Transactional
    public Review create(UUID guestId, UUID propertyId, CreateReviewRequest request) {
        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> NotFoundException.of("Booking", request.bookingId()));
        if (!booking.getGuestId().equals(guestId)) {
            throw new ForbiddenException("You do not have access to this booking");
        }
        if (!booking.getPropertyId().equals(propertyId)) {
            throw new BadRequestException("BOOKING_PROPERTY_MISMATCH", "This booking is not for this property");
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ConflictException("BOOKING_NOT_ELIGIBLE", "Only a completed stay can be reviewed");
        }
        if (reviewRepository.existsByBookingId(booking.getId())) {
            throw new ConflictException("ALREADY_REVIEWED", "This booking has already been reviewed");
        }

        Review review = reviewRepository.save(new Review(booking.getId(), propertyId, guestId, request.rating(), request.comment()));
        recomputeAggregateRating(propertyId);
        return review;
    }

    @Transactional(readOnly = true)
    public Page<Review> listForProperty(UUID propertyId, Pageable pageable) {
        return reviewRepository.findAllByPropertyIdOrderByCreatedAtDesc(propertyId, pageable);
    }

    private void recomputeAggregateRating(UUID propertyId) {
        Property property = propertyRepository.findById(propertyId).orElseThrow(() -> NotFoundException.of("Property", propertyId));
        Double average = reviewRepository.averageRating(propertyId);
        long count = reviewRepository.countByPropertyId(propertyId);
        BigDecimal ratingAvg = average == null ? BigDecimal.ZERO : BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP);
        property.applyAggregateRating(ratingAvg, (int) count);
    }
}
