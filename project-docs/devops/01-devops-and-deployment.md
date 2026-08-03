# DevOps & Deployment

> Status: Draft · Phase 13 · Owner: DevOps Engineer.
>
> **See `03-production-readiness.md` for the prompt 29 go/no-go decision:
> NO-GO for public launch as of session 15**, with the full itemized checklist
> and the specific path to a GO decision. Read that before treating anything
> below as "ready" — most of it is real and tested to the extent this
> engagement's sandbox allows, but none of it has run against real production
> traffic or a real deployed cluster.

## Local
docker-compose: postgres, redis, backend, web, (mailhog + minio for local media/email). One command up. Flyway migrates on start. Seed reference data only.

## Build & containers
Multi-stage Dockerfiles (backend JRE-slim; web standalone Next output). Pin versions. Non-root containers. Health endpoints (Actuator, Next healthcheck).

## Environments
local → staging → production, each isolated config/secrets. Immutable images promoted across envs. Blue/green or rolling deploys; migrations gated and reversible-aware.

## CI/CD
On PR: lint, typecheck, unit+integration (Testcontainers), build images, security scan. On merge to main: build+push images, deploy staging, run E2E/smoke, manual/auto promote to prod. Rollback = redeploy previous image; DB changes forward-compatible.

## Infrastructure (target)
Container platform (managed k8s or equivalent) with autoscaling stateless API + web; managed Postgres (with read replica option) + backups/PITR; managed Redis; object storage + CDN for media; secrets manager; WAF/rate limiting at edge. Everything provider-swappable per ADRs.

## Config & secrets
12-factor env vars; secrets via manager (never in repo/images/logs); least-privilege credentials; rotation.

## Prompt 27 (session 13) — status and deviations

