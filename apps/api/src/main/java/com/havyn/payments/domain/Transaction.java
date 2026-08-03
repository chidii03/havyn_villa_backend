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
@Table(name = "transaction")
public class Transaction extends BaseEntity {

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TransactionType type;

    @Column(name = "provider_ref", length = 100)
    private String providerRef;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "raw_payload")
    private String rawPayload;

    protected Transaction() {
        // JPA
    }

    public Transaction(UUID paymentId, TransactionType type, String providerRef, BigDecimal amount, String currency, String rawPayload) {
        this.paymentId = paymentId;
        this.type = type;
        this.providerRef = providerRef;
        this.amount = amount;
        this.currency = currency;
        this.rawPayload = rawPayload;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public TransactionType getType() {
        return type;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getRawPayload() {
        return rawPayload;
    }
}
