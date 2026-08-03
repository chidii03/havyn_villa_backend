# Incident Response Runbook

> Status: Draft · Prompt 29 (production readiness). Written against real,
> currently-emitted signals — every alert named below is a real Prometheus rule in
> `infra/observability/alerts.yml` (prompt 26), not a hypothetical. **Never drilled
> against a live incident or a real on-call rotation** — see this doc's own closing
> section and `devops/03-production-readiness.md`'s go/no-go record.

## Severity levels

| Level | Definition | Examples | Response |
|---|---|---|---|
| **SEV1** | Guests/hosts can't complete core flows (search, book, pay) platform-wide, or a security/data-integrity incident | API fully down; `HighHttpServerErrorRate` sustained platform-wide; payment webhooks silently failing (money not reconciling); a confirmed security breach | Page immediately, all hands, status page updated |
| **SEV2** | A significant but partial degradation | One region/AZ degraded; `HighP95Latency` on a core path; `DatabaseConnectionPoolSaturated` | Page on-call, no status page update unless it worsens |
| **SEV3** | Real but non-urgent | `RateLimitRejectionsElevated` with no user impact confirmed; a single non-critical background job failing | Ticket, business-hours investigation |

## On-call & escalation

**Not staffed yet** — there is no real on-call rotation, paging tool integration,
or status page for this project today (no deployed environment exists to page
anyone about — see `devops/03-production-readiness.md`). This section documents
the *structure* a real rotation should follow once one exists, so standing it up
is "wire this runbook into PagerDuty/Opsgenie/etc." rather than designing an
escalation policy from scratch during a live incident:

1. Primary on-call acknowledges within 5 min (SEV1) / 15 min (SEV2).
2. No acknowledgment within that window → escalate to secondary on-call.
3. SEV1 unresolved after 30 min → escalate to engineering lead.
4. Any incident touching payments or auth → security lead notified regardless of
   severity (see security/01-security-plan.md's audit-logging additions, prompt
   26 — `RefreshTokenService`'s reuse-detection WARN and
   `PaystackPaymentProvider`'s invalid-signature WARN are exactly the log lines
   to search first for either).

## Per-alert response

Each of `infra/observability/alerts.yml`'s six rules, in the same order as that
file, with what to actually go look at — not just "investigate."

### HighHttpServerErrorRate
**Means:** >5% of requests 5xx-ing for 5+ minutes.
1. Check `/actuator/prometheus`'s `http_server_requests_seconds_count` broken down
   by `uri` — is it one endpoint or everything? One endpoint narrows this fast.
2. Grep JSON logs (prompt 26 — structured, `correlationId`/`traceId` fields) for
   `"level":"ERROR"` in the same window; `GlobalExceptionHandler` logs every
   unhandled exception with its `correlationId`, but never a stack trace to the
   *client* — the server-side log has the trace, use it.
3. Check `DatabaseConnectionPoolSaturated` and Redis reachability — a downstream
   dependency outage is the most common real cause of a sudden platform-wide 5xx
   spike, not application logic.
4. If tied to a recent deploy: this is what `.github/workflows/rollback.yml`
   exists for (prompt 28) — don't debug a bad deploy live in production, roll it
   back, then debug the rolled-back image at leisure.

### HighP95Latency
**Means:** p95 > 500ms on some route for 10+ minutes.
1. Which `uri` (the alert's own label) — cross-reference against
   `apps/api/loadtest/hot-paths.js`'s own per-scenario thresholds (prompt 25) if
   it's search/property-detail/quote/booking; those already have expected
   baselines to compare against.
2. `property_detail` specifically slow → check `PropertyCacheService`/
   `SearchCacheService` hit rates aren't near zero (Redis reachable? TTL too
   short?) — this is exactly the path prompt 25's caching work targeted.
3. Check `DatabaseConnectionPoolSaturated` — connection-pool starvation shows up
   as latency before it shows up as errors.
