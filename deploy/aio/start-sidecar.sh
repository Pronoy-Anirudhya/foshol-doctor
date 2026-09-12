#!/usr/bin/env bash
# The LIVE + visionary pins are sourced here and nowhere else, so they never reach the
# JVM's environment where Spring relaxed binding would bind them to foshol.ai.*.
set -euo pipefail
set -a
. /opt/foshol/sidecar.env
set +a
cd /app
exec python -m uvicorn app.main:app \
  --host "${FOSHOL_SIDECAR_BIND:-127.0.0.1}" --port 8000 --workers 1
