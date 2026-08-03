# Prompt 27 — Docker & Deployment

> Phase: 13 · Order: 27 · Depends on: 08, 19, 26
> This is an implementation prompt for Claude Code. Execute ONLY after prerequisites are approved. Build on prior prompts; do not create disconnected work.

## 1. Objective
Containerize backend and web, finalize docker-compose for local, and define deployment to staging/production with migrations and health checks.

## 2. Prerequisite documents
- `../devops/01-devops-and-deployment.md`

## 3. Deliverables (exact)
- Multi-stage Dockerfiles (non-root) for backend + web; hardened images.
- docker-compose (postgres, redis, backend, web, mail/minio for local).
- Deployment manifests/config for staging+prod with Flyway gating, health/readiness probes, and secrets via manager.

## 4. Constraints
- No secrets in images/repo; env-driven config.
- Reversible-aware migrations; immutable images promoted across envs.
- Least-privilege runtime.

## 5. Acceptance criteria
- `docker-compose up` runs the full stack locally; images deploy to staging with green health checks.
- Migrations run safely on deploy.

## 6. Files you MAY modify
- Dockerfiles, compose, deploy manifests
- `../devops/01-devops-and-deployment.md` (updates)

## 7. Files you MUST NOT modify
- `00-MASTER-INDEX.md` (except appending status ticks when instructed)
- Approved brand identity tokens without an ADR
- Any prompt file in `prompts/` (these are instructions, not code)

## 8. Tests required
- Container smoke tests; migration-on-deploy test; health-probe verification.

## 9. Documentation updates required
- Update `../devops/01-devops-and-deployment.md`.

## 10. Verification before completion
- All new/changed tests pass locally and in CI.
- No hardcoded secrets, no fake production data, no frontend-only business logic introduced.
- Relevant docs updated and cross-links valid.
- Self-review against this prompt's acceptance criteria before marking complete.
