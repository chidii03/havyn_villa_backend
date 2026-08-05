package com.havyn.booking.repo;

import com.havyn.booking.domain.Booking;
import com.havyn.booking.domain.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByGuestIdAndIdempotencyKey(UUID guestId, String idempotencyKey);

    Page<Booking> findAllByGuestIdOrderByCreatedAtDesc(UUID guestId, Pageable pageable);

    List<Booking> findAllByPropertyIdAndStatusAndHoldExpiresAtBefore(UUID propertyId, BookingStatus status, Instant cutoff);

    List<Booking> findAllByStatusAndHoldExpiresAtBefore(BookingStatus status, Instant cutoff);

    Page<Booking> findAllByPropertyIdInOrderByCheckInDesc(Collection<UUID> propertyIds, Pageable pageable);

    long countByPropertyIdInAndStatusInAndCheckInGreaterThanEqual(
            Collection<UUID> propertyIds, Collection<BookingStatus> statuses, LocalDate from);

    long countByStatusIn(Collection<BookingStatus> statuses);

    boolean existsByGuestIdAndPropertyIdAndStatusIn(UUID guestId, UUID propertyId, Collection<BookingStatus> statuses);

    // Platform-wide, not grouped by currency — this product is single-currency (NGN)
    // in every real sense today (property.currency defaults NGN, Paystack is the only
    // configured provider and is NGN-only); revisit if that ever changes.
    @Query("SELECT COALESCE(SUM(b.grandTotal), 0) FROM Booking b WHERE b.status IN :statuses")
    BigDecimal sumGrandTotalByStatusIn(@Param("statuses") Collection<BookingStatus> statuses);

    @Query("SELECT COALESCE(SUM(b.commissionAmount), 0) FROM Booking b WHERE b.status IN :statuses")
    BigDecimal sumCommissionAmountByStatusIn(@Param("statuses") Collection<BookingStatus> statuses);

    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.propertyId = :propertyId
              AND b.status IN :activeStatuses
              AND b.checkIn < :checkOut
              AND b.checkOut > :checkIn
            """)
    boolean existsOverlapping(
            @Param("propertyId") UUID propertyId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("activeStatuses") Collection<BookingStatus> activeStatuses);
}
