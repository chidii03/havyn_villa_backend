# Prompt 29 — Production Readiness

> Phase: 13 · Order: 29 · Depends on: 24, 26, 28
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Run the production-readiness review: security, reliability, observability, backups/DR, runbooks, capacity, and legal/compliance sign-off.

## 2. Prerequisite documents
- `../security/01-security-plan.md`
- `../devops/*`
- `../testing/*`

## 3. Deliverables (exact)
- Completed readiness checklist (security, SLOs, alerting, backups + PITR, DR plan, runbooks, on-call).
- Load/capacity validation; incident-response runbook; data-retention & privacy review.
- Go/no-go decision record.

## 4. Constraints
- No open high/critical security or reliability items.
- Backups + restore actually tested.
- Brand name legal clearance confirmed before public launch.

## 5. Acceptance criteria
- Every checklist item passed or risk-accepted with owner + rationale; restore drill succeeds.
- Go/no-go recorded.

## 6. Files you MAY modify
- runbooks, checklists
- `../devops/*`, `../security/*` (updates)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- Restore drill; failover/DR test; synthetic prod smoke.

## 9. Documentation updates required
- Add production-readiness record to `../devops/`.

## 10. Verification before completion
- Readiness checklist complete.
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
