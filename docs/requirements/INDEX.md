# INDEX — Foshol Doctor requirement suite

**Read order for any agent:** this file → `00-common.ears.md` (in full) → your own module document.
Nothing else. If you find yourself reading a third module's document, stop: you are about to couple
to something you should be calling through a published interface.

| Document | Module | Agent | Owns |
|---|---|---|---|
| `00-common.ears.md` | — | **A1** | The frozen contract: schema, interfaces, events, config, laws |
| `10-identity.ears.md` | `identity` | **A1** | Farmer, FieldOfficer, phone auth, JWT |
| `11-intake.ears.md` | `intake` | **A2** | `DiagnosisCase`, images, audio, idempotency, case status |
| `12-analysis.ears.md` | `analysis` | **A3** | Orchestration, ports, label mapping, `ConfidenceRouter` |
| `13-knowledge.ears.md` | `knowledge` | **A4** | Crop/Disease/Symptom/Remedy, pgvector matcher, `model_label_map` |
| `14-review.ears.md` | `review` | **A5** | Officer queue, claim/approve/edit/replace/reject, `Advisory` |
| `15-notification.ears.md` | `notification` | **A5** | SSE, channel registry, delivery |
| `60-inference-sidecar.ears.md` | sidecar | **A7** | FastAPI, pretrained models, replay + live, eval harness |
| `70-frontend.ears.md` | web | **A6** | Angular: farmer, officer console, admin stats |
| `CONTENT-OWNERS.md` | — | human | Everything an agent may **not** author |
| `../adr/` | — | — | Why each contested decision went the way it did |
| `../openapi/foshol-api.yaml` | — | **A1** | The frozen API contract the Angular client generates from |

---

## 0. Requirement census

**832 requirements across nine documents.** Counted from the files, not from any summary — an ID is
counted once, where it is defined.

| Document | FR | NFR | SEC | DATA | API | UX | CON | ARCH | TEST | **Total** |
|---|---|---|---|---|---|---|---|---|---|---|
| `00-common.ears.md` | — | 38 | 14 | 17 | 4 | 1 | 3 | 18 | — | **95** |
| `10-identity.ears.md` | 20 | 3 | 14 | 7 | 6 | 1 | — | — | — | **51** |
| `11-intake.ears.md` | 45 | 6 | 8 | 10 | 1 | 2 | — | — | — | **72** |
| `12-analysis.ears.md` | 68 | 15 | 5 | 7 | 5 | — | — | — | — | **100** |
| `13-knowledge.ears.md` | 38 | 7 | 3 | 19 | 13 | 4 | — | — | — | **84** |
| `14-review.ears.md` | 63 | 10 | 5 | 5 | — | 4 | — | — | — | **87** |
| `15-notification.ears.md` | 44 | 13 | 3 | 3 | — | — | — | — | — | **63** |
| `60-inference-sidecar.ears.md` | 73 | 12 | 8 | 9 | 4 | — | — | — | — | **106** |
| `70-frontend.ears.md` | 110 | 13 | 6 | 10 | 5 | 21 | — | — | 9 | **174** |
| **All documents** | **461** | **117** | **66** | **87** | **38** | **33** | **3** | **18** | **9** | **832** |

`CON` and `ARCH` exist only in `00-common.ears.md`; `TEST` only in `70-frontend.ears.md`. The
frontend's total is the largest because three surfaces plus i18n, responsive behaviour, SSE, auth and
accessibility all land in one document.

---

## 1. Module dependency graph

```
                             ┌──────────┐
                             │  common  │   shared kernel · enums, constants, Uuid7
                             └────┬─────┘   every module may depend on it
        ┌────────────┬────────────┼────────────┬──────────────┐
        ▼            ▼                         ▼              ▼
  ┌──────────┐  ┌───────────┐            ┌──────────┐   ┌──────────────┐
  │ identity │  │ knowledge │            │ sidecar  │   │   frontend   │
  └────┬─────┘  └─────┬─────┘            └────┬─────┘   └──────┬───────┘
       │              │                       │ HTTP           │ OpenAPI
       │      ┌───────┴───────┐               │                │ only
       ▼      ▼               ▼               │                │
    ┌──────────┐        ┌──────────┐◄─────────┘                │
    │  intake  │───────▶│ analysis │                           │
    └────┬─────┘  event └────┬─────┘                           │
         │                   │ AnalysisCompleted               │
         │                   ▼                                 │
         │             ┌──────────┐                            │
         └────────────▶│  review  │                            │
             API       └────┬─────┘                            │
                            │ AdvisoryApproved                 │
                            ▼                                  │
                    ┌──────────────┐                           │
                    │ notification │◄──────────────────────────┘
                    └──────────────┘        SSE
```

