#!/usr/bin/env bash
# 9000 is the S3 API and must be reachable by the browser: the API issues presigned
# URLs and never proxies object bytes. 9001 is only the console.
set -euo pipefail
export MINIO_ROOT_USER="${FOSHOL_MINIO_ACCESS_KEY:?FOSHOL_MINIO_ACCESS_KEY unset}"
export MINIO_ROOT_PASSWORD="${FOSHOL_MINIO_SECRET_KEY:?FOSHOL_MINIO_SECRET_KEY unset}"
export MINIO_API_CORS_ALLOW_ORIGIN="${MINIO_API_CORS_ALLOW_ORIGIN:-*}"
export MINIO_UPDATE=off
exec /usr/local/bin/minio server "${FOSHOL_STATE_DIR:-/var/lib/foshol}/minio" \
  --address "${FOSHOL_MINIO_BIND:-0.0.0.0:9000}" \
  --console-address "${FOSHOL_MINIO_CONSOLE_BIND:-0.0.0.0:9001}"
