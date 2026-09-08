# 00 — Common Contract (EARS)

**Status:** FROZEN at Day 0. **Owner:** platform agent (A1) only.
**Audience:** every agent, human or otherwise, working on Foshol Doctor.

> **Read this first, read it whole, and treat it as immutable.**
> If your module document disagrees with this file, this file wins.
> If something you need is not here, it does not exist — raise a blocker, do not invent it.
> Traces to `fasol-doctor-plan-v2-revised-scope.md` (hereafter "the plan"). Requirements marked
> `[DERIVED]` extend the plan and carry inline reasoning.

---

## 1. Product context

Foshol Doctor is a crop-disease advisory system for Bangladeshi farmers covering **three crops**
(rice, tomato, potato) and **14 disease classes**. A farmer submits 1–3 photographs of an affected
plant, optionally with a spoken description in Bangla. The system classifies the images, transcribes
and mines the speech for symptoms, and routes the case down one of three decision paths based on
model confidence. **Every case, on every path, without exception, is reviewed by a human field
officer before any advice reaches the farmer.**

The human-in-the-loop approval workflow is the product. The AI is a triage accelerator. The single
sentence the whole system exists to make true:

> **No farmer in this system has ever received unverified pesticide advice.**

### 1.1 Scoped features

| # | Feature |
|---|---|
| F1 | Responsive web interface for farmer and field officer |
| F2 | Farmer uploads 1–3 crop images per case |
| F3 | Image → disease classification with a confidence score |
| F4 | Bangla speech → text → symptom extraction |
| F5 | Confidence below threshold → knowledge-base symptom matching |
| F6 | Knowledge base scoped to 3 crops / 14 disease classes |
| F7 | **Every** case enters the field-officer approval workflow |

### 1.2 The 4-day scope boundary

This build has **four days** and **seven autonomous agent workstreams**. The plan was written for six
days and three human engineers. It has been re-fitted by removing work.

**In scope, non-negotiable:** F1–F7 · two-threshold routing with all three decision paths
(`PRIMARY` / `SECONDARY` / `UNDETERMINED`) · officer approval on every case · Bangla ASR · knowledge-base
symptom fallback · SSE notification · Grad-CAM overlay · confidence bars showing both thresholds ·
officer queue sorted least-confident-first · model-vs-officer agreement rate · the live
sidecar-kill degradation demo · `SmsChannel` and `WebPushChannel` stubs.

**Explicitly out of scope.** Each entry names the seam it plugs into so the deferral is visible in
code, not just on a slide.

| Deferred | One-line reason | Seam |
|---|---|---|
| Admin CRUD UI and admin write endpoints | Highest-cost / lowest-demo-value surface; content is seeded by migration instead | `KnowledgeQueryApi` is read-only; write endpoints exist as `[DEFERRED]` requirements in `13-knowledge.ears.md` |
| VLM visual-descriptor path | Needs a GPU we do not have; not load-bearing for any demo beat | `case_symptom.source = VISION` is defined but never written; `12-analysis.ears.md` §4 `[DEFERRED]` block |
| Bangla text-to-speech | Browser voice support is inconsistent and unverifiable ahead of the demo machine | Frontend only; `70-frontend.ears.md` `[DEFERRED]` |
| k6 load test | Four days does not buy a load-test story worth telling | None required |
| Admin-visible audit log | Audit columns are written; the UI to read them is not | `created_by` / `updated_by` columns exist and are populated |
| Web Push (VAPID + service worker) | HTTPS and permission friction on the demo machine; SSE carries the demo beat | `WebPushChannel` ships as a real registered `NotificationChannel` behind a disabled flag |
| SMS / USSD channel | Out of scope in the plan; kept as extensibility proof | `SmsChannel` ships as a real registered `NotificationChannel` behind a disabled flag |
| Confidence calibration | Needs a calibration set we will not have in time | `foshol.analysis.confidence.temperature` exists and defaults to `1.0` |
| District-based officer routing | **In scope.** Officers and per-district admins see only their own district. Geography is identity-owned (`geo_division` / `geo_district`); cases snapshot the farmer's codes. There is no national admin. | `REVIEW-FR-048`; `FarmerView.divisionCode`; queue and SSE filter by `district_code` |
| Cloud deployment | The live sidecar-kill demo is far more reliable on a local machine. **Local now means HTTPS on the LAN, not plain `http://localhost`** — see `COMMON-SEC-018` | `docker-compose.yml` is the only deployment artefact |
| Offline / on-device inference | Out of scope in the plan | `VisionModelPort` is an interface; today's adapter is HTTP |
| Marketplace, weather alerts, outbreak analytics, multi-district scaling | Out of scope in the plan | Not represented in code |

### 1.3 Prohibited work — read this before you write anything

`COMMON-CON-001` **THE system SHALL contain no model training, fine-tuning, distillation, quantisation
or model-selection-benchmark ("bake-off") task in any form.**

There is **no training infrastructure** for this project. Any instruction in the plan
(`fasol-doctor-plan-v2-revised-scope.md` §6 and §7.1) to fine-tune on Dhan-Shomadhan, to train a ViT
or MobileNet, or to run a 20-clip ASR WER bake-off is **struck and must not be reintroduced**, not as
a requirement, not as a stretch goal, not as a `[DEFERRED]` item, and not as a comment. All models
are pretrained weights loaded by identifier from configuration (§2.4).

`COMMON-CON-002` **THE system SHALL NOT state, display, log or document any accuracy, WER, F1 or
precision figure that originates from a published model card, paper or third-party benchmark.**

The only accuracy numbers that may appear anywhere — product, deck, README, requirements — are those
produced by the project's own held-out evaluation (`SIDECAR-FR-090`, `docs/eval-report.md`).

`COMMON-CON-003` **THE system SHALL NOT contain agronomic content authored by an agent.**

Disease definitions, remedy text, dosages, pre-harvest intervals, `source_ref` citations, Bangla
symptom phrases, and both confidence thresholds are **human-supplied**. See
`docs/requirements/CONTENT-OWNERS.md`. An agent may specify the *structure, source and validation* of
this content and may write the migration *scaffold* with `TODO(content-owner)` markers; it may not
write the values. Fictional farmer and officer personas for demo seed data are exempt — they are not
agronomic content.

---

## 2. Technology baseline

Every version below is **pinned**. An agent that needs a library not listed here must raise a blocker
rather than choose one. Only agent A1 may add to `gradle/libs.versions.toml`.

### 2.1 Backend

| Concern | Choice | Version |
|---|---|---|
| Language | Java | 25 (toolchain; **no preview features**) |
| Framework | Spring Boot | 4.1.1 |
| Modularity | Spring Modulith | 2.1.1 |
| Build | Gradle, **Groovy DSL**, multi-project | 9.7.1 (wrapper, already present) |
| Database | PostgreSQL + pgvector | 17 / `pgvector/pgvector:pg17` |
| Object store | MinIO | `minio/minio:RELEASE.2025-04-22T22-12-26Z` |
| Migrations | Flyway | Boot-managed |
| Persistence | Spring Data JPA + Hibernate | Boot-managed |
| Vector types | `com.pgvector:pgvector` | 0.1.6 |
| UUIDv7 | `com.github.f4b6a3:uuid-creator` | 6.1.1 |
| Resilience | Resilience4j (Spring Boot 3 starter) | 2.3.0 |
| HTTP client | Spring `RestClient` + declarative HTTP interfaces | Boot-managed |
| API docs | springdoc-openapi | 2.8.6 |
| Mapping | **Hand-written static mappers.** No MapStruct, no ModelMapper. | — |
| Validation | `spring-boot-starter-validation` (Jakarta Bean Validation) | Boot-managed |
| Testing | JUnit 5, AssertJ, Mockito, Testcontainers, `spring-modulith-starter-test`, ArchUnit | Boot-managed / ArchUnit 1.3.0 / Testcontainers 1.20.6 |
| JWT | `com.auth0:java-jwt` | 4.5.0 |

`COMMON-NFR-001` **THE build SHALL declare every dependency version in `gradle/libs.versions.toml`
and SHALL NOT contain a version literal in any `build.gradle`.**

`COMMON-NFR-002` **THE build SHALL NOT enable Java preview features.** *(`--enable-preview` is
forbidden on compile, test and run. This rules out `java.util.concurrent.StructuredTaskScope`, which
is a preview API on JDK 25 — see `ANALYSIS-FR-020` for the required concurrency approach.)*

> **Deviation from the generation prompt, recorded deliberately.** The prompt specifies "Gradle 9
> Kotlin DSL". The project uses the **Groovy DSL** by user decision (ADR-0016). The existing
> `build.gradle` is Groovy; converting it buys nothing on a four-day build. **Do not "fix" this.**

### 2.2 Frontend

| Concern | Choice |
|---|---|
| Framework | **Angular**, standalone components, exact pin (§2.3) |
| State | **Angular signals.** No NgRx, no RxJS store, no third-party state library. |
| Styling | Tailwind CSS 4.x |
| i18n | **ngx-translate** — runtime BN/EN toggle. **Not** `$localize`, which is build-time and would need two builds. |
| API client | **`ng-openapi-gen`**, generated from `docs/openapi/foshol-api.yaml` |
| Build | Angular CLI (esbuild) |
| Charts | None. Confidence bars are CSS. |
| Service worker | `[DEFERRED]` — not installed (Web Push is out of scope) |

> **Deviation from the plan, recorded deliberately.** Plan §8 specifies React + Vite. The project
> uses **Angular** by user decision (ADR-0004).

### 2.3 `22.1.5`

`COMMON-NFR-003` **WHEN agent A1 initialises the frontend project, THE platform agent SHALL replace
every occurrence of the token `22.1.5` in this document with the exact Angular
version resolved by `ng version`, and SHALL pin that version in `web/package.json` with no `^` or
`~` range prefix on any `@angular/*` package.**

`COMMON-NFR-004` **IF the token `22.1.5` still appears in this document, THEN
THE frontend agent (A6) SHALL treat itself as blocked and SHALL NOT begin implementation.**

### 2.4 Inference sidecar and models

One Python FastAPI container. **All weights are pretrained and loaded by identifier from
configuration.** Nothing is trained (`COMMON-CON-001`).

| Concern | Choice | Version |
|---|---|---|
| Language | Python | 3.12 |
| Framework | FastAPI + Uvicorn | 0.115.x / 0.34.x |
| Vision runtime | `transformers` + `torch` (CPU) | 4.51.x / 2.6.x CPU wheels |
| ASR runtime | `faster-whisper` (CTranslate2) | 1.1.x |
| Embeddings | `sentence-transformers` | 3.4.x |
| Image ops | `Pillow`, `opencv-python-headless`, `numpy` | — |
| Dependency pinning | `requirements.txt` with `==` on every line | — |

| Role | Model identifier | Id property | Revision property |
|---|---|---|---|
| Rice vision, primary | `kssrikar4/Rice-Leaf-Disease-Classification` (Swin-Tiny, 6 classes) | `foshol.ai.vision.rice.model-id` | `.model-revision` |
| Rice vision, fallback | `prithivMLmods/Rice-Leaf-Disease` (SigLIP2, 5 classes) | `foshol.ai.vision.rice.fallback-model-id` | `.fallback-model-revision` |
| Tomato + potato vision | `Daksh159/plant-disease-mobilenetv2` | `foshol.ai.vision.solanaceae.model-id` | `.model-revision` |
| Bangla ASR | `ashrafulparan/whisper-small-bangla` | `foshol.ai.asr.model-id` | `.model-revision` |
| Text embedding (768-d) | `sentence-transformers/LaBSE` | `foshol.ai.embed.model-id` | `.model-revision` |

`COMMON-NFR-005a` `[DERIVED]` **THE system SHALL pin every model to an immutable Hugging Face
revision (a commit SHA, never a branch name), and THE sidecar SHALL report that revision as the
`model_version` in every inference response.** *(`model_label_map` is keyed on
`(model_id, model_version, raw_label)`. If the revision floats, a silent upstream re-upload changes
the label space underneath a mapping that still validates at startup — the worst failure mode this
schema has. The revision values are resolved once by A1 on Day 0 and recorded in §9.1.)*

