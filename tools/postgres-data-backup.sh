#!/usr/bin/env bash
# Data-only dump/restore of local Foshol Postgres (bind-mounted ./.data/postgres).
# Schema and flyway_schema_history are owned by Flyway; this script never dumps them.
#
#   ./tools/postgres-data-backup.sh dump
#   ./tools/postgres-data-backup.sh restore /path/to/postgres-data-YYYYMMDD-HHMMSS.sql
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

BACKUP_DIR="${FOSHOL_PG_BACKUP_DIR:-$ROOT/.data/backups}"

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing required command: $1" >&2
    exit 1
  }
}

need docker

if ! docker compose exec -T postgres pg_isready -U foshol -d foshol >/dev/null 2>&1; then
  echo "postgres is not ready; start it first" >&2
  exit 1
fi

dump() {
  mkdir -p "$BACKUP_DIR"
  umask 077
  local dest
  dest="${BACKUP_DIR}/postgres-data-$(date +%Y%m%d-%H%M%S).sql"
  echo "dumping data-only backup to ${dest}" >&2
  {
    echo "-- Foshol data-only backup; flyway_schema_history excluded"
    echo "-- created $(date '+%Y-%m-%dT%H:%M:%S%z')"
    docker compose exec -T postgres pg_dump -U foshol -d foshol \
      --data-only --no-owner --disable-triggers --exclude-table=flyway_schema_history
  } >"$dest"
  if [[ ! -s "$dest" ]]; then
    echo "dump produced an empty file: ${dest}" >&2
    exit 1
  fi
  echo "$dest"
}

restore() {
  local src="${1:-}"
  if [[ -z "$src" || ! -f "$src" ]]; then
    echo "usage: $0 restore <dump.sql>" >&2
    exit 1
  fi
  echo "restoring data from ${src} (truncating public tables except flyway_schema_history)" >&2
  {
    cat <<'SQL'
SET session_replication_role = replica;
DO $$
DECLARE
  r record;
BEGIN
  FOR r IN
    SELECT tablename
    FROM pg_tables
    WHERE schemaname = 'public'
      AND tablename <> 'flyway_schema_history'
  LOOP
    EXECUTE format('TRUNCATE TABLE %I.%I CASCADE', 'public', r.tablename);
  END LOOP;
END
$$;
SQL
    cat "$src"
    echo "SET session_replication_role = DEFAULT;"
  } | docker compose exec -T postgres psql -U foshol -d foshol -v ON_ERROR_STOP=1
  echo "restore complete" >&2
}

case "${1:-}" in
  dump) dump ;;
  restore)
    shift
    restore "${1:-}"
    ;;
  *)
    echo "usage: $0 dump | restore <dump.sql>" >&2
    exit 1
    ;;
esac
