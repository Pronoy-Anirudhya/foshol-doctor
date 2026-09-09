# Plan: Plug `farmer-ai-service` into the analyzer

**Date:** 2026-09-08 (revised)  
**Status:** **implemented in codebase** (Python + analysis live/replay adapters + batch `runVision`). Review §11 as the applied design.  
**Related:** `docs/plans/farmer-ai-service-integration.md` (business walkthrough of the handler)  
**Python:** `farmer-ai-service` (disease detect + Whisper)  
**Java owner:** `analysis` module (`RunAnalysisCommandHandler` + live HTTP adapters)

> Sketches in **§11** match what was applied. Remaining ops work: seed `model_label_map` for HF labels; run with `foshol.ai.mode=live` against uvicorn.

---

## 0. Locked decisions

| Decision | Choice |
|----------|--------|
| Call site | **Analysis live adapters only** — never `CaseController` / intake |
| Image limit | Keep **`foshol.intake.max-images=3`** — Python also caps at **3** per request |
| Vision HTTP | **One batch call** per case (not one request per image in a loop) |
| Aggregation | Keep existing **`CandidateAggregator` MAX-by-disease** |
| HTTP client | Extend existing **`SidecarHttpClient` / Spring `RestClient`** pattern |
| Coding style | Production-grade; match existing analysis package structure (ports → adapters → handler) |

---

## 1. Target contracts

### 1.1 Disease detect (batch, max 3)

**Request:** `multipart/form-data`, up to 3 image parts.  
**Response (current sample shape):**

```json
{
  "results": [
    {
      "filename": "rice.jpg",
      "predictions": [
        { "label": "Rice___Leaf_Blast", "score": 0.9111 },
        { "label": "Wheat___Healthy", "score": 0.0583 }
      ]
    }
  ]
}
```

**Business rules (unchanged):**

1. Per-image prediction lists come back together.  
2. After label→disease mapping, **MAX confidence per disease across images** (no per-image disease segregation).  
3. Ranking / `candidate-limit` stay in the analyzer.  
4. Reject / 400 if more than 3 files (Java will not send more; intake already enforces 3).

### 1.2 Whisper / ASR

```json
{ "text": "…" }
```

Mapped in Java to `TranscriptResult.transcriptBn`. Embed + `SymptomMatchApi` stay after transcription.

### 1.3 Endpoints (working names; Python may rename — see §3)

| Method | Path | Role |
|--------|------|------|
| `GET` | `/health` | Liveness |
| `POST` | `/ai/detect-disease` | Batch vision |
| `POST` | `/ai/transcribe` | Single audio |

---

## 2. Comparison with `RunAnalysisCommandHandler` today

| Concern | Today | Target | Action |
|---------|-------|--------|--------|
| Vision I/O | Loop `vision.classify(one image)` | **Single batch** classify for all case images | Change port + `runVision` + live adapter |
| Same disease, many images | `CandidateAggregator` MAX | Same | **Keep** |
| Max images | Intake default **3** | Python max **3** | **No intake config change** |
| ASR | Per-audio port call | Still one call; response `{text}` | Change live ASR adapter only |
| Trigger | `CaseSubmitted` → handler | Same | **Keep** |
| Farmer HTTP | Intake `CaseController` | Still upload-only | **No AI there** |

Handler responsibilities that stay (orchestration only, not replaced by FastAPI):

1. Label resolve via knowledge  
2. Temperature scale + MAX aggregate + ConfidenceRouter  
3. SECONDARY merge with KB  
4. Persist run / candidates / symptoms  
5. Publish `AnalysisCompleted` / `AnalysisFailed`  
6. Optional Grad-CAM (soft-fail if Python has no explain API)

---

## 3. Suggested Python changes (make Java simpler & safer)

You said you will update the Python services. These changes are **recommended** so the Java side stays thin, typed, and production-grade. None invent agricultural content.

### 3.1 Strongly recommended

| Change | Why it helps Java |
|--------|-------------------|
| **Accept stable correlators, not only `filename`** | Filenames are lossy. Prefer each part keyed by **`image_id` (UUID string)** and/or **`sha256`**, echoed in each `results[]` item. Handler already has `imageId` + `sha256` from intake. |
| **Return `model_id` + `model_version` on every vision/ASR response** | `analysis_run` and label map need them; avoids hard-coding only in Java. |
| **Echo `correlation_id`** from request header `X-Correlation-Id` | Matches existing `SidecarHttpClient` / Foshol observability. |
| **Validate `1..3` images**; `400` with a clear JSON body if 0 or >3 | Fail fast; Java maps to `SidecarFailureException` / bad request. |
| **Order guarantee:** `results[i]` corresponds to upload order of files | Lets Java zip results to `CaseImageRef` list without filename matching. |
| **ASR: add `model_id` / `model_version`**; keep `text` (or alias `transcript_bn`) | Cleaner `TranscriptResult` mapping; confidence optional → default `0` in Java if absent. |
| **Pydantic response models** | Stable OpenAPI (`/openapi.json`) for adapter tests. |
| **Always delete temp files** (you already do on vision) | Same for ASR. |
| **Cap prediction list** (e.g. top_k=5) server-side | Matches analyzer candidate limit philosophy; smaller payloads. |