`COMMON-NFR-005` **THE sidecar SHALL load every model by identifier read from configuration at
startup, and SHALL require no source change to substitute a different model identifier.**

`COMMON-NFR-006` **THE text embedding model SHALL produce 768-dimensional vectors**, matching the
`vector(768)` columns in §4. *(LaBSE is 768-d. Substituting a model of different dimensionality is a
schema change, not a config change, and requires a new migration.)*

### 2.5 The 14 disease classes

Rice — brown spot · leaf scald · blast · tungro · sheath blight · healthy.
Tomato — early blight · late blight · leaf curl virus · septoria leaf spot · healthy.
Potato — early blight · late blight · healthy.

`COMMON-DATA-001` **THE `disease` table SHALL contain exactly these 14 rows and no others.**
*(Codes, Bangla names, descriptions and severities are human-supplied — `CONTENT-OWNERS.md` row C1.)*

---

## 3. Repository structure

Complete tree. Files marked **(A1)** may be created or edited **only** by the platform agent.

```
foshol-doctor/
├── CLAUDE.md                                  (A1) agent operating rules
├── README.md                                  (A1) one-command startup
├── docker-compose.yml                         (A1) postgres+pgvector, minio, sidecar, app
├── settings.gradle                            (A1) multi-project include list
├── build.gradle                               (A1) root build
├── gradle.properties                          (A1)
├── gradle/
│   ├── libs.versions.toml                     (A1) THE version catalog
│   └── wrapper/                               (A1) gradle 9.7.1
├── buildSrc/                                  (A1)
│   ├── build.gradle
│   └── src/main/groovy/
│       ├── foshol.java-conventions.gradle       java 25, encoding, test config, compiler args
│       ├── foshol.module-conventions.gradle     applies java-conventions + module deps + archunit
│       └── foshol.spring-conventions.gradle     boot + modulith BOM wiring
├── .github/workflows/
│   ├── verify.yml                             (A1) build + unit + replay-mode integration
│   └── verify-live.yml                        (A1) live-mode e2e with a Hugging Face weight cache
│
├── common/                                    (A1) SHARED KERNEL — Modulith open module
│   └── src/main/java/com/rootcause/foshol/common/
│       ├── ConfigKeys.java                      every property name as a constant
│       ├── ErrorCodes.java                      every error code as a constant
│       ├── CaseStatus.java  DecisionPath.java  CandidateSource.java  SymptomSource.java
│       ├── ReviewState.java  AdvisoryAction.java  RemedyType.java  Severity.java
│       ├── AiMode.java  RejectionReason.java  NotificationType.java  Role.java
│       ├── Uuid7.java                           UUIDv7 factory
│       ├── CorrelationId.java                   MDC key + accessor
│       └── events/                              ALL domain event records (COMMON-ARCH-016)
│
├── app/                                       (A1) the single deployable
│   └── src/main/java/com/rootcause/foshol/FosholDoctorApplication.java
│   └── src/main/resources/
│       ├── application.properties               (A1)
│       ├── application-local.properties          (A1)
│       ├── application-demo.properties           (A1)
│       ├── application-test.properties           (A1)
│       └── db/
│           ├── migration/                        (A1) schema + core reference data
│           │   ├── V1__enable_extensions.sql
│           │   ├── V2__identity.sql
│           │   ├── V3__knowledge.sql
│           │   ├── V4__intake.sql
│           │   ├── V5__analysis.sql
│           │   ├── V6__review.sql
│           │   ├── V7__notification.sql
│           │   ├── V8__projections.sql
│           │   ├── V9__indexes.sql
│           │   ├── V10__ref_crops.sql
│           │   ├── V11__ref_diseases.sql
│           │   ├── V12__ref_symptoms.sql
│           │   ├── V13__ref_symptom_phrases.sql
│           │   ├── V14__ref_disease_symptom_weights.sql
│           │   ├── V15__ref_remedies.sql
│           │   ├── V16__ref_symptom_embeddings.sql
│           │   └── V17__ref_model_label_map.sql
│           └── seed/                             (A1) demo profile only
│               ├── V100__seed_demo_identities.sql
│               └── V101__seed_historical_cases.sql
│   └── src/test/java/com/rootcause/foshol/
│       ├── ModularityTests.java                  (A1) ApplicationModules.verify() + docs
│       ├── ArchitectureTests.java                (A1) the ArchUnit rule set (§5)
│       └── e2e/EndToEndSliceTest.java            (A1) submit → analyse → review → advisory
│
├── modules/
│   ├── identity/     ── A1   com.rootcause.foshol.identity
│   ├── intake/       ── A2   com.rootcause.foshol.intake
│   ├── analysis/     ── A3   com.rootcause.foshol.analysis
│   ├── knowledge/    ── A4   com.rootcause.foshol.knowledge
│   ├── review/       ── A5   com.rootcause.foshol.review
│   └── notification/ ── A5   com.rootcause.foshol.notification
│         each: build.gradle
│         and   src/main/java/com/rootcause/foshol/<module>/
│                 api/              published interfaces + event records — the ONLY importable package
│                 domain/           aggregates, VOs, specifications, domain events, exceptions (no Spring)
│                 application/
│                   command/        commands + command handlers
│                   query/          queries + query handlers + read models
│                   port/           outbound ports
│                 infrastructure/   JPA entities + repositories, adapters, event listeners
│                 web/              controllers, request/response records, mappers
│         and   src/test/java/...   unit tests + one Testcontainers integration test
│
├── sidecar/                                   (A7)
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── app/
│   │   ├── main.py            FastAPI app + routes
│   │   ├── config.py          env-driven settings
│   │   ├── vision.py          classify + Grad-CAM
│   │   ├── asr.py             faster-whisper transcription
│   │   ├── embed.py           LaBSE embeddings
│   │   ├── replay.py          SHA-256-keyed fixture store
│   │   └── schemas.py         pydantic request/response models
│   ├── fixtures/              replay-mode recorded responses + pre-baked Grad-CAM PNGs
│   └── tests/
│
├── web/                                       (A6) Angular application
│   ├── package.json  angular.json  tailwind.config.js  tsconfig.json
│   └── src/app/
│       ├── core/          auth, http interceptors, sse client, config
│       ├── generated/     ng-openapi-gen output — NEVER hand-edited
│       ├── shared/        ui primitives, i18n pipe, confidence bar, decision-path badge
│       ├── farmer/        crop picker, capture, recorder, case list, case detail, advisory card
│       ├── officer/       queue, case detail, remedy editor, actions
│       ├── admin/         read-only stats page
│       └── assets/i18n/   bn.json  en.json
│
├── tools/                                     (A7)
│   ├── eval.py             held-out evaluation → docs/eval-report.md
│   ├── seed_cases.py       generates V101 + uploads dataset images to MinIO
│   └── build_fixtures.py   records replay fixtures from a live sidecar
│
└── docs/
    ├── requirements/  *.ears.md  INDEX.md  CONTENT-OWNERS.md
    ├── adr/           0001..0016
    ├── openapi/       foshol-api.yaml         (A1) frozen contract, hand-authored Day 0
    └── eval-report.md                         generated by tools/eval.py
```

`COMMON-NFR-007` **THE Gradle project SHALL declare one subproject per module**, and each module's
`build.gradle` SHALL declare a dependency **only** on `:common` and on the `api` source of modules it
is permitted to call per §6.

---

## 4. Complete database schema

**This section is the single most important thing in this document.** It is what allows seven agents
to work simultaneously without colliding. Every table, column, type, constraint and index is defined
exactly once, here. **A module document references this schema and never redefines it.**

Conventions applied throughout:

- Primary keys are `uuid`, generated **in application code as UUIDv7** via `common.Uuid7`. The
  database never generates an id.
- All timestamps are `timestamptz`, stored in **UTC**, defaulting to `now()`.
- All money-free decimals use explicit precision. Confidence is `numeric(5,4)` (0.0000–1.0000);
  weights and scores are `numeric(4,3)`.
- Enum-valued columns are `varchar` with a `CHECK` constraint, never a PostgreSQL `ENUM` type
  *(a `CHECK` is alterable in a single migration; a native enum is not)*.
- Foreign keys **are** declared across module boundaries at the database level. JPA associations
  across module boundaries are forbidden (see `COMMON-ARCH-006`).

### 4.1 `V1__enable_extensions.sql`

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

### 4.2 `V2__identity.sql` — owned by `identity`

```sql
CREATE TABLE farmer (
    id                  uuid         PRIMARY KEY,
    name                varchar(120) NOT NULL,
    phone_hash          char(64)     NOT NULL UNIQUE,
    phone_enc           bytea        NOT NULL,
    district_code       varchar(8)   NOT NULL,
    preferred_language  char(2)      NOT NULL DEFAULT 'bn',
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_farmer_language CHECK (preferred_language IN ('bn','en'))
);

CREATE TABLE field_officer (
    id                  uuid         PRIMARY KEY,
    name                varchar(120) NOT NULL,
    username            varchar(60)  NOT NULL UNIQUE,
    password_hash       varchar(72)  NOT NULL,
    phone_hash          char(64)     NOT NULL,
    phone_enc           bytea        NOT NULL,
    district_code       varchar(8)   NOT NULL,
    role                varchar(16)  NOT NULL,
    active              boolean      NOT NULL DEFAULT true,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_officer_role CHECK (role IN ('OFFICER','ADMIN'))
);

CREATE TABLE otp_challenge (
    id                  uuid         PRIMARY KEY,
    phone_hash          char(64)     NOT NULL,
    code_hash           char(64)     NOT NULL,
    attempts            smallint     NOT NULL DEFAULT 0,
    expires_at          timestamptz  NOT NULL,
    consumed_at         timestamptz,
    created_at          timestamptz  NOT NULL DEFAULT now()
);
```

`phone_hash` is the lowercase hex SHA-256 of the E.164 phone number and is the **only** value used for
lookup. `phone_enc` is AES-256-GCM ciphertext of the same number, key from
`foshol.crypto.phone.key`. The plaintext phone number is never stored, never logged, and never
appears in a URL.

### 4.3 `V3__knowledge.sql` — owned by `knowledge`

