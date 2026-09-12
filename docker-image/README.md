# Foshol Doctor — all-in-one image, DevOps handover

One container running the entire backend stack: **Postgres 17 + pgvector, MinIO, the
inference sidecar in LIVE mode with the `visionary` model, and the Spring Boot API.**
Model weights are baked in, so the container needs **no internet access at runtime**.

The Angular frontend is **not** in this image — it lives in a separate repository and is
deployed separately.

> This directory holds build **output**. The tarballs are gitignored; only this README is
> committed. Build sources are in [`../deploy/aio/`](../deploy/aio/).

---

## 0. What is in this directory

> **Most deployments want the four-container stack, not this image.**
> See [`DEPLOY-COMPOSE.md`](DEPLOY-COMPOSE.md) — `postgres`, `minio`, `sidecar`, `app` as four
> containers with independent restarts and separate logs. That is the supported topology.
> Read on only if you specifically want everything in one container.


| File | Size | Purpose |
|---|---|---|
| `foshol-doctor-aio-0.0.1-linux-amd64.tar.gz` | 3.6 GB | **the all-in-one image** — everything in one container |
| `foshol-doctor-app-0.0.1-linux-amd64.tar.gz` | 209 MB | the normal API image, for `docker-compose.dev.yml` |
| `foshol-doctor-sidecar-0.0.1-linux-amd64.tar.gz` | 656 MB | the normal sidecar image, for `docker-compose.dev.yml` |
| `*.sha256` | — | checksums; verify before loading |
| `PUSH.md` | — | how to push these to a registry |

All three are **`linux/amd64`**. The last two are the images `docker-compose.dev.yml` already
builds, shipped so you have a route to the four-container topology without rebuilding
anything — see §9.

---

## 1. Load the image

```bash
gunzip -c foshol-doctor-aio-0.0.1-linux-amd64.tar.gz | docker load
```

Verify the tarball first, and confirm the architecture matches the VM:

```bash
shasum -a 256 -c foshol-doctor-aio-0.0.1-linux-amd64.tar.gz.sha256
uname -m          # must be x86_64 for the -amd64 image
```

---

## 2. Run it

```bash
docker volume create foshol-state

docker run -d --name foshol \
  --restart unless-stopped \
  --memory 10g --cpus 4 --stop-timeout 60 \
  -p 8080:8080 -p 9000:9000 \
  -v foshol-state:/var/lib/foshol \
  -e MINIO_ENDPOINT=http://foshol.example.com:9000 \
  -e FOSHOL_WEB_CORS_ORIGINS=https://app.example.com \
  -e FOSHOL_PRINT_GENERATED_SECRETS=1 \
  foshol-doctor-aio:0.0.1-amd64
```

Four things in that command are not optional:

- **`--memory`.** The JVM sizes its heap as a percentage of the container limit. Without a
  limit it reads the whole host, reserves a heap far too large, and the kernel OOM-kills
  whichever process has the biggest resident set — usually the sidecar. 10 GB is the
  recommended floor; the sidecar alone budgets up to 4 GiB.
- **`--stop-timeout 60`.** Docker's default is 10 seconds, which kills Postgres
  mid-checkpoint and forces recovery on the next boot. Use `docker stop -t 60` too.
- **`-p 9000:9000`.** See §4. Without it every case photo fails to load while the API
  looks perfectly healthy.
- **`-v foshol-state:...`.** Without it the database, the object store **and the generated
  secrets** die with the container. The entrypoint warns when this is missing.

First boot takes **3–8 minutes**: `initdb`, Flyway, the knowledge-content gate, then
loading three models from the baked cache. `docker logs -f foshol` to watch;
`docker inspect -f '{{.State.Health.Status}}' foshol` reports `healthy` when all four
services are up.

---

## 3. Logins

The image ships with demo identities applied (`FOSHOL_DEMO_LOGINS=1`, the default), because
a stack you cannot sign into cannot be demonstrated.

| Username | Password | Role |
|---|---|---|
| `officer` | `password` | field officer, district `DHA` |
| `admin` | `password` | admin |
| `officer-<district>` / `admin-<district>` | `password` | one pair per district |

The farmer signs in by phone OTP with the development code **`123456`** (the SMS channel is
disabled). The review queue starts **empty** — no historical cases are seeded.

> **This is a demo posture, not a production one.** It deviates from the documented `dev`
> contract (`COMMON-SEC-003`): the bcrypt of `password` is public in the repository and the
> OTP is fixed. Do not expose this configuration to real farmers. `FOSHOL_DEMO_LOGINS=0`
> restores strict `dev` with no seeded identities and no dev OTP — but it **must be chosen
> on first boot**, because Flyway cannot add or remove the seed migrations from an existing
> history. Changing it later is refused with an explanatory message.

