# 11 — Intake (EARS)

**Module:** `intake` · **Package:** `com.rootcause.foshol.intake` · **Agent:** A2
**Prefix:** `INTAKE` · **Depends on:** `00-common.ears.md` (FROZEN — read it first)

> This document plus `00-common.ears.md` is sufficient to implement the module. Where the two
> disagree, `00-common.ears.md` wins. Traces to plan §2.4 (`intake` module row), §3 (the pipeline's
> first stage and the `CaseIntakeChannel` port), §4 (data model) and §11 beat 3 (the local
> quality-gate rejection). Clarification items 2, 5 and 14 of the approved plan are binding here.

---

## 1. Scope

### 1.1 What this module owns

| Owned | Detail |
|---|---|
| Aggregate | `DiagnosisCase` — the case, its images, its audio |
| Tables | `diagnosis_case`, `case_image`, `case_audio`, `idempotency_key` (`00-common` §4.4) |
| Projection | `p_farmer_case_history` (`00-common` §4.8) |
| Case status | The state machine, and **the only writes to `diagnosis_case.status`** anywhere in the system |
| Decision path column | `diagnosis_case.decision_path`, written from `AnalysisCompleted` |
| Upload validation | Count, size, magic-byte content type, audio duration |
| Quality gate | Laplacian blur variance and exposure, evaluated **before** anything is stored |
| Object storage | MinIO originals and derivatives, behind `ImageStorePort`; presigned URL issuance |
| Idempotency | Client-supplied `Idempotency-Key`, replay and conflict semantics |
| Resubmission linkage | `diagnosis_case.parent_case_id` |
| Published API | `CaseIntakeApi` (`00-common` §6.2) |
| Extensibility port | `CaseIntakeChannel`, with `WebIntakeAdapter` today |
| Events published | `CaseSubmitted`, `CaseStatusChanged` |

### 1.2 What this module does **not** own

| Not owned | Owner |
|---|---|
| Classification, Grad-CAM, ASR transcription, symptom extraction, confidence routing | `analysis` — `12-analysis.ears.md` |
| Writing `case_audio.transcript_bn` and `asr_confidence` | `analysis` (`12-analysis.ears.md`); `intake` creates the row, `analysis` fills those two columns |
| Officer queue, claims, advisories, rejections, `case_rejection` | `review` — `14-review.ears.md` |
| Crop, disease, symptom and remedy reference data, and all validation of crop identifiers beyond existence | `knowledge` — `13-knowledge.ears.md` |
| Notification delivery, SSE streams, `notification` table | `notification` — `15-notification.ears.md`. `intake` publishes `CaseStatusChanged`; it never opens an SSE connection |
| Authentication, tokens, the caller's identity | `identity` — `10-identity.ears.md` (`IDENTITY-FR-017`) |
| The officer queue projection `p_officer_queue` | `review` |
| Farmer-facing advisory content | `review` (`ReviewSubmissionApi`) |

`INTAKE-DATA-001` **THE intake module SHALL be the sole writer of `diagnosis_case.status`, and no
other module SHALL write that column.** Every status change is the result of an event this module
consumes or a command it handles. *(One writer per column is what lets seven agents run in parallel.)*

---

## 2. Dependencies

### 2.1 Interfaces called

Permitted by the dependency matrix (`00-common` §6.1: `intake → common, identity, knowledge`).

```java
// com.rootcause.foshol.identity.api
Optional<FarmerView> findById(UUID farmerId);                      // FarmerLookupApi

// com.rootcause.foshol.knowledge.api
Optional<CropView> findCropById(UUID cropId);                      // KnowledgeQueryApi
```

`FarmerLookupApi.findById` supplies `districtCode` for `diagnosis_case.district_code` and
`name` for `p_farmer_case_history`. `KnowledgeQueryApi.findCropById` validates `cropId` and supplies
`nameBn` for the projection. **No other method of either interface is called.**

### 2.2 Events published

Verbatim from `00-common` §6.3:

```java
public record CaseSubmitted(UUID caseId, UUID farmerId, UUID cropId, String cropCode,
                            String districtCode, List<CaseImageRef> images, CaseAudioRef audio,
                            String noteBn, UUID parentCaseId,
                            String correlationId, Instant occurredAt) {}

public record CaseStatusChanged(UUID caseId, UUID farmerId, CaseStatus fromStatus,
                                CaseStatus toStatus, String correlationId, Instant occurredAt) {}
```

### 2.3 Events consumed

| Event | From | Effect |
|---|---|---|
| `CaseSubmitted` | own module | `SUBMITTED → ANALYSING` (`INTAKE-FR-040`) |
| `CaseStatusChanged` | own module | `p_farmer_case_history` upsert (`INTAKE-FR-060`) |
| `AnalysisCompleted` | `analysis` | `ANALYSING → ANALYSED → IN_REVIEW`, writes `decision_path` |
| `AnalysisFailed` | `analysis` | `ANALYSING → FAILED` |
| `AdvisoryApproved` | `review` | `→ ADVISED`, enriches the projection |
| `CaseRejected` | `review` | `→ REJECTED`, enriches the projection |

> **Reading of the frozen contract.** `00-common` §6.1 shows `—` for `intake → review`, and its own
> closing note explains why: *"no module calls `review` or `notification` **synchronously** — they are
> driven by events only."* The matrix governs synchronous published-interface calls. Event consumption
> is governed by the publish/consume map in `00-common` §6.3, which names `intake` as a consumer of
> `AdvisoryApproved` and `CaseRejected`. `intake` therefore imports those two records from
> `review.api` and **calls no method on `ReviewSubmissionApi`**.

`INTAKE-NFR-001` **THE intake module SHALL make every `@ApplicationModuleListener` idempotent**, so a
republished event produces no duplicate row, no duplicate `CaseStatusChanged` and no repeated
transition (`COMMON-ARCH-015`). Idempotency is achieved by making every transition a no-op when the
case is already in the target status.

### 2.4 Configuration consumed

Every property under `foshol.intake.*` and `foshol.storage.*` in `00-common` §9, read through a
constant in `common.ConfigKeys` (`COMMON-ARCH-010`). Each is cited at the requirement that uses it;
no property is read anywhere it is not cited.

---

## 3. Domain model

### 3.1 Aggregate — `DiagnosisCase`

Root of a three-entity aggregate. `CaseImage` and `CaseAudio` have no identity outside their case and
are never loaded independently.

| Entity | Table | Notes |
|---|---|---|
| `DiagnosisCase` (root) | `diagnosis_case` | Optimistic lock on `version` |
| `CaseImage` | `case_image` | 1..`foshol.intake.max-images` per case |
| `CaseAudio` | `case_audio` | 0..1 per case (`case_audio.case_id UNIQUE`) |

`IdempotencyRecord` (`idempotency_key`) is a **separate** aggregate with its own transaction
boundary, because it must survive the failure of the case it guards.

### 3.2 Value objects

| Type | Contents |
|---|---|
| `CaseId`, `ImageId`, `AudioId` | UUIDv7 wrappers |
| `ObjectKey` | MinIO key, ≤ 200 characters (`INTAKE-DATA-004`) |
| `Sha256` | 64 lowercase hex characters |
| `ImageQuality` | `blurVariance`, `exposureScore`, `qualityScore`, `width`, `height` |
| `QualityVerdict` | `accepted` plus an optional `QualityReason` |
| `RequestFingerprint` | The SHA-256 defined in `INTAKE-FR-030` |

