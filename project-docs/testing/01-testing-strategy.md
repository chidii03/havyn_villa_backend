# Testing Strategy

> Status: Draft · Phase 11 (but tests written every phase) · Owner: QA Architect.

## Philosophy
Tests are a per-phase deliverable, not an afterthought. Every implementation prompt REQUIRES tests and verification before completion. The backend is the authoritative layer, so business logic (pricing, booking, payments) gets the deepest coverage.

## Test pyramid
- **Unit** (most): pricing calculator, booking rules, policy/refund logic, validators, mappers.
- **Integration**: repositories + services with **Testcontainers** (Postgres, Redis); webhook handling; auth flows.
- **API/contract**: MockMvc/WebTestClient against endpoints; OpenAPI-driven contract tests shared by web + mobile.
- **Frontend**: component tests (React Testing Library), form/validation, a11y (axe), key flows.
- **E2E** (fewer, high-value): search→book→pay→confirm; host publish; cancel/refund; admin moderate. Playwright.
- **Non-functional**: load test booking/search hot paths; security tests (authz/IDOR, rate limits, webhook signature).

## Critical test cases (must exist)
- Pricing correctness across nights, fees, discounts, taxes, commission, currency.
- **Double-booking**: concurrent confirms → exactly one succeeds.
- Cancellation windows → correct refund state/amount.
- Payment webhook verification + idempotency (no double charge/credit).
- RBAC/object-level authorization (IDOR) on bookings/listings.
- Review eligibility gating.

## CI gates
Lint + typecheck + unit + integration on every PR; coverage threshold on domain modules; a11y checks; block merge on failure. E2E on main/nightly.

## Environments & data
Ephemeral test DBs (Testcontainers); factories/fixtures — **no fake production data** shipped. Seed only reference data.

## Prompt 23 (session 9) — status and deviations

**The precondition this prompt states ("Depends on: all feature prompts") is not
actually met.** Auditing the repo before touching anything found that prompts 15
(reviews/favorites), 16 (messaging/notifications), 17 (host dashboard), and 18
(admin platform) are **not implemented** — backend `com.havyn.{reviews,favorites,
messaging,notifications,hosts,admin}` are each a single `package-info.java` stub
("Implemented in prompt N"), and the frontend `/host`, `/admin`, `/wishlists`,
`/messages` routes are `EmptyState` skeletons. This directly shapes what "complete
the test suite" can mean right now — stated plainly rather than silently worked
around:
- **Review eligibility gating** (a strategy-doc "critical case") has no code to
  test — not attempted.
- **"E2E for host publish" / "admin moderate"** can't be driven through a real UI —
  there's no self-serve HOST/ADMIN role-upgrade path and no dashboard to drive even
  if there were. The backend API-level flow (create→submit→publish) is already
  covered by `PropertyFlowIT`, using an in-process test-only role grant; that's the
  authoritative-layer coverage, and it's what exists. See `apps/web/e2e/README.md`
  for the full breakdown of what's covered vs. not, and why.