---

## 4. `MINIO_ENDPOINT` — the one setting that is easy to get wrong

The API **never proxies object bytes.** It checks ownership and returns a MinIO *presigned
URL*, which the browser fetches directly. A presigned URL is signed over its `Host` header,
so the host inside it cannot be rewritten afterwards by a proxy.

That makes `MINIO_ENDPOINT` a single value that must be reachable from **both** the browser
and the container:

- ✅ `http://foshol.example.com:9000` — a hostname the browser resolves. Preferred: the
  container aliases it to `127.0.0.1` internally, so the app's own S3 traffic never leaves
  the container while the signature still carries the public host.
- ⚠️ `http://10.0.0.12:9000` — a bare IP works, but `/etc/hosts` cannot alias an address, so
  the app reaches MinIO by hairpin through the published port. Fine on a normal Linux host;
  if your host blocks hairpin, add `--cap-add NET_ADMIN -e FOSHOL_MINIO_BIND_VM_IP=1`.
- ❌ `http://minio:9000` or `http://127.0.0.1:9000` on a real VM — the browser gets
  `ERR_NAME_NOT_RESOLVED` or hits its own machine. Every image, audio clip and Grad-CAM
  overlay fails while `/actuator/health` stays `UP`.

Port **9000** is the S3 API and is the one to publish. **9001** is only the console and does
not serve presigned objects.

Keep this value in step with the frontend's `MEDIA_OBJECT_STORE_ORIGIN`, which allow-lists
the same origin in its CSP `img-src`, `media-src` and `connect-src`. A mismatch there blocks
the images even when the URLs are correct. Presigned links expire after **10 minutes**.

For HTTPS, terminate TLS on a **subdomain** (`https://s3.example.com`) preserving the `Host`
header, and set `FOSHOL_MINIO_LOCAL_ALIAS=0`. Not a path prefix — the client signs
path-style URLs and stripping a prefix invalidates the signature.

---

## 5. Environment contract

| Variable | Default | Notes |
|---|---|---|
| `MINIO_ENDPOINT` | `http://127.0.0.1:9000` | **must be overridden on a VM.** See §4 |
| `FOSHOL_WEB_CORS_ORIGINS` | `http://localhost:4200,https://localhost` | **override** with the frontend origin |
| `FOSHOL_DEMO_LOGINS` | `1` | `0` for strict `dev`. First boot only |
| `FOSHOL_JWT_SECRET` | generated | ≥ 32 chars |
| `FOSHOL_PHONE_KEY` | generated | 32-byte base64 AES key. **Never change it** — see §7 |
| `FOSHOL_DB_PASSWORD` | generated | a changed value triggers `ALTER ROLE` |
| `FOSHOL_MINIO_ACCESS_KEY` / `_SECRET_KEY` | generated | MinIO root credentials |
| `FOSHOL_PRINT_GENERATED_SECRETS` | `0` | `1` logs the generated MinIO keys once. Off in production |
| `FOSHOL_JVM_OPTS` | `-XX:MaxRAMPercentage=20 …` | raise with a bigger `--memory` |
| `FOSHOL_PG_SHARED_BUFFERS` | `256MB` | raise on a large VM |
| `FOSHOL_MINIO_CONSOLE_BIND` | `0.0.0.0:9001` | publish 9001 only if you want the console |
| `MINIO_API_CORS_ALLOW_ORIGIN` | `*` | tighten in production |
| `TZ` | `UTC` | |

Secrets left unset are **generated on first boot and persisted** to
`/var/lib/foshol/secrets/secrets.env` inside the volume, so restarts reuse them. To supply
your own instead, pass `--env-file`; explicit values always win over the stored ones.

Do **not** set `FOSHOL_AI_*` or `HF_*` on the container. Those belong to the sidecar and are
read from a file inside the image; setting them here also re-points the app's startup
content gate, because Spring binds `FOSHOL_AI_VISION_RICE_MODEL_ID` to its own
`foshol.ai.vision.rice.model-id`.

`SPRING_PROFILES_ACTIVE`, `DB_URL` and `SIDECAR_BASE_URL` are already correct for
in-container loopback. Leave them alone.

---

## 6. Post-deploy checks

A healthy API does **not** prove the media path works. Run these from **outside** the VM.