Solid arrows are **published-interface calls**; the labelled ones are **asynchronous domain events**.
There is no other kind of cross-module interaction in this system. `ApplicationModules.verify()`
fails the build if one appears.

**The event arrows are not module dependencies.** Every event record — and every record an event
references — lives in the shared `common` module (`COMMON-ARCH-016`–`018`), so publishing or
consuming one is a dependency on `common` and never on the other module. This matters because the
event flow deliberately runs *against* the call graph in two places: `intake` consumes
`AnalysisCompleted` while `analysis` consumes `CaseSubmitted`, and `intake` consumes
`AdvisoryApproved` while `review` calls `CaseIntakeApi`. Had the events lived in publisher `api`
packages, both would be module cycles and the build would fail on Day 1.

### Build order

```
1. common
2. identity, knowledge            (no dependencies beyond common — fully parallel)
3. intake                          (needs identity, knowledge)
4. analysis                        (needs intake, knowledge)
5. review                          (needs identity, intake, analysis, knowledge)
6. notification                    (needs identity, review)

off the critical path entirely:
   sidecar    (depends on nothing in the JVM — an HTTP contract only)
   frontend   (depends on docs/openapi/foshol-api.yaml only)
```

---

## 2. Parallelisation plan

### 2.1 The Day-1 freeze gate — everything waits on this

`A1` works alone for the first half-day. Nothing else starts. It delivers:

- the Gradle Groovy multi-project skeleton, `buildSrc` conventions, `gradle/libs.versions.toml`
- `docker-compose.yml` (Postgres + pgvector, MinIO, sidecar) **with TLS termination for the frontend origin** (`COMMON-SEC-019`) — the farmer voice feature does not function over plain HTTP on a LAN address
- Flyway `V1`–`V9`: **the complete schema**, applying cleanly from empty
- **the frozen `api` packages** — every published interface and every event record from
  `00-common.ears.md` §6, compiling, with no implementation
- `ModularityTests` and `ArchitectureTests`, both passing against the empty skeleton
- `22.1.5` resolved and pinned (`COMMON-NFR-003`)

**Exit criterion:** `./gradlew build` green, `docker compose up` healthy, `api` packages frozen.

Frozen contracts plus the already-frozen `docs/openapi/foshol-api.yaml` are what make everything
below genuinely parallel. Skip this gate and the four-day plan does not work.

### 2.2 What runs in parallel after the gate, and what each is waiting on

| Agent | Starts | Waiting on | Contract it waits for |
|---|---|---|---|
| **A1** identity + platform | immediately | — | — |
| **A4** knowledge | immediately | — | — |
| **A7** sidecar | immediately | — | — |
| **A6** frontend | immediately | — | `docs/openapi/foshol-api.yaml` (already frozen) |
| **A2** intake | immediately | `identity` at **runtime only** | `FarmerLookupApi` signature — frozen, so A2 codes against it and stubs it in tests |
| **A3** analysis | immediately | `intake`, `knowledge` at **runtime only** | `CaseSubmitted` record, `CaseIntakeApi`, `SymptomMatchApi`, `KnowledgeQueryApi.resolveModelLabel` — all frozen |
| **A5** review + notification | immediately | `analysis` at **runtime only** | `AnalysisCompleted` record, `AnalysisApi` — frozen |

**The point:** because every signature is frozen on Day 1, **no agent is ever blocked on another
agent's implementation** — only on its own tests needing a real collaborator, which is what the
integration checkpoints are for. Compile-time coupling is zero; runtime coupling is deferred to the
daily gate.

### 2.3 Genuine serialisation points — the only four

