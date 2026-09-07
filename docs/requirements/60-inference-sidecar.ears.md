# 60 — Inference Sidecar (EARS)

**Owner:** agent **A7**. **Deployable:** separate container, separate language, separate Dockerfile.
**Paths owned:** `sidecar/`, `tools/`. **Reads:** `docs/requirements/00-common.ears.md` and this file only.

> This is **not** a Spring Modulith module. It is a stateless HTTP service with no database, no domain
> model and no knowledge of the project taxonomy. The nine mandatory sections are kept, but §3
> describes the **request/response schemas and the model registry** in place of aggregates, and §6
> describes the **fixture store and the model cache** in place of tables.
>
> `00-common.ears.md` wins on every conflict. Requirements marked `[DERIVED]` extend the plan and
> carry inline reasoning.

---

## 1. Scope

### 1.1 What the sidecar owns

| Owned | Detail |
|---|---|
| Model loading | Every model in `00-common.ears.md` §2.4, loaded **by identifier from configuration**, pretrained weights only (`COMMON-CON-001`, `COMMON-NFR-005`) |
| Vision classification | `POST /v1/vision/classify` — ranked `{raw_label, confidence}` for one image |
| Visual explanation | `POST /v1/vision/explain` — a Grad-CAM / attention overlay PNG |
| Bangla transcription | `POST /v1/asr/transcribe` — transcript plus a confidence |
| Text embedding | `POST /v1/embed` — 768-dimensional vectors |
| Introspection | `GET /health`, `GET /v1/models` |
| Replay | The SHA-256-keyed fixture store in `sidecar/fixtures/`, including pre-baked overlay PNGs |
| Tools | `tools/build_fixtures.py`, `tools/eval.py`, `tools/seed_cases.py` |

### 1.2 What the sidecar does **not** own

| Not owned | Owner |
|---|---|
| Mapping a raw model label onto a `disease` | `knowledge` (`model_label_map`, `COMMON-DATA-010`…`017`) |
| Confidence thresholds and decision-path routing | `analysis` (`foshol.analysis.confidence.high` / `.low`) |
| Multi-image aggregation and primary-image selection | `analysis` (`foshol.analysis.multi-image.aggregation`) |
| Symptom retrieval, pgvector kNN, fuzzy fallback, `disease_symptom` scoring | `knowledge` (`SymptomMatchApi`) — see `SIDECAR-FR-055` |
| Storing the overlay PNG | `analysis` (`analysis_run.gradcam_object_key`) |
| Image quality gating, upload limits, idempotency | `intake` |
| Any database table, row or connection | Every Spring module; the sidecar has **none** (`SIDECAR-SEC-001`) |
| Authentication and authorisation | `identity`; the sidecar is network-isolated instead (`SIDECAR-SEC-002`) |

`SIDECAR-FR-016` **THE sidecar SHALL treat `crop_code` as an opaque routing key and SHALL return every
model label verbatim as `raw_label`, and SHALL NOT contain any disease code, disease name, symptom,
remedy or other project-taxonomy value.** *(The label space belongs to the model; the taxonomy belongs
to `knowledge`. Keeping them apart is what makes `COMMON-DATA-016` enforceable.)*

`SIDECAR-NFR-014` **THE sidecar SHALL load pretrained weights only, per `COMMON-CON-001`.**

---

## 2. Dependencies

### 2.1 Inbound

The sidecar publishes **no** Java interface and **no** domain event. It is called over HTTP by the
`analysis` module's outbound adapters. Its contract with the rest of the system is §5 of this document
plus the common requirements below, which it must satisfy exactly:

| Common requirement | Obligation on the sidecar |
|---|---|
| `COMMON-NFR-005` | Every model is loaded by identifier from configuration; no source change to swap one |
| `COMMON-NFR-006` | The embedding model produces 768-dimensional vectors |
| `COMMON-DATA-017` | Every classification response reports the `model_id` and `model_version` that **actually ran** |
| `COMMON-NFR-016` | `X-Correlation-Id` is adopted when present, generated when absent, and returned |
| `COMMON-NFR-021`…`024` | `replay` and `live` are both fully functional; `live` is exercised by `.github/workflows/verify-live.yml` |
| `COMMON-API-002` | Every error is `application/problem+json` (RFC 9457) |
| `COMMON-SEC-001` | No phone number, no upload body, no secret is ever logged |
| `COMMON-SEC-014` | Content type is determined by magic-byte sniffing, never by filename or header |
| `COMMON-NFR-013` | Bangla text is NFC-normalised, ZWNJ/ZWJ-stripped and digit-folded before embedding |
| `COMMON-CON-002` | No published model-card accuracy, WER, F1 or precision figure appears anywhere |

### 2.2 Outbound

`SIDECAR-SEC-007` **THE sidecar SHALL make no outbound network call while serving a request.** Model
weights are fetched only during image build or first start-up, into the mounted cache of
`SIDECAR-DATA-002`. *(A request-time download is a request-time outage.)*

### 2.3 Where the pgvector kNN went — `[DERIVED]`

`SIDECAR-FR-055` **THE sidecar SHALL NOT expose a `/v1/symptoms/extract` endpoint, and `POST /v1/embed`
SHALL be its only text-understanding endpoint.**

*Reasoning (clarification item 10). Plan §5 gives the sidecar a `/v1/symptoms/extract` endpoint, while
plan §7.2's pipeline queries `symptom_phrase.embedding` — a table the sidecar does not own and cannot
reach. The contradiction is resolved by moving retrieval to the module that owns the data: the sidecar
turns text into a vector, and `knowledge` performs the pgvector HNSW kNN, the fuzzy token-overlap
fallback and the weighted `disease_symptom` scoring behind `SymptomMatchApi`. One owner per table is
what lets seven agents work in parallel, and "the AI can be wrong, the knowledge base is
authoritative" only holds if the knowledge-base lookup lives in the knowledge module.*

---

## 3. Request/response schemas and the model registry

### 3.1 The model registry

Five registry entries, each a `(role, model_id, revision)` triple resolved from configuration at
start-up. `role` is internal to the sidecar; `model_id` and `model_version` are the contract.

| Role | Model identifier property | Architecture family |
|---|---|---|
| `vision.rice.primary` | `foshol.ai.vision.rice.model-id` | Swin (hierarchical ViT), 6 labels |
| `vision.rice.fallback` | `foshol.ai.vision.rice.fallback-model-id` | SigLIP2 (ViT + attention pooling), 5 labels |
| `vision.solanaceae` | `foshol.ai.vision.solanaceae.model-id` | MobileNetV2 (CNN) |
| `asr.bangla` | `foshol.ai.asr.model-id` | Whisper family, served by `faster-whisper` |
| `embed.text` | `foshol.ai.embed.model-id` | Sentence-transformer, 768-d |

`SIDECAR-DATA-006` **THE sidecar SHALL read each model identifier from an environment variable whose
name is the property name of `00-common.ears.md` §9.1 upper-cased with `.` replaced by `_`** — e.g.
`FOSHOL_AI_VISION_RICE_MODEL_ID` — and `docker-compose.yml` SHALL pass the same value that the Spring
application reads. *(One value, two consumers, no second source of truth.)*

