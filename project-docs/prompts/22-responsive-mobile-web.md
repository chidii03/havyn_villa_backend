# Prompt 22 — Responsive Mobile Web

> Phase: 6 · Order: 22 · Depends on: 20, 21
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Ensure the web experience is excellent on mobile viewports: responsive layouts, touch targets, sticky search/booking behaviors, and performance on constrained devices.

## 2. Prerequisite documents
- `../design/02-design-tokens.md` (breakpoints)
- `../design/04-accessibility.md`
- `../frontend/*`

## 3. Deliverables (exact)
- Responsive audits + fixes across home/results/detail/booking/dashboards.
- Mobile patterns: bottom sheets for filters/booking, sticky compact search, touch-optimized gallery/calendar.
- Performance pass for mobile (images, bundle, CWV).

## 4. Constraints
- 44×44px minimum targets; reduced-motion honored.
- No layout that hides critical price/CTA on mobile.
- Keep parity with desktop functionality.

## 5. Acceptance criteria
- Core flows are fully usable and fast on small screens; CWV targets met.
- No horizontal overflow; accessible touch interactions.

## 6. Files you MAY modify
- frontend styles/components
- doc updates

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- Responsive component/visual tests.
- a11y on mobile breakpoints.
- E2E on mobile viewport (Playwright).

## 9. Documentation updates required
- Update frontend/design docs.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
