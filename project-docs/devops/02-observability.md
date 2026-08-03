# Observability

> Status: Draft · Phase 12 · Owner: DevOps/Backend.

## Logs
Structured JSON logs with correlation/trace id propagated from API to modules; no PII/secrets in logs; log levels per env.

## Metrics
Actuator + Micrometer → Prometheus-compatible metrics: request rate/latency (p50/p95/p99), error rates, DB pool, cache hit ratio, booking success/failure, payment success/failure, queue/hold counts. Business KPIs surfaced too (bookings, GMV, conversion).

## Tracing
Distributed tracing (OpenTelemetry) across API → DB/Redis/providers to debug latency and failures.

## Dashboards & alerts
Golden-signal dashboards; alerts on error-rate spikes, p95 latency, payment webhook failures, booking-hold anomalies, DB saturation. On-call runbook links (see engineering incident-response practices).

## Health
Actuator liveness/readiness for orchestration; synthetic checks on core flows (search, quote) in staging/prod.

## Prompt 25 (session 11) — status and deviations

**Metrics/tracing/dashboards (Micrometer→Prometheus, OpenTelemetry, golden-signal
dashboards) are still not built** — that's prompt 26's job, not this one; this
prompt's own prerequisite list names this doc as context to read, not a deliverable
to complete. What prompt 25 actually added, in the closest thing this repo has to a
"metrics" surface right now — a load test with explicit, checked latency targets:

- **`apps/api/loadtest/hot-paths.js` (k6)** — four scenarios against real, booted
  infra: `search` (20 VUs, 30s, p95 < 300ms), `property_detail` (20 VUs hammering
  the same id — this is what the new cache exists for, p95 < 150ms), `quote` (10
  VUs, p95 < 200ms), `booking` (5 VUs, real create+cancel, p95 < 400ms, error rate
  excluding legitimate concurrent-date-collision 409s < 5%). **These targets are
  reasoned, not measured** — there's no Docker in this dev sandbox to run k6
  against a real Postgres/Redis here, so there's no observed baseline to tune
  against (same limitation as every Testcontainers IT and the Playwright E2E suite
  — see `testing/01-testing-strategy.md`'s prompt 23 notes for the fuller
  explanation of why). `.github/workflows/ci.yml`'s new `load-test` job runs this
  for real on every PR, where Docker exists — revise the thresholds once real
  numbers come back from there, don't treat them as validated.
- **`apps/api/loadtest/seed.mjs`** seeds 20 fixture properties (direct SQL, same
  ephemeral-test-DB reasoning as `apps/web/e2e/seed.ts`) so `search` has more than
  one row to actually filter/paginate over.
