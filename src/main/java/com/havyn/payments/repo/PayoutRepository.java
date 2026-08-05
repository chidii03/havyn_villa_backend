package com.havyn.payments.repo;

import com.havyn.payments.domain.Payout;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    Optional<Payout> findByHostIdAndPeriodAndCurrency(UUID hostId, String period, String currency);

    Page<Payout> findAllByHostIdOrderByPeriodDesc(UUID hostId, Pageable pageable);

    List<Payout> findAllByHostId(UUID hostId);
}