### 3.3 Enums

`CaseStatus` (`common.CaseStatus`) — `SUBMITTED`, `ANALYSING`, `ANALYSED`, `IN_REVIEW`, `ADVISED`,
`REJECTED`, `FAILED`. Matches `ck_case_status`.
`DecisionPath` (`common.DecisionPath`) — written, never decided, by this module.
`QualityReason` `[DERIVED]` — `BLURRY`, `TOO_DARK`, `TOO_BRIGHT`, `TOO_SMALL`. *(The gate has four
distinguishable failure modes and the farmer needs a different Bangla re-capture instruction for each;
`00-common` defines no such enum because `case_image.rejected_reason` is unused in this scope —
see `INTAKE-DATA-006`.)* Declared in `common` per `COMMON-ARCH-010`.

### 3.4 The status state machine

| From | To | Trigger |
|---|---|---|
| — | `SUBMITTED` | `SubmitCaseCommand` succeeds |
| `SUBMITTED` | `ANALYSING` | own `CaseSubmitted` listener |
| `ANALYSING` | `ANALYSED` | `AnalysisCompleted` |
| `ANALYSED` | `IN_REVIEW` | `AnalysisCompleted`, same listener, same transaction |
| `ANALYSING` | `FAILED` | `AnalysisFailed` |
| `IN_REVIEW` | `ADVISED` | `AdvisoryApproved` |
| `IN_REVIEW` | `REJECTED` | `CaseRejected` |
| `FAILED` | `ADVISED` | `AdvisoryApproved` |
| `FAILED` | `REJECTED` | `CaseRejected` |

Every other ordered pair is illegal.

> **`[DERIVED]` — `FAILED` is not terminal.** `00-common` §6.3 maps `AnalysisFailed` to *"review
> (creates an `UNDETERMINED` `ReviewTask`), intake (status `FAILED`)"*. A failed case therefore still
> reaches an officer, who will approve or reject it. If `FAILED` were terminal the officer's decision
> would have nowhere to land and the case would strand at the exact moment the sidecar-kill demo runs.

> **`[DERIVED]` — `ANALYSED → IN_REVIEW` is driven by `AnalysisCompleted`.** The frozen event set
> contains no `ReviewTaskCreated`. F7 guarantees that *every* analysed case enters review, so the two
> transitions are performed in one listener and one transaction, emitting two `CaseStatusChanged`
> events. `ANALYSED` remains a distinct, observable status rather than being collapsed away, because
> the farmer-facing timeline distinguishes "analysed" from "with an officer".

### 3.5 Invariants

| # | Invariant |
|---|---|
| INV-1 | A case carries between `foshol.intake.min-images` and `foshol.intake.max-images` images |
| INV-2 | Exactly one image has `is_primary = true` |
| INV-3 | Image `position` values are `1..n`, contiguous and unique (`uq_case_image_position`) |
| INV-4 | A case carries at most one audio row |
| INV-5 | `status` changes only along §3.4; an illegal transition raises `IllegalCaseTransitionException` |
| INV-6 | `ADVISED` and `REJECTED` accept no further transition |
| INV-7 | `district_code` is snapshotted from the farmer at submission and never updated afterwards |
| INV-8 | `correlation_id` is non-null and is the correlation id of the submitting request (`COMMON-NFR-016`) |
| INV-9 | `parent_case_id`, when present, references a case of the **same farmer** whose status is `REJECTED` |
| INV-10 | `decision_path` is null until `AnalysisCompleted` and is written once |
| INV-11 | Every image has a non-null `sha256` and a non-null `object_key` |

### 3.6 Specifications

| Specification | Predicate |
|---|---|
| `TransitionAllowedSpec` | The (from, to) pair appears in §3.4 |
| `ResubmissionParentSpec` | INV-9 |
| `AcceptableImageSpec` | Sniffed type allowed, size ≤ ceiling, shorter edge ≥ `min-edge-px` |
| `ImageQualitySpec` | `INTAKE-FR-020` — blur and exposure both inside their bounds |
| `IdempotencyReplaySpec` | Stored key, same farmer, same `request_hash` |

### 3.7 Outbound ports (`application/port`)

Internal to this module — **not** part of `00-common` §6.2 and not importable elsewhere.

```java
public interface ImageStorePort {
    StoredObject put(String objectKey, String contentType, byte[] bytes);
    String presign(String objectKey, Duration ttl);
    void delete(String objectKey);
}
public record StoredObject(String objectKey, int byteSize, String sha256) {}

public interface ImageQualityPort {                 // local, in-process, no network
    ImageProbe probe(byte[] bytes);
}
public record ImageProbe(int width, int height, double blurVariance, double exposureScore) {}
```

`MinioImageStoreAdapter` and `OpenCvFreeImageQualityAdapter` live in `infrastructure`.

`INTAKE-NFR-002` **THE image quality gate SHALL execute in-process and SHALL make no network call.**
*(Plan §3: "local image quality gate". It runs before storage and before the sidecar exists in the
request path, which is what makes demo beat 3 instantaneous and independent of the sidecar.)*

### 3.8 Extensibility — `CaseIntakeChannel`

Frozen in `00-common` §6.2:

```java
public interface CaseIntakeChannel {
    UUID submit(IntakeRequest request);
}
public record IntakeRequest(UUID farmerId, UUID cropId, String noteBn, UUID parentCaseId,
                            List<IntakeImage> images, IntakeAudio audio, UUID idempotencyKey) {}
public record IntakeImage(String filename, String contentType, byte[] bytes) {}
public record IntakeAudio(String filename, String contentType, byte[] bytes, int durationMs) {}
```

`INTAKE-FR-001` **THE intake module SHALL register exactly one `CaseIntakeChannel` implementation,
`WebIntakeAdapter`, and every submission SHALL pass through it.** The controller maps the multipart
request to an `IntakeRequest` and delegates to the channel; the channel builds a `SubmitCaseCommand`
and delegates to `SubmitCaseCommandHandler`. No validation logic lives in the controller
(`COMMON-ARCH-009`).

`INTAKE-FR-002` `[DEFERRED]` **THE system SHALL NOT ship an `SmsIntakeAdapter`.** *Seam: plan §3 names
`SmsIntakeAdapter` as the second `CaseIntakeChannel` implementation. Because every rule in §4.1–§4.6
is enforced inside `SubmitCaseCommandHandler` rather than in the controller, an SMS or USSD adapter is
a new `CaseIntakeChannel` bean and nothing else. SMS is out of scope per `00-common` §1.2.*

`INTAKE-FR-003` **THE `IntakeImage.contentType` and `IntakeAudio.contentType` supplied by a channel
SHALL be treated as hints only**, never as the authority for validation (`INTAKE-SEC-001`).

`INTAKE-FR-004` `[DERIVED]` **THE audio sniffer SHALL recognise the ISO base-media (`ftyp`) signature
and accept it as `audio/mp4`**, alongside the RIFF/WAVE, EBML/WebM and OggS signatures.