**Containers, new:** `apps/api/Dockerfile` (multi-stage: `eclipse-temurin:21-jdk-
alpine` build → `21-jre-alpine` runtime, non-root `havyn` user, `HEALTHCHECK`
against `/actuator/health`) and `apps/web/Dockerfile` (multi-stage: `node:20-alpine`
deps → build → runtime, non-root, Next.js standalone output). **`output:
"standalone"` was added to `next.config.ts`** (wasn't set before this prompt) — its
exact monorepo output structure (`.next/standalone/apps/web/server.js`, with
`node_modules` at the standalone root, not nested under `apps/web/`) was verified
by actually running `next build` locally and inspecting the real directory tree
before writing the Dockerfile's `COPY` paths, not assumed from a non-monorepo
example. Both images: pinned base versions (matches this repo's existing
`postgres:16-alpine`/`redis:7-alpine` pinning convention, not stricter), non-root,
`.dockerignore`d appropriately.

**A real architectural gap surfaced while wiring `NEXT_PUBLIC_API_BASE_URL` into
docker-compose, not swept under the rug:** `apps/web/src/lib/api/http.ts` uses one
URL for both browser (client-side) and Server Component (SSR) fetches. In a
multi-container topology, those need *different* hostnames (the browser needs
`localhost:8080` via the published port; server-side fetches from inside the web
container need the backend's container/service name). This codebase has no
server/client URL split today, and fixing it touches `apps/web/src/lib/api/*` —
outside this prompt's file scope (Dockerfiles/compose/deploy manifests only). Set to
`localhost:8080` (correct for the browser — the primary way this stack gets
exercised locally) and documented plainly in `infra/docker-compose.yml` itself, with
the specific fix and the reason it wasn't made here, for whoever next has that
module's file scope.

**`docker-compose.yml`**, updated — previously only stood up infrastructure
(postgres/redis/minio/mailhog); now also `backend` and `web` (this prompt's own
explicit deliverable: "docker-compose (postgres, redis, backend, web, mail/minio for
local)"), with `depends_on: condition: service_healthy` chains and real healthchecks
on every service.

**Kubernetes manifests, new** — `infra/k8s/`: `base/` (Deployment + Service + HPA
for both apps, readiness/liveness probes wired to Spring Boot's *actual*
`management.health.readinessstate`/`livenessstate` sub-paths rather than the generic
`/actuator/health`, least-privilege `securityContext` — non-root, no privilege
escalation, all capabilities dropped, read-only root filesystem), `overlays/staging`
and `overlays/production` (Kustomize — separate namespaces, staging sized down,
production matching base's 2–6 replica HPA range), plus `backend-secret.example.yaml`
(a template documenting every required key, never applied — real secrets created
out-of-band, matching "everything provider-swappable"). **No specific cloud was
picked** — no ADR has decided one (checked `architecture/02-adr-index.md` before
writing anything) — manifests target any conformant managed Kubernetes.

**Migration gating — a deliberate design choice, not an oversight:** no separate
Flyway migration Job. `spring.flyway.enabled: true` already runs migrations
synchronously during Spring context startup, before the readiness probe endpoint
exists at all; a failed migration means the pod never becomes Ready, `kubectl
rollout status` (in `.github/workflows/deploy.yml`) blocks and fails the deploy, and
`maxUnavailable: 0` keeps the previous healthy pods serving throughout. A true
separate migrate-only Job would need a real run-mode in the Spring Boot app itself —
outside this prompt's file scope. Full reasoning (including the alternative) is in
`infra/k8s/README.md`.

**CI/CD, new** — `.github/workflows/deploy.yml`: build+push both images to GHCR on
merge to `main` (using the built-in `GITHUB_TOKEN`, no extra registry secret needed),
`deploy-staging` (applies via `kustomize edit set image` + `kubectl apply -k`, waits
on `kubectl rollout status`, then runs prompt 26's `synthetic-checks.mjs` as the real
smoke test — reused, not reimplemented), `promote-production` gated behind a GitHub
Environment (`environment: production`, real manual-approval enforcement requires
repo-settings configuration this workflow file can't do on its own) that **re-tags
the exact image staging already validated** (`docker buildx imagetools create`, not
a fresh build) — "immutable images promoted across envs," literally. Every real-
cluster step (`KUBE_CONFIG_STAGING`/`KUBE_CONFIG_PRODUCTION`) checks for its secret
first and skips with a clear message if absent — no cluster exists yet, same pattern
as prompt 26's synthetic-checks workflow.

**`.github/workflows/ci.yml`'s new `docker` job** — this is the one piece of this
prompt actually verified end-to-end, not just written-and-reasoned-about: hadolint
on both Dockerfiles, `infra/k8s/validate.mjs` (structural cross-reference checks —
`envFrom`/`scaleTargetRef`/Ingress-backend/patch-target names all resolve to real
resources; caught nothing wrong on the first run, same as prompt 26's equivalent),
then **real `docker build` + `docker run` container smoke tests** — `ubuntu-latest`
runners have Docker preinstalled (unlike this dev sandbox), so this actually builds
both images, boots the API container against real Postgres/Redis service
containers, waits for its own `HEALTHCHECK` to report `healthy`, then boots the web
container *against that live API container* (`--network host`, both reachable via
`localhost`) so its smoke test is a real check against live data, not a container
that 500s on every page because nothing answers on the other end.

**Verification performed:** `apps/web`'s `next build` run locally with `output:
"standalone"` — succeeded, and its real output structure was inspected (not
assumed) before the Dockerfile was written. `npm run lint`/`typecheck`/`vitest run`
(128 tests) all clean after the `next.config.ts` change. `./gradlew
compileJava compileTestJava` clean (no backend source touched this prompt).
`infra/k8s/validate.mjs` and `infra/observability/validate.mjs` both pass.
All new/changed YAML (`ci.yml`, `deploy.yml`, `docker-compose.yml`, both
`kustomization.yaml` overlays) parsed and validated with `js-yaml`. **Not verified
here, same honest gap as every session since prompt 09:** no Docker in this dev
sandbox means `docker build`/`docker run`/`docker-compose up` have never actually
run locally — `.github/workflows/ci.yml`'s new `docker` job is what closes that loop
for real, the next time CI runs. No `kubectl`/`kustomize` CLI either, so the K8s
manifests have never gone through a real `kustomize build` or been applied to an
actual cluster — see `infra/k8s/README.md`'s "What's verified vs. not" for the full
breakdown.

## Prompt 28 (session 14) — status and deviations

Most of this prompt's own deliverables were, in fact, already built across prompts
23/24/25/26/27 — checked each explicitly against the actual workflow files before
assuming so, not just against this doc's summary of them: PR gate (lint, typecheck,
unit+integration, build images — `ci.yml`'s `backend`/`frontend`/`docker` jobs),
security scan (`codeql.yml` + `dependabot.yml`, prompt 24), coverage + a11y gates
(JaCoCo + Vitest coverage thresholds, inline `jest-axe` — prompts 09/23), image
publish + staging deploy + smoke + gated prod promotion (`deploy.yml`, prompt 27).
**What was genuinely missing, found by checking this prompt's specific wording
against what existed, not assumed present:**

- **Rollback had no actual mechanism — "documented rollback" existed only as a
  one-line aspiration** ("Rollback = redeploy previous image," this doc's own
  §CI/CD, unchanged since it was drafted). New: `.github/workflows/rollback.yml`
  (`workflow_dispatch`, two modes — `previous-revision` via `kubectl rollout undo`
  for the fast/common case, `specific-tag` for redeploying a named image when
  rolling back more than one revision). Deliberately **not** gated behind the same
  `environment: production` manual-approval as a forward deploy — triggering it at
  all already requires repo write access, and an emergency rollback should be fast,
  not queued behind the same review flow as a normal release. Runs the same
  `kubectl rollout status` gate and `synthetic-checks.mjs` confirmation as a
  forward deploy, so a rollback that itself doesn't restore health is reported as
  failed, not silently assumed to have worked.
- **"Forward-compatible DB changes for safe rollback" was a constraint with no
  actual guidance anywhere.** Belongs conceptually in
  `database/02-migrations-and-conventions.md`, but that file is outside this
  prompt's file scope (CI/CD config + `devops/*` only) — added here instead, with
  a one-line pointer left in that file rather than duplicating the content:

  *"Forward-only; never edit an applied migration" makes migrations safe to apply
  in order — it does **not** make them safe to **roll back past**, since
  `rollback.yml` redeploys old code against whatever schema is already live (no
  Flyway `down` migrations, by design). The pattern: **expand/contract** across two
  deploys. Expand deploy adds only (nullable column, new table, new index) — the
  schema stays a strict superset of what old code expects, safe to roll back past
  at any point. Only once the new code path is confirmed live does a later
  contract deploy drop/rename/tighten anything. Concretely: adding a nullable
  column, a new table, or an index is safe same-deploy; renaming/dropping a
  column or table, adding a `NOT NULL`/`CHECK` a prior version didn't satisfy, or
  an incompatible type change all need the two-deploy split. None of this repo's
  seven migrations (`V1`–`V7`) currently need it — all purely additive from a
  clean baseline — so this is guidance for the first rename/drop, not a fix to
  anything existing.*

- **Branch protection + required checks, new** —
  `infra/github/configure-branch-protection.sh`. Branch protection is a GitHub
  repo *setting*, not something GitHub reads from a file in the repo automatically
  — this script is the as-code artifact (idempotent `gh api` call), run once by a
  repo admin against a real repo. Required checks are deliberately **not** "every
  job that exists": `load-test` (`ci.yml`) is excluded on purpose — p95-latency
  thresholds on a shared, noisy CI runner are inherently noisier than functional
  correctness, and making it a hard merge-blocker risks blocking valid PRs on
  infrastructure jitter rather than a real regression (the same "actionable, not
  noisy" reasoning prompt 26 applied to alerts, applied here to a merge gate
  instead). It still runs and is visible on every PR. CodeQL's two matrix jobs
  *are* required — security scanning is a correctness gate, not a performance one.
- **"Pipeline dry-run," new** — no `act` (+ Docker) here to actually run GitHub
  Actions locally, so `infra/github/validate.mjs` is the structural proxy: every
  `needs:` reference in every workflow points at a job that actually exists (a
  real, easy-to-typo class of bug GitHub only catches by failing an entire run),
  and every `secrets.*`/`vars.*` reference across all five workflow files matches
  `infra/github/required-secrets-and-vars.md` in both directions — nothing used-
  but-undocumented, nothing documented-but-unused. Passed cleanly on first run (2
  secrets, 5 vars, all matched). Wired into `ci.yml`'s new `pipeline-validation`
  job, which also now hosts prompt 26's and 27's equivalent validators (moved
  there from the `backend`/`docker` jobs they'd been living in — one place for
  "checks about our own infra-as-code," not scattered wherever there happened to
  be a Node install already).

**"Rollback drill"** — the required test this prompt names — could not be run
against a real cluster (none exists). What was verified instead: `rollback.yml`'s
YAML structure and `needs:`/secrets references pass `infra/github/validate.mjs`
alongside every other workflow, and its logic was traced by hand against
`deploy.yml`'s own (already-reasoned-through, prompt 27) gating pattern rather than
written fresh. A real drill — trigger `rollback.yml` against a live staging
cluster, confirm `synthetic-checks.mjs` goes from failing to passing — is the
actual verification once one exists.

**Verification performed:** all five workflow files (`ci.yml`, `deploy.yml`,
`rollback.yml`, `codeql.yml`, `synthetic-checks.yml`) plus `dependabot.yml` and
`docker-compose.yml` parsed and validated with `js-yaml`. `infra/github/validate.mjs`,
`infra/k8s/validate.mjs`, and `infra/observability/validate.mjs` all pass after the
`ci.yml` reorganization. `bash -n` on `configure-branch-protection.sh`. No backend
or frontend source touched this prompt — nothing to re-run there beyond confirming
the existing test suites are unaffected (they are; no source changed).

## Prompt 29 (session 15) — production-readiness review: NO-GO

Full checklist, evidence per item, and the decision rationale now live in
`03-production-readiness.md` — not duplicated here. Short version: 13 items
passed, 2 real gaps were formally risk-accepted (with owner + rationale, per this
prompt's own acceptance criteria), and **15 items are genuinely blocking** —
including three entire feature areas that were never built (host dashboard,
messaging, admin — roadmap Phases 8–10, skipped), a payment integration that has
never processed one real transaction, a real PII-exposure bug this review's own
audit found while writing the privacy inventory (`PropertyDetail`'s public
response leaks every listing's exact address pre-booking), and brand/legal
clearance that has been marked "pending" since this project's Phase 0 and is
still pending. **Decision: NO-GO for public launch**, with a specific, ordered
path to GO. New this prompt: `infra/backups/` (backup/restore scripts, real but
never run against a live Postgres), `runbooks/01-incident-response.md`,
`runbooks/02-disaster-recovery.md`, `runbooks/03-data-retention-and-privacy.md`,
and `security/01-security-plan.md`'s least-privilege DB role procedure (closing
the gap session 10 deferred to "prompts 27/29" — this is that closure, with one
honestly-stated remaining limitation).
