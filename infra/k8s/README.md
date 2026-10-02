# Kubernetes manifests (prompt 27)

Generic manifests for "managed k8s or equivalent"
(`architecture/01-system-architecture.md#8`) — no ADR has picked a specific cloud
(`architecture/02-adr-index.md`), so nothing here is GKE/EKS/AKS-specific beyond the
Ingress using nginx-ingress-controller annotations (swap for your controller).

## Layout

```
base/        Deployment/Service/HPA/ConfigMap/Ingress for havyn-api,
             plus backend-secret.example.yaml (a template — never applied as-is)
overlays/
  staging/     namespace havyn-staging, 1 replica, staging.CHANGEME.example.com
  production/  namespace havyn-production, base's 2-6 replica HPA range, higher
               per-pod resources, CHANGEME.example.com
```

`CHANGEME.example.com` appears throughout deliberately — no real domain exists yet.
`ghcr.io/OWNER/...` image refs are placeholders too — this repo has no git remote
configured yet (confirmed via `git remote -v`), so there's no real registry path to
put here without fabricating one.

## Why there's no separate migration Job

The devops doc and this prompt both call for "migrations gated" on deploy.
Two ways to get that with Flyway:

1. **A dedicated pre-deploy migration Job** (a separate Job resource, same image,
   running migrations only, exiting when done, blocking the Deployment rollout
   until it succeeds). This is the more explicit, heavier-weight pattern.
2. **Rely on Flyway's own startup-time migration + Kubernetes readiness probing.**
   `spring.flyway.enabled: true` (application.yml, already set since prompt 08)
   means every pod runs its migrations synchronously during Spring context
   startup, *before* the embedded web server (and therefore the readiness probe's
   `/actuator/health/readiness` endpoint) comes up at all. Flyway's own locking
   (`flyway_schema_history` table lock) makes concurrent-replica-startup safe — a
   well-established, documented Flyway behavior, not an assumption specific to
   this app. If migration fails, the pod's Spring context never finishes starting,
   it never becomes Ready, `kubectl rollout status` (used by
   `.github/workflows/deploy.yml`) blocks and eventually fails the deploy, and —
   because `maxUnavailable: 0` on both Deployments — the *previous*, still-healthy
   pods keep serving traffic the entire time. That's real gating, achieved for
   free from infra config alone.

This repo uses **option 2**. Option 1 would need a genuine migrate-only run mode in
the Spring Boot app itself (e.g. a dedicated profile that runs Flyway then calls
`SpringApplication.exit()`) — that's an `apps/api/src/main/java` change, outside
this prompt's file scope (Dockerfiles/compose/deploy manifests only). Flagged here
as a legitimate follow-up for whoever next has backend file scope, not silently
decided against.

## Secrets

`base/backend-secret.example.yaml` documents every key `havyn-api-secrets` needs —
it is a template, not something `kustomize build` includes (see
`base/kustomization.yaml`'s own comment) or that should ever be `kubectl apply`'d
as-is. Real secrets are created out-of-band per environment/namespace, via
whichever secrets-manager integration the actual cluster uses (External Secrets
Operator, Sealed Secrets, a cloud provider's native secret sync, or plain
`kubectl create secret` for a first manual setup) — deliberately not prescribed
here, matching `architecture/01-system-architecture.md#8`'s "everything
provider-swappable."

## What's verified vs. not

No `kubectl`/`kustomize` CLI in this dev sandbox (same category of gap as no
Docker — see `testing/01-testing-strategy.md`'s session 9 notes for the fuller
pattern this project has followed since). Every manifest here was written and
manually cross-checked (probe paths against `application.yml`'s real Actuator
config, `envFrom`/`scaleTargetRef` names against the actual resource names they
reference, etc.) — `validate.mjs` in this directory automates the structural half
of that (well-formed YAML, required fields present, name cross-references match)
and runs in `.github/workflows/ci.yml`. **Never run through a real
`kustomize build` or applied to a real cluster** — that's the part this sandbox
genuinely cannot do, same honesty as every other "not verified end-to-end" note in
this project.
