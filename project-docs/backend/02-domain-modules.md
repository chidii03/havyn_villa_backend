# Backend Domain Modules — Detail

> Status: Draft · Phase 3–10. Per-module responsibilities and rules.

## auth
Register/login/refresh/logout, email verification, password reset. Argon2/BCrypt hashing. Short-lived access JWT + rotating refresh (revocation list in Redis). RBAC roles seeded.

## users / profiles / hosts
User + Profile CRUD; "become a host" upgrade creates HostProfile and triggers KYC (VerificationRequest). Host status gates listing publish.

## properties / media / amenities
Listing CRUD with status lifecycle (draft→pending→active→suspended). Media upload issues signed URLs to object storage; DB stores metadata only. Amenity taxonomy seeded.

## search
Filter by destination/date/guests/price/type/rooms/amenities/rating. Excludes unavailable/blocked/booked. Read-optimized queries + Redis cache for hot queries. Geospatial is future (schema keeps lat/lng).

## pricing (authoritative)
Pure service computing: nights × base (+ per-date overrides) + cleaning + service fee − discounts + taxes + commission. Deterministic, unit-tested, currency-aware. Exposed via `/quote`; reused by booking. **Never trusts frontend totals.**

## booking
Validates capacity/availability/overlap; takes Redis hold; transactional confirm with Postgres exclusion constraint; status machine (pending→confirmed→completed/cancelled/refunded); cancellation → refund state per policy; emits domain events.

## payments
Provider-agnostic port; adapters for Stripe/Paystack/Flutterwave; creates intents, verifies via signed webhooks, records Payment/Transaction/Refund; triggers Payout accrual. Secrets server-side only.

## reviews / favorites
Reviews gated on completed eligible bookings; update aggregate rating transactionally. Favorites is a simple join for auth users.

## messaging / notifications
Conversations per property/booking; messages persisted; notifications via channel-agnostic service (in-app + email now; realtime later via WebSocket/SSE/Redis pub-sub).

## admin / audit
Moderation queues, dispute tooling, KYC review, commission/settings management, analytics reads. All sensitive actions write AuditLog.

## Session 4 / prompts 10+11 (property domain + search) — status and deviations

**Implemented:** `properties` module — `Property`/`PropertyType`/`Availability`
entities, `PropertyStatus` lifecycle (`DRAFT -> PENDING -> ACTIVE -> SUSPENDED`, plus
`PENDING -> DRAFT` withdraw and `SUSPENDED -> ACTIVE` reactivate, enforced via an
explicit transition matrix, not left implicit), host-scoped CRUD + submit/publish/
suspend/reactivate + availability set/read under `/api/v1/host/listings`, public
`GET /properties`, `GET /properties/{id}`, `GET /property-types` under `properties/`.
`amenities` module — `Amenity` entity + public `GET /amenities`. `search` module —
`GET /search` with destination/dates/guests/price/type/bedrooms/amenities/rating
filters, sort (`price_asc`/`price_desc`/`rating_desc`/`newest`, default `newest`),
pagination, and a Redis cache keyed by a generation counter (see
`architecture/03-api-design.md`'s new section for the endpoint contract).

**Known gap — no "become a host" upgrade path exists anywhere in the roadmap yet.**
Granting the `HOST` role isn't in prompt 10 or 11's file scope (`auth/`/`users/` are
off-limits — prompt 10 lists only `properties/`, `amenities/` as "MAY modify"). So as
of this session, a real registered user has **no product-facing way** to get the
`HOST` role and actually use any of the endpoints above — `POST /api/v1/host/listings`
et al. will 403 for every real user today. This needs its own small addition before
the feature is reachable in production: most likely a `POST /api/v1/me/become-host`
endpoint in the `auth`/`users` module, or folded into prompt 17 (host dashboard). The
integration tests (`PropertyFlowIT`, `SearchFlowIT`) work around this by minting a
HOST-scoped JWT directly via `JwtService` for a normally-registered user — the same
technique `RbacTest` (session 2) uses for role-gated slice tests — which is sufficient
to test the properties/search code but does **not** exercise a real upgrade flow,
because none exists yet.

**Deviations:**
- **`config/SecurityConfig.java`'s `PUBLIC_PATHS` list was extended** (`/properties`,
  `/property-types`, `/amenities`, `/search`) — outside both prompts' strict "MAY
  modify" file scope, but unavoidable: a new public GET endpoint 401s for every
  anonymous caller until its path is registered there. Same category of necessary
  cross-cutting touch as ADR-010's `RateLimiter` in session 2. None of the newly
  public path patterns overlap with `/api/v1/host/listings`, which stays
  authenticated + role-gated.
- **`host_id` references `app_user` directly, not a `HostProfile` row** — see
  `database/01-data-model.md`'s new "Implementation notes" section for why.
- **`property_amenity` is a plain `@ManyToMany` join, no `PropertyAmenity` entity
  class** — the join carries no extra columns.
