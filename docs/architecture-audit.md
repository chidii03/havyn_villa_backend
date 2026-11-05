# HavynVilla architecture audit

**Phase:** 1 — audit only  
**Audit date:** 30 October 2026  
**Scope:** the sibling `my-app`, `web`, and `mobile` repositories in the supplied workspace. No runtime, application, or infrastructure configuration was changed during this phase.

## Executive summary

The workspace contains a functional-looking Spring Boot API in `my-app/apps/api` and a separate Next.js application in `web`. It is **not currently a single reproducible deployment repository**: the API/infra and web application are independent Git repositories, and the frontend Dockerfile is written for a monorepo layout that does not exist in either repository. The DevOps repository has useful API-focused Docker, Compose, Kubernetes, GitHub Actions, and monitoring-rule artifacts, but it has no Jenkinsfile, no frontend deployment, no Prometheus or Grafana deployment/provisioning, and no confirmed cluster deployment.

The recommended next phase is to establish the intended source/deployment boundary and make the existing API and web repositories build together without changing business APIs. Do not use any current placeholder Kubernetes or registry values for a deployment.

## Current architecture

```text
Browser
  -> Next.js 16.2.11 application (`web`, Vercel configuration present)
  -> Spring Boot 3.5.3 API (`my-app/apps/api`, port 8080)
       -> PostgreSQL via Spring Data JPA/Hibernate; Flyway owns schema changes
       -> Redis for refresh-token/session state and a best-effort search cache
       -> Paystack hosted checkout and signed webhook endpoint
       -> RayProp, Cloudinary, Google Identity Services, SMTP, OpenAI (optional integrations)

API infrastructure currently tracked in `my-app/infra`:
Docker Compose -> PostgreSQL, Redis, MinIO, MailHog, API
Kubernetes -> API-only Deployment/Service/HPA/Ingress templates
Observability -> alert rules, dashboard JSON, synthetic and structural validators
```

## Repository and frontend audit

| Item | Finding |
| --- | --- |
| Frontend location | `web`, a separate Git repository, not a child of `my-app`. |
| Framework/runtime | Next.js 16.2.11, React 19.2.4, TypeScript; npm lockfile and npm scripts are present. |
| Scripts | `dev`, `build`, `start`, `lint`, `typecheck`, Vitest tests/coverage, and Playwright E2E. |
| Required browser configuration | `NEXT_PUBLIC_API_BASE_URL`; README also identifies `NEXT_PUBLIC_GOOGLE_CLIENT_ID`. The API client defaults to `https://localhost:8080`, which is unsuitable for ordinary local HTTP development if the variable is omitted. |
| Deployment configuration | `vercel.json` builds with `npm install`, rather than lockfile-reproducible `npm ci`. `next.config.mjs` enables standalone output. |
| Dockerfile | Runs non-root and has a multi-stage design, but copies `apps/web` and `packages/shared` from a root npm workspace. The actual `web` repository instead contains `src`, `packages/shared`, and its own `package.json`; no `apps/web` exists. This Dockerfile cannot be built with `web` as its declared context. |
| Tests | Vitest and Playwright configuration exists. The Phase 1 environment did not return a completed typecheck result, so no frontend test/build is claimed as verified. |

## Backend, data, and integrations audit

