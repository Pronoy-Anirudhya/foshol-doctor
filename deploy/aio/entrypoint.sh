#!/usr/bin/env bash
# PID 1 pre-flight, then hand over to supervisord. Everything here is idempotent and
# runs on every boot.
set -euo pipefail

STATE="${FOSHOL_STATE_DIR:-/var/lib/foshol}"
PGDATA="${PGDATA:-$STATE/pgdata}"
MINIO_DIR="$STATE/minio"
SECRETS_DIR="$STATE/secrets"
SECRETS="$SECRETS_DIR/secrets.env"
DEMO_LOCK="$STATE/.demo-logins"
PGBIN=/usr/lib/postgresql/17/bin

log() { printf '[entrypoint] %s\n' "$*"; }
die() { printf '[entrypoint] FATAL %s\n' "$*" >&2; exit 1; }

# --- 0. layout -----------------------------------------------------------
install -d -o postgres -g postgres -m 0700 "$PGDATA"
install -d -o foshol   -g foshol   -m 0750 "$MINIO_DIR"
install -d -o root     -g root     -m 0700 "$SECRETS_DIR"
install -d -o postgres -g postgres -m 0775 /var/run/postgresql

if ! findmnt -rn --target "$STATE" >/dev/null 2>&1 || \
   [ "$(findmnt -rno TARGET --target "$STATE" 2>/dev/null)" = "/" ]; then
  log "WARNING $STATE is not a mounted volume. The database, the object store and the"
  log "WARNING generated secrets will be destroyed with this container."
  log "WARNING Mount one volume to persist all three: -v foshol-state:$STATE"
fi

# --- 1. refuse a PGDATA from another major version ----------------------
if [ -s "$PGDATA/PG_VERSION" ]; then
  have="$(cat "$PGDATA/PG_VERSION")"
  [ "$have" = "17" ] || die "PGDATA was initialised by Postgres $have; this image ships 17. \
Use an image with Postgres $have, or restore into a fresh volume with pg_dump/pg_restore."
fi

# --- 2. secrets: mint once, persist, reuse ------------------------------
gen_b64_32() { python3 -c 'import base64,secrets;print(base64.b64encode(secrets.token_bytes(32)).decode())'; }
gen_hex()    { python3 -c 'import secrets,sys;print(secrets.token_hex(int(sys.argv[1])))' "$1"; }

if [ -f "$SECRETS" ]; then
  log "reusing secrets from $SECRETS"
  # Split with parameter expansion, not `IFS='=' read -r k v`: bash strips a trailing
  # IFS delimiter from the last field, which silently truncates the '=' padding on a
  # base64 value. That made FOSHOL_PHONE_KEY fail validation on every restart.
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in ''|\#*) continue;; esac
    k="${line%%=*}"
    v="${line#*=}"
    [ "$k" = "$line" ] && continue          # no '=' on the line at all
    # Explicit container env wins; the file only fills the gaps.
    [ -n "${!k:-}" ] || export "$k=$v"
  done < "$SECRETS"
else
  if [ -s "$PGDATA/PG_VERSION" ] && [ "${FOSHOL_ALLOW_SECRET_REGEN:-0}" != "1" ]; then
    die "PGDATA exists but $SECRETS is missing. Minting a new FOSHOL_PHONE_KEY against an \
existing database permanently orphans every stored phone ciphertext: PhoneCipher is \
write-only, the IV is random and no key id is stored, so old rows can never be decrypted \
again. Restore the secrets file from backup, or set FOSHOL_ALLOW_SECRET_REGEN=1 to accept \
that loss."
  fi
  log "generating secrets (first boot)"
  : "${FOSHOL_JWT_SECRET:=$(gen_hex 32)}"
  : "${FOSHOL_PHONE_KEY:=$(gen_b64_32)}"
  : "${FOSHOL_DB_PASSWORD:=$(gen_hex 24)}"
  : "${FOSHOL_MINIO_ACCESS_KEY:=foshol$(gen_hex 6)}"
  : "${FOSHOL_MINIO_SECRET_KEY:=$(gen_hex 24)}"
  export FOSHOL_JWT_SECRET FOSHOL_PHONE_KEY FOSHOL_DB_PASSWORD \
         FOSHOL_MINIO_ACCESS_KEY FOSHOL_MINIO_SECRET_KEY
  ( umask 077
    cat > "$SECRETS" <<EOF
FOSHOL_JWT_SECRET=$FOSHOL_JWT_SECRET
FOSHOL_PHONE_KEY=$FOSHOL_PHONE_KEY
FOSHOL_DB_PASSWORD=$FOSHOL_DB_PASSWORD
FOSHOL_MINIO_ACCESS_KEY=$FOSHOL_MINIO_ACCESS_KEY
FOSHOL_MINIO_SECRET_KEY=$FOSHOL_MINIO_SECRET_KEY
EOF
  )
  chmod 0600 "$SECRETS"
fi

# Fail on the app's own invariants now, not 90 seconds later in a Spring stack trace.
python3 - <<'PY' || die "FOSHOL_PHONE_KEY must be base64 of exactly 32 bytes"
import base64, os, sys
try:
    sys.exit(0 if len(base64.b64decode(os.environ["FOSHOL_PHONE_KEY"], validate=True)) == 32 else 1)
except Exception:
    sys.exit(1)
PY
[ "${#FOSHOL_JWT_SECRET}" -ge 32 ]      || die "FOSHOL_JWT_SECRET must be at least 32 characters"
[ "${#FOSHOL_MINIO_SECRET_KEY}" -ge 8 ] || die "FOSHOL_MINIO_SECRET_KEY must be at least 8 characters (MinIO)"

