# syntax=docker/dockerfile:1.7
#
# Foshol Doctor — all-in-one VM/demo image.
#
# Postgres 17 + pgvector, MinIO, the LIVE + visionary inference sidecar and the Spring
# Boot app in one container under supervisord. Model weights are baked in, so the
# container needs no network at runtime.
#
# This is a demo/handover convenience. docker-compose.dev.yml remains the supported
# four-container topology; see deploy/aio/README.md.

ARG PY_BASE=python:3.12-slim-bookworm
ARG JRE_IMAGE=eclipse-temurin:25-jre
ARG JDK_IMAGE=eclipse-temurin:25-jdk
ARG MINIO_IMAGE=quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z

############################################################
# 1. jar — runs on the BUILD platform; a bootJar is arch-independent
############################################################
FROM --platform=$BUILDPLATFORM ${JDK_IMAGE} AS jar
WORKDIR /src
COPY gradlew settings.gradle build.gradle gradle.properties lombok.config ./
COPY gradle gradle
COPY buildSrc buildSrc
COPY common common
COPY modules modules
COPY app app
RUN chmod +x gradlew \
 && ./gradlew :app:bootJar --no-daemon -x test -x integrationTest \
 && test -s app/build/libs/foshol-doctor.jar

############################################################
# 2. pydeps-target — the sidecar Python env on the TARGET platform.
#    Pins copied from sidecar/Dockerfile; huggingface_hub is pinned here because
#    the sidecar Dockerfile leaves it to transitive resolution and 1.x changes
#    offline snapshot behaviour.
############################################################
FROM ${PY_BASE} AS pydeps-target
ENV PIP_DISABLE_PIP_VERSION_CHECK=1 PIP_NO_CACHE_DIR=1
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends libgomp1 ffmpeg; \
    rm -rf /var/lib/apt/lists/*; \
    pip install --upgrade pip
RUN set -eux; \
    pip install torch==2.6.0 torchvision==0.21.0 --index-url https://download.pytorch.org/whl/cpu; \
    pip install \
        fastapi==0.115.12 uvicorn==0.34.2 pydantic==2.11.4 python-multipart==0.0.20 \
        pillow==11.2.1 transformers==4.51.3 timm==1.0.15 faster-whisper==1.1.1 \
        ctranslate2==4.5.0 sentence-transformers==3.4.1 huggingface_hub==0.30.2

############################################################
# 3. pydeps-build — the same env on the BUILD platform, for the bake.
#    Duplicated deliberately: re-platforming a local stage is not reliable, and on a
#    native build the identical instructions hit the same cache entry for free.
############################################################
FROM --platform=$BUILDPLATFORM ${PY_BASE} AS pydeps-build
ENV PIP_DISABLE_PIP_VERSION_CHECK=1 PIP_NO_CACHE_DIR=1
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends libgomp1 ffmpeg; \
    rm -rf /var/lib/apt/lists/*; \
    pip install --upgrade pip
RUN set -eux; \
    pip install torch==2.6.0 torchvision==0.21.0 --index-url https://download.pytorch.org/whl/cpu; \
    pip install \
        fastapi==0.115.12 uvicorn==0.34.2 pydantic==2.11.4 python-multipart==0.0.20 \
        pillow==11.2.1 transformers==4.51.3 timm==1.0.15 faster-whisper==1.1.1 \
        ctranslate2==4.5.0 sentence-transformers==3.4.1 huggingface_hub==0.30.2

############################################################
# 4. bake — fetch the 3 LIVE roles, convert Whisper to CTranslate2 int8, plant the
#    tokenizer sentinel and prove the fast path. Runs NATIVE. The only stage with
#    network access to Hugging Face. The HF cache and the int8 model are
#    architecture-independent data, so the result is COPYed into the target runtime.
############################################################
FROM pydeps-build AS bake
WORKDIR /app
COPY sidecar/app /app/app
COPY deploy/aio/sidecar.env /opt/foshol/sidecar.env
COPY deploy/aio/bake_models.py /opt/foshol/bake_models.py
ENV HF_HOME=/opt/foshol/hf-cache \
    HF_HUB_DISABLE_TELEMETRY=1 \
    HF_HUB_OFFLINE=0 \
    TRANSFORMERS_OFFLINE=0 \
    PYTHONUNBUFFERED=1
ARG BAKE_CT2=1
ARG PRUNE_ASR_TRAINING_STATE=1
RUN set -eux; \
    set -a; . /opt/foshol/sidecar.env; set +a; \
    HF_HUB_OFFLINE=0 TRANSFORMERS_OFFLINE=0 \
    BAKE_CT2="${BAKE_CT2}" python /opt/foshol/bake_models.py
# Adam optimiser state from the upstream fine-tune: never read by the converter,
# transformers or faster-whisper. Truncated rather than deleted so the file still
# exists for huggingface_hub 1.x's existence-only snapshot check.
RUN set -eux; \
    if [ "${PRUNE_ASR_TRAINING_STATE}" = "1" ]; then \
      for p in /opt/foshol/hf-cache/hub/models--ashrafulparan--whisper-small-bangla/snapshots/*/checkpoint-5394/optimizer.pt; do \
        [ -e "$p" ] || continue; \
        b="$(readlink -f "$p")"; : > "$b"; test -e "$p"; \
      done; \
    fi; \
    du -sh /opt/foshol/hf-cache