```sql
CREATE TABLE crop (
    id             uuid         PRIMARY KEY,
    code           varchar(24)  NOT NULL UNIQUE,
    name_bn        varchar(120) NOT NULL,
    name_en        varchar(120),
    icon_key       varchar(64)  NOT NULL,
    display_order  smallint     NOT NULL DEFAULT 0,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    created_by     varchar(60)  NOT NULL DEFAULT 'system',
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    updated_by     varchar(60)  NOT NULL DEFAULT 'system',
    deleted_at     timestamptz
);

CREATE TABLE disease (
    id              uuid         PRIMARY KEY,
    crop_id         uuid         NOT NULL REFERENCES crop(id),
    code            varchar(48)  NOT NULL,
    name_bn         varchar(120) NOT NULL,
    name_en         varchar(120),
    description_bn  text,
    severity        varchar(12)  NOT NULL,
    is_healthy      boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      varchar(60)  NOT NULL DEFAULT 'system',
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      varchar(60)  NOT NULL DEFAULT 'system',
    deleted_at      timestamptz,
    CONSTRAINT uq_disease_crop_code UNIQUE (crop_id, code),
    CONSTRAINT ck_disease_severity CHECK (severity IN ('LOW','MODERATE','HIGH','CRITICAL','NONE'))
);

CREATE TABLE symptom (
    id          uuid         PRIMARY KEY,
    code        varchar(48)  NOT NULL UNIQUE,
    name_bn     varchar(120) NOT NULL,
    name_en     varchar(120),
    organ       varchar(16)  NOT NULL,
    embedding   vector(768),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  varchar(60)  NOT NULL DEFAULT 'system',
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  varchar(60)  NOT NULL DEFAULT 'system',
    deleted_at  timestamptz,
    CONSTRAINT ck_symptom_organ CHECK (organ IN ('LEAF','STEM','ROOT','PANICLE','FRUIT','TUBER','WHOLE'))
);

CREATE TABLE symptom_phrase (
    id             uuid        PRIMARY KEY,
    symptom_id     uuid        NOT NULL REFERENCES symptom(id),
    phrase_bn      text        NOT NULL,
    normalised_bn  text        NOT NULL,
    embedding      vector(768),
    created_at     timestamptz NOT NULL DEFAULT now(),
    created_by     varchar(60) NOT NULL DEFAULT 'system',
    updated_at     timestamptz NOT NULL DEFAULT now(),
    updated_by     varchar(60) NOT NULL DEFAULT 'system',
    deleted_at     timestamptz,
    CONSTRAINT uq_symptom_phrase UNIQUE (symptom_id, normalised_bn)
);

CREATE TABLE disease_symptom (
    disease_id  uuid          NOT NULL REFERENCES disease(id),
    symptom_id  uuid          NOT NULL REFERENCES symptom(id),
    weight      numeric(4,3)  NOT NULL,
    PRIMARY KEY (disease_id, symptom_id),
    CONSTRAINT ck_disease_symptom_weight CHECK (weight > 0 AND weight <= 1)
);

CREATE TABLE remedy (
    id             uuid         PRIMARY KEY,
    disease_id     uuid         NOT NULL REFERENCES disease(id),
    type           varchar(12)  NOT NULL,
    title_bn       varchar(200) NOT NULL,
    steps_bn       jsonb        NOT NULL,
    dosage_bn      text,
    phi_days       smallint,
    cost_tier      varchar(8)   NOT NULL,
    efficacy       varchar(8)   NOT NULL,
    source_ref     text         NOT NULL,
    display_order  smallint     NOT NULL DEFAULT 0,
    active         boolean      NOT NULL DEFAULT true,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    created_by     varchar(60)  NOT NULL DEFAULT 'system',
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    updated_by     varchar(60)  NOT NULL DEFAULT 'system',
    deleted_at     timestamptz,
    CONSTRAINT ck_remedy_type     CHECK (type IN ('CULTURAL','ORGANIC','BIOLOGICAL','CHEMICAL')),
    CONSTRAINT ck_remedy_cost     CHECK (cost_tier IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_remedy_efficacy CHECK (efficacy IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_remedy_steps    CHECK (jsonb_typeof(steps_bn) = 'array'),
    CONSTRAINT ck_remedy_phi      CHECK (type <> 'CHEMICAL' OR phi_days IS NOT NULL)
);

CREATE TABLE model_label_map (
    id             uuid         PRIMARY KEY,
    model_id       varchar(160) NOT NULL,
    model_version  varchar(64)  NOT NULL,
    raw_label      varchar(160) NOT NULL,
    disease_id     uuid         NOT NULL REFERENCES disease(id),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_model_label UNIQUE (model_id, model_version, raw_label)
);
```

`ck_remedy_phi` is the database-level expression of the plan's rule that a pre-harvest interval is
mandatory on every chemical remedy. It is not optional and it is not enforced only in Java.

**`model_label_map` `[DERIVED]`.** The plan (§4) puts a single `disease.model_class_label` column on
`disease`. That cannot work: this build runs **three** vision models with three disjoint label
spaces, and one column can express at most one. The mapping is therefore a table keyed by
`(model_id, model_version, raw_label)`. See §7 for the requirements attached to it.

### 4.4 `V4__intake.sql` — owned by `intake`

```sql
CREATE TABLE diagnosis_case (
    id              uuid         PRIMARY KEY,
    farmer_id       uuid         NOT NULL REFERENCES farmer(id),
    crop_id         uuid         NOT NULL REFERENCES crop(id),
    parent_case_id  uuid         REFERENCES diagnosis_case(id),
    status          varchar(16)  NOT NULL,
    decision_path   varchar(16),
    note_bn         text,
    district_code   varchar(8)   NOT NULL,
    correlation_id  varchar(36)  NOT NULL,
    version         integer      NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_case_status CHECK (status IN
        ('SUBMITTED','ANALYSING','ANALYSED','IN_REVIEW','ADVISED','REJECTED','FAILED')),
    CONSTRAINT ck_case_path CHECK (decision_path IS NULL OR decision_path IN
        ('PRIMARY','SECONDARY','UNDETERMINED'))
);

CREATE TABLE case_image (
    id                     uuid         PRIMARY KEY,
    case_id                uuid         NOT NULL REFERENCES diagnosis_case(id),
    object_key             varchar(200) NOT NULL,
    derivative_object_key  varchar(200),
    content_type           varchar(60)  NOT NULL,
    byte_size              integer      NOT NULL,
    width                  integer,
    height                 integer,
    sha256                 char(64)     NOT NULL,
    quality_score          numeric(4,3),
    blur_variance          numeric(10,3),
    exposure_score         numeric(4,3),
    rejected_reason        varchar(32),
    is_primary             boolean      NOT NULL DEFAULT false,
    position               smallint     NOT NULL,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_case_image_position UNIQUE (case_id, position)
);

CREATE TABLE case_audio (
    id              uuid         PRIMARY KEY,
    case_id         uuid         NOT NULL UNIQUE REFERENCES diagnosis_case(id),
    object_key      varchar(200) NOT NULL,
    duration_ms     integer      NOT NULL,
    sample_rate_hz  integer      NOT NULL,
    byte_size       integer      NOT NULL,
    transcript_bn   text,
    asr_confidence  numeric(4,3),
    created_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_key (
    key              uuid         PRIMARY KEY,
    farmer_id        uuid         NOT NULL REFERENCES farmer(id),
    endpoint         varchar(80)  NOT NULL,
    request_hash     char(64)     NOT NULL,
    response_status  smallint     NOT NULL,
    response_body    jsonb        NOT NULL,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    expires_at       timestamptz  NOT NULL
);
```

**`parent_case_id` `[DERIVED]`.** Rejection is terminal (ADR-0012); a farmer who is asked for a
better photograph creates a *new* case. `parent_case_id` preserves the link so the officer console
can show that a case is a re-submission, without making the case state machine reopenable.

**`idempotency_key` `[DERIVED]`.** The plan requires idempotency on case submission but names no
mechanism (ADR-0014).

### 4.5 `V5__analysis.sql` — owned by `analysis`

```sql
CREATE TABLE case_symptom (
    id          uuid         PRIMARY KEY,
    case_id     uuid         NOT NULL REFERENCES diagnosis_case(id),
    symptom_id  uuid         NOT NULL REFERENCES symptom(id),
    score       numeric(4,3) NOT NULL,
    source      varchar(8)   NOT NULL,
    matcher     varchar(8),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_case_symptom UNIQUE (case_id, symptom_id, source),
    CONSTRAINT ck_case_symptom_source  CHECK (source IN ('SPEECH','VISION','OFFICER')),
    CONSTRAINT ck_case_symptom_matcher CHECK (matcher IS NULL OR matcher IN ('VECTOR','FUZZY','MANUAL'))
);

CREATE TABLE case_candidate (
    id          uuid          PRIMARY KEY,
    case_id     uuid          NOT NULL REFERENCES diagnosis_case(id),
    disease_id  uuid          NOT NULL REFERENCES disease(id),
    confidence  numeric(5,4)  NOT NULL,
    rank        smallint      NOT NULL,
    source      varchar(8)    NOT NULL,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_case_candidate UNIQUE (case_id, disease_id, source),
    CONSTRAINT ck_case_candidate_source CHECK (source IN ('MODEL','KB','MERGED')),
    CONSTRAINT ck_case_candidate_conf   CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE TABLE analysis_run (
    id                     uuid          PRIMARY KEY,
    case_id                uuid          NOT NULL REFERENCES diagnosis_case(id),
    mode                   varchar(8)    NOT NULL,
    vision_model_id        varchar(160),
    vision_model_version   varchar(64),
    asr_model_id           varchar(160),
    embed_model_id         varchar(160),
    top1_confidence        numeric(5,4),
    top2_confidence        numeric(5,4),
    margin                 numeric(5,4),
    decision_path          varchar(16),
    latency_ms             integer       NOT NULL,
    gradcam_object_key     varchar(200),
    unmapped_labels        jsonb         NOT NULL DEFAULT '[]'::jsonb,
    raw_output             jsonb,
    error_code             varchar(32),
    created_at             timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_analysis_mode CHECK (mode IN ('REPLAY','LIVE')),
    CONSTRAINT ck_analysis_path CHECK (decision_path IS NULL OR decision_path IN
        ('PRIMARY','SECONDARY','UNDETERMINED')),
    CONSTRAINT ck_analysis_unmapped CHECK (jsonb_typeof(unmapped_labels) = 'array')
);
```

**`analysis_run.mode` `[DERIVED]`.** Replay and live are both supported and both may produce a
result. Recording which one produced a given result — and displaying it to the officer
(`REVIEW-UX-004`) — is how the team avoids mistaking a fixture for a live inference.

**`analysis_run.unmapped_labels` `[DERIVED]`.** A model output with no `model_label_map` row is
recorded here and never silently discarded (`COMMON-DATA-012`).

**`analysis_run.gradcam_object_key` `[DERIVED]`.** The plan lists Grad-CAM as a protected wow factor
but gives it no storage. Persisting the overlay means it still renders after the sidecar is killed
during the degradation demo.

### 4.6 `V6__review.sql` — owned by `review`

```sql
CREATE TABLE review_task (
    id                    uuid          PRIMARY KEY,
    case_id               uuid          NOT NULL UNIQUE REFERENCES diagnosis_case(id),
    officer_id            uuid          REFERENCES field_officer(id),
    state                 varchar(12)   NOT NULL,
    priority_confidence   numeric(5,4),
    claimed_at            timestamptz,
    sla_due_at            timestamptz   NOT NULL,
    requeue_count         smallint      NOT NULL DEFAULT 0,
    version               integer       NOT NULL DEFAULT 0,
    created_at            timestamptz   NOT NULL DEFAULT now(),
    created_by            varchar(60)   NOT NULL DEFAULT 'system',
    updated_at            timestamptz   NOT NULL DEFAULT now(),
    updated_by            varchar(60)   NOT NULL DEFAULT 'system',
    CONSTRAINT ck_review_state CHECK (state IN ('PENDING','CLAIMED','DONE','REJECTED')),
    CONSTRAINT ck_review_claim CHECK ((state = 'CLAIMED') = (officer_id IS NOT NULL AND claimed_at IS NOT NULL)
                                       OR state IN ('DONE','REJECTED'))
);

CREATE TABLE advisory (
    id              uuid         PRIMARY KEY,
    case_id         uuid         NOT NULL REFERENCES diagnosis_case(id),
    disease_id      uuid         REFERENCES disease(id),
    officer_id      uuid         NOT NULL REFERENCES field_officer(id),
    action          varchar(12)  NOT NULL,
    officer_note_bn text,
    version         smallint     NOT NULL DEFAULT 1,
    supersedes_id   uuid         REFERENCES advisory(id),
    published_at    timestamptz  NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      varchar(60)  NOT NULL,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      varchar(60)  NOT NULL,
    CONSTRAINT uq_advisory_case_version UNIQUE (case_id, version),
    CONSTRAINT ck_advisory_action CHECK (action IN ('APPROVED','EDITED','REPLACED')),
    CONSTRAINT ck_advisory_version CHECK (version >= 1),
    CONSTRAINT ck_advisory_supersedes CHECK ((version = 1) = (supersedes_id IS NULL))
);

CREATE TABLE advisory_remedy (
    advisory_id    uuid     NOT NULL REFERENCES advisory(id),
    remedy_id      uuid     NOT NULL REFERENCES remedy(id),
    display_order  smallint NOT NULL DEFAULT 0,
    PRIMARY KEY (advisory_id, remedy_id)
);

CREATE TABLE case_rejection (
    id           uuid         PRIMARY KEY,
    case_id      uuid         NOT NULL UNIQUE REFERENCES diagnosis_case(id),
    officer_id   uuid         NOT NULL REFERENCES field_officer(id),
    reason_code  varchar(32)  NOT NULL,
    message_bn   text         NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_rejection_reason CHECK (reason_code IN
        ('BLURRY_IMAGE','NOT_A_CROP','WRONG_CROP','INSUFFICIENT_DETAIL','INAUDIBLE_AUDIO','OTHER'))
);
```

**`advisory_remedy` `[DERIVED]`.** The plan models the remedy list as `remedy_ids[]` on `advisory`.
A join table is used instead so the reference is foreign-key-checked; an array column cannot be.

