package com.havyn.payments.domain;

/** Every provider event/attempt recorded against a {@link Payment} — the audit trail. */
public enum TransactionType {
    CHARGE_INTENT_CREATED,
    CHARGE_SUCCEEDED,
    CHARGE_FAILED,
    REFUND_INITIATED,
    REFUND_SUCCEEDED,
    REFUND_FAILED,
    /** A webhook event this adapter doesn't map to anything above — still recorded (raw_payload has the real event name), not acted on. */
    WEBHOOK_UNRECOGNIZED
}
