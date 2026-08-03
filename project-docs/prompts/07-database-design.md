# Prompt 07 — Database Design

> Phase: 2 · Order: 07 · Depends on: 06
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Finalize the PostgreSQL data model, ERD, concurrency strategy, and Flyway migration conventions.

## 2. Prerequisite documents
- `../database/01-data-model.md`
- `../database/02-migrations-and-conventions.md`
- `../architecture/*`

## 3. Deliverables (exact)
- Approved ERD + entity/column baseline.
- Concurrency design: booking overlap exclusion constraint + Redis hold.
- Migration conventions (Flyway, naming, seeds of reference data only).

## 4. Constraints
- Money = numeric; UUID PKs; timestamptz UTC; FKs indexed.
- Media metadata only in DB (binaries in object storage).
- No Prisma — Flyway migrations.

## 5. Acceptance criteria
- Data model supports all MVP features and prevents double-booking.
- Migration/seed strategy defined; no fake production data.

## 6. Files you MAY modify
- `../database/*`

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- N/A here — migrations tested in prompt 08+ with Testcontainers.

## 9. Documentation updates required
- Update `../database/*`.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
