-- V7__property_media.sql
-- Property media (prompt 14) — URL/metadata only, per ADR-005. Binaries and full
-- videos live in Cloudinary, never in this table.

CREATE TABLE property_media (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id   uuid NOT NULL REFERENCES property (id) ON DELETE CASCADE,
    secure_url    text NOT NULL,
    public_id     varchar(255) NOT NULL,
    resource_type varchar(10) NOT NULL CHECK (resource_type IN ('IMAGE', 'VIDEO')),
    format        varchar(10) NOT NULL,
    width         integer,
    height        integer,
    duration      numeric(8,2), -- seconds, video only
    bytes         bigint NOT NULL CHECK (bytes >= 0),
    position      integer NOT NULL DEFAULT 0,
    alt           text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (property_id, public_id)
);

CREATE INDEX idx_property_media_property_id ON property_media (property_id, position);
