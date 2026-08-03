# Prompt 12 — Booking Engine

> Phase: 6 · Order: 12 · Depends on: 10, 11
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Implement the authoritative booking engine and pricing calculator: server-computed totals, availability validation, temporary holds, transactional confirmation with double-booking prevention, and status/cancellation/refund states.

## 2. Prerequisite documents
- `../backend/02-domain-modules.md`
- `../database/01-data-model.md`
- `../architecture/02-adr-index.md` (ADR-008)
- `../product/02-user-stories-and-acceptance.md`

## 3. Deliverables (exact)
- Pricing service: nights × base (+ overrides) + cleaning + service fee − discounts + taxes + commission; currency-aware; unit-tested.
- `POST /properties/{id}/quote` (no persistence) and `POST /bookings` (recompute + verify server-side; reject client-provided totals).
- Redis checkout hold + Postgres overlap exclusion constraint; booking status machine; cancellation→refund states.
- Idempotency-Key handling on booking creation.

## 4. Constraints
- NEVER trust frontend price/totals — recompute and verify on the backend.
- Prevent double-booking under concurrency (transaction + exclusion constraint + hold).
- Refunds initiated via payment provider in prompt 13 — model states here.

## 5. Acceptance criteria
- Two concurrent bookings for overlapping dates → exactly one succeeds.
- Server total matches deterministic calculation; mismatched client totals are rejected.
- Cancellation yields correct refund state per policy.

## 6. Files you MAY modify
- backend `booking/`, `pricing/`
- migrations (exclusion constraint)
- `../backend/*`, `../database/*` (updates)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)
- `payments/` provider internals (integrate in prompt 13)

## 8. Tests required
- Unit: pricing across all components + currencies.
- Concurrency test: parallel confirms → one success (Testcontainers).
- Cancellation/refund state tests; idempotency test.

## 9. Documentation updates required
- Update backend/database/architecture docs; OpenAPI.

## 10. Verification before completion
- Concurrency and pricing tests pass.
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
