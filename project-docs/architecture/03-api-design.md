# API Design Guidelines

> Status: Draft · Phase 2. REST + JSON, OpenAPI-documented. Base path `/api/v1`.

## Conventions
- Nouns, plural resources: `/properties`, `/bookings`, `/hosts/{id}/listings`.
- Standard verbs; POST for creation; PATCH for partial update.
- **Pagination:** cursor or page/size; envelope `{ data, page, size, total, nextCursor }`.
- **Filtering/sorting:** query params documented per endpoint (search below).
- **Errors:** consistent envelope
```
{ "error": { "code": "BOOKING_UNAVAILABLE", "message": "...", "details": [...], "traceId": "..." } }
```
- **Idempotency:** `Idempotency-Key` header required on `POST /bookings` and payment intent creation.
- **Auth:** `Authorization: Bearer <access>`; refresh via `POST /auth/refresh` (rotating). RBAC checked server-side.
- **Validation:** Bean Validation; 422 with field-level details.
- **Versioning:** URI version `v1`; breaking changes → `v2`.

## Representative endpoints (MVP)
```
POST   /auth/register            POST /auth/login       POST /auth/refresh   POST /auth/logout
GET    /me                       PATCH /me
GET    /properties               GET  /properties/{id}
GET    /search?destination&checkIn&checkOut&guests&minPrice&maxPrice&type&bedrooms&amenities&rating&page
POST   /properties/{id}/quote    -> server-computed price breakdown (no persistence)
POST   /bookings                 GET  /bookings/{id}     POST /bookings/{id}/cancel
POST   /payments/intent          POST /payments/webhook/{provider}
GET    /favorites  POST /favorites/{propertyId}  DELETE /favorites/{propertyId}
POST   /properties/{id}/reviews  GET /properties/{id}/reviews
GET    /conversations  POST /conversations/{id}/messages
GET    /host/listings  POST /host/listings  PATCH /host/listings/{id}
GET    /host/reservations  GET /host/earnings
GET    /admin/...  (moderation, disputes, kyc, settings, analytics)
```

## Pricing/quote endpoint (critical)
`POST /properties/{id}/quote` returns the authoritative breakdown; `POST /bookings` recomputes and verifies server-side and rejects mismatches. **Frontend totals are display-only.**

## Implemented endpoints (prompts 10/11, session 4)

All under `/api/v1`, all responses/errors follow this doc's envelopes above.

**Public, unauthenticated:**
```
GET /properties                 -> PageResponse<PropertySummary>, status=ACTIVE only
GET /properties/{id}            -> PropertyDetail, 404 if not ACTIVE (no info leak on draft/suspended)
GET /property-types             -> [{code, name}]
GET /amenities                  -> [{code, name, category}]
GET /search?destination&checkIn&checkOut&guests&minPrice&maxPrice&type&bedrooms
    &amenities&rating&sort&page&size
                                 -> PageResponse<SearchResultItem> (includes lat/lng per
                                    item for map markers). checkIn/checkOut must both be
                                    given together or neither; sort defaults to `newest`
                                    (`price_asc`|`price_desc`|`rating_desc`|`newest`).
```