| # | What must wait | On what | Why |
|---|---|---|---|
| 1 | Everything | The Day-1 freeze gate | Contracts and schema must exist before anyone writes a handler |
| 2 | `A3` live-mode integration test | `A7` sidecar live endpoints | The HTTP contract can be stubbed, but the live e2e cannot |
| 3 | `A4` matcher tuning, `A5` seeded advisories | **Human content delivery** (end of Day 2) | `CONTENT-OWNERS.md`. The matcher cannot be tuned against phrases that do not exist |
| 4 | `A7` `tools/seed_cases.py`, demo dress rehearsal | `A4` reference data loaded | Seeded historical cases reference real disease and remedy rows |

Serialisation point 3 is the schedule's real risk. See §6.

---

## 3. The compressed four-day schedule

Seven agent workstreams. One integration checkpoint at the end of each day, run by A1 on `main`:

```bash
./gradlew clean build && docker compose down -v && docker compose up -d && ./gradlew integrationTest
```

`main` must be demoable at every checkpoint (`COMMON-NFR-044`).

### Day 1 — Freeze the contracts, then fan out

**Morning — A1 alone.** The freeze gate (§2.1). Nothing else runs.

**Afternoon — all seven in parallel.**

| Agent | Work |
|---|---|
| **A1** | `identity`: OTP challenge, JWT issuance and filter chain, seeded officers, phone encryption. MinIO adapter behind `ImageStorePort`. |
| **A2** | `intake`: `POST /api/v1/cases`, multipart binding, magic-byte sniffing, size and count limits, the local quality gate, MinIO storage of original + derivative, `CaseSubmitted`. |
| **A3** | `analysis`: orchestrator skeleton on virtual threads, the four outbound ports, **replay adapters complete**, `ConfidenceRouter` with both thresholds and all three paths. |
| **A4** | `knowledge`: entities and read APIs; migrations `V10`–`V17` **scaffolded with `TODO(content-owner: C<n>)` markers**, no invented values. |
| **A5** | `review`: `ReviewTask` creation on `AnalysisCompleted` **and** on `AnalysisFailed`; `p_officer_queue` projection with the least-confident-first index. |
| **A6** | `web`: app shell, routing, `ng-openapi-gen` client, **both `bn.json` and `en.json` from hour one**, crop picker, capture screen. |
| **A7** | `sidecar`: FastAPI with all five endpoints, model loading by config, `GET /v1/models`, **replay mode complete**. |

**Day-1 DoD:** `docker compose up`; a photo submitted in the browser reaches the officer queue as a
`ReviewTask`, in replay mode, end to end.

### Day 2 — The image path, live

| Agent | Work |
|---|---|
| **A1** | Read-only `DataSource` qualifier, Actuator + health indicators, Resilience4j `sidecar` instance, rate limiting, CORS, `verify.yml`. |
| **A2** | Idempotency (`Idempotency-Key`, replay, 409 on body mismatch), the full case status state machine driven by listeners, `p_farmer_case_history`, presigned image URLs with the ownership check. |
| **A3** | Live HTTP adapters, **label-map resolution through `KnowledgeQueryApi.resolveModelLabel`**, multi-image `MAX` aggregation, `analysis_run` persistence, Grad-CAM capture to MinIO. |
| **A4** | pgvector HNSW kNN, the fuzzy token-overlap layer, the weighted `disease_symptom` scorer, both startup validations (`COMMON-DATA-014`, `-015`). |
| **A5** | Claim / release / approve / edit / replace / reject, `Advisory` with versioning, `AdvisoryApproved`. |
| **A6** | Farmer capture → progress → result against the real API; officer queue table; case detail read-only. |
| **A7** | Live vision + `/v1/vision/explain`; `tools/build_fixtures.py` records the fixture set. |

**Content deadline: end of Day 2.** All 14 diseases with remedies, `phi_days`, `source_ref` and
Bangla symptom phrases. *(The plan put this on Day 3 of 6; at four days it must land a day earlier or
serialisation point 3 eats Day 3.)*

**Day-2 DoD:** photo in → ranked diagnosis out, live, in the browser. **Screen-record it.** That
recording is the insurance policy.

### Day 3 — Voice, the fallback path, and the loop closed

