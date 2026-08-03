-- V10__admin_audit_kyc_disputes.sql
-- Admin platform (prompt 18): a real, queryable AuditLog (database/01-data-model.md#6
-- and security/01-security-plan.md have both described this shape since early
-- sessions — this is where the jsonb convention they were waiting on gets
-- established), host identity verification (KYC), and booking disputes.

-- ---------------------------------------------------------------------------
-- audit_log — actor_id is a real, nullable FK (ON DELETE SET NULL: the log entry
-- must outlive the actor account being later removed, unlike favorite's "the row
-- has no reason to exist without it" case). target_type/target_id are a plain,
-- polymorphic pair (no FK possible across multiple target tables) — mirrors the
-- data model's own "polymorphic actor/target" description.
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    uuid REFERENCES app_user (id) ON DELETE SET NULL,
    action      varchar(60) NOT NULL,
    target_type varchar(60) NOT NULL,
    target_id   uuid,
    before      jsonb,
    after       jsonb,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_target ON audit_log (target_type, target_id, created_at DESC);
CREATE INDEX idx_audit_log_actor ON audit_log (actor_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- verification_request — host identity/KYC review. user_id has a real FK,
-- ON DELETE CASCADE (mirrors favorite.user_id: no reason to keep a verification
-- request for a deleted account). reviewed_by is a real, nullable FK to the
-- reviewing admin, ON DELETE SET NULL (the review record must outlive that admin
-- account being later removed).
-- ---------------------------------------------------------------------------
CREATE TABLE verification_request (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    document_url text NOT NULL,
    notes        text,
    status       varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    review_notes text,
    reviewed_by  uuid REFERENCES app_user (id) ON DELETE SET NULL,
    reviewed_at  timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_verification_request_user ON verification_request (user_id, created_at DESC);
CREATE INDEX idx_verification_request_pending ON verification_request (created_at) WHERE status = 'PENDING';

-- ---------------------------------------------------------------------------
-- dispute — booking_id has a real FK, ON DELETE RESTRICT (same "not a valid state
-- without a genuine booking behind it" reasoning as payment.booking_id/
-- review.booking_id). raised_by is a plain UUID column (could be either the
-- booking's guest or the property's host — same historical-record reasoning as
-- booking.guest_id). resolved_by mirrors verification_request.reviewed_by.
-- ---------------------------------------------------------------------------
CREATE TABLE dispute (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id       uuid NOT NULL REFERENCES booking (id) ON DELETE RESTRICT,
    raised_by        uuid NOT NULL,
    reason           text NOT NULL,
    status           varchar(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    resolution_notes text,
    resolved_by      uuid REFERENCES app_user (id) ON DELETE SET NULL,
    resolved_at      timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_dispute_booking ON dispute (booking_id);
CREATE INDEX idx_dispute_open ON dispute (created_at) WHERE status = 'OPEN';
