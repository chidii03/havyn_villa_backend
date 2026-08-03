-- V1__init.sql
-- Reference/enum data only (roles, property types, amenities) per
-- project-docs/database/02-migrations-and-conventions.md. No business/production data.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------------
-- role — RBAC roles (CLAUDE.md#roles: Guest is unauthenticated, so only the
-- three roles that require an account row are seeded here).
-- ---------------------------------------------------------------------------
CREATE TABLE role (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code       varchar(30) NOT NULL UNIQUE,
    name       varchar(60) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO role (code, name) VALUES
    ('CUSTOMER', 'Customer'),
    ('HOST', 'Host'),
    ('ADMIN', 'Admin');

-- ---------------------------------------------------------------------------
-- property_type — listing taxonomy (project-docs/database/01-data-model.md#1).
-- ---------------------------------------------------------------------------
CREATE TABLE property_type (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code       varchar(30) NOT NULL UNIQUE,
    name       varchar(60) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO property_type (code, name) VALUES
    ('APARTMENT', 'Apartment'),
    ('VILLA', 'Villa'),
    ('HOUSE', 'House'),
    ('STUDIO', 'Studio'),
    ('CONDO', 'Condo'),
    ('CABIN', 'Cabin'),
    ('GUESTHOUSE', 'Guesthouse');

-- ---------------------------------------------------------------------------
-- amenity — amenity taxonomy (project-docs/database/01-data-model.md#1).
-- ---------------------------------------------------------------------------
CREATE TABLE amenity (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code       varchar(50) NOT NULL UNIQUE,
    name       varchar(80) NOT NULL,
    category   varchar(40),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO amenity (code, name, category) VALUES
    ('WIFI', 'Wifi', 'internet_and_office'),
    ('DEDICATED_WORKSPACE', 'Dedicated workspace', 'internet_and_office'),
    ('KITCHEN', 'Kitchen', 'kitchen_and_dining'),
    ('WASHER', 'Washer', 'bedroom_and_laundry'),
    ('FREE_PARKING', 'Free parking on premises', 'parking_and_facilities'),
    ('POOL', 'Pool', 'parking_and_facilities'),
    ('AIR_CONDITIONING', 'Air conditioning', 'heating_and_cooling'),
    ('HEATING', 'Heating', 'heating_and_cooling'),
    ('TV', 'TV', 'entertainment'),
    ('SELF_CHECK_IN', 'Self check-in', 'services'),
    ('PET_FRIENDLY', 'Pets allowed', 'general'),
    ('GYM', 'Shared gym in building', 'parking_and_facilities'),
    ('EXTERIOR_SECURITY_CAMERAS', 'Exterior security cameras on property', 'home_safety'),
    ('SMOKE_ALARM', 'Smoke alarm', 'home_safety'),
    ('CARBON_MONOXIDE_ALARM', 'Carbon monoxide alarm', 'home_safety'),
    ('BREAKFAST', 'Breakfast', 'general');
