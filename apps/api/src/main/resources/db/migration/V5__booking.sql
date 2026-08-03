-- V5__booking.sql
-- Booking engine (prompt 12): platform-wide settings (commission rate — must be
-- server-side/configurable, never hardcoded, per product/03-business-model.md) and
-- the booking table itself with the double-booking exclusion constraint (ADR-008).

-- ---------------------------------------------------------------------------
-- platform_setting — minimal key/value store. This migration only seeds the one
-- key pricing/ needs; a full admin read/write API is prompt 18's deliverable, not
-- this one (see backend/02-domain-modules.md's session 6 notes).
-- ---------------------------------------------------------------------------
CREATE TABLE platform_setting (
    key        varchar(60) PRIMARY KEY,
    value      varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- "default illustrative 12-15%" — project-docs/product/03-business-model.md#17.
INSERT INTO platform_setting (key, value) VALUES ('commission_pct', '12.00');

-- ---------------------------------------------------------------------------
-- booking — see project-docs/database/01-data-model.md#3.
-- ---------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE booking (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id      uuid NOT NULL REFERENCES property (id) ON DELETE RESTRICT,
    guest_id         uuid NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    check_in         date NOT NULL,
    check_out        date NOT NULL CHECK (check_out > check_in),
    nights           integer NOT NULL CHECK (nights > 0),
    guests_count     integer NOT NULL CHECK (guests_count > 0),
    base_total       numeric(12,2) NOT NULL CHECK (base_total >= 0),
    cleaning_fee     numeric(12,2) NOT NULL DEFAULT 0 CHECK (cleaning_fee >= 0),
    service_fee      numeric(12,2) NOT NULL DEFAULT 0 CHECK (service_fee >= 0),
    discount_total   numeric(12,2) NOT NULL DEFAULT 0 CHECK (discount_total >= 0),
    tax_total        numeric(12,2) NOT NULL DEFAULT 0 CHECK (tax_total >= 0),
    commission_amount numeric(12,2) NOT NULL DEFAULT 0 CHECK (commission_amount >= 0),
    grand_total      numeric(12,2) NOT NULL CHECK (grand_total >= 0),
    currency         varchar(3) NOT NULL,
    status           varchar(20) NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'REFUNDED')),
    hold_expires_at  timestamptz,
    idempotency_key  varchar(100),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_booking_property_id ON booking (property_id);
CREATE INDEX idx_booking_guest_id ON booking (guest_id);
CREATE INDEX idx_booking_status ON booking (status);
CREATE INDEX idx_booking_hold_expiry ON booking (hold_expires_at) WHERE status = 'PENDING';

-- One booking row per (guest, idempotency key) — a retried request with the same key
-- must resolve to the same booking, never a second row.
CREATE UNIQUE INDEX uq_booking_guest_idempotency_key
    ON booking (guest_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

-- ADR-008: exactly one of two concurrent overlapping-date bookings can ever commit.
-- check_out is exclusive (the departure date's night isn't occupied), matching how
-- search's availability-exclusion query (prompt 11) already treats date ranges.
ALTER TABLE booking ADD CONSTRAINT no_overlapping_active_bookings
    EXCLUDE USING gist (
        property_id WITH =,
        daterange(check_in, check_out, '[)') WITH &&
    ) WHERE (status IN ('PENDING', 'CONFIRMED', 'COMPLETED'));