**What *was* already well-covered before this session** (confirmed by reading the
actual test files, not assumed): pricing correctness (`PricingServiceTest`),
double-booking (`BookingConcurrencyIT`, plus ADR-008's exclusion constraint tested
directly), cancellation/refund (`CancellationPolicyCalculatorTest`), webhook
idempotency (`PaymentServiceTest`, `PaymentFlowIT`'s replay assertion), and
RBAC/object-level authorization on the real business endpoints — not just
`RbacTest`'s generic demo controller: `BookingFlowIT` asserts a second guest gets
403 both viewing and cancelling someone else's booking, `PropertyFlowIT` asserts a
second host gets 403 editing/reading someone else's listing. This session's job was
mostly to add the pieces that were genuinely missing: coverage gates, CI, and E2E.

**Backend — JaCoCo coverage gate** (`apps/api/build.gradle`): 80% line coverage on
`pricing.service`, `booking.domain`, `booking.service`, `auth.domain` — the four
packages carrying money-critical logic. Measured locally with unit/Mockito tests
only (this dev sandbox has no Docker daemon, so the Testcontainers `*FlowIT`/`*IT`
suites can't run here — same gap documented every session since session 4):
`pricing.service` and `booking.domain` already clear 80% from unit tests alone.
`auth.domain` (24%) and `booking.service` (72%) don't — both have logic that only
executes against live Redis (refresh-token session families, booking holds/locks),
deliberately tested via Testcontainers rather than mocked Redis. The full 80% gate
is expected to pass once those IT suites run too (in CI), but that combined number
has **not** been empirically verified end-to-end in this sandbox — only the gate's
mechanics were (a deliberately-uncovered unit-only run correctly failed with the
exact expected ratios before this doc was written).

**Frontend — Vitest coverage gate** (`vitest.config.ts`): v8 provider, thresholds
70% lines/statements, 65% functions/branches, scoped to `src/**/*.{ts,tsx}` minus
generated shadcn primitives and layout/middleware shells. Auditing actual coverage
surfaced real, live gaps — not just low numbers on out-of-scope skeleton pages: the
login/signup/forgot-password/reset-password forms and `AuthProvider` itself
(the app's central auth context, used everywhere but never tested directly) had
**zero** test coverage. Added `login-form.test.tsx`, `signup-form.test.tsx`,
`forgot-password-form.test.tsx`, `reset-password-form.test.tsx`,
`auth-provider.test.tsx`, `require-session-cookie.test.ts`, plus direct tests for
the `lib/api/*` client functions (`auth.ts`, `bookings.ts`, `properties.ts`,
`search.ts`) and the `verify-email`/`reset-password` page-level branching logic.
Coverage went from 58%/58%/52%/59% (stmts/branch/funcs/lines) to 81%/71%/73%/84%.
**A real bug came out of writing `auth-provider.test.tsx`, not a hypothetical one:**
`AuthProvider.logout()` didn't catch `authApi.logout()`'s own failure — a network
error during logout produced an unhandled promise rejection in production (the
`finally` block still cleared client-side state correctly; the rejection itself was
never caught). Fixed with a `catch` before the `finally`.

**E2E (Playwright)**, `apps/web/e2e/` — installed fresh (`@playwright/test`, `pg` for
fixture seeding), config + 4 spec files: `auth.spec.ts` (signup→redirect,
logout→redirect-to-login, login, wrong-credentials error), `search-and-reserve.spec.ts`
(home→listing→real calendar interaction via `[data-day]`→real `POST /quote`→real
`POST /bookings`, ending at the honest "Dates held" state — no live Paystack
credentials exist anywhere this project has run, so this is the same stopping point
`BookingWidget` itself uses), `trips.spec.ts` (view + real cancel), and
`mobile-viewport.spec.ts` (Pixel 7 project — **real rendered `boundingBox()`
touch-target sizes**, the thing jsdom fundamentally cannot check; closes prompt 22's
own explicitly-deferred "E2E on mobile viewport" gap). Fixture data (one ACTIVE
property) is seeded via direct SQL against the ephemeral test Postgres
(`e2e/seed.ts`) rather than through the UI, because — see above — there's no
self-serve host-publish flow to drive; this mirrors `PropertyFlowIT`'s own
`registerHost()` test-only shortcut, just done from an external process.
**Playwright itself was verified to launch and drive a real Chromium browser in
this sandbox** (a throwaway smoke check against a `data:` URL, removed after
confirming); running the actual specs needs a live Postgres/Redis/API/web stack,
which this sandbox can't provide (no Docker) — same category of gap as the backend
ITs, closed for real by the CI workflow below, not by this sandbox.

**CI** — `.github/workflows/ci.yml`, new (none existed before this session):
`backend` (Gradle test + JaCoCo verification — GitHub's `ubuntu-latest` runners have
Docker preinstalled, so the Testcontainers suites that can't run locally run for
real here), `frontend` (lint, typecheck, test with coverage gate — a11y assertions
are inline `jest-axe` calls within those same tests, not a separate step — then
build), `e2e` (Postgres + Redis + **Mailhog** as service containers, backend booted
via `bootRun` and polled on `/actuator/health`, frontend built + started and polled,
then Playwright runs against the real thing). The Mailhog service specifically was
not an assumption — `AuthService.register` calls `SmtpMailer` synchronously inside
the same `@Transactional` method with no `try/catch`; without a reachable SMTP
endpoint, every spec that signs up (nearly all of them, since reserving requires
auth) would 500 on step one. Caught by reading `AuthService`/`SmtpMailer` before
writing the workflow, not by a failed CI run (this sandbox can't run the workflow to
find out the hard way).

**Verification performed:** `./gradlew test` (baseline: 193 tests, 153 pass / 40 fail
— all 40 failures are `DockerClientProviderStrategy`, the same pre-existing,
already-documented gap, not a regression); JaCoCo gate mechanics verified against a
deliberately unit-only run; `npm run lint`, `npm run typecheck`, `npm run
test:coverage` (×3 for stability — 128 tests, all passing, coverage gate passing
with margin), `npm run build`, all green in `apps/web`. The CI workflow's YAML was
parsed/validated and every script it invokes was independently confirmed to exist
and run correctly; the workflow itself has **not** run on GitHub Actions — that
requires a push/PR, which is the user's call, not something to do unprompted.
