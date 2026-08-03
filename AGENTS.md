# AGENTS.md — Havyn Villa (autonomous build guide)

> Instructions for any coding agent (Claude Code, etc.) building this repo. Companion to `CLAUDE.md` (which holds the product/stack/brand facts). This file defines **how to execute** so the human can mostly approve steps and watch it build.

## Prime directive
Build Havyn Villa to production quality by executing `project-docs/prompts/` **in order, one prompt per session**. The prompts and `project-docs/` are the source of truth. Brand + naming are DONE — do not re-open them. Study competitor UX for **patterns** only; all design, copy, icons, and assets are original.

## Before writing any code (every session)
1. Read `CLAUDE.md`, this file, `project-docs/00-MASTER-INDEX.md`, the current `project-docs/prompts/NN-*.md`, and its prerequisite docs.
2. State a short plan (files to create/change) and the target acceptance criteria.
3. Proceed. Ask the human **only** for genuinely blocking decisions; otherwise pick the documented default and note it.

## Definition of done (per task)
- Scope matches the prompt's deliverables; only "MAY modify" files touched.
- Migrations (Flyway `V__`), tests written and passing (backend: JUnit + Testcontainers for Postgres/Redis; frontend: React Testing Library + axe; E2E via Playwright where specified).
- Named docs updated; OpenAPI regenerated for API changes; `.env.example` updated for new keys.
- Build + tests + health checks run green. End with a summary: changed / tested / next.
- No hardcoded secrets, no fake data, no frontend-only business logic, no client-side authoritative pricing.

## Guardrails (hard stops)
- Do **not** add Prisma or a second backend. Do **not** put payment/media secrets or authoritative price math on the client.
- Do **not** store media binaries or full videos in Postgres — Cloudinary URL/metadata only.
- Do **not** skip the double-booking protection (tx + Postgres exclusion constraint + Redis hold).
- Do **not** modify another prompt's files or the design tokens without adding an ADR in `project-docs/architecture/`.

## Session plan (recommended order)
| Session | Prompt(s) | Outcome |
|---|---|---|
| 1 | Bootstrap + `08` | Monorepo + docker-compose (postgres/redis/minio/mailhog) + Spring Boot foundation (Actuator, Swagger, Flyway, error envelope) + tests green |
| 2 | `09` | JWT access + rotating refresh, RBAC, email verify, password reset |
| 3 | `19` | Next.js app shell: header tabs (active=brand blue), where/when/who search, footer, routes, auth wiring |
| 4 | `10` + `11` | Property domain + search/filter (availability-aware, Redis cache) |
| 5 | `20` | Explore/search/detail UI: category chips, carousels, Google Maps, gallery/lightbox, wishlist |
| 6 | `12` + `21` | Booking engine (server-verified price, no double-book) + booking/checkout UI |
| 7 | `13` + `14` | Payments (provider-agnostic, verified webhooks) + Cloudinary media (URL-only) |
| 8 | `22` | Responsive mobile web pass |
| 9 | `17` | Host dashboard (listings, calendar, reservations, earnings, payouts) |
| 10 | `15` + `16` | Reviews + favorites; messaging + notifications |
| 11 | `18` | Admin platform (moderation, disputes, KYC, commissions, analytics) |
| 12 | `23` + `24` | Test consolidation + security hardening |
| 13 | `25` + `26` | Performance + observability |
| 14 | `27` + `28` + `29` | Docker + CI/CD + production readiness |
| later | `30` + `31` | Expo mobile app + launch checklist |

## Session 1 bootstrap checklist (do first)
- Scaffold the monorepo in `CLAUDE.md`; init git; add `README.md`, `.gitignore`, `.editorconfig`, `.env.example` (keys only — see `project-docs/architecture/04-integrations.md`).
- `infra/docker-compose.yml`: PostgreSQL 16, Redis 7, MinIO, Mailhog, with healthchecks.
- `apps/api`: Spring Boot (Gradle) per prompt `08` — package-by-module, error envelope, validation, Actuator, Swagger, Flyway `V1__init.sql` (reference data only), Postgres+Redis via env.
- `apps/web`: Next.js App Router + Tailwind theme wired to blue tokens + shadcn/ui rebranded + TanStack Query + Zod/RHF + branded shell (logo/favicon).
- `packages/shared`: TS types + Zod schemas.
- Verify: `docker compose up` works, `/actuator/health` green, Swagger loads, migrations apply, web renders the branded shell, sample tests pass. Update the two foundation docs with run instructions.

## Environment keys (never commit values)
See `project-docs/architecture/04-integrations.md` §5 — Cloudinary, Google Maps (browser + server), payments provider + webhook secret, email. Put placeholders in `.env.example`.

## Human-in-the-loop
The human wants to mostly click "approve" and watch it build. So: keep each step reviewable, commit at the end of every green session, prefer safe reversible changes, and clearly flag anything that needs a real credential (Cloudinary/Maps/payment keys) or a legal step (name/trademark clearance) rather than inventing it.