if [ "${FOSHOL_PRINT_GENERATED_SECRETS:-0}" = "1" ]; then
  log "MINIO_ACCESS_KEY=$FOSHOL_MINIO_ACCESS_KEY"
  log "MINIO_SECRET_KEY=$FOSHOL_MINIO_SECRET_KEY"
fi

# --- 3. demo-logins choice is fixed at first boot -----------------------
# Flyway rejects a history containing applied migrations it can no longer resolve, and
# adding db/seed later would introduce V100/V101/V107 below the already-applied V114,
# which is an out-of-order insert. So the choice is locked in both directions.
want_demo="${FOSHOL_DEMO_LOGINS:-1}"
case "$want_demo" in 0|1) ;; *) die "FOSHOL_DEMO_LOGINS must be 0 or 1, got '$want_demo'";; esac
if [ -f "$DEMO_LOCK" ]; then
  had_demo="$(cat "$DEMO_LOCK")"
  if [ "$had_demo" != "$want_demo" ]; then
    die "FOSHOL_DEMO_LOGINS=$want_demo but this volume was initialised with \
FOSHOL_DEMO_LOGINS=$had_demo. Flyway cannot switch the db/seed location on an existing \
history (it would be an out-of-order insert in one direction and an unresolvable applied \
migration in the other). Keep $had_demo, or start from a fresh volume."
  fi
else
  printf '%s' "$want_demo" > "$DEMO_LOCK"
fi
export FOSHOL_DEMO_LOGINS="$want_demo"

# --- 4. initdb, role and database --------------------------------------
if [ ! -s "$PGDATA/PG_VERSION" ]; then
  log "initdb"
  runuser -u postgres -- "$PGBIN/initdb" -D "$PGDATA" \
      --username=postgres --encoding=UTF8 --locale=C.UTF-8 \
      --auth-local=trust --auth-host=scram-sha-256
fi

needs_role=0
[ -f "$STATE/.db-bootstrapped" ] || needs_role=1
[ "${FOSHOL_DB_PASSWORD}" = "$(cat "$STATE/.db-password-fingerprint" 2>/dev/null || true)" ] || needs_role=1

if [ "$needs_role" = 1 ]; then
  log "bootstrapping role and database on a socket-only postgres"
  runuser -u postgres -- "$PGBIN/pg_ctl" -D "$PGDATA" -w -t 60 \
      -o "-c listen_addresses='' -c unix_socket_directories=/var/run/postgresql" start
  # SUPERUSER because V1__enable_extensions.sql runs CREATE EXTENSION vector and
  # pgcrypto. This matches what pgvector/pgvector:pg17 grants POSTGRES_USER in compose.
  runuser -u postgres -- psql -v ON_ERROR_STOP=1 --no-psqlrc -d postgres \
      -v user="${FOSHOL_DB_USER:-foshol}" -v pw="$FOSHOL_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN SUPERUSER PASSWORD %L', :'user', :'pw')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'user') \gexec
SELECT format('ALTER ROLE %I PASSWORD %L', :'user', :'pw') \gexec
SELECT format('CREATE DATABASE foshol OWNER %I', :'user')
 WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'foshol') \gexec
SQL
  runuser -u postgres -- "$PGBIN/pg_ctl" -D "$PGDATA" -w -t 60 -m fast stop
  ( umask 077; printf '%s' "$FOSHOL_DB_PASSWORD" > "$STATE/.db-password-fingerprint" )
  : > "$STATE/.db-bootstrapped"
fi

# --- 5. make MINIO_ENDPOINT resolvable from inside the container --------
# A presigned URL is signed over its Host header, so MINIO_ENDPOINT is simultaneously
# the browser's origin and the app's S3 target. For a hostname we point it at the local
# MinIO, which keeps the app's traffic in-container while the signature still carries
# the public host. For https the TLS terminator must answer, so aliasing would break it.
minio_host_port="${MINIO_ENDPOINT#*://}"; minio_host_port="${minio_host_port%%/*}"
minio_host="${minio_host_port%%:*}"
is_ip=0
case "$minio_host" in
  *[!0-9.]*) ;;
  *.*.*.*)   is_ip=1 ;;
esac

if [ "${FOSHOL_MINIO_LOCAL_ALIAS:-1}" = "1" ]; then
  case "$MINIO_ENDPOINT" in
    https://*)
      log "https MINIO_ENDPOINT: no local alias; this container must reach the TLS terminator" ;;
    http://*)
      case "$minio_host" in
        127.0.0.1|localhost|'') ;;
        *)
          if [ "$is_ip" = 1 ]; then
            log "MINIO_ENDPOINT is a bare IP ($minio_host); /etc/hosts cannot alias an address."
            log "The app will reach it by hairpin through the published port. A hostname avoids that."
          else
            log "aliasing $minio_host -> 127.0.0.1 for in-container S3 calls"
            printf '127.0.0.1 %s\n' "$minio_host" >> /etc/hosts
          fi ;;
      esac ;;
  esac
fi

# Escape hatch for a host that blocks hairpin: bind the address on loopback in here.
if [ "${FOSHOL_MINIO_BIND_VM_IP:-0}" = "1" ] && [ "$is_ip" = 1 ]; then
  if ip addr add "$minio_host/32" dev lo 2>/dev/null; then
    log "bound $minio_host on loopback"
  else
    log "WARNING could not bind $minio_host on lo; --cap-add NET_ADMIN is required"
  fi
fi

log "handing over to supervisord"
exec "$@"