**Host-scoped, requires `HOST` role + listing ownership** (`ForbiddenException` / 403
if the caller doesn't own the listing, distinct from `NotFoundException` / 404 if it
doesn't exist at all):
```
POST   /host/listings                        -> create (status=DRAFT)
GET    /host/listings                         -> PageResponse<PropertySummary>, own listings only
GET    /host/listings/{id}                    -> PropertyDetail
PATCH  /host/listings/{id}                    -> partial update (null fields = unchanged)
POST   /host/listings/{id}/submit             -> DRAFT -> PENDING
POST   /host/listings/{id}/publish            -> PENDING -> ACTIVE
POST   /host/listings/{id}/suspend            -> ACTIVE -> SUSPENDED
POST   /host/listings/{id}/reactivate         -> SUSPENDED -> ACTIVE
PUT    /host/listings/{id}/availability       -> upsert per-day blocks/price overrides
GET    /host/listings/{id}/availability?from&to -> read a date range
```
Invalid status transitions return 400 `INVALID_STATUS_TRANSITION`. **There is
currently no way for a real user to obtain the `HOST` role** — see
`backend/02-domain-modules.md`'s session 4 notes for the gap and how tests work around
it.

## Implemented endpoints (prompt 12, session 6)

```
POST /properties/{id}/quote     -> QuoteResponse — public, no persistence
     { checkIn, checkOut, guests }
     -> { nights, baseTotal, cleaningFee, serviceFee, discountTotal, taxTotal,
          grandTotal, currency }   (no commission line — see PricingBreakdown's Javadoc)

POST   /bookings                -> BookingDetail, requires auth, requires
                                    Idempotency-Key header for retry-safety
     { propertyId, checkIn, checkOut, guests, expectedTotal }
     -> 201 on real creation, 200 if the Idempotency-Key already resolved to an
        existing booking (same guest) — never a duplicate
GET    /bookings                -> PageResponse<BookingDetail>, own bookings only
GET    /bookings/{id}           -> BookingDetail, own only (403 otherwise)
POST   /bookings/{id}/cancel    -> CancellationResult { booking, refundPercentage, refundAmount }
```
Error codes worth knowing: `DATES_UNAVAILABLE` (409), `PRICE_CHANGED` (409 — the
client's `expectedTotal` didn't match the server's recomputation), `EXCEEDS_CAPACITY`
(400), `PROPERTY_BOOKING_IN_PROGRESS` (409 — another request is mid-creation for the
same property), `BOOKING_NOT_CANCELLABLE` (400 — already terminal).

**A `BookingDetail`'s `status` can currently only ever be `PENDING` or `CANCELLED`** —
there's no payment provider yet (prompt 13), so nothing can reach `CONFIRMED`. See
`backend/02-domain-modules.md`'s session 6 notes.

## Implemented endpoints (prompts 13/14, session 7)

```
POST /payments/intent                     -> PaymentIntentResponse, requires auth, own booking only
     { bookingId }
     -> { paymentId, provider, checkoutUrl }   (redirect the guest here)
POST /payments/webhook/{provider}         -> 200, public — provider-signed, not JWT-authenticated
```
Error codes: `BOOKING_NOT_PAYABLE` (409 — booking isn't `PENDING`), `HOLD_EXPIRED`
(409). A `BookingDetail`'s `status` can now reach `CONFIRMED` for real, once a webhook
verifies successfully — see `backend/02-domain-modules.md`'s session 7 notes for how
that connects back to session 6's `confirmPayment`.

```
POST   /host/listings/{id}/media/signature  -> MediaSignatureResponse (host-owned only)
     -> { cloudName, apiKey, timestamp, signature, folder }   (client uploads directly to Cloudinary with this)
POST   /host/listings/{id}/media            -> PropertyMediaSummary — persist metadata after a direct client upload
     { publicId, secureUrl, resourceType, format, width, height, duration, bytes, alt }
GET    /host/listings/{id}/media            -> [PropertyMediaSummary], host-owned only
PUT    /host/listings/{id}/media/order      -> [PropertyMediaSummary]
     { orderedMediaIds }
DELETE /host/listings/{id}/media/{mediaId}  -> 204 — removes both the DB row and the Cloudinary asset
GET    /properties/{id}/media               -> [PropertyMediaSummary], public, ACTIVE listings only (404 otherwise)
```
`PropertyMediaSummary` includes pre-built CDN transformation URLs (`cardUrl`,
`heroUrl`, `thumbUrl`, `posterUrl` — the last only for video) alongside the raw
`secureUrl`, so consumers don't need their own Cloudinary transformation logic. Error
codes: `UNSUPPORTED_MEDIA_FORMAT`, `MEDIA_TOO_LARGE`, `MEDIA_LIMIT_REACHED`,
`INVALID_MEDIA_SOURCE` (400), `INVALID_MEDIA_ORDER` (400 — `orderedMediaIds` doesn't
exactly match the listing's current media).
