# Foshol Doctor — four-container deployment (recommended)

The supported topology: **four containers** — `postgres`, `minio`, `sidecar`, `app` — wired by
[`../docker-compose.dev.yml`](../docker-compose.dev.yml). Independent restarts, separate logs,
separate failure domains.

In Docker Desktop this appears as a **`foshol-dev` group that expands to four containers**,
the same shape you see locally.

> The all-in-one image ([`README.md`](README.md)) packs the same four processes into one
> container under supervisord. It is a single-artifact convenience; this is the better
> operational shape. Both are built and shipped — pick one.

---

## 1. What DevOps needs

| Item | Source |
|---|---|
| `foshol-doctor-app-0.0.1-linux-amd64.tar.gz` | in this directory |
| `foshol-doctor-sidecar-0.0.1-baked-linux-amd64.tar.gz` | in this directory — **use this one** |
| `foshol-doctor-sidecar-0.0.1-linux-amd64.tar.gz` | alternative, downloads weights on first boot |
| `pgvector/pgvector:pg17` | pulled from Docker Hub automatically |
| `minio/minio:RELEASE.2025-04-22T22-12-26Z` | pulled from Docker Hub automatically |
| `docker-compose.dev.yml` + `.env` | from the repository |

Only the two application images are private and need transferring. Postgres and MinIO are
public upstream images that `docker compose` pulls on its own.

### Which sidecar image

| | `:0.0.1-baked` (recommended) | `:0.0.1` |
|---|---|---|
| Model weights | baked in (~3 GB) | downloads ~4.8 GB from Hugging Face on first boot |
| Outbound HTTPS needed | no | **yes**, on first boot |
| First boot | ~1 min | ~10 min, or longer on a slow link |
| Every restart | fast | re-converts Whisper to int8, ~260s (see §6) |
| Tarball | 3.3 GB | 651 MB |

Both run **LIVE mode with the Visionary model** by default.

---

## 2. Load the images

```bash
shasum -a 256 -c foshol-doctor-app-0.0.1-linux-amd64.tar.gz.sha256
shasum -a 256 -c foshol-doctor-sidecar-0.0.1-baked-linux-amd64.tar.gz.sha256

gunzip -c foshol-doctor-app-0.0.1-linux-amd64.tar.gz           | docker load
gunzip -c foshol-doctor-sidecar-0.0.1-baked-linux-amd64.tar.gz | docker load
```

These load as `foshol-doctor-app:0.0.1` and `foshol-doctor-sidecar:0.0.1-baked`, which is
exactly what the Compose file expects. Confirm the VM is x86_64 first: `uname -m`.

---

## 3. Configure `.env`

```bash
cp .env.dev.example .env
chmod 600 .env
```

Fill in every placeholder, then **add these two lines** for the baked sidecar:

```sh
FOSHOL_SIDECAR_IMAGE=foshol-doctor-sidecar:0.0.1-baked
HF_HUB_OFFLINE=1
TRANSFORMERS_OFFLINE=1
```

`MINIO_ENDPOINT` is the one value that is not a Docker DNS name — it must be the
**browser-reachable** origin, because the API returns presigned URLs signed over their `Host`
header. `.env.dev.example` documents this. Use the VM's own address and port 9000.

To get seeded logins (see §5), also add:

```sh
SPRING_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed
FOSHOL_AUTH_OTP_DEV_CODE=123456
```

---

## 4. Start

```bash
docker compose -f docker-compose.dev.yml up -d --wait
```

**Without `--build`.** Compose uses the images you just loaded; `--build` would rebuild from
source and discard them. Verify what it resolved before starting:

```bash
docker compose -f docker-compose.dev.yml config --images
```

Expect four images and no surprises. Then:

```bash
docker compose -f docker-compose.dev.yml ps        # four services, all healthy
docker compose -f docker-compose.dev.yml logs -f app sidecar
```

Published ports: `8080` (API) and `9000`/`9001` (MinIO). **9000 must be published and
browser-reachable** or every case photo fails while the API looks healthy.

---

## 5. Logins

With the two seed lines from §3 in place:

| Username | Password | Role |
|---|---|---|
| `officer` | `password` | field officer, district `DHA` |
| `admin` | `password` | admin |
| `officer-<district>` / `admin-<district>` | `password` | one pair per district |

Farmer sign-in uses phone OTP `123456`. The review queue starts empty.

> Demo posture, not production. The bcrypt of `password` is public in the repository. Leave
> both lines out for the documented strict `dev` contract (`COMMON-SEC-003`) — but then there
> are no logins at all, and the choice is fixed at first boot because Flyway cannot add or
> remove seed migrations from an existing history.

---

## 6. Verified, and two defects you should know about

This exact stack was brought up and checked: **12 of 12 passed** — four containers healthy,
sidecar `mode=LIVE` with the Visionary model at revision `63080391…` matching
`V112__visionaryquant_label_map.sql`, 3 of 5 roles loaded, no Hugging Face download, ASR
warmup ran, `/actuator/health` UP, 20 live diseases through the knowledge gate, `db/seed`
applied, `foshol-cases` auto-created, and `officer`/`password` returning a token.

**`sidecar/Dockerfile` base pin — fixed.** It said `FROM python:3.12-slim`, and that tag moved
from Debian bookworm to trixie (glibc 2.36 → 2.41). On linux/amd64, glibc 2.41 refuses the
executable stack that `ctranslate2 4.5.0`'s bundled library requests, so `import ctranslate2`
fails and the LIVE sidecar exits — ASR never loads. aarch64 wheels are unaffected, which is
why it never showed on an Apple Silicon laptop. Now pinned to `python:3.12-slim-bookworm`.

**`sidecar/app/asr.py:418` — not fixed, owned elsewhere.** The CTranslate2 conversion is
skipped only when `ct2/model.bin` *and* `ct2/tokenizer.json` both exist. The Whisper repo
ships neither a `tokenizer.json` nor does the converter emit one, so the sentinel can never be
satisfied and **every boot re-runs the full int8 conversion, measured at ~260s**. faster-whisper
then falls back to fetching `openai/whisper-tiny` through a Rust path that ignores
`HF_HUB_OFFLINE`. The `-baked` image sidesteps both by shipping that file; the plain `:0.0.1`
image still pays the cost on every restart.

---

## 7. Post-deploy checks

Run from **outside** the VM. A healthy API does not prove the media path works.

```bash
curl -sf http://<vm>:8080/actuator/health
curl -sf http://<vm>:9000/minio/health/live

# the sidecar must report the Visionary revision V112 keys on
docker compose -f docker-compose.dev.yml exec sidecar \
  python -c "import urllib.request;print(urllib.request.urlopen('http://127.0.0.1:8000/v1/models').read().decode())" \
  | grep -o 63080391f7d2bdb331ab356b0d1d9b4b603b3946

curl -sf -X POST http://<vm>:8080/api/v1/auth/officer/login \
  -H 'Content-Type: application/json' -d '{"username":"officer","password":"password"}'

# media path, once a case exists
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://<vm>:8080/api/v1/cases/<caseId>/images/<imageId>/url"
```

The returned `url` must start `http://<vm>:9000/foshol-cases/…`. Fetch it with **no**
`Authorization` header — a bearer makes MinIO answer `400 InvalidRequest`, and
`403 SignatureDoesNotMatch` means `MINIO_ENDPOINT` disagrees with the host requested.

---

## 8. Operations

```bash
docker compose -f docker-compose.dev.yml restart sidecar   # one service, not the stack
docker compose -f docker-compose.dev.yml stop              # keep volumes
docker compose -f docker-compose.dev.yml down              # keep named volumes
docker compose -f docker-compose.dev.yml down -v           # DESTROYS db, objects, model cache
```

Volumes: `foshol-dev_postgres-data`, `foshol-dev_minio-data`, `foshol-dev_huggingface-cache`.

```bash
docker compose -f docker-compose.dev.yml exec postgres \
  pg_dump -U foshol -d foshol -Fc > foshol-$(date +%F).dump
```

Sizing: the sidecar budgets up to 4 GiB resident; allow **≥ 10 GB RAM** and 4 vCPU for the
host overall. Pushing these to a registry: [`PUSH.md`](PUSH.md).
