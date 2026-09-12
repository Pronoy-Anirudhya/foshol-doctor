#!/bin/sh
# Healthy means all four services are up. Cheapest check first, so the component that
# failed is the last line of `docker inspect --format '{{json .State.Health}}'`.
set -e
pg_isready -h 127.0.0.1 -p 5432 -U "${FOSHOL_DB_USER:-foshol}" -d foshol -q
curl -fsS -o /dev/null --max-time 3 http://127.0.0.1:9000/minio/health/live
# /health is 503 STARTING until warm, then 200. DEGRADED is the expected LIVE steady
# state: 3 of 5 roles load, so degraded_reasons is never empty.
curl -fsS -o /dev/null --max-time 5 http://127.0.0.1:8000/health
curl -fsS --max-time 5 http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'
