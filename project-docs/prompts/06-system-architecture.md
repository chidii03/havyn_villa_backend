# Prompt 06 — System Architecture

> Phase: 2 · Order: 06 · Depends on: 03, 05
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Finalize the modular-monolith, API-first architecture, module boundaries, event model, integration abstractions, and ADRs.

## 2. Prerequisite documents
- `../architecture/01-system-architecture.md`
- `../architecture/02-adr-index.md`
- `../architecture/03-api-design.md`

## 3. Deliverables (exact)
- Approved architecture doc + diagram.
- ADRs for backend, DB, Redis, payments, media, auth, web framework, double-booking.
- API design guidelines (versioning, errors, pagination, idempotency, auth).

## 4. Constraints
- Modular monolith first — no premature microservices.
- Backend authoritative; providers behind interfaces; secrets server-side.
- No Prisma; Spring Boot only.

## 5. Acceptance criteria
- Clear module boundaries with an extraction path.
- API conventions cover errors/pagination/idempotency/auth.

## 6. Files you MAY modify
- `../architecture/*`

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- N/A (architecture).

## 9. Documentation updates required
- Update `../architecture/*`; add ADRs for any decisions.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
