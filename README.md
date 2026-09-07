# foshol-doctor

AI-triaged crop-disease diagnosis from photographs and Bangla speech. Every case is approved by a
human field officer before any advice reaches the farmer.

## One-command startup

```bash
cp .env.example .env   # fill secrets
docker compose up -d postgres minio caddy
./gradlew :app:bootRun --args='--spring.profiles.active=local'
```

Replay-mode sidecar (after `sidecar/` exists):

```bash
docker compose --profile ai up -d sidecar
```

## Demo credentials (profile `demo`)

- Farmer phone `+8801711111111`, OTP `123456` (`foshol.auth.otp.dev-code`)
- Officer username `officer`, password `password`
- Admin username `admin`, password `password`

## HTTPS for phones (`COMMON-SEC-019`)

1. Install [mkcert](https://github.com/FiloSottile/mkcert) and run `mkcert -install`.
2. `mkdir -p deploy/certs && mkcert -cert-file deploy/certs/local.pem -key-file deploy/certs/local-key.pem "$(hostname).local" 192.168.x.x localhost`
3. Install the mkcert CA on the demo phone.
4. Open `https://<lan-ip>/`.

If the venue Wi-Fi isolates clients, fall back to a tunnel (`cloudflared` or `ngrok`).

## Build

```bash
./gradlew clean build
docker compose down -v && docker compose up -d && ./gradlew integrationTest
```

Angular is a separate app under `web/`, owned by the frontend engineer. Pin: **22.1.5**.
