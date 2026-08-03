# Data Model — Havyn

> Status: Draft for approval · Phase 2 · Owner: DB Architect. Postgres. Tables NOT final — this is the design baseline.

## 1. Entity overview
User, Role, UserRole, Profile, HostProfile, Property, PropertyType, PropertyMedia, Amenity, PropertyAmenity, Availability, Booking, BookingGuest, PricingRule, Payment, Refund, Transaction, Payout, Favorite, Review, Conversation, Message, Notification, VerificationRequest (KYC), AuditLog, PlatformSetting.

## 2. Text ERD (key relationships)
```
User 1─* UserRole *─1 Role
User 1─1 Profile
User 1─1 HostProfile (if host)
HostProfile 1─* Property
Property *─1 PropertyType
Property 1─* PropertyMedia
Property *─* Amenity  (via PropertyAmenity)
Property 1─* Availability        (per-date: available/blocked/price override)
Property 1─* Booking
Booking  *─1 User (guest)
Booking  1─* BookingGuest
Booking  1─1 Payment
Payment  1─* Transaction
Payment  1─* Refund
Booking  1─0..1 Review
User     *─* Property (via Favorite)
Conversation 1─* Message ; Conversation *─1 Property/Booking
User 1─* Notification
HostProfile 1─* Payout
User 1─* VerificationRequest
* AuditLog (polymorphic actor/target)
```

## 3. Selected columns (illustrative, not exhaustive)
**Property:** id(uuid), host_id, title, description, type_id, address, city, state, country, lat, lng, currency, base_price(numeric), capacity, bedrooms, beds, bathrooms(numeric), cleaning_fee, service_fee_pct, house_rules, cancellation_policy, status(enum: draft/pending/active/suspended), rating_avg, rating_count, created_at, updated_at.

**Booking:** id, property_id, guest_id, check_in(date), check_out(date), nights, guests_count, base_total, cleaning_fee, service_fee, discount_total, tax_total, commission_amount, grand_total, currency, status(enum: pending/confirmed/cancelled/completed/refunded), hold_expires_at, created_at.

**Payment:** id, booking_id, provider, provider_ref, amount, currency, status(enum: requires_action/pending/succeeded/failed), created_at. **Refund:** id, payment_id, amount, reason, status. **Payout:** id, host_id, amount, period, status.

**Availability:** id, property_id, date, is_blocked, price_override, booking_id(nullable).

## 4. Integrity & concurrency
- Money as `NUMERIC(12,2)`, never float. Store currency per row.
- **Double-booking:** overlap prevention on active bookings for a property — Postgres `EXCLUDE USING gist (property_id WITH =, daterange(check_in, check_out) WITH &&)` where status in active set (plus Redis checkout hold).
- Soft-delete via `deleted_at` where user-facing recoverability matters; hard rules in migrations.
- Timestamps `timestamptz`, UTC. UUID PKs.
- Foreign keys enforced; indexes on search columns (city, country, type_id, price, rating, lat/lng), booking(property_id, check_in, check_out), and FK columns.

## 5. Media
Only metadata/URLs in DB (PropertyMedia: url, provider, width, height, kind[image/video], position, alt). Binaries live in object storage.

## 6. Auditing
AuditLog captures actor, action, target type/id, before/after (jsonb), timestamp for admin/moderation/payment-sensitive actions.

## 7. Implementation notes (prompts 10/11, session 4)

**`property.host_id` references `app_user.id` directly, not a `HostProfile` row.**
`HostProfile` (this section's ERD, `hosts` module) doesn't exist yet — creating it
wasn't in prompt 10's file scope (`hosts/` isn't a "MAY modify" path; the deliverables
listed only `Property, PropertyType, Amenity/PropertyAmenity, Availability`). `host_id`
is FK-enforced against `app_user(id) ON DELETE RESTRICT`. When `HostProfile` is built,
this will need a migration to repoint the FK.

**`property_amenity` is a plain two-column join table**, mapped as a JPA `@ManyToMany`
on `Property` — no separate `PropertyAmenity` entity class, since the join carries no
extra columns (no `PropertyAmenity 1-1` attributes were specified anywhere).

**`availability.booking_id` has no FK** — it's a plain `uuid` column reserved for
prompt 12, which is the first prompt to create a `booking` table. Search's
availability-exclusion query (`is_blocked = true OR booking_id IS NOT NULL`) already
accounts for it being populated later.

**Search read-path (`V4__search_indexes.sql`):** `pg_trgm` + GIN indexes on
`city`/`state`/`country` for destination `ILIKE` matching, plus partial B-tree indexes
`WHERE status = 'ACTIVE'` on `base_price`, `rating_avg`, `created_at`, `capacity`,
`bedrooms` (every search query filters on `status = 'ACTIVE'` first, so scoping the
indexes to that predicate keeps them small against draft/pending/suspended churn).

## 8. Implementation notes (prompt 12, session 6)

**`platform_setting`** — a minimal key/value table (`V5__booking.sql`), not the
`PlatformSetting` entity this ERD's overview lists in the abstract. It's seeded with
one row (`commission_pct`) that `pricing.service.PricingService` reads. A full admin
read/write API over this table is prompt 18's deliverable ("commission/settings"
explicitly named there), not this one — see backend/02-domain-modules.md's session 6
notes.

