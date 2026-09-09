#!/usr/bin/env bash
# Start Postgres + MinIO, then boot the Spring API until /actuator/health is UP.
# Default profiles: local,demo (datasource + replay fixtures, no sidecar required).
# LIVE vision (ViT sidecar + Spring local): ./tools/start-live.sh
# (local still seeds identities; do not add demo — it forces replay.)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

APP_LOG="${APP_LOG:-$ROOT/tools/.run/app.log}"
APP_PID_FILE="${APP_PID_FILE:-$ROOT/tools/.run/app.pid}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
PROFILES="${FOSHOL_SPRING_PROFILES:-local,demo}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"

mkdir -p "$ROOT/tools/.run"

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing required command: $1" >&2
    exit 1
  }
}

need docker
need curl

if ! docker info >/dev/null 2>&1; then
  echo "Docker is not running. Start Docker Desktop and retry." >&2
  exit 1
fi

if [[ ! -f "$ROOT/.env" ]]; then
  echo "creating $ROOT/.env from generated secrets"
  umask 077
  {
    echo "FOSHOL_JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')"
    echo "FOSHOL_PHONE_KEY=$(openssl rand -base64 32 | tr -d '\n')"
    echo "FOSHOL_DB_PASSWORD=$(openssl rand -hex 12)"
    echo "FOSHOL_MINIO_ACCESS_KEY=fosholminio"
    echo "FOSHOL_MINIO_SECRET_KEY=$(openssl rand -hex 16)"
  } >"$ROOT/.env"
fi

set -a
# shellcheck disable=SC1091
source "$ROOT/.env"
set +a

export FOSHOL_JWT_SECRET FOSHOL_PHONE_KEY FOSHOL_DB_PASSWORD
export FOSHOL_MINIO_ACCESS_KEY FOSHOL_MINIO_SECRET_KEY

wait_for_postgres() {
  local label="${1:-postgres}"
  echo -n "waiting for ${label}"
  for _ in $(seq 1 60); do
    if docker compose exec -T postgres pg_isready -U foshol -d foshol >/dev/null 2>&1; then
      echo " ok"
      return 0
    fi
    echo -n "."
    sleep 1
  done
  echo " postgres did not become ready" >&2
  return 1
}

reset_postgres_data() {
  echo "local Flyway history does not match current migrations; resetting ./.data/postgres"
  docker compose stop postgres >/dev/null
  rm -rf "$ROOT/.data/postgres"
  docker compose up -d postgres
  wait_for_postgres "postgres after reset" || exit 1
}

# Parallel feature branches reused V110 (officer-queue vs farmer provision). Bind-mounted
# ./.data/postgres keeps the old checksum, and Flyway validate refuses to boot. Same
# recovery as the MinIO key mismatch below: wipe local data, do not repair history.
flyway_history_matches() {
  command -v python3 >/dev/null 2>&1 || {
    echo "python3 not found; skipping Flyway history check" >&2
    return 0
  }
  local dirs="migration"
  if [[ "${PROFILES}" == *local* || "${PROFILES}" == *demo* ]]; then
    dirs="migration,seed"
  fi
  local applied_history
  applied_history="$(docker compose exec -T postgres psql -U foshol -d foshol -At -c \
    "SELECT version || E'\t' || checksum FROM flyway_schema_history WHERE version IS NOT NULL" \
    2>/dev/null || true)"
  [[ -z "${applied_history}" ]] && return 0
  FOSHOL_FLYWAY_HISTORY="${applied_history}" python3 - "$ROOT" "${dirs}" <<'PY'
import os, pathlib, re, sys, zlib

root = pathlib.Path(sys.argv[1])
dirs = [d.strip() for d in sys.argv[2].split(",") if d.strip()]
applied = {}
for line in os.environ.get("FOSHOL_FLYWAY_HISTORY", "").splitlines():
    line = line.strip().replace("\r", "")
    if not line:
        continue
    version, checksum = line.split("\t", 1)
    applied[version] = int(checksum)

def flyway_checksum(path):
    crc = 0
    with open(path, "r", encoding="utf-8-sig", newline="") as handle:
        for raw in handle:
            line = raw[:-1] if raw.endswith("\n") else raw
            if line.endswith("\r"):
                line = line[:-1]
            crc = zlib.crc32(line.encode("utf-8"), crc)
    crc &= 0xFFFFFFFF
    return crc - 0x100000000 if crc >= 0x80000000 else crc

seen = set()
failed = False
for folder in dirs:
    for path in sorted((root / "app/src/main/resources/db" / folder).glob("V*.sql")):
        match = re.match(r"V(\d+)__", path.name)
        if not match:
            continue
        version = str(int(match.group(1)))
        seen.add(version)
        if version not in applied:
            continue
        local = flyway_checksum(path)
        if local != applied[version]:
            print(
                f"  mismatch version {version}: applied {applied[version]}, "
                f"local {local} ({path.name})",
                file=sys.stderr,
            )
            failed = True

for version, checksum in applied.items():
    if version not in seen:
        print(
            f"  mismatch version {version}: applied {checksum}, local file missing",
            file=sys.stderr,
        )
        failed = True

sys.exit(1 if failed else 0)
PY
}