4. Check OpenTelemetry traces (prompt 26 — `LoggingSpanExporter` output, or a
   real OTLP backend once `MANAGEMENT_OTLP_TRACING_ENDPOINT` is configured) for
   where time is actually going: DB, Redis, or an outbound call to
   Paystack/Cloudinary/Maps.

### PaymentWebhookFailuresElevated
**Means:** `invalid_signature` or `unmatched_ref` outcomes elevated for 5+ minutes.
**This is a SEV1-track alert regardless of volume** — payment integrity is not a
"wait and see" category.
1. `invalid_signature` repeatedly → check whether `PAYSTACK_SECRET_KEY` was
   recently rotated on one side (this app or Paystack's dashboard) without the
   other; a real spoofing attempt is the other explanation — check source IPs in
   the same log lines (`PaystackPaymentProvider`'s WARN, prompt 26) against
   Paystack's documented webhook IP ranges.
2. `unmatched_ref` repeatedly → check for an environment mismatch (this app's
   webhook endpoint receiving events from the *wrong* Paystack account/environment
   — e.g., a test account misconfigured to point at production's URL).
3. Either way: confirm no bookings are stuck `PENDING` past their
   `hold_expires_at` that *should* have confirmed — cross-check against
   `BookingHoldExpiryRateElevated` below.

### BookingHoldExpiryRateElevated
**Means:** holds expiring unusually often for 15+ minutes.
1. First question: is checkout actually broken, or are guests just... not
   completing checkout (a real, non-incident explanation)? Check
   `PaymentWebhookFailuresElevated` and `POST /payments/intent` error rates first
   — if those are clean, this may not be an incident at all.
2. If payments look healthy but holds still expire fast: check
   `havyn.booking.hold-duration-minutes` (application.yml) wasn't accidentally
   changed, and that `BookingService.sweepExpiredHolds`'s scheduled interval
   wasn't misconfigured to run too aggressively.

### DatabaseConnectionPoolSaturated
**Means:** Hikari pool >90% utilized for 5+ minutes.
1. `leak-detection-threshold: 30s` (application.yml, prompt 25) logs a WARN with
   a full stack trace for any connection held that long — check for that WARN
   *first*, before assuming this is legitimate load and reaching for
   `maximum-pool-size`.
2. If no leak WARN: check whether this correlates with a traffic spike
   (`HighP95Latency`/request-rate panels on `infra/observability/dashboard.json`)
   or a single slow query (Postgres's own `pg_stat_activity` — a managed
   provider's dashboard equivalent).
3. Raising `DB_POOL_MAX_SIZE` (application.yml's env override) is a legitimate
   *temporary* mitigation, not a fix, if the root cause is a slow query or a leak.

### RateLimitRejectionsElevated
**Means:** sustained rejections on one rule (`/api/v1/auth`, `/search`, or
`/bookings` — `application.yml`'s three configured rules, prompt 24) for 5+ min.
1. Check the source IP distribution in the WARN logs (`RateLimitFilter`, prompt
   26) — concentrated on a handful of IPs = likely a real attack (credential
   stuffing on `/auth`, scraping on `/search`); spread across many IPs = likely a
   legitimate traffic spike outgrowing the configured limit.
2. Attack → nothing further needed, the rate limiter is doing its job; consider a
   temporary IP-level block upstream (WAF/CDN) if it's sustained and targeted.
3. Legitimate spike → raise the specific rule's `limit` in `application.yml` and
   redeploy; don't raise all three rules "to be safe" — `/api/v1/auth` staying
   tight is a deliberate security control (security/01-security-plan.md), not an
   arbitrary number.

## What hasn't been verified

This runbook was written against real alert rules and real code paths (every
file/config reference above was checked against the actual current source, not
assumed), but has **never been drilled** — no fault was ever actually injected
into a running instance of this app to confirm an alert fires and this runbook's
steps actually lead somewhere useful. That drill requires a real deployed
environment (prompts 27/28's infra, never applied to a real cluster — see
`infra/k8s/README.md`) and is the concrete next step once one exists, not
something this session could complete.
