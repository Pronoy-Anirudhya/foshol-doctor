# Foshol Doctor

AI-triaged crop-disease diagnosis from photographs and Bangla speech. The product is the
human-in-the-loop: **every case is approved by a field officer before any advice reaches the
farmer.** The model only accelerates triage.

Three crops (rice, tomato, potato), a Spring Modulith API, a Python inference sidecar, and a
separate Angular frontend (not in this repository; pin **22.1.5**). This README is for running the
**API and data plane locally**.

The sentence the system exists to make true:

> No farmer in this system has ever received unverified pesticide advice.

Remedy rows in the database are labelled **DEMO ONLY**. They are not production agronomy.

## What you run

| Piece | Role | Default local address |
|---|---|---|
| Postgres 17 + pgvector | Cases, knowledge, identities, Flyway | host **5433** → container 5432 |
| MinIO | Case photos and audio (`foshol-cases`) | **9000** (API), **9001** (console) |
| Spring Boot (`:app:bootRun`) | HTTP API | **8080** |
| Inference sidecar | Optional live vision / ASR / embeddings | **8000** (`docker compose --profile ai`) |
| Caddy | Optional HTTPS for phones | **80** / **443** |

Postgres and MinIO persist under `./.data/` on the host. `docker compose down` and `docker compose
down -v` do **not** wipe that directory.

`./tools/start-stack.sh` is the supported local path: it starts Postgres and MinIO, creates `.env` if
missing, boots Spring with profiles `local,demo`, and waits until `/actuator/health` is UP.

## Prerequisites

- Docker Desktop (or equivalent) running
- JDK 25 (same as `./gradlew`)
- `curl` (used by the stack script and the API client)
- macOS / zsh: always invoke scripts with `./` (`./tools/start-stack.sh`, not `start-stack.sh`)

## Seed data

Flyway runs on first boot of an empty `./.data/postgres`.

**Always applied** (`classpath:db/migration`):

- Schema for identity, intake, analysis, knowledge, review and notification
- Reference knowledge: three crops, 14 disease classes, symptoms, phrases, weights, remedies, label
  map (`V10`–`V18`)
- Modulith `event_publication` tables (`V102`)

**Applied on `local` and `demo` only** (`classpath:db/seed`):

| Migration | What it is |
|---|---|
| `V100__seed_demo_identities.sql` | Fictional farmer, officer and admin. Not agronomic content. |
| `V101__seed_historical_cases.sql` | Placeholder (`SELECT 1`). Historical case rows are not loaded yet. |

`./tools/start-stack.sh` uses `local,demo`, so V100 runs. Login as:

| Role | How |
|---|---|
| Farmer | Phone `+8801711111111`, OTP `123456` (`foshol.auth.otp.dev-code`) |
| Officer | Username `officer`, password `password` |
| Admin | Username `admin`, password `password` |

If this machine already has an older Flyway history in `./.data/postgres` that cannot apply a new
migration, wipe data (see below) and start again. Do not hand-edit Flyway’s schema history.

## Guided usage — test locally with the scripts

The API client (`./tools/call-api.sh`) is a numbered menu. After each call it stays open (Enter returns
to the menu; `q` quits). It remembers tokens and IDs in `tools/.run/api-session.json`. Auth calls
1–4 need no bearer; later calls attach the token harvested from login.

### 1. Start the stack

```bash
cd /path/to/foshol-doctor
./tools/start-stack.sh
```

Leave this terminal running. It tails `tools/.run/app.log`. **Ctrl-C detaches** (Docker and the
Java process keep running). To stop the API later: `kill $(cat tools/.run/app.pid)` (or the
`FosholDoctorApplication` process).

Wait until you see `stack is up` and `{"status":"UP"}`. First boot compiles Gradle and applies
Flyway; later boots are faster.

If MinIO was created with different keys than `.env`, the script resets `./.data/minio` and
recreates the `foshol-cases` bucket.

Live sidecar instead of replay fixtures (API client **and** Angular UI):

```bash
docker compose --profile ai up -d sidecar
FOSHOL_SPRING_PROFILES=local ./tools/start-stack.sh
```

`application-local` sets `foshol.ai.mode=live` and still loads `db/seed` (farmer / officer / admin).
Do **not** combine with the `demo` profile: `demo` sets `foshol.ai.mode=replay` and would win.