*(`audio/mp4` is in `foshol.intake.allowed-audio-types` because Safari's `MediaRecorder` emits it and
the farmer surface is used on phones (`COMMON-NFR-047`, `WEB-FR-138`). Magic-byte sniffing is the
authority per `COMMON-SEC-014`, so widening the allowed-types list alone is not sufficient — a
sniffer that knows only three signatures rejects every iPhone recording with `415` no matter what the
configuration says.)*

---

## 4. Requirements

### 4.1 Submission — request validation

`INTAKE-FR-010` **WHEN an authenticated `FARMER` posts `POST /api/v1/cases` with a valid multipart
body, THE intake module SHALL create one `diagnosis_case` row with `status = SUBMITTED` and SHALL
respond `202 Accepted` with the case id and a `Location` header.**

`INTAKE-FR-011` **THE intake module SHALL take the submitting farmer from the authenticated principal
(`IDENTITY-FR-017`) and SHALL ignore any farmer identifier present in the request body.**

`INTAKE-FR-012` **IF the number of image parts is below `foshol.intake.min-images` or above
`foshol.intake.max-images`, THEN THE intake module SHALL respond `400` with code `ERR_IMAGE_COUNT`.**

`INTAKE-FR-013` **IF `cropId` does not resolve through `KnowledgeQueryApi.findCropById`, THEN THE
intake module SHALL respond `400` with code `ERR_CROP_NOT_FOUND`.**

`INTAKE-FR-014` **THE intake module SHALL accept an optional `note` field, SHALL normalise it to
Unicode NFC (`COMMON-NFR-013`) and SHALL store it in `diagnosis_case.note_bn` unmodified.**

`INTAKE-SEC-002` **IF a farmer has already created the number of cases permitted by `COMMON-SEC-015`
inside its window, THEN THE intake module SHALL respond `429` with code `ERR_CASE_RATE_LIMITED` and a
`Retry-After` header**, and SHALL create no case. The count is taken over `diagnosis_case` by
`farmer_id` and `created_at` using index `ix_case_farmer`; no additional table is introduced.

> **Externalisation gap — RESOLVED by A1.** This document originally raised a blocker: `COMMON-SEC-015`
> stated the case ceiling and window as literals, which `COMMON-NFR-020` forbids. A1 has since added
> `foshol.intake.rate-limit.max-cases` (`20`) and `foshol.intake.rate-limit.window` (`PT1H`) to
> `00-common` §9.1, and `COMMON-SEC-015` now reads from them. Bind to the properties, not the numbers.

### 4.2 Content-type sniffing and size limits

`INTAKE-SEC-001` **THE intake module SHALL determine the content type of every uploaded part by
inspecting its leading bytes, and SHALL ignore the client-supplied `Content-Type` header and the
filename extension.** (Implements `COMMON-SEC-014`.)

| Type | Leading-byte signature |
|---|---|
| `image/jpeg` | `FF D8 FF` |
| `image/png` | `89 50 4E 47 0D 0A 1A 0A` |
| `image/webp` | `52 49 46 46` … offset 8 `57 45 42 50` |
| `audio/wav` | `52 49 46 46` … offset 8 `57 41 56 45` |
| `audio/webm` | `1A 45 DF A3` |
| `audio/ogg` | `4F 67 67 53` |

`INTAKE-SEC-003` **IF a sniffed image type is not listed in `foshol.intake.allowed-image-types`, or a
sniffed audio type is not listed in `foshol.intake.allowed-audio-types`, THEN THE intake module SHALL
respond `415` with code `ERR_UNSUPPORTED_MEDIA_TYPE`.** (`COMMON-SEC-014`.)

`INTAKE-SEC-004` **IF any image part exceeds `foshol.intake.max-image-bytes`, or the audio part
exceeds `foshol.intake.max-audio-bytes`, THEN THE intake module SHALL respond `413` with code
`ERR_IMAGE_TOO_LARGE` or `ERR_AUDIO_TOO_LARGE` respectively.** (`COMMON-SEC-014`.)

`INTAKE-FR-015` **IF the supplied audio duration exceeds `foshol.intake.max-audio-seconds`, THEN THE
intake module SHALL respond `400` with code `ERR_AUDIO_TOO_LONG`; IF the sample rate cannot be read
from the container header, THEN the code SHALL be `ERR_AUDIO_UNREADABLE`**, because
`case_audio.sample_rate_hz` is `NOT NULL`.

`INTAKE-SEC-005` **THE intake module SHALL NOT write an uploaded filename to the database, to any
object key, or to any log line.** Object keys are derived from generated identifiers only
(`INTAKE-DATA-004`), so a hostile filename cannot traverse a path or appear in a response.

### 4.3 The local image quality gate — demo beat 3

