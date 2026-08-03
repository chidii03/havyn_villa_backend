# Prompt 13 — Payments

> Phase: 7 · Order: 13 · Depends on: 12
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Implement the provider-agnostic payment layer: create intents, verify via signed webhooks, record Payment/Transaction/Refund, and accrue host payouts — with all secrets server-side.

## 2. Prerequisite documents
- `../architecture/02-adr-index.md` (ADR-004)
- `../security/01-security-plan.md`
- `../backend/02-domain-modules.md`

## 3. Deliverables (exact)
- `PaymentProvider` port + at least one adapter (Stripe or Paystack/Flutterwave per target market) selectable by config.
- `POST /payments/intent`; signed webhook handler `POST /payments/webhook/{provider}` (idempotent).
- Payment/Transaction/Refund persistence; booking marked paid only after verified webhook; Payout accrual.

## 4. Constraints
- Secret keys NEVER exposed to any client; verify webhook signatures.
- Idempotent webhook processing (no double charge/credit).
- Booking is authoritative — payment confirms, does not compute price.

## 5. Acceptance criteria
- Successful payment confirms booking after webhook verification; failed/again-tried webhooks do not double-apply.
- Refund flow initiates via provider and updates state; payout accrues to host.

## 6. Files you MAY modify
- backend `payments/`
- migrations
- `../security/*`, `../backend/*` (updates)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)
- `booking/` pricing/overlap logic (consume, don't alter)

## 8. Tests required
- Unit: adapter + signature verification (mock provider).
- Integration: intent→webhook→booking paid (Testcontainers).
- Idempotency + refund tests.

## 9. Documentation updates required
- Update security/backend/architecture docs; OpenAPI; document provider config.

## 10. Verification before completion
- Webhook signature + idempotency verified.
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