### 3.2 Nice-to-have (optional but production-friendly)

| Change | Why |
|--------|-----|
| Field aliases: accept/emit both `label`/`raw_label` and `score`/`confidence` | Java can prefer one pair; less brittle if sidecar docs evolve |
| Problem-style errors: `{ "code": "ERR_…", "detail": "…" }` | Align with Foshol error codes where practical |
| `GET /health` include `"mode": "LIVE"` and whether models are loaded | Spring health / ops |
| Reject non-image / non-audio MIME early (`415`) | Avoids opaque 500s |
| Request size limits matching `foshol.intake.max-image-bytes` | Consistency with intake |
| Keep `/ai/*` paths for now; later add `/v1/vision/classify` aliases if you want closer to `60-inference-sidecar` | Not required for first live integration |

### 3.3 Suggested response shapes (after Python update)

**Vision**

```json
{
  "model_id": "wambugu71/crop_leaf_diseases_vit",
  "model_version": "1",
  "correlation_id": "…",
  "results": [
    {
      "image_id": "018f…",
      "sha256": "abc…",
      "filename": "optional-debug.jpg",
      "predictions": [
        { "label": "Rice___Leaf_Blast", "score": 0.9111 }
      ]
    }
  ]
}
```

**ASR**

```json
{
  "model_id": "ashrafulparan/whisper-small-bangla",
  "model_version": "1",
  "correlation_id": "…",
  "text": "…"
}
```

**Multipart vision request (suggested):**

- Parts named `files` (list), **or** repeated `file` — pick one and document it.  
- Parallel form fields (same order): `image_ids` as repeated strings, **or** part headers / part filenames set to `imageId`.  
- Simplest for FastAPI + Spring: part name `files`, and each part’s **filename** set to `imageId.toString()` so Python can echo `image_id` from `UploadFile.filename` without extra form fields.

---

## 4. Java architecture (maintain existing structure)

### 4.1 Layers (do not invent a parallel stack)

```
application/port          VisionModelPort, SpeechToTextPort, …
application/command       RunAnalysisCommandHandler
infrastructure/adapter/http
                          SidecarHttpClient, Http*Adapter, SidecarHttpConfiguration
infrastructure/adapter/fixture
                          Fixture*Adapter (replay — keep)
infrastructure/sidecar    SidecarCallSupport (Resilience4j — keep wrapping live calls)
```

Records for DTOs/commands; constants from `com.rootcause.foshol.common`; hand-written mapping; no MapStruct.

### 4.2 Plug diagram (batch vision)

```
CaseController                 ← unchanged (no AI)
        │
CaseSubmitted
        │
RunAnalysisCommandHandler
        │
        ├─ runVision
        │     │
        │     ▼
        │  VisionModelPort.classifyBatch(VisionBatchRequest)   ← ADD / evolve port
        │     │
        │     ▼
        │  HttpVisionModelAdapter (@ConditionalOnProperty live)
        │     │  SidecarCallSupport.execute("vision", …)
        │     ▼
        │  SidecarHttpClient.postMultipart(...)
        │     │
        │     ▼
        │  POST {foshol.ai.base-url}/ai/detect-disease
        │     │
        │     ▼
        │  map results[] → per-image RawCandidate lists
        │     │
        │     ▼
        │  existing LabelResolver + CandidateAggregator + Grad-CAM hook
        │
        └─ runSpeech
              SpeechToTextPort.transcribe(...)
              HttpSpeechToTextAdapter → POST /ai/transcribe
              TextEmbeddingPort + SymptomMatchApi   ← unchanged
```

### 4.3 Port change (batch vision) — planned API

**Replace per-image-only usage in `runVision` with a batch method.** Keep a single-image method only if fixtures/tests still need it; otherwise one batch API is enough.

Planned shapes (illustrative — final names follow existing record style):

```text
VisionBatchRequest(
  caseId, cropCode, correlationId,
  images: List<VisionImageRef(imageId, objectKey, sha256)>   // size 1..3
)

VisionBatchResult(
  modelId, modelVersion, latencyMs,
  images: List<VisionImageResult(imageId, sha256, candidates: List<RawCandidate>)>
)
```

`runVision` then:

1. Build `VisionBatchRequest` from `summary.images()` (assert ≤3; empty list → empty vision bundle).  
2. **One** `classifyBatch` call.  
3. For each image result: temperature scale → `LabelResolver` → `maxWithinImage`.  
4. `aggregateAcrossImages` (MAX) — **unchanged**.  
5. Grad-CAM still uses primary image selection — **unchanged**.

**Fixture adapter:** implement `classifyBatch` by loading each image’s fixture by `sha256` (same files as today) and assembling a batch result — preserves `foshol.ai.mode=replay`.

### 4.4 ASR port

Keep `SpeechToTextPort.transcribe(TranscriptRequest)`. Only the **live adapter** mapping changes (`text` → `transcriptBn`, default confidence, model ids from response or config).

---

## 5. RestClient plan (production-grade)

