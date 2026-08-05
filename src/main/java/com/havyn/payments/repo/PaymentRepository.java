package com.havyn.payments.repo;

import com.havyn.payments.domain.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderAndProviderRef(String provider, String providerRef);

    List<Payment> findAllByBookingIdOrderByCreatedAtDesc(UUID bookingId);
}
