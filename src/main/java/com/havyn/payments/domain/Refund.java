package com.havyn.payments.domain;

import com.havyn.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "refund")
public class Refund extends BaseEntity {

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefundStatus status = RefundStatus.PENDING;

    @Column(name = "provider_ref", length = 100)
    private String providerRef;

    protected Refund() {
        // JPA
    }

    public Refund(UUID paymentId, BigDecimal amount, String reason) {
        this.paymentId = paymentId;
        this.amount = amount;
        this.reason = reason;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public void markSucceeded(String providerRef) {
        this.status = RefundStatus.SUCCEEDED;
        this.providerRef = providerRef;
    }

    public void markFailed() {
        this.status = RefundStatus.FAILED;
    }
}
