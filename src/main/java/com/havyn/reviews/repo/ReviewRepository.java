package com.havyn.reviews.repo;

import com.havyn.reviews.domain.Review;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByBookingId(UUID bookingId);

    Page<Review> findAllByPropertyIdOrderByCreatedAtDesc(UUID propertyId, Pageable pageable);

    long countByPropertyId(UUID propertyId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.propertyId = :propertyId")
    Double averageRating(@Param("propertyId") UUID propertyId);
}
