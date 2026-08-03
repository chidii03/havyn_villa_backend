package com.havyn.payments.domain;

/** {@code PAID}/{@code FAILED} are modeled for schema completeness — no payout-execution rail exists yet, so every row stays {@code PENDING}. */
public enum PayoutStatus {
    PENDING,
    PAID,
    FAILED
}