- **Connection pool** (`application.yml`'s new `spring.datasource.hikari.*`) is now
  explicit rather than implicit Boot defaults — sizes are HikariCP's own
  small-pool-for-OLTP guidance, not load-test-derived (same "no Docker" caveat).
  `leak-detection-threshold: 30s` is new and cheap — worth keeping regardless of
  what real tuning eventually lands on.

## Prompt 26 (session 12) — status and deviations

Everything this doc's own sections above describe as missing after prompt 25 is now
implemented — real, working code, not scaffolding — with one honest exception
(dashboards/alerts have never been loaded into a live Prometheus/Grafana; see below).

**Logs** — `apps/api/src/main/resources/logback-spring.xml`, new. Replaces the plain-
text `logging.pattern.console` line with `LogstashEncoder` (JSON), including
`correlationId`/`traceId`/`spanId` MDC fields whenever present. **A real PII gap was
found and fixed, not just avoided going forward:** `AuthService`'s failed-login log
line (added prompt 24) logged the raw attempted email — personal data, and exactly
what this prompt's "No PII/secrets in logs" constraint exists to catch. Now logs a
12-hex-char SHA-256 prefix instead (`AuthServiceTest`'s three new tests pin this:
the raw address never appears in the captured log output, the hash is deterministic
so ops can still spot the same address being hammered repeatedly, and a *successful*
login logs the user id, never the email, confirming the already-correct paths stayed
correct). Every other log call added since prompt 24 (payments, rate-limiting,
booking) was re-checked against this same bar — none of them log a password, token,
or other credential; IP addresses in rate-limit logs are a deliberate, documented
exception (security/01-security-plan.md's IP-keyed rate limiting cannot work without
logging the IP).

**Metrics** — `micrometer-registry-prometheus` (build.gradle), `/actuator/prometheus`
exposed (not in `SecurityConfig`'s public paths — see below). Golden-signal HTTP
metrics (`http_server_requests_seconds_*`, p50/p95/p99 histograms) are Spring Boot's
own auto-instrumentation, now actually exported. Real business counters were added at
the actual decision points, not synthesized after the fact: `havyn_booking_created_
total`, `havyn_booking_create_failed_total{reason}` (dates_unavailable/price_changed),
`havyn_booking_cancelled_total{refunded}`, `havyn_booking_hold_expired_total{trigger}`
(the scheduled sweep and the lazy per-property expiry both feed this — see
`BookingService`), `havyn_payment_webhook_total{provider,outcome}` (charge_succeeded/
charge_failed/already_terminal/unmatched_ref/invalid_signature), and `havyn_rate_
limit_rejected_total{rule}`. `hikaricp_connections_*` (pool utilization) comes free
from Micrometer's existing Hikari auto-instrumentation once a `MeterRegistry` bean
exists — no extra code. `BookingServiceTest`/`PaymentServiceTest` assert real counter
values via `SimpleMeterRegistry`, not mocks.

**Tracing** — `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-logging`
(build.gradle); Spring Boot auto-instruments JDBC (Postgres), Lettuce (Redis), and
outbound `RestClient` calls (Paystack/Cloudinary) once these are on the classpath —
no manual span code anywhere. `TracingConfig` registers a `LoggingSpanExporter` bean
so spans are visible in console/log output with zero external collector required;
`management.otlp.tracing.endpoint` is deliberately left unset by default (an empty-
string placeholder would still "count as configured" and try to export to nowhere —
see `application.yml`'s inline comment) but works the moment
`MANAGEMENT_OTLP_TRACING_ENDPOINT` is set at deploy time, no code change needed.
100% sampling in the current (dev/test/CI) config — revisit for real prod traffic
volume. **A real MDC key collision was caught before it shipped, not discovered
later:** Micrometer Tracing's own MDC population defaults to the keys `traceId`/
`spanId` — identical to what `CorrelationIdFilter` was already manually writing to
MDC under the name `traceId` (prompt 08). Left as-is, the app-level correlation id
and the real OTel trace id would have silently overwritten each other in every log
line. `CorrelationIdFilter`'s own key is now `correlationId` — a distinct, genuinely
complementary value (always present vs. only present in a sampled trace), not a
collision. Confirmed via `SecurityHeadersTest`/`RbacTest` (both re-verified passing
after `SecurityConfig`-adjacent changes) that the API's own `traceId` JSON response
field (a different, pre-existing concept — see `architecture/03-api-design.md`'s
error envelope) was untouched by this rename; that field's tests
(`GlobalExceptionHandlerTest`) don't depend on the MDC key name at all.

**Dashboards & alerts as code**, new — `infra/observability/`: `alerts.yml`
(Prometheus rules for every item this doc's own "Dashboards & alerts" section
names: HTTP error rate, p95 latency, payment webhook failures, booking-hold
anomalies, DB pool saturation, plus rate-limit rejection spikes), `dashboard.json`
(a real Grafana dashboard — golden signals + the same business KPIs the alerts
watch). `validate.mjs` is the closest thing to a real "alert-rule test" achievable
without a live Prometheus: parses both files, and cross-checks every `havyn_*`
metric name either one references against `apps/api`'s actual
`meterRegistry.counter(...)` call sites — this exact check already caught nothing
wrong (all 6 metrics matched cleanly), but it's there so a future rename doesn't
silently break an alert. Wired into `.github/workflows/ci.yml`'s `backend` job.
**Honestly unverified beyond that:** neither file has ever been loaded into a real
Prometheus/Grafana — "alerts fire on injected faults" (this prompt's own acceptance
criteria) has not been demonstrated, only structural/naming correctness. See
`infra/observability/README.md` for the full breakdown.

**Synthetic checks**, new — `infra/observability/synthetic-checks.mjs` +
`.github/workflows/synthetic-checks.yml` (scheduled every 15 minutes). Real checks
against `/actuator/health`, `GET /search`, `POST /properties/{id}/quote` — but the
scheduled workflow exits 0 immediately, logging why, until the `SYNTHETIC_BASE_URL`
repository variable is actually set. No staging/prod exists yet (prompts 27/29) —
setting that variable once one does is the only step left to make this run for real,
no code change required.

**A real, deliberate deployment gap, stated rather than silently worked around:**
`/actuator/prometheus` is exposed (`management.endpoints.web.exposure.include`) but
NOT added to `SecurityConfig`'s public paths — a scraper needs a valid bearer token
by default. Spring Boot's own recommended production pattern (a separate
`management.server.port`, kept off the public load balancer entirely) would avoid
needing auth on the metrics endpoint at all, but was **not** done here: it would move
`/actuator/health` too, breaking `.github/workflows/ci.yml`'s existing `e2e` and
`load-test` jobs, which poll `http://localhost:8080/actuator/health` on the main
port (built in prompts 23/25). Fixing this properly belongs with prompts 27/29's
real deployment topology decisions, not as an incidental side effect here.

**Verification performed:** `./gradlew compileJava compileTestJava` clean.
`./gradlew test` — 204 tests (was 201), 161 pass / 43 fail (was 158/43 — the 3 new
tests, all `AuthServiceTest`, all pass; zero new Docker-dependent failures). Measured
`auth.domain`'s unit-only coverage directly: 24% (prompt 23's baseline) → 38% now
that `AuthServiceTest` exists — real improvement, still short of the 80% gate, which
remains correctly dependent on `AuthFlowIT`/`RefreshTokenServiceIT` (Testcontainers)
to close the gap in CI, per build.gradle's own documented reasoning.
`infra/observability/validate.mjs` passes locally (no Docker needed — pure
file/source-code validation). CI YAML (all four workflow files) parsed and validated
locally; none has run on GitHub yet.