| Agent | Work |
|---|---|
| **A1** | Security pass: no PII in logs, role checks on every endpoint, secrets out of the repo, `verify-live.yml` with the Hugging Face cache. |
| **A2** | Rejection → `parent_case_id` resubmission flow. |
| **A3** | ASR + embed branch, symptom fusion, the `SECONDARY` merge and re-rank, deadline abandonment, circuit-breaker fallback to `UNDETERMINED`. |
| **A4** | Matcher tuned against the delivered phrase set; `inconclusive` behaviour verified. |
| **A5** | `notification`: SSE fan-out, channel registry, `WebPushChannel` and `SmsChannel` as real disabled channels. |
| **A6** | Voice recording with waveform, transcript + symptom chips, **confidence bars with both thresholds drawn**, Grad-CAM overlay toggle, decision-path and REPLAY/LIVE badges, advisory card, admin stats page. |
| **A7** | `tools/eval.py` → `docs/eval-report.md`; `tools/seed_cases.py` → 40–60 historical cases. |

**Day-3 DoD:** voice-only submission produces symptoms and a KB-matched shortlist; approving a case
delivers an SSE notification the farmer sees; all three decision paths reproducible on demand.

### Day 4 — Harden, freeze, rehearse

**Morning.** **The mobile device pass first** (`WEB-TEST-009`): the farmer capture path on one
Android Chrome and one iOS Safari phone, over the HTTPS origin. It is first because it is the only
requirement no agent can discharge, and because a failure here removes feature F4 from the demo.
Then the failure drills, each recorded: sidecar killed mid-case (→ `UNDETERMINED`, workflow
continues), MinIO down (→ `503`, no partial case), oversized upload, corrupt file, non-crop photo,
silent audio, claim expiry and re-queue. Seed data loaded so no screen is empty. Responsive check at
360 / 768 / 1280 px. Accessibility floor. `docs/eval-report.md` finalised.

**Feature freeze 13:00. Day 4 afternoon is not a coding afternoon.**

**Afternoon.** Three timed dress rehearsals on the actual demo machine and network. Deck. Artefacts:
README with one-command startup, ADRs, OpenAPI, the Modulith-generated module diagram, the eval
report. Buffer — something will break.

### Per-day demoable milestone

| Day | What you can show a judge that evening |
|---|---|
| 1 | A photo submitted in a browser appears in the officer queue |
| 2 | A photo produces a live ranked diagnosis with a Grad-CAM overlay |
| 3 | The full loop: voice → symptoms → shortlist → officer approves → farmer's screen updates |
| 4 | The rehearsed six-minute demo, including the live sidecar kill |

---

## 4. The demo script as acceptance criteria

The whole build serves these eight beats. Each is stated as a test.

