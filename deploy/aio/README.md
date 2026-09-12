# All-in-one image — build sources

Build sources for a single container running the whole backend stack: Postgres 17 +
pgvector, MinIO, the inference sidecar in LIVE mode with the `visionary` vision backend,
and the Spring Boot app. Model weights are baked in, so the container needs **no network
at runtime**.

This exists for one job: handing DevOps a single artefact to run on a VM.
[`docker-compose.dev.yml`](../../docker-compose.dev.yml) remains the supported topology
and the thing to go back to — four containers, independent restarts, separate logs,
separate failure domains. Running Postgres, MinIO, a PyTorch process and a JVM in one
container is a deliberate trade of operability for handover simplicity.

## What is here

| File | Role |
|---|---|
| `Dockerfile` | 6 stages; the jar and the model bake run on the **build** platform, everything else on the target |
| `sidecar.env` | the LIVE + visionary pins, sourced **only** by `start-sidecar.sh` |
| `bake_models.py` | pre-fetches the 3 roles LIVE actually loads, converts Whisper to CTranslate2 int8, plants the tokenizer sentinel |
| `entrypoint.sh` | PID-1 pre-flight: state layout, secrets, `initdb`, role/db, MinIO host alias |
| `supervisord.conf` | 4 programs by priority + a FATAL listener that stops the container |
| `start-*.sh` | one launcher per process; `start-app.sh` gates on Postgres and MinIO |
| `healthcheck.sh` | all four services, cheapest probe first |
| `NOTICE` | bundled MinIO is AGPL-3.0 |

## Build

From the repository root — the build context is the root, not this directory:

```bash
docker buildx build -f deploy/aio/Dockerfile --platform linux/amd64 \
  --provenance=false --sbom=false -t foshol-doctor-aio:0.0.1-amd64 --load .
```

Build args:

| Arg | Default | Effect |
|---|---|---|
| `BAKE_CT2` | `1` | convert Whisper to int8 at build time. `0` ships raw weights and converts on first boot — then raise the healthcheck start period to ~1200s |
| `PRUNE_ASR_TRAINING_STATE` | `1` | truncate the upstream fine-tune's `optimizer.pt` (−1.79 GiB). `0` keeps a byte-for-byte HF cache |

The `jar` and `bake` stages are pinned to `$BUILDPLATFORM`. A bootJar has no native code
and the HF cache plus the CTranslate2 int8 model are little-endian serialised tensors, so
both are architecture-independent and are copied into the target-platform runtime. That
keeps a cross-build's emulated work down to apt and pip instead of a Gradle build and a
multi-minute tensor conversion.

## Design notes worth knowing before changing anything

**Python 3.12 is a hard floor and ceiling.** `ctranslate2==4.5.0` publishes no cp313
wheels, which is why the base is `python:3.12-slim-bookworm` and not
`pgvector/pgvector:pg17` (system Python 3.11). The `-bookworm` suffix is not decoration:
`python:3.12-slim` now resolves to Debian trixie, and the ML stack is only tested on
bookworm. Postgres comes from PGDG `bookworm-pgdg` to match.

**The sidecar's env must not leak to the JVM.** Every process in one container inherits
the container environment, and Spring relaxed binding maps `FOSHOL_AI_VISION_RICE_MODEL_ID`
onto the app's own `foshol.ai.vision.rice.model-id` — which `KnowledgeContentValidator`
checks against `model_label_map` at startup. So the LIVE pins live in `sidecar.env` and are
sourced only by `start-sidecar.sh`. Do not promote them to image `ENV`.

**The Whisper revision must match `V112__visionaryquant_label_map.sql`.** Label resolution
keys on the id and version the *sidecar reports*, and an unmapped label is a designed,
silent path to `UNDETERMINED`. A revision drift looks healthy and returns nothing useful.

**Why `ct2/tokenizer.json` is baked.** `_ensure_ct2_model` (`sidecar/app/asr.py:418`) skips
conversion only when `ct2/model.bin` **and** `ct2/tokenizer.json` exist. The Whisper repo
ships `vocab.json` + `merges.txt` and no `tokenizer.json`, and the converter emits none, so
that sentinel can never be satisfied — every boot re-runs the full int8 conversion with
`force=True`. faster-whisper then falls back to fetching `openai/whisper-tiny`'s
`tokenizer.json` through the Rust `hf-hub` crate, which ignores `HF_HUB_OFFLINE`. Baking
that exact file fixes both. **This is a workaround for a defect in the sidecar, not a fix
for it** — the sidecar is owned elsewhere and still pays this cost under compose.

**Do not parse the secrets file with `IFS='=' read`.** Bash strips a trailing IFS delimiter
from the last field, which truncates the `=` padding on a base64 value. `FOSHOL_PHONE_KEY` is
base64 of 32 bytes and always ends in `=`, so it came back 43 characters instead of 44 and
failed validation — first boot was fine (the value was still in the environment) but every
restart died. `entrypoint.sh` splits with `${line%%=*}` / `${line#*=}` for this reason.

**Models deliberately absent.** LIVE loads only `vision.rice.primary`, `asr.bangla` and
`embed.text`. `vision.rice.fallback` and `vision.solanaceae` are config-validated but never
loaded, so their weights are not downloaded. This is also why every crop routes to the rice
family in `FOSHOL_AI_VISION_CROP_ROUTES`.

**Startup coupling is real.** The app fails to boot if Postgres is unreachable (Flyway plus
`ddl-auto=validate`) or if MinIO is unreachable (`MinioObjectStore`'s constructor eagerly
calls `ensureBucket`). It boots fine without the sidecar, degrading a case to
`UNDETERMINED`. `start-app.sh` gates on the first two and deliberately not on the third.

See [`../../docker-image/README.md`](../../docker-image/README.md) for the runtime env
contract, the presigned-URL constraint and the post-deploy checks.
