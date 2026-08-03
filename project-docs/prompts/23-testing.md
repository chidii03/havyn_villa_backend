# Prompt 23 — Testing

> Phase: 11 · Order: 23 · Depends on: all feature prompts
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Consolidate and complete the automated test suite to strategy: unit, integration (Testcontainers), API/contract, frontend, a11y, and high-value E2E — with CI coverage gates.

## 2. Prerequisite documents
- `../testing/01-testing-strategy.md`

## 3. Deliverables (exact)
- Coverage of critical cases: pricing correctness, double-booking, cancellation/refund, webhook idempotency, RBAC/IDOR, review eligibility.
- Contract tests shared by web+mobile; E2E for search→book→pay→confirm, host publish, admin moderate.
- CI configured with coverage thresholds on domain modules and a11y checks.

## 4. Constraints
- No fake production data — factories/fixtures only.
- Tests must be deterministic and CI-runnable.
- Block merges on failures.

## 5. Acceptance criteria
- All critical cases covered and green; coverage gate enforced; E2E suite passes.
- Contract tests guarantee API stability for mobile.

## 6. Files you MAY modify
- test suites across backend + frontend
- CI config
- `../testing/*` (updates)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- This prompt IS the test consolidation — see deliverables.

## 9. Documentation updates required
- Update `../testing/01-testing-strategy.md` with coverage status.

## 10. Verification before completion
- Full suite green in CI.
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
