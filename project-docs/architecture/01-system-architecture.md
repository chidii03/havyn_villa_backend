# System Architecture — Havyn

> Status: Draft for approval · Phase 2 · Owner: CTO/Architect.

## 1. Style: Modular Monolith (API-first)
One deployable Spring Boot application organized into clear domain modules with enforced boundaries, exposing a versioned REST API consumed by the Next.js web app today and the Expo mobile app later. Chosen over microservices to avoid premature distribution cost; boundaries are drawn so modules can later be extracted into services if scale demands.

```
        WEB (Next.js/React/TS)              MOBILE (Expo/RN) — future
                 │  HTTPS/JSON                        │
                 └───────────────┬────────────────────┘
                                 ▼
                     SPRING BOOT API (modular monolith)
   ┌──────────────────────────────────────────────────────────┐
   │ modules: auth · users · properties · search · booking ·   │
   │ pricing · payments · media · reviews · favorites ·        │
   │ messaging · notifications · admin · audit                 │
   │ cross-cutting: security, validation, error, config, obs   │
   └──────────────────────────────────────────────────────────┘
        │            │            │             │           │
   PostgreSQL      Redis      Object Storage  Payment    Maps/Email
  (source of     (cache,     (Cloudinary/    Providers   /External
   truth)        holds,       S3/R2)         (paytack/flutterwave/…)
                 locks,
                 rate-limit)
```

## 2. Module boundaries
Each module owns its entities, services, and API controllers; modules interact via internal service interfaces (and, where decoupling helps, domain events), never by reaching into each other's tables. A future extraction = promote a module's interface to a network API.

## 3. Domain events (in-process first)
Booking confirmed, Payment succeeded/failed, Review published, Message sent → published on an in-process event bus (Spring events), with option to move to Redis Pub/Sub or a broker later. Notifications, analytics, and audit subscribe.

## 4. API surface
Versioned `//api/v1/...`, REST + JSON, OpenAPI/Swagger generated. Auth via short-lived JWT access token + rotating refresh token (httpOnly cookie for web; secure storage for mobile). Consistent error envelope, pagination, filtering, and idempotency keys for booking/payment endpoints.

## 5. Data & consistency
Postgres is authoritative. Booking uses transactional writes + a uniqueness/overlap constraint + a short-lived Redis hold to prevent double-booking. Redis is a cache/coordination layer, never a source of truth.

## 6. External integrations (abstracted)
Payments, media storage, maps, and email are behind interfaces so providers are swappable via config (see ADRs). No provider SDK leaks into domain logic.

## 7. Environments
local (docker-compose) → staging → production. Config via env vars/secrets manager; Flyway migrations run on deploy; Actuator health/readiness probes.

## 8. Scalability path
Stateless API replicas behind a load balancer; Postgres read replicas + connection pooling; Redis for hot caches; CDN for media. Extract high-load modules (search, media) to services only when justified by data.

## 9. Implementation notes (prompt 25, session 11)

"Redis for hot caches" (§8) now covers two read paths, both using the same
generation-counter-or-direct-delete invalidation discipline (never a `KEYS`/`SCAN`
pattern-delete — see each service's own doc comment): `search.cache.
SearchCacheService` (existing, prompt 11) and the new `properties.cache.
PropertyCacheService` for `GET /properties/{id}` — prompt 25's "caching for popular
properties." Both are strictly best-effort per §5: `PropertyService.getActive(UUID)`
— the method booking/quote read through — stays entirely uncached by design, so
price-critical reads never see a stale value; only the new `getActiveDetail(UUID)`,
backing the public read-only detail page, goes through the cache.

**A real N+1 was found and fixed**, not just theorized: `search.repo.
PropertySearchRepository`'s Criteria query selected `Property` rows without
fetch-joining `type` (`@ManyToOne(fetch = LAZY)`), and `SearchResultItem.from()`
reads `property.getType().getCode()` for every row — every page of `GET /search`
results was one query for the page plus one more per row. Fixed with an `INNER`
fetch join on `type`; safe with the existing `setFirstResult`/`setMaxResults`
pagination specifically because it's a to-one relationship (a *collection* fetch
join is what breaks pagination, not a to-one). Index review (V1–V7 migrations) found
no other gaps — every FK this app actually queries by already has a matching index.

## 10. Implementation notes (prompt 16, session 17)

**§3's own list — "Booking confirmed, Payment succeeded/failed, Review published,
Message sent → published on an in-process event bus... Notifications... subscribe" —
is now real, not just a stated intent.** `booking.domain.event.BookingConfirmedEvent`
(published from `BookingService#confirmPayment`) and `BookingCancelledEvent`
(published from `BookingService#cancel`, both the free-cancel and refund branches) are
new; `messaging.domain.event.MessageSentEvent` (published from
`ConversationService#appendMessage`) is new. `notifications.service.NotificationService`
subscribes to all three via the exact `@TransactionalEventListener(phase =
AFTER_COMMIT, fallbackExecution = true)` pattern `PaymentService#onBookingRefundDue`
(session 7) and the two cache-invalidation listeners (session 4/11) already
established — never notify about a change that ends up rolling back. "Review
published" is deliberately not wired yet: doing so would mean editing `reviews/`
(session 16's module), outside this prompt's own file scope
(`messaging/`, `notifications/`, migrations, `architecture/*`) — a real, small
follow-up for whichever session next has `reviews/` in scope, not silently done here.

**Realtime design — documented per this prompt's own explicit deliverable #3, not
built** (constraint #1: "Choose realtime tech only when justified — MVP may
poll/refresh"; acceptance criteria never requires push delivery, only that
"notifications fire" and "participants exchange messages"). If/when a live inbox
badge or a live chat view is actually wanted:

- **Redis Pub/Sub, not a new broker** — Redis is already a hard dependency of this
  stack (session 5: booking holds, session 4/11: search/property cache,
  `security/01-security-plan.md`: rate limiting), so this is zero new
  infrastructure, just a new usage of an existing one. `NotificationService#create`
  would `PUBLISH` the new `NotificationSummary` (already a small, JSON-serializable
  DTO) to a per-user channel (`notifications:{userId}`) alongside the existing
  Postgres write — the DB row stays the source of truth; Pub/Sub is purely a
  "something changed, go re-fetch or apply this" signal, same "cache/coordination
  layer, never a source of truth" rule §5 already states for Redis.
- **Server-Sent Events over the API tier, not WebSocket** — this product's realtime
  need (as scoped today) is one-directional server→client push ("a new notification/
  message arrived"), not bidirectional low-latency exchange (no typing indicators,
  no presence, no cursors in this prompt's acceptance criteria). SSE is a plain HTTP
  response (`GET /api/v1/notifications/stream`, `text/event-stream`), so it needs no
  new protocol, load balancer config, or connection-upgrade handling the way
  WebSocket does — the client's existing bearer-JWT auth middleware works unchanged.
  A Spring `SseEmitter` per connected client, fed by a `@Component` Redis
  `MessageListener` subscribed to that user's channel, is the whole shape of it.
  WebSocket becomes the right call only if/when messaging grows real-time,
  bidirectional needs (typing indicators, read receipts pushed live rather than
  polled) — revisit then, not preemptively.
- **Why not build it now**: no real traffic exists yet to justify it (same
  "nothing has ever been measured against real load" caveat as
  `devops/03-production-readiness.md`'s SLO section), and MVP polling
  (`GET /api/v1/notifications`/`GET /api/v1/conversations/{id}/messages`, both built
  this session) already satisfies every stated acceptance criterion.
