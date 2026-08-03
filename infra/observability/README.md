# Observability as code (prompt 26)

Real, usable artifacts — none of this is a placeholder, but none of it has run
against live infrastructure either, since no Prometheus/Grafana/staging deployment
exists yet (prompts 27/29). See `project-docs/devops/02-observability.md`'s prompt 26
notes for the full status.

## What's here

- **`alerts.yml`** — Prometheus alerting rules: HTTP error rate, p95 latency (golden
  signals), payment webhook failures, booking-hold expiry anomalies, DB connection
  pool saturation, rate-limit rejection spikes. Every metric name in every `expr` is
  real — validated against `apps/api`'s actual `meterRegistry.counter(...)` call
  sites by `validate.mjs` (wired into `.github/workflows/ci.yml`'s `backend` job on
  every PR), not just written and hoped correct.
- **`dashboard.json`** — a Grafana dashboard (golden signals + the same business KPIs
  the alerts watch) in Grafana's standard dashboard JSON model. Import directly into
  any Grafana instance pointed at a Prometheus scraping `apps/api`'s
  `/actuator/prometheus` (note: that endpoint requires a bearer token by default —
  see `application.yml`'s prompt 26 notes on why, and what a real deployment needs to
  do differently).
- **`synthetic-checks.mjs`** + **`.github/workflows/synthetic-checks.yml`** — hits
  `/actuator/health`, `GET /search`, `POST /properties/{id}/quote` against a real
  `SYNTHETIC_BASE_URL`. The scheduled workflow runs every 15 minutes but exits 0
  immediately (logging why) until that repository variable is actually set — set it
  once staging/prod exists and this starts checking it for real, no code change
  needed.
- **`validate.mjs`** — the "alert-rule test" for `alerts.yml`/`dashboard.json`: well-
  formed YAML/JSON, and a cross-check that every `havyn_*` metric name either file
  references matches a real `meterRegistry.counter(...)` in `apps/api`'s source —
  catches a typo'd or renamed-and-forgotten-to-update metric name.

## What hasn't been verified end-to-end

No Docker in this dev sandbox (same gap documented in every session since prompt
09 — see `backend/01-backend-foundation.md`) means:
- `alerts.yml` has never been loaded into a real Prometheus/Alertmanager — "alerts
  fire on injected faults" (this prompt's own acceptance criteria) has not been
  demonstrated, only the rules' YAML structure and metric-name correctness.
- `dashboard.json` has never been imported into a real Grafana — its JSON schema
  correctness was checked, not its rendered appearance.
- `/actuator/prometheus`'s actual scrape output has never been inspected against a
  running instance — the metric names it validates against are derived from source
  code (`meterRegistry.counter("havyn...")` call sites), which is a strong but not
  100%-identical proxy for what Micrometer's Prometheus registry actually names them
  at runtime (verified separately: Micrometer's naming convention — dots become
  underscores, Counters get a `_total` suffix — is well-documented, stable behavior,
  not a guess).

CI closes some of this loop for real: `.github/workflows/ci.yml`'s `backend` job runs
`validate.mjs` on every PR (Docker is available there), and the existing
`ApiApplicationTests.contextLoads()` test will catch it if any of prompt 26's new
Spring beans (`TracingConfig`, the Prometheus registry, structured logging) fail to
wire up — something this sandbox can't confirm on its own.