| Item | Finding |
| --- | --- |
| Backend location | `my-app/apps/api`, a Gradle-wrapper Spring Boot modular monolith. |
| Java/build | `build.gradle` requires Java 21 and Spring Boot 3.5.3. The README says Java 24, which conflicts with the build configuration. The local host has Java 24.0.1. |
| API configuration | `application.yml` supplies env-driven PostgreSQL, Redis, mail, JWT, Paystack, RayProp, Google, OpenAI, media, and server settings. No Spring profile files were found. |
| PostgreSQL | PostgreSQL is the system of record. Spring Data JPA/Hibernate uses `ddl-auto: validate`; Flyway migrations `V1`–`V16` under `src/main/resources/db/migration` own the schema. Hikari has finite pool and five-second connection timeout configuration. |
| Redis | Spring Data Redis backs refresh-token/session state and `SearchCacheService`. Search caching is 60-second, generation-key invalidated, and catches `DataAccessException` for reads/writes/invalidation. This is appropriately best-effort for search, but authentication remains dependent on Redis. Redis health is disabled by default. |
| Payments | `POST /api/v1/payments/intent` creates a Paystack hosted-checkout intent for a pending booking. `POST /api/v1/payments/webhook/paystack` accepts raw payloads, validates HMAC-SHA512 using the Paystack secret, persists a transaction, and treats terminal-payment repeats as idempotent. Booking confirmation is webhook-led rather than redirect-led. Unit tests for the provider and payment flow are present. No live Paystack request was performed or verified. |
| Health/metrics/logging | Actuator exposes `health`, `info`, and `prometheus`; liveness/readiness probes are enabled. Micrometer Prometheus, tracing, structured Logstash encoder, correlation ID, and domain metrics are configured. Health endpoints are public; Prometheus is not in the public security allowlist. |
| Tests | Unit and Docker-backed integration test tasks exist. Integration tests use Testcontainers and are excluded from ordinary `test`. JaCoCo gates selected packages. |

## Existing infrastructure audit

| Area | Reusable components | Gaps or risks |
| --- | --- | --- |
| API Docker image | Java 21 multi-stage build, non-root runtime, health check. | No explicit JVM container memory options; Docker build has not been executed in this audit. |
| Docker Compose | PostgreSQL 16, Redis 7, MailHog, API health checks, named PostgreSQL volume. Redis is deliberately ephemeral. | API-only: no frontend service. It also includes MinIO although no corresponding storage integration was confirmed. `minio/minio:latest` and `mailhog/mailhog:latest` are mutable image tags. Default development passwords are embedded as Compose fallbacks. No tracked `.env.example` was found despite README references to one. |
| Kubernetes | API Deployment, Service, HPA, ConfigMap, example Secret, staged/production Kustomize overlays, readiness/liveness probes, resource requests/limits, non-root/read-only filesystem. | API-only; no web workload/service, PostgreSQL/Redis design, namespace manifests, PDB, quotas, or NetworkPolicy. Ingress hosts, TLS issuer, registry owner, and image tags are placeholders. The base `ConfigMap` omits several required non-secret settings. Cluster deployment is unverified. |
| CI/CD | GitHub Actions validates artifacts, tests the API, builds the API image, publishes images, and has optional deployment/rollback workflows. Immutable SHA tags are available. | This does **not** satisfy the requested Jenkins CI/CD deliverable: no `Jenkinsfile` exists. It does not build/test/publish the frontend. The publish workflow also emits mutable `latest`; the learning deploy uses a download from `releases/latest` and lacks a smoke-test step. Deployment requires a configured kubeconfig secret and has not been demonstrated. |
| Monitoring | Prometheus alert rules, Grafana dashboard JSON, metric-name validator, and optional synthetic checks exist. | There is no Prometheus scrape configuration/deployment, Grafana datasource/dashboard provisioning, Alertmanager, or evidence of a successful scrape/dashboard/alert. These assets are not an operating monitoring stack. |

## Security and operational findings

