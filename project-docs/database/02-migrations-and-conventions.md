# Migrations & DB Conventions

> Status: Draft · Phase 2. Tooling: **Flyway** (NOT Prisma — Prisma is only for the alternative Node stack and is not used here).

## Conventions
- Migrations in `src/main/resources/db/migration`, named `V<version>__<description>.sql` (e.g., `V1__init.sql`, `V2__add_booking_exclusion.sql`).
- Forward-only; never edit an applied migration — add a new one. Repeatable migrations `R__` for views/seed-of-reference-data only.
- Snake_case table/column names; plural tables; `id uuid default gen_random_uuid()`.
- Every table: `created_at timestamptz not null default now()`, `updated_at` maintained by trigger or app.
- Reference/enum data (roles, property types, amenities) seeded via migration; **no fake production/business data**.
- Money = `numeric(12,2)`; enums via Postgres `CREATE TYPE` or check-constrained varchar (document choice).
- All FKs indexed; add composite indexes for search & booking-overlap queries.

## Booking overlap migration (sketch)
```sql
ALTER TABLE booking ADD CONSTRAINT no_overlap
  EXCLUDE USING gist (
    property_id WITH =,
    daterange(check_in, check_out, '[]') WITH &&
  ) WHERE (status IN ('pending','confirmed','completed'));
-- requires: CREATE EXTENSION IF NOT EXISTS btree_gist;
```

## Environments
Flyway runs on app start in staging/prod (or as a deploy step); local via docker-compose. Migration status exposed through Actuator. Baseline strategy documented for existing DBs.

Rollback-safety guidance for migrations (expand/contract) lives in
`devops/01-devops-and-deployment.md`'s prompt 28 notes, alongside the rest of this
project's rollback documentation — not duplicated here.
