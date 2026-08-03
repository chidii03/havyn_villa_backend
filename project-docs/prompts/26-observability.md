# Prompt 26 — Observability

> Phase: 12 · Order: 26 · Depends on: 08, 25
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Implement logging, metrics, tracing, dashboards, and alerts per the observability doc.

## 2. Prerequisite documents
- `../devops/02-observability.md`

## 3. Deliverables (exact)
- Structured JSON logs with correlation/trace ids; Micrometer metrics; OpenTelemetry tracing across API→DB/Redis/providers.
- Golden-signal + business dashboards; alerts (error rate, p95, webhook failures, hold anomalies, DB saturation).
- Synthetic checks on core flows.

## 4. Constraints
- No PII/secrets in logs.
- Traces propagate end-to-end.
- Alerts actionable, not noisy.

## 5. Acceptance criteria
- Dashboards show golden signals + KPIs; alerts fire on injected faults; traces span a full request.
- Health/readiness wired for orchestration.

## 6. Files you MAY modify
- backend observability config
- dashboards/alerts as code
- `../devops/02-observability.md` (updates)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- Verify metrics/trace emission; alert-rule tests; log-scrubbing test (no PII).

## 9. Documentation updates required
- Update `../devops/02-observability.md`.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
