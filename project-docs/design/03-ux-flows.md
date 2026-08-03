# Key UX Flows

> Status: Draft · Phase 1. Text-flow specs; wireframes to follow in design tooling.

## 1. Discover → Book (core conversion path)
Home (search hero) → enter destination/dates/guests → Results (list + map, filters) → Property detail (gallery, amenities, reviews, sticky booking widget showing server-computed total) → Review & pay → Confirmation → Trip in "Trips".
- Guest may browse Home/Results/Detail unauthenticated; account required at "Reserve".

## 2. Become a host → publish listing
Profile → "Become a host" (role upgrade + KYC start) → Listing wizard (basics → location → photos → amenities → rules → availability → pricing → review) → Publish → Moderation/Live → appears in search.

## 3. Manage a reservation (host)
Host dashboard → Reservations → accept/decline (if request-to-book) or view (instant) → message guest → post-stay payout → review.

## 4. Cancellation & refund (traveler)
Trips → booking → Cancel → policy preview (refund amount/state) → confirm → refund initiated → status updates + notification.

## 5. Messaging
From detail or booking → Conversation (per property/booking) → in-app + email notification on new message.

## 6. Admin moderation
Admin → queue (reports/new listings/KYC) → action (approve/reject/suspend) → audit-logged → notify affected user.

## States to design for every flow
loading · empty · error · offline/degraded · success · permission-denied. Calm, specific copy per the voice guide.

## Implementation note (prompt 21, session 6)

Flow 1 ("Discover → Book") currently ends at **"reserve a real hold"**, not "pay ->
confirmation" — there's no payment provider yet (prompt 13), so `BookingWidget`
stops at an honest "dates held, payment isn't available yet" state rather than a fake
checkout/payment screen. Flow 4 ("Cancellation & refund") is real for the reachable
case (a `PENDING` hold cancels for free) — the refund-amount/policy-preview math is
built and tested, but can't be exercised end-to-end against a paid booking until
prompt 13 exists. See `backend/02-domain-modules.md` and `frontend/01-frontend-
foundation.md`'s session 6 notes.
