# Prompt 25 — Performance

> Phase: 12 · Order: 25 · Depends on: 11, 12, 20, 21
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Meet performance targets: cache hot paths (Redis), optimize DB queries/indexes, tune the API, and optimize web Core Web Vitals.

## 2. Prerequisite documents
- `../devops/02-observability.md`
- `../architecture/01-system-architecture.md`

## 3. Deliverables (exact)
- Caching for popular properties/search with correct invalidation; N+1 elimination; index review.
- API p95 within targets under load test; connection pool tuning.
- Web CWV optimization (images/CDN, code-splitting, streaming).

## 4. Constraints
- Cache is best-effort; Postgres remains source of truth.
- Do not cache user-specific/price-critical data unsafely.
- Measure before/after — data-driven.

## 5. Acceptance criteria
- Documented load test meets latency/throughput targets on hot paths.
- CWV targets met; no correctness regressions.

## 6. Files you MAY modify
- backend caching/query layers
- frontend perf changes
- `../devops/*`, `../architecture/*` (updates)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- Load tests on search/quote/booking; cache correctness tests; regression suite green.

## 9. Documentation updates required
- Update observability/architecture docs with results.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
