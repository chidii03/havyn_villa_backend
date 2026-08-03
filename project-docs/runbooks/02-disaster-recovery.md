# Disaster Recovery Plan

> Status: Draft · Prompt 29. RTO/RPO below are **targets**, not measurements — no
> real infrastructure exists to measure against yet (see `devops/
> 03-production-readiness.md`'s go/no-go record). Revise from targets to
> measurements the first time `infra/backups/README.md`'s restore drill actually
> runs.

## Scope

What this covers: total loss of the database, total loss of the API/web
deployment, and loss of a single availability zone/region (if the chosen managed
Postgres/Kubernetes provider spans multiple). What this does **not** cover: a
managed provider's own total outage (Postgres provider, Kubernetes provider, DNS,
GHCR) — those are accepted third-party risk, mitigated only by provider choice
(uptime SLA) and provider-swappability (`architecture/01-system-architecture.md#8`
already designs for this at the *code* level; actually re-platforming to a
different provider mid-incident is a days-not-hours operation, not a DR failover).

## RTO / RPO targets

| Failure | RPO target | RTO target | Mechanism |
|---|---|---|---|
| Database data loss/corruption | ≤ 5 minutes | ≤ 1 hour | Managed provider PITR — restore-to-timestamp just before corruption (`infra/backups/README.md`) |
| Database instance loss (hardware/AZ) | Near-zero | ≤ 15 minutes | Managed provider's own multi-AZ failover (a provider *feature to select*, not something this repo implements) |
| API/web deployment loss | N/A (stateless) | ≤ 10 minutes | `kubectl rollout` / re-apply Kustomize manifests (`infra/k8s/`) — stateless replicas, no data to lose |
| Bad deploy (not infra failure) | N/A | ≤ 5 minutes | `.github/workflows/rollback.yml` (prompt 28) |

**These are targets set from reasoning about the architecture (managed Postgres
PITR windows are typically minute-granularity; a stateless K8s redeploy is
typically minutes), not from a measured drill.** The one real number available —
`infra/backups/README.md`'s restore drill, once actually run — replaces the
database-row here with an observed time, not an estimate.

## Failure scenarios & procedures

### Database data loss or corruption
1. Identify the last-known-good timestamp (from monitoring/logs — the
   `HighHttpServerErrorRate`/data-integrity signal that revealed the corruption).
2. Managed provider console/API: restore-to-timestamp (PITR) into a **new**
   instance — never restore over the live one.
3. Point a staging/scratch instance of the API at the restored instance, run
   `infra/backups/README.md`'s verification steps (health check, row-count
   spot-check, `flyway_schema_history` intact).
4. Only after verification: cut the real `POSTGRES_HOST` secret over to the
   restored instance (`kubectl` secret update + rolling restart, or re-apply via
   `infra/k8s/overlays/<env>` with the secret updated out-of-band — see
   `infra/k8s/README.md`'s secrets section).
5. If the managed provider's PITR is somehow unavailable: fall back to the most
   recent `infra/backups/backup.sh` snapshot — this is the "portable, provider-
   independent" reason that script exists (its own README explains this in full).

### Full API/web deployment loss
Stateless — there is no data to lose here, just capacity. `kubectl apply -k
infra/k8s/overlays/<env>` re-applies the full desired state from source control;
`kubectl rollout status` (already how `deploy.yml`/`rollback.yml` gate a normal
deploy — same command, same meaning here) confirms it's actually healthy again,
not just "applied."

### Bad deploy (self-inflicted, not an infra failure)
This is what `.github/workflows/rollback.yml` (prompt 28) is *for* — see that
workflow and `project-docs/runbooks/01-incident-response.md`'s "tied to a recent
deploy" guidance. Not a separate DR procedure; the fastest path back is always
"undo the last change," not "restore from backup."

## What's been drilled vs. what hasn't

**Not drilled, at all, against real infrastructure** — no staging/production
cluster or managed Postgres instance exists yet (prompts 27/28's infra was built
correctly but never applied to a real cluster; see `infra/k8s/README.md`'s "What's
verified vs. not"). This entire document is a *plan* someone can execute, written
against this project's actual architecture and actual scripts/workflows (every
cross-reference above points at something real and current, not aspirational) —
not a report of a drill that happened. `devops/03-production-readiness.md`'s
go/no-go record treats "DR plan drilled" as an open item, not a closed one.
