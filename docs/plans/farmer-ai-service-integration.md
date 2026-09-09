# What `RunAnalysisCommandHandler` does (business)

**File:** `modules/analysis/.../command/RunAnalysisCommandHandler.java`  
**Trigger:** `AnalysisEventListener` listens for `CaseSubmitted` (published by intake after `POST /api/v1/cases`) and calls `handle(...)`.

This handler is the **AI triage brain** of Foshol. It never talks to the farmer. It turns photos (+ optional Bangla audio) into ranked disease candidates and a routing decision for the field officer queue.

---

## 1. Place in the product

```
Farmer submits case (intake)
        │
        ▼
  CaseSubmitted event
        │
        ▼
  RunAnalysisCommandHandler   ← you are here
        │
        ├─ persist analysis_run + candidates + symptoms
        └─ publish AnalysisCompleted (or AnalysisFailed)
                │
                ▼
        intake advances status → IN_REVIEW
        review creates officer queue task
                │
                ▼
        Officer must approve / edit / reject
                │
                ▼
        Only then can the farmer get advice
```

**Business sentence:** the AI proposes; the officer decides; the farmer never sees unverified pesticide advice.

---

## 2. Entry: `handle(command)`

1. **Idempotency** — if this case already has a completed analysis run, exit (no double work).  
2. **Load case** via `CaseIntakeApi.findById`. If missing → publish `AnalysisFailed` (`ERR_CASE_NOT_FOUND`).  
3. **Orchestrate** vision + speech in parallel.  
4. **Persist** `analysis_run`, disease candidates, speech-matched symptoms.  
5. **Best-effort** write transcript back to intake (`recordTranscript`). Failure here is logged; analysis still completes.  
6. **Publish** `AnalysisCompleted` with decision path, candidates, symptoms, correlation id.  
7. On unexpected persistence failure → `AnalysisFailed`.

---

## 3. Parallel branches (deadline)

Within a configured deadline (`settings.deadline()`), two branches run on virtual threads:

| Branch | Input | Output (business meaning) |
|--------|--------|---------------------------|
| **Vision** | Each case image from MinIO | Ranked diseases the leaf model thinks it sees |
| **Speech** | Optional audio | Bangla transcript → symptom matches → disease scores from the knowledge base |

If a branch times out, it is **abandoned** (cancelled). The other branch’s result can still be used. Sidecar HTTP failures become branch **FAILED** with an error code — the case can still finish as `UNDETERMINED` rather than hanging forever.

---

## 4. Vision branch — “what does the leaf look like?”

For **each** image:

1. Call `VisionModelPort.classify` (replay fixture **or** live HTTP sidecar / your models).  
2. **Temperature-scale** raw softmax scores (calibration knob).  
3. **Map labels** through knowledge (`resolveModelLabel`) — model strings like `Tomato___Early_blight` become Foshol `diseaseId` / disease code. Unmapped labels are kept for audit, **not** treated as diseases.  
4. Keep the **max** score per disease **within** that image.

Then:

5. **Aggregate across images** (e.g. MAX) and truncate to candidate limit.  
6. Optionally **Grad-CAM** on the primary image (best quality score, then position) — overlay PNG stored in object store for the officer UI. Grad-CAM failure is soft (analysis continues without overlay).

**Result:** a list of mapped vision candidates: disease + confidence, plus unmapped raw labels.

---

## 5. Speech branch — “what did the farmer say?”

If there is **no audio** → empty success (vision-only case).

If there is audio:

1. Read bytes from object store; SHA-256.  
2. `SpeechToTextPort.transcribe` → Bangla transcript + ASR confidence.  
3. Normalise Bangla for storage.  
4. `TextEmbeddingPort.embed` → vector.  
5. If vector missing / all zeros → keep transcript, skip KB match.  
6. If wrong embedding dimension → speech branch failed.  
7. Else `SymptomMatchApi.match` (pgvector in **knowledge**) → matched symptoms + scored diseases.

**Result:** transcript, ASR model id, embed model id, optional KB symptom/disease match.

Knowledge is authoritative for symptom→disease retrieval. Vision and KB are deliberately separate so a wrong model label does not rewrite the knowledge base.

---

## 6. Assemble — routing decision

Merge the two branches into one **decision path** for the officer:

### Inputs to the router

- **top1 / top2** — highest vision confidences after mapping/aggregation  
- **prescribable?** — top disease is either “healthy” **or** has at least one active remedy in knowledge  
- **kbInconclusive?** — speech/KB path did not produce a usable match  

### `ConfidenceRouter` outcomes

| Path | Business meaning |
|------|------------------|
| **PRIMARY** | Vision is confident enough **and** top disease is prescribable → officer can quickly approve model suggestion |
| **SECONDARY** | Mid confidence → **merge** vision ranking with KB disease scores (`MergeRanker`); officer sees blended candidates |
| **UNDETERMINED** | Too uncertain, no remedy, all labels unmapped, sidecar down, both branches failed, etc. → officer must diagnose more carefully / system could not triage cleanly |

Forced `UNDETERMINED` also when:

- Sidecar unavailable / fixture missing on vision  
- Every vision label was unmapped  
- Both branches failed  

### What gets persisted

- Always: one `AnalysisRun` (path, top1/top2, latency, error code, unmapped labels, raw JSON audit blob, model ids, optional gradcam key).  
- Vision-only paths: candidates with source `MODEL`.  
- **SECONDARY**: candidates with sources `MODEL`, `KB`, and `MERGED` (merged list is what the event exposes to the rest of the system).  
- Speech-matched symptoms as `CaseSymptom` (`SPEECH` source).

### Event payload (`AnalysisCompleted`)

Case id, farmer, crop, decision path, AI mode (replay/live), top1 confidence, margin, candidate views (with Bangla disease names from knowledge), symptom views, whether audio existed, image count, correlation id.

Downstream: intake moves the case toward review; review creates the officer task. **No farmer notification of remedies from this handler.**

---

## 7. Ports it depends on (where your models plug in)

| Port | Role |
|------|------|
| `VisionModelPort` | Image → raw labels + scores |
| `SpeechToTextPort` | Audio → Bangla transcript |
| `TextEmbeddingPort` | Transcript text → embedding vector |
| `ExplainabilityPort` | Optional Grad-CAM overlay |
| `ObjectStorePort` | Read images/audio; write gradcam |
| `KnowledgeQueryApi` | Label map, disease metadata, remedies |
| `SymptomMatchApi` | Vector → symptoms / diseases |
| `CaseIntakeApi` | Load case; store transcript |
| `AnalysisPersistencePort` | Save run / candidates / symptoms |
| `AnalysisEventPort` | Publish completed / failed |

Live vs replay is chosen only by `foshol.ai.mode`. The handler does not care whether the model is a fixture or HTTP.

---

## 8. What this is *not*

- Not a REST controller — no HTTP API of its own.  
- Not intake — does not accept farmer uploads.  
- Not review — does not approve advisories.  
- Not knowledge authoring — does not invent disease text or dosages.  
- Not training — only consumes pretrained inference via ports.

---

## 9. One-sentence summary

**After a case is submitted, `RunAnalysisCommandHandler` runs vision and speech AI in parallel, maps model output onto the knowledge base, chooses PRIMARY / SECONDARY / UNDETERMINED for the officer queue, persists the audit trail, and emits `AnalysisCompleted` — never sending advice to the farmer.**

---

## Next

*(Intentionally empty — tell me what you want next after reading this.)*
