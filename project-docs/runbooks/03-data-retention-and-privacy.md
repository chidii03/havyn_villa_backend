# Data Retention & Privacy Review

> Status: Draft · Prompt 29. This is an engineering-level PII inventory and
> retention policy proposal, written against the actual current data model and
> actual current code — **not a legal compliance sign-off**. See
> `devops/03-production-readiness.md`'s go/no-go record: real legal/privacy-counsel
> review (Nigeria Data Protection Act 2023 / NDPR, and any other jurisdiction this
> product actually operates in) is a named, unclosable-by-this-engagement blocking
> item, same category as brand/trademark clearance.

## Why Nigeria specifically

`CLAUDE.md`/`AGENTS.md` position this product Nigeria-first (`Naira` currency
throughout, Paystack as the only implemented payment provider, `en-NG` locale for
currency formatting). Nigeria's data protection law is the **Nigeria Data
Protection Act 2023** (which superseded the earlier NDPR) — the relevant regime
here, not GDPR, though the practical obligations below (lawful basis, minimization,
retention limits, breach notification, data-subject rights) are broadly similar in
shape to GDPR's, which is why they'll look familiar. If/when this product expands
beyond Nigeria, each new jurisdiction needs its own review — not assumed covered by
this one.

## PII inventory (what's *actually* collected today, per the current data model)

| Table | PII fields | Purpose | Who can access it (per `security/01-security-plan.md`'s RBAC) |
|---|---|---|---|
| `app_user` | email, password_hash (Argon2id, never plaintext) | Auth | User themself; never returned to any other user |
| `profile` | full_name, phone (nullable), avatar_url (nullable) | Identity shown to counterparties (host/guest) | Owner; counterparties in a shared booking see full_name only (`BookingPropertySummary` — never phone/email, confirmed via `booking/web` DTOs) |
| `property` | address, city, state, country, lat/lng | Listing location | **Fixed — launch-checklist follow-up, session 20.** `GET /properties/{id}` (`PropertyController#get`), `GET /properties` (list), and `GET /search` all now return `address: null` and `lat`/`lng` rounded to 2 decimal places (~1.1km) unless the caller is the property's own host, an ADMIN, or a guest with a `CONFIRMED`/`COMPLETED` booking for that property (`PropertyService#getActiveDetail` decides; `PropertyDetail`/`PropertySummary`/`SearchResultItem#withApproximateLocation()` do the redaction). A `PENDING` hold alone — trivially creatable by anyone — does **not** unlock the exact address, matching real payment-gated reveal semantics. `architecture/04-integrations.md#2`'s original design intent ("show a circle/area, not the exact pin, until booked") is now actually enforced server-side, not just as a frontend map-display choice. |
| `booking` | guest_id, check-in/out dates, guest count | Reservation record | Guest; host (via `BookingPropertySummary`, which deliberately excludes guest PII beyond what's needed — see `database/01-data-model.md`'s session 6 notes on why `booking.guest_id` is a plain UUID, not a JPA association) |
| `payment`/`transaction` | amount, currency, provider reference, `raw_payload` (full webhook body, stored as `text`) | Financial record | Backend only — never returned to any client (`PaymentController` never echoes `raw_payload`) |
| `refresh_token` (Redis, not Postgres) | Hashed session identifiers, TTL-bound | Session management | Backend only; auto-expires (`JWT_REFRESH_TTL_DAYS`) |
| verification/password-reset tokens (Redis) | SHA-256 hash of the raw token, never the raw token itself | One-time auth flows | Backend only; single-use (`GETDEL`) + TTL |

**Not yet built, so nothing to inventory yet — flagged so this review gets
revisited when they are, not forgotten:** `VerificationRequest` (KYC — host
identity documents; `security/01-security-plan.md#kyc` already says "store
minimal PII, restrict access, audit all access" as a requirement, but there's no
code yet to check that requirement against), `message` (guest↔host chat content —
prompt 16, not built), `review` (prompt 15, not built).

## Retention policy (proposed — needs legal sign-off, not just engineering review)

| Data | Proposed retention | Rationale |
|---|---|---|
| Account data (`app_user`, `profile`) | Retained while account is active; deleted or anonymized within 30 days of a verified deletion request | Right-to-erasure-style request handling; 30 days covers fraud/dispute cool-off |
| Booking/payment records | Retained 7 years post-completion | Standard financial/tax record-keeping period (matches typical statutory bookkeeping requirements — **confirm the actual Nigerian statutory period with counsel**, not assumed from general practice) |
| Refresh tokens, verification/reset tokens | Auto-expire via Redis TTL (already implemented — `JWT_REFRESH_TTL_DAYS`, 24h/1h token TTLs, `security/01-security-plan.md`) | Already correct, no policy gap |
| Audit/security logs (structured JSON, prompt 26) | 90 days hot, longer in cold storage if a log-aggregation retention policy is configured | No log-shipping/retention infra actually configured yet — this is a **target**, not a current setting (see `devops/02-observability.md`) |
| Rejected/abandoned host applications (once KYC exists) | TBD — needs a specific policy once `VerificationRequest` is built | Flagged now so it's designed in from the start, not bolted on |

## What's already right (verified, not assumed)

- Passwords: Argon2id, never plaintext, never logged (`security/01-security-plan.md`).
- No account-enumeration leak on login/reset (`AuthFlowIT` asserts byte-identical
  responses — session 9).
- Auth logs a SHA-256 hash of a failed-login email, never the raw address
  (session 12 — a real fix, not a design that was always correct).
- Payment webhook `raw_payload` never returned to any client — confirmed by
  reading `PaymentController`'s actual response DTOs, not assumed.
- Refresh/verification tokens are hashed-at-rest in Redis, single-use, TTL-bound.

## Real open gaps (engineering-level, distinct from "needs legal sign-off")

1. ~~**`PropertyDetail`'s public API response leaks the exact address/coordinates
   of every listing, pre-booking, to any unauthenticated caller**~~ — **Fixed,
   session 20** (see the table above). Was the highest-severity finding of this
   whole review across three sessions (prompt 29 → `roadmap/02-launch-checklist.md`
   → this fix); now closed.
2. **No account-deletion or data-export endpoint exists.** A user has no
   self-serve way to request their data or delete their account today — the
   "30 days of a verified deletion request" row above describes a policy with
   no mechanism to fulfill it yet. This is a real product gap, not just a
   documentation one.
3. **No log-retention/deletion infrastructure** — structured JSON logs
   (prompt 26) go to console output; nothing currently enforces the 90-day-hot
   target above, because there's no log aggregator deployed yet to enforce it
   against (same "no real infra deployed" gap as everywhere else in this
   readiness review).

## What this document is not

Not a lawful-basis analysis, not a DPIA (Data Protection Impact Assessment), not
legal advice, and not a substitute for actual privacy counsel review before
handling real users' real PII and real payment data at scale. It's the accurate,
current PII inventory that a real privacy review would start from — built so that
review doesn't start from zero, not built to replace it.
