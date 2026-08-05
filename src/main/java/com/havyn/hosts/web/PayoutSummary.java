package com.havyn.hosts.web;

import com.havyn.payments.domain.Payout;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutSummary(UUID id, String period, BigDecimal amount, String currency, String status, Instant createdAt) {

    public static PayoutSummary from(Payout payout) {
        return new PayoutSummary(
                payout.getId(), payout.getPeriod(), payout.getAmount(), payout.getCurrency(), payout.getStatus().name(),
                payout.getCreatedAt());
    }
}