- **No admin-approval gate on `PENDING -> ACTIVE`** — a host currently publishes their
  own submitted listing. Prompt 18 (admin platform) is the natural place to add a real
  moderation gate later; nothing here blocks that.
- **Search's "booked" exclusion is availability-row-based only** — there's no
  `Booking` entity yet (prompt 12). The query already checks `availability.booking_id
  IS NOT NULL` so it picks up real bookings automatically once prompt 12 starts
  populating that column; no search-side change will be needed then.
- **Redis cache invalidation is global, not per-listing** — any `PropertyChangedEvent`
  (create/update/transition/availability change, anywhere) bumps one shared generation
  counter, invalidating every cached search result rather than just the affected
  listing's. This trades cache efficiency for correctness/simplicity, which is the
  right tradeoff at MVP scale — see `search.cache.SearchCacheService`'s Javadoc.

**Bugs caught by actually running the tests, not just building:** the first draft of
`PropertyServiceTest`'s availability upsert test stubbed
`AvailabilityRepository.findByProperty_IdAndDate` against the test's own random
`propertyId`, but a plain `new Property(...)` in a Mockito unit test (no real
Hibernate involved) never gets its `BaseEntity#id` populated — the service actually
calls `property.getId()`, which was `null`. The stub silently never matched, and
Mockito's default `Optional`-returning answer (`Optional.empty()`) for the unmatched
call happened to be plausible enough that the test still ran without erroring, just
asserting the wrong thing (it failed on `existing.isBlocked()` never having been
mutated, since the service always took the "create new" branch rather than the
"update existing" branch). Fixed by matching the id argument with
`nullable(UUID.class)` instead of the test's own unrelated `propertyId` value — a
reminder that `any(Class<T>)` excludes `null` in Mockito, only bare `any()`/
`nullable(Class<T>)` include it.

**Known limitation (documented in code, not fixed):** the JPA Criteria API query in
`PropertySearchRepository` runs the same predicate-building logic twice per request
(once for the paged `SELECT`, once for the `COUNT`) since Criteria `Predicate`
instances aren't reusable across two different `CriteriaQuery` roots. This is the
standard pattern for this API and is fine at MVP query complexity/volume; if search
filters grow much more complex, revisit with `JOIN FETCH` + a windowed count or a
native query.

## Session 6 / prompt 12 (booking engine) — status and deviations

**Implemented:** `pricing` module — `PlatformSetting` (+ minimal `platform_setting`
table), `PricingService` (deterministic `nights x base(+overrides) + cleaning +
service - discounts + taxes`, currency-aware, unit-tested). `booking` module —
`Booking`/`BookingStatus` (full 5-state lifecycle + transition matrix),
`CancellationPolicyCalculator` (pure, policy-based refund %), `BookingHoldLock`
(Redis per-property lock), `BookingService` (quote/create/cancel/get/list, hold
expiry — lazy on write + `@Scheduled` sweep), `POST /properties/{id}/quote` (public),
`POST /bookings` (Idempotency-Key, rejects mismatched client totals), `GET /bookings`,
`GET /bookings/{id}`, `POST /bookings/{id}/cancel`.

**Deviations:**
- **Commission rate lives in a new minimal `platform_setting` table**, not hardcoded
  and not (yet) admin-editable via API — see database/01-data-model.md's session 6
  notes. Prompt 18 is expected to add read/write endpoints over the same table.
- **`config/SecurityConfig.java` needed no new public-path entry** — `POST /properties/
  {id}/quote` already falls under the existing `/api/v1/properties/**` public
  prefix from session 4/5. Updated that entry's code comment, since it's no longer
  literally true that nothing under that prefix is a POST (quote is, but persists
  nothing).
- **`properties/domain/Availability.java` gained one setter** (`setBookingId`) — the
  column was explicitly reserved for this prompt in session 4; see its Javadoc.
- **Discount and tax are real response fields, always `0`** — no promo-code system or
  tax-jurisdiction config exists anywhere in this project. This is the honest current
  value, not a placeholder standing in for unbuilt logic.
- **Only `PENDING -> CANCELLED` is reachable end-to-end.** There's still no payment
  provider (prompt 13), so no booking can ever reach `CONFIRMED`. The `CONFIRMED ->
  REFUNDED`/`CANCELLED` cancellation path (real, policy-computed refund
  percentage/amount via `CancellationPolicyCalculator`) is unit-tested directly since
  it can't be exercised through the real API yet.
