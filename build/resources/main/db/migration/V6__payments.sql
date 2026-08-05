-- V6__payments.sql
-- Payments (prompt 12): Payment/Transaction/Refund/Payout — see
-- project-docs/database/01-data-model.md#3 and ADR-004. Money as numeric(12,2), never
-- float, per project-docs/database/02-migrations-and-conventions.md.

CREATE TABLE payment (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id   uuid NOT NULL REFERENCES booking (id) ON DELETE RESTRICT,
    provider     varchar(30) NOT NULL,
    provider_ref varchar(100) NOT NULL,
    amount       numeric(12,2) NOT NULL CHECK (amount >= 0),
    currency     varchar(3) NOT NULL,
    status       varchar(20) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('REQUIRES_ACTION', 'PENDING', 'SUCCEEDED', 'FAILED')),
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_ref)
);

CREATE INDEX idx_payment_booking_id ON payment (booking_id);

-- ---------------------------------------------------------------------------
-- transaction — an append-only ledger of every provider event/attempt against a
-- payment (charge attempts, webhook deliveries, refund attempts), for audit —
-- project-docs/database/01-data-model.md#6's AuditLog rationale, scoped to payments
-- specifically since a general AuditLog entity doesn't exist yet.
-- ---------------------------------------------------------------------------
CREATE TABLE transaction (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id   uuid NOT NULL REFERENCES payment (id) ON DELETE CASCADE,
    type         varchar(30) NOT NULL,
    provider_ref varchar(100),
    amount       numeric(12,2) NOT NULL CHECK (amount >= 0),
    currency     varchar(3) NOT NULL,
    raw_payload  text,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_transaction_payment_id ON transaction (payment_id);

CREATE TABLE refund (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id   uuid NOT NULL REFERENCES payment (id) ON DELETE RESTRICT,
    amount       numeric(12,2) NOT NULL CHECK (amount >= 0),
    reason       text,
    status       varchar(20) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    provider_ref varchar(100),
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_refund_payment_id ON refund (payment_id);

-- ---------------------------------------------------------------------------
-- payout — accrues per host per period as payments succeed. No real payout-execution
-- rail exists yet (bank transfer / provider payout API) — see backend/02-domain-
-- modules.md's session 7 notes; every row stays PENDING for now, but the accrual math
-- (grand_total - commission_amount, per booking, summed per host per period) is real.
-- ---------------------------------------------------------------------------
CREATE TABLE payout (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id    uuid NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    period     varchar(7) NOT NULL, -- "YYYY-MM"
    amount     numeric(12,2) NOT NULL DEFAULT 0 CHECK (amount >= 0),
    currency   varchar(3) NOT NULL,
    status     varchar(20) NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING', 'PAID', 'FAILED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (host_id, period, currency)
);

CREATE INDEX idx_payout_host_id ON payout (host_id);
