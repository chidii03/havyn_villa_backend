# Prompt 17 — Host Dashboard (API + UI)

> Phase: 8 · Order: 17 · Depends on: 10, 12, 13, 14
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Deliver host platform capabilities: listing management, availability/pricing control, reservation management, earnings, and payouts — API plus web UI.

## 2. Prerequisite documents
- `../product/01-product-requirements.md`
- `../frontend/01-frontend-foundation.md`
- `../backend/02-domain-modules.md`

## 3. Deliverables (exact)
- Host APIs: listings, availability/calendar, reservations, earnings, payouts, reviews, performance.
- Web host dashboard (Next.js) consuming these APIs with proper states and RBAC.
- Calendar UI for blocking dates / price overrides.

## 4. Constraints
- Host sees only own data (object-level authz).
- No business math on the frontend — earnings/payout figures come from backend.
- Follow design system + accessibility.

## 5. Acceptance criteria
- Host can manage listings/calendar/reservations and view accurate earnings/payouts.
- Unauthorized access blocked; UI states complete.

## 6. Files you MAY modify
- backend `host/` endpoints
- frontend `app/host/*`
- doc updates

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)
- Core pricing/booking/payment logic (consume only)

## 8. Tests required
- Backend: host API authz + data correctness (Testcontainers).
- Frontend: dashboard components + a11y.
- E2E: host publishes listing and manages a reservation.

## 9. Documentation updates required
- Update product/frontend/backend docs; OpenAPI.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
