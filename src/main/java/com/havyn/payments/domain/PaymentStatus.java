package com.havyn.payments.domain;

/**
 * project-docs/database/01-data-model.md#3: {@code requires_action/pending/succeeded/
 * failed}. Paystack's hosted-checkout redirect flow resolves any 3DS/OTP challenge on
 * Paystack's own page before ever notifying us, so {@code REQUIRES_ACTION} is kept for
 * schema fidelity but isn't reachable through the current adapter — only {@code
 * PENDING -> SUCCEEDED}/{@code FAILED} is.
 */
public enum PaymentStatus {
    REQUIRES_ACTION,
    PENDING,
    SUCCEEDED,
    FAILED
}
