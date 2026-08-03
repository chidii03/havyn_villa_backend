package com.havyn.admin.repo;

import com.havyn.admin.domain.Dispute;
import com.havyn.admin.domain.DisputeStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    Page<Dispute> findAllByStatusOrderByCreatedAtAsc(DisputeStatus status, Pageable pageable);

    long countByStatus(DisputeStatus status);
}
