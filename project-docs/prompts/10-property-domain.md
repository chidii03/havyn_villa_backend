# Prompt 10 — Property Domain

> Phase: 5 · Order: 10 · Depends on: 08, 09
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Implement the property domain: listings CRUD with status lifecycle, property types, amenities taxonomy, and availability model (excluding media upload, which is prompt 14).

## 2. Prerequisite documents
- `../database/01-data-model.md`
- `../backend/02-domain-modules.md`
- `../product/01-product-requirements.md`

## 3. Deliverables (exact)
- Property, PropertyType, Amenity/PropertyAmenity, Availability entities + migrations + repositories + services.
- Host-scoped listing CRUD endpoints with status lifecycle (draft→pending→active→suspended).
- Seeded property types + amenities (reference data).

## 4. Constraints
- Only hosts manage their own listings (object-level authz).
- No media binaries in DB (metadata comes in prompt 14).
- Money as numeric; validate all inputs.

## 5. Acceptance criteria
- A host can create/edit/publish a listing; status transitions enforced.
- Amenities/types available; availability records settable.

## 6. Files you MAY modify
- backend `properties/`, `amenities/`
- migrations
- `../database/*`, `../backend/*` (updates)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)
- `auth/` core (only consume it)

## 8. Tests required
- Unit: status transitions, authorization.
- Integration: listing CRUD (Testcontainers).
- Authz: host cannot edit another host's listing.

## 9. Documentation updates required
- Update data-model/backend docs; OpenAPI.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
