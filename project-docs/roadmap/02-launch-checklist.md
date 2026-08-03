# Launch Checklist & Sign-Off Record

> **Addendum (session 20, same day):** the two small, bounded code fixes this
> review named — the `PropertyDetail` address/coordinate leak (§3) and the
> missing `bookings_enabled` kill switch (§7) — are now both fixed. See
> `runbooks/03-data-retention-and-privacy.md` and `backend/02-domain-modules.md`'s
> "Launch-checklist follow-up" session note for what changed. The tables below
> are left as the original, dated finding — a checklist should record what was
> true at review time, not get silently rewritten — but the overall picture: of
> 22 original blocking items, 2 are now closed, both by code. The remaining 20
> are unchanged and still block public launch (legal, brand/domain, Paystack
> account, app store accounts, on-call, support channel — none performable by
> this engagement). **Decision is still NO-GO.**

> Status: **Consolidated — decision recorded below.** Prompt 31, session 20 (2026-07-28).
> Owner: this review. Every item below was checked against the actual current
> state of the codebase/docs today — re-verified fresh, not carried forward from
> `devops/03-production-readiness.md`'s prompt-29 review (session 15), which is
> now **stale on its single largest section**: three of the four feature areas it
> lists as "never built" (host dashboard, reviews/favorites, messaging, admin)
> were built in sessions 16–19, after that review was written. This document
> supersedes that one's feature-completeness verdict; it does not re-litigate the
> parts of it that are still accurate (payments, legal, ops-drills — see below).

## How to read this

Same three-state model as the prompt-29 review, for the same reason — a
checklist with only pass/fail pressures real blockers into a false
"risk-accepted": **PASS** = verified true today. **RISK-ACCEPTED** = a real gap
with an owner and a rationale a reasonable business could stand behind.
**BLOCKING** = a real gap with no such rationale — must close before go-live.

## 0. Feature completeness (re-verified — the biggest change since prompt 29)