Follow and extend what already exists — do **not** introduce WebClient, Feign, or a second HTTP stack.

### 5.1 Existing pieces to reuse

| Piece | Role |
|-------|------|
| `SidecarHttpConfiguration` | `@Bean SidecarHttpClient` when `foshol.ai.mode=live` |
| `SidecarHttpClient` | Builds `RestClient` with `baseUrl(settings.aiBaseUrl())`, read timeout `settings.aiTimeout()`, `JdkClientHttpRequestFactory` |
| `SidecarCallSupport` | Resilience4j time limiter / circuit around `"vision"` / `"asr"` |
| `AnalysisSettings` | `aiBaseUrl`, `aiTimeout`, `aiMode` |
| `CorrelationId.HEADER` | Outbound header on every call |
| `ObjectStorePort` | Read image/audio bytes from MinIO before upload |
| `SidecarFailureException` + `ErrorCodes.ERR_SIDECAR_UNAVAILABLE` | Uniform failure → handler branch FAILED / UNDETERMINED |

### 5.2 Planned `SidecarHttpClient` extensions

Keep `postJson` for any remaining JSON callers (embed/explain if still used). **Add** multipart helpers, e.g.:

| Method | Purpose |
|--------|---------|
| `byte[] readBytes(objectKey)` | Raw bytes (multipart needs bytes, not only base64) |
| `JsonNode postMultipart(path, MultiValueMap or builder, correlationId)` | Vision batch + ASR |
| Optional typed `postMultipart(path, …, Class<T>)` | If you prefer records over `JsonNode` — still hand-mapped |

**RestClient usage pattern (planned):**

1. One shared `RestClient` instance per live mode (already in `SidecarHttpClient`).  
2. `.post().uri(path).header(CorrelationId.HEADER, id).contentType(MULTIPART_FORM_DATA).body(multipart).retrieve()`.  
3. On `RestClientException` / non-2xx: wrap `SidecarFailureException` (same as today).  
4. Parse body with existing `ObjectMapper` into package-local **records** under `adapter.http` (or `adapter.http.dto`) — hand mapping into port `VisionBatchResult` / `TranscriptResult`.  
5. **No** business logic in the client — only HTTP + parse. Mapping of `label`/`score` → `RawCandidate` lives in the adapter.

**Multipart construction (vision):**

- Use Spring `MultipartBodyBuilder`.  
- For each image: read bytes via `ObjectStorePort`, add part `files` (or agreed name), set filename to `imageId` (or sha256) per §3.  
- Content type from sniffing or `image/jpeg` default consistent with intake.

**Multipart construction (ASR):**

- Single part `file` with audio bytes from object key.

### 5.3 Timeouts & resilience

| Concern | Plan |
|---------|------|
| Read timeout | Existing `foshol.ai.timeout` (default `PT8S`) on `JdkClientHttpRequestFactory` |
| Connect timeout | Set explicitly on the request factory if not already (production hygiene) |
| Bulk vision | One HTTP call still must finish inside analysis branch deadline (`settings.deadline()`); if GPU is slow, raise timeout via config — do not remove Resilience4j |
| Retries | Do **not** blindly retry non-idempotent GPU work unless product asks; prefer fail → UNDETERMINED |
| Circuit breaker | Keep `SidecarCallSupport` instance name `"sidecar"` |

### 5.4 Configuration

Reuse:

```properties
foshol.ai.mode=live
foshol.ai.base-url=http://localhost:8000
foshol.ai.timeout=PT8S
```

Optional additions (only if needed, via `ConfigKeys` + `AnalysisSettings`):

- `foshol.ai.vision.path=/ai/detect-disease`  
- `foshol.ai.asr.path=/ai/transcribe`  
- `foshol.ai.vision.model-id=…` / `foshol.ai.asr.model-id=…` as fallbacks when Python omits them  

Do **not** change `foshol.intake.max-images`.

### 5.5 What not to do with RestClient

- No RestClient beans inside `domain` or `CaseController`  
- No fire-and-forget async HTTP outside the handler’s existing virtual-thread branches  
- No leaking FastAPI DTOs into domain  
- No logging full Bangla transcripts or image bytes at INFO  

---

## 6. Keep / change / add / remove

### 6.1 KEEP

- `RunAnalysisCommandHandler` orchestration (except vision loop → batch)  
- `CandidateAggregator`, `LabelResolver`, `ConfidenceRouter`, `MergeRanker`  
- Embed + symptom match after ASR  
- Fixture adapters + replay mode  
- `AnalysisEventListener`  
- Intake upload path and max-images=3  
- Resilience4j wrapping  

### 6.2 CHANGE

| Piece | Change |
|-------|--------|
| `VisionModelPort` (+ request/result records) | Batch classify API |
| `RunAnalysisCommandHandler.runVision` | One batch call; then existing map/aggregate |
| `HttpVisionModelAdapter` | Multipart batch → `/ai/detect-disease`; map `label`/`score` |
| `HttpSpeechToTextAdapter` | Multipart → `/ai/transcribe`; map `text` |
| `FixtureVisionModelAdapter` | Implement batch via per-sha fixtures |
| `SidecarHttpClient` | Multipart + byte read |
| Tests for handler / HTTP adapters | Cover batch zip-by-order / image_id |

