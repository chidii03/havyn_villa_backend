# Prompt 08 — Backend Foundation

> Phase: 3 · Order: 08 · Depends on: 06, 07
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Scaffold the Spring Boot application: project layout, config, error envelope, validation, Actuator, OpenAPI, Flyway, Postgres + Redis wiring, and the module skeletons.

## 2. Prerequisite documents
- `../backend/01-backend-foundation.md`
- `../backend/02-domain-modules.md`
- `../database/*`
- `../architecture/*`

## 3. Deliverables (exact)
- Booting Spring Boot (Java 21) app with modular package structure.
- Global error handling, Bean Validation, pagination, correlation-id logging.
- Actuator health/readiness, OpenAPI/Swagger UI, Flyway `V1__init.sql` with reference-data seeds.
- docker-compose for local Postgres + Redis.

## 4. Constraints
- No Prisma; Spring Boot only.
- 12-factor config; secrets via env; none hardcoded.
- Enforce module boundaries.

## 5. Acceptance criteria
- `./gradlew bootRun` (or mvn) starts; health endpoint green; Swagger reachable; migrations apply on a clean DB.
- Structured error envelope returned for a forced error.

## 6. Files you MAY modify
- backend source tree
- `../backend/*` (doc updates)
- docker-compose (local)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)
- Frontend source (not created yet)

## 8. Tests required
- Boot/context-load test.
- A repository/integration test using Testcontainers (Postgres + Redis).
- An error-handler test.

## 9. Documentation updates required
- Update `../backend/01-backend-foundation.md` with any deviations + run instructions.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