| # | Beat | Acceptance criterion | Requirements |
|---|---|---|---|
| 1 | Clear rice leaf → high-confidence diagnosis in ~2 s, Grad-CAM shows the lesions | **Given** an image whose top-1 ≥ `foshol.analysis.confidence.high`, **when** submitted, **then** `decision_path = PRIMARY`, a remedy is prefilled from the KB, and `analysis_run.gradcam_object_key` is non-null | `ANALYSIS` router + Grad-CAM, `KNOWLEDGE` remedy lookup, `WEB` overlay |
| 2 | Ambiguous leaf → lands between the thresholds → **farmer speaks Bangla into a phone** → transcript and symptom chips → KB re-ranked shortlist | **Given** an image whose top-1 is in `[low, high)` and audio containing known symptom phrases, **when** submitted, **then** `decision_path = SECONDARY`, `case_symptom` rows exist with `source = SPEECH`, and `case_candidate` rows exist with `source = MERGED` | `ANALYSIS` fusion, `KNOWLEDGE` matcher, `WEB` chips + bars |
| 3 | Blurry or non-crop photo → rejected locally with a Bangla re-capture prompt | **Given** an image below `foshol.intake.quality.blur-variance-min`, **when** submitted, **then** `422` with a `QualityGateProblem`, **and nothing is written to MinIO or the database** | `INTAKE` quality gate |
| 4 | Officer console: both cases queued least-confident first; officer opens case 2, listens, **edits the remedy**, approves | **Given** two pending tasks, **when** the queue is listed, **then** the lower `top_confidence` is first; **when** the officer publishes with `action = EDITED`, **then** an `advisory` row exists at version 1 with the edited remedy set | `REVIEW` ordering + publish |
| 5 | Farmer tab: notification arrives, advisory shown with the officer's name and a "verified" stamp | **Given** a published advisory, **when** the farmer's stream is open, **then** an `ADVISORY_PUBLISHED` SSE event arrives within 1 s carrying `officerName` | `NOTIFY` SSE, `WEB` advisory card |
| 6 | Admin strip: model-vs-officer agreement rate | **Given** published advisories, **when** `/api/v1/admin/stats` is called, **then** `modelOfficerAgreementRate` equals the share whose `diseaseId` matches the top `MODEL` candidate | `REVIEW` stats |
| 7 | Architecture: module diagram, open `SmsChannel`, the AMQP one-liner, the read-only DataSource qualifier | **Given** the build, **when** `ModularityTests` runs, **then** it passes and emits the module diagram; `SmsChannel` and `WebPushChannel` exist as registered disabled channels | `COMMON-ARCH-001`, `NOTIFY` channels |
| 8 | **Kill the sidecar live** → the case still reaches the officer as `UNDETERMINED` | **Given** the sidecar is stopped, **when** a case is submitted, **then** `decision_path = UNDETERMINED`, `analysis_run.error_code` is set, `AnalysisCompleted` is still published, and a `ReviewTask` is created | `COMMON-NFR-035`, `REVIEW` task-on-every-path invariant |

Beat 8 is the one to rehearse most. It is the only beat that proves the architecture rather than
describing it.

---

## 5. What was cut, and why

Nothing disappeared silently. Every cut below is either a `[DEFERRED]` requirement that still appears
in its module document with the seam named, or — for the two training tasks — a deliberate total
removal.

| Cut | Reason | Where the seam remains |
|---|---|---|
| **Model training: the Dhan-Shomadhan fine-tune** | **No training infrastructure.** Removed **entirely**, not deferred — a `[DEFERRED]` training requirement is exactly the thing an autonomous agent would attempt. Rice is covered by pretrained weights. | Nowhere. `COMMON-CON-001` forbids it explicitly. ADR-0008 |
| **Model selection: the 20-clip ASR bake-off** | Same. The ASR model is pinned once; swapping it is a config change. | `foshol.ai.asr.model-id`. ADR-0008 |
| Admin CRUD UI **and** admin write endpoints | Highest cost, lowest demo value. Content is seeded by migration instead. | `[DEFERRED]` endpoint block in `13-knowledge.ears.md`; read-only stats page survives |
| VLM visual-descriptor path | Needs a GPU. Not load-bearing for any demo beat. | `case_symptom.source = VISION` is defined and never written; `[DEFERRED]` block in `12-analysis.ears.md` |
| Bangla TTS | Browser voice support is inconsistent and unverifiable ahead of the demo machine. | `[DEFERRED]` in `70-frontend.ears.md` |
| k6 load test | Four days does not buy a load-test story worth telling. Honest silence beats a thin number. | None |
| Admin-visible audit log | Audit columns are written; the UI to read them is not. | `created_by` / `updated_by` populated on knowledge entities, `advisory`, `review_task` |
| Web Push (VAPID + service worker) | HTTPS and permission friction on the demo machine. SSE carries the beat. | `WebPushChannel` ships as a **real registered channel** behind `foshol.channels.webpush.enabled=false` |
| Confidence calibration | Needs a calibration set that will not exist in time. | `foshol.analysis.confidence.temperature=1.0` |
| District-based officer routing | One officer in the demo; a shared claim pool is simpler and safer. | `OfficerLookupApi.findActiveByDistrict` exists, unused |
| Web Speech API browser fallback | The always-available text box already covers the degraded path. | `[DEFERRED]` in `70-frontend.ears.md` |
| Cloud deployment | The live sidecar-kill demo is more reliable locally. | `docker-compose.yml` |

### Added back, against the plan