**`advisory.version` / `supersedes_id` `[DERIVED]`, `case_rejection` `[DERIVED]`.** See ADR-0013 and
ADR-0012.

### 4.7 `V7__notification.sql` — owned by `notification`

```sql
CREATE TABLE notification (
    id            uuid         PRIMARY KEY,
    farmer_id     uuid         NOT NULL REFERENCES farmer(id),
    case_id       uuid         NOT NULL REFERENCES diagnosis_case(id),
    advisory_id   uuid         REFERENCES advisory(id),
    channel       varchar(16)  NOT NULL,
    type          varchar(32)  NOT NULL,
    title_bn      varchar(200) NOT NULL,
    body_bn       text         NOT NULL,
    payload       jsonb        NOT NULL DEFAULT '{}'::jsonb,
    state         varchar(12)  NOT NULL,
    attempts      smallint     NOT NULL DEFAULT 0,
    delivered_at  timestamptz,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('SSE','WEB_PUSH','SMS')),
    CONSTRAINT ck_notification_state   CHECK (state IN ('PENDING','SENT','FAILED','SKIPPED')),
    CONSTRAINT ck_notification_type    CHECK (type IN
        ('ADVISORY_PUBLISHED','ADVISORY_REVISED','CASE_REJECTED','CASE_STATUS_CHANGED'))
);
```

### 4.8 `V8__projections.sql` — read-side projection tables

Owned by `review` (`p_officer_queue`, `p_admin_stats_daily`) and `intake`
(`p_farmer_case_history`). **These are tables, not views**, fed by `@ApplicationModuleListener`s.

> **`[DERIVED]` — naming.** The plan calls these `v_officer_queue` and `v_farmer_case_history`, but
> plan §2.2 also requires that projections be "updated by `@ApplicationModuleListener` on domain
> events, not by triggers or joins at read time". A `v_` name implies a view and would contradict
> that rule, so the prefix is `p_`.

```sql
CREATE TABLE p_officer_queue (
    case_id               uuid          PRIMARY KEY REFERENCES diagnosis_case(id),
    review_task_id        uuid          NOT NULL,
    farmer_name           varchar(120)  NOT NULL,
    crop_code             varchar(24)   NOT NULL,
    crop_name_bn          varchar(120)  NOT NULL,
    district_code         varchar(8)    NOT NULL,
    decision_path         varchar(16),
    top_disease_id        uuid,
    top_disease_name_bn   varchar(120),
    top_confidence        numeric(5,4),
    image_count           smallint      NOT NULL DEFAULT 0,
    has_audio             boolean       NOT NULL DEFAULT false,
    analysis_mode         varchar(8),
    state                 varchar(12)   NOT NULL,
    officer_id            uuid,
    is_resubmission       boolean       NOT NULL DEFAULT false,
    submitted_at          timestamptz   NOT NULL,
    sla_due_at            timestamptz   NOT NULL,
    updated_at            timestamptz   NOT NULL DEFAULT now()
);

CREATE TABLE p_farmer_case_history (
    case_id                 uuid          PRIMARY KEY REFERENCES diagnosis_case(id),
    farmer_id               uuid          NOT NULL,
    crop_name_bn            varchar(120)  NOT NULL,
    status                  varchar(16)   NOT NULL,
    decision_path           varchar(16),
    advisory_id             uuid,
    advisory_version        smallint,
    disease_name_bn         varchar(120),
    officer_name            varchar(120),
    rejection_message_bn    text,
    thumbnail_object_key    varchar(200),
    submitted_at            timestamptz   NOT NULL,
    published_at            timestamptz,
    updated_at              timestamptz   NOT NULL DEFAULT now()
);
```

The admin stats strip is computed by an aggregate query over `review_task`, `advisory` and
`case_candidate` — there is no stats projection table (`REVIEW-FR-070`).

### 4.9 `V9__indexes.sql`

```sql
-- vector search (F5's core lookup)
CREATE INDEX ix_symptom_phrase_embedding ON symptom_phrase
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
CREATE INDEX ix_symptom_embedding ON symptom
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- the officer queue's one and only sort: least-confident first
CREATE INDEX ix_officer_queue_priority ON p_officer_queue (state, top_confidence ASC NULLS FIRST, submitted_at ASC);
CREATE INDEX ix_officer_queue_officer  ON p_officer_queue (officer_id) WHERE officer_id IS NOT NULL;

CREATE INDEX ix_farmer_history          ON p_farmer_case_history (farmer_id, submitted_at DESC);
CREATE INDEX ix_case_farmer             ON diagnosis_case (farmer_id, created_at DESC);
CREATE INDEX ix_case_status             ON diagnosis_case (status);
CREATE INDEX ix_case_parent             ON diagnosis_case (parent_case_id) WHERE parent_case_id IS NOT NULL;
CREATE INDEX ix_case_image_case         ON case_image (case_id);
CREATE INDEX ix_case_image_sha          ON case_image (sha256);
CREATE INDEX ix_case_symptom_case       ON case_symptom (case_id);
CREATE INDEX ix_case_candidate_case     ON case_candidate (case_id, source, rank);
CREATE INDEX ix_analysis_run_case       ON analysis_run (case_id, created_at DESC);
CREATE INDEX ix_review_task_state       ON review_task (state, sla_due_at);
CREATE INDEX ix_review_task_claimed     ON review_task (claimed_at) WHERE state = 'CLAIMED';
CREATE INDEX ix_advisory_case           ON advisory (case_id, version DESC);
CREATE INDEX ix_disease_symptom_symptom ON disease_symptom (symptom_id);
CREATE INDEX ix_symptom_phrase_symptom  ON symptom_phrase (symptom_id);
CREATE INDEX ix_remedy_disease          ON remedy (disease_id) WHERE active AND deleted_at IS NULL;
CREATE INDEX ix_notification_farmer     ON notification (farmer_id, created_at DESC);
CREATE INDEX ix_idempotency_expiry      ON idempotency_key (expires_at);
CREATE INDEX ix_otp_phone               ON otp_challenge (phone_hash, created_at DESC);
```

### 4.10 Migration rules

`COMMON-DATA-002` **THE application SHALL apply schema exclusively through Flyway and SHALL set
`spring.jpa.hibernate.ddl-auto=validate` in every profile.** *(`none` is not sufficient — `validate`
catches an entity that has drifted from the migration, which is exactly the failure mode of parallel
agents.)*

`COMMON-DATA-003` **THE `V1`–`V9` migrations SHALL be authored by agent A1 only.** A module agent
that needs a schema change SHALL raise a blocker; it SHALL NOT add a migration.

`COMMON-DATA-004` **THE `V10`–`V17` reference-data migrations SHALL be applied in every profile**,
because the knowledge base is the product, not demo dressing.

`COMMON-DATA-005` **THE `V100`–`V101` seed migrations SHALL be applied only under the `demo`
profile**, via `spring.flyway.locations`.

`COMMON-DATA-006` **WHEN the application starts against an empty database, THE system SHALL apply
every migration successfully with no manual step.**

---

## 5. Architectural laws

Each law is a testable requirement paired with the assertion that enforces it. All assertions live in
`app/src/test/java/com/rootcause/foshol/ArchitectureTests.java` and `ModularityTests.java`, owned by
A1, and **run on every build**. A law without a passing assertion is not a law.

`COMMON-ARCH-001` **THE system SHALL expose module verification as a build-failing test.**
```java
@Test void modulesAreVerified() { ApplicationModules.of(FosholDoctorApplication.class).verify(); }
```

`COMMON-ARCH-002` **THE system SHALL permit a module to import another module's `api` package only.**
Modulith enforces this by treating the module root package as the published API; every other package
is internal. Reinforced explicitly:
```java
ArchRuleDefinition.noClasses()
    .that().resideInAPackage("..foshol.(*)..")
    .should().dependOnClassesThat()
    .resideInAnyPackage("..foshol.*.domain..", "..foshol.*.application..",
                        "..foshol.*.infrastructure..", "..foshol.*.web..")
    .because("only the api package of another module may be imported");
```
*(The rule is written so a class may still reach its **own** module's internals; see the reference
implementation in `ArchitectureTests` for the same-module exclusion.)*

`COMMON-ARCH-003` **THE system SHALL enforce the dependency rule `web → application → domain` and
`infrastructure → application`.**
```java
layeredArchitecture().consideringOnlyDependenciesInAnyPackage("..foshol..")
    .layer("web").definedBy("..web..")
    .layer("application").definedBy("..application..")
    .layer("domain").definedBy("..domain..")
    .layer("infrastructure").definedBy("..infrastructure..")
    .whereLayer("web").mayNotBeAccessedByAnyLayer()
    .whereLayer("application").mayOnlyBeAccessedByLayers("web", "infrastructure")
    .whereLayer("domain").mayOnlyBeAccessedByLayers("application", "infrastructure", "web");
```

`COMMON-ARCH-004` **THE `domain` package SHALL contain no Spring, Jakarta Persistence or Jackson
dependency.**
```java
noClasses().that().resideInAPackage("..domain..")
    .should().dependOnClassesThat().resideInAnyPackage(
        "org.springframework..", "jakarta.persistence..", "com.fasterxml.jackson..");
```

`COMMON-ARCH-005` **THE system SHALL separate commands from queries: no class SHALL both handle a
command and handle a query.**
```java
noClasses().that().resideInAPackage("..application.command..")
    .should().dependOnClassesThat().resideInAPackage("..application.query..");
noClasses().that().resideInAPackage("..application.query..")
    .should().dependOnClassesThat().resideInAPackage("..application.command..");
```

`COMMON-ARCH-006` **THE system SHALL NOT declare a JPA association whose target entity belongs to
another module.** Cross-module references are stored as a raw `UUID` column and resolved through the
owning module's published API. *(Database-level foreign keys across modules are required and are not
affected by this rule.)*
```java
noFields().that().areAnnotatedWith(ManyToOne.class).or().areAnnotatedWith(OneToMany.class)
    .or().areAnnotatedWith(OneToOne.class).or().areAnnotatedWith(ManyToMany.class)
    .should(haveRawTypeInAnotherFosholModule());
```

`COMMON-ARCH-007` **THE query side SHALL inject the `@ReadOnlyDataSource`-qualified `DataSource` and
SHALL NOT load an aggregate.**
```java
noClasses().that().resideInAPackage("..application.query..")
    .should().dependOnClassesThat().areAnnotatedWith(Entity.class);
```
The qualified bean is declared once by A1 and today points at the same URL as the write
`DataSource` (`foshol.datasource.read-only.url`). Splitting to a replica is one property.

`COMMON-ARCH-008` **THE system SHALL express every DTO, command, query, event and read model as a
`record`.**
```java
classes().that().resideInAnyPackage("..api..", "..application.command..", "..application.query..", "..web..")
    .and().haveSimpleNameEndingWith("Command").or().haveSimpleNameEndingWith("Query")
    .or().haveSimpleNameEndingWith("Request").or().haveSimpleNameEndingWith("Response")
    .or().haveSimpleNameEndingWith("View").or().haveSimpleNameEndingWith("Event")
    .should().beRecords();
```

`COMMON-ARCH-009` **THE system SHALL contain no business logic in a controller.** A controller method
SHALL do no more than bind, delegate to exactly one command or query handler, and map the result.
```java
noClasses().that().resideInAPackage("..web..")
    .should().dependOnClassesThat().resideInAPackage("..domain..")
    .because("controllers must not touch the domain model directly");
methods().that().areDeclaredInClassesThat().resideInAPackage("..web..")
    .should(haveCyclomaticComplexityLessThan(4));
```

`COMMON-ARCH-010` **THE system SHALL define every event name, error code, configuration key and
enumerated string as a constant or enum in `com.rootcause.foshol.common`, and SHALL NOT use a string
literal for any of them.**
```java
noClasses().that().resideOutsideOfPackage("..foshol.common..")
    .should().callMethod(Environment.class, "getProperty", String.class)
    .orShould(containConfigurationStringLiteral());
```

`COMMON-ARCH-011` **THE system SHALL contain no cycle between modules.** Enforced by
`ApplicationModules.verify()`.

`COMMON-ARCH-012` **THE system SHALL import types; it SHALL NOT reference a type by its fully
qualified name in a method body or field declaration.**

