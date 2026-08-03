#!/usr/bin/env bash
# Restores a backup.sh dump into a database you name EXPLICITLY (see README.md's
# restore drill procedure) — deliberately has no default target, so an omitted
# argument fails loudly instead of silently restoring over whatever POSTGRES_DB
# happens to be set to.
#
# Required env: POSTGRES_HOST, POSTGRES_PORT, POSTGRES_USER, PGPASSWORD.
# Usage: ./restore.sh <path-to-.dump-file> <target-database-name>
set -euo pipefail

: "${POSTGRES_HOST:?}"
: "${POSTGRES_PORT:=5432}"
: "${POSTGRES_USER:?}"
: "${PGPASSWORD:?}"

DUMP_FILE="${1:?Usage: $0 <path-to-.dump-file> <target-database-name>}"
TARGET_DB="${2:?Usage: $0 <path-to-.dump-file> <target-database-name>}"

if [ ! -f "$DUMP_FILE" ]; then
  echo "No such file: $DUMP_FILE"
  exit 1
fi

echo "About to restore $DUMP_FILE into database '$TARGET_DB' on $POSTGRES_HOST:$POSTGRES_PORT."
echo "This target database must already exist (create it first: createdb -h \$POSTGRES_HOST -U \$POSTGRES_USER $TARGET_DB) — this script does not create databases."
read -r -p "Continue? [y/N] " CONFIRM
if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
  echo "Aborted."
  exit 1
fi

pg_restore \
  --host="$POSTGRES_HOST" \
  --port="$POSTGRES_PORT" \
  --username="$POSTGRES_USER" \
  --dbname="$TARGET_DB" \
  --clean --if-exists \
  --no-owner --no-privileges \
  "$DUMP_FILE"

echo "Restore complete. Now verify per README.md's restore drill step 4 — don't assume success from exit code alone."
