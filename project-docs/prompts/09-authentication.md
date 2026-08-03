# Prompt 09 — Authentication & RBAC

> Phase: 3 · Order: 09 · Depends on: 08
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Implement secure auth: register/login/refresh/logout, email verification, password reset, JWT access + rotating refresh, and RBAC for the four roles.

## 2. Prerequisite documents
- `../security/01-security-plan.md`
- `../backend/02-domain-modules.md`
- `../architecture/03-api-design.md`

## 3. Deliverables (exact)
- Auth endpoints + DTOs; Argon2/BCrypt hashing; JWT access + rotating refresh with reuse detection (Redis).
- RBAC (Guest/Customer/Host/Admin) via method security; object-level checks scaffolding.
- Email verification + password reset (single-use, expiring tokens).

## 4. Constraints
- Refresh in httpOnly Secure cookie (web) and bearer-capable (mobile).
- No account-enumeration leaks; rate-limit auth endpoints.
- Secrets server-side only.

## 5. Acceptance criteria
- Full auth lifecycle works; refresh rotates and detects reuse; RBAC blocks unauthorized access.
- Passwords never stored in plaintext; tokens expire correctly.

## 6. Files you MAY modify
- backend `auth/`, `users/`, `security/config`
- `../security/01-security-plan.md` (updates)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- Unit: hashing, token rotation, reuse detection.
- Integration: register→verify→login→refresh→logout (Testcontainers).
- Authz tests: role + object-level (IDOR) checks.

## 9. Documentation updates required
- Update `../security/01-security-plan.md`; OpenAPI reflects auth.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