`COMMON-ARCH-013` **THE `common` module SHALL be declared a Modulith open/shared module** so that
every other module may depend on it without violating `COMMON-ARCH-002`:
`spring.modulith.shared-modules=common`. It SHALL contain only enums, constants, domain event
records (`COMMON-ARCH-016`) and pure static utilities — no Spring beans, no persistence, no logic.

---

## 6. Cross-module contracts

**These signatures are FROZEN.** They are authored by A1 on Day 0 before any other agent starts. A
module agent implements against them exactly and never edits another module's `api` package.

### 6.1 Permitted dependency matrix

A `✓` means the row module may **call a method on** the column module's published interface — that
is, a *synchronous* dependency.

**This matrix does not govern events**, because no event record lives in a module's `api` package —
they all live in the shared `common` module (`COMMON-ARCH-016`). Publishing or consuming an event is
therefore always a dependency on `common`, never on the other module, and can never be a matrix
violation or a cycle. `intake` consumes `AdvisoryApproved` while showing `—` for `intake → review`
here: it reacts to that fact but calls no method on `ReviewSubmissionApi`. Events deliberately flow
against the call graph, and routing them through `common` is what keeps the graph acyclic
(`COMMON-ARCH-011`).

| calls ↓ / callee → | common | identity | intake | analysis | knowledge | review | notification |
|---|---|---|---|---|---|---|---|
| **identity**     | ✓ | — | — | — | — | — | — |
| **intake**       | ✓ | ✓ | — | — | ✓ | — | — |
| **analysis**     | ✓ | — | ✓ | — | ✓ | — | — |
| **knowledge**    | ✓ | — | — | — | — | — | — |
| **review**       | ✓ | ✓ | ✓ | ✓ | ✓ | — | — |
| **notification** | ✓ | ✓ | — | — | — | ✓ | — |

Everything not marked `✓` is a build failure. Note that no module calls `review` or `notification`
synchronously — they are driven by events only.

### 6.2 Published interfaces

```java
// ── com.rootcause.foshol.identity.api ─────────────────────────────────────────
public interface FarmerLookupApi {
    Optional<FarmerView> findById(UUID farmerId);
    Optional<FarmerView> findByPhone(String e164Phone);
}
public record FarmerView(UUID id, String name, String districtCode, String preferredLanguage, String divisionCode) {}

public interface OfficerLookupApi {
    Optional<OfficerView> findById(UUID officerId);
    List<OfficerView> findActiveByDistrict(String districtCode);
}
public record OfficerView(UUID id, String name, String districtCode, String role, boolean active, String divisionCode) {}

// ── com.rootcause.foshol.intake.api ───────────────────────────────────────────
public interface CaseIntakeApi {
    Optional<CaseSummary> findById(UUID caseId);
    boolean isOwnedBy(UUID caseId, UUID farmerId);
    boolean officerSharesDistrict(UUID caseId, UUID officerId);
    void recordTranscript(UUID caseId, String transcriptBn, BigDecimal asrConfidence);
}
public record CaseSummary(UUID caseId, UUID farmerId, UUID cropId, String cropCode,
                          String districtCode, String divisionCode, CaseStatus status, DecisionPath decisionPath,
                          String noteBn, UUID parentCaseId, List<CaseImageRef> images,
                          CaseAudioRef audio, String correlationId, Instant submittedAt) {}
public record CaseImageRef(UUID imageId, String objectKey, String derivativeObjectKey,
                           String sha256, BigDecimal qualityScore, boolean primary, int position) {}
public record CaseAudioRef(UUID audioId, String objectKey, int durationMs, String transcriptBn) {}

public interface CaseIntakeChannel {                       // extensibility seam, plan §3
    UUID submit(IntakeRequest request);                    // WebIntakeAdapter today, SmsIntakeAdapter later
}
public record IntakeRequest(UUID farmerId, UUID cropId, String noteBn, UUID parentCaseId,
                            List<IntakeImage> images, IntakeAudio audio, UUID idempotencyKey) {}
public record IntakeImage(String filename, String contentType, byte[] bytes) {}
public record IntakeAudio(String filename, String contentType, byte[] bytes, int durationMs) {}

// ── com.rootcause.foshol.analysis.api ─────────────────────────────────────────
public interface AnalysisApi {
    Optional<AnalysisView> findByCaseId(UUID caseId);
    void recordOfficerSymptoms(UUID caseId, List<UUID> symptomIds);
}
public record AnalysisView(UUID caseId, DecisionPath decisionPath, AiMode mode,
                           BigDecimal top1Confidence, BigDecimal top2Confidence, BigDecimal margin,
                           List<CandidateView> candidates, List<SymptomView> symptoms,
                           String transcriptBn, BigDecimal asrConfidence, String gradcamObjectKey,
                           List<String> unmappedLabels, String visionModelId,
                           String visionModelVersion, int latencyMs, String errorCode) {}
public record CandidateView(UUID diseaseId, String diseaseCode, String diseaseNameBn,
                            BigDecimal confidence, int rank, CandidateSource source) {}
public record SymptomView(UUID symptomId, String symptomCode, String nameBn,
                          BigDecimal score, SymptomSource source, String matcher) {}

// ── com.rootcause.foshol.knowledge.api ────────────────────────────────────────
public interface KnowledgeQueryApi {
    List<CropView> listCrops();
    Optional<CropView> findCropById(UUID cropId);
    Optional<CropView> findCropByCode(String code);
    Optional<DiseaseView> findDiseaseById(UUID diseaseId);
    List<DiseaseView> listDiseasesByCrop(UUID cropId);
    List<RemedyView> listActiveRemedies(UUID diseaseId);
    List<SymptomRefView> listSymptoms();
    Optional<UUID> resolveModelLabel(String modelId, String modelVersion, String rawLabel);
}
public record CropView(UUID id, String code, String nameBn, String nameEn, String iconKey) {}
public record DiseaseView(UUID id, UUID cropId, String code, String nameBn, String nameEn,
                          String descriptionBn, Severity severity, boolean healthy) {}
public record RemedyView(UUID id, UUID diseaseId, RemedyType type, String titleBn,
                         List<String> stepsBn, String dosageBn, Integer phiDays,
                         String costTier, String efficacy, String sourceRef) {}
public record SymptomRefView(UUID id, String code, String nameBn, String nameEn, String organ) {}

public interface SymptomMatchApi {
    SymptomMatchResult match(SymptomMatchRequest request);
}
public record SymptomMatchRequest(UUID cropId, String transcriptBn,
                                  float[] transcriptEmbedding, List<UUID> officerSymptomIds) {}
public record SymptomMatchResult(List<MatchedSymptom> symptoms, List<ScoredDisease> diseases,
                                 boolean inconclusive) {}
public record MatchedSymptom(UUID symptomId, String code, String nameBn,
                             BigDecimal score, String matcher) {}
public record ScoredDisease(UUID diseaseId, String code, String nameBn, BigDecimal score, int rank) {}

// ── com.rootcause.foshol.review.api ───────────────────────────────────────────
public interface ReviewSubmissionApi {
    Optional<AdvisoryView> findPublishedAdvisory(UUID caseId);
    List<AdvisoryView> findAdvisoryHistory(UUID caseId);
    Optional<RejectionView> findRejection(UUID caseId);
}
public record AdvisoryView(UUID advisoryId, UUID caseId, UUID diseaseId, String diseaseNameBn,
                           UUID officerId, String officerName, AdvisoryAction action,
                           String officerNoteBn, int version, UUID supersedesId,
                           List<RemedyRefView> remedies, Instant publishedAt) {}
public record RemedyRefView(UUID remedyId, RemedyType type, String titleBn, List<String> stepsBn,
                            String dosageBn, Integer phiDays, String sourceRef) {}
public record RejectionView(UUID caseId, UUID officerId, String officerName,
                            RejectionReason reasonCode, String messageBn, Instant createdAt) {}

// ── com.rootcause.foshol.notification.api ─────────────────────────────────────
public interface NotificationPort {
    void publish(AdvisoryNotification notification);
}
public record AdvisoryNotification(UUID notificationId, UUID farmerId, UUID caseId, UUID advisoryId,
                                   NotificationType type, String titleBn, String bodyBn,
                                   Map<String, String> data) {}
public interface NotificationChannel {
    String name();
    boolean enabled();
    boolean supports(UUID farmerId);
    boolean send(AdvisoryNotification notification);
}
```

### 6.3 Domain events

`COMMON-ARCH-016` `[DERIVED]` **THE system SHALL declare every domain event `record` in
`com.rootcause.foshol.common.events`, in the shared `common` module — NOT in the publishing module's
`api` package.**

*(This corrects the plan and an earlier draft of this document. If an event record lived in its
publisher's `api` package, consuming it would be a type dependency on the publisher, and this
system's event flow is bidirectional in two places: `analysis` consumes `CaseSubmitted` from
`intake` while `intake` consumes `AnalysisCompleted` from `analysis`; and `review` calls
`CaseIntakeApi` while `intake` consumes `AdvisoryApproved` from `review`. Both are module cycles, and
`ApplicationModules.verify()` fails on a cycle — so `COMMON-ARCH-001`, the build's own architectural
proof, would fail on Day 1. Because `common` is a Modulith shared module (`COMMON-ARCH-013`),
depending on it never creates a cycle and never violates `COMMON-ARCH-002`. Ownership is unchanged
and is defined by the publish/consume map below: exactly one module may publish each event.)*

`COMMON-ARCH-017` **THE system SHALL permit exactly one publisher per event type**, as given in the
publish/consume map. A module SHALL NOT publish an event the map does not assign to it.

`COMMON-ARCH-018` `[DERIVED]` **THE system SHALL declare every record referenced by an event record
in `com.rootcause.foshol.common.events` as well.**

Specifically these four payload records move out of their module `api` packages into
`common.events`, and the module `api` interfaces below reuse them rather than redeclaring them:

| Record | Referenced by event | Also used by |
|---|---|---|
| `CaseImageRef` | `CaseSubmitted` | `CaseSummary` (`intake.api`) |
| `CaseAudioRef` | `CaseSubmitted` | `CaseSummary` (`intake.api`) |
| `CandidateView` | `AnalysisCompleted` | `AnalysisView` (`analysis.api`) |
| `SymptomView` | `AnalysisCompleted` | `AnalysisView` (`analysis.api`) |

*(Relocating only the event records is not sufficient. `AnalysisCompleted` carries
`List<CandidateView>`; if `CandidateView` stayed in `analysis.api`, then `intake` — which consumes
`AnalysisCompleted` to advance case status — would still hold a type reference into `analysis.api`,
and the `intake ↔ analysis` cycle would survive transitively. A cycle through a payload type is
harder to spot than one through an event type and would surface as a confusing
`ApplicationModules.verify()` failure on Day 1.)*

Every event carries `correlationId` and `occurredAt`. Consumers use `@ApplicationModuleListener`.
Records below are shown with their field lists; their package is `common.events` in every case.

```java
// publisher: intake  ·  package: com.rootcause.foshol.common.events
public record CaseSubmitted(UUID caseId, UUID farmerId, UUID cropId, String cropCode,
                            String districtCode, String divisionCode, List<CaseImageRef> images, CaseAudioRef audio,
                            String noteBn, UUID parentCaseId,
                            String correlationId, Instant occurredAt) {}

public record CaseStatusChanged(UUID caseId, UUID farmerId, CaseStatus fromStatus,
                                CaseStatus toStatus, String correlationId, Instant occurredAt) {}

// publisher: analysis  ·  package: com.rootcause.foshol.common.events
public record AnalysisCompleted(UUID caseId, UUID farmerId, UUID cropId, DecisionPath decisionPath,
                                AiMode mode, BigDecimal top1Confidence, BigDecimal margin,
                                List<CandidateView> candidates, List<SymptomView> symptoms,
                                boolean hasAudio, int imageCount,
                                String correlationId, Instant occurredAt) {}

public record AnalysisFailed(UUID caseId, UUID farmerId, String errorCode,
                             String correlationId, Instant occurredAt) {}

// publisher: review  ·  package: com.rootcause.foshol.common.events
public record AdvisoryApproved(UUID advisoryId, UUID caseId, UUID farmerId, UUID officerId,
                               String officerName, UUID diseaseId, String diseaseNameBn,
                               AdvisoryAction action, int version,
                               String correlationId, Instant occurredAt) {}

public record AdvisoryRevised(UUID advisoryId, UUID supersededAdvisoryId, UUID caseId, UUID farmerId,
                              UUID officerId, String officerName, int version,
                              String correlationId, Instant occurredAt) {}

public record CaseRejected(UUID caseId, UUID farmerId, UUID officerId, String officerName,
                           RejectionReason reasonCode, String messageBn,
                           String correlationId, Instant occurredAt) {}
```

