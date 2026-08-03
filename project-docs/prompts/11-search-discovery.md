# Prompt 11 — Search & Discovery

> Phase: 5 · Order: 11 · Depends on: 10
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Implement property search and filtering: destination, dates, guests, price, type, rooms, amenities, rating — excluding unavailable/blocked/booked properties. Add Redis caching for hot queries.

## 2. Prerequisite documents
- `../architecture/03-api-design.md`
- `../database/01-data-model.md`
- `../product/01-product-requirements.md`

## 3. Deliverables (exact)
- `GET /search` with all MVP filters, sorting, pagination, and map-marker data.
- Availability-aware exclusion (blocked/booked/capacity).
- Redis caching for popular/hot queries with sane invalidation.

## 4. Constraints
- Geospatial radius and recommendations are FUTURE — keep lat/lng but do not implement radius search now.
- Postgres remains source of truth; cache is best-effort.
- Read-optimized indexes.

## 5. Acceptance criteria
- Search returns only truly bookable properties for the given dates/guests.
- Filters/sort/pagination correct; cache improves latency without stale critical data.

## 6. Files you MAY modify
- backend `search/`
- indexes/migrations
- `../architecture/*`, `../database/*` (updates)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- Unit: filter/query building.
- Integration: availability exclusion + pagination (Testcontainers).
- Cache hit/miss + invalidation test.

## 9. Documentation updates required
- Update API + architecture docs; OpenAPI.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
