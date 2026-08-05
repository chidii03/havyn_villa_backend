-- V3__properties.sql
-- Property domain (prompt 10): listings, amenity taxonomy join, per-date availability.
-- host_id references app_user directly — HostProfile does not exist yet (not in this
-- prompt's scope; see backend/02-domain-modules.md's properties section for the note).

CREATE TABLE property (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id            uuid NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    type_id            uuid NOT NULL REFERENCES property_type (id) ON DELETE RESTRICT,
    title              varchar(150) NOT NULL,
    description        text NOT NULL,
    address            varchar(255) NOT NULL,
    city               varchar(100) NOT NULL,
    state              varchar(100) NOT NULL,
    country            varchar(100) NOT NULL,
    lat                numeric(9,6),
    lng                numeric(9,6),
    currency           varchar(3) NOT NULL DEFAULT 'NGN',
    base_price         numeric(12,2) NOT NULL CHECK (base_price >= 0),
    capacity           integer NOT NULL CHECK (capacity > 0),
    bedrooms           integer NOT NULL CHECK (bedrooms >= 0),
    beds               integer NOT NULL CHECK (beds >= 0),
    bathrooms          numeric(3,1) NOT NULL CHECK (bathrooms >= 0),
    cleaning_fee       numeric(12,2) NOT NULL DEFAULT 0 CHECK (cleaning_fee >= 0),
    service_fee_pct    numeric(5,2) NOT NULL DEFAULT 0 CHECK (service_fee_pct >= 0 AND service_fee_pct <= 100),
    house_rules        text,
    cancellation_policy varchar(40) NOT NULL DEFAULT 'FLEXIBLE',
    status             varchar(20) NOT NULL DEFAULT 'DRAFT'
                           CHECK (status IN ('DRAFT', 'PENDING', 'ACTIVE', 'SUSPENDED')),
    rating_avg         numeric(3,2) NOT NULL DEFAULT 0,
    rating_count       integer NOT NULL DEFAULT 0,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_property_host_id ON property (host_id);
CREATE INDEX idx_property_type_id ON property (type_id);
CREATE INDEX idx_property_status ON property (status);

-- ---------------------------------------------------------------------------
-- property_amenity — many-to-many join, no extra columns
-- (project-docs/database/01-data-model.md#1: Property *-* Amenity).
-- ---------------------------------------------------------------------------
CREATE TABLE property_amenity (
    property_id uuid NOT NULL REFERENCES property (id) ON DELETE CASCADE,
    amenity_id  uuid NOT NULL REFERENCES amenity (id) ON DELETE RESTRICT,
    PRIMARY KEY (property_id, amenity_id)
);

CREATE INDEX idx_property_amenity_amenity_id ON property_amenity (amenity_id);

-- ---------------------------------------------------------------------------
-- availability — per-date exceptions to the default-available calendar.
-- booking_id is a plain uuid (no FK) — the booking table doesn't exist until
-- prompt 12; this column is reserved for it to populate later.
-- ---------------------------------------------------------------------------
CREATE TABLE availability (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id    uuid NOT NULL REFERENCES property (id) ON DELETE CASCADE,
    date           date NOT NULL,
    is_blocked     boolean NOT NULL DEFAULT false,
    price_override numeric(12,2) CHECK (price_override IS NULL OR price_override >= 0),
    booking_id     uuid,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (property_id, date)
);

CREATE INDEX idx_availability_property_date ON availability (property_id, date);
