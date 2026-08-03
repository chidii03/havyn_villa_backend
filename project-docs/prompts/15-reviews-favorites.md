# Prompt 15 — Reviews & Favorites

> Phase: 9 · Order: 15 · Depends on: 12
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Implement reviews (gated on completed eligible stays, updating aggregate rating) and favorites (save/unsave, list) for authenticated customers.

## 2. Prerequisite documents
- `../database/01-data-model.md`
- `../product/02-user-stories-and-acceptance.md`

## 3. Deliverables (exact)
- Review create/list endpoints with eligibility gating (completed booking only); transactional aggregate-rating update.
- Favorite add/remove/list endpoints (auth required).

## 4. Constraints
- Only eligible completed-stay users can review; one review per eligible booking.
- Aggregate rating updated atomically.
- Object-level authz on favorites.

## 5. Acceptance criteria
- Ineligible users cannot review; aggregate rating reflects new reviews.
- Favorites toggle and list correctly per user.

## 6. Files you MAY modify
- backend `reviews/`, `favorites/`
- migrations
- `../database/*` (updates)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- Unit: eligibility + rating aggregation.
- Integration: review lifecycle + favorites (Testcontainers).
- Authz tests.

## 9. Documentation updates required
- Update database/backend docs; OpenAPI.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
