# Prompt 04 — User Experience

> Phase: 1 · Order: 04 · Depends on: 03
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Define the core UX flows and states for the MVP (discover→book, become-a-host, manage reservation, cancel/refund, messaging, admin moderation).

## 2. Prerequisite documents
- `../design/03-ux-flows.md`
- `../product/*`
- `../brand/02-brand-identity.md`

## 3. Deliverables (exact)
- Finalized flow specs with every state (loading/empty/error/offline/success/permission-denied).
- Navigation/IA map for web (traveler, host, admin areas).
- Wireframe-level descriptions for core screens.

## 4. Constraints
- Search-first; guest can browse unauthenticated; auth required to book/save/message.
- Whole-price transparency shown before commitment.
- Accessible flows (keyboard + screen reader).

## 5. Acceptance criteria
- Core flows cover all states and roles.
- Conversion path (discover→book) is the shortest, clearest path.

## 6. Files you MAY modify
- `../design/03-ux-flows.md`
- `../design/*`

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- N/A (design) — flows become E2E specs later.

## 9. Documentation updates required
- Update `../design/03-ux-flows.md` and IA.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
