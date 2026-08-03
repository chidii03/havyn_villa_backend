-- V13__rayprop_import.sql
-- RayProp wholesale-inventory integration (project-docs decision: imported into
-- Postgres, not proxied live, so search/caching/rate-limiting stay unchanged for
-- these listings — Postgres remains the single source of truth per CLAUDE.md).

-- RayProp's own property_category is always "shortlet"; none of the existing
-- property_type rows (V1__init.sql) fit that vocabulary.
INSERT INTO property_type (code, name) VALUES
    ('SHORTLET', 'Shortlet');

-- Lets the sync job upsert idempotently: re-running a sync updates the same rows
-- instead of duplicating them. Both columns are NULL for every host-created listing
-- (Postgres UNIQUE allows any number of NULLs, so this doesn't collide with them);
-- only rows imported from an external partner populate them.
ALTER TABLE property
    ADD COLUMN external_source varchar(30),
    ADD COLUMN external_id varchar(100),
    ADD CONSTRAINT uq_property_external_source_id UNIQUE (external_source, external_id);

-- property.host_id is NOT NULL (V3__properties.sql) — RayProp-imported listings need
-- *some* app_user row to attach to since RayProp is a B2B wholesaler and never
-- exposes a real host's identity to us. This system account exists only to satisfy
-- that FK; its password_hash is a deliberately invalid placeholder (not a real
-- Argon2 hash) so it can never actually authenticate — nobody is meant to log in as
-- this user.
INSERT INTO app_user (id, email, password_hash, status, email_verified_at)
VALUES (
    'a11a11a1-1a11-4a11-a11a-11a11a11a11a',
    'rayprop-partner@havynvilla.internal',
    'RAYPROP_SYSTEM_ACCOUNT_NO_LOGIN_POSSIBLE',
    'ACTIVE',
    now()
);

INSERT INTO profile (user_id, full_name)
VALUES ('a11a11a1-1a11-4a11-a11a-11a11a11a11a', 'RayProp Partner Listings');

INSERT INTO user_role (user_id, role_id)
SELECT 'a11a11a1-1a11-4a11-a11a-11a11a11a11a', id FROM role WHERE code = 'HOST';