```bash
# 1. API
curl -sf http://<vm>:8080/actuator/health

# 2. the presign target must be reachable by clients, not just by the app
curl -sf http://<vm>:9000/minio/health/live

# 3. the sidecar loaded the right model. Expect 3 of 5 roles and this exact revision;
#    a mismatch silently turns every prediction into UNDETERMINED.
docker exec foshol curl -sf http://127.0.0.1:8000/v1/models \
  | grep -o '63080391f7d2bdb331ab356b0d1d9b4b603b3946'

# 4. log in (proves the seed migrations applied)
curl -sf -X POST http://<vm>:8080/api/v1/auth/officer/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"officer","password":"password"}'

# 5. the media path, end to end, once a case exists
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://<vm>:8080/api/v1/cases/<caseId>/images/<imageId>/url"
```

The `url` from step 5 must begin `http://<vm>:9000/foshol-cases/…`. Fetch it with **no**
`Authorization` header — a presigned URL carries its own credentials, and adding a bearer
makes MinIO answer `400 InvalidRequest`. `403 SignatureDoesNotMatch` means `MINIO_ENDPOINT`
disagrees with the host you requested.

`/opt/foshol/build-manifest.txt` inside the image records exactly what was built in:
`docker run --rm --entrypoint cat foshol-doctor-aio:0.0.1-amd64 /opt/foshol/build-manifest.txt`

---

## 7. Backup, restore, and the one irreversible mistake

Everything persistent is in the single volume `/var/lib/foshol`:

```
pgdata/               the database
minio/                case photos, audio, Grad-CAM overlays
secrets/secrets.env   the generated secrets
```

```bash
# database dump
docker exec foshol pg_dump -U foshol -d foshol -Fc > foshol-$(date +%F).dump

# whole-volume snapshot (stop first for a consistent copy)
docker stop -t 60 foshol
docker run --rm -v foshol-state:/state -v "$PWD":/out alpine \
  tar czf /out/foshol-state-$(date +%F).tar.gz -C /state .
docker start foshol
```

**Back up `secrets/secrets.env` with the database, always.** Farmer phone numbers are
encrypted with `FOSHOL_PHONE_KEY`; the cipher is write-only, the IV is random, and no key id
is stored on the row. Restoring `pgdata` with a *different* phone key permanently orphans
every phone number written before the change — silently, irreversibly, and invisibly to
every health check. The container refuses to boot if it finds `pgdata` without
`secrets.env` rather than quietly minting a new key.

---

## 8. Troubleshooting

| Symptom | Cause |
|---|---|
| Images/audio 404 or `ERR_NAME_NOT_RESOLVED` in the browser | `MINIO_ENDPOINT` or the 9000 publish. §4 |
| `403 SignatureDoesNotMatch` | the host fetched ≠ `MINIO_ENDPOINT` |
| `400 InvalidRequest` on an object | an `Authorization` header was sent with a presigned URL |
| Container exits during boot | read `docker logs`; the entrypoint and `start-app.sh` fail with explicit messages |
| Every diagnosis returns `UNDETERMINED` | sidecar model revision vs `model_label_map`. Check 3 in §6 |
| Cannot log in | `FOSHOL_DEMO_LOGINS` was `0` on first boot |
| `FOSHOL_DEMO_LOGINS` change refused | correct — it is fixed at first boot. Use a fresh volume |
| OOM kills | `--memory` missing or below 10 GB |
| Restart is slow | a restart should be fast; the models are baked. A slow one means the sidecar is re-converting, i.e. the baked cache was shadowed by a volume mount over `/opt/foshol/hf-cache` |

Per-service control without restarting the container:

```bash
docker exec foshol /opt/supervisor-venv/bin/supervisorctl \
  -c /etc/supervisor/supervisord.conf status
docker exec foshol /opt/supervisor-venv/bin/supervisorctl \
  -c /etc/supervisor/supervisord.conf restart sidecar
```

---

## 9. Operational shape — read before relying on this

Four processes share one container, so they share one failure domain, one log stream, one
filesystem and one memory limit. There is no independent scaling and no independent restart
beyond `supervisorctl`. Postgres and MinIO compete for the same fsync queue.

That is an acceptable trade for a demo or a single-VM deployment, and a poor one for
production. The supported topology is `docker-compose.dev.yml` — four containers, named
volumes, separate restarts — and the `app` and `sidecar` images shipped alongside this
tarball are the same ones it builds, so moving over means providing Postgres and MinIO and
setting the env vars in `.env.dev.example`. Nothing has to be rebuilt.

MinIO is redistributed inside this image under AGPL-3.0; see `/opt/foshol/NOTICE`. Database
remedy content is **DEMO ONLY** and is not production agronomy.
