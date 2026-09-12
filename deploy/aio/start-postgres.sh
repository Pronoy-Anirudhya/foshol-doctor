#!/usr/bin/env bash
# Loopback only: nothing outside the container talks to Postgres. Publish 5432 and
# change listen_addresses only for debugging.
set -euo pipefail
exec /usr/lib/postgresql/17/bin/postgres -D "${PGDATA}" \
  -c listen_addresses=127.0.0.1 \
  -c port=5432 \
  -c unix_socket_directories=/var/run/postgresql \
  -c shared_buffers="${FOSHOL_PG_SHARED_BUFFERS:-256MB}" \
  -c max_connections="${FOSHOL_PG_MAX_CONNECTIONS:-100}" \
  -c logging_collector=off \
  -c log_timezone=UTC \
  -c log_line_prefix='[postgres] %m [%p] '
