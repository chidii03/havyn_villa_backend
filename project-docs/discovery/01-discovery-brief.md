# Discovery Brief — Havyn

> Status: Draft for approval · Phase 0 · Owner: Product/CTO
> Synthesizes the project context brief into a shared understanding of *what* we are building and *why*.

## 1. Problem
Booking a place to stay is high-stakes and low-trust: photos mislead, fees hide until checkout, hosts go silent, and cancellations/refunds are opaque. Hosts, meanwhile, lack simple, professional tools to list, price, and manage properties and to get paid reliably.

## 2. Opportunity
A premium, trust-first property marketplace ("Havyn") that treats booking as a serious domain: honest listings, whole prices computed authoritatively on the backend, dependable payouts, and a design that feels like a modern travel company rather than a template.

## 3. Product in one sentence
Havyn is a production-grade PropTech marketplace where travelers discover, book, and pay for quality stays, and hosts list, manage, and earn — built API-first so web and a future mobile app share one backend.

## 4. Goals (MVP)
- Travelers can search, view, and **book** a real property with correct pricing and no double-booking.
- Hosts can create listings, set availability/pricing, and manage reservations.
- Payments flow through a provider-agnostic abstraction with backend verification.
- Admins can moderate and operate the marketplace.
- A single Spring Boot API serves web today and mobile later.

## 5. Non-goals (MVP)
Experiences marketplace, hotels/long-stay categories, AI/personalized discovery, geospatial radius search, native mobile app, multi-currency settlement, host subscriptions. **Architected for, not built in, MVP.**

## 6. Primary roles
Guest (unauth browse) · Customer/Traveler · Host · Admin. RBAC enforced backend-side.

## 7. Constraints & principles
Modular monolith (not microservices) first; backend is the authoritative business layer; no business-critical math on the frontend; no hardcoded secrets; object storage for media (never blobs in Postgres); Postgres is the source of truth; Redis only where it adds real value.

## 8. Success metrics (post-MVP targets, illustrative)
Search→detail CTR, detail→booking conversion, booking success rate (no double-book/errors), payment success rate, host activation (listing published), time-to-first-booking, review submission rate.

## 9. Open questions (non-blocking; tracked)
Launch market(s) & currency, payment provider priority (Stripe vs Paystack/Flutterwave for target geography), maps provider (Mapbox vs Google), media provider (Cloudinary vs S3/R2). Defaults are proposed in architecture docs and can be changed via ADR.

## 10. Blocking questions
None at discovery stage. Asset review (videos/images/design refs) will refine brand/UX but does not block documentation.
