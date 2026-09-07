# 12 — Analysis (EARS)

**Module:** `com.rootcause.foshol.analysis` · **Agent:** A3 · **Prefix:** `ANALYSIS`
**Reads first:** `00-common.ears.md` (FROZEN). This document references that contract and never
redefines it. Where the two disagree, `00-common.ears.md` wins.

This is the orchestration module: the one place where the vision branch, the speech branch, the
knowledge base and the confidence policy meet. It is also where the demo's three decision paths and
the live sidecar-kill degradation beat are produced.

---

## 1. Scope

### 1.1 What analysis owns

| Owns | Detail |
|---|---|
| The pipeline | Fan-out, deadline, branch abandonment, result assembly (§4.2) |
| Model label resolution | Every raw label → `disease.id`, per `COMMON-DATA-010`…`017` (§4.4) |
| The `ConfidenceRouter` | The single authority on `PRIMARY` / `SECONDARY` / `UNDETERMINED` (§4.5) |
| Outbound AI ports | `VisionModelPort`, `SpeechToTextPort`, `TextEmbeddingPort`, `ExplainabilityPort` and their two adapter families (§4.3) |
| Grad-CAM production and storage | Overlay bytes → MinIO → `analysis_run.gradcam_object_key` (§4.6) |
| Tables | `case_symptom`, `case_candidate`, `analysis_run` (§6) |
| Published surface | `AnalysisApi`, events `AnalysisCompleted` and `AnalysisFailed` |
| Endpoints | `GET /api/v1/cases/{id}/analysis`, `GET /api/v1/cases/{id}/gradcam` (§5) |

### 1.2 What analysis does NOT own

| Not owned | Owner | Why it matters here |
|---|---|---|
| The images and the audio — upload, quality gate, MinIO objects, `case_image`, `case_audio` | `intake` (`11-intake.ears.md`) | Analysis receives object keys and SHA-256 digests on `CaseSubmitted` and reads bytes; it never writes those tables |
| Case status and the case state machine (`diagnosis_case.status`, `.decision_path`) | `intake` | Analysis reports a decision path **on an event**; intake persists it |
| The knowledge base — `crop`, `disease`, `symptom`, `symptom_phrase`, `disease_symptom`, `remedy`, `model_label_map` | `knowledge` (`13-knowledge.ears.md`) | Read-only through `KnowledgeQueryApi` |
| pgvector kNN retrieval, the fuzzy fallback layer, weighted `disease_symptom` scoring | `knowledge` | **Analysis supplies the 768-d embedding; knowledge does the search.** Analysis has no vector query of its own (clarification item 10) |
| The officer queue, claims, advisories, remedy prefill, rejection | `review` (`14-review.ears.md`) | Analysis publishes `AnalysisCompleted` and stops |
| Notifications and SSE fan-out | `notification` (`15-notification.ears.md`) | — |
| Model hosting, model selection by crop, Grad-CAM computation, embedding computation | the sidecar (`60-inference-sidecar.ears.md`) | Analysis calls it; it does not host weights |
| Confidence threshold **values** | human content owners (`CONTENT-OWNERS.md`) | Analysis owns the policy, not the numbers |

> **The plan's `/v1/symptoms/extract` endpoint does not exist.** Clarification item 10 resolves the
> plan's self-contradiction: the sidecar gets no database credentials, so it cannot search
> `symptom_phrase.embedding`. The sidecar exposes `/v1/embed`; retrieval lives in `knowledge` behind
> `SymptomMatchApi`. Do not reintroduce `/v1/symptoms/extract`.

---

## 2. Dependencies

### 2.1 Published interfaces this module calls

Permitted by the §6.1 dependency matrix: `common`, `intake`, `knowledge`. **Nothing else.**

```java
// com.rootcause.foshol.intake.api
public interface CaseIntakeApi {
    Optional<CaseSummary> findById(UUID caseId);
    boolean isOwnedBy(UUID caseId, UUID farmerId);
}

// com.rootcause.foshol.knowledge.api
public interface KnowledgeQueryApi {
    Optional<DiseaseView> findDiseaseById(UUID diseaseId);
    List<RemedyView> listActiveRemedies(UUID diseaseId);
    List<SymptomRefView> listSymptoms();
    Optional<UUID> resolveModelLabel(String modelId, String modelVersion, String rawLabel);
    // …remaining members per 00-common §6.2
}

public interface SymptomMatchApi {
    SymptomMatchResult match(SymptomMatchRequest request);
}
public record SymptomMatchRequest(UUID cropId, String transcriptBn,
                                  float[] transcriptEmbedding, List<UUID> officerSymptomIds) {}
public record SymptomMatchResult(List<MatchedSymptom> symptoms, List<ScoredDisease> diseases,
                                 boolean inconclusive) {}
```

### 2.2 Events consumed

`CaseSubmitted`, published by `intake` — signature in `00-common.ears.md` §6.3. This is the only
event analysis consumes.

### 2.3 Events published

`AnalysisCompleted` and `AnalysisFailed` — signatures in `00-common.ears.md` §6.3. Consumed by
`review` and `intake` per the publish/consume map. Analysis publishes nothing else.

### 2.4 Published interface this module implements

```java
// com.rootcause.foshol.analysis.api
public interface AnalysisApi {
    Optional<AnalysisView> findByCaseId(UUID caseId);
    void recordOfficerSymptoms(UUID caseId, List<UUID> symptomIds);
}
```

`AnalysisView`, `CandidateView`, `SymptomView` are frozen in `00-common.ears.md` §6.2.

---

## 3. Domain model

### 3.1 `AnalysisRun` — the aggregate root

One `AnalysisRun` per executed pipeline, identified by a UUIDv7, referencing `caseId` as a raw
`UUID` (`COMMON-ARCH-006`). It owns two child collections: `CaseCandidate` and `CaseSymptom`.

| Invariant | Statement |
|---|---|
| `INV-A1` | `mode` is exactly one of `REPLAY`, `LIVE`, and equals the active `foshol.ai.mode`. |
| `INV-A2` | `decisionPath` is non-null once the run is completed, and is one of `PRIMARY`, `SECONDARY`, `UNDETERMINED`. |
| `INV-A3` | `top2Confidence` is null unless at least two ranked candidates exist; `margin` is null exactly when `top2Confidence` is null, and otherwise equals `top1Confidence − top2Confidence`. |
| `INV-A4` | `margin` is never an input to `decisionPath`. |
| `INV-A5` | `unmappedLabels` is a JSON array of distinct raw label strings, never null; the empty array is the default. |
| `INV-A6` | `gradcamObjectKey` is non-null only when the overlay bytes were successfully stored. |
| `INV-A7` | `errorCode` is non-null if and only if at least one branch degraded, timed out or failed. |
| `INV-A8` | `latencyMs` is the wall-clock span from pipeline start to result assembly and is always recorded, including on the degraded path. |
| `INV-A9` | A completed run for a case is terminal: it is never mutated after `AnalysisCompleted` is published. |

### 3.2 `CaseCandidate` — entity

| Invariant | Statement |
|---|---|
| `INV-C1` | `confidence` ∈ [0, 1] at scale 4, half-up rounding. |
| `INV-C2` | `rank` is 1-based, dense, and strictly increasing with descending `confidence` within a `(caseId, source)` pair. |
| `INV-C3` | `source` ∈ {`MODEL`, `KB`, `MERGED`}; `(caseId, diseaseId, source)` is unique. |
| `INV-C4` | Every `diseaseId` resolved through `KnowledgeQueryApi`; a raw label never reaches this entity. |
| `INV-C5` | The candidate list for a `(caseId, source)` pair holds at most `foshol.analysis.candidate-limit` rows. |

### 3.3 `CaseSymptom` — entity

