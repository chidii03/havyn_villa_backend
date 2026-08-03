package com.havyn.reviews.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.havyn.booking.domain.Booking;
import com.havyn.booking.domain.BookingStatus;
import com.havyn.booking.repo.BookingRepository;
import com.havyn.common.error.BadRequestException;
import com.havyn.common.error.ConflictException;
import com.havyn.common.error.ForbiddenException;
import com.havyn.common.error.NotFoundException;
import com.havyn.properties.domain.Property;
import com.havyn.properties.domain.PropertyType;
import com.havyn.properties.repo.PropertyRepository;
import com.havyn.reviews.domain.Review;
import com.havyn.reviews.repo.ReviewRepository;
import com.havyn.reviews.web.CreateReviewRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReviewServiceTest {

    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);

    private final ReviewService service = new ReviewService(reviewRepository, bookingRepository, propertyRepository);

    private final UUID guestId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private Property property;

    @BeforeEach
    void setUp() {
        PropertyType villa = mock(PropertyType.class);
        property = new Property(
                UUID.randomUUID(), villa, "Sunset Villa", "Description", "1 Beach Rd", "Lagos", "Lagos", "Nigeria",
                BigDecimal.valueOf(10000), 4, 2, 2, BigDecimal.valueOf(2));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateReviewRequest request(UUID bookingId) {
        return new CreateReviewRequest(bookingId, 5, "Wonderful stay.");
    }

    private Booking completedBooking() {
        Booking booking = new Booking(
                propertyId, guestId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5), 4, 2,
                BigDecimal.valueOf(40000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(40000), "NGN");
        booking.transitionTo(BookingStatus.CONFIRMED);
        booking.transitionTo(BookingStatus.COMPLETED);
        return booking;
    }

    @Test
    void create_rejectsWhenTheBookingDoesNotExist() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(guestId, propertyId, request(bookingId)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_rejectsWhenCallerIsNotTheBookingsGuest() {
        Booking booking = completedBooking();
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), propertyId, request(booking.getId())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_rejectsWhenTheBookingIsForADifferentProperty() {
        Booking booking = completedBooking();
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.create(guestId, UUID.randomUUID(), request(booking.getId())))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("BOOKING_PROPERTY_MISMATCH");
    }

    @Test
    void create_rejectsWhenTheBookingIsNotCompleted() {
        Booking booking = new Booking(
                propertyId, guestId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5), 4, 2,
                BigDecimal.valueOf(40000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(40000), "NGN"); // still PENDING
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.create(guestId, propertyId, request(booking.getId())))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("BOOKING_NOT_ELIGIBLE");
    }

    @Test
    void create_rejectsASecondReviewForTheSameBooking() {
        Booking booking = completedBooking();
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingId(booking.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.create(guestId, propertyId, request(booking.getId())))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("ALREADY_REVIEWED");
    }

    @Test
    void create_savesTheReviewAndRecomputesTheAggregateRatingFromSource() {
        Booking booking = completedBooking();
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingId(booking.getId())).thenReturn(false);
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(reviewRepository.averageRating(propertyId)).thenReturn(4.3333333);
        when(reviewRepository.countByPropertyId(propertyId)).thenReturn(3L);

        Review review = service.create(guestId, propertyId, request(booking.getId()));

        assertThat(review.getBookingId()).isEqualTo(booking.getId());
        assertThat(review.getPropertyId()).isEqualTo(propertyId);
        assertThat(review.getGuestId()).isEqualTo(guestId);
        assertThat(review.getRating()).isEqualTo(5);
        // Recomputed from source (AVG/COUNT), rounded HALF_UP to the column's 2-decimal scale.
        assertThat(property.getRatingAvg()).isEqualByComparingTo("4.33");
        assertThat(property.getRatingCount()).isEqualTo(3);
    }

    @Test
    void create_appliesAZeroAggregateWhenNoReviewsExistYet() {
        // Defensive: shouldn't happen (this call itself just inserted one), but the
        // AVG(...) JPQL query returns null over an empty/non-matching set — must not NPE.
        Booking booking = completedBooking();
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(reviewRepository.averageRating(propertyId)).thenReturn(null);
        when(reviewRepository.countByPropertyId(propertyId)).thenReturn(0L);

        service.create(guestId, propertyId, request(booking.getId()));

        assertThat(property.getRatingAvg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(property.getRatingCount()).isEqualTo(0);
    }
}
