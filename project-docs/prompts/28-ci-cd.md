# Prompt 28 — CI/CD

> Phase: 13 · Order: 28 · Depends on: 23, 24, 27
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Build the CI/CD pipeline: PR checks (lint/typecheck/tests/scan/build), image publish, staging deploy, E2E/smoke, and gated promotion to production with rollback.

## 2. Prerequisite documents
- `../devops/01-devops-and-deployment.md`
- `../testing/01-testing-strategy.md`

## 3. Deliverables (exact)
- CI: lint, typecheck, unit+integration (Testcontainers), security scan, build images on every PR; coverage + a11y gates.
- CD: publish images, deploy staging, run E2E/smoke, promote to prod (manual/auto) with documented rollback.
- Branch protection + required checks.

## 4. Constraints
- Block merges on failing checks.
- Secrets via CI secret store; never printed.
- Forward-compatible DB changes for safe rollback.

## 5. Acceptance criteria
- A PR runs the full gate; merge to main deploys staging and can promote to prod; rollback verified.
- Pipeline is reproducible.

## 6. Files you MAY modify
- CI/CD config
- `../devops/*` (updates)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- Pipeline dry-run; rollback drill; smoke tests post-deploy.

## 9. Documentation updates required
- Update `../devops/01-devops-and-deployment.md`.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
