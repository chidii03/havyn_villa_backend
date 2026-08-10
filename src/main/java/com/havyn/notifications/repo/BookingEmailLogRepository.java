package com.havyn.notifications.repo;

import com.havyn.notifications.domain.BookingEmailLog;
import com.havyn.notifications.domain.BookingEmailStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingEmailLogRepository extends JpaRepository<BookingEmailLog, UUID> {

    Page<BookingEmailLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<BookingEmailLog> findByStatusOrderByCreatedAtDesc(BookingEmailStatus status, Pageable pageable);

    @Query("""
            SELECT log FROM BookingEmailLog log
            WHERE LOWER(COALESCE(log.bookingReferenceId, '')) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(log.recipientEmail) LIKE LOWER(CONCAT('%', :search, '%'))
            ORDER BY log.createdAt DESC
            """)
    Page<BookingEmailLog> search(@Param("search") String search, Pageable pageable);

    @Query("""
            SELECT log FROM BookingEmailLog log
            WHERE log.status = :status
              AND (LOWER(COALESCE(log.bookingReferenceId, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(log.recipientEmail) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY log.createdAt DESC
            """)
    Page<BookingEmailLog> searchByStatus(
            @Param("status") BookingEmailStatus status,
            @Param("search") String search,
            Pageable pageable);

    long countByStatus(BookingEmailStatus status);
}
