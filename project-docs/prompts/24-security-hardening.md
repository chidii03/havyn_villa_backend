# Prompt 24 — Security Hardening

> Phase: 11 · Order: 24 · Depends on: 09, 13, 14, 23
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Harden the platform against the OWASP Top 10 and the documented threat model: authz/IDOR, rate limiting, headers/CSP, CORS, webhook verification, upload validation, secret management, and audit coverage.

## 2. Prerequisite documents
- `../security/01-security-plan.md`

## 3. Deliverables (exact)
- Security controls implemented/verified per plan; dependency + SAST scanning in CI.
- Rate limiting on auth/search/booking/messaging; secure headers + CSP; CORS allowlist.
- Pen-test-style checklist executed; findings triaged/fixed.

## 4. Constraints
- No secrets in repo/images/logs; least-privilege DB roles.
- Verify all webhooks; enforce object-level authz everywhere.
- PII access restricted + audited.

## 5. Acceptance criteria
- Threat-model controls verified by tests; scanners clean or risk-accepted with rationale.
- No high/critical findings open at completion.

## 6. Files you MAY modify
- security-related backend config + CI
- `../security/*` (updates)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- Authz/IDOR tests; rate-limit tests; webhook signature tests; header/CSP checks.

## 9. Documentation updates required
- Update `../security/01-security-plan.md` with verification status.

## 10. Verification before completion
- Security checklist complete; scanners green.
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