| Invariant | Statement |
|---|---|
| `INV-S1` | `source` ∈ {`SPEECH`, `VISION`, `OFFICER`}; `VISION` is never written in this build (§4.8). |
| `INV-S2` | `matcher` is `VECTOR` or `FUZZY` when `source = SPEECH` and is `MANUAL` when `source = OFFICER`. |
| `INV-S3` | `score` ∈ (0, 1] at scale 3; an `OFFICER` symptom carries `1.000`. |
| `INV-S4` | `(caseId, symptomId, source)` is unique; recording the same officer symptom twice is a no-op. |

### 3.4 Value objects and enums

`BranchOutcome` (`COMPLETED` | `ABANDONED` | `FAILED`) · `RawCandidate(String rawLabel, BigDecimal
confidence)` · `MappedCandidate(UUID diseaseId, BigDecimal confidence)` · `RoutingDecision(DecisionPath
path, BigDecimal top1, BigDecimal top2, BigDecimal margin, String errorCode)`.
Enums `DecisionPath`, `AiMode`, `CandidateSource`, `SymptomSource` live in `common`
(`COMMON-ARCH-013`) and are not redeclared here.

### 3.5 Specifications

| Specification | Rule |
|---|---|
| `PrimaryPathSpec` | `top1 ≥ foshol.analysis.confidence.high` **and** the top-1 disease is prescribable (§4.5) |
| `SecondaryPathSpec` | `foshol.analysis.confidence.low ≤ top1 < foshol.analysis.confidence.high` **and** the KB result is conclusive |
| `UndeterminedPathSpec` | negation of both — the catch-all, and the only path reachable from a degraded run |
| `PrescribableSpec` | the disease is `healthy`, **or** `KnowledgeQueryApi.listActiveRemedies(diseaseId)` is non-empty |

---

## 4. Requirements

### 4.1 Pipeline entry and run lifecycle

`ANALYSIS-FR-001` **WHEN `CaseSubmitted` is delivered, THE analysis module SHALL execute one analysis
run for the case and SHALL persist exactly one `analysis_run` row for it.**

`ANALYSIS-FR-002` **IF a completed `analysis_run` row already exists for the case id, THEN THE
analysis module SHALL return without re-running the pipeline and without re-publishing
`AnalysisCompleted`.** *(`COMMON-ARCH-015`: a republished event must not produce a duplicate row or a
duplicate officer queue entry.)*

`ANALYSIS-FR-003` **WHEN a run starts, THE analysis module SHALL load the case through
`CaseIntakeApi.findById`, and IF the case is absent, THEN THE module SHALL publish `AnalysisFailed`
with error code `ERR_CASE_NOT_FOUND` and SHALL NOT persist an `analysis_run` row.**

`ANALYSIS-FR-004` **THE analysis module SHALL adopt the `correlationId` carried on `CaseSubmitted`
into the SLF4J MDC for the whole run and SHALL propagate it on every outbound sidecar call**
(`COMMON-NFR-016`).

`ANALYSIS-FR-005` **THE analysis module SHALL record `analysis_run.mode` as `REPLAY` or `LIVE`
according to the active `foshol.ai.mode`** (`COMMON-NFR-022`, `COMMON-NFR-023`).

`ANALYSIS-FR-006` **THE analysis module SHALL record `analysis_run.latency_ms` as the wall-clock
milliseconds from run start to result assembly, on every path including the degraded path.**

`ANALYSIS-FR-007` **THE analysis module SHALL record the model identifiers the sidecar reported in
`analysis_run.vision_model_id`, `.vision_model_version`, `.asr_model_id` and `.embed_model_id`, and
SHALL NOT record the configured identifiers in their place** (`COMMON-DATA-017`).

### 4.2 Concurrency, deadline and abandonment

`ANALYSIS-FR-020` **THE analysis orchestrator SHALL fan the vision branch and the speech branch out
concurrently onto an `ExecutorService` obtained from `Executors.newVirtualThreadPerTaskExecutor()`,
SHALL compose them with `java.util.concurrent.CompletableFuture`, and SHALL close that executor in a
try-with-resources block at the end of the run.**

`ANALYSIS-FR-021` **THE analysis module SHALL NOT use `java.util.concurrent.StructuredTaskScope` or
any other Java preview API.**

> **Read this before you "improve" the orchestrator.** `StructuredTaskScope` is a **preview** API on
> JDK 25. It needs `--enable-preview`, which `COMMON-NFR-002` forbids and which lives in a `buildSrc`
> convention plugin only agent A1 may touch — so reintroducing it breaks the build for every other
> agent. The plan's §3 diagram says "StructuredTaskScope joins the branches with a deadline"; that
> line is **struck** by clarification item 11. `CompletableFuture` on virtual threads gives the same
> fan-out, deadline and cancellation, with no preview flag.

`ANALYSIS-FR-022` **THE analysis orchestrator SHALL enforce a single hard deadline of
`foshol.analysis.deadline` measured from run start across both branches combined.**

`ANALYSIS-FR-023` **IF a branch has not completed when the deadline elapses, THEN THE orchestrator
SHALL cancel that branch with interruption, SHALL mark its outcome `ABANDONED`, and SHALL assemble a
result from whichever branches completed** (`COMMON-NFR-030`).

`ANALYSIS-FR-024` **WHEN a branch is abandoned, THE analysis module SHALL record
`analysis_run.error_code` as `ERR_VISION_BRANCH_TIMEOUT` or `ERR_SPEECH_BRANCH_TIMEOUT` and SHALL log
a `WARN` carrying the correlation id.**

`ANALYSIS-FR-025` **IF both branches are abandoned or failed, THEN THE analysis module SHALL route
the case to `UNDETERMINED` and SHALL still publish `AnalysisCompleted`** (`COMMON-NFR-035`).

`ANALYSIS-FR-026` **THE analysis module SHALL execute the pipeline off the event publication thread**,
so that a slow branch never blocks intake's transaction commit.

### 4.3 Outbound ports and their two adapter families

`ANALYSIS-NFR-001` **THE analysis module SHALL declare its outbound ports in
`com.rootcause.foshol.analysis.application.port` and SHALL NOT publish them in the `api` package.**
*(A port is an internal seam. Publishing it would let another module bind an adapter to our pipeline,
which `COMMON-ARCH-002` exists to prevent.)*

The four ports, exactly as they must be declared:

```java
package com.rootcause.foshol.analysis.application.port;

// ── vision ────────────────────────────────────────────────────────────────────
public interface VisionModelPort {
    VisionResult classify(VisionRequest request);
}
public record VisionRequest(UUID caseId, UUID imageId, String cropCode,
                            String objectKey, String sha256, String correlationId) {}
public record VisionResult(String modelId, String modelVersion,
                           List<RawCandidate> candidates, int latencyMs) {}
public record RawCandidate(String rawLabel, BigDecimal confidence) {}

// ── speech ────────────────────────────────────────────────────────────────────
public interface SpeechToTextPort {
    TranscriptResult transcribe(TranscriptRequest request);
}
public record TranscriptRequest(UUID caseId, UUID audioId, String objectKey, String sha256,
                                int durationMs, String correlationId) {}
public record TranscriptResult(String modelId, String transcriptBn,
                               BigDecimal asrConfidence, int latencyMs) {}

// ── embedding ─────────────────────────────────────────────────────────────────
public interface TextEmbeddingPort {
    EmbeddingResult embed(EmbeddingRequest request);
}
public record EmbeddingRequest(UUID caseId, String normalisedText, String correlationId) {}
public record EmbeddingResult(String modelId, float[] vector, int latencyMs) {}

// ── explainability ────────────────────────────────────────────────────────────
public interface ExplainabilityPort {
    ExplanationResult explain(ExplanationRequest request);
}
public record ExplanationRequest(UUID caseId, UUID imageId, String cropCode, String objectKey,
                                 String sha256, String rawLabel, String correlationId) {}
public record ExplanationResult(String modelId, String modelVersion,
                                byte[] overlayPng, String contentType, int latencyMs) {}

// ── object store ── [DERIVED] ─────────────────────────────────────────────────
public interface ObjectStorePort {
    byte[] read(String objectKey);
    void write(String objectKey, byte[] bytes, String contentType);
    PresignedUrl presign(String objectKey, Duration ttl);
}
public record PresignedUrl(String url, Instant expiresAt) {}
```

