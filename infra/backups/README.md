# Backups & PITR (prompt 29)

> **Status: strategy documented, scripts written, restore NOT drilled against a
> real database.** No Postgres/Docker exists in the dev sandbox this was written
> in — see "What's not verified" below. This is the single most important open
> item in `project-docs/devops/03-production-readiness.md`'s go/no-go record.

## Strategy: managed-provider PITR is primary, this is a portable supplement

`architecture/01-system-architecture.md#8` and `devops/01-devops-and-deployment.md`
both already target "managed Postgres (with read replica option) + backups/PITR" —
this project deliberately does not run its own Postgres server (`infra/docker-
compose.yml`'s `postgres:16-alpine` service is for **local dev only**; production
points at a managed provider via `POSTGRES_HOST`/credentials, same as every other
env-driven config in this app).

Every mainstream managed Postgres offering (RDS, Cloud SQL, Azure Database for
PostgreSQL, Neon, Supabase, etc.) provides continuous WAL-archiving PITR as a
built-in feature — typically restore-to-any-second within a retention window,
automated, and already tested by the provider at a scale this project could never
replicate itself. **Hand-rolling WAL archiving would be worse than what any managed
provider already offers**, and would work against
`architecture/01-system-architecture.md#8`'s explicit "everything provider-
swappable" stance by coupling this project to one provider's WAL format/tooling.
So: **PITR is a managed-provider configuration choice** (enable it, set the
retention window — typically 7–35 days depending on provider/plan), not something
built in this repo.

## What *is* built here: a portable logical backup, for two real reasons

1. A `pg_dump` snapshot restores into *any* Postgres, including a different
   provider — useful for provider migration, and as a sanity-check independent of
   the managed provider's own backup system (checks "can we actually get our data
   out and back in," not just "does the provider's dashboard say backups are
   enabled").
2. Something concrete to actually rehearse a restore against locally/in CI, where
   a full managed-provider PITR restore isn't something you can (or should)
   casually drill.

- **`backup.sh`** — `pg_dump` in custom format (`-Fc`, supports selective/parallel
  restore, smaller than plain SQL), timestamped, optionally uploaded to S3-
  compatible object storage if `BACKUP_UPLOAD_URI` is set (works with AWS S3, or
  MinIO — the same provider `infra/docker-compose.yml` already runs for local
  media dev — via `aws s3 cp`'s endpoint-url override; left generic rather than
  picking one cloud, same reasoning as everywhere else in this project).
- **`restore.sh`** — `pg_restore` wrapper, `--clean --if-exists` (safe to run
  against a database that already has the schema) into a **target database you
  name explicitly** — never defaults to overwriting whatever `POSTGRES_DB` points
  at, specifically to make an accidental production-target restore hard to do by
  fat-fingering a missing argument.

## Restore drill procedure (run this for real once staging exists)

1. `./backup.sh` against a real database with real (test) data in it.
2. Provision a **fresh**, empty Postgres instance/database — never restore over
   the source you just backed up.
3. `./restore.sh <the backup file> <fresh database name>`.
4. Point a throwaway instance of the API at the restored database
   (`POSTGRES_DB`/`POSTGRES_HOST` overrides — no code change needed) and confirm:
   `/actuator/health` reports `UP`, a few real rows spot-checked (row counts per
   table match the source), `flyway_schema_history` is intact (Flyway won't try to
   re-run migrations that already applied).
5. Record the actual time taken end-to-end — this becomes the real RPO/RTO input
   for `project-docs/runbooks/02-disaster-recovery.md`, replacing that doc's
   current *targets* (not yet measurements).

## What's not verified

No Postgres in this dev sandbox (same gap as every Testcontainers IT since prompt
09) means `backup.sh`/`restore.sh` have been written and manually reviewed
line-by-line against real `pg_dump`/`pg_restore` flag semantics, but **never
actually run**. This is flagged as the top open item in the production-readiness
go/no-go record, not silently assumed to work.