**Angular / WEB-FR-112.** The farmer UI re-encodes every photo (GPS strip). Replay keys vision by
SHA-256 of the **uploaded** bytes, so a browser capture of `docs/demo/images/01-rice-blast-primary.jpg`
will miss the fixture and land `UNDETERMINED` (still queued for an officer — demo beat 8). Byte-exact
uploads from `./tools/call-api.sh` on the default `local,demo` stack still hit PRIMARY in replay.
For PRIMARY (or SECONDARY) **from the UI**, run live sidecar as above.

### 2. Open the API client

In another terminal, same directory:

```bash
./tools/call-api.sh
```

Quit only with `q`.

### 3. Smoke path (auth, knowledge, submit a case)

Run in order. Empty Enter after each result.

| Step | Menu | What you should see |
|---|---|---|
| OTP | **1** | HTTP **202**, `DEV_FIXED` |
| Verify | **2** | HTTP **200**, farmer JWT stored |
| Crops | **7** | HTTP **200**, rice / tomato / potato |
| Submit | **12** | HTTP **202**, `status: SUBMITTED`, a `caseId` |

**12** uploads `docs/demo/images/01-rice-blast-primary.jpg` for the seeded rice crop with a fresh
`Idempotency-Key`.

Analysis is asynchronous. After **12**, wait a few seconds (watch `tools/.run/app.log`) before the
review queue fills.

### 4. Officer path (optional, full loop)

Suggested continuation:

**3** (officer login) → **16** (case detail) → **21** (analysis) → **23** (queue) → **25** (claim) →
**28** (approve) → **30** (advisory as farmer).

Use **13** for photo + audio, **14** for the blurry image (expect **422**, nothing stored). **36**
is `/actuator/health` with no auth.

If **21** or **23** is empty, analysis has not finished or failed; check the log rather than
retrying **12** blindly (each **12** is a new case).

## Demo capture files

Under `docs/demo/` for the dress-rehearsal beats. Replay ASR uses the Bangla transcript in
`docs/demo/manifest.json` (the WAV is a 16 kHz tone). Photographs are Wikimedia Commons stills,
resized for the repo.

| Beat | Files | Intended behaviour |
|---|---|---|
| PRIMARY | `images/01-rice-blast-primary.jpg` | High-confidence rice-blast path (menu **12**) |
| SECONDARY | `images/02-rice-brown-spot-ambiguous.jpg`, `audio/secondary-brown-spot.wav` | Ambiguous photo + speech (menu **13**) |
| Quality gate | `images/07-blurry-reject.jpg` | Rejected before storage (menu **14**) |
| Extras | `03`–`06` | Other crops / healthy / brown-spot coverage |

Optional replay sidecar (Spring `demo` already replays from classpath fixtures):

```bash
docker compose --profile ai up -d sidecar
```

## Secrets and wipe

`./tools/start-stack.sh` writes `.env` on first run (`FOSHOL_JWT_SECRET`, `FOSHOL_PHONE_KEY`,
`FOSHOL_DB_PASSWORD`, MinIO keys). You can instead `cp .env.example .env` and fill values. Do not
commit `.env`.

Wipe Postgres + MinIO on disk:

```bash
docker compose down
rm -rf .data
```

Then `./tools/start-stack.sh` again so Flyway and the `foshol-cases` bucket are recreated.

## HTTPS for phones (`COMMON-SEC-019`)

1. Install [mkcert](https://github.com/FiloSottile/mkcert) and run `mkcert -install`.
2. `mkdir -p deploy/certs && mkcert -cert-file deploy/certs/local.pem -key-file deploy/certs/local-key.pem "$(hostname).local" 192.168.x.x localhost`
3. Install the mkcert CA on the demo phone.
4. `docker compose up -d caddy` and open `https://<lan-ip>/`.

If venue Wi-Fi isolates clients, use a tunnel (`cloudflared` or `ngrok`).

## Build

```bash
./gradlew clean build
```

Integration tests need Postgres and MinIO (and a wipe if schema is stale):

```bash
docker compose down
rm -rf .data
docker compose up -d postgres minio
./gradlew integrationTest
```