### 6.3 ADD

| Piece | Purpose |
|-------|---------|
| `VisionBatchRequest` / `VisionBatchResult` (or equivalent records) | Port contract |
| HTTP response records under `adapter.http` | Jackson binding |
| Optional path/model-id config keys | Operability |
| Knowledge `model_label_map` entries for HF labels | Required for PRIMARY/SECONDARY; content-owned |
| Adapter unit tests with MockRestServiceServer or WireMock | Contract tests against sample JSON |

### 6.4 REMOVE / DO NOT ADD

| Item | Action |
|------|--------|
| Per-image HTTP loop in live mode | **Remove** once batch port is live |
| AI calls from `CaseController` | **Never** |
| Raising intake max images to 5 | **Not doing** |
| Custom re-aggregation replacing MAX | **Unnecessary** |
| New WebClient / Feign / OpenFeign stack | **No** |
| Agricultural copy / dosages in Java or Python | **Forbidden** |

### 6.5 Soft-fail / defer

| Port | Plan |
|------|------|
| `TextEmbeddingPort` | Keep fixture or separate live embed; Whisper alone is insufficient for KB match |
| `ExplainabilityPort` | Disable via `gradcamEnabled=false` or soft-fail until Python supports it |

---

## 7. Implementation sequence (when coding starts)

1. **Python:** lock multipart field names, max-3 validation, ordered `results` with `image_id`/`sha256`, `model_id`/`model_version`, correlation echo.  
2. **Java ports:** introduce batch vision types; update fixture adapter.  
3. **`SidecarHttpClient`:** multipart + connect timeout; keep JSON method.  
4. **`HttpVisionModelAdapter` / `HttpSpeechToTextAdapter`:** RestClient multipart, DTO map, Resilience4j.  
5. **`RunAnalysisCommandHandler.runVision`:** single batch call; preserve label resolve + MAX aggregate + Grad-CAM selection.  
6. **Label map** rows for your model labels (blocker if missing).  
7. **Tests:** adapter contract tests + handler test with stubbed batch port.  
8. **Local verify:** `foshol.ai.mode=live`, uvicorn on `:8000`, submit ≤3-image case; confirm one disease row when scores share a mapped label.

---

## 8. Acceptance checks

- [ ] Live vision uses **exactly one** HTTP POST for N images (N∈1..3)  
- [ ] Intake max images remains **3**; Python rejects >3  
- [ ] Two images → same mapped disease → **one** MODEL candidate at **max** score  
- [ ] ASR `{text}` stored as transcript; embed/KB path still runs when audio present  
- [ ] No FastAPI calls from intake / `CaseController`  
- [ ] Unmapped labels → `unmapped_labels`, not fake diseases  
- [ ] Sidecar down → existing FAILED / UNDETERMINED behaviour  
- [ ] `foshol.ai.mode=replay` still green without Python  
- [ ] Correlation header sent on every RestClient call  

---

## 9. Decision log (resolved)

| Decision | Choice |
|----------|--------|
| Batch vs per-image HTTP | **Batch** (one call per case) |
| Intake max images | **Keep 3** |
| CaseController AI | **No** |
| HTTP stack | **Existing RestClient via `SidecarHttpClient`** |
| Python contract polish | **Yes** — image_id/order, model ids, correlation, max-3 (§3) |
| Persist winning image ids on candidates | **No** for v1 (audit `raw_output` enough) |
| Embed / Grad-CAM | Fixture / soft-fail until available |

---

## 10. Bottom line

Integrate by evolving **`VisionModelPort` to batch**, teaching **`SidecarHttpClient` + live adapters** to call FastAPI with **multipart RestClient**, and leaving triage logic in **`RunAnalysisCommandHandler`**. Python should echo **stable image ids, model metadata, and correlation** so Java stays a thin, production-shaped adapter. Do not touch intake max-images or `CaseController`.

---

## 11. Proposed code (review only — not applied yet)

Conventions matched to the repo: records, imports not FQNs, `SidecarCallSupport`, `@ConditionalOnProperty` on live/replay, hand-written mapping, British prose in comments only where needed.

### 11.1 Python — `farmer-ai-service/api/main.py` (target)