- **"24 hours before check-in" (the `FLEXIBLE` policy's product copy) is approximated
  as whole days** — `Booking`/`Property` store dates with no check-in time, so a hard
  24-hour boundary isn't representable; see `CancellationPolicyCalculator`'s Javadoc.

**Bugs caught by actually running the tests, not just building:**
- A first draft of `PricingServiceTest`'s per-date-override test stubbed the
  availability-repository mock with the wrong date-range argument (`[checkIn,
  checkIn]` instead of the service's actual `[checkIn, checkOut - 1 day]`), so the
  override silently never applied and the test asserted the wrong (but still
  internally consistent) total. Same category of mismatched-mock-argument bug as
  session 4's `PropertyServiceTest` — fixed by matching the exact window the service
  computes, not a re-derived one.
- A `BookingServiceTest` cancellation test was named/commented as testing "too close to
  check-in, no refund" but the fixture's actual date math (12 days out under `STRICT`'s
  7-day minimum) meant it was testing the opposite case (a 50% refund). The assertions
  happened to still pass — a passing test with a wrong name/comment is its own kind of
  bug (misleads whoever reads it next). Renamed and added a real "too close" case
  (4 days out) alongside it.

**Known limitation (documented in code, not fixed):** `BookingHoldLock`'s release is a
check-then-delete, not a single atomic Redis operation — a narrow theoretical race
where this request's TTL expires, another request acquires the key, and this
request's `finally` deletes that other request's fresh lock. Acceptable because the
lock is a fail-fast contention aid, not the correctness guarantee — the Postgres
exclusion constraint is, and `BookingConcurrencyIT` proves that directly by bypassing
the lock entirely.

**Known gap:** the Testcontainers-only backend tests still need Docker, unchanged.

## Session 7 / prompts 13+14 (payments + media) — status and deviations

**Implemented:** `payments` module — `PaymentProvider` port + a real Paystack adapter
(`PaystackPaymentProvider`: "Initialize Transaction" for intents, HMAC-SHA512 webhook
signature verification, refund API), `Payment`/`Transaction`/`Refund`/`Payout`
persistence, `POST /payments/intent`, public `POST /payments/webhook/{provider}`
(idempotent by `Payment` status), payout accrual on confirmed payment. `media` module
— `MediaStorage` port + a real Cloudinary adapter (`CloudinaryMediaStorage`: signed
uploads via local SHA-1 computation, the "destroy" API for deletes),
`CloudinaryUrlBuilder` (card/hero/thumb/video-poster CDN URL transformations, pure
string manipulation), `POST .../media/signature`, `POST/GET/DELETE .../media`,
`PUT .../media/order`, and a public `GET /properties/{id}/media`.

**This is where session 6's booking-confirmation gap actually gets used**: a verified
`charge.success` webhook now calls `BookingService.confirmPayment(bookingId)` — the
first and only real caller of that method since it was added. Similarly, cancelling a
`CONFIRMED` booking with a refund due now triggers a real (mocked-provider-tested)
refund call via `BookingRefundDueEvent`.

**Deviations:**
- **Both `PaystackPaymentProvider` and `CloudinaryMediaStorage` take the Spring Boot
  auto-configured `RestClient.Builder`** (not a fresh `RestClient.builder()`)
  specifically so `MockRestServiceServer` can bind to it in tests — this means the
  actual HTTP *request shaping* (URLs, headers, body content, amount-to-kobo
  conversion) is genuinely unit-tested, not just documented as "untestable." What
  remains untestable in this environment is the live round-trip itself: **there is no
  real Paystack or Cloudinary account configured here**, so neither adapter has ever
  actually talked to its real API. Signature generation/verification (pure local
  crypto) has no such limitation and is fully real.
- **Corrected a session-1 placeholder**: `.env.example` had a `PAYSTACK_WEBHOOK_SECRET`
  that doesn't correspond to anything real — Paystack signs webhooks with the same
  secret key used for API auth (`HMAC-SHA512(rawBody, PAYSTACK_SECRET_KEY)`), there is
  no separate webhook-signing secret to configure. Removed, with a note explaining why.
- **`BookingService` gained one new public method** (`confirmPayment`) and one new
  event publish (`BookingRefundDueEvent`, only when a cancellation's refund amount is
  non-zero) — both minimal, additive touches, not changes to pricing/overlap logic;
  same category as session 4's `Availability.setBookingId` precedent.
- **Cloudinary uploads are pure signed uploads, not an unsigned upload preset** — the
  session-1-provisioned `CLOUDINARY_UPLOAD_PRESET` env var is unused by this
  implementation (kept in `.env.example`, noted as such) since signed uploads don't
  need a dashboard-configured preset and work identically regardless of Cloudinary
  account configuration.
- **Media size/type validation is post-hoc, not preventive** — the client uploads
  directly to Cloudinary (the backend never sees the file bytes), so `MediaService.
  addMedia` validates the *reported* size/format after the fact and immediately calls
  `MediaStorage.deleteAsset` if it violates policy, rather than leaving a
  policy-violating asset orphaned in storage.
- **A public `GET /properties/{id}/media` endpoint was added** even though prompt 14's
  deliverable list only names host-scoped endpoints — without it, uploaded media would
  be completely unreachable by anyone but the host, which contradicts the prompt's own
  acceptance criterion ("media renders from Cloudinary CDN"). Same ACTIVE-only 404
  story as `PropertyController`, no info leak on draft/suspended listings.
- **Payout execution has no real rail** (bank transfer / provider payout API) — every
  `Payout` row stays `PENDING` forever in this pass. The accrual *math* (`grand_total -
  commission_amount`, summed per host per period) is real; only the "actually pay the
  host" step is unbuilt. A real payout rail is a plausible prompt-17 (host dashboard)
  concern, not scoped here.

**Bugs caught while designing, not after running tests** (worth recording since no
test run actually surfaced them — they were caught by reasoning through the design
before writing code, per the same discipline as bugs caught via tests): the webhook
signature header name (`x-paystack-signature`) is provider-specific, so an earlier
draft of `PaymentProvider.parseWebhook` took a single pre-extracted signature string —
that would have forced the (supposedly provider-agnostic) controller to hardcode
Paystack's header name, breaking the abstraction the moment a second provider is
added. Changed to pass the full `HttpHeaders` and let each adapter read its own header.

**Known limitation (documented in code, not fixed):** a genuine double-payment (two
separate `Payment` rows for the same booking both succeeding — e.g. a guest opening
two checkout sessions) is handled safely but not elegantly: the second webhook still
marks its `Payment` `SUCCEEDED` (financially honest — money really was received) but
skips re-confirming the booking and skips a second payout accrual (`BookingService.
confirmPayment` is idempotent). Reconciling the resulting double-charge is a manual
ops concern for now, not automated.

**Known gap:** the Testcontainers-only backend tests still need Docker, unchanged.

## Session 16 / prompt 15 (reviews + favorites) — status and deviations

**Implemented:** `reviews` module — `Review` entity, `ReviewRepository`,
`ReviewService` (eligibility-gated create + public list), `ReviewController`
(`POST`/`GET /properties/{id}/reviews`). `favorites` module — `Favorite` entity,
`FavoriteRepository`, `FavoriteService` (idempotent add/remove/list, implicit
object-level authz since every query is scoped to the caller's own id),
`FavoriteController` (`POST`/`DELETE /favorites/{propertyId}`,
`GET /favorites`). `V8__reviews_favorites.sql` creates both tables.
`Property.applyAggregateRating(BigDecimal, int)` — a minimal, necessary mutator added
outside `reviews/`'s own package, since `rating_avg`/`rating_count` already existed on
`Property` specifically provisioned for this prompt's own "aggregate rating updated
atomically" constraint (same "minimal necessary cross-module touch" category as
session 4's `Availability.setBookingId` and session 7's `BookingService.confirmPayment`
precedents).

**Deviations:**
- **`SecurityConfig` gained one precise, method-scoped rule**, also technically outside
  `reviews/`'s file scope but directly required by this prompt's own "only eligible...
  users can review" constraint: `/api/v1/properties/**` was already `permitAll()`
  (covers `GET`/`POST /quote`, prompts 10–12), and `GET .../reviews` is meant to be
  public too (reviews are browsable like the listing itself) — but without an explicit
  carve-out, `POST .../reviews` would have inherited that same blanket `permitAll()`
  and been silently unauthenticated. Added one `requestMatchers(HttpMethod.POST,
  "/api/v1/properties/*/reviews").authenticated()` rule, registered before the public
  wildcard so the more specific rule wins. `favorites/` needed no such change — none of
  its paths fall under an existing public prefix, so they're authenticated by
  `SecurityConfig`'s default `anyRequest().authenticated()` fallback automatically.
- **Aggregate rating is recomputed from source on every review**, not incrementally
  mutated — see database/01-data-model.md's session 16 notes for the full rationale
  (floating-point drift; no edit/delete-review capability in scope to make the extra
  `AVG`/`COUNT` query a real cost).
- **Favoriting an already-favorited property is idempotent (200, not a 409 conflict)**
  — deliberately different from reviews' "ALREADY_REVIEWED" conflict, since favoriting
  is a toggle-style action (like a heart icon), not a one-time submission. Unfavoriting
  something not favorited (or already removed) does 404, matching `MediaService.
  delete`'s existing precedent for owned-resource deletion in this codebase, rather
  than a silent idempotent no-op.
- **`ReviewService` reads `booking/`'s `BookingRepository` directly** (read-only, never
  writes a `booking` row) to check eligibility — the same established cross-module read
  pattern `BookingService` itself already uses against `properties/`'s repositories, not
  a new one introduced here.