`SIDECAR-FR-003` **THE sidecar SHALL resolve each model to a pinned revision from
`<property>.revision`, SHALL report that resolved revision as `model_version`, and SHALL fail to start
if a required revision is unset.** `[DERIVED]` — `00-common.ears.md` §9.1 pins model identifiers but
not revisions, while `model_label_map` is keyed on `(model_id, model_version, raw_label)`
(`COMMON-DATA-010`). Without a pinned revision the mapping key is not reproducible.

`SIDECAR-DATA-007` **IF a model revision changes, THEN THE sidecar agent SHALL raise a blocker for
agent A1 and SHALL NOT proceed, because a new `model_label_map` migration is required.** *(Per
`COMMON-NFR-042`, A7 does not author migrations.)*

### 3.2 Sidecar-local configuration `[DERIVED]`

These values have no entry in `00-common.ears.md` §9.1 because they configure the sidecar process, not
the Spring application. They are environment variables of the `sidecar` container only. Any value the
two share is listed with the common property it must equal.

| Variable | Default | Note |
|---|---|---|
| `FOSHOL_AI_MODE` | `replay` | Must equal `foshol.ai.mode` |
| `FOSHOL_AI_VISION_CROP_ROUTES` | — | `crop_code`→role map, e.g. `<code>=rice,<code>=solanaceae`; values supplied at deployment |
| `FOSHOL_SIDECAR_VISION_TOP_K` | `5` | Must equal `foshol.analysis.candidate-limit` |
| `FOSHOL_SIDECAR_MAX_CONCURRENT` | `2` | Inference semaphore permits |
| `FOSHOL_SIDECAR_QUEUE_TIMEOUT_MS` | `2000` | Wait for a permit before `429` |
| `FOSHOL_SIDECAR_MAX_IMAGE_BYTES` | `8388608` | Must equal `foshol.intake.max-image-bytes` |
| `FOSHOL_SIDECAR_MAX_AUDIO_BYTES` | `4194304` | Must equal `foshol.intake.max-audio-bytes` |
| `FOSHOL_SIDECAR_MAX_AUDIO_SECONDS` | `30` | Must equal `foshol.intake.max-audio-seconds` |
| `FOSHOL_SIDECAR_ASR_LANGUAGE` | `bn` | |
| `FOSHOL_SIDECAR_ASR_TARGET_LUFS` | `-23.0` | Loudness-normalisation target |
| `FOSHOL_SIDECAR_EXPLAIN_ALPHA` | `0.45` | Overlay opacity |
| `FOSHOL_SIDECAR_EXPLAIN_MAX_EDGE_PX` | `1024` | Must equal `foshol.storage.derivative-max-edge-px` |
| `FOSHOL_SIDECAR_EMBED_MAX_BATCH` | `32` | |
| `FOSHOL_SIDECAR_EMBED_MAX_CHARS` | `2000` | |
| `FOSHOL_SIDECAR_TORCH_THREADS` | `2` | `torch.set_num_threads` |
| `FOSHOL_SIDECAR_FIXTURE_DIR` | `/app/fixtures` | |

`SIDECAR-NFR-020` **THE sidecar SHALL define every configuration key once in `sidecar/app/config.py`
and SHALL NOT read an environment variable anywhere else.**

`SIDECAR-NFR-021` **THE sidecar SHALL define every error code once in `sidecar/app/errors.py`.**
`[DERIVED]` — the mirror of `COMMON-ARCH-010` for a non-Java process.

### 3.3 Shared response envelope

Every JSON response body carries: `mode` (`REPLAY`|`LIVE`), `model_id`, `model_version`,
`inference_ms`, `correlation_id`. Every response carries the `X-Correlation-Id` header.

### 3.4 Error codes

| Code | Status | Meaning |
|---|---|---|
| `ERR_SIDECAR_BAD_REQUEST` | 400 | Missing or malformed field |
| `ERR_SIDECAR_UNKNOWN_CROP` | 400 | `crop_code` has no route |
| `ERR_SIDECAR_FIXTURE_MISSING` | 404 | Replay mode, no fixture for that digest |
| `ERR_SIDECAR_PAYLOAD_TOO_LARGE` | 413 | Byte or duration limit exceeded |
| `ERR_SIDECAR_UNSUPPORTED_MEDIA` | 415 | Sniffed content type not allowed |
| `ERR_SIDECAR_UNDECODABLE` | 422 | Bytes are of an allowed type but cannot be decoded |
| `ERR_SIDECAR_BUSY` | 429 | No inference permit within the queue timeout |
| `ERR_SIDECAR_INFERENCE_FAILED` | 500 | Model raised during inference |
| `ERR_SIDECAR_MODEL_UNAVAILABLE` | 503 | No usable model for the requested role |
| `ERR_SIDECAR_WARMING_UP` | 503 | Start-up warm-up incomplete |

---

## 4. Requirements in EARS syntax

### 4.1 Process lifecycle and model loading

`SIDECAR-FR-001` **WHEN the sidecar starts, THE sidecar SHALL read `FOSHOL_AI_MODE` and SHALL operate
in that mode for the life of the process.**

`SIDECAR-FR-002` **WHILE the mode is `LIVE`, THE sidecar SHALL load every registry model of §3.1 by
identifier and pinned revision before accepting traffic.**

`SIDECAR-FR-004` **WHEN model loading completes, THE sidecar SHALL execute exactly one warm-up
inference per loaded model against a synthetically generated input, and SHALL report `warm: true` only
after every warm-up has returned.** *(The first real request must not be the slow one; a demo's first
photograph is the one on stage.)*

`SIDECAR-FR-005` **WHILE the mode is `REPLAY`, THE sidecar SHALL load no model weights and SHALL serve
every endpoint from the fixture store.** *(Replay is the Day-1 deliverable and must start on a machine
that has never downloaded a weight file.)*

`SIDECAR-FR-006` **IF a required model identifier or revision is unset, or a configured model fails to
load, THEN THE sidecar SHALL log an `ERROR` naming the role and SHALL exit non-zero**, except for
`vision.rice.primary`, which is governed by `SIDECAR-FR-020`.

`SIDECAR-FR-007` **THE sidecar SHALL report its own `mode` in every response body and in the
`X-Foshol-Mode` header.** `[DERIVED]` — the same reasoning as `COMMON-DATA-017`: the analysis module
must be able to detect a sidecar whose mode disagrees with `foshol.ai.mode` rather than mislabel an
`analysis_run` row.

### 4.2 Vision classification

`SIDECAR-FR-010` **WHEN a valid image and `crop_code` are posted to `/v1/vision/classify`, THE sidecar
SHALL return the top `FOSHOL_SIDECAR_VISION_TOP_K` predictions as `{raw_label, confidence, rank}`,
ordered by descending confidence with `rank` starting at 1.**

`SIDECAR-FR-011` **THE sidecar SHALL route an image to a vision model using
`FOSHOL_AI_VISION_CROP_ROUTES`**, so that a rice `crop_code` is classified by the rice model and a
tomato or potato `crop_code` by the solanaceae model.