**Publish / consume map.**

| Event | Publisher | Consumers |
|---|---|---|
| `CaseSubmitted` | intake | analysis (starts the pipeline) |
| `CaseStatusChanged` | intake | notification (SSE fan-out), intake (own history projection) |
| `AnalysisCompleted` | analysis | review (creates the `ReviewTask`), intake (advances status to `ANALYSED`) |
| `AnalysisFailed` | analysis | review (creates an `UNDETERMINED` `ReviewTask`), intake (status `FAILED`) |
| `AdvisoryApproved` | review | notification (notifies the farmer), intake (status `ADVISED`) |
| `AdvisoryRevised` | review | notification (second notification) |
| `CaseRejected` | review | notification, intake (status `REJECTED`) |

> **`[DERIVED]` — `CaseAnalysed` removed.** Plan §2.4 lists `intake` publishing both `CaseSubmitted`
> and `CaseAnalysed`. `CaseAnalysed` would duplicate `AnalysisCompleted`, which the analysis module
> publishes and which carries strictly more information. Two events for one fact is exactly the kind
> of ambiguity that makes parallel agents diverge, so intake publishes the generic
> `CaseStatusChanged` instead.

`COMMON-ARCH-014` **THE system SHALL deliver every domain event through Spring Modulith's event
publication registry**, with `spring-modulith-events-jpa` on the classpath and
`spring.modulith.events.jdbc.schema-initialization.enabled=true`, so an event that fails mid-delivery
is retried on restart. *(This is the plan's "outbox semantics for one dependency and one property".)*

`COMMON-ARCH-015` **THE system SHALL keep every `@ApplicationModuleListener` idempotent**, so that a
republished event produces no duplicate row and no duplicate notification.

---

## 7. Model label mapping

The three vision models have three disjoint label spaces which agree neither with each other nor with
the 14-class taxonomy. This mapping is a first-class contract, not a lookup detail.

`COMMON-DATA-010` **THE `model_label_map` table SHALL map every raw label of every configured vision
model, keyed by `(model_id, model_version, raw_label)`, onto exactly one `disease.id`.**

`COMMON-DATA-011` **THE `model_label_map` table SHALL be populated exclusively by Flyway migration
`V17__ref_model_label_map.sql`, and THE system SHALL NOT write to it at runtime.**

`COMMON-DATA-012` **IF a vision model returns a raw label with no `model_label_map` row for the
reporting model id and version, THEN THE analysis module SHALL discard that candidate, SHALL append
the raw label to `analysis_run.unmapped_labels`, SHALL log a `WARN` with the correlation id, and
SHALL NOT include it in `case_candidate`.**

`COMMON-DATA-013` **IF every candidate returned for a case is unmapped, THEN THE analysis module
SHALL route the case to `UNDETERMINED`.** *(An unmapped result is an unknown result. It is never
silently dropped and it never becomes a confident diagnosis.)*

`COMMON-DATA-014` **WHEN the application starts, THE system SHALL verify that every model identifier
configured under `foshol.ai.vision.*` has at least one `model_label_map` row, and IF any configured
model has none, THEN THE application SHALL fail to start with error code
`ERR_MODEL_LABEL_MAP_MISSING`.**

`COMMON-DATA-015` **WHEN the application starts, THE system SHALL verify that every
`model_label_map.disease_id` resolves to an existing, non-deleted `disease` row, and IF any does not,
THEN THE application SHALL fail to start with error code `ERR_MODEL_LABEL_MAP_INVALID`.**

`COMMON-DATA-016` **THE system SHALL NOT allow a raw model label string to be persisted on
`case_candidate`, `advisory`, `p_officer_queue` or `p_farmer_case_history`, nor returned by any
endpoint outside `GET /api/v1/cases/{id}/analysis`.** Raw labels appear only in
`analysis_run.raw_output`, `analysis_run.unmapped_labels`, and the analysis detail response the
officer console shows for transparency.

`COMMON-DATA-017` **THE sidecar SHALL report the `model_id` and `model_version` that produced each
classification in its response, and THE analysis module SHALL use those reported values — not its own
configuration — as the mapping key.** *(If the sidecar has been reconfigured, the mapping must follow
the model that actually ran, not the model Spring believes is configured.)*

`COMMON-DATA-018` `[DERIVED]` **THE label mapping SHALL have exactly one human-validated source of
record: `docs/content/model-label-map.csv`, with the columns
`model_id,model_version,raw_label,disease_code`.** Flyway migration `V17__ref_model_label_map.sql`
(agent A1) and the evaluation harness `tools/eval.py` (agent A7) SHALL both be generated from that
file and SHALL NOT maintain independent copies.

*(The sidecar and the evaluation harness have no database access, so `tools/eval.py` needs a
file-based mapping. Two independently authored copies of the same mapping is a silent correctness
bug: the evaluation would score against a different taxonomy than the product serves, and nothing
would fail. Row content is human-supplied — see `CONTENT-OWNERS.md`.)*

`COMMON-DATA-019` `[DERIVED]` **IF `docs/content/model-label-map.csv` is absent or contains a
`disease_code` that does not exist in the `disease` table, THEN THE build SHALL fail**, and agent A7
SHALL raise a blocker to A1 rather than authoring a mapping of its own.

`COMMON-DATA-020` `[DERIVED]` **THE analysis module SHALL record the ASR transcript and confidence by
calling `CaseIntakeApi.recordTranscript`, and SHALL NOT write `case_audio` directly.**

*(`case_audio.transcript_bn` and `asr_confidence` are intake-owned columns, but the transcript is
produced by the analysis pipeline. Without a published method the column would stay permanently
null and the transcript would live only in `analysis_run.raw_output` — readable, but not where the
schema says it lives. One writer per table is preserved: intake still performs the write.)*

---

## 8. Shared conventions

### 8.1 Naming

| Artefact | Convention | Example |
|---|---|---|
| Base package | `com.rootcause.foshol` | — |
| Module package | `com.rootcause.foshol.<module>` | `com.rootcause.foshol.intake` |
| Aggregate root | noun | `DiagnosisCase` |
| Command | verb-noun + `Command` | `SubmitCaseCommand` |
| Command handler | command name + `Handler` | `SubmitCaseCommandHandler` |
| Query | noun + `Query` | `OfficerQueueQuery` |
| Query handler | query name + `Handler` | `OfficerQueueQueryHandler` |
| Read model / DTO | noun + `View` or `Row` | `AdvisoryView`, `OfficerQueueRow` |
| Outbound port | noun + `Port` | `VisionModelPort` |
| Adapter | technology + port name | `HttpVisionModelAdapter` |
| Event listener | source + `EventListener` | `AnalysisEventListener` |
| Domain exception | noun + reason + `Exception` | `CaseNotFoundException` |
| JPA entity | noun + `Entity` | `DiagnosisCaseEntity` |
| Table | singular snake_case | `diagnosis_case` |
| Projection table | `p_` + snake_case | `p_officer_queue` |
| Constraint | `ck_` / `uq_` / `ix_` + table + column | `ix_case_status` |

`COMMON-NFR-010` **THE JPA entity SHALL be a distinct type from the domain aggregate**, living in
`infrastructure`, mapped by a hand-written static mapper. The domain never carries a JPA annotation
(`COMMON-ARCH-004`).

### 8.2 REST conventions

- Base path `/api/v1`. Plural, lowercase, hyphenated collection nouns.
- `GET` reads, `POST` creates or invokes a state transition, `PUT` full replace, `DELETE` removes.
  No verbs in paths except for explicit state transitions on a sub-resource
  (`POST /review/tasks/{id}/claim`), which are permitted and preferred over inventing a resource.
- Every list endpoint is paginated: `?page` (0-based) and `?size` (default `20`, max `100`), returning
  `{"content":[…],"page":0,"size":20,"totalElements":0,"totalPages":0}`.
- Sorting is **not** client-controlled on the officer queue: `REVIEW-FR-030` fixes the order.

| Situation | Status |
|---|---|
| Read succeeded | `200` |
| Resource created | `201` + `Location` |
| Accepted for async processing | `202` |
| Command succeeded, nothing to return | `204` |
| Validation failure | `400` |
| Missing or invalid credentials | `401` |
| Authenticated but not permitted | `403` |
| Unknown id, or an id the caller may not see | `404` |
| State transition not legal from the current state | `409` |
| Idempotency key reused with a different body | `409` |
| Payload too large | `413` |
| Unsupported media type | `415` |
| Rate limit exceeded | `429` |
| Downstream unavailable after retry and circuit break | `503` |

`COMMON-API-001` **THE system SHALL return `404`, not `403`, when a caller requests a resource that
exists but belongs to another farmer.** *(Ownership must not be probeable.)*

### 8.3 Error envelope — RFC 9457

`COMMON-API-002` **THE system SHALL return every error as `application/problem+json` conforming to
RFC 9457**, with these members:

```json
{
  "type":     "https://foshol.local/problems/case-not-found",
  "title":    "Case not found",
  "status":   404,
  "detail":   "No case with that identifier is visible to you.",
  "instance": "/api/v1/cases/018f...",
  "code":     "ERR_CASE_NOT_FOUND",
  "correlationId": "8f2c...",
  "errors":   [ { "field": "images", "message": "at most 3 images" } ]
}
```

`code` is a constant from `common.ErrorCodes`; `errors` is present only for validation failures.
`detail` is safe to display and never contains a stack trace, SQL, phone number or object key.

`COMMON-API-003` **THE system SHALL localise `title` and `detail` using the `Accept-Language` header,
defaulting to `bn`.**

### 8.4 Time, ids and text

`COMMON-NFR-011` **THE system SHALL store and transmit every instant in UTC as ISO-8601 with a `Z`
suffix**, and SHALL render times to users in `Asia/Dhaka` (`foshol.i18n.display-zone`). Conversion is
a presentation concern and happens in the frontend only.

`COMMON-NFR-012` **THE system SHALL generate every primary key as a UUIDv7** via `common.Uuid7`, so
that primary-key order approximates insertion order.

`COMMON-NFR-013` **THE system SHALL normalise every Bangla string on input to Unicode NFC**, strip
zero-width non-joiners and zero-width joiners for matching purposes only, and normalise Bengali digit
forms to ASCII digits before comparison. The original string is stored unmodified; the normalised
form is stored alongside it where matching requires it (`symptom_phrase.normalised_bn`).

### 8.5 Validation

`COMMON-NFR-014` **THE system SHALL validate every request body with Jakarta Bean Validation
annotations on the request record**, and SHALL translate a `MethodArgumentNotValidException` into the
`400` problem envelope with a populated `errors` array. Domain invariants are enforced additionally
in the aggregate and are never replaced by annotation validation.

### 8.6 Logging and correlation

`COMMON-NFR-015` **THE system SHALL log in a single-line structured format** with fields
`timestamp level correlationId module message`, using SLF4J with no string concatenation in log
statements.

`COMMON-NFR-016` **WHEN a request arrives without an `X-Correlation-Id` header, THE system SHALL
generate one**; WHEN one is present, THE system SHALL adopt it. THE system SHALL place it in the SLF4J
MDC under `correlationId`, SHALL propagate it on every outbound sidecar call as `X-Correlation-Id`,
SHALL carry it on every domain event, and SHALL return it on every response and in every problem
document.

`COMMON-SEC-001` **THE system SHALL NOT log a phone number, an OTP code, a JWT, a password hash, an
encryption key, or the body of an uploaded image or audio file, at any level, in any profile.**

Levels: `ERROR` unhandled or unrecoverable · `WARN` degraded path taken, circuit opened, unmapped
label, claim expired · `INFO` state transition of a case or review task, one line per transition ·
`DEBUG` handler entry with correlation id · `TRACE` unused.

---

### 8.7 Error-code registry

`COMMON-ARCH-010` forbids string literals for error codes, so every code below SHALL exist as a
constant in `common.ErrorCodes`. `common` is A1-owned, so **A1 declares all of them on Day 0** — a
module agent that needs a code not listed here raises a blocker rather than adding one.

The grouping records which document *first* declares each code, not which module may throw it: any
module may return any code, but only A1 may add to the class.

| Declared by | Codes |
|---|---|
| **A1 · common** | `ERR_CASE_NOT_FOUND` · `ERR_DEV_OTP_IN_NON_DEV_PROFILE` · `ERR_MODEL_LABEL_MAP_INVALID` · `ERR_MODEL_LABEL_MAP_MISSING` |
| **A1 · identity** | `ERR_ACCOUNT_INACTIVE` · `ERR_FORBIDDEN` · `ERR_INVALID_CREDENTIALS` · `ERR_JWT_SECRET_TOO_SHORT` · `ERR_OTP_ATTEMPTS_EXCEEDED` · `ERR_OTP_DISABLED` · `ERR_OTP_EXPIRED` · `ERR_OTP_INVALID` · `ERR_OTP_RATE_LIMITED` · `ERR_PHONE_INVALID` · `ERR_PHONE_KEY_INVALID` · `ERR_SUBJECT_NOT_FOUND` · `ERR_TOKEN_EXPIRED` · `ERR_TOKEN_INVALID` |
| **A2 · intake** | `ERR_AUDIO_NOT_FOUND` · `ERR_AUDIO_TOO_LARGE` · `ERR_AUDIO_TOO_LONG` · `ERR_AUDIO_UNREADABLE` · `ERR_CASE_RATE_LIMITED` · `ERR_CROP_NOT_FOUND` · `ERR_IDEMPOTENCY_KEY_CONFLICT` · `ERR_IDEMPOTENCY_KEY_INVALID` · `ERR_IDEMPOTENCY_KEY_MISSING` · `ERR_IMAGE_COUNT` · `ERR_IMAGE_NOT_FOUND` · `ERR_IMAGE_QUALITY_REJECTED` · `ERR_IMAGE_TOO_LARGE` · `ERR_PARENT_CASE_INVALID` · `ERR_STORAGE_UNAVAILABLE` · `ERR_UNSUPPORTED_MEDIA_TYPE` |
| **A3 · analysis** | `ERR_ALL_LABELS_UNMAPPED` · `ERR_ANALYSIS_NOT_FOUND` · `ERR_EMBEDDING_DIMENSION` · `ERR_FIXTURE_MISSING` · `ERR_NO_REMEDY_FOR_DIAGNOSIS` · `ERR_SIDECAR_UNAVAILABLE` · `ERR_SPEECH_BRANCH_TIMEOUT` · `ERR_UNKNOWN_SYMPTOM` · `ERR_VISION_BRANCH_TIMEOUT` |
| **A4 · knowledge** | `ERR_DISEASE_NOT_FOUND` · `ERR_KB_CONTENT_INVALID` · `ERR_KB_EMBEDDING_MISSING` |
| **A5 · review** | `ERR_ADVISORY_NOT_FOUND` · `ERR_ADVISORY_REQUIRES_REMEDY` · `ERR_CLAIM_CONFLICT` · `ERR_CLAIM_NOT_HELD` · `ERR_QUEUE_SORT_NOT_SUPPORTED` · `ERR_REMEDY_DISEASE_MISMATCH` · `ERR_REMEDY_PHI_MISSING` · `ERR_REVIEW_TASK_NOT_FOUND` · `ERR_TASK_TERMINAL` |
| **A5 · notification** | `ERR_STREAM_LIMIT_EXCEEDED` · `ERR_STREAM_SUBJECT_MISMATCH` |
| **A7 · sidecar** | `ERR_SIDECAR_BAD_REQUEST` · `ERR_SIDECAR_BUSY` · `ERR_SIDECAR_EMBED_DIM_MISMATCH` · `ERR_SIDECAR_FIXTURE_MISSING` · `ERR_SIDECAR_INFERENCE_FAILED` · `ERR_SIDECAR_MODEL_UNAVAILABLE` · `ERR_SIDECAR_PAYLOAD_TOO_LARGE` · `ERR_SIDECAR_UNDECODABLE` · `ERR_SIDECAR_UNKNOWN_CROP` · `ERR_SIDECAR_UNSUPPORTED_MEDIA` · `ERR_SIDECAR_WARMING_UP` |

`COMMON-API-004` **THE `code` member of every problem document SHALL be one of the constants above**,
and THE system SHALL NOT return a code absent from `common.ErrorCodes`.

## 9. Configuration

`COMMON-NFR-020` **THE system SHALL externalise every threshold, limit, timeout and toggle to a
property under the `foshol.` prefix, and SHALL define its name as a constant in
`common.ConfigKeys`.** No numeric literal that a human might want to change may appear in Java
source.

> Note: the plan uses the prefix `fasol.`. The project is **Foshol Doctor**, so the prefix is
> `foshol.` throughout. There is no `fasol.` property.

### 9.1 `application.properties` (all profiles)

```properties
spring.application.name=foshol-doctor
spring.threads.virtual.enabled=true
spring.profiles.default=local

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.modulith.shared-modules=common
spring.modulith.events.jdbc.schema-initialization.enabled=true
spring.modulith.events.republish-outstanding-events-on-restart=true

spring.servlet.multipart.max-file-size=8MB
spring.servlet.multipart.max-request-size=32MB

management.endpoints.web.exposure.include=health,info,metrics,modulith
management.endpoint.health.show-details=when-authorized

# ── identity ────────────────────────────────────────────────────────────────
foshol.auth.jwt.issuer=foshol-doctor
foshol.auth.jwt.ttl=PT8H
foshol.auth.jwt.secret=${FOSHOL_JWT_SECRET}
foshol.auth.otp.enabled=true
foshol.auth.otp.ttl=PT5M
foshol.auth.otp.max-attempts=5
foshol.auth.otp.length=6
foshol.auth.otp.rate-limit.max-requests=3
foshol.auth.otp.rate-limit.window=PT10M
foshol.crypto.phone.key=${FOSHOL_PHONE_KEY}

# ── storage ─────────────────────────────────────────────────────────────────
foshol.storage.endpoint=http://localhost:9000
foshol.storage.access-key=${FOSHOL_MINIO_ACCESS_KEY}
foshol.storage.secret-key=${FOSHOL_MINIO_SECRET_KEY}
foshol.storage.bucket=foshol-cases
foshol.storage.derivative-max-edge-px=1024
foshol.storage.presign-ttl=PT10M

# ── intake ──────────────────────────────────────────────────────────────────
foshol.intake.max-images=3
foshol.intake.min-images=1
foshol.intake.max-image-bytes=8388608
foshol.intake.allowed-image-types=image/jpeg,image/png,image/webp
foshol.intake.max-audio-seconds=30
foshol.intake.max-audio-bytes=4194304
foshol.intake.allowed-audio-types=audio/wav,audio/webm,audio/ogg,audio/mp4
foshol.intake.idempotency.ttl=PT24H
foshol.intake.rate-limit.max-cases=20
foshol.intake.rate-limit.window=PT1H
foshol.intake.quality.blur-variance-min=60.0
foshol.intake.quality.exposure-min=0.15
foshol.intake.quality.exposure-max=0.90
foshol.intake.quality.min-edge-px=224

# ── analysis ────────────────────────────────────────────────────────────────
foshol.analysis.confidence.high=0.75
foshol.analysis.confidence.low=0.45
foshol.analysis.confidence.temperature=1.0
foshol.analysis.multi-image.aggregation=MAX
foshol.analysis.deadline=PT12S
foshol.analysis.candidate-limit=5
foshol.analysis.gradcam.enabled=true

# ── ai sidecar ──────────────────────────────────────────────────────────────
foshol.ai.mode=replay
foshol.ai.base-url=http://localhost:8000
foshol.ai.timeout=PT8S
foshol.ai.vision.rice.model-id=kssrikar4/Rice-Leaf-Disease-Classification
foshol.ai.vision.rice.model-revision=02a6e6ea1b5da9b0458b12c4ec8bccd0582a4f26
foshol.ai.vision.rice.fallback-model-id=prithivMLmods/Rice-Leaf-Disease
foshol.ai.vision.rice.fallback-model-revision=170d10e070c308e0e5337690d67371825591f35b
foshol.ai.vision.solanaceae.model-id=Daksh159/plant-disease-mobilenetv2
foshol.ai.vision.solanaceae.model-revision=d3fb2afc90da83086eff06e9088a889b6c43d4a6
foshol.ai.asr.model-id=ashrafulparan/whisper-small-bangla
foshol.ai.asr.model-revision=25c88973563146654493b97882fb2806d2fdeaaa
foshol.ai.embed.model-id=sentence-transformers/LaBSE
foshol.ai.embed.model-revision=836121a0533e5664b21c7aacc5d22951f2b8b25b

# ── knowledge ───────────────────────────────────────────────────────────────
foshol.knowledge.match.vector-threshold=0.72
foshol.knowledge.match.fuzzy-threshold=0.60
foshol.knowledge.match.max-symptoms=8
foshol.knowledge.match.knn-limit=25
foshol.knowledge.match.inconclusive-score-min=0.30

# ── review ──────────────────────────────────────────────────────────────────
foshol.review.claim.ttl=PT15M
foshol.review.sla=PT4H
foshol.review.sweeper.interval=PT1M

# ── notification ────────────────────────────────────────────────────────────
foshol.channels.sse.enabled=true
foshol.channels.sse.heartbeat=PT20S
foshol.channels.sse.timeout=PT30M
foshol.channels.webpush.enabled=false
foshol.channels.sms.enabled=false

# ── i18n ────────────────────────────────────────────────────────────────────
foshol.i18n.default-locale=bn
foshol.i18n.supported-locales=bn,en
foshol.i18n.display-zone=Asia/Dhaka

# ── resilience ──────────────────────────────────────────────────────────────
resilience4j.circuitbreaker.instances.sidecar.sliding-window-size=10
resilience4j.circuitbreaker.instances.sidecar.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.sidecar.wait-duration-in-open-state=20s
resilience4j.circuitbreaker.instances.sidecar.permitted-number-of-calls-in-half-open-state=3
resilience4j.timelimiter.instances.sidecar.timeout-duration=8s
resilience4j.retry.instances.sidecar.max-attempts=2
resilience4j.retry.instances.sidecar.wait-duration=500ms
resilience4j.retry.instances.sidecar.exponential-backoff-multiplier=2
```

### 9.2 Profile overlays

```properties
# application-local.properties
spring.datasource.url=jdbc:postgresql://localhost:5432/foshol
spring.datasource.username=foshol
spring.datasource.password=${FOSHOL_DB_PASSWORD}
foshol.datasource.read-only.url=jdbc:postgresql://localhost:5432/foshol
foshol.auth.otp.dev-code=123456
foshol.ai.mode=live
logging.level.com.rootcause.foshol=DEBUG

# application-demo.properties
spring.flyway.locations=classpath:db/migration,classpath:db/seed
foshol.auth.otp.dev-code=123456
foshol.ai.mode=replay
logging.level.com.rootcause.foshol=INFO

# application-test.properties
spring.flyway.locations=classpath:db/migration
foshol.auth.otp.dev-code=000000
foshol.ai.mode=replay
foshol.analysis.deadline=PT3S
foshol.review.sweeper.interval=PT5S
logging.level.com.rootcause.foshol=DEBUG
```

`COMMON-SEC-002` **THE repository SHALL contain no secret value.** `FOSHOL_JWT_SECRET`,
`FOSHOL_PHONE_KEY`, `FOSHOL_DB_PASSWORD`, `FOSHOL_MINIO_ACCESS_KEY` and `FOSHOL_MINIO_SECRET_KEY`
are supplied by the environment. `docker-compose.yml` reads them from a `.env` file that is listed in
`.gitignore` and shipped as `.env.example` with placeholder values.

`COMMON-SEC-003` **IF `foshol.auth.otp.dev-code` is set while the active profile is neither `local`,
`demo` nor `test`, THEN THE application SHALL fail to start with error code
`ERR_DEV_OTP_IN_NON_DEV_PROFILE`.**

### 9.3 Replay and live

`COMMON-NFR-021` **THE system SHALL support `foshol.ai.mode` values `replay` and `live`, and both
SHALL be fully functional.**

`COMMON-NFR-022` **WHILE `foshol.ai.mode=replay`, THE analysis module SHALL obtain every sidecar
response from the fixture store keyed by image SHA-256 (or audio SHA-256), and SHALL record
`analysis_run.mode = 'REPLAY'`.**

`COMMON-NFR-023` **WHILE `foshol.ai.mode=live`, THE analysis module SHALL call the sidecar over HTTP
and SHALL record `analysis_run.mode = 'LIVE'`.**

`COMMON-NFR-024` **THE continuous integration pipeline SHALL execute the end-to-end slice test in
both `replay` and `live` mode**, the live run in a separate workflow
(`.github/workflows/verify-live.yml`) with a cached Hugging Face model directory. *(A system that
only ever runs in replay is not a system. The live job is permitted to be slow.)*

`COMMON-UX-001` **THE officer console SHALL display the analysis mode (`REPLAY` or `LIVE`) on every
case detail view.** *(So the team never mistakes a fixture for a live inference.)*

---

## 10. Cross-cutting non-functional requirements

### 10.1 Security

`COMMON-SEC-010` **THE system SHALL authenticate every endpoint under `/api/v1` except
`/api/v1/auth/**` and `/actuator/health`.**

`COMMON-SEC-011` **THE system SHALL authorise by role**: `FARMER` may read only its own cases and
advisories; `OFFICER` may read any case and act on review tasks; `ADMIN` additionally may read
`/api/v1/admin/**`. The rule for every endpoint is stated in that module's API section.

`COMMON-SEC-012` **THE system SHALL issue a stateless JWT (HS256) carrying `sub` (subject id), `role`
and `exp`**, signed with `foshol.auth.jwt.secret`, valid for `foshol.auth.jwt.ttl`. There is no
refresh token and no server-side session.

`COMMON-SEC-013` **THE system SHALL store farmer and officer phone numbers encrypted at rest**
(AES-256-GCM, `foshol.crypto.phone.key`) and SHALL index them only by SHA-256 hash.

`COMMON-SEC-014` **THE system SHALL reject an upload exceeding `foshol.intake.max-image-bytes` or
`foshol.intake.max-audio-bytes` with `413`, and one whose detected content type is not in the allowed
list with `415`.** Content type is determined by **magic-byte sniffing**, not by the client-supplied
`Content-Type` header or the filename extension.

`COMMON-SEC-015` **THE system SHALL rate-limit `POST /api/v1/auth/otp/request` to
`foshol.auth.otp.rate-limit.max-requests` per phone hash per `foshol.auth.otp.rate-limit.window`, and
`POST /api/v1/cases` to `foshol.intake.rate-limit.max-cases` per farmer per
`foshol.intake.rate-limit.window`, returning `429` with a `Retry-After` header.**

`COMMON-SEC-016` **THE system SHALL serve stored objects only through a time-limited presigned URL
(`foshol.storage.presign-ttl`) issued after an ownership check.** MinIO object keys are never
guessable resources and the bucket is never public.

`COMMON-SEC-017` **THE system SHALL apply CORS to the frontend origin only**, configured per profile.

`COMMON-SEC-018` `[DERIVED]` **THE system SHALL serve the frontend over HTTPS from a certificate the
demo mobile device trusts, and SHALL make that origin reachable from a phone on the same network.**

*(This is a hard functional prerequisite, not a hardening nicety. `navigator.mediaDevices` — and
therefore `getUserMedia`, and therefore the entire Bangla voice feature F4 — is exposed **only in a
secure context**: HTTPS, or the `localhost` / `127.0.0.1` exemption. A farmer's phone loading
`http://192.168.x.x:8080` does not qualify: `navigator.mediaDevices` is `undefined` and the recorder
cannot start at all. Every browser enforces this and no flag on the server side changes it. Serving
the demo over plain HTTP on a LAN address silently removes F4 from the product on every phone.)*

`COMMON-SEC-019` `[DERIVED]` **THE `docker-compose.yml` SHALL terminate TLS for the frontend origin
using a locally generated certificate (for example `mkcert`), and the repository SHALL document
installing that CA on the demo phone as a setup step in `README.md`.**

*(Chosen over a public tunnel because it works with no internet at the venue. A tunnel — `cloudflared`
or `ngrok` — is documented as the fallback when the LAN blocks device-to-device traffic, which some
guest and conference networks do. Client-isolated Wi-Fi is the failure mode to test for on arrival:
if phones cannot reach the laptop at all, the tunnel is the only route and it needs internet.)*

`COMMON-SEC-020` **THE `localhost` origin SHALL continue to work over plain HTTP for desktop
development**, so no developer or test needs a certificate.

`COMMON-NFR-047` `[DERIVED]` **THE system SHALL treat mobile browsers as a supported target for the
farmer surface**, specifically current Chrome on Android and current Safari on iOS. *(Farmers load
this on a phone. A capture pipeline verified only in desktop Chromium is not evidence that the
feature works for its actual users — and Safari differs from Chromium in exactly the two places this
pipeline touches, container format and audio-context lifecycle.)*

### 10.2 Performance budgets

Measured at p95 on the demo machine, excluding model inference time.

| Operation | Budget | Property |
|---|---|---|
| `POST /api/v1/cases` returns `202` | 800 ms | — |
| Analysis pipeline end to end (replay) | 1.5 s | `foshol.analysis.deadline` |
| Analysis pipeline end to end (live, CPU) | 12 s | `foshol.analysis.deadline` |
| Officer queue page load | 300 ms | — |
| Case detail load | 500 ms | — |
| SSE event from `AdvisoryApproved` to browser | 1 s | — |

`COMMON-NFR-030` **THE analysis pipeline SHALL abandon any branch that exceeds
`foshol.analysis.deadline` and SHALL produce a result from whatever branches completed**, rather than
failing the case.

### 10.3 Observability

`COMMON-NFR-031` **THE system SHALL expose `/actuator/health`, `/actuator/info`,
`/actuator/metrics` and `/actuator/modulith`.**

`COMMON-NFR-032` **THE system SHALL register a health indicator for the sidecar, MinIO and the
database**, and the sidecar indicator SHALL report `DEGRADED`-equivalent detail rather than failing
overall health when `foshol.ai.mode=replay`.

`COMMON-NFR-033` **THE system SHALL record a timer metric per outbound sidecar call
(`foshol.ai.call`, tagged by endpoint and outcome) and a counter for each decision path
(`foshol.analysis.path`, tagged `PRIMARY|SECONDARY|UNDETERMINED`).**

### 10.4 Resilience

`COMMON-NFR-034` **THE system SHALL wrap every outbound sidecar call in a Resilience4j time limiter,
retry and circuit breaker under the instance name `sidecar`** (configured in §9.1).

`COMMON-NFR-035` **IF the sidecar circuit is open or a call fails after retry, THEN THE analysis
module SHALL fall back to `UNDETERMINED`, SHALL record `analysis_run.error_code`, and SHALL still
publish `AnalysisCompleted` so the case reaches the officer queue.** *(This is the live
sidecar-kill demo: degradation, not failure.)*

`COMMON-NFR-036` **IF MinIO is unavailable during submission, THEN THE system SHALL return `503` and
SHALL NOT persist a partial case.**

### 10.5 Internationalisation

`COMMON-NFR-037` **THE system SHALL treat Bangla as the language of record for all crop, disease,
symptom and remedy content.** `*_en` columns are nullable.

`COMMON-NFR-038` **IF an English string is requested and the `*_en` value is null, THEN THE system
SHALL return the Bangla value and SHALL set a sibling boolean field `<field>Fallback: true`** so the
frontend can mark it. *(Requiring English parity would gate content curation on translation work
nobody has time for.)*

`COMMON-NFR-039` **THE frontend SHALL provide a runtime BN/EN toggle covering all UI chrome**, and
SHALL render content fields per `COMMON-NFR-038`.

---

## 11. Definition of Done

A module is done when **every** line below is true. There is no partial credit.

1. `./gradlew build` completes with no error and no new warning.
2. Every unit test passes: domain invariants and **every** command and query handler.
3. The module's single Testcontainers integration test passes.
4. `ApplicationModules.verify()` passes.
5. `ArchitectureTests` passes — all of `COMMON-ARCH-001` … `COMMON-ARCH-013`.
6. Flyway migrations apply cleanly from an empty database (`docker compose down -v && docker compose up`).
7. Every endpoint the module declares matches `docs/openapi/foshol-api.yaml` in path, method, status
   codes and schema.
8. No `TODO`, no commented-out code, no `System.out`, no unused import.
9. Every requirement ID in the module's document is either implemented or explicitly listed as
   `[DEFERRED]` in the module's progress note.
10. No agronomic content was authored (`COMMON-CON-003`).

---

## 12. Agent coordination protocol

### 12.1 File ownership

| Agent | Owns (exclusive write access) |
|---|---|
| **A1 platform + identity** | `buildSrc/`, `settings.gradle`, root `build.gradle`, `gradle/libs.versions.toml`, `docker-compose.yml`, `.github/workflows/`, `common/`, `app/` (including **all** migrations and `application*.properties`), `modules/identity/`, `docs/openapi/foshol-api.yaml`, this document |
| **A2 intake** | `modules/intake/` |
| **A3 analysis** | `modules/analysis/` |
| **A4 knowledge** | `modules/knowledge/` |
| **A5 review + notification** | `modules/review/`, `modules/notification/` |
| **A6 frontend** | `web/` |
| **A7 sidecar** | `sidecar/`, `tools/` |

`COMMON-NFR-040` **THE agent SHALL NOT create, edit or delete a file outside its ownership column.** A
needed change to a file owned by another agent is raised as a blocker (§12.3), never made directly.

`COMMON-NFR-041` **THE `api` package of every module SHALL be frozen after Day 0 and SHALL NOT be
edited by any agent.** An agent that believes
a published signature is wrong raises a blocker; it does not change the signature. *(This is the
single highest-value gate in the whole build: with contracts frozen, agents working in parallel
cannot break each other.)*

`COMMON-NFR-042` **A module agent SHALL NOT add a Flyway migration.** Schema changes go to A1.

`COMMON-NFR-043` **THE frontend agent SHALL NOT hand-edit `web/src/app/generated/`.** It is
regenerated from `docs/openapi/foshol-api.yaml`.

### 12.2 Integration checkpoints

Four checkpoints, one at the end of each day. At each, A1 runs the full verification gate on `main`:

```
./gradlew clean build            # compile + unit tests + ArchUnit + Modulith
docker compose down -v && docker compose up -d && ./gradlew integrationTest
```

`COMMON-NFR-044` **THE `main` branch SHALL be demoable at every checkpoint.** An agent whose work does not pass
the gate does not merge; it reverts to the last green commit and reports a blocker.

### 12.3 Progress and blockers

`COMMON-NFR-045` **THE agent SHALL maintain `docs/progress/<agent>.md`** — the only file outside its
ownership area it may create — with three sections updated at every checkpoint:

```markdown
## Done          requirement IDs completed, one per line
## In progress   requirement ID + what remains
## Blocked       requirement ID · what is blocking · which agent or human owns the unblock · since when
```

`COMMON-NFR-046` **IF an agent cannot satisfy a requirement without violating an architectural law,
a frozen contract or `COMMON-CON-003`, THEN THE agent SHALL stop, record the blocker, and SHALL NOT
work around it.** A workaround that violates a law is worse than an unimplemented requirement,
because it is invisible until integration.

---

## 13. Requirement index for this document

| Category | IDs |
|---|---|
| Constraints | `COMMON-CON-001` … `COMMON-CON-003` |
| Architecture | `COMMON-ARCH-001` … `COMMON-ARCH-018` |
| Data | `COMMON-DATA-001` … `COMMON-DATA-020` |
| API | `COMMON-API-001` … `COMMON-API-004` |
| Security | `COMMON-SEC-001` … `COMMON-SEC-020` |
| Non-functional | `COMMON-NFR-001` … `COMMON-NFR-047` |
| UX | `COMMON-UX-001` |