`ANALYSIS-NFR-002` **THE `ObjectStorePort` SHALL be declared as above.** `[DERIVED]` — *the four AI
ports need image and audio bytes, and Grad-CAM produces bytes that must be stored
(`analysis_run.gradcam_object_key`). `case_image` and `case_audio` belong to `intake`, so analysis
receives object keys, not bytes; without a narrow storage port this document would leave "how do the
bytes reach the sidecar" undefined. The adapter uses the shared `foshol.storage.*` properties and
performs no database access.*

`ANALYSIS-NFR-003` **THE analysis module SHALL provide two fully functional adapter families for
`VisionModelPort`, `SpeechToTextPort`, `TextEmbeddingPort` and `ExplainabilityPort`: an HTTP family
(`Http*Adapter`) and a fixture family (`Fixture*Adapter`)** (`COMMON-NFR-021`).

`ANALYSIS-NFR-004` **THE analysis module SHALL select the adapter family by `foshol.ai.mode` alone,
via `@ConditionalOnProperty(prefix = "foshol.ai", name = "mode", havingValue = "live" | "replay")`,
and SHALL expose exactly one bean per port in any given context.**

`ANALYSIS-NFR-005` **WHILE `foshol.ai.mode=live`, THE HTTP adapters SHALL call `foshol.ai.base-url`
at `POST /v1/vision/classify`, `POST /v1/vision/explain`, `POST /v1/asr/transcribe` and
`POST /v1/embed`, with a per-call timeout of `foshol.ai.timeout` and an `X-Correlation-Id` header.**

`ANALYSIS-NFR-006` **WHILE `foshol.ai.mode=replay`, THE fixture adapters SHALL resolve every response
from the classpath fixture store, keyed by the SHA-256 of the input**, with this layout:

| Port | Fixture path | Key |
|---|---|---|
| `VisionModelPort` | `classpath:/fixtures/vision/{sha256}.json` | image SHA-256 |
| `SpeechToTextPort` | `classpath:/fixtures/asr/{sha256}.json` | audio SHA-256 |
| `TextEmbeddingPort` | `classpath:/fixtures/embed/{sha256}.json` | SHA-256 of the NFC-normalised text |
| `ExplainabilityPort` | `classpath:/fixtures/gradcam/{sha256}.png` | primary image SHA-256 |

Fixtures live under `modules/analysis/src/main/resources/fixtures/`, are generated by
`tools/build_fixtures.py` against a live sidecar, and are committed by agent A3.

`ANALYSIS-NFR-007` **IF a fixture is absent for the requested key, THEN THE fixture adapter SHALL
raise the same failure type as an unavailable sidecar with error code `ERR_FIXTURE_MISSING`, SHALL log
a `WARN`, and SHALL NOT synthesise a response.** *(A fabricated fixture is an invented diagnosis. A
missing fixture must degrade, visibly, exactly like a dead sidecar.)*

`ANALYSIS-NFR-008` **THE analysis module SHALL produce byte-identical candidate sets, decision paths
and rankings across repeated runs of the same case WHILE `foshol.ai.mode=replay`.** *(Every demo case
must be reproducible on stage.)*

### 4.4 Model label mapping

Implements `COMMON-DATA-010` … `COMMON-DATA-017`. The order of operations is fixed and testable:

```
per image:  raw candidates → temperature rescale → label resolution → per-image de-duplication (MAX)
across images:  aggregate per disease (MAX) → sort → rank → truncate to candidate-limit
```

`ANALYSIS-FR-030` **THE analysis module SHALL resolve every raw label through
`KnowledgeQueryApi.resolveModelLabel(modelId, modelVersion, rawLabel)` using the `modelId` and
`modelVersion` the sidecar reported on that response.**

`ANALYSIS-FR-031` **THE analysis module SHALL NOT use `foshol.ai.vision.*` configuration values as the
mapping key.** *(`COMMON-DATA-017`: if the sidecar has been reconfigured, the mapping must follow the
model that actually ran, not the model Spring believes is configured.)*

`ANALYSIS-FR-032` **IF `resolveModelLabel` returns empty for a raw label, THEN THE analysis module
SHALL discard that candidate, SHALL append the raw label to `analysis_run.unmapped_labels`, SHALL log
a `WARN` with the correlation id, and SHALL NOT write a `case_candidate` row for it**
(`COMMON-DATA-012`).

`ANALYSIS-FR-033` **THE analysis module SHALL de-duplicate `analysis_run.unmapped_labels`, preserving
first-seen order.**

`ANALYSIS-FR-034` **IF every candidate returned for a case is unmapped, THEN THE analysis module SHALL
route the case to `UNDETERMINED` and SHALL record `analysis_run.error_code =
ERR_ALL_LABELS_UNMAPPED`** (`COMMON-DATA-013`).

`ANALYSIS-FR-035` **IF two raw labels from the same image resolve to the same `disease.id`, THEN THE
analysis module SHALL keep the higher confidence of the two.**

### 4.5 Multi-image aggregation and the `ConfidenceRouter`

`ANALYSIS-FR-040` **THE vision branch SHALL classify every image attached to the case, not only the
primary image.** *(Clarification item 2.)*

`ANALYSIS-FR-041` **THE analysis module SHALL aggregate mapped candidates across images per disease by
taking the maximum confidence, WHERE `foshol.analysis.multi-image.aggregation = MAX`.** *(Max is
explainable on stage and never lets two poor photographs dilute one good one.)*

`ANALYSIS-FR-042` **THE analysis module SHALL designate as primary the image with the highest
`case_image.quality_score`, breaking a tie by the lowest `position`.**

`ANALYSIS-FR-043` **THE analysis module SHALL sort aggregated candidates by descending confidence,
breaking ties by ascending `disease.code`, SHALL assign dense 1-based ranks, and SHALL retain at most
`foshol.analysis.candidate-limit` rows.**

`ANALYSIS-FR-044` **THE analysis module SHALL record `analysis_run.top1_confidence` from rank 1,
`analysis_run.top2_confidence` from rank 2 where it exists, and `analysis_run.margin` as
`top1 − top2`, leaving `margin` null when fewer than two candidates exist.**

`ANALYSIS-FR-045` **THE `ConfidenceRouter` SHALL apply `foshol.analysis.confidence.high` and
`foshol.analysis.confidence.low` to the raw softmax top-1 confidence and to nothing else.**
*(Clarification item 8.)*

`ANALYSIS-FR-046` **THE `ConfidenceRouter` SHALL NOT use `margin` as a routing input**, though margin
is persisted and displayed. *(`INV-A4`. Margin is diagnostic information for the officer, not a
decision rule; two rules would make the three demo paths unpredictable.)*

`ANALYSIS-FR-047` **THE analysis module SHALL rescale each image's candidate probability vector by
`foshol.analysis.confidence.temperature` before mapping, using
`p'ᵢ = pᵢ^(1/T) ⁄ Σⱼ pⱼ^(1/T)`, and WHERE `T = 1.0` the transform SHALL be a no-op that returns the
input values unchanged.** *(The property exists so calibration becomes a configuration change rather
than a code change.)*

`ANALYSIS-FR-048` `[DEFERRED]` **WHERE confidence calibration is included, THE analysis module SHALL
read a fitted temperature from `foshol.analysis.confidence.temperature` instead of `1.0`.** The seam
is the property itself; deferred because there is no calibration set (`00-common.ears.md` §1.2).

#### The three paths