```python
from fastapi import FastAPI, UploadFile, File, Header, HTTPException
from models.whisper_service import WhisperService
from models.disease_service import DiseaseService
import tempfile
import shutil
import os
from typing import Annotated

MAX_IMAGES = 3
VISION_MODEL_ID = "wambugu71/crop_leaf_diseases_vit"
VISION_MODEL_VERSION = "1"
ASR_MODEL_ID = "ashrafulparan/whisper-small-bangla"
ASR_MODEL_VERSION = "1"

app = FastAPI(title="Farmer AI Service", version="1.0.0")

whisper_service = WhisperService()
disease_service = DiseaseService()


@app.get("/health")
def health():
    return {"status": "UP", "mode": "LIVE"}


@app.post("/ai/detect-disease")
async def detect_disease(
    files: list[UploadFile] = File(...),
    x_correlation_id: Annotated[str | None, Header(alias="X-Correlation-Id")] = None,
):
    if not files or len(files) > MAX_IMAGES:
        raise HTTPException(
            status_code=400,
            detail={"code": "ERR_SIDECAR_BAD_REQUEST", "message": f"image count must be 1..{MAX_IMAGES}"},
        )

    temp_paths: list[str] = []
    try:
        for upload in files:
            with tempfile.NamedTemporaryFile(delete=False, suffix=".jpg") as temp:
                shutil.copyfileobj(upload.file, temp)
                temp_paths.append(temp.name)

        predictions = disease_service.predict(temp_paths)

        results = []
        for upload, prediction in zip(files, predictions):
            # Java sets part filename = imageId (UUID). Optional sha256 can be encoded later.
            results.append(
                {
                    "image_id": upload.filename,
                    "filename": upload.filename,
                    "predictions": [
                        {"label": p["label"], "score": float(p["score"])}
                        if isinstance(p, dict)
                        else {"label": p.label, "score": float(p.score)}
                        for p in prediction
                    ],
                }
            )

        return {
            "model_id": VISION_MODEL_ID,
            "model_version": VISION_MODEL_VERSION,
            "correlation_id": x_correlation_id,
            "results": results,
        }
    finally:
        for path in temp_paths:
            try:
                os.remove(path)
            except OSError:
                pass


@app.post("/ai/transcribe")
async def transcribe(
    file: UploadFile = File(...),
    x_correlation_id: Annotated[str | None, Header(alias="X-Correlation-Id")] = None,
):
    temp_path = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as temp:
            shutil.copyfileobj(file.file, temp)
            temp_path = temp.name
        text = whisper_service.transcribe(temp_path)
        return {
            "model_id": ASR_MODEL_ID,
            "model_version": ASR_MODEL_VERSION,
            "correlation_id": x_correlation_id,
            "text": text,
        }
    finally:
        if temp_path:
            try:
                os.remove(temp_path)
            except OSError:
                pass
```

Adjust the `predictions` list comprehension to match whatever `disease_service.predict` actually returns (dicts vs objects).

---

### 11.2 Java — port records (new)

**`VisionImageRef.java`**

```java
package com.rootcause.foshol.analysis.application.port;

import java.util.UUID;

public record VisionImageRef(UUID imageId, String objectKey, String sha256) {}
```

**`VisionBatchRequest.java`**

```java
package com.rootcause.foshol.analysis.application.port;

import java.util.List;
import java.util.UUID;

public record VisionBatchRequest(
        UUID caseId, String cropCode, String correlationId, List<VisionImageRef> images) {}
```

**`VisionImageResult.java`**

```java
package com.rootcause.foshol.analysis.application.port;

import java.util.List;
import java.util.UUID;

public record VisionImageResult(UUID imageId, String sha256, List<RawCandidate> candidates) {}
```

**`VisionBatchResult.java`**

```java
package com.rootcause.foshol.analysis.application.port;

import java.util.List;

public record VisionBatchResult(
        String modelId, String modelVersion, List<VisionImageResult> images, int latencyMs) {}
```

**`VisionModelPort.java` (evolved)**

```java
package com.rootcause.foshol.analysis.application.port;

public interface VisionModelPort {

    /** Single-image classify retained for fixture compatibility / Grad-CAM callers if needed. */
    VisionResult classify(VisionRequest request);

    /** Preferred live path: one sidecar round-trip for all case images (1..3). */
    VisionBatchResult classifyBatch(VisionBatchRequest request);
}
```

Default on the interface is avoided (Java style in this repo); both adapters implement both methods. Live `classify` may delegate to `classifyBatch` of size 1 if anything still calls it.

---

### 11.3 Java — HTTP DTOs (adapter package only)

**`adapter/http/dto/DetectDiseaseResponse.java`**

```java
package com.rootcause.foshol.analysis.infrastructure.adapter.http.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DetectDiseaseResponse(
        String model_id,
        String model_version,
        String correlation_id,
        List<ImageResultDto> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageResultDto(String image_id, String sha256, String filename, List<PredictionDto> predictions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PredictionDto(String label, double score) {}
}
```

**`adapter/http/dto/TranscribeResponse.java`**

```java
package com.rootcause.foshol.analysis.infrastructure.adapter.http.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TranscribeResponse(
        String model_id, String model_version, String correlation_id, String text) {}
```

(Jackson may need `@JsonProperty("model_id")` if you prefer camelCase record components — either style is fine; keep one consistently.)

---

### 11.4 Java — `SidecarHttpClient` extensions

Keep existing `postJson` / `readBase64`. Add:

```java
public byte[] readBytes(String objectKey) {
    return objectStore.read(objectKey);
}

public JsonNode postMultipart(String path, MultiValueMap<String, HttpEntity<?>> multipart, String correlationId) {
    try {
        String response = restClient.post()
                .uri(path)
                .header(CorrelationId.HEADER, correlationId)
                .body(multipart)
                .retrieve()
                .body(String.class);
        return mapper.readTree(response);
    } catch (RestClientException | java.io.IOException ex) {
        throw new SidecarFailureException(ErrorCodes.ERR_SIDECAR_UNAVAILABLE, "Sidecar HTTP failed", ex);
    }
}

public <T> T postMultipart(
        String path, MultiValueMap<String, HttpEntity<?>> multipart, String correlationId, Class<T> type) {
    try {
        String response = restClient.post()
                .uri(path)
                .header(CorrelationId.HEADER, correlationId)
                .body(multipart)
                .retrieve()
                .body(String.class);
        return mapper.readValue(response, type);
    } catch (RestClientException | java.io.IOException ex) {
        throw new SidecarFailureException(ErrorCodes.ERR_SIDECAR_UNAVAILABLE, "Sidecar HTTP failed", ex);
    }
}
```

Constructor tweak — set connect timeout as well as read timeout:

```java
JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
factory.setConnectTimeout(Duration.ofSeconds(2));
factory.setReadTimeout(settings.aiTimeout());
this.restClient = RestClient.builder()
        .baseUrl(settings.aiBaseUrl())
        .requestFactory(factory)
        .build();
```

Imports to add: `Duration`, `HttpEntity`, `MultiValueMap`, and for building parts in adapters: `MultipartBodyBuilder`, `MediaType`.

---

### 11.5 Java — `HttpVisionModelAdapter` (live, batch)

```java
package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import com.rootcause.foshol.analysis.application.port.RawCandidate;
import com.rootcause.foshol.analysis.application.port.VisionBatchRequest;
import com.rootcause.foshol.analysis.application.port.VisionBatchResult;
import com.rootcause.foshol.analysis.application.port.VisionImageRef;
import com.rootcause.foshol.analysis.application.port.VisionImageResult;
import com.rootcause.foshol.analysis.application.port.VisionModelPort;
import com.rootcause.foshol.analysis.application.port.VisionRequest;
import com.rootcause.foshol.analysis.application.port.VisionResult;
import com.rootcause.foshol.analysis.infrastructure.adapter.http.dto.DetectDiseaseResponse;
import com.rootcause.foshol.analysis.infrastructure.adapter.http.dto.DetectDiseaseResponse.ImageResultDto;
import com.rootcause.foshol.analysis.infrastructure.adapter.http.dto.DetectDiseaseResponse.PredictionDto;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import com.rootcause.foshol.common.ConfigKeys;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

@Component
@ConditionalOnProperty(name = ConfigKeys.AI_MODE, havingValue = "live")
public class HttpVisionModelAdapter implements VisionModelPort {

    private static final String PATH = "/ai/detect-disease";
    private static final int MAX_IMAGES = 3;

    private final SidecarHttpClient http;
    private final SidecarCallSupport sidecar;

    public HttpVisionModelAdapter(SidecarHttpClient http, SidecarCallSupport sidecar) {
        this.http = http;
        this.sidecar = sidecar;
    }

    @Override
    public VisionResult classify(VisionRequest request) {
        VisionBatchResult batch = classifyBatch(new VisionBatchRequest(
                request.caseId(),
                request.cropCode(),
                request.correlationId(),
                List.of(new VisionImageRef(request.imageId(), request.objectKey(), request.sha256()))));
        VisionImageResult only = batch.images().getFirst();
        return new VisionResult(batch.modelId(), batch.modelVersion(), only.candidates(), batch.latencyMs());
    }

    @Override
    public VisionBatchResult classifyBatch(VisionBatchRequest request) {
        return sidecar.execute("vision", () -> doClassifyBatch(request));
    }

    private VisionBatchResult doClassifyBatch(VisionBatchRequest request) {
        List<VisionImageRef> images = request.images() == null ? List.of() : request.images();
        if (images.isEmpty() || images.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("vision batch size must be 1.." + MAX_IMAGES);
        }

        long started = System.nanoTime();
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        Map<String, VisionImageRef> byId = new LinkedHashMap<>();
        for (VisionImageRef image : images) {
            byte[] bytes = http.readBytes(image.objectKey());
            String partName = image.imageId().toString();
            byId.put(partName, image);
            builder.part("files", new ByteArrayResource(bytes) {
                        @Override
                        public String getFilename() {
                            return partName;
                        }
                    })
                    .contentType(MediaType.IMAGE_JPEG);
        }

        MultiValueMap<String, HttpEntity<?>> multipart = builder.build();
        DetectDiseaseResponse body =
                http.postMultipart(PATH, multipart, request.correlationId(), DetectDiseaseResponse.class);

        List<VisionImageResult> mapped = new ArrayList<>();
        if (body.results() != null) {
            for (ImageResultDto row : body.results()) {
                VisionImageRef ref = byId.get(row.image_id());
                if (ref == null && mapped.size() < images.size()) {
                    ref = images.get(mapped.size()); // order fallback
                }
                if (ref == null) {
                    continue;
                }
                mapped.add(new VisionImageResult(ref.imageId(), ref.sha256(), toCandidates(row.predictions())));
            }
        }

        int latencyMs = (int) ((System.nanoTime() - started) / 1_000_000L);
        String modelId = body.model_id() == null ? "" : body.model_id();
        String modelVersion = body.model_version() == null ? "" : body.model_version();
        return new VisionBatchResult(modelId, modelVersion, List.copyOf(mapped), latencyMs);
    }

    private static List<RawCandidate> toCandidates(List<PredictionDto> predictions) {
        if (predictions == null) {
            return List.of();
        }
        List<RawCandidate> out = new ArrayList<>(predictions.size());
        for (PredictionDto p : predictions) {
            out.add(new RawCandidate(p.label(), BigDecimal.valueOf(p.score())));
        }
        return List.copyOf(out);
    }
}
```