echo "starting postgres and minio"
if ! docker compose up -d postgres minio; then
  occupant="$(docker ps --filter publish=5434 --format '{{.Names}}' | head -n 1 || true)"
  if [[ -z "${occupant}" ]]; then
    occupant="$(docker ps --filter publish=5433 --format '{{.Names}}' | head -n 1 || true)"
  fi
  echo "Could not bind Postgres on the host." >&2
  if [[ -n "${occupant}" ]]; then
    echo "Port is already used by container: ${occupant}" >&2
    echo "Stop it with:  docker stop ${occupant}" >&2
    echo "Then re-run ${ROOT}/tools/start-stack.sh" >&2
  fi
  exit 1
fi

wait_for_postgres || exit 1

echo "checking Flyway history"
if ! flyway_history_matches; then
  reset_postgres_data
fi

echo -n "waiting for minio"
for _ in $(seq 1 60); do
  if curl -sf "http://127.0.0.1:9000/minio/health/live" >/dev/null; then
    echo " ok"
    break
  fi
  echo -n "."
  sleep 1
done
if ! curl -sf "http://127.0.0.1:9000/minio/health/live" >/dev/null; then
  echo " minio did not become ready" >&2
  exit 1
fi

echo "checking MinIO credentials"
if ! docker compose exec -T minio sh -c 'mc alias set local http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc mb --ignore-existing local/foshol-cases >/dev/null'; then
  echo "MinIO data was created with different keys; resetting local object store"
  docker compose stop minio >/dev/null
  rm -rf "$ROOT/.data/minio"
  docker compose up -d minio
  echo -n "waiting for minio after reset"
  for _ in $(seq 1 60); do
    if curl -sf "http://127.0.0.1:9000/minio/health/live" >/dev/null; then
      echo " ok"
      break
    fi
    echo -n "."
    sleep 1
  done
  docker compose exec -T minio sh -c 'mc alias set local http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc mb --ignore-existing local/foshol-cases >/dev/null'
fi

java_pid() {
  pgrep -f 'com.rootcause.foshol.FosholDoctorApplication' | head -n 1 || true
}

if [[ -f "$APP_PID_FILE" ]]; then
  old="$(cat "$APP_PID_FILE" 2>/dev/null || true)"
  if [[ -n "${old:-}" ]] && kill -0 "$old" 2>/dev/null && [[ -n "$(java_pid)" ]]; then
    echo "application already running pid=$(java_pid)"
  else
    rm -f "$APP_PID_FILE"
  fi
fi

if [[ ! -f "$APP_PID_FILE" ]]; then
  existing="$(java_pid)"
  if [[ -n "$existing" ]]; then
    echo "application already running pid=$existing (logs: $APP_LOG)"
    echo "$existing" >"$APP_PID_FILE"
  else
    echo "starting Spring Boot profiles=$PROFILES (logs: $APP_LOG)"
    export FOSHOL_LOG_FILE="$APP_LOG"
    nohup ./gradlew :app:bootRun --console=plain --args="--spring.profiles.active=${PROFILES} --logging.file.name=${APP_LOG}" >>"$APP_LOG" 2>&1 &
    echo $! >"$APP_PID_FILE"
  fi
fi

trap 'echo; echo "Ctrl-C received. Docker stays up. Application keeps running in the background (pid $(cat "$APP_PID_FILE" 2>/dev/null || echo unknown)). Stop it with: kill $(cat "'"$APP_PID_FILE"'")"' INT

echo -n "waiting for ${BASE_URL}/actuator/health"
deadline=$((SECONDS + HEALTH_TIMEOUT))
until curl -sf "${BASE_URL}/actuator/health" >/dev/null 2>&1; do
  if (( SECONDS >= deadline )); then
    echo
    echo "application did not become healthy in ${HEALTH_TIMEOUT}s" >&2
    echo "last log lines:" >&2
    tail -n 80 "$APP_LOG" >&2 || true
    exit 1
  fi
  if [[ -f "$APP_PID_FILE" ]]; then
    pid="$(cat "$APP_PID_FILE")"
    if ! kill -0 "$pid" 2>/dev/null; then
      echo
      echo "application process exited before health check passed" >&2
      tail -n 80 "$APP_LOG" >&2 || true
      rm -f "$APP_PID_FILE"
      exit 1
    fi
  fi
  echo -n "."
  sleep 2
done
echo " ok"

echo
echo "stack is up"
echo "  API     ${BASE_URL}"
echo "  health  $(curl -sf "${BASE_URL}/actuator/health")"
echo "  MinIO   http://127.0.0.1:9000  (console :9001)"
echo "  profile ${PROFILES}"
echo
echo "farmer  +8801711111111  OTP 123456  (Dhaka / DHA)"
echo "officer officer / password   (Dhaka; also officer-dha)"
echo "admin   admin / password     (Dhaka; also admin-dha)"
echo "other districts: officer-{code} / admin-{code}  (e.g. officer-ctg)"
echo
echo "in another terminal:  ${ROOT}/tools/call-api.sh"
echo "follow logs:          tail -f ${APP_LOG}"
echo
echo "this terminal will follow application logs (Ctrl-C detaches, does not kill Docker)"
tail -f "$APP_LOG"