**`booking.property_id`/`guest_id` are plain UUID columns, not JPA associations** —
unlike `Property.type`, a booking should stay a readable historical record even if the
referenced property is later suspended; see `booking.web.BookingPropertySummary`'s
Javadoc.

## 9. Implementation notes (prompts 13/14, session 7)

**`payment.booking_id` DOES have a real FK** (`REFERENCES booking(id)`), unlike
`booking.property_id`/`guest_id`'s deliberate plain-UUID pattern above — a payment
without a genuine booking behind it isn't a valid state, so referential integrity is
enforced at the DB layer even though the Java entity still uses a plain UUID field
(same modular-monolith reasoning as everywhere else: `payments/` shouldn't take a JPA
association onto `booking/`'s entity graph).

**`transaction.raw_payload` is `text`, not `jsonb`.** No other table in this schema
uses `jsonb` yet (the data model's own `AuditLog` design assumes it, but `AuditLog`
itself isn't built). Storing the raw webhook/response body as plain text avoids
introducing Hibernate JSON-type mapping as a one-off for a single column; revisit if
`AuditLog` (a later prompt) establishes a real `jsonb` convention this should follow.

**`payout` accrues per `(host_id, period, currency)`** (unique constraint) rather than
one row per booking — `PaymentService.accruePayout` finds-or-creates the period's row
and adds to it. `period` is a plain `"YYYY-MM"` string, not a date range column.

**`property_media.property_id` has a real FK** (`ON DELETE CASCADE` — media rows are
meaningless without their listing) despite `media/` being a separate module from
`properties/`, for the same reason `payment.booking_id` does: this is a strict
ownership relationship, not a "should survive independently" one like `Booking`'s
references.

**The double-booking exclusion constraint** (ADR-008) is `EXCLUDE USING gist
(property_id WITH =, daterange(check_in, check_out, '[)') WITH &&) WHERE status IN
('PENDING','CONFIRMED','COMPLETED')` — `check_out` is exclusive, matching how search's
availability-exclusion query (prompt 11) already treats date ranges. `PENDING` is
included deliberately: a hold has to actually occupy the dates for the hold window to
mean anything. Expired holds are cleaned up by `BookingService` (lazily on the next
write for that property, and via a periodic `@Scheduled` sweep) rather than a DB-level
expiry mechanism, since Postgres exclusion constraints have no concept of "expires."

**`booking.idempotency_key`** has a partial unique index on `(guest_id,
idempotency_key) WHERE idempotency_key IS NOT NULL` — a retried request with the same
key resolves to the same row rather than a duplicate.

## 10. Implementation notes (prompt 15, session 16)

**`review.booking_id` is a real, UNIQUE FK** (`REFERENCES booking(id) ON DELETE
RESTRICT`), same reasoning as `payment.booking_id` (§9): a review without a genuine
booking behind it isn't a valid state. The `UNIQUE` constraint enforces "one review per
booking" at the DB level, not just in `ReviewService`. **`review.property_id`/
`guest_id` are plain UUID columns**, mirroring `booking.property_id`/`guest_id` (§8) —
a review should stay a readable historical record even if the referenced property or
profile later changes.

**`property.rating_avg`/`rating_count` are recomputed from source on every new
review** (`SELECT AVG(rating), COUNT(*) FROM review WHERE property_id = ...`), not
incrementally mutated — avoids floating-point drift, and there's no edit/delete-review
capability in this prompt's scope to make the extra query a real cost.

**`favorite.user_id`/`property_id` DO have real FKs, both `ON DELETE CASCADE`** —
unlike `review`/`booking`'s "must survive independently" pattern, a favorite has no
such requirement: deleting the user or the property should correctly remove the
favorite, not orphan it. `UNIQUE (user_id, property_id)` prevents duplicate favorite
rows; the entity still uses plain UUID fields at the JPA level (same convention as
every other cross-aggregate reference in this schema — real referential integrity at
the DB layer, no JPA association).

**Structural gap, not closed by this prompt** (out of `reviews/`'s file scope):
nothing in `booking/` ever transitions a real booking to `COMPLETED` — confirmed by
grep, and already flagged in `devops/03-production-readiness.md`'s go/no-go record as
a feature-completeness blocker. Review eligibility-gating is real and correctly checks
for a `COMPLETED` booking, but is only reachable today via a directly-constructed test
fixture (`ReviewFlowIT`), the same "test-only fixture shortcut" `BookingFlowIT` already
uses for stale-hold setup — not through any live product flow.

## 11. Implementation notes (prompt 16, session 17)

Prompt 16's own documentation-update list names architecture/backend docs, not this
one — this section is added anyway, on the same reasoning every prior migration-adding
session has followed: this is the one canonical place schema decisions get recorded,
and this ERD's own overview (§1) already named `Conversation`/`Message`/`Notification`
as entities a future session would build.

**`conversation.property_id`/`host_id`/`guest_id` are plain UUID columns**, same
historical-record reasoning as `booking`/`review`'s equivalents (§8, §10).
**`conversation.booking_id` IS a real, nullable FK** (`ON DELETE SET NULL`, not
`CASCADE` — losing the booking link shouldn't delete the conversation itself, unlike
`message.conversation_id` below). `UNIQUE (property_id, guest_id)` — one thread per
guest per property; a booking, once it exists, attaches to that same thread rather
than spawning a parallel one.

**`message.conversation_id` has a real FK, `ON DELETE CASCADE`** (a message is
meaningless without its conversation, same category as `property_media.property_id`,
§9). `message.sender_id` is a plain column, same reasoning as `conversation.host_id`/
`guest_id`.

**`notification.user_id` has a real FK, `ON DELETE CASCADE`** (deleting a user removes
their notifications, same reasoning as `favorite.user_id`, §10). `notification.type`
has a `CHECK` constraint listing exactly the three values the enum currently has
(`BOOKING_CONFIRMED`, `BOOKING_CANCELLED`, `MESSAGE_RECEIVED`), same convention as
`booking.status`. `notification.link_id` is a plain, untyped UUID (a bookingId or
conversationId, whichever produced the notification) for client-side deep-linking —
deliberately not `jsonb`, continuing §9's "no real jsonb convention exists yet in this
schema" reasoning rather than introducing one for a single column.

## 12. Implementation notes (prompt 18, session 19)

**`audit_log` is the first table in this schema to actually use `jsonb`** — every
prior session that touched a similar "should this be jsonb" decision (§9's
`transaction.raw_payload`, §10's review reasoning, §11's `notification.link_id`) said
some version of "no real jsonb convention exists yet, revisit once AuditLog
establishes one." This is that session. `before`/`after` use Hibernate 6's native
`@JdbcTypeCode(SqlTypes.JSON)` mapped to a Java `String` field holding pre-serialized
JSON (via a plain injected `ObjectMapper`, not a custom Hibernate `UserType`) —
simplest option that actually works, no extra dependency. `actor_id` is a real,
nullable FK (`ON DELETE SET NULL` — the log entry must outlive the actor account
being later removed). `target_type`/`target_id` are a plain, polymorphic pair (no
single FK target is possible across every table admin actions touch) — matches this
ERD's own §1 description ("AuditLog (polymorphic actor/target)") exactly.

**`verification_request.user_id` and `dispute.booking_id` follow the same two
established patterns exactly, not new ones**: `verification_request.user_id` is a
real FK, `ON DELETE CASCADE` (same "no reason to survive a deleted account"
reasoning as `favorite.user_id`, §10). `dispute.booking_id` is a real FK,
`ON DELETE RESTRICT` (same "not a valid state without a genuine booking behind it"
reasoning as `payment.booking_id`/`review.booking_id`, §9/§10). `dispute.raised_by`
is a plain UUID column — could be either the booking's guest or the listing's host,
validated at raise-time in application code, not by a DB constraint — same
historical-record reasoning as `booking.guest_id` (§8). `verification_request.
reviewed_by`/`dispute.resolved_by` are real, nullable FKs to the reviewing/resolving
admin, `ON DELETE SET NULL` (the record must outlive that admin account being later
removed).

**`platform_setting` (§8) got its first mutator this session** — a public
`(key, value)` constructor and a `setValue` setter, both trivial, both exactly what
that class's own session-6 Javadoc already said prompt 18 would add. No schema
change; the table itself has existed since `V5__booking.sql`.