`INTAKE-FR-020` **THE intake module SHALL compute, for every uploaded image, the variance of the
Laplacian of its 8-bit greyscale conversion as `blur_variance`, and the mean of its greyscale
histogram divided by 255 as `exposure_score`.** (Plan §3: *"blur = Laplacian variance, exposure =
histogram"*.)

`INTAKE-FR-021` **IF an image's `blur_variance` is below `foshol.intake.quality.blur-variance-min`,
THEN THE intake module SHALL reject the submission with quality reason `BLURRY`.**

`INTAKE-FR-022` **IF an image's `exposure_score` is below `foshol.intake.quality.exposure-min`, THEN
THE intake module SHALL reject the submission with quality reason `TOO_DARK`; IF it is above
`foshol.intake.quality.exposure-max`, THEN the reason SHALL be `TOO_BRIGHT`.**

`INTAKE-FR-023` **IF an image's shorter edge in pixels is below `foshol.intake.quality.min-edge-px`,
THEN THE intake module SHALL reject the submission with quality reason `TOO_SMALL`.**

`INTAKE-FR-024` **WHEN any image fails the quality gate, THE intake module SHALL reject the whole
submission with `400` and code `ERR_IMAGE_QUALITY_REJECTED`, SHALL store no object in MinIO, SHALL
create no `diagnosis_case` row, and SHALL create no `idempotency_key` row.**
*(All-or-nothing. Demo beat 3 is "rejected **locally**"; a partially stored case would be neither
locally rejected nor usefully accepted, and leaving the idempotency key unclaimed is what lets the
farmer immediately retry with a better photograph under the same key.)*

`INTAKE-UX-001` **THE `ERR_IMAGE_QUALITY_REJECTED` problem document SHALL carry one `errors` entry per
failing image, each naming the image's zero-based part index and its `QualityReason`, and SHALL carry
a Bangla `detail` resolved from the message key for that reason.**

| Reason | Message key |
|---|---|
| `BLURRY` | `intake.quality.blurry` |
| `TOO_DARK` | `intake.quality.tooDark` |
| `TOO_BRIGHT` | `intake.quality.tooBright` |
| `TOO_SMALL` | `intake.quality.tooSmall` |

`INTAKE-UX-002` **THE Bangla and English values for the keys of `INTAKE-UX-001` SHALL live in the
application `MessageSource` bundles and in `web/src/assets/i18n/`**, and SHALL NOT be written into
this document or into Java source. They are re-capture instructions, not agronomic content, and are
owned by A6 alongside the rest of the UI chrome (`COMMON-NFR-039`, `COMMON-API-003`).

`INTAKE-FR-025` **WHEN an image passes the gate, THE intake module SHALL persist its `blur_variance`,
`exposure_score`, `width`, `height` and a `quality_score` computed as:**

```
blurNorm     = blur_variance / (blur_variance + foshol.intake.quality.blur-variance-min)
midpoint     = (exposure-min + exposure-max) / 2
halfWidth    = (exposure-max − exposure-min) / 2
exposureNorm = 1 − |exposure_score − midpoint| / halfWidth
quality_score = round(blurNorm × exposureNorm, 3)
```

*(`[DERIVED]` — `00-common` §4.4 defines `case_image.quality_score numeric(4,3)` and clarification
item 2 makes it the primary-image selector, but no formula is given. This one introduces **no**
constant that is not already a configured property: `blurNorm` is a saturating ratio equal to `0.5`
exactly at the threshold, and `exposureNorm` is `1.0` at the midpoint of the accepted exposure band
and `0.0` at either edge. Both terms are in `[0,1]`, so the product fits `numeric(4,3)`.)*

`INTAKE-FR-026` **THE intake module SHALL mark as `is_primary` the accepted image with the highest
`quality_score`, breaking a tie by the lowest `position`.** (Clarification item 2 — the primary image
is what Grad-CAM runs on and what the officer sees first.) Upholds INV-2.

### 4.4 Storage

`INTAKE-FR-027` **WHEN every image has passed the gate, THE intake module SHALL store each image's
original bytes and one derivative in MinIO through `ImageStorePort`, and SHALL store the audio bytes
if present**, all before the case transaction opens.

`INTAKE-FR-028` **THE derivative SHALL be the original scaled so that its longest edge equals
`foshol.storage.derivative-max-edge-px`, preserving aspect ratio, encoded as `image/jpeg`**, and SHALL
be skipped when the original's longest edge is already at or below that value, in which case
`case_image.derivative_object_key` is set to the original's key.

`INTAKE-DATA-002` **THE intake module SHALL compute the lowercase hexadecimal SHA-256 of each image's
original bytes and store it in `case_image.sha256`.** *(This is the replay fixture key of
`COMMON-NFR-022`; a wrong digest silently breaks replay mode and therefore the whole demo.)*

`INTAKE-DATA-003` **THE intake module SHALL store `case_image.content_type` as the sniffed type, not
the supplied header, and `case_image.byte_size` as the original byte length.**

`INTAKE-DATA-004` **THE intake module SHALL derive object keys as
`cases/{caseId}/img/{imageId}.{ext}`, `cases/{caseId}/img/{imageId}-d.jpg` and
`cases/{caseId}/audio/{audioId}.{ext}`**, where `ext` follows from the sniffed type. No key contains
user-supplied text, and every key fits `varchar(200)`.

`INTAKE-NFR-003` **IF MinIO is unavailable or an object write fails, THEN THE intake module SHALL
delete every object it has already written for that submission on a best-effort basis, SHALL respond
`503` with code `ERR_STORAGE_UNAVAILABLE`, and SHALL NOT persist a partial case.**
(Implements `COMMON-NFR-036`.)

`INTAKE-SEC-006` **THE intake module SHALL NOT make the MinIO bucket public and SHALL serve every
stored object only through a presigned URL issued after an ownership check**
(`COMMON-SEC-016`, `INTAKE-FR-070`).

### 4.5 Idempotency

`INTAKE-FR-030` **THE intake module SHALL require an `Idempotency-Key` request header on
`POST /api/v1/cases` and SHALL compute a `request_hash` as the lowercase hexadecimal SHA-256 of the
UTF-8 concatenation, separated by `\n`, of: the farmer id, the crop id, the NFC-normalised note (empty
string when absent), the parent case id (empty string when absent), each image SHA-256 in part order,
and the audio SHA-256 (empty string when absent).**
*(`[DERIVED]` — `00-common` §4.4 defines `idempotency_key.request_hash char(64)` but not its recipe.
Hashing the content digests rather than the raw multipart body makes the fingerprint stable across
boundary strings and part ordering metadata, which differ between HTTP clients.)*

`INTAKE-FR-031` **IF the `Idempotency-Key` header is absent, THEN THE intake module SHALL respond
`400` with code `ERR_IDEMPOTENCY_KEY_MISSING`; IF it is present but not a UUID, THEN the code SHALL
be `ERR_IDEMPOTENCY_KEY_INVALID`.** (Clarification item 14.)

`INTAKE-FR-032` **WHEN a submission arrives whose key is already stored for the same farmer with the
same `request_hash`, THE intake module SHALL return the stored `response_status` and `response_body`
unchanged, SHALL set the response header `Idempotency-Replayed: true`, and SHALL create no new case
and store no new object.**
*(`[DERIVED]` — the `Idempotency-Replayed` header. A replay and a first submission are otherwise
indistinguishable to the client, and the end-to-end slice test needs to assert which one it got.)*

`INTAKE-FR-033` **IF a stored key is presented with a different `request_hash`, or by a different
farmer, THEN THE intake module SHALL respond `409` with code `ERR_IDEMPOTENCY_KEY_CONFLICT`.**
*(Same code for both, deliberately: another farmer's key must not be probeable — `COMMON-API-001`.)*

`INTAKE-FR-034` **WHEN a submission succeeds, THE intake module SHALL insert one `idempotency_key`
row carrying the key, the farmer id, the endpoint, the `request_hash`, the returned status and body,
and `expires_at = now() + foshol.intake.idempotency.ttl`, in the same transaction as the case.**

`INTAKE-FR-035` **IF two submissions with the same key race, THEN THE intake module SHALL allow the
primary-key insert to fail for the loser, SHALL re-read the stored row, and SHALL apply
`INTAKE-FR-032` or `INTAKE-FR-033` to it.**

`INTAKE-DATA-005` **WHEN a submission is processed, THE intake module SHALL delete `idempotency_key`
rows whose `expires_at` has passed**, using index `ix_idempotency_expiry`. *(Purging on the write path
avoids introducing a scheduler and a property that `00-common` §9 does not define.)*

### 4.6 Event publication

`INTAKE-FR-036` **WHEN a case is persisted, THE intake module SHALL publish `CaseSubmitted` carrying
every field of the frozen record, including `parentCaseId`, the ordered `CaseImageRef` list with the
primary image flagged, and the `CaseAudioRef` when audio is present.** `CaseAudioRef.transcriptBn` is
null at this point; `analysis` fills it later.

`INTAKE-FR-037` **THE intake module SHALL publish `CaseSubmitted` after the submitting transaction
commits**, via the Modulith event publication registry (`COMMON-ARCH-014`), so a rolled-back
submission never starts an analysis.

`INTAKE-FR-038` **WHEN `diagnosis_case.status` changes for any reason, THE intake module SHALL publish
exactly one `CaseStatusChanged` carrying the previous and new status.** No transition is silent.

`INTAKE-FR-039` **THE intake module SHALL carry the submitting request's correlation id on
`diagnosis_case.correlation_id` and on every event it publishes for that case**, including events
raised by listeners hours later (`COMMON-NFR-016`).

### 4.7 The state machine

`INTAKE-FR-040` **WHEN `CaseSubmitted` is received, THE intake module SHALL transition the case from
`SUBMITTED` to `ANALYSING`.**

`INTAKE-FR-041` **WHEN `AnalysisCompleted` is received, THE intake module SHALL write
`diagnosis_case.decision_path` from the event, SHALL transition `ANALYSING → ANALYSED`, and SHALL then
transition `ANALYSED → IN_REVIEW`, in one transaction, publishing two `CaseStatusChanged` events.**
(INV-10, and the `[DERIVED]` note in §3.4.)

`INTAKE-FR-042` **WHEN `AnalysisFailed` is received, THE intake module SHALL transition
`ANALYSING → FAILED` and SHALL leave `decision_path` null.**

`INTAKE-FR-043` **WHEN `AdvisoryApproved` is received, THE intake module SHALL transition the case to
`ADVISED`.**

`INTAKE-FR-044` **WHEN `CaseRejected` is received, THE intake module SHALL transition the case to
`REJECTED`.**

`INTAKE-FR-045` **IF a transition is requested that does not appear in §3.4 — including any transition
out of the terminal `ADVISED` or `REJECTED` (INV-6, clarification item 5) — THEN THE intake module
SHALL leave the status unchanged, SHALL publish no `CaseStatusChanged`, and SHALL log a `WARN` with
the case id, both statuses and the correlation id.** *(A listener must not poison the event registry
by throwing on an out-of-order redelivery — `COMMON-ARCH-015`.)*

`INTAKE-FR-046` **IF a transition is requested whose target equals the case's current status, THEN THE
intake module SHALL treat it as a successful no-op and SHALL publish no event.** (Upholds
`INTAKE-NFR-001`.)

`INTAKE-NFR-004` **THE intake module SHALL guard every status write with the optimistic lock on
`diagnosis_case.version`**, and SHALL retry a listener once on an optimistic-lock failure before
letting the event registry retry it.

### 4.8 Resubmission after rejection

`INTAKE-FR-050` **THE intake module SHALL accept an optional `parentCaseId` on
`POST /api/v1/cases` and SHALL store it in `diagnosis_case.parent_case_id`.** (Clarification item 5 —
rejection is terminal; the farmer creates a *new* case that carries the link.)

`INTAKE-FR-051` **IF `parentCaseId` does not exist, belongs to a different farmer, or is not in status
`REJECTED`, THEN THE intake module SHALL respond `400` with code `ERR_PARENT_CASE_INVALID`.**
(INV-9. A parent belonging to another farmer produces the same code as a non-existent one, so
ownership is not probeable — `COMMON-API-001`.)

`INTAKE-FR-052` **THE intake module SHALL NOT reopen, mutate or copy a rejected case, and SHALL expose
`parentCaseId` on both `CaseSummary` and `CaseSubmitted`.** A resubmission is a wholly new aggregate
with its own images, idempotency key and status history; carrying the link on the event is what lets
`review` set `p_officer_queue.is_resubmission` without querying `intake`.

### 4.9 The farmer history projection

`INTAKE-FR-060` **WHEN `CaseStatusChanged` is received, THE intake module SHALL upsert the
`p_farmer_case_history` row for that case**, setting `farmer_id`, `crop_name_bn` (from
`KnowledgeQueryApi`), `status`, `submitted_at`, `thumbnail_object_key` (the primary image's
`derivative_object_key`) and `updated_at`.

`INTAKE-FR-061` **WHEN one of the events below is received, THE intake module SHALL set the listed
`p_farmer_case_history` columns from that event's fields.**

| Event | Columns set |
|---|---|
| `AnalysisCompleted` | `decision_path` |
| `AdvisoryApproved` | `advisory_id`, `advisory_version`, `disease_name_bn`, `officer_name`, `published_at` |
| `CaseRejected` | `rejection_message_bn` |

`INTAKE-FR-064` **THE intake module SHALL make every projection write an idempotent upsert keyed by
`case_id`**, so a redelivered event produces no duplicate and no lost field.

`INTAKE-NFR-005` **THE `p_farmer_case_history` row SHALL be treated as a list-view convenience, not as
the authoritative advisory.** `intake` does not consume `AdvisoryRevised` (`00-common` §6.3 routes it
to `notification` only), so after a revision the projection's `advisory_version` may lag. The farmer's
advisory **content** is always served by `review`, which is authoritative.
*(`[DERIVED]` — the alternative, adding `intake` as a consumer of `AdvisoryRevised`, would contradict
the frozen publish/consume map. Recorded here so the staleness is a known, bounded seam rather than a
surprise; the case list shows status and disease name, the case view shows the current advisory.)*

`INTAKE-DATA-006` **THE intake module SHALL NOT write `case_image.rejected_reason` in this scope.**
`[DEFERRED]` *Seam: the column is the extension point for partial acceptance — keeping the images that
pass the gate and recording the reason for those that do not. `INTAKE-FR-024` makes the gate
all-or-nothing today.*

### 4.10 Read endpoints

`INTAKE-FR-070` **WHEN a farmer requests a presigned URL for an image or the audio of a case they own,
THE intake module SHALL issue one valid for `foshol.storage.presign-ttl` and SHALL return it with its
expiry.** (`COMMON-SEC-016`.)

`INTAKE-SEC-007` **IF a `FARMER` requests any case, image or audio belonging to another farmer, THEN
THE intake module SHALL respond `404` with code `ERR_CASE_NOT_FOUND`, never `403`.**
(Implements `COMMON-API-001`. Ownership must not be probeable.)

`INTAKE-SEC-008` **THE intake module SHALL permit an `OFFICER` or `ADMIN` to read any case, its images
and its audio**, because every case reaches the review queue (`COMMON-SEC-011`).

`INTAKE-FR-071` **THE intake module SHALL return the farmer's case list from
`p_farmer_case_history`, ordered by `submitted_at` descending, paginated per `00-common` §8.2**, and
SHALL scope it to the authenticated farmer.

`INTAKE-FR-072` **THE intake module SHALL NOT return a MinIO object key in any farmer-facing
response.** Images are referenced by `imageId` and fetched through `INTAKE-FR-070`.
*(An object key in a response body is a durable identifier for a private object; the presigned URL is
not.)*

`INTAKE-API-001` **THE intake module SHALL implement `CaseIntakeApi.findById` to return a
`CaseSummary` for any existing case without an ownership check, and `CaseIntakeApi.isOwnedBy` to
return `true` only when the case exists and its `farmer_id` equals the supplied id.** The API is
module-to-module; the ownership check belongs to the web layer (`INTAKE-SEC-007`).

---

## 5. API surface

Base path `/api/v1`. Errors are RFC 9457 (`COMMON-API-002`), localised per `COMMON-API-003`.

### 5.1 `POST /api/v1/cases`

| | |
|---|---|
| Auth | `FARMER` |
| Headers | `Idempotency-Key: <uuid>` **required**; `X-Correlation-Id` optional |
| Body | `multipart/form-data` — `images` (1–3 parts), `audio` (0–1), `cropId` (uuid), `note` (optional), `parentCaseId` (optional uuid), `audioDurationMs` (required with `audio`) |
| Success | `202` · `Location: /api/v1/cases/{caseId}` · `{ "caseId": "018f…", "status": "SUBMITTED", "submittedAt": "…Z" }` |
| Replay | `202` · identical body · `Idempotency-Replayed: true` |
| `400` | `ERR_IMAGE_COUNT` · `ERR_CROP_NOT_FOUND` · `ERR_PARENT_CASE_INVALID` · `ERR_AUDIO_TOO_LONG` · `ERR_AUDIO_UNREADABLE` · `ERR_IDEMPOTENCY_KEY_MISSING` · `ERR_IDEMPOTENCY_KEY_INVALID` · `ERR_IMAGE_QUALITY_REJECTED` |
| `409` | `ERR_IDEMPOTENCY_KEY_CONFLICT` |
| `413` | `ERR_IMAGE_TOO_LARGE` · `ERR_AUDIO_TOO_LARGE` |
| `415` | `ERR_UNSUPPORTED_MEDIA_TYPE` |
| `429` | `ERR_CASE_RATE_LIMITED` + `Retry-After` |
| `503` | `ERR_STORAGE_UNAVAILABLE` |

### 5.2 `GET /api/v1/cases`

| | |
|---|---|
| Auth | `FARMER` — own cases only |
| Query | `page` (0-based), `size` (default 20, max 100) |
| Success | `200` · paginated envelope of `FarmerCaseRow` |

```json
{ "caseId": "018f…", "cropNameBn": "…", "cropNameEn": "…", "cropNameEnFallback": false,
  "status": "IN_REVIEW", "decisionPath": "SECONDARY",
  "diseaseNameBn": null, "diseaseNameEn": null, "diseaseNameEnFallback": true,
  "officerName": null, "advisoryVersion": null,
  "rejectionMessageBn": null, "submittedAt": "…Z", "publishedAt": null }
```

### 5.3 `GET /api/v1/cases/{caseId}`

| | |
|---|---|
| Auth | `FARMER` owner, or `OFFICER` / `ADMIN` |
| Success | `200` · case, `cropNameBn`, `cropNameEn`, `cropNameEnFallback` (`COMMON-NFR-038`), ordered image references (`imageId`, `position`, `primary`, `qualityScore`, `width`, `height`), audio reference (`audioId`, `durationMs`), `status`, `decisionPath`, `noteBn`, `parentCaseId`, `submittedAt` |
| `404` | `ERR_CASE_NOT_FOUND` — unknown, or another farmer's (`INTAKE-SEC-007`) |

No object key appears in the response (`INTAKE-FR-072`).

### 5.4 `GET /api/v1/cases/{caseId}/images/{imageId}/url`

| | |
|---|---|
| Auth | `FARMER` owner, or `OFFICER` / `ADMIN` |
| Success | `200` · `{ "url": "https://…", "expiresAt": "…Z" }` |
| `404` | `ERR_CASE_NOT_FOUND` · `ERR_IMAGE_NOT_FOUND` |

`?variant=derivative` selects `derivative_object_key`; the default is the original.

### 5.5 `GET /api/v1/cases/{caseId}/audio/url`

Same authorisation and shape as §5.4; `404` `ERR_AUDIO_NOT_FOUND` when the case has no audio.

---

## 6. Persistence

### 6.1 Tables owned

`diagnosis_case`, `case_image`, `case_audio`, `idempotency_key`, `p_farmer_case_history` — defined in
`00-common` §4.4 and §4.8 and **never redefined here**. Indexes used: `ix_case_farmer`,
`ix_case_status`, `ix_case_parent`, `ix_case_image_case`, `ix_case_image_sha`,
`ix_idempotency_expiry`, `ix_farmer_history` (`00-common` §4.9).

`INTAKE-DATA-007` **THE intake module SHALL reference `farmer_id` and `crop_id` as raw `UUID` columns
with no JPA association** (`COMMON-ARCH-006`); the database-level foreign keys declared in
`00-common` §4.4 remain in force.

`INTAKE-DATA-008` **THE intake module SHALL NOT write `case_audio.transcript_bn` or
`case_audio.asr_confidence`.** Those two columns are written by `analysis`
(`12-analysis.ears.md`); `intake` creates the row with both null.

`INTAKE-DATA-009` **THE intake module SHALL never delete a `diagnosis_case`, `case_image` or
`case_audio` row and SHALL never delete a stored object once its case has committed.**
(Clarification item 15 — case data is never deleted.)

### 6.2 Query patterns

| Query | Access path | Index |
|---|---|---|
| Case by id | primary key | PK |
| Ownership check | `SELECT farmer_id FROM diagnosis_case WHERE id = ?` | PK |
| Farmer case list | `p_farmer_case_history WHERE farmer_id = ? ORDER BY submitted_at DESC` | `ix_farmer_history` |
| Rate-limit count | `diagnosis_case WHERE farmer_id = ? AND created_at > ?` | `ix_case_farmer` |
| Images of a case | `case_image WHERE case_id = ? ORDER BY position` | `ix_case_image_case` |
| Idempotency lookup | primary key on `idempotency_key.key` | PK |
| Expired key purge | `DELETE FROM idempotency_key WHERE expires_at < now()` | `ix_idempotency_expiry` |
| Resubmission chain | `diagnosis_case WHERE parent_case_id = ?` | `ix_case_parent` |

`INTAKE-DATA-010` **THE query side SHALL read through the `@ReadOnlyDataSource`-qualified
`DataSource` and SHALL NOT load an aggregate** (`COMMON-ARCH-007`); the farmer case list and case
detail are served from projection and entity **rows mapped straight to `record` views**.

---

## 7. Acceptance criteria

| Requirement | Given | When | Then |
|---|---|---|---|
| `INTAKE-FR-001` | a valid multipart submission | it is posted | the call passes through `WebIntakeAdapter.submit` |
| `INTAKE-FR-002` | the module's bean registry | it is inspected | exactly one `CaseIntakeChannel` bean exists |
| `INTAKE-FR-010` | 2 sharp images and a valid crop | `POST /cases` | `202`, `Location` set, one `diagnosis_case` row with `SUBMITTED` |
| `INTAKE-FR-011` | a body carrying a foreign `farmerId` | `POST /cases` | the case belongs to the token's subject |
| `INTAKE-FR-012` | 4 images | `POST /cases` | `400` `ERR_IMAGE_COUNT`, no rows |
| `INTAKE-FR-013` | an unknown `cropId` | `POST /cases` | `400` `ERR_CROP_NOT_FOUND` |
| `INTAKE-FR-014` | a note with decomposed Bangla | `POST /cases` | `note_bn` is stored in NFC |
| `INTAKE-SEC-002` | the `COMMON-SEC-015` ceiling reached | one more submission | `429`, `Retry-After` present, no case |
| `INTAKE-SEC-001` | a `.png` file renamed `.jpg` with `Content-Type: image/jpeg` | `POST /cases` | `case_image.content_type` is `image/png` |
| `INTAKE-SEC-003` | a PDF named `leaf.jpg` | `POST /cases` | `415` `ERR_UNSUPPORTED_MEDIA_TYPE` |
| `INTAKE-SEC-004` | an image one byte over the ceiling | `POST /cases` | `413` `ERR_IMAGE_TOO_LARGE` |
| `INTAKE-FR-015` | audio of `max-audio-seconds + 1`, and audio with an unreadable header | each is posted | `400` `ERR_AUDIO_TOO_LONG`, then `400` `ERR_AUDIO_UNREADABLE` |
| `INTAKE-SEC-005` | a part named `../../etc/passwd` | `POST /cases` | the object key contains only generated ids |
| `INTAKE-FR-020` | a known sharp and a known blurred fixture | both are probed | the sharp one has the higher `blurVariance` |
| `INTAKE-FR-021` | one blurred image | `POST /cases` | `400` `ERR_IMAGE_QUALITY_REJECTED`, reason `BLURRY` |
| `INTAKE-FR-022` | one under-exposed and one over-exposed image | each is posted | reasons `TOO_DARK` and `TOO_BRIGHT` |
| `INTAKE-FR-023` | a 160 × 160 image | `POST /cases` | reason `TOO_SMALL` |
| `INTAKE-FR-024` | one good and one blurred image | `POST /cases` | `400`; MinIO holds no object; no case row; no `idempotency_key` row |
| `INTAKE-UX-001` | two failing images | `POST /cases` | `errors` has two entries with part index and reason, `detail` is Bangla |
| `INTAKE-FR-025` | an accepted image | it is persisted | `quality_score` is in `[0,1]`, 3 decimal places, and matches the formula |
| `INTAKE-FR-026` | three images with distinct quality scores | `POST /cases` | exactly one `is_primary`, and it is the highest scorer |
| `INTAKE-FR-027` | a valid submission | it succeeds | MinIO holds one original and one derivative per image |
| `INTAKE-FR-028` | a 4000 px image with `derivative-max-edge-px = 1024` | it is stored | the derivative's longest edge is 1024 |
| `INTAKE-DATA-002` | a fixture image with a known digest | it is stored | `case_image.sha256` equals that digest |
| `INTAKE-NFR-003` | MinIO stopped | `POST /cases` | `503` `ERR_STORAGE_UNAVAILABLE` and no case row |
| `INTAKE-FR-030` | two submissions with identical content | their hashes are computed | the two `request_hash` values are equal |
| `INTAKE-FR-031` | no `Idempotency-Key` header | `POST /cases` | `400` `ERR_IDEMPOTENCY_KEY_MISSING` |
| `INTAKE-FR-032` | a successful submission | the identical request is repeated with the same key | `202`, the same `caseId`, `Idempotency-Replayed: true`, still one case |
| `INTAKE-FR-033` | a used key | a different body with that key | `409` `ERR_IDEMPOTENCY_KEY_CONFLICT` |
| `INTAKE-FR-034` | a successful submission | the row is read | `expires_at ≈ now + foshol.intake.idempotency.ttl` |
| `INTAKE-FR-035` | two concurrent identical submissions | both complete | one case exists and both callers get the same body |
| `INTAKE-DATA-005` | an expired key row | any submission runs | the expired row is gone |
| `INTAKE-FR-036` | a submission with 2 images and audio | it commits | `CaseSubmitted` carries 2 `CaseImageRef`s and a `CaseAudioRef` |
| `INTAKE-FR-037` | a submission that rolls back | the transaction ends | no `CaseSubmitted` is delivered |
| `INTAKE-FR-038` | any transition | it completes | exactly one `CaseStatusChanged` with the correct from/to |
| `INTAKE-FR-039` | a request with `X-Correlation-Id: abc` | the case is created | `correlation_id = abc` and every event carries it |
| `INTAKE-FR-040` | a case in `SUBMITTED` | `CaseSubmitted` is delivered | status is `ANALYSING` |
| `INTAKE-FR-041` | a case in `ANALYSING` | `AnalysisCompleted(SECONDARY)` | status is `IN_REVIEW`, `decision_path = SECONDARY`, two `CaseStatusChanged` published |
| `INTAKE-FR-042` | a case in `ANALYSING` | `AnalysisFailed` | status is `FAILED`, `decision_path` null |
| `INTAKE-FR-043` | a case in `IN_REVIEW` | `AdvisoryApproved` | status is `ADVISED` |
| `INTAKE-FR-044` | a case in `IN_REVIEW` | `CaseRejected` | status is `REJECTED` |
| `INTAKE-FR-043`/`FR-044` (FAILED path) | a case in `FAILED` | `AdvisoryApproved`, then separately `CaseRejected` | `ADVISED` and `REJECTED` respectively |
| `INTAKE-FR-045` | a case in `SUBMITTED`, and a case in `REJECTED` | `AdvisoryApproved` is delivered to each | status unchanged in both, no event, one `WARN` each, no exception thrown |
| `INTAKE-FR-046` | a case in `ANALYSING` | `CaseSubmitted` is redelivered | status unchanged, no second event |
| `INTAKE-NFR-004` | two listeners racing on one case | both run | one wins, the other retries and no-ops |
| `INTAKE-FR-050` | a rejected case of the same farmer | a new submission with that `parentCaseId` | `202` and `parent_case_id` set |
| `INTAKE-FR-051` | a parent in `ADVISED`, and a parent of another farmer | each is submitted | `400` `ERR_PARENT_CASE_INVALID` in both cases |
| `INTAKE-FR-052` | a rejected parent | a resubmission commits | the parent's status and images are unchanged and `CaseSubmitted.parentCaseId` is populated |
| `INTAKE-FR-060` | a new case | `CaseStatusChanged` is delivered | a `p_farmer_case_history` row exists with crop name, status and thumbnail |
| `INTAKE-FR-061` | one case per row of the §4.9 table | each event is delivered | exactly the listed columns are populated from the event |
| `INTAKE-FR-064` | any projection event | it is delivered twice | exactly one row, no field lost |
| `INTAKE-FR-070` | a farmer's own image | `GET …/url` | `200` with a URL that expires within `foshol.storage.presign-ttl` |
| `INTAKE-SEC-007` | farmer B's case | farmer A requests it, its image, and its audio | `404` `ERR_CASE_NOT_FOUND` in all three |
| `INTAKE-SEC-008` | any case | an `OFFICER` token requests it | `200` |
| `INTAKE-FR-071` | 25 cases for one farmer | `GET /cases?size=20` | 20 rows, newest first, `totalElements = 25` |
| `INTAKE-FR-072` | any farmer-facing response | it is serialised | no `objectKey` field is present |
| `INTAKE-API-001` | an existing case and two farmer ids | `findById`, then `isOwnedBy` for each id | a populated `CaseSummary`; `true` for the owner, `false` for the other |
| `INTAKE-DATA-008` | a newly created case with audio | the row is read | `transcript_bn` and `asr_confidence` are null |

---

## 8. Test requirements

### 8.1 Unit tests (no Spring context)

| Target | Must assert |
|---|---|
| `DiagnosisCase` aggregate | INV-1 … INV-11, every legal and one illegal transition per state |
| `TransitionAllowedSpec` | The full §3.4 matrix, true and false |
| `ResubmissionParentSpec` | INV-9, all three failure modes |
| `ImageQualitySpec` and the `quality_score` formula | Each of the four `QualityReason` branches, and monotonicity of `quality_score` in `blur_variance` |
| `ContentTypeSniffer` | Every signature in §4.2, plus a truncated file and an unknown type |
| `RequestFingerprint` | Stability across part ordering metadata; change on any content change |
| `IdempotencyReplaySpec` | Replay, conflict on hash, conflict on farmer |
| **Every** command handler | `SubmitCaseCommandHandler`, `ChangeCaseStatusCommandHandler`, `RecordAnalysisOutcomeCommandHandler` |
| **Every** query handler | `FarmerCaseListQueryHandler`, `CaseDetailQueryHandler`, `CaseImageUrlQueryHandler`, `CaseAudioUrlQueryHandler` |
| Event listeners | `INTAKE-FR-040` … `INTAKE-FR-046` and `INTAKE-FR-060` … `INTAKE-FR-064`, each with a mocked repository |

`ImageStorePort` is mocked in every unit test; no unit test touches MinIO.

### 8.2 Integration test — exactly one (`00-common` §11.3)

`IntakeIntegrationTest` on Testcontainers PostgreSQL + MinIO, profile `test`:

> Insert one farmer and one crop → `POST /api/v1/cases` with two sharp fixture images, one 2-second
> WAV and an `Idempotency-Key` → assert `202` and `Location` → assert one `diagnosis_case` in
> `SUBMITTED`, two `case_image` rows with exactly one primary and correct SHA-256 digests, one
> `case_audio` row, four MinIO objects, one `idempotency_key` row → assert `CaseSubmitted` was
> published → repeat the identical request with the same key → assert `202`,
> `Idempotency-Replayed: true`, the same `caseId` and still one case → post the same request with a
> blurred image and a fresh key → assert `400` `ERR_IMAGE_QUALITY_REJECTED` and no new object in
> MinIO.

No per-endpoint integration tests (`00-common` §11 test floor).

### 8.3 Fixtures

| Fixture | Purpose |
|---|---|
| `sharp-rice-leaf.jpg` | Passes the gate; `blur_variance` comfortably above the minimum |
| `blurred-rice-leaf.jpg` | Same subject, Gaussian-blurred; drives `BLURRY` |
| `dark-leaf.jpg` / `bright-leaf.jpg` | Drive `TOO_DARK` and `TOO_BRIGHT` |
| `tiny-leaf.png` | 160 × 160; drives `TOO_SMALL` |
| `oversized.jpg` | One byte over `foshol.intake.max-image-bytes` |
| `not-an-image.pdf` | Drives `415` |
| `leaf-as-png-named-jpg.jpg` | Drives `INTAKE-SEC-001` |
| `speech-2s.wav` | Valid audio, known sample rate and duration |
| `IntakeFixtures.imageDigests()` | Pre-computed SHA-256 per image, so `INTAKE-DATA-002` is asserted against a constant |

`INTAKE-NFR-006` **THE image fixtures SHALL be committed to the repository and SHALL be plant
photographs with no identifiable person in them**, and SHALL be the same files that
`tools/build_fixtures.py` keys replay responses by (`COMMON-NFR-022`).

---

## 9. Agent execution notes

### 9.1 Implementation order

1. **Wait for the Day-0 gate**: `common` enums (`CaseStatus`, `DecisionPath`, `QualityReason`),
   `ErrorCodes` and `ConfigKeys` entries, the frozen `intake.api` package, and a working
   `identity` filter chain (`10-identity.ears.md` §9.2). Do not start before these compile.
2. `intake/domain/` — `DiagnosisCase`, `CaseImage`, `CaseAudio`, the value objects of §3.2, the five
   specifications, `IllegalCaseTransitionException`, `CaseNotFoundException`. No Spring, no JPA
   (`COMMON-ARCH-004`). **Write the state-machine unit tests here, before any adapter exists.**
3. `intake/application/port/` — `ImageStorePort`, `ImageQualityPort`.
4. `intake/application/command/` — `SubmitCaseCommandHandler` first; it is the module's centre of
   gravity and everything else is a listener or a query. Then the status handlers.
5. `intake/application/query/` — the four query handlers of §8.1.
6. `intake/infrastructure/` — JPA entities and repositories, hand-written mappers,
   `MinioImageStoreAdapter`, the local quality adapter, the projection writer, and the six event
   listeners of §2.3.
7. `intake/web/` — `CaseController`, `CaseMediaController`, request/response records, the
   `Idempotency-Key` argument resolver.
8. `intake/api/` implementation — `CaseIntakeService` implementing `CaseIntakeApi`, and
   `WebIntakeAdapter` implementing `CaseIntakeChannel`. **Never edit the interfaces**
   (`COMMON-NFR-041`).
9. `IntakeIntegrationTest` last.
10. Raise the `COMMON-SEC-015` externalisation blocker to A1 (§4.1) at the first checkpoint.

### 9.2 Ordering constraints for other agents

- `analysis` (A3) cannot start its pipeline test until `CaseSubmitted` is published — land steps 1–6
  by the **Day-1 checkpoint**.
- `review` (A5) needs `CaseIntakeApi` and `parentCaseId` on `CaseSubmitted` for
  `p_officer_queue.is_resubmission`.
- `notification` (A5) needs `CaseStatusChanged`.
- `70-frontend` (A6) needs §5 reflected in `docs/openapi/foshol-api.yaml`; request that update from A1
  as soon as §5 is stable — A2 does not own that file.

### 9.3 Local Definition of Done

All ten clauses of `00-common` §11, plus:

1. Every requirement in §4 is implemented, or listed as `[DEFERRED]` in `docs/progress/a2.md`
   (`COMMON-NFR-045`). The `[DEFERRED]` set is exactly `INTAKE-FR-002` and `INTAKE-DATA-006`.
2. `docker compose down -v && docker compose up`, then a submission of the blurred fixture returns
   `400` with a Bangla `detail` and leaves the MinIO bucket empty — **demo beat 3, verified by hand
   once and by `IntakeIntegrationTest` always**.
3. A `grep` of `modules/intake/` finds no write to `case_audio.transcript_bn`, no reference to
   `case_rejection`, `review_task`, `advisory`, `analysis_run`, `case_candidate` or `case_symptom`,
   and no import from any module package other than `common`, `identity.api`, `knowledge.api` and the
   two consumed records from `review.api`.
4. Every numeric literal in `modules/intake/` traces to a property in `00-common` §9, except the two
   `COMMON-SEC-015` values whose blocker is open.
5. `ApplicationModules.verify()` reports `intake` depending on `common`, `identity`, `knowledge` and
   `review` (events only), and on nothing else.

### 9.4 Requirement index

| Category | IDs | Count |
|---|---|---|
| `FR` | `-001` … `-003`, `-010` … `-015`, `-020` … `-028`, `-030` … `-046`, `-050` … `-052`, `-060`, `-061`, `-064`, `-070` … `-072` | 44 |
| `SEC` | `INTAKE-SEC-001` … `INTAKE-SEC-008` | 8 |
| `DATA` | `INTAKE-DATA-001` … `INTAKE-DATA-010` | 10 |
| `API` | `INTAKE-API-001` | 1 |
| `NFR` | `INTAKE-NFR-001` … `INTAKE-NFR-006` | 6 |
| `UX` | `INTAKE-UX-001` … `INTAKE-UX-002` | 2 |
| **Total** | | **71** |
