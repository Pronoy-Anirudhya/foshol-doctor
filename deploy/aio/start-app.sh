#!/usr/bin/env bash
# The app dies at boot if Postgres (Flyway + ddl-auto=validate) or MinIO
# (MinioObjectStore's constructor calls ensureBucket) is unreachable, so wait for both.
# The sidecar is deliberately NOT waited on: the app boots fine without it and degrades
# a case to UNDETERMINED, and gating here would add minutes to every restart.
set -euo pipefail

deadline=$(( $(date +%s) + ${FOSHOL_BOOT_WAIT_SECONDS:-300} ))

until pg_isready -h 127.0.0.1 -p 5432 -U "${FOSHOL_DB_USER:-foshol}" -d foshol -q; do
  [ "$(date +%s)" -lt "$deadline" ] || { echo "[app] postgres not ready in time" >&2; exit 1; }
  sleep 1
done
echo "[app] postgres ready"

until curl -fsS -o /dev/null --max-time 3 http://127.0.0.1:9000/minio/health/live; do
  [ "$(date +%s)" -lt "$deadline" ] || { echo "[app] minio not ready in time" >&2; exit 1; }
  sleep 1
done
echo "[app] minio ready"

# MINIO_ENDPOINT is both the S3 client target and the host baked into every presigned
# URL's SigV4 signature, so it must be reachable from in here as well as from the
# browser. Probe it explicitly: failing now with this message beats a Spring stack
# trace from MinioObjectStore's constructor 90 seconds later.
probe_deadline=$(( $(date +%s) + 30 ))
until curl -fsS -o /dev/null --max-time 5 "${MINIO_ENDPOINT%/}/minio/health/live"; do
  if [ "$(date +%s)" -ge "$probe_deadline" ]; then
    cat >&2 <<MSG
[app] FATAL MINIO_ENDPOINT is not reachable from inside the container:
[app]   MINIO_ENDPOINT=${MINIO_ENDPOINT}
[app] Local MinIO is healthy on 127.0.0.1:9000, so this is a routing problem, not MinIO.
[app] This value is signed into every presigned URL, so it must resolve BOTH here and
[app] in the browser. Fixes, in order of preference:
[app]   1. Use a hostname instead of a bare IP. The entrypoint then aliases it to
[app]      127.0.0.1 inside the container and nothing depends on hairpin NAT.
[app]   2. Publish -p 9000:9000 and confirm the host allows hairpin back to itself.
[app]   3. Run with --cap-add NET_ADMIN and FOSHOL_MINIO_BIND_VM_IP=1 to bind the
[app]      address on loopback inside the container.
MSG
    exit 1
  fi
  sleep 1
done
echo "[app] MINIO_ENDPOINT ${MINIO_ENDPOINT} reachable"

# Flyway always runs in-process on every boot and is idempotent; it applies only
# versioned migrations absent from flyway_schema_history. db/seed is the location that
# carries the demo logins, and profile dev omits it.
flyway_locations='classpath:db/migration'
demo_flags=()
if [ "${FOSHOL_DEMO_LOGINS:-1}" = "1" ]; then
  flyway_locations='classpath:db/migration,classpath:db/seed'
  # Farmers authenticate by OTP and the SMS channel is disabled, so without this the
  # seeded Demo Farmer cannot log in and no case can be submitted.
  demo_flags+=(-Dfoshol.auth.otp.dev-code=123456)
  echo "[app] demo logins ENABLED (db/seed + dev OTP). Not a farmer-facing posture."
else
  echo "[app] demo logins disabled: strict dev, no seeded identities"
fi

echo "[app] starting JVM"
# shellcheck disable=SC2086
exec java ${FOSHOL_JVM_OPTS} \
  "-Dspring.flyway.locations=${flyway_locations}" \
  "-Dfoshol.web.cors.origins=${FOSHOL_WEB_CORS_ORIGINS}" \
  "${demo_flags[@]}" \
  -jar /opt/foshol/foshol-doctor.jar