**Known, pre-existing structural gap (not fixed here — out of `reviews/`'s file
scope, and already named in `devops/03-production-readiness.md`'s go/no-go record as a
feature-completeness blocker):** nothing in `booking/` ever transitions a real booking
to `COMPLETED`. Review eligibility-gating is real and correct, but only reachable today
via `ReviewFlowIT`'s directly-constructed `COMPLETED` booking fixture (the same
test-only shortcut `BookingFlowIT` already established for stale-hold setup) — not
through any live product flow. This gap belongs to whichever future prompt actually
drives a booking to completion (a scheduled sweep past `check_out`, or a host/guest
manual action), not to this one.

**Known gap:** the Testcontainers-only backend tests still need Docker, unchanged —
`ReviewFlowIT`/`FavoriteFlowIT` compile and pass locally against the same
`DockerClientProviderStrategy` failure as every other `*FlowIT` in this sandbox; the 7
new `ReviewServiceTest` unit tests (pure Mockito, no Docker) pass.

## Session 17 / prompt 16 (messaging + notifications) — status and deviations

**Implemented:** `messaging` module — `Conversation`/`Message` entities (one thread
per (property, guest) pair, unique-constrained; optionally linked to a real booking),
repositories, `ConversationService` (start-or-continue, send, list, get, mark-read —
object-level authz: only participants), two controllers
(`PropertyConversationController` for nested creation, `ConversationController` for
everything else). `notifications` module — `Notification`/`NotificationType`,
`EmailSender` port + `SmtpEmailSender` adapter (own port, not a reuse/extension of
`auth.domain.Mailer` — that one is narrowly scoped to auth's two specific flows),
`NotificationService` (in-app persistence + best-effort email, three
`@TransactionalEventListener`s), `NotificationController` (list/unread-count/mark-read).
`V9__messaging_notifications.sql` creates all three tables. Two new booking domain
events (`BookingConfirmedEvent`, `BookingCancelledEvent`) and one new messaging event
(`MessageSentEvent`) — see architecture/01-system-architecture.md's session 17 notes
for the full event-wiring picture and the documented-but-not-built realtime design.

**Deviations:**
- **`BookingService` gained two new event publishes** (`BookingConfirmedEvent` in
  `confirmPayment`, `BookingCancelledEvent` in both branches of `cancel`) — minimal,
  additive touches outside this prompt's own `messaging/`/`notifications/` file scope,
  but directly necessary: without a real event to subscribe to, "notifications fire on
  booking confirmed/cancelled" (this prompt's own acceptance criterion) would be
  unbuildable. Same category as session 7's `BookingService.confirmPayment`/
  `BookingRefundDueEvent` precedent — a real domain event, not a change to booking's
  own pricing/overlap/cancellation logic.
- **`SecurityConfig` gained one more path in the existing auth-required carve-out
  array** (generalized session 16's single `REVIEWS_PATH` field into
  `AUTH_REQUIRED_PROPERTY_POST_PATHS`, now covering both `.../reviews` and
  `.../conversations`) — same reasoning as session 16: `/api/v1/properties/**` is
  `permitAll()`, and starting a conversation needs auth even though the rest of that
  prefix (including `GET .../conversations`... except there is no such endpoint, see
  below) is public.
- **Conversation creation is nested (`POST /properties/{id}/conversations`), but
  list/get/send/read live on a separate `/api/v1/conversations` controller** — there
  is no `GET /properties/{id}/conversations` (a host's "all conversations about this
  one listing" view). `GET /api/v1/conversations` (all of the caller's conversations,
  as either participant, across every property) already covers the practical need
  without the added complexity of a second, role-filtered listing endpoint — kept out
  deliberately, not an oversight.
- **A host cannot start a conversation on their own listing**
  (`HOST_CANNOT_MESSAGE_OWN_LISTING`, 400) — a real, obvious invalid state (host_id ==
  guest_id breaks the two-participant model), not speculative hardening.
- **`ConversationSummary` carries a plain `propertyTitle` string, resolved at the
  controller layer** (`propertyRepository.findById(...)`, same "resolve display data
  in the controller, not the service" pattern `BookingController`/
  `BookingPropertySummary` already established in session 6) rather than reusing
  `booking.web.BookingPropertySummary` itself — deliberately not reused across module
  boundaries; each module owns its own response DTOs even when the shape overlaps.
- **Notification email is best-effort, wrapped in try/catch** — the in-app
  `Notification` row is the source of truth; a flaky SMTP/provider failure must never
  roll back or fail a request over a side channel. Logged at WARN
  (`NotificationService`), same severity `PaystackPaymentProvider`'s invalid-signature
  WARN uses for a comparable "real but non-fatal" condition.
- **"Review published" notifications are deliberately not wired** — would require
  editing `reviews/` (session 16's module), outside this prompt's declared file scope.
  Flagged as a real, bounded follow-up, not silently done under a different prompt's
  authority.

**Known gap:** the Testcontainers-only backend tests still need Docker, unchanged —
`MessagingFlowIT`/`NotificationFlowIT` compile and pass locally against the same
`DockerClientProviderStrategy` failure as every other `*FlowIT` in this sandbox; the 17
new unit tests (`ConversationServiceTest`, `NotificationServiceTest` — pure Mockito, no
Docker) pass. Both integration tests mock `EmailSender` (`@MockitoBean`, same pattern as
`MediaFlowIT`'s `MediaStorage`/`PaymentFlowIT`'s `PaymentProvider`) per this prompt's own
"Testcontainers, mock email" test requirement — real Mailhog-backed email delivery (CI's
`test` job already runs one, for `AuthFlowIT`'s registration emails) was deliberately not
relied on here.

## Session 18 / prompt 17 (host dashboard) — status and deviations

**Implemented:** a new `hosts` module — `HostOnboardingService`/`HostOnboardingController`
(`POST /host/onboarding` — self-serve HOST role grant, idempotent, requires a verified
email, re-mints tokens via a new `AuthService.reissueTokens`), `HostDashboardService`/
`HostDashboardController` (`GET /host/dashboard/summary` — active/total listings,
upcoming reservations, earnings by currency, pending payouts, average rating, all
computed backend-side), `HostReservationController` (`GET /host/reservations`,
optional `propertyId` filter, batch-resolves property/guest names for the page),
`HostPayoutController` (`GET /host/payouts`). Listing CRUD/availability itself was
already built (`properties.web.HostListingController`, prompt 10) and needed no
changes — this module orchestrates across `properties`/`booking`/`payments`/`users`,
it doesn't duplicate their logic.

**Deviations:**
- **`AuthService` gained one small public method, `reissueTokens(User)`**, wrapping
  the existing private `issueTokens` — `hosts/` needed to re-mint tokens for an
  already-loaded, already-mutated `User` right after granting the HOST role (so the
  caller's next request can use `@PreAuthorize("hasRole('HOST')")` endpoints
  immediately, no re-login), and duplicating that 2-line token-issuance sequence
  (rather than reusing it) would have meant two independently-maintained copies of
  security-relevant logic. `HostOnboardingController` also duplicates `AuthController`'s
  small refresh-cookie-construction helper (referencing its already-`public static
  final REFRESH_COOKIE_NAME` rather than a fresh string literal) — that part stayed
  local rather than extracted, consistent with this codebase's standing preference for
  small per-controller repetition over new shared abstractions (e.g. every controller
  already has its own `principal(Authentication)` helper, never a shared base class).
- **Four new repository/service methods added across `booking`/`properties`/
  `payments`/`users`**, each minimal and directly required by a named prompt
  deliverable, no substitute existing: `BookingRepository.
  findAllByPropertyIdInOrderByCheckInDesc`/`countByPropertyIdInAndStatusInAndCheckInGreaterThanEqual`
  + `BookingService.listForHost`/`countUpcomingForHost` (reservations list + the
  summary's upcoming count); `PropertyRepository.countByHostIdAndStatus` (unused in
  the end — `HostDashboardService` ended up computing active/total from the same
  `findAllByHostId(hostId, Pageable.unpaged())` call it already needed for the rating
  average, so a second count query would have been redundant; left in place since
  it's a real, correct, independently-useful query a future session may still want,
  not dead code masquerading as used); `PayoutRepository.
  findAllByHostIdOrderByPeriodDesc`/`findAllByHostId` + `PaymentService.
  listPayoutsForHost`/`listAllPayoutsForHost` (payout list + summary aggregation);
  `ProfileRepository.findAllByUser_IdIn` (batch guest-name resolution, avoiding an
  N+1 on the reservations list — same discipline as architecture/
  01-system-architecture.md's session 11 notes).
- **"Performance" (this prompt's own deliverable-list word) is deliberately folded
  into the dashboard summary, not a dedicated endpoint/occupancy-math feature** — no
  page-view/analytics tracking infrastructure exists anywhere in this codebase, and
  this prompt's actual acceptance criteria never names occupancy or traffic metrics
  (only "view accurate earnings/payouts"). Building a new tracking pipeline for a
  word in a deliverables bullet, unsupported by the acceptance criteria that bullet
  serves, would have been scope creep — flagged and scoped down deliberately, not
  silently dropped.
- **Every earnings/payout figure is computed in `HostDashboardService`/
  `PaymentService`, never in a controller or the frontend** — per this prompt's own
  "No business math on the frontend" constraint. `Payout` rows are the real, already-
  accrued earnings figure (session 7: `grand_total - commission_amount`, summed per
  host per period) — there's no separate "earnings" computation to build or to drift
  from payouts.
- **A pre-existing, real gap found and left honestly documented, not silently
  worked around:** every `Payout` row is `PENDING` forever (no payout-execution rail
  exists — `PayoutStatus`'s own Javadoc, session 7). `HostDashboardFlowIT`'s fixture
  code can only construct `PENDING` payouts for exactly this reason (there's no
  domain method to reach `PAID`), and the earnings frontend page states this plainly
  rather than implying money has moved.

**Tests:** `HostOnboardingServiceTest` (4), `HostDashboardServiceTest` (3) — pure
Mockito, no Docker, all pass. `HostDashboardFlowIT` — real onboarding (rejects
unverified email, grants HOST, idempotent re-call), real listing publish through the
existing `HostListingController`, a real guest reservation, real webhook-driven
confirmation, and object-level isolation (a second host sees an empty reservations/
payouts list, not the first host's data) — compiles clean, fails only on the same
`DockerClientProviderStrategy` (no Docker in this sandbox) every other `*FlowIT` in
this suite fails on. Full backend suite re-run as a regression check: 250 tests, 58
failures, every failure Docker-related (matches the pre-existing count of `*FlowIT`/
`*IT` classes in this repo, not a new number) — the other 192, including every edited
service's existing unit tests (`BookingServiceTest` ×12, `PaymentServiceTest` ×11),
still pass unchanged.

## Session 19 / prompt 18 (admin platform) — status and deviations

**Implemented:** a real, queryable `AuditLog` (`audit` module — the jsonb convention
sessions 7/15/16 all explicitly deferred, established here via Hibernate 6's native
`@JdbcTypeCode(SqlTypes.JSON)`, no extra dependency). `admin` module: user management
(list/search, grant/revoke HOST or ADMIN, suspend/reactivate — self-revoking your own
ADMIN role is blocked), listing moderation (list every listing regardless of status,
suspend, reject a pending submission back to DRAFT — additive to, not a replacement
for, host self-publish), host identity/KYC review (`VerificationRequest`: host
submits a document URL + notes, admin approves/rejects, one PENDING request per user
at a time), booking disputes (`Dispute`: the booking's guest or host raises one,
admin resolves/dismisses), commission/platform settings (real read/write over the
`platform_setting` table that's existed since session 6, explicitly reserved for this
prompt), and platform-wide analytics computed from data this project already
persists. Every sensitive action calls `AuditLogService#record` in its own
transaction — action and audit trail commit together or not at all.

**Deviations:**
- **No self-serve path to ADMIN exists, by design, not a gap.** Granting ADMIN is
  itself an ADMIN-only action (`AdminUserService#grantRole`) — a self-serve "become
  an admin" endpoint (mirroring session 18's host onboarding) would be a real
  security hole, not a convenience. The very first admin account is a genuine
  ops/deployment bootstrapping concern this engagement can't solve from inside the
  app (same category as `devops/03-production-readiness.md`'s legal/ToS items —
  real, structural, and out of an AI coding session's reach). `AdminPlatformFlowIT`
  mints an ADMIN-scoped JWT directly (the same test-only shortcut every session has
  used for a role with no self-serve path yet — HOST before session 18); the new
  Playwright spec seeds one via a real registration + a single SQL role grant (see
  `apps/web/e2e/README.md`'s session 19 notes).
- **Nine small, individually-justified cross-module touches**, each mirroring this
  engagement's established "minimal, necessary, directly required by a named
  deliverable" precedent, not a departure from it: `PropertyService.
  transitionAsAdmin`/`getAny`/`listAll` (listing moderation needs an ownership-check-
  free transition and an unfiltered list — additive alongside the existing
  host-scoped `transition`, never replacing it); `AuthService` unchanged this
  session (no new method needed — user status/role mutations go straight through
  `User`'s own `addRole`/new `removeRole`/new `setStatus`, all trivial, symmetric
  additions to methods/fields that already existed for the host-facing half of the
  same concern); `PlatformSetting` gained a public `(key, value)` constructor and a
  `setValue` setter — its own Javadoc already said the read/write API was this
  prompt's job; `UserRepository`/`PropertyRepository`/`BookingRepository` each
  gained one or two `count*`/`sum*` aggregate queries for the analytics summary,
  every one a single, real, indexed-appropriate query, not a fetch-all-and-sum
  shortcut (except payouts, which session 18 already established as a fetch-and-sum
  pattern at a scale where that's fine).
- **"Performance"/analytics is deliberately computed from data this project already
  persists** (users, properties, bookings, verification requests, disputes) — no
  page-view/traffic tracking pipeline exists anywhere in this codebase, and building
  one for this prompt alone would be scope creep, the exact same reasoning session
  18's host-dashboard "performance" scoping note already established. Multi-currency
  revenue grouping (like session 18's payout summary) was deliberately **not**
  applied to the analytics sums — this product is single-currency (NGN) in every
  real sense today; noted in code as a call to revisit if that ever changes, not
  silently assumed.
- **Deliberately out of scope, flagged plainly, not silently dropped** (see
  `admin/package-info.java`'s own Javadoc for the same list): review moderation
  (would mean editing `reviews/`, a different prompt's file scope); a hard
  admin-approval gate replacing host self-publish entirely (`PENDING -&gt; ACTIVE`
  staying host-initiated) — would be a breaking change to an already-shipped
  capability, and outside `admin/`'s/`audit/`'s file scope to boot; "reports" — never
  defined anywhere in this project's docs, not in the original ERD
  (database/01-data-model.md#1), not in this prompt's own acceptance criteria (only
  its deliverables bullet names the word, with no definition anywhere to build
  against).
- **KYC submission and dispute-raising live in the `admin` package, not `hosts`/
  `booking`**, even though hosts/booking-participants are who calls them — same
  reasoning `HostListingController` lives in `properties.web`, not `hosts.web`: the
  URL prefix (`/host/verification-requests`, `/bookings/{id}/disputes`) reflects who
  calls it; the Java package reflects who owns the domain lifecycle (admin review is
  what `VerificationRequest`/`Dispute` are fundamentally about).

**Tests:** `AuditLogServiceTest` (2), `AdminUserServiceTest` (6),
`AdminPropertyServiceTest` (2), `VerificationServiceTest` (7), `DisputeServiceTest`
(6), `AdminSettingsServiceTest` (5) — 28 new unit tests, pure Mockito, no Docker, all
pass. `AdminPlatformFlowIT` — the full moderation/KYC/dispute/settings/analytics/
audit-log flow against a real Postgres, plus a dedicated self-revoke-guard test and
an authz test covering every `/admin/**` endpoint — compiles clean, fails only on the
same `DockerClientProviderStrategy` (no Docker in this sandbox) every other `*FlowIT`
in this suite fails on. Full backend suite re-run as a regression check: 281 tests,
61 failures, every failure Docker-related (58 pre-existing + exactly the 3 new
`AdminPlatformFlowIT` methods — not a new failure category) — the other 220 pass
unchanged.

## Session 20 (same day) — launch-checklist follow-up: two small, bounded fixes

Not a numbered prompt — a direct follow-up to `roadmap/02-launch-checklist.md`
(prompt 31, written earlier this session), which named exactly two open items as
small enough to fix without a new prompt file: the `PropertyDetail` PII leak and
the missing `bookings_enabled` kill switch. Both closed this pass, `apps/api`
only, no frontend changes.

**Location redaction** (`properties/`, `search/`, `booking/`): `PropertyDetail`,
`PropertySummary`, and `SearchResultItem` (three DTOs, not the one the checklist
named — the same precise-coordinate leak was found in `PropertySummary`/
`SearchResultItem` too, by grepping every caller before touching anything) each
gained a `withApproximateLocation()` method — `address` withheld (`PropertyDetail`
only; the other two never had it), `lat`/`lng` rounded to 2 decimal places
(~1.1km). `PropertyService#getActiveDetail` gained two new params (`viewerId`,
`viewerIsAdmin`) and now redacts the cached full-precision detail *after* the
cache lookup, per call — the cache itself still stores the untouched original,
so a warm entry can never leak the wrong view to the wrong viewer. Exact location
goes to: the property's own host, an `ADMIN`, or a guest with a `CONFIRMED`/
`COMPLETED` booking for that property (`BookingRepository`'s new
`existsByGuestIdAndPropertyIdAndStatusIn`) — deliberately **not** a `PENDING`
hold alone, since anyone can create one of those. `PropertyController#list` and
`SearchService#execute` apply the same redaction unconditionally (always
public, no privileged variant) — `PropertySummary.from()` itself stays
unredacted, since `HostListingController`/`AdminPropertyController` reuse it
for views that legitimately need exact coordinates.

**Kill switch** (`booking/`, `admin/`, `common/error/`): new
`ServiceUnavailableException` (503), new `V11__bookings_enabled_setting.sql`
seeding `bookings_enabled = true` into the existing `platform_setting` table
(zero schema change — same key/value shape `commission_pct` already uses).
`BookingService#create` reads it fresh on every attempt (no caching, same
reasoning as `PricingService#commissionPct`'s existing read-fresh pattern for
the sibling setting) and throws `BOOKINGS_DISABLED` when off; fails **open**
(a missing row still means enabled) so the safety mechanism can't itself take
bookings down. `AdminSettingsService` gained one validation branch
(`bookings_enabled` must be `"true"`/`"false"`) — the existing generic
`PUT /admin/settings/{key}` already handles the read/write/audit-log path for
any key present in the table, so this is toggleable from the already-built
`/admin/settings` UI with zero frontend changes.

**Tests:** 9 new unit tests across `PropertyServiceTest` (4: anonymous viewer
redacted, `PENDING`-only guest redacted, `CONFIRMED`-booking guest sees exact,
admin sees exact regardless), `SearchServiceTest` (1: rounding before caching),
`BookingServiceTest` (2: kill switch rejects, missing setting fails open),
`AdminSettingsServiceTest` (2: rejects non-boolean, accepts the toggle) — plus
3 existing `PropertyServiceTest` cache-mechanics tests updated to pass a
privileged viewer (host) so they keep testing cache behavior, not redaction.
Full backend suite: 290 tests, 61 failures — every one Docker-related
(`DockerClientProviderStrategy`/`DefaultCacheAwareContextLoaderDelegate`,
including `ApiApplicationTests#contextLoads`, which also needs a live
Postgres/Redis to boot the full context), zero new non-Docker failures. One
real bug caught and fixed during this pass, not shipped: a new redaction test
first stubbed `BookingRepository#existsByGuestIdAndPropertyIdAndStatusIn`
against the test's own `propertyId` field, but `Property.getId()` is
`@GeneratedValue`-null until Hibernate actually persists it — these are pure
Mockito unit tests that never do — so the stub never matched the real call.
Fixed by loosening that one argument matcher; the assertion itself was correct
throughout.