`SIDECAR-FR-012` **IF `crop_code` has no entry in the route map, THEN THE sidecar SHALL return `400`
with `ERR_SIDECAR_UNKNOWN_CROP` and SHALL NOT run any model.**

`SIDECAR-FR-013` **THE sidecar SHALL return raw softmax probabilities in `[0,1]` rounded to four
decimal places, and SHALL apply no temperature, calibration or re-scaling.** *(Four places match
`case_candidate.confidence numeric(5,4)`. Calibration is `analysis`'s deferred concern via
`foshol.analysis.confidence.temperature`.)*

`SIDECAR-FR-014` **THE sidecar SHALL report in every classification response the `model_id` and
`model_version` of the model that actually produced the predictions, and THE caller SHALL key the
label mapping on those reported values rather than on its own configuration** (`COMMON-DATA-017`).

`SIDECAR-FR-015` **THE sidecar SHALL return the lowercase hexadecimal SHA-256 of the received image
bytes as `image_sha256`.** *(It is the replay key and the join back to `case_image.sha256`.)*

### 4.3 Rice fallback

`SIDECAR-FR-020` **IF `vision.rice.primary` fails to load at start-up, THEN THE sidecar SHALL continue
starting with `vision.rice.fallback` serving rice, SHALL log a `WARN`, and SHALL report `DEGRADED` from
`GET /health`.**

`SIDECAR-FR-021` **IF inference on `vision.rice.primary` raises, THEN THE sidecar SHALL retry the same
request once on `vision.rice.fallback` and SHALL return that result.**

`SIDECAR-FR-022` **WHERE the request supplies `model_role=fallback`, THE sidecar SHALL use
`vision.rice.fallback` regardless of the primary's availability.** *(Needed by `tools/eval.py`, which
must be able to evaluate either rice model without a redeployment.)*

`SIDECAR-FR-023` **WHEN the fallback rice model produces a result, THE sidecar SHALL report
`model_id` and `model_version` of the fallback, `model_role: "fallback"`, `fallback_used: true` and a
`fallback_reason` of `PRIMARY_NOT_LOADED`, `PRIMARY_INFERENCE_ERROR` or `REQUESTED`.** *(The reported
identity is always the model that ran. Two rice models have disjoint label spaces, so reporting the
configured model instead of the executed one would map every prediction wrongly — silently.)*

`SIDECAR-FR-024` **IF neither rice model is usable, THEN THE sidecar SHALL return `503` with
`ERR_SIDECAR_MODEL_UNAVAILABLE`**, which the analysis module degrades to `UNDETERMINED`
(`COMMON-NFR-035`).

`SIDECAR-FR-025` **WHEN a fallback is used for a reason other than `REQUESTED`, THE sidecar SHALL log
one `WARN` line carrying the correlation id and the reason.**

### 4.4 Visual explanation

`SIDECAR-FR-030` **WHEN an image and `crop_code` are posted to `/v1/vision/explain`, THE sidecar SHALL
return a single `image/png` overlay of the same aspect ratio as the input, bounded on its longest edge
by `FOSHOL_SIDECAR_EXPLAIN_MAX_EDGE_PX`.**

