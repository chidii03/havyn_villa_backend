# Prompt 18 — Admin Platform

> Phase: 10 · Order: 18 · Depends on: 10, 12, 13, 15, 16
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Deliver admin capabilities: manage users/hosts/properties; moderate listings; manage bookings/payments/disputes/reviews/reports; KYC/verification workflow; commissions & platform settings; analytics — API plus web UI.

## 2. Prerequisite documents
- `../product/01-product-requirements.md`
- `../security/01-security-plan.md`
- `../frontend/01-frontend-foundation.md`

## 3. Deliverables (exact)
- Admin APIs for moderation queues, disputes, KYC review, commission/settings, analytics reads.
- AuditLog writes on all sensitive admin actions.
- Web admin dashboard (Next.js) with RBAC-gated access.

## 4. Constraints
- Admin-only via RBAC + object checks; every sensitive action audited.
- Commissions/settings are configurable data, never hardcoded.
- Least-privilege; PII access restricted and logged.

## 5. Acceptance criteria
- Admin can moderate, resolve disputes, review KYC, set commissions, and view analytics.
- All sensitive actions produce audit entries.

## 6. Files you MAY modify
- backend `admin/`, `audit/`
- frontend `app/admin/*`
- doc updates

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- Backend: admin authz + audit logging (Testcontainers).
- Frontend: admin components + a11y.
- E2E: moderate a listing / resolve a dispute.

## 9. Documentation updates required
- Update product/security/frontend/backend docs; OpenAPI.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
