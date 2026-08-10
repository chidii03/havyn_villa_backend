package com.havyn.support.repo;

import com.havyn.support.domain.SupportTicket;
import com.havyn.support.domain.SupportTicketStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    Page<SupportTicket> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<SupportTicket> findByStatusOrderByCreatedAtDesc(SupportTicketStatus status, Pageable pageable);

    @Query("""
            SELECT ticket FROM SupportTicket ticket
            WHERE LOWER(COALESCE(ticket.bookingReferenceId, '')) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(ticket.summary) LIKE LOWER(CONCAT('%', :search, '%'))
            ORDER BY ticket.createdAt DESC
            """)
    Page<SupportTicket> search(@Param("search") String search, Pageable pageable);

    @Query("""
            SELECT ticket FROM SupportTicket ticket
            WHERE ticket.status = :status
              AND (LOWER(COALESCE(ticket.bookingReferenceId, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(ticket.summary) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY ticket.createdAt DESC
            """)
    Page<SupportTicket> searchByStatus(
            @Param("status") SupportTicketStatus status,
            @Param("search") String search,
            Pageable pageable);
}