############################################################
# 5. external binaries, pinned, resolved per TARGETPLATFORM
############################################################
FROM ${JRE_IMAGE}   AS jre
FROM ${MINIO_IMAGE} AS minio

############################################################
# 6. runtime
############################################################
FROM pydeps-target AS runtime

# --- Postgres 17 + pgvector from PGDG (bookworm) --------------------------
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
        curl ca-certificates gnupg procps less iproute2 netcat-openbsd; \
    install -d /usr/share/postgresql-common/pgdg; \
    curl -fsSL -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc \
        https://www.postgresql.org/media/keys/ACCC4CF8.asc; \
    echo 'deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt bookworm-pgdg main' \
        > /etc/apt/sources.list.d/pgdg.list; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
        postgresql-17 postgresql-client-17 postgresql-17-pgvector; \
    pg_dropcluster --stop 17 main || true; \
    rm -rf /var/lib/apt/lists/*; \
    /usr/lib/postgresql/17/bin/postgres --version; \
    ls /usr/share/postgresql/17/extension/vector.control
ENV PATH=/usr/lib/postgresql/17/bin:$PATH

# --- JRE 25 --------------------------------------------------------------
COPY --from=jre /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=/opt/java/openjdk/bin:$PATH
RUN java -version

# --- MinIO ---------------------------------------------------------------
COPY --from=minio /usr/bin/minio /usr/local/bin/minio
COPY --from=minio /usr/bin/mc    /usr/local/bin/mc
RUN minio --version

# --- supervisord in its own venv, so the sidecar site-packages stay untouched
RUN set -eux; \
    python -m venv /opt/supervisor-venv; \
    /opt/supervisor-venv/bin/pip install --no-cache-dir --upgrade pip setuptools; \
    /opt/supervisor-venv/bin/pip install --no-cache-dir supervisor==4.3.0; \
    /opt/supervisor-venv/bin/supervisord -v

# --- users and directories ----------------------------------------------
RUN set -eux; \
    useradd --create-home --uid 10001 --shell /usr/sbin/nologin foshol; \
    install -d -o postgres -g postgres -m 0775 /var/run/postgresql; \
    install -d -o root -g root -m 0755 /opt/foshol /etc/supervisor /var/lib/foshol

# --- sidecar code + replay fixtures -------------------------------------
COPY --chown=foshol:foshol sidecar/app      /app/app
COPY --chown=foshol:foshol sidecar/fixtures /app/fixtures

# --- baked model cache, split per repo so no single layer is oversized.
#     Ownership is baked in so the container never chowns ~3 GB at boot.
COPY --from=bake --chown=foshol:foshol \
     /opt/foshol/hf-cache/hub/models--sentence-transformers--LaBSE \
     /opt/foshol/hf-cache/hub/models--sentence-transformers--LaBSE
COPY --from=bake --chown=foshol:foshol \
     /opt/foshol/hf-cache/hub/models--ashrafulparan--whisper-small-bangla \
     /opt/foshol/hf-cache/hub/models--ashrafulparan--whisper-small-bangla
COPY --from=bake --chown=foshol:foshol \
     /opt/foshol/hf-cache/hub/models--VisionaryQuant--5_Crop_Disease_Detection \
     /opt/foshol/hf-cache/hub/models--VisionaryQuant--5_Crop_Disease_Detection
COPY --from=bake --chown=foshol:foshol \
     /opt/foshol/hf-cache/hub/models--openai--whisper-tiny \
     /opt/foshol/hf-cache/hub/models--openai--whisper-tiny

# --- the application jar -------------------------------------------------
COPY --from=jar --chown=foshol:foshol \
     /src/app/build/libs/foshol-doctor.jar /opt/foshol/foshol-doctor.jar

# --- process management + scripts ---------------------------------------
COPY deploy/aio/supervisord.conf  /etc/supervisor/supervisord.conf
COPY deploy/aio/sidecar.env       /opt/foshol/sidecar.env
COPY deploy/aio/NOTICE            /opt/foshol/NOTICE
COPY deploy/aio/entrypoint.sh     /usr/local/bin/entrypoint.sh
COPY deploy/aio/start-postgres.sh /usr/local/bin/start-postgres.sh
COPY deploy/aio/start-minio.sh    /usr/local/bin/start-minio.sh
COPY deploy/aio/start-sidecar.sh  /usr/local/bin/start-sidecar.sh
COPY deploy/aio/start-app.sh      /usr/local/bin/start-app.sh
COPY deploy/aio/healthcheck.sh    /usr/local/bin/healthcheck.sh
COPY deploy/aio/fatal_exit.py     /usr/local/bin/fatal_exit.py
RUN chmod 0755 /usr/local/bin/entrypoint.sh /usr/local/bin/start-postgres.sh \
    /usr/local/bin/start-minio.sh /usr/local/bin/start-sidecar.sh \
    /usr/local/bin/start-app.sh /usr/local/bin/healthcheck.sh

# --- env contract. App-facing only: the sidecar's FOSHOL_AI_* pins deliberately
#     live in /opt/foshol/sidecar.env, because Spring relaxed binding would map them
#     onto the app's own foshol.ai.* properties and move the knowledge-content gate.
ENV FOSHOL_STATE_DIR=/var/lib/foshol \
    PGDATA=/var/lib/foshol/pgdata \
    SPRING_PROFILES_ACTIVE=dev \
    DB_URL=jdbc:postgresql://127.0.0.1:5432/foshol \
    FOSHOL_DB_USER=foshol \
    SIDECAR_BASE_URL=http://127.0.0.1:8000 \
    MINIO_ENDPOINT=http://127.0.0.1:9000 \
    MINIO_API_CORS_ALLOW_ORIGIN=* \
    FOSHOL_MINIO_BIND=0.0.0.0:9000 \
    FOSHOL_MINIO_CONSOLE_BIND=0.0.0.0:9001 \
    FOSHOL_MINIO_LOCAL_ALIAS=1 \
    FOSHOL_SIDECAR_BIND=127.0.0.1 \
    FOSHOL_WEB_CORS_ORIGINS=http://localhost:4200,https://localhost \
    FOSHOL_DEMO_LOGINS=1 \
    FOSHOL_JVM_OPTS="-XX:MaxRAMPercentage=20 -XX:InitialRAMPercentage=10 -XX:MaxMetaspaceSize=256m -XX:+ExitOnOutOfMemoryError -XX:+UseSerialGC -Djava.security.egd=file:/dev/urandom" \
    FOSHOL_PG_SHARED_BUFFERS=256MB \
    FOSHOL_PG_MAX_CONNECTIONS=100 \
    FOSHOL_BOOT_WAIT_SECONDS=300 \
    FOSHOL_PRINT_GENERATED_SECRETS=0 \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    TZ=UTC

# --- build manifest: exactly what went in -------------------------------
RUN set -eux; \
    { echo "built_at=$(date -u +%FT%TZ)"; \
      echo "target_arch=$(uname -m)"; \
      java -version 2>&1 | head -1; \
      /usr/lib/postgresql/17/bin/postgres --version; \
      minio --version 2>&1 | head -1; \
      echo '--- dpkg ---'; dpkg-query -W -f='${Package}\t${Version}\n' | sort; \
      echo '--- pip ---';  pip freeze; \
      echo '--- models ---'; du -sh /opt/foshol/hf-cache/hub/*; \
    } > /opt/foshol/build-manifest.txt

LABEL org.opencontainers.image.title="foshol-doctor-aio" \
      org.opencontainers.image.description="Postgres 17+pgvector, MinIO, the LIVE visionary inference sidecar and the Spring Boot app in one container" \
      org.opencontainers.image.source="https://github.com/Pronoy-Anirudhya/foshol-doctor" \
      org.opencontainers.image.licenses="see /opt/foshol/NOTICE; bundles MinIO (AGPL-3.0)"

EXPOSE 8080 9000 9001 8000 5432
STOPSIGNAL SIGTERM

HEALTHCHECK --interval=15s --timeout=10s --start-period=600s --start-interval=5s --retries=6 \
    CMD /usr/local/bin/healthcheck.sh

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["/opt/supervisor-venv/bin/supervisord", "-c", "/etc/supervisor/supervisord.conf"]
