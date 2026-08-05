package com.havyn.payments.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Accrues per (host, period, currency) as payments succeed — see {@code PayoutStatus}'s Javadoc for why every row stays {@code PENDING} today. */
@Entity
@Table(name = "payout")
public class Payout extends BaseEntity {

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Column(name = "period", nullable = false, length = 7)
    private String period;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutStatus status = PayoutStatus.PENDING;

    protected Payout() {
        // JPA
    }

    public Payout(UUID hostId, String period, String currency) {
        this.hostId = hostId;
        this.period = period;
        this.currency = currency;
    }

    public UUID getHostId() {
        return hostId;
    }

    public String getPeriod() {
        return period;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public void accrue(BigDecimal additionalAmount) {
        this.amount = this.amount.add(additionalAmount);
    }
}
