-- V8__reviews_favorites.sql
-- Reviews & favorites (prompt 15). Reviews are gated on a COMPLETED booking (see
-- BookingStatus) and update property.rating_avg/rating_count transactionally, recomputed
-- from source rather than incrementally mutated (see backend/02-domain-modules.md's
-- prompt 15 notes). Favorites are a plain auth-user-to-property join.

-- ---------------------------------------------------------------------------
-- review — property_id/guest_id are plain columns, no FK (mirrors booking.property_id/
-- guest_id: a review should stay a readable historical record even if the referenced
-- property/profile later changes). booking_id IS a real, UNIQUE FK — a review without a
-- genuine booking behind it isn't a valid state (mirrors payment.booking_id), and UNIQUE
-- enforces "one review per booking" at the DB level too, not just in application code.
-- ---------------------------------------------------------------------------
CREATE TABLE review (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  uuid NOT NULL UNIQUE REFERENCES booking (id) ON DELETE RESTRICT,
    property_id uuid NOT NULL,
    guest_id    uuid NOT NULL,
    rating      integer NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_review_property_id ON review (property_id, created_at DESC);
CREATE INDEX idx_review_guest_id ON review (guest_id);

-- ---------------------------------------------------------------------------
-- favorite — unlike review/booking, a favorite has no "must survive independently"
-- requirement, so it gets real FKs with ON DELETE CASCADE (deleting the user or the
-- property should correctly remove the favorite, not orphan it).
-- ---------------------------------------------------------------------------
CREATE TABLE favorite (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    property_id uuid NOT NULL REFERENCES property (id) ON DELETE CASCADE,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, property_id)
);

CREATE INDEX idx_favorite_user_id ON favorite (user_id, created_at DESC);
CREATE INDEX idx_favorite_property_id ON favorite (property_id);
