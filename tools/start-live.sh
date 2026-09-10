#!/usr/bin/env bash
# Start the LIVE stack: Postgres, MinIO, one vision backbone + ASR + LaBSE, then Spring (profile local).
# Replay/demo remains: ./tools/start-stack.sh
#
# Usage:
#   ./tools/start-live.sh              # ViT (default)
#   ./tools/start-live.sh vit
#   ./tools/start-live.sh visionary    # EfficientNet-B3; never loaded together with ViT
#
# First run builds the sidecar image (torch) and downloads the selected vision model,
# Whisper, and LaBSE weights into ~/.cache/huggingface. Later runs reuse the cache.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ $# -gt 1 ]]; then
  echo "usage: $0 [vit|visionary]" >&2
  exit 1
fi

BACKEND="${1:-vit}"
case "$BACKEND" in
  vit)
    export FOSHOL_SIDECAR_VISION_BACKEND=vit
    export FOSHOL_AI_VISION_RICE_MODEL_ID=wambugu71/crop_leaf_diseases_vit
    export FOSHOL_AI_VISION_RICE_MODEL_REVISION=7d5b32bcd6f83a2f57e7e0346358fad276296877
    ;;
  visionary)
    export FOSHOL_SIDECAR_VISION_BACKEND=visionary
    export FOSHOL_AI_VISION_RICE_MODEL_ID=VisionaryQuant/5_Crop_Disease_Detection
    export FOSHOL_AI_VISION_RICE_MODEL_REVISION=63080391f7d2bdb331ab356b0d1d9b4b603b3946
    ;;
  *)
    echo "usage: $0 [vit|visionary]" >&2
    exit 1
    ;;
esac

export FOSHOL_AI_MODE=live
export FOSHOL_AI_VISION_CROP_ROUTES="${FOSHOL_AI_VISION_CROP_ROUTES:-rice=rice,tomato=rice,potato=rice,corn=rice,wheat=rice}"
export FOSHOL_SIDECAR_TORCH_THREADS="${FOSHOL_SIDECAR_TORCH_THREADS:-4}"
export HF_HUB_OFFLINE=0
export TRANSFORMERS_OFFLINE=0
export FOSHOL_SPRING_PROFILES=local
export HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-300}"
SIDECAR_URL="${SIDECAR_URL:-http://127.0.0.1:8000}"
SIDECAR_TIMEOUT="${SIDECAR_TIMEOUT:-1800}"

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

mkdir -p "${HOME}/.cache/huggingface"

echo "starting postgres, minio, and LIVE sidecar (mode=${FOSHOL_AI_MODE} backend=${FOSHOL_SIDECAR_VISION_BACKEND} model=${FOSHOL_AI_VISION_RICE_MODEL_ID})"
docker compose --profile ai up -d --build postgres minio sidecar

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

echo -n "waiting for sidecar ${SIDECAR_URL}/health (first run downloads vision, Whisper, and LaBSE weights)"
deadline=$((SECONDS + SIDECAR_TIMEOUT))
until curl -sf "${SIDECAR_URL}/health" >/dev/null 2>&1; do
  if (( SECONDS >= deadline )); then
    echo
    echo "sidecar did not become healthy in ${SIDECAR_TIMEOUT}s" >&2
    docker compose --profile ai logs --tail 80 sidecar >&2 || true
    exit 1
  fi
  echo -n "."
  sleep 3
done
echo " ok"
echo "  sidecar $(curl -sf "${SIDECAR_URL}/health")"

stop_pid() {
  local pid="$1"
  [[ -z "$pid" ]] && return 0
  kill "$pid" 2>/dev/null || true
}

java_pid() {
  pgrep -f 'com.rootcause.foshol.FosholDoctorApplication' | head -n 1 || true
}

gradle_pid() {
  pgrep -f 'gradlew :app:bootRun' | head -n 1 || true
}

existing="$(java_pid)"
gradle="$(gradle_pid)"
if [[ -n "$existing" || -n "$gradle" ]]; then
  echo "stopping existing Spring/gradle so LIVE profile=local can start"
  stop_pid "$existing"
  stop_pid "$gradle"
  for _ in $(seq 1 20); do
    if [[ -z "$(java_pid)" && -z "$(gradle_pid)" ]]; then
      break
    fi
    sleep 1
  done
  if [[ -n "$(java_pid)" ]]; then
    echo "Spring did not exit; sending SIGKILL" >&2
    kill -9 "$(java_pid)" 2>/dev/null || true
  fi
  if [[ -n "$(gradle_pid)" ]]; then
    kill -9 "$(gradle_pid)" 2>/dev/null || true
  fi
  rm -f "$ROOT/tools/.run/app.pid"
fi

echo "starting Spring Boot (Flyway applies pending migrations on this boot)"
exec "$ROOT/tools/start-stack.sh"
