package com.havyn.payments.repo;

import com.havyn.payments.domain.Transaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findAllByPaymentIdOrderByCreatedAtAsc(UUID paymentId);
}
