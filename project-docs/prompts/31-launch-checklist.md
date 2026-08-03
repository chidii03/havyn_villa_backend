# Prompt 31 — Launch Checklist

> Phase: 14 · Order: 31 · Depends on: 29, 30
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Final pre-launch verification across product, brand/legal, web, mobile, payments, security, ops, and support — culminating in launch sign-off.

## 2. Prerequisite documents
- `../roadmap/01-phased-roadmap.md`
- `../devops/*`
- `../security/*`
- `../product/*`

## 3. Deliverables (exact)
- Consolidated launch checklist: brand/domain/trademark cleared; payments live-verified; legal (ToS/privacy/host agreements); web + mobile store readiness; monitoring/alerts on; support & incident process ready.
- Rollback/kill-switch plan; post-launch monitoring plan; launch sign-off record.

## 4. Constraints
- No launch with unresolved high/critical items.
- Brand name must be legally cleared before public launch.
- Real payment + refund verified in production-like conditions.

## 5. Acceptance criteria
- All checklist items green or explicitly risk-accepted; sign-off recorded; rollback plan ready.
- Post-launch monitoring active.

## 6. Files you MAY modify
- launch checklist + records
- `../roadmap/*` (updates)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- Production smoke of critical flows; payment + refund verification; alert verification.

## 9. Documentation updates required
- Add launch record to `../roadmap/` or `../devops/`.

## 10. Verification before completion
- Launch checklist complete and signed off.
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
