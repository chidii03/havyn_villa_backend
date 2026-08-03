# Prompt 21 — Booking & Checkout UI

> Phase: 6 · Order: 21 · Depends on: 12, 13, 20
> Build the booking widget, checkout, confirmation, and Trips. Server totals only.

## 1. Objective
Implement the booking + checkout experience: the sticky **BookingWidget** on `/rooms/[id]` (date/guest selection → server `/quote` → server-computed breakdown → reserve with Idempotency-Key), provider checkout, confirmation, and the **/trips** list with cancellation.

## 2. Prerequisite documents
- `../design/03-ux-flows.md`, `../architecture/03-api-design.md`, `../architecture/04-integrations.md` (payment provider)
- `../frontend/03-ui-and-navigation-spec.md`

## 3. Deliverables (exact)
- **BookingWidget** (client): date-range + guest steppers (respecting capacity), calls `POST /properties/{id}/quote`, renders the **server** breakdown (nights, base, cleaning, service, discounts, taxes, total), reserve button (auth-gated; `Idempotency-Key`).
- **Checkout**: integrate the configured payment provider's client SDK/redirect (Paystack/Flutterwave/Stripe); handle 3DS/redirect; show confirmation only after backend webhook verification.
- **Confirmation** page + **/trips** (upcoming/past, status, receipt, **cancel** → policy preview → refund state).
- States: unavailable / hold-expired / payment-failed handled with calm, specific copy; brand-blue primary actions.

## 4. Constraints
- **Display server totals only** — never compute the authoritative price client-side.
- No secret payment keys on the frontend — use provider client tokens/redirects; backend verifies webhooks.
- Concurrency: surface a clear "no longer available" error if a hold/date is lost.
- Accessibility on calendar, steppers, and checkout; brand blue for active/selected/focus.

## 5. Acceptance criteria
- User completes search → book → pay → confirmation against real APIs; the total shown equals the backend's.
- Reserving twice (same Idempotency-Key) does not double-book/charge; cancel initiates the correct refund state.
- All booking states render correctly.

## 6. Files you MAY modify
- `apps/web/**` (booking/checkout/trips), `packages/shared/**`
- `../frontend/*` (doc updates)

## 7. Files you MUST NOT modify
- `apps/api/**` booking/pricing/payment logic (consume only)
- Any other prompt file; design tokens without an ADR

## 8. Tests required
- Component: BookingWidget (renders server breakdown; never computes total), checkout states, TripCard cancel.
- a11y (axe) on booking + checkout.
- E2E: full booking + payment (provider test mode) + cancel.

## 9. Documentation updates required
- Update frontend + UX docs with booking/checkout flow and states.

## 10. Verification before completion
- Verify server-total parity, idempotent reserve, cancel/refund preview, all error states.
- Build + tests + axe + E2E green; docs updated.