`ANALYSIS-FR-050` **IF `top1 ≥ foshol.analysis.confidence.high` and the top-1 disease satisfies
`PrescribableSpec`, THEN THE `ConfidenceRouter` SHALL route the case to `PRIMARY`.**

`ANALYSIS-FR-051` **WHILE the path is `PRIMARY`, THE analysis module SHALL persist the aggregated
vision candidates with `case_candidate.source = 'MODEL'` and SHALL name the rank-1 disease as the
model diagnosis on `AnalysisCompleted`.** *(Remedy retrieval for the officer's prefilled advisory is
`review`'s work, driven from this disease id.)*

`ANALYSIS-FR-052` **IF `top1 ≥ foshol.analysis.confidence.high` and the top-1 disease is neither
`healthy` nor has any active remedy, THEN THE `ConfidenceRouter` SHALL route the case to
`UNDETERMINED` and SHALL record `analysis_run.error_code = ERR_NO_REMEDY_FOR_DIAGNOSIS`.**
`[DERIVED]` — *`PRIMARY` promises the officer a prefilled remedy. A confident diagnosis with an empty
`remedy` table would render an empty advisory, which is worse than sending the case to the officer
undecided. Healthy classes are exempt because they legitimately carry no remedy.*

`ANALYSIS-FR-053` **IF `foshol.analysis.confidence.low ≤ top1 < foshol.analysis.confidence.high` and
the knowledge-base result is conclusive, THEN THE `ConfidenceRouter` SHALL route the case to
`SECONDARY`.**

`ANALYSIS-FR-054` **IF `top1 < foshol.analysis.confidence.low`, THEN THE `ConfidenceRouter` SHALL
route the case to `UNDETERMINED`.**

`ANALYSIS-FR-055` **IF `SymptomMatchResult.inconclusive` is true while the case would otherwise be
`SECONDARY`, THEN THE `ConfidenceRouter` SHALL route the case to `UNDETERMINED`.**

`ANALYSIS-FR-056` **IF the speech branch produced no knowledge-base result at all — no audio on the
case, or the branch was abandoned or failed — THEN THE analysis module SHALL treat the knowledge-base
result as inconclusive.** *(`SECONDARY` exists to substitute knowledge for model confidence. With no
knowledge signal there is nothing to substitute, so a mid-confidence case correctly reaches the
officer with nothing prefilled.)*

`ANALYSIS-FR-057` **WHILE the path is `UNDETERMINED`, THE analysis module SHALL NOT nominate a
diagnosis and SHALL publish `AnalysisCompleted` with whatever candidates were produced, which may be
an empty list.**

`ANALYSIS-FR-058` **THE `ConfidenceRouter` SHALL be a pure function of `(top1, top2, prescribable,
kbInconclusive, thresholds)` with no I/O**, so it is unit-testable without Spring.

#### The `SECONDARY` merge and re-rank — the exact rule

`ANALYSIS-FR-060` **WHILE the path is `SECONDARY`, THE analysis module SHALL merge the aggregated
vision candidates with `SymptomMatchResult.diseases` by the formula**

```
merged(d) = ( vision(d) + kb(d) ) / 2

  vision(d) = aggregated model confidence for disease d, or 0 if d is absent from the vision set
  kb(d)     = ScoredDisease.score for disease d,          or 0 if d is absent from the KB set
  domain    = the union of both disease id sets
  rounding  = HALF_UP to scale 4
```

*Halving rather than normalising means a disease supported by only one source can never outrank an
equally strong disease supported by both. The rule is parameter-free by design: introducing a weight
property would require editing `00-common.ears.md` §9, which is frozen and owned by A1.*

`ANALYSIS-FR-061` **THE analysis module SHALL re-rank merged candidates by descending `merged(d)`,
breaking ties by descending `vision(d)`, then by descending `kb(d)`, then by ascending
`disease.code`.** *(Four levels make the ordering total and reproducible; ranking must never depend on
map iteration order.)*

`ANALYSIS-FR-062` **THE analysis module SHALL persist the merged, re-ranked shortlist with
`case_candidate.source = 'MERGED'`, truncated to `foshol.analysis.candidate-limit`.**

`ANALYSIS-FR-063` **WHILE the path is `SECONDARY`, THE analysis module SHALL additionally persist the
unmerged inputs — the vision set with `source = 'MODEL'` and the knowledge-base set with
`source = 'KB'` — so the merge is auditable from the database.**

`ANALYSIS-FR-064` **THE analysis module SHALL emit the `MERGED` shortlist as the candidate list on
`AnalysisCompleted` WHILE the path is `SECONDARY`, and the `MODEL` list otherwise.**

### 4.6 The speech branch

`ANALYSIS-FR-070` **WHEN a case carries audio, THE speech branch SHALL call `SpeechToTextPort`, then
`TextEmbeddingPort` on the transcript, then `SymptomMatchApi.match`, in that order.**

`ANALYSIS-FR-071` **IF a case carries no audio, THEN THE speech branch SHALL complete immediately with
an empty result and SHALL make no outbound call.**

`ANALYSIS-FR-072` **THE analysis module SHALL normalise the transcript to Unicode NFC before embedding
it** (`COMMON-NFR-013`).

`ANALYSIS-FR-073` **THE analysis module SHALL pass `cropId`, the normalised transcript, the 768-d
embedding and an empty officer-symptom list to `SymptomMatchRequest`, and SHALL perform no vector
search of its own.** *(Clarification item 10: retrieval belongs to `knowledge`.)*

`ANALYSIS-FR-074` **THE analysis module SHALL persist every `MatchedSymptom` as a `case_symptom` row
with `source = 'SPEECH'` and `matcher` copied from `MatchedSymptom.matcher`.**

`ANALYSIS-FR-075` **THE analysis module SHALL record the transcript, the ASR confidence and the
reported ASR and embedding model identifiers in `analysis_run.raw_output`, and SHALL NOT write
`case_audio.transcript_bn` or `case_audio.asr_confidence`.** `[DERIVED]` — *`case_audio` is owned by
`intake` (`00-common.ears.md` §4.4); a module never writes another module's table. `AnalysisView`
serves the transcript from `analysis_run.raw_output`, which is the authoritative copy.*

`ANALYSIS-FR-076` **IF the embedding vector returned is not 768-dimensional, THEN THE analysis module
SHALL abandon the speech branch with error code `ERR_EMBEDDING_DIMENSION` and SHALL NOT call
`SymptomMatchApi`** (`COMMON-NFR-006`).

### 4.7 Grad-CAM

`ANALYSIS-FR-080` **WHERE `foshol.analysis.gradcam.enabled` is true, THE analysis module SHALL call
`ExplainabilityPort` once per run, for the primary image only** (`ANALYSIS-FR-042`, clarification
item 13).

`ANALYSIS-FR-081` **THE analysis module SHALL request the explanation for the raw label the sidecar
reported as the primary image's top-1 result**, so that the overlay explains the class the model
actually chose.

`ANALYSIS-FR-082` **WHEN an overlay is returned, THE analysis module SHALL store it through
`ObjectStorePort` at object key `cases/{caseId}/gradcam/{imageId}.png` in bucket
`foshol.storage.bucket` with content type `image/png`, and SHALL record that key in
`analysis_run.gradcam_object_key`.** `[DERIVED]` — *the plan protects Grad-CAM as a wow factor but
gives it no storage; a persisted overlay still renders after the sidecar is killed on stage.*

`ANALYSIS-FR-083` **IF the explanation call fails, times out or the circuit is open, THEN THE analysis
module SHALL leave `analysis_run.gradcam_object_key` null, SHALL log a `WARN`, and SHALL continue the
run.** *(An explanation is evidence, not a diagnosis. Its absence never fails a case.)*

`ANALYSIS-FR-084` **WHILE `foshol.ai.mode=replay`, THE analysis module SHALL read the pre-baked
overlay from the fixture store and SHALL store it in MinIO by the same key convention**, so replay and
live produce the same observable artefact.

`ANALYSIS-FR-085` **IF the vision branch produced no mapped candidate, THEN THE analysis module SHALL
NOT call `ExplainabilityPort`.**

### 4.8 Officer symptoms

`ANALYSIS-FR-090` **WHEN `AnalysisApi.recordOfficerSymptoms(caseId, symptomIds)` is invoked, THE
analysis module SHALL insert one `case_symptom` row per symptom id with `source = 'OFFICER'`,
`matcher = 'MANUAL'` and `score = 1.000`.**

`ANALYSIS-FR-091` **IF a supplied symptom id is not present in `KnowledgeQueryApi.listSymptoms()`,
THEN THE analysis module SHALL reject the whole call with `ERR_UNKNOWN_SYMPTOM` and SHALL insert no
row.** *(All or nothing: a partially recorded observation is worse than a rejected one.)*

`ANALYSIS-FR-092` **IF a symptom id has already been recorded for the case with `source = 'OFFICER'`,
THEN THE analysis module SHALL leave the existing row unchanged and SHALL NOT fail the call.**
*(Idempotent under `uq_case_symptom`; the officer console may re-submit the same set.)*

`ANALYSIS-FR-093` **IF no `analysis_run` exists for the case, THEN THE analysis module SHALL reject
`recordOfficerSymptoms` with `ERR_ANALYSIS_NOT_FOUND`.**

`ANALYSIS-FR-094` **THE `review` module SHALL add officer symptoms exclusively through
`AnalysisApi.recordOfficerSymptoms` and SHALL NOT write `case_symptom` directly.**

> **Why the review module may not touch this table.** `case_symptom` is owned by `analysis`
> (`00-common.ears.md` §4.5), and a second writer causes three real failures. (1) `COMMON-ARCH-006`
> forbids a JPA association into another module's entity, so `review` would need a second mapping onto
> the same table — two entity definitions, two Hibernate caches, and `ddl-auto=validate` proving
> nothing. (2) `INV-S2` and `INV-S3` live in the analysis aggregate; a direct writer bypasses them and
> the `uq_case_symptom` violation surfaces as a raw constraint error on the officer's screen.
> (3) Single table ownership is what lets A3 and A5 work the same day without coordinating
> (`COMMON-NFR-040`). The published method costs `review` one line.

`ANALYSIS-FR-095` **THE analysis module SHALL NOT re-run the `ConfidenceRouter` when officer symptoms
are recorded, and SHALL NOT alter `analysis_run.decision_path`.** *(The case is already with a human;
the officer's judgement supersedes the router rather than feeding it.)*

### 4.9 Result assembly, events and degradation

`ANALYSIS-FR-100` **WHEN result assembly completes, THE analysis module SHALL publish
`AnalysisCompleted` carrying the decision path, the mode, `top1Confidence`, `margin`, the candidate
list per `ANALYSIS-FR-064`, the symptom list, `hasAudio`, `imageCount` and the correlation id.**

`ANALYSIS-FR-101` **IF the sidecar circuit is open, or a call fails after the configured retries, THEN
THE analysis module SHALL route the case to `UNDETERMINED`, SHALL record
`analysis_run.error_code = ERR_SIDECAR_UNAVAILABLE`, and SHALL still publish `AnalysisCompleted`**
(`COMMON-NFR-035`).

> **This is the live sidecar-kill demo.** On stage, `docker compose stop sidecar` is executed and a
> case is submitted. The circuit opens, both branches fail, the case is routed `UNDETERMINED` with an
> error code, `AnalysisCompleted` is published anyway, and the case appears at the top of the officer
> queue (least-confident-first, `REVIEW-FR-030`). Grad-CAM overlays already stored for earlier cases
> still render, because they are bytes in MinIO and not a live call. **Degradation, not failure.** An
> agent that "fixes" this by publishing `AnalysisFailed` or by throwing instead destroys the single
> most persuasive beat in the demo.

`ANALYSIS-FR-102` **THE analysis module SHALL publish `AnalysisFailed` only when no `analysis_run` row
could be written at all** — an absent case (`ANALYSIS-FR-003`) or a persistence failure. *(A degraded
inference is a completed analysis with an error code, not a failed analysis.)*

`ANALYSIS-FR-103` **THE analysis module SHALL persist `analysis_run`, `case_candidate` and
`case_symptom` in one transaction and SHALL publish `AnalysisCompleted` only after that transaction
commits.**

### 4.10 `[DEFERRED]` — the VLM visual-descriptor path

Clarification item 7. The seam stays visible; nothing behind it is built.

`ANALYSIS-FR-110` `[DEFERRED]` **THE analysis module SHALL derive visual symptom descriptors from the
primary image and SHALL persist them as `case_symptom` rows with `source = 'VISION'`.**

`ANALYSIS-FR-111` `[DEFERRED]` **THE analysis module SHALL feed `VISION`-sourced symptoms into
`SymptomMatchRequest` alongside the speech-sourced symptoms.**

**Status.** Deferred because it requires a GPU this project does not have; CPU inference for a
vision-language model does not fit inside `foshol.analysis.deadline`. It is not load-bearing for any
demo beat.

**The seam, precisely.** `case_symptom.source = 'VISION'` is defined in the frozen schema
(`00-common.ears.md` §4.5, `ck_case_symptom_source`) and is **never written** in this build. `INV-S1`
records that. When the path is picked up, it plugs into a new outbound port beside the four in §4.3
and writes through the same `case_symptom` repository. Nothing else changes.

`ANALYSIS-NFR-009` **THE analysis module SHALL write no `case_symptom` row with
`source = 'VISION'`**, so a test can assert the deferral rather than trusting it.

**Out of scope for this document and for every agent:** how such a model is obtained, hosted, prompted
or evaluated. `COMMON-CON-001` forbids training work of any kind.

### 4.11 Resilience, observability and security

`ANALYSIS-NFR-010` **THE analysis module SHALL wrap every outbound call made through
`VisionModelPort`, `SpeechToTextPort`, `TextEmbeddingPort` and `ExplainabilityPort` in the Resilience4j
time limiter, retry and circuit breaker registered under the instance name `sidecar`**
(`COMMON-NFR-034`, configured in `00-common.ears.md` §9.1).

`ANALYSIS-NFR-011` **THE analysis module SHALL apply the `sidecar` resilience instance in both adapter
families**, so that a fixture miss exercises the same degradation path as a dead sidecar.

`ANALYSIS-NFR-012` **THE analysis module SHALL record the `foshol.ai.call` timer tagged by endpoint and
outcome, and SHALL increment the `foshol.analysis.path` counter tagged `PRIMARY|SECONDARY|UNDETERMINED`
on every completed run** (`COMMON-NFR-033`).

`ANALYSIS-NFR-013` **THE analysis pipeline SHALL complete within the p95 budgets of
`00-common.ears.md` §10.2 — 1.5 s in replay and 12 s live — bounded by `foshol.analysis.deadline`.**

`ANALYSIS-SEC-001` **THE analysis module SHALL NOT log image bytes, audio bytes, overlay bytes, or the
transcript at `INFO` or above** (`COMMON-SEC-001`). *(A Bangla transcript is farmer speech; it belongs
in the database and on the officer's screen, not in a log aggregator.)*

`ANALYSIS-SEC-002` **THE analysis module SHALL NOT return a raw model label from any endpoint other
than `GET /api/v1/cases/{id}/analysis`, and SHALL NOT persist one on `case_candidate`**
(`COMMON-DATA-016`).

`ANALYSIS-SEC-003` **THE analysis module SHALL serve the Grad-CAM overlay only as a presigned URL with
a lifetime of `foshol.storage.presign-ttl`, issued after the authorisation check of
`ANALYSIS-SEC-004`** (`COMMON-SEC-016`).

`ANALYSIS-SEC-004` **IF the caller holds role `FARMER` and `CaseIntakeApi.isOwnedBy(caseId,
callerId)` is false, THEN THE analysis module SHALL return `404`, not `403`** (`COMMON-API-001` —
ownership must not be probeable).

`ANALYSIS-SEC-005` **THE analysis module SHALL permit a caller holding role `OFFICER` or `ADMIN` to
read the analysis and the overlay of any case** (`COMMON-SEC-011`).

---

## 5. API surface

Both endpoints are read-only. Analysis exposes no write endpoint; officer symptoms arrive through
`AnalysisApi`, in process, from `review`.

### 5.1 `GET /api/v1/cases/{caseId}/analysis`

`ANALYSIS-API-001` **THE analysis module SHALL expose `GET /api/v1/cases/{caseId}/analysis` returning
the full analysis detail for the case.**

**Response `200 application/json`** — a 1:1 mapping of `AnalysisView`:

```json
{
  "caseId": "018f…", "decisionPath": "SECONDARY", "mode": "REPLAY",
  "top1Confidence": 0.6231, "top2Confidence": 0.2044, "margin": 0.4187,
  "candidates": [
    { "diseaseId": "018f…", "diseaseCode": "…", "diseaseNameBn": "…",
      "confidence": 0.5115, "rank": 1, "source": "MERGED" }
  ],
  "symptoms": [
    { "symptomId": "018f…", "symptomCode": "…", "nameBn": "…",
      "score": 0.812, "source": "SPEECH", "matcher": "VECTOR" }
  ],
  "transcriptBn": "…", "asrConfidence": 0.910,
  "gradcamObjectKey": null,
  "unmappedLabels": ["…"],
  "visionModelId": "…", "visionModelVersion": "…",
  "latencyMs": 1180, "errorCode": null
}
```

| Status | Condition |
|---|---|
| `200` | Analysis exists and the caller may see the case |
| `401` | No or invalid JWT |
| `404` | No such case, no analysis run yet, or a farmer requesting another farmer's case (`ANALYSIS-SEC-004`) |

**Authorisation.** `FARMER` — own cases only. `OFFICER`, `ADMIN` — any case.

`ANALYSIS-API-002` **THE analysis detail response SHALL be the only endpoint response in the system
that carries raw model label strings, and it SHALL carry them only in `unmappedLabels`**
(`COMMON-DATA-016`).

`ANALYSIS-API-003` **THE analysis detail response SHALL NOT contain a MinIO object key for any image
or audio object**; `gradcamObjectKey` is present because the officer console needs to know whether an
overlay exists, and it is not itself fetchable (`ANALYSIS-SEC-003`).

### 5.2 `GET /api/v1/cases/{caseId}/gradcam`

`ANALYSIS-API-004` **THE analysis module SHALL expose `GET /api/v1/cases/{caseId}/gradcam` returning a
time-limited presigned URL for the stored overlay.**

**Response `200 application/json`**

```json
{ "caseId": "018f…", "imageId": "018f…",
  "url": "http://localhost:9000/foshol-cases/…?X-Amz-Expires=600&…",
  "expiresAt": "2026-09-07T11:04:00Z" }
```

| Status | Condition |
|---|---|
| `200` | An overlay exists for the case |
| `401` | No or invalid JWT |
| `404` | No such case, no overlay stored (`gradcam_object_key` is null), or a farmer requesting another farmer's case |
| `503` | MinIO unreachable while presigning |

**Authorisation.** Identical to §5.1.

`ANALYSIS-API-005` **THE analysis module SHALL return every error as an RFC 9457 problem document with
a `code` from `common.ErrorCodes` and the request's `correlationId`** (`COMMON-API-002`).

### 5.3 Error codes introduced by this module

Constants live in `common.ErrorCodes`, which is owned by agent A1; A3 supplies this list on Day 0 and
does not edit the file (`COMMON-NFR-040`).

| Code | Raised when |
|---|---|
| `ERR_SIDECAR_UNAVAILABLE` | Circuit open, or the call failed after retry (`ANALYSIS-FR-101`) |
| `ERR_VISION_BRANCH_TIMEOUT` | Vision branch abandoned at the deadline |
| `ERR_SPEECH_BRANCH_TIMEOUT` | Speech branch abandoned at the deadline |
| `ERR_ALL_LABELS_UNMAPPED` | Every candidate unmapped (`ANALYSIS-FR-034`) |
| `ERR_NO_REMEDY_FOR_DIAGNOSIS` | Confident, non-healthy diagnosis with no active remedy (`ANALYSIS-FR-052`) |
| `ERR_EMBEDDING_DIMENSION` | Embedding not 768-d (`ANALYSIS-FR-076`) |
| `ERR_FIXTURE_MISSING` | Replay fixture absent (`ANALYSIS-NFR-007`) |
| `ERR_UNKNOWN_SYMPTOM` | Unknown symptom id passed to `recordOfficerSymptoms` |
| `ERR_ANALYSIS_NOT_FOUND` | No analysis run for the case |

---

## 6. Persistence

### 6.1 Tables owned

`case_symptom`, `case_candidate`, `analysis_run` — defined in `00-common.ears.md` §4.5 and **not
redefined here**. Indexes `ix_case_symptom_case`, `ix_case_candidate_case`, `ix_analysis_run_case` are
defined in §4.9 of that document.

`ANALYSIS-DATA-001` **THE analysis module SHALL write to `case_symptom`, `case_candidate` and
`analysis_run` and to no other table.**

`ANALYSIS-DATA-002` **THE analysis module SHALL read `diagnosis_case`, `case_image`, `case_audio`,
`disease`, `symptom` and `model_label_map` only through `CaseIntakeApi`, `KnowledgeQueryApi` and
`SymptomMatchApi`, and SHALL declare no JPA association to an entity of another module**
(`COMMON-ARCH-006`).

`ANALYSIS-DATA-003` **WHEN a run is re-executed for a case, THE analysis module SHALL delete existing
`case_candidate` rows for that case with `source IN ('MODEL','KB','MERGED')` before inserting, and
SHALL delete `case_symptom` rows with `source = 'SPEECH'`, leaving `source = 'OFFICER'` rows
untouched.** *(Re-running must not lose the officer's own observations.)*

`ANALYSIS-DATA-004` **THE analysis module SHALL round every confidence to scale 4 and every symptom
score to scale 3, `HALF_UP`, before persisting**, matching `numeric(5,4)` and `numeric(4,3)`.

`ANALYSIS-DATA-005` **THE analysis module SHALL write `analysis_run.raw_output` in this shape:**

```json
{
  "schemaVersion": 1,
  "images": [
    { "imageId": "018f…", "sha256": "…", "primary": true,
      "modelId": "…", "modelVersion": "…", "latencyMs": 412,
      "candidates": [ { "rawLabel": "…", "confidence": 0.8123 } ] }
  ],
  "speech": { "audioId": "018f…", "modelId": "…", "transcriptBn": "…",
              "asrConfidence": 0.910, "embedModelId": "…", "latencyMs": 1840 },
  "branches": { "vision": "COMPLETED", "speech": "ABANDONED" }
}
```

`ANALYSIS-DATA-006` **THE analysis module SHALL write `analysis_run.unmapped_labels` as a JSON array of
strings, defaulting to `[]`** (`ck_analysis_unmapped`).

`ANALYSIS-DATA-007` **THE query side SHALL inject the `@ReadOnlyDataSource`-qualified `DataSource` and
SHALL NOT load an aggregate** (`COMMON-ARCH-007`).

### 6.2 Query patterns

| Query | Access path |
|---|---|
| Latest run for a case | `analysis_run WHERE case_id = ? ORDER BY created_at DESC LIMIT 1` → `ix_analysis_run_case` |
| Candidates for the detail view | `case_candidate WHERE case_id = ? AND source = ? ORDER BY rank` → `ix_case_candidate_case` |
| Symptoms for the detail view | `case_symptom WHERE case_id = ? ORDER BY source, score DESC` → `ix_case_symptom_case` |
| Officer-symptom idempotency | insert guarded by `uq_case_symptom (case_id, symptom_id, source)` |

> **Implementation note.** `rank` is a reserved word in PostgreSQL's window-function grammar. Quote it
> in native SQL (`"rank"`) and map the Java field with an explicit `@Column(name = "\"rank\"")`.

---

## 7. Acceptance criteria

One scenario per significant requirement, each mapping onto a single test method.

| # | Requirement | Given / When / Then |
|---|---|---|
| A1 | `FR-001` | **Given** a submitted case with two images **When** `CaseSubmitted` is delivered **Then** exactly one `analysis_run` row exists for the case |
| A2 | `FR-002` | **Given** a completed run **When** `CaseSubmitted` is redelivered **Then** the row count is unchanged and no second `AnalysisCompleted` is published |
| A3 | `FR-003` | **Given** `CaseIntakeApi.findById` returns empty **When** the listener runs **Then** `AnalysisFailed` with `ERR_CASE_NOT_FOUND` is published and no `analysis_run` row is written |
| A4 | `FR-020` | **Given** a case with images and audio **When** the pipeline runs **Then** both branches start before either completes and the executor is closed on exit |
| A5 | `FR-021` | **Given** the compiled module **When** ArchUnit scans it **Then** no class references `StructuredTaskScope` and no build script enables preview features |
| A6 | `FR-023` | **Given** `foshol.analysis.deadline=PT3S` and a speech port that sleeps 10 s **When** the pipeline runs **Then** the run completes, the speech branch is `ABANDONED`, and vision candidates are persisted |
| A7 | `FR-024` | **Given** the scenario of A6 **Then** `analysis_run.error_code = ERR_SPEECH_BRANCH_TIMEOUT` |
| A8 | `FR-025` | **Given** both ports sleep past the deadline **Then** `decision_path = UNDETERMINED` and `AnalysisCompleted` is published |
| A9 | `NFR-004` | **Given** `foshol.ai.mode=replay` **When** the context starts **Then** exactly one bean of each port type exists and it is the fixture adapter |
| A10 | `NFR-006` | **Given** a fixture at `fixtures/vision/{sha}.json` **When** the vision branch runs for that image **Then** the fixture's candidates are returned with no HTTP call |
| A11 | `NFR-007` | **Given** no fixture for the image SHA **Then** the run degrades to `UNDETERMINED` with `ERR_FIXTURE_MISSING` and no candidate is invented |
| A12 | `NFR-008` | **Given** the same case in replay **When** the pipeline runs twice **Then** candidate ids, confidences, ranks and decision path are identical |
| A13 | `FR-030`/`FR-031` | **Given** the sidecar reports `modelVersion = "v2"` while configuration names `v1` **Then** `resolveModelLabel` is called with `"v2"` |
| A14 | `FR-032` | **Given** one of three labels has no mapping **Then** two candidates are persisted, `unmapped_labels` holds the third, and a `WARN` is logged |
| A15 | `FR-034` | **Given** every label is unmapped **Then** `decision_path = UNDETERMINED` and `error_code = ERR_ALL_LABELS_UNMAPPED` |
| A16 | `FR-035` | **Given** two raw labels on one image map to the same disease with 0.30 and 0.70 **Then** one candidate is persisted at 0.70 |
| A17 | `FR-040`/`FR-041` | **Given** three images scoring 0.40, 0.80 and 0.55 for disease D **Then** the aggregated confidence for D is 0.80 |
| A18 | `FR-042` | **Given** images with `quality_score` 0.60, 0.90, 0.90 at positions 1, 2, 3 **Then** the primary image is position 2 |
| A19 | `FR-043` | **Given** seven mapped candidates and `candidate-limit=5` **Then** five rows are persisted with ranks 1–5 |
| A20 | `FR-044` | **Given** top1 0.8000 and top2 0.1000 **Then** `margin = 0.7000`; **Given** a single candidate **Then** `top2` and `margin` are null |
| A21 | `FR-046` | **Given** top1 0.80 and margin 0.01 **Then** the path is `PRIMARY` — margin does not demote it |
| A22 | `FR-047` | **Given** `temperature = 1.0` **Then** the rescaled vector equals the input exactly |
| A23 | `FR-050` | **Given** top1 = 0.76 and an active remedy exists **Then** the path is `PRIMARY` |
| A24 | `FR-052` | **Given** top1 = 0.90, the disease is not healthy and has no active remedy **Then** the path is `UNDETERMINED` with `ERR_NO_REMEDY_FOR_DIAGNOSIS` |
| A25 | `FR-053` | **Given** top1 = 0.60 and a conclusive KB result **Then** the path is `SECONDARY` |
| A26 | `FR-054` | **Given** top1 = 0.44 **Then** the path is `UNDETERMINED` |
| A27 | `FR-055` | **Given** top1 = 0.60 and `inconclusive = true` **Then** the path is `UNDETERMINED` |
| A28 | `FR-056` | **Given** top1 = 0.60 and the case has no audio **Then** the path is `UNDETERMINED` |
| A29 | `FR-060` | **Given** vision {D1: 0.60, D2: 0.20} and KB {D1: 0.40, D3: 0.80} **Then** merged = {D1: 0.5000, D3: 0.4000, D2: 0.1000} |
| A30 | `FR-061` | **Given** two diseases with equal merged score and unequal vision score **Then** the higher vision score ranks first |
| A31 | `FR-062`/`FR-063` | **Given** a `SECONDARY` run **Then** `case_candidate` holds rows with `source` `MODEL`, `KB` and `MERGED` |
| A32 | `FR-071` | **Given** a case with no audio **Then** `SpeechToTextPort` and `TextEmbeddingPort` are never invoked |
| A33 | `FR-074` | **Given** two matched symptoms **Then** two `case_symptom` rows exist with `source = SPEECH` and matcher copied from the match result |
| A34 | `FR-076` | **Given** a 512-d embedding **Then** `SymptomMatchApi` is not called and the branch fails with `ERR_EMBEDDING_DIMENSION` |
| A35 | `FR-082` | **Given** `gradcam.enabled=true` and an overlay returned **Then** the object exists at `cases/{caseId}/gradcam/{imageId}.png` and the key is recorded |
| A36 | `FR-080` | **Given** three images **Then** `ExplainabilityPort` is invoked exactly once, for the primary image |
| A37 | `FR-083` | **Given** the explain call throws **Then** the run completes, `gradcam_object_key` is null and the decision path is unaffected |
| A38 | `FR-090` | **Given** two symptom ids **When** `recordOfficerSymptoms` is called **Then** two rows exist with `source = OFFICER`, `matcher = MANUAL`, `score = 1.000` |
| A39 | `FR-091` | **Given** one valid and one unknown symptom id **Then** the call fails with `ERR_UNKNOWN_SYMPTOM` and no row is inserted |
| A40 | `FR-092` | **Given** the same ids submitted twice **Then** the row count is unchanged and no exception is raised |
| A41 | `FR-095` | **Given** a `PRIMARY` run **When** officer symptoms are recorded **Then** `analysis_run.decision_path` is still `PRIMARY` |
| A42 | `FR-101` | **Given** the sidecar circuit is open **Then** the path is `UNDETERMINED`, `error_code = ERR_SIDECAR_UNAVAILABLE`, and `AnalysisCompleted` is published |
| A43 | `FR-102` | **Given** the degraded run of A42 **Then** `AnalysisFailed` is **not** published |
| A44 | `NFR-009` | **Given** any completed run **Then** no `case_symptom` row has `source = VISION` |
| A45 | `SEC-004` | **Given** farmer B's JWT **When** `GET /api/v1/cases/{A}/analysis` is called **Then** the response is `404`, not `403` |
| A46 | `SEC-005` | **Given** an officer's JWT **When** the same case is requested **Then** the response is `200` |
| A47 | `API-004` | **Given** a stored overlay **Then** the response carries a presigned URL expiring within `foshol.storage.presign-ttl` |
| A48 | `API-004` | **Given** `gradcam_object_key` is null **Then** the response is `404` |
| A49 | `DATA-003` | **Given** a run with an officer symptom **When** the pipeline is re-executed **Then** the `OFFICER` row survives and the `SPEECH` rows are replaced |
| A50 | `SEC-002` | **Given** any run **Then** no `case_candidate` row and no `p_officer_queue` row contains a raw label string |

---

## 8. Test requirements

### 8.1 Unit tests — mandatory

| Under test | Must cover |
|---|---|
| `ConfidenceRouter` | All three paths, both threshold boundaries **at and either side of** 0.75 and 0.45, margin-does-not-route (A21), no-remedy demotion, KB-inconclusive, no-KB-signal, all-unmapped. Pure function, no Spring. |
| `CandidateAggregator` | MAX across images, within-image collision, tie-break by `disease.code`, `candidate-limit` truncation, rank density, scale-4 rounding. |
| `TemperatureScaler` | `T = 1.0` identity; `T ≠ 1.0` renormalises to a sum of 1. |
| `LabelResolver` | Reported-vs-configured model id (A13), unmapped append and de-duplication, WARN emission. |
| `MergeRanker` | The `FR-060` formula, the four-level tie-break, disjoint and overlapping input sets, empty KB set. |
| `AnalysisRun` aggregate | `INV-A1` … `INV-A9`. |
| `CaseCandidate` / `CaseSymptom` | `INV-C1` … `INV-C5`, `INV-S1` … `INV-S4`. |

**Every command and query handler** is unit tested with mocked ports and mocked published APIs:
`RunAnalysisCommandHandler`, `RecordOfficerSymptomsCommandHandler`, `AnalysisDetailQueryHandler`,
`GradcamLinkQueryHandler`.

Orchestration timing is unit tested with fake ports that sleep, against
`foshol.analysis.deadline=PT3S` from the `test` profile — no real sidecar, no container.

### 8.2 Integration test — exactly one

`ANALYSIS-NFR-014` **THE analysis module SHALL contain exactly one Testcontainers integration test**
(`00-common.ears.md` clarification 21 / §11), covering the primary happy path: a seeded case with two
images and one audio clip, `foshol.ai.mode=replay`, asserting the persisted `analysis_run`,
`case_candidate` and `case_symptom` rows, the stored Grad-CAM object, and the published
`AnalysisCompleted`.

Containers: `pgvector/pgvector:pg17` and MinIO. **No sidecar container** — replay mode makes it
unnecessary, and the live path is covered by the shared end-to-end slice test in
`.github/workflows/verify-live.yml` (`COMMON-NFR-024`), which A1 owns.

### 8.3 Fixtures required

| Fixture | Purpose |
|---|---|
| `fixtures/vision/{sha}.json` × 3 | One case per decision path: a high-confidence, a mid-confidence and a low-confidence response |
| `fixtures/vision/{sha}.json` (unmapped) | A response whose every raw label is absent from `model_label_map` |
| `fixtures/asr/{sha}.json` | One Bangla transcript with an ASR confidence — **transcript text is human-supplied** (`CONTENT-OWNERS.md`) |
| `fixtures/embed/{sha}.json` | A 768-d vector, and a deliberately 512-d vector for `FR-076` |
| `fixtures/gradcam/{sha}.png` | One pre-baked overlay for the primary image |
| Stub `KnowledgeQueryApi` | Mapping hits, one mapping miss, one disease with no active remedy, one healthy disease |
| Stub `SymptomMatchApi` | Conclusive result, inconclusive result, empty result |

`ANALYSIS-NFR-015` **THE analysis module SHALL NOT author Bangla transcript or symptom content in a
fixture** (`COMMON-CON-003`); placeholders carry `TODO(content-owner)` until supplied.

---

## 9. Agent execution notes

### 9.1 Implementation order

1. **`api/`** — `AnalysisApi` and the two event records, copied verbatim from `00-common.ears.md` §6.2
   and §6.3. **Frozen after Day 0** (`COMMON-NFR-041`).
2. **`domain/`** — `AnalysisRun`, `CaseCandidate`, `CaseSymptom`, `BranchOutcome`, `RoutingDecision`,
   the four specifications, and the domain exceptions. No Spring, no JPA, no Jackson
   (`COMMON-ARCH-004`). Unit-test the invariants here before writing anything else.
3. **`domain/` policy classes** — `TemperatureScaler`, `CandidateAggregator`, `LabelResolver`,
   `MergeRanker`, `ConfidenceRouter`. These are pure and carry the whole routing story; get them green
   before touching Spring.
4. **`application/port/`** — the five port interfaces and their records, exactly as §4.3 gives them.
5. **`application/command/`** — `RunAnalysisCommand` + handler (the orchestrator),
   `RecordOfficerSymptomsCommand` + handler.
6. **`application/query/`** — `AnalysisDetailQuery` + handler, `GradcamLinkQuery` + handler, read
   models. Read-only `DataSource`, no entities (`COMMON-ARCH-007`).
7. **`infrastructure/`** — JPA entities and repositories; `Fixture*Adapter` family **first** (replay is
   the Day-1 deliverable), then `Http*Adapter`; `MinioObjectStoreAdapter`;
   `AnalysisEventListener` (`@ApplicationModuleListener` on `CaseSubmitted`).
8. **`web/`** — the two controllers and their response records plus hand-written mappers
   (`COMMON-NFR-010`, `COMMON-ARCH-009`).
9. **Tests** — unit tests alongside each step; the single Testcontainers test last.

### 9.2 Day-0 hand-offs to agent A1

Raise these as blockers, do not fix them yourself (`COMMON-NFR-040`):

- The nine error-code constants of §5.3, to be added to `common/ErrorCodes.java`.
- Confirmation that `foshol.storage.*` is readable from the analysis module for `ObjectStorePort`.
- Both endpoints of §5 present in `docs/openapi/foshol-api.yaml`.

### 9.3 Local Definition of Done

In addition to `00-common.ears.md` §11, all of:

1. All three decision paths reproducible from committed fixtures in `replay` mode, by requirement id.
2. `docker compose stop sidecar` in `live` mode yields `UNDETERMINED` + `ERR_SIDECAR_UNAVAILABLE` +
   a published `AnalysisCompleted` + a case at the top of the officer queue.
3. No reference to `StructuredTaskScope` anywhere in the module (`ANALYSIS-FR-021`).
4. No `case_symptom` row with `source = 'VISION'` (`ANALYSIS-NFR-009`).
5. No raw model label outside `analysis_run` and the analysis detail response
   (`ANALYSIS-SEC-002`).
6. `ANALYSIS-FR-110` and `ANALYSIS-FR-111` recorded as `[DEFERRED]` in `docs/progress/a3.md`.

### 9.4 Requirement index

| Category | IDs | Count |
|---|---|---|
| `FR` | `001`–`007`, `020`–`026`, `030`–`035`, `040`–`048`, `050`–`058`, `060`–`064`, `070`–`076`, `080`–`085`, `090`–`095`, `100`–`103`, `110`–`111` | 68 |
| `NFR` | `001`–`015` | 15 |
| `SEC` | `001`–`005` | 5 |
| `DATA` | `001`–`007` | 7 |
| `API` | `001`–`005` | 5 |
| **Total** | | **100** |

Two of the 68 `FR` items are `[DEFERRED]` (`ANALYSIS-FR-110`, `ANALYSIS-FR-111`) and one is a
`[DEFERRED]` note on calibration (`ANALYSIS-FR-048`); all three stay in the document so the seams
remain visible.