| Added | Why |
|---|---|
| **Held-out evaluation harness** (`tools/eval.py`, ≥100 images per crop, `docs/eval-report.md`) | Because no published model-card accuracy figure may be quoted (`COMMON-CON-002`, ADR-0011), this is now the **only** source of an accuracy number. It is not optional. |
| **`model_label_map` table** | Three pretrained models with three disjoint label spaces; the plan's single column cannot express that. ADR-0009 |
| **Live mode exercised in CI** (`verify-live.yml`) | A system that only ever runs in replay is not a system. ADR-0010 |

### Test floor, budgeted honestly

Per module: unit tests on domain invariants and on **every** command and query handler; **exactly
one** Testcontainers integration test covering the module's primary happy path. Once globally:
`ApplicationModules.verify()`, the ArchUnit rule set, and one end-to-end slice test run in **both**
replay and live mode.

**What this deliberately leaves unverified:** per-endpoint HTTP behaviour beyond the slice test,
error-path integration behaviour, concurrent claim contention under real load, and the projection
listeners under event replay. Accepted knowingly. See ADR-0015.

---

## 6. Risks

| Risk | Mitigation | Cut trigger |
|---|---|---|
| **Content curation slips past end of Day 2** — the schedule's single largest risk, since the matcher, the seeded cases and the officer's remedy editor all depend on it | Named owner per row in `CONTENT-OWNERS.md`; migration scaffolds with `TODO(content-owner)` markers land Day 1 so only values are missing; a rice-only subset unblocks the demo | Ship **8 diseases fully sourced rather than 14 half-sourced**, and demo rice only |
| Pretrained classifier weak on real field photographs — a documented weakness of PlantVillage-derived models | Two-threshold routing + KB fallback + mandatory officer approval; own the limitation on stage before a judge finds it | If own-eval top-3 < 60% on Day 3, reframe the UI as "differential diagnosis" and lead with the workflow, not the model |
| Bangla ASR poor on demo-room audio | Loudness normalisation, 16 kHz mono capture, always-available text box, pre-recorded clean audio held in reserve | Demo with pre-recorded audio and **say so** |
| Live mode too slow on CPU for a live demo | Replay is the demo default; live is proven separately in `verify-live.yml` | Demo entirely in replay; show the live CI run as evidence instead |
| Frontend is the bottleneck — one agent, three surfaces | Farmer and officer are must-have; admin is already reduced to one read-only page | Cut the admin page; read the agreement rate from the API in a terminal |
| Agent-generated code compiles but is wrong | Verification gate per run: compile → unit → `ApplicationModules.verify()` → ArchUnit → integration | Drop to agent-assisted rather than agent-led for the failing module |
| Two agents collide on a shared file | `00-common` §12.1 ownership table; `api` packages and migrations frozen after Day 0 | A1 arbitrates; the offending change is reverted, not merged |
| An agent invents agronomic content to unblock itself | `COMMON-CON-003`, `CONTENT-OWNERS.md`, and a grep for `TODO(content-owner)` at every checkpoint | Any invented dosage or PHI value found → revert that migration wholesale, no exceptions |
| **Mobile microphone unavailable at the venue** — an insecure origin, a client-isolated guest Wi-Fi, or an untrusted certificate on the phone. Any one of these removes feature F4 entirely | HTTPS via a locally trusted certificate (`COMMON-SEC-019`); the CA installed on the demo phone as a documented setup step; `WEB-FR-137` reports an insecure origin as a diagnostic instead of a dead button; device pass on Day 4 morning | Demo the voice path on the laptop over `localhost` and say so, or fall back to the tunnel; the always-available Bangla text box (`WEB-FR-140`) keeps the SECONDARY path demoable with no microphone at all |
| **iPhone recordings rejected `415`** — Safari emits `audio/mp4`, which the original allowed-types list excluded | `audio/mp4` accepted end to end (`COMMON` §9.1, `INTAKE-FR-004`, `SIDECAR-FR-041a`); container negotiated by `MediaRecorder.isTypeSupported` (`WEB-FR-138`) | Demo on Android only |
| SSE connection dropped by a proxy on the demo network | Heartbeat every `foshol.channels.sse.heartbeat`; client reconnects with backoff | Poll the case endpoint every 3 s as a visible fallback |
