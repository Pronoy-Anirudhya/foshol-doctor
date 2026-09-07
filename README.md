# foshol-doctor

AI-triaged crop-disease diagnosis from photographs and Bangla speech. Every case is approved by a
human field officer before any advice reaches the farmer.

## One-command startup

```bash
cp .env.example .env   # fill secrets
docker compose up -d postgres minio caddy
./gradlew :app:bootRun --args='--spring.profiles.active=local'
```

First boot applies Flyway `V1`–`V18` (schema + DEMO knowledge base) and, on `local` and `demo`, the V100 farmer/officer personas. If this machine already applied V1–V17 against `./.data/postgres`, reset once so `V18` can run:

```bash
docker compose down
rm -rf .data
docker compose up -d postgres minio caddy
```

Postgres and MinIO write to `./.data/` on the host (not Docker named volumes). Cases, Flyway history and uploaded objects survive `docker compose stop`, `docker compose down`, `docker compose down -v`, and quitting Docker Desktop. To wipe and start empty:

```bash
docker compose down
rm -rf .data
```

## Demo (replay, dress rehearsal)

Replay mode does not need the sidecar. Use the `demo` profile:

```bash
./gradlew :app:bootRun --args='--spring.profiles.active=demo'
```

Capture files for the eight-beat script are under `docs/demo/`:

| Beat | File | Expected path |
|---|---|---|
| 1 PRIMARY rice blast | `docs/demo/images/01-rice-blast-primary.jpg` | top-1 ≥ 0.75, Grad-CAM fixture, brown-spot/blast remedies prefilled |
| 2 SECONDARY | `docs/demo/images/02-rice-brown-spot-ambiguous.jpg` plus `docs/demo/audio/secondary-brown-spot.wav` | top-1 in `[0.45, 0.75)`; transcript matches brown-spot phrases |
| 3 quality gate | `docs/demo/images/07-blurry-reject.jpg` | `422` QualityGateProblem, nothing in MinIO |
| extras | `03` brown spot PRIMARY, `04` tomato early blight, `05` potato late blight, `06` rice healthy | routing coverage |

The WAV is a 16 kHz tone; replay ASR substitutes the Bangla transcript in `docs/demo/manifest.json`. Photographs are Wikimedia Commons stills (USDA-ARS / Bugwood / CC-licensed field photos), resized for the repo. **Every remedy row is labelled DEMO ONLY** — not production advice.

Credentials (also seeded on `local`):

- Farmer phone `+8801711111111`, OTP `123456` (`foshol.auth.otp.dev-code`)
- Officer username `officer`, password `password`
- Admin username `admin`, password `password`

Replay-mode sidecar (optional; Spring `demo` profile already replays from classpath fixtures):

```bash
docker compose --profile ai up -d sidecar
```

## HTTPS for phones (`COMMON-SEC-019`)

1. Install [mkcert](https://github.com/FiloSottile/mkcert) and run `mkcert -install`.
2. `mkdir -p deploy/certs && mkcert -cert-file deploy/certs/local.pem -key-file deploy/certs/local-key.pem "$(hostname).local" 192.168.x.x localhost`
3. Install the mkcert CA on the demo phone.
4. Open `https://<lan-ip>/`.

If the venue Wi-Fi isolates clients, fall back to a tunnel (`cloudflared` or `ngrok`).

## Build

```bash
./gradlew clean build
docker compose down && rm -rf .data && docker compose up -d && ./gradlew integrationTest
```

Do not rely on `docker compose down -v` to reset this stack: bind mounts are not Compose volumes, so `-v` will not delete `./.data`.

## Terminal E2E (no frontend)

```bash
cd /path/to/foshol-doctor
./start-stack.sh                # docker postgres+minio, then Spring on :8080
# another terminal, same directory:
./call-api.sh                   # numbered menu; POSTs send predefined demo JSON
```

macOS zsh does not search the current folder. Use `./start-stack.sh`, not `start-stack.sh`. `bash tools/start-stack.sh` also works.

`start-stack.sh` uses profiles `local,demo` (replay, seeded farmer/officer). Live sidecar: `FOSHOL_SPRING_PROFILES=local ./start-stack.sh`.

Suggested `call-api.sh` order: 1 → 2 → 3 → 4 → 7 → 12 → 16 → 21 → 23 → 25 → 28 → 30.
