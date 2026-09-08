#!/usr/bin/env bash
# Start Postgres + MinIO, then boot the Spring API until /actuator/health is UP.
# Default profiles: local,demo (datasource + replay fixtures, no sidecar required).
# Angular UI demo (WEB-FR-112 re-encodes photos): start the sidecar, then
#   docker compose --profile ai up -d sidecar
#   FOSHOL_SPRING_PROFILES=local ./tools/start-stack.sh
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

echo -n "waiting for postgres"
for _ in $(seq 1 60); do
  if docker compose exec -T postgres pg_isready -U foshol -d foshol >/dev/null 2>&1; then
    echo " ok"
    break
  fi
  echo -n "."
  sleep 1
done
if ! docker compose exec -T postgres pg_isready -U foshol -d foshol >/dev/null 2>&1; then
  echo " postgres did not become ready" >&2
  exit 1
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
