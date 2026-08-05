-- V4__search_indexes.sql
-- Read-optimized indexes for GET /search (prompt 11). Every search query filters on
-- status = 'ACTIVE' first, so the hot-path indexes are partial (WHERE status='ACTIVE')
-- to keep them small and cheap to maintain against draft/pending/suspended churn.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- destination text match (ILIKE '%term%' against city/state/country)
CREATE INDEX idx_property_city_trgm ON property USING gin (city gin_trgm_ops);
CREATE INDEX idx_property_state_trgm ON property USING gin (state gin_trgm_ops);
CREATE INDEX idx_property_country_trgm ON property USING gin (country gin_trgm_ops);

-- filter/sort columns, scoped to the only status search ever returns
CREATE INDEX idx_property_active_price ON property (base_price) WHERE status = 'ACTIVE';
CREATE INDEX idx_property_active_rating ON property (rating_avg DESC) WHERE status = 'ACTIVE';
CREATE INDEX idx_property_active_created ON property (created_at DESC) WHERE status = 'ACTIVE';
CREATE INDEX idx_property_active_capacity ON property (capacity) WHERE status = 'ACTIVE';
CREATE INDEX idx_property_active_bedrooms ON property (bedrooms) WHERE status = 'ACTIVE';

-- availability exclusion (NOT EXISTS ... WHERE is_blocked OR booking_id IS NOT NULL)
CREATE INDEX idx_availability_unavailable ON availability (property_id, date)
    WHERE is_blocked = true OR booking_id IS NOT NULL;
