package com.havyn.admin.repo;

import com.havyn.admin.domain.VerificationRequest;
import com.havyn.admin.domain.VerificationStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, UUID> {

    Page<VerificationRequest> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<VerificationRequest> findAllByStatusOrderByCreatedAtAsc(VerificationStatus status, Pageable pageable);

    boolean existsByUserIdAndStatus(UUID userId, VerificationStatus status);

    long countByStatus(VerificationStatus status);
}