---

### 11.6 Java — `HttpSpeechToTextAdapter` (live)

```java
package com.rootcause.foshol.analysis.infrastructure.adapter.http;

import com.rootcause.foshol.analysis.application.port.SpeechToTextPort;
import com.rootcause.foshol.analysis.application.port.TranscriptRequest;
import com.rootcause.foshol.analysis.application.port.TranscriptResult;
import com.rootcause.foshol.analysis.infrastructure.adapter.http.dto.TranscribeResponse;
import com.rootcause.foshol.analysis.infrastructure.sidecar.SidecarCallSupport;
import com.rootcause.foshol.common.ConfigKeys;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

@Component
@ConditionalOnProperty(name = ConfigKeys.AI_MODE, havingValue = "live")
public class HttpSpeechToTextAdapter implements SpeechToTextPort {

    private static final String PATH = "/ai/transcribe";

    private final SidecarHttpClient http;
    private final SidecarCallSupport sidecar;

    public HttpSpeechToTextAdapter(SidecarHttpClient http, SidecarCallSupport sidecar) {
        this.http = http;
        this.sidecar = sidecar;
    }

    @Override
    public TranscriptResult transcribe(TranscriptRequest request) {
        return sidecar.execute("asr", () -> {
            long started = System.nanoTime();
            byte[] bytes = http.readBytes(request.objectKey());
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(bytes) {
                        @Override
                        public String getFilename() {
                            return request.audioId().toString() + ".wav";
                        }
                    })
                    .contentType(MediaType.parseMediaType("audio/wav"));

            MultiValueMap<String, HttpEntity<?>> multipart = builder.build();
            TranscribeResponse body =
                    http.postMultipart(PATH, multipart, request.correlationId(), TranscribeResponse.class);

            int latencyMs = (int) ((System.nanoTime() - started) / 1_000_000L);
            String modelId = body.model_id() == null ? "" : body.model_id();
            String text = body.text() == null ? "" : body.text();
            return new TranscriptResult(modelId, text, BigDecimal.ZERO, latencyMs);
        });
    }
}
```

---

### 11.7 Java — `FixtureVisionModelAdapter` (replay, batch)

```java
@Override
public VisionResult classify(VisionRequest request) {
    // existing fixture-by-sha256 body unchanged
}

@Override
public VisionBatchResult classifyBatch(VisionBatchRequest request) {
    return sidecar.execute("vision", () -> {
        long started = System.nanoTime();
        String modelId = null;
        String modelVersion = null;
        List<VisionImageResult> images = new ArrayList<>();
        for (VisionImageRef image : request.images()) {
            VisionResult one = classify(new VisionRequest(
                    request.caseId(),
                    image.imageId(),
                    request.cropCode(),
                    image.objectKey(),
                    image.sha256(),
                    request.correlationId()));
            modelId = one.modelId();
            modelVersion = one.modelVersion();
            images.add(new VisionImageResult(image.imageId(), image.sha256(), one.candidates()));
        }
        int latencyMs = (int) ((System.nanoTime() - started) / 1_000_000L);
        return new VisionBatchResult(modelId, modelVersion, List.copyOf(images), latencyMs);
    });
}
```

Note: calling `classify` inside `classifyBatch` nests `sidecar.execute` — better to extract a private `loadFixture(sha256)` used by both to avoid double decoration. Prefer that when implementing.

---

### 11.8 Java — `RunAnalysisCommandHandler.runVision` (batch)

Replace the per-image `vision.classify(...)` loop with one batch call, then keep label resolve / aggregate / Grad-CAM:

```java
private VisionBundle runVision(RunAnalysisCommand command, CaseSummary summary) {
    List<CaseImageRef> images = summary.images() == null ? List.of() : summary.images();
    if (images.isEmpty()) {
        return new VisionBundle(
                BranchOutcome.COMPLETED, null, null, null, List.of(), List.of(), List.of(), null, null);
    }

    List<List<MappedCandidate>> perImage = new ArrayList<>();
    List<ImageRaw> raws = new ArrayList<>();
    Set<String> unmapped = new LinkedHashSet<>();
    String modelId = null;
    String modelVersion = null;
    String primaryRawLabel = null;
    CaseImageRef primary = primaryImage(images);

    try {
        List<VisionImageRef> refs = new ArrayList<>(images.size());
        for (CaseImageRef image : images) {
            refs.add(new VisionImageRef(image.imageId(), image.objectKey(), image.sha256()));
        }

        VisionBatchResult batch = vision.classifyBatch(new VisionBatchRequest(
                command.caseId(), summary.cropCode(), command.correlationId(), List.copyOf(refs)));

        modelId = batch.modelId();
        modelVersion = batch.modelVersion();

        Map<UUID, CaseImageRef> caseById = new LinkedHashMap<>();
        for (CaseImageRef image : images) {
            caseById.put(image.imageId(), image);
        }

        for (VisionImageResult imageResult : batch.images()) {
            CaseImageRef image = caseById.get(imageResult.imageId());
            if (image == null) {
                continue;
            }
            List<RawCandidate> scaled =
                    TemperatureScaler.rescale(toDomain(imageResult.candidates()), settings.temperature());

            if (primary != null && image.imageId().equals(primary.imageId()) && !scaled.isEmpty()) {
                primaryRawLabel = scaled.get(0).rawLabel();
                BigDecimal best = scaled.get(0).confidence();
                for (RawCandidate c : scaled) {
                    if (c.confidence().compareTo(best) > 0) {
                        best = c.confidence();
                        primaryRawLabel = c.rawLabel();
                    }
                }
            }

            LabelResolver.Resolution resolution = labelResolver.resolve(
                    scaled,
                    batch.modelId(),
                    batch.modelVersion(),
                    knowledge::resolveModelLabel,
                    id -> knowledge.findDiseaseById(id).map(DiseaseView::code).orElse(id.toString()),
                    command.correlationId());
            unmapped.addAll(resolution.unmappedLabels());
            perImage.add(aggregator.maxWithinImage(resolution.mapped()));

            // ImageRaw still needs a VisionResult-shaped audit row:
            VisionResult audit = new VisionResult(
                    batch.modelId(), batch.modelVersion(), imageResult.candidates(), batch.latencyMs());
            raws.add(new ImageRaw(image, audit, scaled));
        }

        List<MappedCandidate> aggregated =
                aggregator.aggregateAcrossImages(perImage, settings.aggregation(), settings.candidateLimit());
        String gradcamKey = storeGradcam(command, summary, primary, primaryRawLabel, aggregated);
        return new VisionBundle(
                BranchOutcome.COMPLETED,
                null,
                modelId,
                modelVersion,
                aggregated,
                List.copyOf(unmapped),
                raws,
                primary,
                gradcamKey);
    } catch (SidecarFailureException ex) {
        return VisionBundle.failed(ex.errorCode());
    } catch (RuntimeException ex) {
        SidecarFailureException sidecar = findSidecar(ex);
        if (sidecar != null) {
            return VisionBundle.failed(sidecar.errorCode());
        }
        throw ex;
    }
}
```

`runSpeech` stays as today; only the live ASR adapter changes underneath.

---

### 11.9 Config / properties (no max-images change)

```properties
# application-local.properties (example)
foshol.ai.mode=live
foshol.ai.base-url=http://localhost:8000
foshol.ai.timeout=PT8S
# foshol.intake.max-images stays 3
```

Optional later (only if you want path overrides without recompile):

```properties
foshol.ai.vision.path=/ai/detect-disease
foshol.ai.asr.path=/ai/transcribe
```

Wire through `ConfigKeys` + `AnalysisSettings` if added.

---

### 11.10 Tests to add when implementing

**Adapter (WireMock / MockRestServiceServer):**

- Multipart POST `/ai/detect-disease` with 2 parts → parse sample `results[]` → 2 `VisionImageResult`s.  
- Header `X-Correlation-Id` present.  
- `/ai/transcribe` → `TranscriptResult.transcriptBn` equals `text`.

**Handler:**

- Stub `VisionModelPort.classifyBatch` returning same disease on two images with scores `0.91` and `0.69` → persisted MODEL candidate confidence `0.9100` (after scale).  
- Replay mode: existing fixtures still pass via batch fixture adapter.

---

### 11.11 File touch list (when you approve)

| Repo | Path | Action |
|------|------|--------|
| farmer-ai-service | `api/main.py` | Update contract (§11.1) |
| foshol-doctor | `application/port/VisionImageRef.java` etc. | Add |
| foshol-doctor | `application/port/VisionModelPort.java` | Add `classifyBatch` |
| foshol-doctor | `infrastructure/adapter/http/SidecarHttpClient.java` | Multipart + `readBytes` |
| foshol-doctor | `infrastructure/adapter/http/HttpVisionModelAdapter.java` | Rewrite to batch multipart |
| foshol-doctor | `infrastructure/adapter/http/HttpSpeechToTextAdapter.java` | Multipart + `text` |
| foshol-doctor | `infrastructure/adapter/http/dto/*` | Add |
| foshol-doctor | `infrastructure/adapter/fixture/FixtureVisionModelAdapter.java` | Implement batch |
| foshol-doctor | `application/command/RunAnalysisCommandHandler.java` | Batch `runVision` |
| foshol-doctor | tests for adapters + handler | Add/update |
| foshol-doctor | `CaseController` | **No change** |

---

When you are satisfied with these sketches, ask to implement in the codebase and we will apply them for real (with tests and compile checks).
