# Business Model

> Status: Draft · Phase 0/1. Principle: monetize the simplest path first; architect for more later.

## 1. Revenue streams (explored)
| Stream | MVP? | Notes |
|---|---|---|
| Booking commission (host-side %) | **Yes (primary)** | Deducted from payout; simplest, aligned incentive |
| Guest service fee | Should | Small % added at checkout, shown transparently |
| Host service fee | Could | Alternative/added to commission |
| Featured listings | Later | Marketplace liquidity needed first |
| Host subscriptions / pro tools | Later | Post-MVP SaaS layer |
| Property-management SaaS | Later | Adjacent expansion |
| Partner commissions / experiences | Later | Future categories |

## 2. Recommended MVP monetization
**Host booking commission** (e.g., configurable %, default illustrative 12–15%) + optional small, transparent **guest service fee**. Commission is a first-class, admin-configurable platform setting; it must be computed server-side and stored per booking (never hardcoded).

## 3. Unit economics (illustrative, to validate)
Revenue/booking = commission + guest fee. Costs = payment processing, media storage/CDN, infra, support, fraud/chargebacks. Track contribution margin per booking.

## 4. Pricing/fee transparency (brand-critical)
All fees disclosed before commitment; the "whole price" promise is a competitive differentiator and a UI requirement (see PRD 2.4).

## 5. Marketplace liquidity strategy (high-level)
Seed supply first (host onboarding + verification), concentrate demand by geography/launch market, use trust features (reviews, verification, dependable refunds) as the flywheel.
