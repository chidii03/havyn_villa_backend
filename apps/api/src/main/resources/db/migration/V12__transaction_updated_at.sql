-- V12__transaction_updated_at.sql
-- Real bug, caught for the first time against a real Postgres (session 20): `Transaction`
-- (payments/domain/Transaction.java) extends BaseEntity, which unconditionally maps both
-- created_at AND updated_at — but V6__payments.sql's `CREATE TABLE transaction` only
-- defined created_at (transactions were reasonably assumed immutable, but BaseEntity's
-- mapping doesn't know that). Every other BaseEntity-backed table across V1-V11 was
-- audited and already has updated_at correctly; transaction was the one outlier.
-- Never caught until now because no Docker has existed in this project's dev sandbox
-- across every prior session, so Hibernate schema validation never ran against a real
-- database — Flyway's own migration checksums were never wrong, this is a genuine schema
-- gap, not a Flyway/checksum issue.

ALTER TABLE transaction ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
