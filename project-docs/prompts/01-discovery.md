# Prompt 01 — Discovery

> Phase: 0 · Order: 01 · Depends on: 00
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Produce/finalize the discovery artifacts: problem, opportunity, goals/non-goals, personas & JTBD, market & competitive analysis, and success metrics.

## 2. Prerequisite documents
- `00-project-context.md`
- `../discovery/*`

## 3. Deliverables (exact)
- Finalized `../discovery/01-discovery-brief.md`, `02-market-and-competitive-analysis.md`, `03-personas-and-jobs.md`.
- A prioritized open-questions list (non-blocking) and any blocking questions.

## 4. Constraints
- Study marketplace UX patterns; do NOT copy Airbnb branding/wording/assets.
- Keep MVP scope tight; mark future items explicitly.

## 5. Acceptance criteria
- Discovery docs are coherent, reviewed, and reflect the four roles and MVP scope.
- Differentiation vs incumbents is explicit.

## 6. Files you MAY modify
- `../discovery/*`

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- N/A (documentation).

## 9. Documentation updates required
- Update discovery docs; note assumptions to validate.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
