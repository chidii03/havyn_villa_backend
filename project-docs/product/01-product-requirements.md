# Product Requirements (PRD) — Havyn MVP

> Status: Draft for approval · Phase 1 · Owner: Product.
> Scope = MVP. Future items are explicitly marked. Requirements use MoSCoW (Must/Should/Could/Won't-now).

## 1. Scope summary
Authentication, profiles, property discovery/search/filter, property detail + media gallery, amenities, map/location, availability calendar, booking engine, pricing calculation, favorites, host listings + dashboard, booking management, payments, reviews, notifications, admin dashboard.

## 2. Functional requirements

### 2.1 Accounts & Auth (Must)
Email/password registration + login; email verification; password reset; RBAC (Guest/Customer/Host/Admin); refresh-token rotation; profile management. Host role obtained via "Become a host" upgrade.

### 2.2 Discovery & Search (Must)
Search by destination (city/country), date range, guests. Filters: price range, property type, bedrooms/beds/baths, amenities, rating. Sort options. Results as list + map markers. Pagination/infinite scroll. **Future:** geospatial radius, recommendations, AI search.

### 2.3 Property detail (Must)
Gallery, description, amenities, house rules, location preview (map), host card, reviews & rating summary, availability calendar, sticky booking widget with live price breakdown.

### 2.4 Booking engine (Must)
Date/guest selection validated against capacity, availability, blocked dates, and existing reservations. **Server computes and verifies** nights × base rate + cleaning fee + service fee − discounts + taxes + commission. Temporary hold to prevent double-booking. Booking statuses; cancellation & refund states. See `backend/` + `architecture/`.

### 2.5 Payments (Must)
Provider-agnostic checkout (Stripe/Paystack/Flutterwave via abstraction). Backend creates payment intents, verifies via webhook, records Payment/Transaction/Refund. No secrets on frontend.

### 2.6 Favorites (Should)
Save/unsave properties; view saved list (auth required).

### 2.7 Reviews (Must)
Only after an eligible completed stay. Structured ratings + text. Aggregate rating on property.

### 2.8 Messaging (Should MVP / Must later)
Guest↔host conversations tied to a property/booking. **Future/realtime:** websockets/SSE.

### 2.9 Notifications (Should)
Booking confirmations, messages, status changes — in-app + email. Channel-agnostic notification service.

### 2.10 Host dashboard (Must)
Create/edit listings, upload media, set amenities/availability/pricing, manage reservations, view earnings & payouts, view reviews & performance.

### 2.11 Admin dashboard (Must)
Manage users/hosts/properties; moderate listings; manage bookings/payments/disputes/reviews/reports; platform settings; analytics; commissions; verification/KYC workflow.

## 3. Non-functional requirements
Performance (p95 API < 300ms for reads under normal load, cached hot paths), availability, security (OWASP, see `security/`), accessibility (WCAG 2.2 AA), observability (logs/metrics/traces), horizontal-scalability-ready, i18n-ready copy, mobile-responsive web.

## 4. Out of scope for MVP
Hotels/long-stay/experiences categories, host subscriptions & pro tools, multi-currency settlement, native mobile app, AI/personalization. Architecture must not preclude them.

## 5. Acceptance (MVP "done")
A traveler can register → search → open a real listing → select valid dates/guests → see a correct server-verified total → pay via a provider → receive confirmation → later review the stay; a host can publish a listing and manage the resulting reservation and see the payout; an admin can moderate and resolve a dispute. All covered by automated tests.