`SIDECAR-FR-031` **WHERE the routed model is of the Swin family, THE sidecar SHALL produce the
saliency map as a gradient-weighted class activation map over the output of the final Swin stage's
normalisation layer, with the token sequence reshaped to its `H/32 × W/32` spatial grid.** *(Swin's
shifted-window attention is not a single global attention matrix, so an attention rollout is
ill-defined across stages; the final stage's feature map is.)*

`SIDECAR-FR-032` **WHERE the routed model is of the SigLIP2 family, THE sidecar SHALL produce the
saliency map from the attention weights of the final block's attention-pooling head over the patch
tokens, reshaped to the patch grid.**

`SIDECAR-FR-033` **WHERE the routed model is of the MobileNetV2 family, THE sidecar SHALL produce the
saliency map as a gradient-weighted class activation map over the output of the final convolutional
feature block.**

`SIDECAR-FR-034` **THE sidecar SHALL bilinearly upsample the saliency map to the output size,
min-max normalise it to `[0,1]`, apply a single fixed perceptually-ordered colour map, and
alpha-composite it over the input at `FOSHOL_SIDECAR_EXPLAIN_ALPHA`.**

`SIDECAR-FR-035` **THE sidecar SHALL compute the overlay for the top-1 label by default, and WHERE
`target_label` is supplied, SHALL compute it for that label.** **IF `target_label` is not in the
routed model's label space, THEN THE sidecar SHALL return `400`.**

`SIDECAR-FR-036` **THE sidecar SHALL return `X-Foshol-Model-Id`, `X-Foshol-Model-Version`,
`X-Foshol-Target-Label`, `X-Foshol-Method` and `X-Foshol-Mode` headers on every overlay response.**
*(The body is a PNG, so the response envelope of §3.3 travels in headers.)*

`SIDECAR-FR-037` **THE sidecar SHALL select the explain model by the same routing and fallback rules
as `/v1/vision/classify`**, so that an overlay always depicts the model whose prediction the officer is
looking at.

### 4.5 Bangla transcription

`SIDECAR-FR-040` **WHEN audio is posted to `/v1/asr/transcribe`, THE sidecar SHALL return the Bangla
transcript, a confidence, a segment list and the audio duration.**

`SIDECAR-FR-041` **THE sidecar SHALL decode the audio, downmix it to one channel and resample it to
16 000 Hz before inference.**

`SIDECAR-FR-041a` `[DERIVED]` **THE sidecar SHALL decode every container listed in
`foshol.intake.allowed-audio-types` — `audio/wav` (PCM), `audio/webm` (Opus), `audio/ogg` (Opus) and **`audio/mp4`** (AAC) — and SHALL
report `ERR_SIDECAR_UNDECODABLE` for anything else.**

*(MP4/AAC is what Safari's `MediaRecorder` produces, so it is what arrives from every iPhone
(`WEB-FR-138`). A decode path built and fixture-tested only against WebM/Opus silently fails for
every iOS farmer. The Dockerfile must therefore carry an `ffmpeg` build with an AAC decoder — a
container-image dependency, not a Python one, and easy to miss.)*

`SIDECAR-FR-042` **THE sidecar SHALL normalise integrated loudness to `FOSHOL_SIDECAR_ASR_TARGET_LUFS`
with a true-peak ceiling of −1.0 dBTP before inference.** *(Plan §7.1: half of field-demo ASR quality
is audio hygiene.)*

`SIDECAR-FR-043` **IF the decoded audio is longer than `FOSHOL_SIDECAR_MAX_AUDIO_SECONDS`, THEN THE
sidecar SHALL return `413` with `ERR_SIDECAR_PAYLOAD_TOO_LARGE` and SHALL NOT transcribe it.** The
limit mirrors `foshol.intake.max-audio-seconds`; `intake` enforces it first and the sidecar enforces it
again.

`SIDECAR-FR-044` **THE sidecar SHALL compute `confidence` as the exponential of the duration-weighted
mean of the per-segment average log-probability, clamped to `[0,1]` and rounded to three decimal
places.** *(Three places match `case_audio.asr_confidence numeric(4,3)`; a single deterministic formula
means the officer console never sees two different meanings of the same number.)*

`SIDECAR-FR-045` **IF no speech segment is detected, THEN THE sidecar SHALL return `200` with an empty
transcript, `confidence: 0.000` and `speech_detected: false`.** *(A silent recording is a valid answer,
not an error; `intake` and `review` decide what to do about it.)*

`SIDECAR-FR-046` **THE sidecar SHALL request the language configured in `FOSHOL_SIDECAR_ASR_LANGUAGE`
and SHALL NOT auto-detect the language.** *(Auto-detection on a short noisy clip is a source of
non-determinism the demo cannot afford.)*

### 4.6 Text embedding

`SIDECAR-FR-050` **WHEN a text batch is posted to `/v1/embed`, THE sidecar SHALL return one
768-dimensional float vector per input text, in input order** (`COMMON-NFR-006`).

`SIDECAR-FR-051` **THE sidecar SHALL normalise every input text to Unicode NFC, strip zero-width
joiners and non-joiners, and fold Bengali digit forms to ASCII digits before embedding**
(`COMMON-NFR-013`).

`SIDECAR-FR-052` **THE sidecar SHALL return L2-normalised vectors and SHALL report
`normalised: true`.** *(The `knowledge` HNSW indexes use `vector_cosine_ops`; consistently normalised
vectors make cosine and inner product agree, and make the seeded `symptom_phrase.embedding` values and
the runtime transcript vector directly comparable.)*

`SIDECAR-FR-053` **IF a batch exceeds `FOSHOL_SIDECAR_EMBED_MAX_BATCH` texts, or any text exceeds
`FOSHOL_SIDECAR_EMBED_MAX_CHARS` characters, THEN THE sidecar SHALL return `413` and SHALL embed
nothing.**

`SIDECAR-FR-054` **IF the loaded embedding model's output dimension is not 768, THEN THE sidecar SHALL
exit non-zero at start-up with `ERR_SIDECAR_EMBED_DIM_MISMATCH`.** *(A dimension change is a schema
change against `vector(768)`, not a configuration change — `COMMON-NFR-006`.)*

### 4.7 Introspection

`SIDECAR-FR-060` **THE sidecar SHALL expose `GET /health` returning `status`, `mode`, `warm`,
`models_loaded`, `models_expected` and `degraded_reasons`.**

`SIDECAR-FR-061` **WHILE warm-up is incomplete, THE sidecar SHALL answer `GET /health` with `503` and
status `STARTING`, and SHALL answer every inference endpoint with `503` and
`ERR_SIDECAR_WARMING_UP`.**

`SIDECAR-FR-062` **THE sidecar SHALL expose `GET /v1/models` returning, for every registry role, the
`model_id`, `model_version`, `architecture`, `source`, `loaded`, `warm`, `num_labels` and
`loaded_at`.**

`SIDECAR-FR-063` **THE `GET /v1/models` response SHALL include the complete, index-ordered raw label
list of every vision model.** *(This is what makes the label-mapping contract verifiable: the list can
be diffed against `V17__ref_model_label_map.sql` before `COMMON-DATA-014` fails a start-up.)*

### 4.8 Replay mode

`SIDECAR-FR-070` **WHILE the mode is `REPLAY`, THE sidecar SHALL key every lookup on the lowercase
hexadecimal SHA-256 of the received image bytes, the received audio bytes, or the normalised text of
`SIDECAR-FR-051`.**

`SIDECAR-FR-071` **THE fixture store SHALL be the content-addressed layout of `SIDECAR-DATA-001`, and
a served fixture SHALL be returned byte-for-byte with `mode: "REPLAY"` substituted.**

`SIDECAR-FR-072` **IF no fixture exists for the computed digest, THEN THE sidecar SHALL return `404`
with `ERR_SIDECAR_FIXTURE_MISSING` and SHALL NOT fall back to a model.** *(A silent live inference
inside a replay demo is exactly the confusion `analysis_run.mode` exists to prevent.)*

`SIDECAR-FR-073` **WHILE the mode is `REPLAY`, THE sidecar SHALL serve `/v1/vision/explain` from the
pre-baked PNG stored against the same digest.**

`SIDECAR-FR-074` **THE sidecar SHALL treat the fixture store as read-only at runtime and SHALL NOT
create, modify or delete a fixture while serving.** Fixtures are produced only by
`tools/build_fixtures.py`.

`SIDECAR-FR-075` **THE sidecar SHALL be fully functional in `LIVE` mode**, and the end-to-end slice
test SHALL exercise it there in `.github/workflows/verify-live.yml` (`COMMON-NFR-024`). *(Replay and
live are both first-class. A system that only ever runs in replay is not a system.)*

`SIDECAR-DATA-005` **THE fixture store and the analysis module's replay path SHALL be the same store.**
`[DERIVED]` — `COMMON-NFR-022` says the analysis module obtains replay responses "from the fixture
store"; the store lives in `sidecar/fixtures/` and is reached through the sidecar's HTTP API in both
modes. There is exactly one fixture store, owned by A7, and the Spring adapters are identical in both
modes apart from the base URL's mode setting.

### 4.9 `tools/build_fixtures.py`

`SIDECAR-FR-080` **WHEN `tools/build_fixtures.py` is run against a source directory and a sidecar base
URL, THE tool SHALL call each relevant endpoint once per source file and SHALL write the response into
the fixture layout keyed by the file's SHA-256.**

`SIDECAR-FR-081` **IF the target sidecar reports `mode: "REPLAY"`, THEN THE tool SHALL exit non-zero
without writing anything.** *(Recording a replay of a replay produces fixtures that prove nothing.)*

`SIDECAR-FR-082` **THE tool SHALL write `sidecar/fixtures/index.json` recording, per digest, the
endpoint, the `model_id`, the `model_version`, the source filename and the recording timestamp.**

`SIDECAR-FR-083` **IF a fixture already exists for a digest and endpoint, THEN THE tool SHALL leave it
unchanged unless `--force` is supplied.** *(Fixtures are the demo's deterministic ground; they change
deliberately or not at all.)*

`SIDECAR-FR-084` **THE tool SHALL record a `/v1/vision/explain` PNG for every image it records a
classification for**, so `SIDECAR-FR-073` can always be satisfied.

### 4.10 `tools/eval.py` — the only source of an accuracy number

`SIDECAR-FR-090` **WHEN `tools/eval.py` is run, THE tool SHALL evaluate a held-out set of at least 100
images per crop against a live sidecar and SHALL emit `docs/eval-report.md` containing top-1 accuracy,
top-3 accuracy and a confusion matrix per crop.**

`SIDECAR-FR-091` **THE tool SHALL score predictions after mapping each `raw_label` through the
project's own taxonomy, and SHALL NOT score raw model labels directly.** The mapping is read from the
human-supplied label-map artefact of `SIDECAR-DATA-009`, keyed by the `model_id` and `model_version`
the sidecar reported (`COMMON-DATA-017`).

`SIDECAR-FR-092` **THE tool SHALL count a prediction whose `raw_label` has no mapping row as
incorrect, and SHALL report the tally and the distinct unmapped labels in the report.** *(Consistent
with `COMMON-DATA-012`: an unmapped label is an unknown result, never a silent omission.)*

`SIDECAR-FR-093` **IF fewer than 100 labelled images are present for any crop, THEN THE tool SHALL
exit non-zero and SHALL NOT write a report.**

`SIDECAR-FR-094` **IF the target sidecar reports `mode: "REPLAY"`, THEN THE tool SHALL exit non-zero.**

`SIDECAR-DATA-008` **THE held-out set SHALL consist of images that appear nowhere else in the project**
— not in `sidecar/fixtures/`, not in the demo seed images of `tools/seed_cases.py`, not in any test
resource. **THE tool SHALL compute the SHA-256 of every held-out image, SHALL compare it against
`sidecar/fixtures/index.json` and the seed manifest, and IF any digest appears in both, THEN THE tool
SHALL exit non-zero and SHALL name the offending files.**

`SIDECAR-FR-095` **THE held-out set's crop and disease labels SHALL be human-supplied in
`tools/eval/manifest.csv` with columns `path,crop_code,disease_code`, and THE tool SHALL NOT infer,
derive or edit a label.** The manifest is a `CONTENT-OWNERS.md` artefact with a named owner
(`COMMON-CON-003`).

`SIDECAR-FR-096` **THE report SHALL state the image count per crop, the `model_id` and `model_version`
that ran, the fallback-usage count, the manifest SHA-256, the run timestamp and the mode.** *(A number
without its provenance is a claim, not a measurement.)*

`SIDECAR-NFR-013` **THE project SHALL NOT state, display or document any accuracy, WER, F1 or
precision figure that originates from a published model card, paper or third-party benchmark, in the
product, the deck or any document** (`COMMON-CON-002`), **and `docs/eval-report.md` SHALL be the only
permitted source of an accuracy number.**

`SIDECAR-FR-097` **THE tool SHALL write `docs/eval-report.md` and an optional per-image predictions
CSV, and SHALL write no other file.** It reads models only through the sidecar's HTTP API and modifies
nothing.

### 4.11 `tools/seed_cases.py`

`SIDECAR-FR-100` **WHEN `tools/seed_cases.py` is run, THE tool SHALL generate between 40 and 60
historical demo cases, the count taken from `--count` and rejected outside that range.**

`SIDECAR-FR-101` **THE tool SHALL upload each selected dataset image to MinIO under the object-key
convention of `intake`, and SHALL reference those object keys from the generated SQL.**

`SIDECAR-FR-102` **THE tool SHALL emit the migration to the path given by `--out`, defaulting to
`tools/out/V101__seed_historical_cases.sql`.** `[DERIVED]` — `00-common.ears.md` §3 places `V101`
under `app/src/main/resources/db/seed/`, which agent A1 owns, and `COMMON-NFR-042` forbids A7 from
adding a migration. A7 therefore generates the file and A1 places it.

`SIDECAR-FR-103` **THE generated SQL SHALL reference farmers, officers, diseases and remedies by
`INSERT … SELECT` against their natural keys and SHALL NOT contain a literal identifier for any of
them.** *(Seeded advisories must point at human-authored remedy rows from `V15`; a subselect cannot
invent one, and a literal UUID would break the moment `V100` is regenerated.)*

`SIDECAR-FR-104` **THE tool SHALL leave `diagnosis_case.note_bn`, `case_audio.transcript_bn` and
`advisory.officer_note_bn` NULL unless a human-supplied text file is given by `--notes`.**
*(`COMMON-CON-003`: an agent may author a fictional persona, but not a Bangla symptom phrase.)*

`SIDECAR-FR-105` **THE tool SHALL be deterministic for a given `--seed`, producing byte-identical SQL
across runs.**

`SIDECAR-FR-106` **THE tool SHALL write a seed manifest listing the SHA-256 of every uploaded image,
and SHALL exit non-zero IF any digest also appears in `tools/eval/manifest.csv`** (`SIDECAR-DATA-008`).

`SIDECAR-FR-107` **THE fictional farmer and officer personas SHALL be seeded by
`V100__seed_demo_identities.sql`, and `tools/seed_cases.py` SHALL NOT insert an identity row.**
`[DERIVED]` — `farmer.phone_enc` is AES-256-GCM ciphertext under `foshol.crypto.phone.key`
(`COMMON-SEC-013`). Generating identities would require handing the encryption key to a developer
tool. The personas remain fictional and remain A1's, per `COMMON-DATA-005`.

### 4.12 Deferred

`[DEFERRED]` `SIDECAR-FR-110` **WHERE the VLM visual-descriptor path is included, THE sidecar SHALL
expose `POST /v1/vision/describe` returning structured visual descriptors constrained to the project's
symptom code list.** Out of scope (clarification 7 — needs a GPU). The seam is
`case_symptom.source = 'VISION'`, defined and never written.

`[DEFERRED]` `SIDECAR-NFR-015` **WHERE a GPU is available, THE sidecar SHALL select an accelerated
execution device from configuration.** Today every model runs on CPU.

---

## 5. API surface

Base URL `foshol.ai.base-url`. No path is authenticated; see `SIDECAR-SEC-002`.

`SIDECAR-API-001` **THE sidecar SHALL return every error as `application/problem+json` per
`COMMON-API-002`**, with `code` drawn from §3.4 and `correlationId` populated.

`SIDECAR-API-002` **THE sidecar SHALL adopt an inbound `X-Correlation-Id`, generate one when absent,
place it on every log line, and return it on every response** (`COMMON-NFR-016`).

`SIDECAR-API-003` **THE sidecar SHALL publish its own OpenAPI document at `/openapi.json`**, which is
informational; `docs/openapi/foshol-api.yaml` describes only the Spring API and does not describe the
sidecar.

`SIDECAR-API-004` **THE sidecar SHALL keep the `/v1` path prefix stable for the life of the build**; a
breaking change is a blocker for agent A3, not a redeploy.

### 5.1 `POST /v1/vision/classify`

**Request** `multipart/form-data`

| Field | Type | Required | Notes |
|---|---|---|---|
| `image` | file | yes | ≤ `FOSHOL_SIDECAR_MAX_IMAGE_BYTES`; type sniffed |
| `crop_code` | string | yes | Opaque routing key |
| `top_k` | integer | no | Default `FOSHOL_SIDECAR_VISION_TOP_K`, 1–20 |
| `model_role` | enum | no | `primary` \| `fallback`; rice only |

**Response `200`**

```json
{ "mode": "LIVE", "crop_code": "…", "model_id": "…", "model_version": "…",
  "model_role": "primary", "fallback_used": false, "fallback_reason": null,
  "architecture": "swin", "image_sha256": "…",
  "predictions": [ { "raw_label": "…", "confidence": 0.9123, "rank": 1 } ],
  "inference_ms": 812, "correlation_id": "…" }
```

**Statuses** `200` · `400` `ERR_SIDECAR_BAD_REQUEST` / `ERR_SIDECAR_UNKNOWN_CROP` · `404`
`ERR_SIDECAR_FIXTURE_MISSING` · `413` · `415` · `422` · `429` · `500` · `503`.

### 5.2 `POST /v1/vision/explain`

**Request** as §5.1 plus optional `target_label` (string) and `alpha` (0.0–1.0).

**Response `200`** — body `image/png`; headers `X-Foshol-Model-Id`, `X-Foshol-Model-Version`,
`X-Foshol-Target-Label`, `X-Foshol-Method` (`gradcam` | `attention-pool`), `X-Foshol-Mode`,
`X-Correlation-Id`.

**Statuses** `200` · `400` · `404` · `413` · `415` · `422` · `429` · `500` · `503`.

### 5.3 `POST /v1/asr/transcribe`

**Request** `multipart/form-data`: `audio` (file, required), `language` (string, optional).

**Response `200`**

```json
{ "mode": "LIVE", "model_id": "…", "model_version": "…", "audio_sha256": "…",
  "language": "bn", "duration_ms": 21340, "sample_rate_hz": 16000,
  "speech_detected": true, "transcript": "…", "confidence": 0.812,
  "segments": [ { "start_ms": 0, "end_ms": 3200, "avg_logprob": -0.31, "no_speech_prob": 0.02 } ],
  "inference_ms": 6100, "correlation_id": "…" }
```

**Statuses** `200` · `400` · `404` · `413` (bytes or duration) · `415` · `422` · `429` · `500` · `503`.

### 5.4 `POST /v1/embed`

**Request** `application/json`: `{ "texts": ["…"] }`.

**Response `200`**

```json
{ "mode": "LIVE", "model_id": "…", "model_version": "…", "dimension": 768,
  "normalised": true, "embeddings": [[0.0123, "…"]],
  "inference_ms": 95, "correlation_id": "…" }
```

**Statuses** `200` · `400` · `404` · `413` · `429` · `500` · `503`.

### 5.5 `GET /health` and `GET /v1/models`

`GET /health` → `200` when `UP` or `DEGRADED`, `503` when `STARTING` or no model is usable. Consumed
by the Docker healthcheck and by the Spring health indicator of `COMMON-NFR-032`.

`GET /v1/models` → `200`, the array described by `SIDECAR-FR-062` and `SIDECAR-FR-063`. `503` while
warming up.

---

## 6. Persistence — the fixture store and the model cache

`SIDECAR-SEC-001` **THE sidecar SHALL have no database access and no database credentials.** Its
container SHALL receive no datasource URL, username or password; `requirements.txt` SHALL contain no
database driver; and `docker-compose.yml` SHALL NOT place the sidecar on a network path to PostgreSQL.
The same holds for every script in `tools/`, which emits SQL text but never executes it.

*This is why the pgvector kNN lives in `knowledge` and not here.* Plan §5 gave the sidecar a
`/v1/symptoms/extract` endpoint while plan §7.2 had that endpoint querying `symptom_phrase.embedding`
— a table owned by `knowledge`. Granting the sidecar credentials would have given a second writer to a
module's private schema, broken the one-owner-per-table rule that makes seven parallel agents safe, and
put the authoritative knowledge-base lookup inside the component whose output the knowledge base exists
to check. The sidecar therefore returns a vector and nothing more (`SIDECAR-FR-055`).

`SIDECAR-DATA-001` **THE fixture store SHALL live at `sidecar/fixtures/` with this layout, and every
fixture SHALL be committed to the repository:**

```
sidecar/fixtures/
├── index.json                       digest → {endpoint, model_id, model_version, source, recorded_at}
├── vision/classify/<sha256>.json    a full /v1/vision/classify response body
├── vision/explain/<sha256>.png      a pre-baked overlay
├── asr/transcribe/<sha256>.json     a full /v1/asr/transcribe response body
└── embed/<sha256>.json              a full /v1/embed response body
```

`SIDECAR-DATA-002` **THE model cache SHALL be a mounted volume** whose path is given to the
Hugging Face cache environment variable, shared by the `docker compose` stack and by the CI job of
`COMMON-NFR-024`, so weights are downloaded at most once per machine.

`SIDECAR-DATA-003` **THE sidecar SHALL persist nothing else**: no uploaded image, no audio file, no
transcript, no embedding, and no request log containing user content.

`SIDECAR-DATA-004` **THE sidecar SHALL own no table, no column and no migration.** Every table lives in
`00-common.ears.md` §4.

`SIDECAR-DATA-009` **THE label-map artefact consumed by `tools/eval.py` SHALL be a single
human-validated CSV with columns `model_id,model_version,raw_label,disease_code`, and it SHALL be the
same artefact from which agent A1 authors `V17__ref_model_label_map.sql`.** `[DERIVED]` — `eval.py`
must map raw labels through the project taxonomy (`SIDECAR-FR-091`) but has no database access, so the
mapping needs a file-based source of truth. Two independently authored copies of this mapping would be
a silent correctness bug, so there is one file with one owner; A7 reads it and never writes it. **A7
SHALL raise a blocker with A1 if the artefact does not exist.**

---

## 7. Acceptance criteria

One scenario per requirement, each mapping onto a single test method in `sidecar/tests/`.

| ID | Given | When | Then |
|---|---|---|---|
| `FR-001` | `FOSHOL_AI_MODE=replay` | the process starts | `GET /health` reports `mode: "REPLAY"` |
| `FR-002` | live mode, all identifiers set | the process starts | `GET /v1/models` lists five roles with `loaded: true` |
| `FR-003` | a pinned revision | a classify call returns | `model_version` equals that revision |
| `FR-004` | live mode | start-up completes | one warm-up inference per model has run and `warm` is `true` |
| `FR-005` | replay mode, empty model cache | the process starts | no weight is fetched and `/health` is `UP` |
| `FR-006` | `FOSHOL_AI_ASR_MODEL_ID` unset | the process starts | it exits non-zero naming `asr.bangla` |
| `FR-007` | any mode | any endpoint responds | the body and `X-Foshol-Mode` carry that mode |
| `FR-010` | a rice image, `top_k=3` | posted to `/v1/vision/classify` | three predictions, ranks 1–3, descending confidence |
| `FR-011` | a tomato `crop_code` | posted to classify | `model_id` is the solanaceae model |
| `FR-012` | an unrouted `crop_code` | posted to classify | `400` `ERR_SIDECAR_UNKNOWN_CROP`, no inference timer recorded |
| `FR-013` | any classification | the response is read | every confidence is in `[0,1]` with four decimal places |
| `FR-014` | the fallback rice model ran | the response is read | `model_id` is the fallback's, not the configured primary's |
| `FR-015` | a known image | posted to classify | `image_sha256` equals the digest of the posted bytes |
| `FR-020` | the primary rice model cannot load | the process starts | it starts, `/health` is `DEGRADED`, rice is served by the fallback |
| `FR-021` | the primary raises on inference | a rice image is posted | the fallback's result returns with `fallback_reason: "PRIMARY_INFERENCE_ERROR"` |
| `FR-022` | both rice models loaded | `model_role=fallback` is posted | the fallback runs and reports `"REQUESTED"` |
| `FR-023` | any fallback result | the response is read | `model_role`, `fallback_used` and `fallback_reason` are all populated |
| `FR-024` | neither rice model usable | a rice image is posted | `503` `ERR_SIDECAR_MODEL_UNAVAILABLE` |
| `FR-025` | a non-requested fallback | the call completes | exactly one `WARN` line carries the correlation id and reason |
| `FR-030` | a valid image | posted to `/v1/vision/explain` | a decodable PNG returns, longest edge ≤ the configured bound |
| `FR-031`…`033` | each architecture family | explain is called | the recorded `X-Foshol-Method` matches the family's specified method |
| `FR-034` | the same image twice | explain is called twice | both PNGs are byte-identical |
| `FR-035` | a `target_label` outside the label space | explain is called | `400` |
| `FR-036` | any overlay response | the headers are read | all five metadata headers are present |
| `FR-037` | a rice image while the primary is down | explain is called | the overlay is produced by the fallback and the header says so |
| `FR-040` | a 20-second Bangla clip | posted to transcribe | a transcript, a confidence and a segment list return |
| `FR-041` | 44.1 kHz stereo input | posted to transcribe | the response reports `sample_rate_hz: 16000` |
| `FR-042` | two clips differing only in gain | posted to transcribe | both report the same normalised loudness in the debug field |
| `FR-043` | a clip one second over the limit | posted to transcribe | `413` `ERR_SIDECAR_PAYLOAD_TOO_LARGE` |
| `FR-044` | fixed segment log-probabilities | confidence is computed | the value equals the specified formula to three places |
| `FR-045` | a silent clip | posted to transcribe | `200`, empty transcript, `confidence: 0.000`, `speech_detected: false` |
| `FR-046` | any clip | posted to transcribe | the request language is `bn`, never auto-detected |
| `FR-050` | three texts | posted to `/v1/embed` | three vectors of length 768 in input order |
| `FR-051` | the same text in NFC and NFD | posted to embed | both produce identical vectors |
| `FR-052` | any vector | its norm is computed | it is 1.0 within floating-point tolerance |
| `FR-053` | a batch one over the maximum | posted to embed | `413`, no vector returned |
| `FR-054` | an embedding model of another dimension | the process starts | it exits non-zero with `ERR_SIDECAR_EMBED_DIM_MISMATCH` |
| `FR-055` | a running sidecar | `POST /v1/symptoms/extract` | `404`; the route does not exist |
| `FR-060` | a healthy sidecar | `GET /health` | `200` with all documented fields |
| `FR-061` | warm-up incomplete | any inference endpoint is called | `503` `ERR_SIDECAR_WARMING_UP` |
| `FR-062` | live mode | `GET /v1/models` | every role reports id, version, architecture and `loaded_at` |
| `FR-063` | live mode | `GET /v1/models` | each vision model's full label list is present and index-ordered |
| `FR-070` | replay mode, a recorded image | posted to classify | the recorded body returns |
| `FR-071` | a served fixture | it is compared to the file | it matches byte-for-byte apart from `mode` |
| `FR-072` | replay mode, an unrecorded image | posted to classify | `404` `ERR_SIDECAR_FIXTURE_MISSING`, no model loaded |
| `FR-073` | replay mode, a recorded image | posted to explain | the pre-baked PNG returns |
| `FR-074` | replay mode | a request is served | the fixture directory's mtimes are unchanged |
| `FR-075` | live mode | the end-to-end slice test runs | it passes in `verify-live.yml` |
| `FR-080` | a live sidecar and a source directory | `build_fixtures.py` runs | one fixture per file per endpoint plus `index.json` |
| `FR-081` | a replay sidecar | `build_fixtures.py` runs | it exits non-zero and writes nothing |
| `FR-083` | an existing fixture | the tool re-runs without `--force` | the file is unchanged |
| `FR-084` | a recorded image | the tool finishes | an explain PNG exists for the same digest |
| `FR-090` | ≥ 100 labelled images per crop | `eval.py` runs | `docs/eval-report.md` contains top-1, top-3 and a confusion matrix per crop |
| `FR-091` | a raw label with a mapping row | scoring runs | the prediction is scored as its mapped `disease_code` |
| `FR-092` | a raw label with no mapping row | scoring runs | it counts as incorrect and appears in the unmapped tally |
| `FR-093` | 99 rice images | `eval.py` runs | it exits non-zero and writes no report |
| `FR-094` | a replay sidecar | `eval.py` runs | it exits non-zero |
| `FR-096` | a completed run | the report is read | counts, model ids, versions, manifest digest, timestamp and mode are present |
| `DATA-008` | an eval image also present in the fixtures | `eval.py` runs | it exits non-zero and names the file |
| `FR-100` | `--count 45` | `seed_cases.py` runs | the SQL contains 45 cases |
| `FR-101` | a dataset directory | the tool runs | each image exists in MinIO under the referenced key |
| `FR-102` | no `--out` | the tool runs | the file lands in `tools/out/` and nothing under `app/` is touched |
| `FR-103` | the generated SQL | it is inspected | no literal farmer, officer, disease or remedy UUID appears |
| `FR-104` | no `--notes` | the tool runs | every Bangla text column in the output is `NULL` |
| `FR-105` | the same `--seed` twice | the tool runs twice | both SQL files are byte-identical |
| `SEC-001` | the built image | it is inspected | no database driver is installed and no datasource variable is set |
| `SEC-003` | a transcribe call | the logs are read | no transcript text and no audio bytes appear |
| `NFR-004` | `FOSHOL_SIDECAR_MAX_CONCURRENT=1` | two concurrent calls arrive | the second waits, then succeeds or returns `429` |

---

## 8. Test requirements

Python tests live in `sidecar/tests/`, run with `pytest`, and are wired into `verify.yml`.

**Unit — must be covered**

1. **Routing**: `crop_code` → role for every configured route, and the unrouted case.
2. **Fallback selection**: each of the three `fallback_reason` values, plus the no-model-usable path.
3. **Confidence formula** (`SIDECAR-FR-044`) against fixed segment inputs, including the empty case.
4. **Text normalisation** (`SIDECAR-FR-051`): NFC, ZWNJ/ZWJ removal, Bengali digit folding.
5. **Replay key derivation** for image, audio and normalised text.
6. **Error mapping**: every code in §3.4 maps to its status and to a well-formed problem document.
7. **Config loading**: each required variable missing → non-zero exit naming the role.
8. **`eval.py` scoring**: top-1 and top-3 counters, confusion-matrix cell placement, unmapped tally,
   and the two disjointness guards of `SIDECAR-DATA-008`.
9. **`seed_cases.py`**: determinism under a fixed seed, count bounds, and the null-Bangla rule.

**Integration — the replay suite is the floor**

`SIDECAR-NFR-016` **THE replay-mode test suite SHALL run with no model weights present and SHALL cover
every endpoint**, so CI's fast job never downloads a weight file.

`SIDECAR-NFR-017` **THE live-mode suite SHALL be a separate marked suite run only by
`.github/workflows/verify-live.yml`**, and it is permitted to be slow (`COMMON-NFR-024`).

**Fixtures needed**

| Fixture | Purpose |
|---|---|
| 3 rice, 2 tomato, 2 potato images | the three demo beats plus routing coverage |
| 1 image whose digest is deliberately absent | `SIDECAR-FR-072` |
| 1 Bangla clip, 1 silent clip, 1 over-length clip | `SIDECAR-FR-040`, `045`, `043` |
| 1 stereo 44.1 kHz clip | `SIDECAR-FR-041` |
| 1 corrupt PNG and 1 text file renamed `.jpg` | `422` and `415` |
| A pre-baked overlay per demo image | `SIDECAR-FR-073` |
| A stub label-map CSV with a deliberately unmapped label | `SIDECAR-FR-092` |

`SIDECAR-SEC-008` **THE committed fixtures SHALL contain no real personal data**: no identifiable face,
no voice of an identifiable person, and no location metadata. EXIF SHALL be stripped from every
committed image.

---

## 9. Agent execution notes

### 9.1 Implementation order

1. `sidecar/requirements.txt` — every line pinned with `==` (`00-common.ears.md` §2.4), no database
   driver. Then `sidecar/Dockerfile` with a `/health` healthcheck.
2. `app/config.py` (§3.2) and `app/errors.py` (§3.4). Nothing reads an environment variable elsewhere.
3. `app/schemas.py` — the pydantic models of §5.
4. `app/replay.py` and the fixture layout — **`SIDECAR-FR-070`…`074` are the Day-1 deliverable.**
5. `app/main.py` with all six routes wired to the replay store. At this point A3 is unblocked.
6. `app/vision.py` — loading, routing, fallback, then Grad-CAM/attention.
7. `app/asr.py` — decode, downmix, resample, loudness-normalise, transcribe.
8. `app/embed.py` — normalise, embed, L2-normalise, dimension guard.
9. Warm-up and `/v1/models`.
10. `tools/build_fixtures.py`, then re-record the committed fixtures from the live sidecar.
11. `tools/seed_cases.py` — hand the generated `V101` to A1.
12. `tools/eval.py` and `docs/eval-report.md`, last, once the label-map artefact exists.

### 9.2 Blockers to raise, not work around

- `SIDECAR-DATA-009` — the label-map CSV does not exist → **A1**.
- `SIDECAR-DATA-007` — a model revision needs changing → **A1** (new migration).
- `SIDECAR-FR-095` — the held-out manifest's labels are human-supplied → **content owner**.
- `V100__seed_demo_identities.sql` not landed → **A1** (`SIDECAR-FR-107`).

### 9.3 Budgets

`SIDECAR-NFR-004` **THE sidecar SHALL admit at most `FOSHOL_SIDECAR_MAX_CONCURRENT` concurrent
inferences**, queueing further requests for at most `FOSHOL_SIDECAR_QUEUE_TIMEOUT_MS` before returning
`429` with `ERR_SIDECAR_BUSY` and a `Retry-After` header.

`SIDECAR-NFR-005` **THE sidecar SHALL run a single Uvicorn worker process** and SHALL set the torch
thread count from `FOSHOL_SIDECAR_TORCH_THREADS`. *(Each worker would hold its own copy of every
weight; one process is the memory budget.)*

`SIDECAR-NFR-006` **THE sidecar SHALL hold resident memory at or below 4 GiB with every model loaded
and warm.**

`SIDECAR-NFR-007` **THE sidecar SHALL meet these p95 budgets on the demo machine's CPU**, all of which
sit inside `foshol.ai.timeout=PT8S`, which is the binding contract:

| Endpoint | p95 budget |
|---|---|
| `POST /v1/vision/classify` | 2 500 ms |
| `POST /v1/vision/explain` | 4 000 ms |
| `POST /v1/asr/transcribe` | ≤ 1.0 × audio duration |
| `POST /v1/embed` (batch of 1) | 300 ms |
| `GET /health`, `GET /v1/models` | 50 ms |
| Any endpoint in `REPLAY` mode | 100 ms |

`SIDECAR-NFR-008` **IF an inference would exceed `foshol.ai.timeout`, THEN THE sidecar SHALL still
return a response rather than hang**, because the caller's time limiter will already have given up and
degraded the case to `UNDETERMINED` (`COMMON-NFR-035`).

`SIDECAR-NFR-009` **THE sidecar SHALL log one structured single-line record per request** with
`timestamp level correlation_id endpoint mode model_id status inference_ms`, and no other per-request
log at `INFO`.

`SIDECAR-SEC-003` **THE sidecar SHALL NOT log image bytes, audio bytes, transcript text, embedded text
or embedding vectors at any level in any mode** (`COMMON-SEC-001`). Transcript text may be logged only
as a character count.

`SIDECAR-SEC-002` **THE sidecar SHALL be reachable only from the application container on the
`docker compose` network, SHALL NOT publish its port beyond `localhost`, and SHALL NOT be reachable
from a browser.** *(It has no authentication of its own by design; network isolation is the control.)*

`SIDECAR-SEC-004` **THE sidecar SHALL determine content type by magic-byte sniffing and SHALL reject a
mismatch with `415`** (`COMMON-SEC-014`), and SHALL reject a payload above its byte limit with `413`
before decoding it.

`SIDECAR-SEC-005` **THE sidecar and every tool SHALL read credentials — MinIO keys included — from the
environment only, and the repository SHALL contain no secret value** (`COMMON-SEC-002`).

`SIDECAR-SEC-006` **THE sidecar SHALL run as a non-root user in its container.**

### 9.4 Local Definition of Done

1. `docker compose up` starts the sidecar in replay mode with no weight download, and `/health`
   reports `UP` within 10 seconds.
2. Every endpoint of §5 answers correctly in replay mode; `pytest sidecar/tests` is green with no
   model present.
3. With `FOSHOL_AI_MODE=live`, every endpoint answers correctly and the live-marked suite is green.
4. `GET /v1/models` lists five roles, each with a resolved `model_version` and a complete label list.
5. Killing the sidecar mid-demo produces `UNDETERMINED` cases in Spring, not stack traces.
6. `tools/build_fixtures.py`, `tools/seed_cases.py` and `tools/eval.py` each run end to end.
7. `docs/eval-report.md` exists, states ≥ 100 images per crop, and is the only accuracy figure in the
   repository (`SIDECAR-NFR-013`).
8. `requirements.txt` pins every line with `==` and contains no database driver.
9. No file outside `sidecar/`, `tools/`, `docs/eval-report.md` and `docs/progress/a7.md` was written.
10. No agronomic content was authored (`COMMON-CON-003`).

---

## 10. Requirement index

| Category | IDs | Count |
|---|---|---|
| Functional | `SIDECAR-FR-001`…`007`, `010`…`016`, `020`…`025`, `030`…`037`, `040`…`046`, `050`…`055`, `060`…`063`, `070`…`075`, `080`…`084`, `090`…`097`, `100`…`107`, `110` | 73 |
| Non-functional | `SIDECAR-NFR-004`…`009`, `013`…`017`, `020`, `021` | 13 |
| Security | `SIDECAR-SEC-001`…`008` | 8 |
| Data | `SIDECAR-DATA-001`…`009` | 9 |
| API | `SIDECAR-API-001`…`004` | 4 |
| **Total** | | **107** |

Deferred: `SIDECAR-FR-110`, `SIDECAR-NFR-015`.
