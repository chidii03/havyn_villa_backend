# Prompt 03 — Product Requirements

> Phase: 1 · Order: 03 · Depends on: 01, 02
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Finalize the MVP PRD, user stories, acceptance criteria, and the MVP monetization path.

## 2. Prerequisite documents
- `../product/01-product-requirements.md`
- `../product/02-user-stories-and-acceptance.md`
- `../product/03-business-model.md`
- `../discovery/*`

## 3. Deliverables (exact)
- Approved PRD with MoSCoW prioritization and explicit non-goals.
- User stories with Given/When/Then acceptance criteria for all MVP features.
- Chosen MVP monetization (host commission + optional transparent guest fee), configurable server-side.

## 4. Constraints
- Backend is authoritative for all pricing/booking logic.
- No fake/frontend-only functionality in scope.
- Keep future categories out of MVP scope.

## 5. Acceptance criteria
- Every MVP feature has stories + acceptance criteria.
- Monetization is the simplest viable path and admin-configurable.

## 6. Files you MAY modify
- `../product/*`

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- N/A (documentation) — acceptance criteria become test specs later.

## 9. Documentation updates required
- Update `../product/*`; ensure traceability to roadmap phases.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
