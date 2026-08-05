package com.havyn.payments.repo;

import com.havyn.payments.domain.Refund;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findAllByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
}
