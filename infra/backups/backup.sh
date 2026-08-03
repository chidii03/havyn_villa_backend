#!/usr/bin/env bash
# Logical backup (pg_dump, custom format) — see README.md for why this exists
# alongside (not instead of) the managed Postgres provider's own PITR.
#
# Required env: POSTGRES_HOST, POSTGRES_PORT, POSTGRES_DB, POSTGRES_USER,
# PGPASSWORD (pg_dump's own expected var name — not POSTGRES_PASSWORD, deliberately
# not reusing the app's own env var name here so this script's requirements are
# self-evident from `env | grep PG` rather than needing this comment memorized).
# Optional: BACKUP_UPLOAD_URI (e.g. s3://my-bucket/havyn-villa/, or unset to only
# write locally), BACKUP_DIR (default ./backups).
set -euo pipefail

: "${POSTGRES_HOST:?}"
: "${POSTGRES_PORT:=5432}"
: "${POSTGRES_DB:?}"
: "${POSTGRES_USER:?}"
: "${PGPASSWORD:?}"

BACKUP_DIR="${BACKUP_DIR:-./backups}"
mkdir -p "$BACKUP_DIR"

TIMESTAMP=$(date -u +%Y%m%dT%H%M%SZ)
FILE="$BACKUP_DIR/havyn-villa-${POSTGRES_DB}-${TIMESTAMP}.dump"

echo "Backing up $POSTGRES_DB@$POSTGRES_HOST:$POSTGRES_PORT -> $FILE"
pg_dump \
  --host="$POSTGRES_HOST" \
  --port="$POSTGRES_PORT" \
  --username="$POSTGRES_USER" \
  --dbname="$POSTGRES_DB" \
  --format=custom \
  --no-owner \
  --no-privileges \
  --file="$FILE"

echo "Backup written: $FILE ($(du -h "$FILE" | cut -f1))"

if [ -n "${BACKUP_UPLOAD_URI:-}" ]; then
  echo "Uploading to $BACKUP_UPLOAD_URI..."
  aws s3 cp "$FILE" "${BACKUP_UPLOAD_URI%/}/$(basename "$FILE")" ${BACKUP_S3_ENDPOINT:+--endpoint-url "$BACKUP_S3_ENDPOINT"}
  echo "Uploaded."
fi