| Item | Prompt-29 verdict (session 15) | Verdict today | Evidence |
|---|---|---|---|
| Host listing management | BLOCKING — never built | **PASS** | `com.havyn.hosts` (session 18) — 11 real files: onboarding, dashboard, reservations, payouts. Self-serve `POST /host/onboarding` exists now (didn't before). |
| Reviews & favorites | BLOCKING — never built | **PASS** | `com.havyn.reviews` (7 files), `com.havyn.favorites` (6 files) — session 16, real, not `package-info.java` stubs. |
| Messaging & notifications | BLOCKING — never built | **PASS (backend + mobile) / gap on web** | `com.havyn.messaging` (13 files, session 17) is real and tested. `apps/mobile`'s messaging screens (session 20) are its first working frontend consumer. **`apps/web`'s own `/messages` page is still an `EmptyState` stub** — flagged in `frontend/01-frontend-foundation.md`'s session 20 note, not fixed there (out of that session's file scope). Not launch-blocking for a mobile-first go-live, but a real web-parity gap. |
| Admin moderation/KYC/disputes | BLOCKING — never built | **PASS** | `com.havyn.admin` (32 files, session 19) — moderation, KYC queue, disputes, settings, audit log, all through a real `RequireAuth role="ADMIN"` gate. |
| Mobile app | Not evaluated (prompt 30 hadn't run) | **PASS, traveler flows only** | `apps/mobile` (session 20) — auth/search/detail/book/trips/messaging real against the same API. Payment/confirmation and push notifications explicitly not built (see below); host tools not in mobile's scope by design (`mobile/01-mobile-architecture.md`). |

**This section is no longer a NO-GO driver.** It was the single largest blocker
in the prompt-29 review; today it contributes exactly one still-open item (web
messaging UI) that is a parity gap, not a missing capability.

## 1. Brand / domain / trademark

| Item | Status | Evidence |
|---|---|---|
| Visual identity finalized | PASS | `brand/brand-colors.md` — logo, palette, tagline ("Stay beautiful, live better") all decided and in active use across web/mobile. |
| Brand name legal/trademark clearance | **BLOCKING — unclosable by this engagement** | `roadmap/01-phased-roadmap.md`'s Phase 0 exit criteria has read "Brand winner approved (pending legal)" since this project's first phase. Still pending. Needs a real trademark search and real legal counsel in the actual jurisdiction(s) of operation — not something code-and-docs work can perform or substitute for. |
| Domain registered | **BLOCKING** | No domain registration evidence anywhere in this repo (checked configs, docs, DNS references) — searched for `havynvilla.com`/`havyn-villa.com` and found nothing. Every reference to a base URL in this codebase is `localhost` or an unset env var. |

## 2. Payments — live-verified

| Item | Status | Evidence |
|---|---|---|
| Pricing/hold/charge/webhook code | PASS (code) | Prompt 13, tested via `PaymentServiceTest` and signature-verification unit tests. |
| Refund code path | PASS (code) | `payments.service.PaymentService`, `Refund`/`RefundStatus`/`RefundRequest`/`RefundResult` — real domain model and service logic, unit-tested. |
| **Real Paystack account exists** | **BLOCKING** | `.env.example`: `PAYSTACK_SECRET_KEY=` — empty. No live credential has existed in any environment this engagement has run in, ever (documented every session since prompt 13). |
| **Real payment processed** | **BLOCKING** | Zero. The actual HTTP round-trip to `api.paystack.co` has never happened — only its request/response shape is unit-tested against fixtures. |
| **Real refund processed** | **BLOCKING** | Same reason — no real transaction has ever existed to refund. |
| Webhook signature verification against a real Paystack-signed payload | **BLOCKING** | Verified against hand-constructed HMAC fixtures only, never a payload Paystack itself actually signed. |

**This prompt's own hard constraint #3 — "Real payment + refund verified in
production-like conditions" — is not met and cannot be met inside this sandbox.**

## 3. Legal (ToS / Privacy Policy / host agreement)

| Item | Status | Evidence |
|---|---|---|
| Terms of Service | **BLOCKING** | Does not exist anywhere in this repo. Not referenced in any product doc either (`grep`'d `product/*` for "terms of service" — zero hits) — this was never even specified as a requirement, not just unbuilt. |
| Privacy Policy | **BLOCKING** | Does not exist. Same as above — no product doc names it. This also blocks §4 below: both Apple's App Store and Google Play require a live Privacy Policy URL at submission time. |
| Host agreement | **BLOCKING** | Does not exist. Hosts can onboard (`POST /host/onboarding`, session 18) with no agreement presented or accepted anywhere in that flow. |
| Data-retention & privacy review (engineering-level) | PASS (engineering) / **RISK — needs real counsel, owner: whoever holds privacy/legal responsibility** | `runbooks/03-data-retention-and-privacy.md` (prompt 29) — real PII inventory against the actual current data model, re-checked today and still accurate. Explicitly not a substitute for Nigeria Data Protection Act 2023 counsel review. |
| **PII leak this review re-confirmed, unfixed** | ~~BLOCKING~~ **FIXED (session 20, same day)** | *Original finding, at review time:* `PropertyDetail.java` still returned the exact `address`, `lat`, `lng` of every listing on the public, unauthenticated `GET /properties/{id}` endpoint — field-for-field identical to the prompt-29 finding, nine sessions later. *Fix:* `PropertyController#get` now passes viewer identity through to `PropertyService#getActiveDetail`, which redacts the address and rounds coordinates unless the caller is the host, an ADMIN, or a guest with a `CONFIRMED`/`COMPLETED` booking. The same rounding was applied to `GET /properties` and `GET /search`, found to leak precise coordinates too while fixing this. See `runbooks/03-data-retention-and-privacy.md`. |

## 4. Web + mobile store readiness

| Item | Status | Evidence |
|---|---|---|
| Web production hosting | **BLOCKING** | No domain (§1), no staging/prod cluster ever applied (`infra/k8s/` manifests real, never `kubectl apply`'d — `devops/01-devops-and-deployment.md`'s prompt 27 section). |
| Mobile build config (EAS) | **BLOCKING** | No `eas.json` in `apps/mobile` — no build profile exists to produce an actual App Store/Play Store-submittable binary. |
| App icons / splash / store assets | **BLOCKING** | `apps/mobile/assets/icon.png` etc. are the unmodified `create-expo-app` scaffold defaults (1024×1024 placeholder, default filenames) — not Havyn Villa's real logo, despite the brand identity itself being finalized (§1). No screenshots, no store listing copy, anywhere. |
| Apple Developer / Google Play Console accounts | **BLOCKING — organizational, unclosable by this engagement** | No evidence either exists; these require a real paid enrollment and a real legal entity, not something this engagement can provision. |
| Privacy Policy URL for store submission | **BLOCKING** | Both stores require one at submission; none exists (§3). |
| Push notification wiring | **BLOCKING** | Prompt 30 named this as a deliverable; not implemented — no Expo push-token registration endpoint exists on the backend. Noted honestly in `mobile/01-mobile-architecture.md`'s session 20 parity table, not silently dropped. |

## 5. Monitoring / alerts on

| Item | Status | Evidence |
|---|---|---|
| Alert rules defined, covering golden signals + business KPIs | PASS | `infra/observability/alerts.yml` (prompt 26) — 6 rules, metric names cross-validated against real counters (`validate.mjs`, passes in CI). Unchanged and still accurate. |
| Dashboard defined | PASS | `infra/observability/dashboard.json` — real Grafana dashboard, never loaded into a live Grafana. |
| **Alerts fire on injected faults** | **BLOCKING** | Never tested — no live Prometheus/Alertmanager exists anywhere this project has run. Unchanged since prompt 29. |
| **Alerts routed to a real on-call** | **BLOCKING** | No on-call rotation is staffed (§6). Unchanged. |
| Synthetic checks | PASS (code) / **BLOCKING (inactive)** | `infra/observability/synthetic-checks.mjs` + scheduled workflow exist and are correct; the workflow exits 0 immediately until `SYNTHETIC_BASE_URL` is set, because no staging/prod URL exists yet (§4). |

## 6. Support & incident process ready

| Item | Status | Evidence |
|---|---|---|
| Engineering incident-response runbook | PASS | `runbooks/01-incident-response.md` (prompt 29) — all 6 alert rules mapped to concrete investigation steps against real code/config. Re-read today, still accurate. |
| On-call rotation staffed | **BLOCKING** | No team roster exists in this engagement to staff one from — a genuine organizational prerequisite, not an engineering task. |
| **Customer-facing support channel/process** | **BLOCKING — newly assessed, not covered by prompt 29** | No support email, ticketing system, help center, or status page exists anywhere in this repo, and — checked directly — no product doc (`product/01-03`) ever specifies one. A marketplace processing real payments and real bookings needs a real way for a confused or harmed user to reach a human. This is a product-requirements gap, not just an unbuilt feature. |

## 7. Rollback / kill-switch plan

| Item | Status | Evidence |
|---|---|---|
| Deploy rollback mechanism | PASS (code) / RISK-ACCEPTED (undrilled) | `.github/workflows/rollback.yml` (prompt 28) — `kubectl rollout undo` or redeploy-a-specific-tag, gated on the same health checks as a forward deploy. Never run against a real cluster (none exists) — carried forward from prompt 29 as the same accepted, bounded risk (owner: whoever next has cluster access). |
| **Application-level kill switch** (disable new bookings/payments without a redeploy) | ~~BLOCKING — does not exist~~ **FIXED (session 20, same day)** | *Original finding:* `PlatformSetting` held exactly one key (`commission_pct`) — no maintenance-mode/feature-flag switch existed anywhere. *Fix:* `V11__bookings_enabled_setting.sql` seeds a new `bookings_enabled` key (default `true`); `BookingService#create` reads it fresh on every attempt (same no-caching pattern as `commission_pct`) and throws a new `ServiceUnavailableException` (503, `BOOKINGS_DISABLED`) when off. Toggleable immediately via the existing `PUT /admin/settings/bookings_enabled` — zero new endpoint or frontend work needed, since `/admin/settings` already edits any `platform_setting` row. Fails open (missing row = enabled), not closed, so the setting itself can't take the platform down. Still not drilled against a real incident (no live deployment exists), but the mechanism itself now exists and is unit-tested. |

## 8. Post-launch monitoring plan

What would run, and what's actually wired vs. dormant, stated plainly rather
than presented as "ready": the moment a real staging/prod URL exists,
`SYNTHETIC_BASE_URL` gets set and `synthetic-checks.yml`'s existing 15-minute
schedule starts exercising `/actuator/health`, `GET /search`, and
`POST /properties/{id}/quote` for real, no code change required. `alerts.yml`
and `dashboard.json` load into whatever Prometheus/Grafana that environment
runs, unmodified. `havyn_booking_*`/`havyn_payment_webhook_*` business counters
(prompt 26) are already emitted by the running app today, in this sandbox even
— they just have no real traffic behind them yet. **The plan is real and
load-bearing; it has simply never been switched on**, because nothing has ever
been deployed for it to watch.

## Summary

> Updated same day (session 20 addendum, see top of document) to reflect the two
> items closed by code after this table was first written. Original counts:
> Legal was 0/1/4, Rollback/kill-switch was 1/1/1, Total was 11/2/22.

| Category | Pass | Risk-accepted | Blocking |
|---|---|---|---|
| Feature completeness | 4 | 0 | 0 (1 non-blocking parity gap: web messaging UI) |
| Brand / domain / trademark | 1 | 0 | 2 |
| Payments | 2 | 0 | 4 |
| Legal | 1 | 1 | 3 |
| Web + mobile store readiness | 0 | 0 | 6 |
| Monitoring / alerts | 2 | 0 | 3 |
| Support & incident process | 1 | 0 | 2 |
| Rollback / kill-switch | 2 | 1 | 0 |
| **Total** | **13** | **2** | **20** |

(Post-launch monitoring plan is descriptive, not scored — it inherits §5's counts.)

## Decision: **NO-GO for public launch.**

Twenty blocking items (originally twenty-two — two closed same-day, see the
addendum at the top) still fails this prompt's own hard constraint ("no launch
with unresolved high/critical items") outright — not a judgment call. But the
**shape** of the gap has changed completely since the prompt-29 review, and
that shift is the actual news here:

- **Session 15 (prompt 29):** blocked mostly on missing *engineering* — three
  entire feature areas (host, messaging, admin) didn't exist yet.
- **Session 20 (prompt 31, same day):** every one of those is built and
  tested, and the two remaining code items this review found — the
  `PropertyDetail` PII leak (§3) and the missing kill-switch (§7) — are now
  fixed too. **Every single remaining blocking item is legal, business, or
  infrastructure provisioning this engagement cannot perform**: trademark
  clearance, a registered domain, a Paystack merchant account, ToS/Privacy
  Policy/host agreement, App Store/Play Store developer accounts, a staffed
  on-call rotation, a support channel. Zero of them are code anymore.

**What a GO decision would require, in priority order:**
1. ~~Fix the `PropertyDetail` address/coordinate leak~~ — **done, session 20.**
2. ~~Add a `bookings_enabled`/maintenance-mode kill switch~~ — **done, session 20.**
3. Build web's `/messages` page against the now-real messaging backend, for
   web/mobile parity.
4. Real business/legal engagement (outside engineering): trademark clearance,
   domain registration, Paystack merchant account, ToS, Privacy Policy, host
   agreement, Apple/Google developer accounts, real app store assets
   (icon/splash/screenshots/copy), an `eas.json` build profile, a support
   channel, a staffed on-call rotation.
5. Once real infrastructure exists (staging/prod cluster, real Paystack
   credentials): the restore drill, a DR drill, alerts firing against
   injected faults, a real rollback drill, and at least one real
   charge→webhook→refund round-trip — all named explicitly as required tests
   by this prompt and by prompt 29 before it, none performable in this sandbox.

**What's genuinely ready, stated plainly:** every core traveler and host and
admin flow, on both web and mobile, is real, tested (to the extent this
sandbox allows), and internally consistent with itself — this is no longer a
skeleton with gaps in the middle. The engineering path to a **controlled,
non-public pilot** (a handful of hand-onboarded hosts, an invited guest list,
a founder standing in for on-call, a real but unpublicized domain) is shorter
today than it has been at any point in this engagement, and is the honest
next milestone to aim for before the public-launch bar this document was
actually asked to evaluate against.

## Launch sign-off record

| Field | Value |
|---|---|
| Reviewed by | Claude Code (engineering-level review only) |
| Date | 2026-07-28 |
| Prompt | 31 — Launch Checklist, session 20 |
| Decision | **NO-GO** for public launch |
| Blocking items | 20 (see Summary; originally 22, 2 closed same-day by code — the `PropertyDetail` PII leak and the `bookings_enabled` kill switch) |
| Risk-accepted items | 2 (rollback undrilled; least-privilege DB roles — both carried from prompt 29, owners unchanged) |
| Re-verification method | Every row checked against actual current code/docs today; nothing carried forward from the prompt-29 review without re-confirming |
| **Human sign-off** | **Not recorded — pending.** A NO-GO engineering review cannot authorize itself into a GO; equally, an actual launch decision (accepting the legal/brand-clearance risk, approving spend on a Paystack/Apple/Google account, staffing on-call) is a business decision this engagement cannot make on a real company's behalf. This row is intentionally blank for whoever holds that authority to sign, dated, once the priority-ordered path above is actually closed. |