1. **High — insecure default JWT secrets:** `application.yml` supplies non-empty JWT defaults. A deployment missing secret injection can start with predictable credentials. Replace these with required environment values in a later, tested change.
2. **High — local secret exposure must be investigated outside this report:** untracked/ignored `.env` files exist in `my-app`, `apps/api`, and `web`. They were intentionally not read or copied during the audit. Confirm they contain no committed or leaked credentials before sharing the workspace.
3. **High — deployment topology is incomplete:** API and web source trees are separate while the frontend image assumes a different root layout. Current Compose/Kubernetes/CI paths do not deploy the browser application.
4. **Medium — observability is unverified:** metrics code and alert/dashboard assets exist, but no Prometheus/Grafana service is configured to use them.
5. **Medium — configuration/documentation drift:** the backend README specifies Java 24 while Gradle targets Java 21; multiple README paths refer to a prior monorepo layout; `.env.example` is documented but absent.
6. **Medium — raw webhook payload retention:** payment transactions persist the raw webhook body. Review retention and access controls because provider payloads can contain payment/customer metadata.
7. **Medium — Git safety state cannot be assessed in this sandbox:** Git refuses status/list-files because the repositories are owned by a different Windows SID (`detected dubious ownership`). No Git configuration was changed. This prevents a reliable report of pre-existing versus new uncommitted changes.
8. **Low — Compose image provenance:** MinIO and MailHog use `latest`, contrary to reproducibility expectations.

## Commands and evidence

| Command/activity | Result |
| --- | --- |
| Repository/configuration/source audit with `rg`, file inspection, and manifest inspection | Completed. Findings above are source-level observations. |
| `java -version` | Java 24.0.1 is installed. |
| `node --version` | Node 24.5.0 is installed. |
| `npm --version` | Blocked because this host's PowerShell execution policy blocks `npm.ps1`; use `npm.cmd` or adjust the shell policy in a later validation phase. |
| `apps/api/gradlew.bat --version` | Blocked before Gradle started: wrapper needed Gradle 9.5.1 and could not download it because sandbox network access was denied. No backend build or test result is claimed. |
| `my-app/infra/k8s`: `node validate.mjs` | Passed: five base resources and both overlays parsed; all structural cross-references checked out. This is not a server-side Kubernetes validation or deployment. |
| `my-app/infra/observability`: `node validate.mjs` | Passed: `alerts.yml` (four groups/six rules), `dashboard.json` (13 panels), and six referenced custom metric names matched API source. This is not a Prometheus scrape or Grafana runtime test. |
| `web`: `npm.cmd run typecheck` | TypeScript typecheck was started using the Windows command shim after the PowerShell policy issue. The session returned only the command start and no reliable final exit status, so no pass/fail outcome is claimed. |
| Docker, Compose, Kubernetes deployment, Paystack live calls, Prometheus scrape, Grafana rendering | Not executed. |

## Recommended implementation sequence

1. Decide and document the deployment boundary: either make `my-app` the true monorepo by bringing the web source under its controlled layout, or keep separate repositories and give each an independently valid Dockerfile, CI pipeline, release contract, and image reference. Preserve the existing API contract.
2. Add safe `.env.example` files, remove predictable runtime-secret defaults, reconcile Java 21/24 documentation, pin Compose image tags, and document actual local development commands.
3. Repair and validate the frontend image; extend Compose to include it only after its build context is correct. Verify PostgreSQL, Redis, API readiness, and web-to-API communication using isolated development settings.
4. Add a Jenkinsfile matching the decided topology. Keep GitHub Actions only if it remains intentional; do not claim it as Jenkins. Require tests, immutable images, manifest validation, and a gated test-environment deployment.
5. Expand Kubernetes manifests for the frontend and explicitly choose external versus demonstration PostgreSQL/Redis. Replace placeholders only through environment-specific secret/config management.
6. Add reproducible Prometheus and Grafana provisioning, then prove a scrape, dashboard data, alerts, smoke tests, and controlled recovery before documenting them as complete.

## Phase 1 hand-off

**Created:** this audit only (`docs/architecture-audit.md`).  
**Implemented:** no application or infrastructure behavior changes.  
**Outstanding blockers:** source/deployment topology decision; Gradle distribution/network availability; PowerShell npm execution policy; Git safe-directory ownership restriction; no designated Jenkins instance, registry, or Kubernetes test cluster.  
**Next step:** review this audit and authorize Phase 2 only after choosing whether the DevOps project is a monorepo or a multi-repository deployment.
