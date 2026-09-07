# Fasol Doctor — Implementation Plan v2 (revised scope)

**Team:** 3 engineers — assumed **2 backend + 1 frontend** (your note said "2 BE + 1 BE"; confirm)
**Duration:** 6 days · **Stack:** Java 25 · Spring Boot 4.1.1 · Spring Modulith · Gradle 9 (Kotlin DSL) · PostgreSQL 17 + pgvector · MinIO · `application.properties`
**Dropped from v1:** RabbitMQ, transactional outbox, SMS/USSD, mobile app, on-device inference, outbreak dashboard (stretch only)

---

## 1. Scope — the seven features, restated as a system

| # | Feature | Notes |
|---|---|---|
| F1 | Responsive web interface (farmer + field officer) | PWA-lite; mobile/tablet/desktop |
| F2 | Farmer uploads crop image(s) | 1–3 images per case, MinIO |
| F3 | Image → disease/symptom classification with confidence | Fine-tuned ViT/CNN in a Python inference sidecar |
| F4 | Bangla speech → text → symptom extraction | Whisper-family ASR + symptom lexicon matching |
| F5 | Confidence below threshold → knowledge-base symptom matching | Deterministic weighted scoring over `disease_symptom` |
| F6 | Knowledge base scoped to 3 crops | Rice, Tomato, Potato — ~14 disease classes |
| F7 | **Every** case enters the field officer approval workflow | Officer inspects, approves, edits, or replaces the remedy |

**The centre of gravity has moved.** In v1 the AI was the product. Here, the **human-in-the-loop approval workflow is the product**, and the AI is a triage accelerator. That is a stronger position for a hackathon and you should pitch it that way: *we do not ship unverified pesticide advice to a farmer — ever.* It also means a mediocre classifier can't sink your demo.

### Explicitly deferred (put on one slide, with the seam shown in code)

SMS/USSD channel · offline/on-device model · marketplace · weather alerts · outbreak analytics · multi-district scaling. For each, name the interface it plugs into (§4.4).

---

## 2. Architecture decisions (your calls, confirmed + reasoning)

### 2.1 RabbitMQ — drop it. You were right.

For a 6-day build with cross-module communication inside **one deployable**, a broker buys you nothing you can demo and costs you: topology declaration, DLQ config, consumer idempotency, container ops, and a whole class of "why didn't the message arrive" debugging at 2 a.m. on Day 5.

**Use instead:** Spring Modulith application events + **published module APIs**.

- Cross-module *commands* (module A needs module B to do something now): call B's published application-service interface — `com.fasol.review.ReviewSubmissionApi`. Only interfaces in the module's root package are visible; Modulith's `ApplicationModules.verify()` test fails the build if anyone reaches into another module's internals. This is your architectural proof, and it runs in CI.
- Cross-module *facts* (something happened, others may care): publish a domain event, consume with `@ApplicationModuleListener` (= `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` + transaction propagation). Same code shape as a broker consumer.

**The pitch line:** "Every cross-module interaction is either a published interface or an asynchronous domain event. Swapping the in-process event bus for RabbitMQ or Kafka is a dependency and a configuration change — `spring-modulith-events-amqp` externalises annotated events to a broker without touching a handler. We chose not to pay that cost for a single-node demo."

**Worth 20 minutes, optional:** add `spring-modulith-events-jpa`. Spring Modulith's Event Publication Registry persists each event before delivery and republishes incomplete ones on restart — you get outbox semantics for one dependency and one property, with no code. You said drop the outbox; this is the version that costs nothing. Take it if Day 2 runs ahead, skip it otherwise. Fall-back if you skip it: `@Retryable` with exponential backoff on listeners + a `failed_event` table and an admin replay endpoint.

### 2.2 CQRS with write and read co-located

- Separate packages and types: `application/command/{Command, CommandHandler}` and `application/query/{Query, QueryHandler, ReadModel}`. No shared service class does both.
- Query handlers **never** load aggregates. They read purpose-built projection tables or read-only JPA views and return DTO `record`s directly.
- Projections are updated by `@ApplicationModuleListener` on domain events, not by triggers or joins at read time — so the read side is already fed by events, which is the only part that's hard to retrofit.
- Both sides use the same `DataSource` bean today, but the query side injects a **separate qualified `@ReadOnlyDataSource`** that currently points at the same URL. Splitting to a replica later = one property. Show this in the walkthrough; it takes 15 seconds and lands well.

### 2.3 Clean Architecture per module

```
<module>/
  api/              ← published interfaces + event records (the ONLY package other modules may import)
  domain/           ← aggregates, VOs, specifications, domain events, domain exceptions (no Spring)
  application/
    command/        ← commands + handlers
    query/          ← queries + handlers + read models
    port/           ← outbound ports (ImageStorePort, VisionModelPort, SpeechToTextPort, NotificationPort)
  infrastructure/   ← JPA adapters, MinIO adapter, HTTP clients to the AI sidecar, listeners
  web/              ← controllers, request/response records, mappers
```

Dependency rule: `web → application → domain`, `infrastructure → application` (implements ports). Enforce with ArchUnit + Modulith verification in CI.

### 2.4 Modules

| Module | Owns | Published API |
|---|---|---|
| `identity` | Farmer, FieldOfficer, phone-based auth, roles | `FarmerLookupApi`, `OfficerLookupApi` |
| `intake` | `DiagnosisCase` aggregate: images, audio, crop, plot, lifecycle | `CaseIntakeApi`; events `CaseSubmitted`, `CaseAnalysed` |
| `analysis` | Orchestrates vision + ASR + symptom extraction + confidence + KB fallback | `AnalysisApi`; event `AnalysisCompleted` |
| `knowledge` | Crop, Disease, Symptom, Remedy; symptom→disease matcher; admin CRUD | `KnowledgeQueryApi`, `SymptomMatchApi` |
| `review` | Officer queue, claim/approve/edit/reject, published `Advisory` | `ReviewSubmissionApi`; events `AdvisoryApproved`, `AdvisoryRevised` |
| `notification` | Delivery to farmer; channel abstraction | `NotificationPort` + `WebPushChannel` (+ `SmsChannel` stub) |

`analysis` and `knowledge` stay separate deliberately: the AI can be wrong, the knowledge base is authoritative. That separation is the safety argument.

---

## 3. The diagnosis pipeline (F3 → F5 → F7)

```
POST /api/v1/cases  (multipart: images[], audio?, cropId, note?)
  │  idempotency key, size/type validation
  │  local image quality gate  (blur = Laplacian variance, exposure = histogram)
  │  store originals + 1024px derivative in MinIO
  │  persist DiagnosisCase(SUBMITTED)
  │  → 202 Accepted { caseId }   +   publish CaseSubmitted
  ▼
AnalysisOrchestrator  (virtual threads; StructuredTaskScope joins the branches with a deadline)
  ├─ vision branch:  POST sidecar /v1/vision/classify  → [{classId, confidence}]
  └─ speech branch:  POST sidecar /v1/asr/transcribe   → Bangla transcript
                     → POST sidecar /v1/symptoms/extract → [{symptomCode, score}]
  ▼
ConfidenceRouter
  ├─ top1 ≥ τ_high (0.75)                    → PRIMARY: model diagnosis, remedy retrieved from KB
  ├─ τ_low (0.45) ≤ top1 < τ_high            → SECONDARY: knowledge-base symptom match
  │        symptoms from speech (+ optional VLM visual descriptors)
  │        weighted scoring over disease_symptom (weight × presence, normalised)
  │        merge with vision candidates → re-ranked shortlist
  └─ top1 < τ_low OR KB inconclusive         → UNDETERMINED: no remedy pre-filled, officer diagnoses
  ▼
publish AnalysisCompleted  →  review module creates ReviewTask (ALL cases, every path)
  ▼
Field officer console: images, audio playback, transcript, extracted symptoms, ranked candidates
with confidence bars, decision path badge (PRIMARY / SECONDARY / UNDETERMINED), suggested remedy
  →  Approve  |  Edit remedy  |  Replace diagnosis + remedy  |  Reject / request better photo
  ▼
AdvisoryApproved → notification module → Web Push + in-app  →  farmer sees the advisory
```

**Two thresholds, not one.** A single threshold makes the fallback feel binary and arbitrary. Two gives you three visibly different demo cases and a much better story. Both externalised: `fasol.analysis.confidence.high=0.75`, `fasol.analysis.confidence.low=0.45`.

**Ports that make the deferred features credible:**

```java
public interface CaseIntakeChannel { CaseId submit(IntakeRequest request); }   // WebIntakeAdapter today, SmsIntakeAdapter later
public interface NotificationChannel { boolean supports(Farmer f); void send(AdvisoryNotification n); }  // WebPushChannel today, SmsChannel stub
public interface VisionModelPort { List<Candidate> classify(ImageRef ref); }   // HTTP sidecar today, on-device/ONNX later
```

Ship `SmsChannel` as a real class that logs and returns `false`, registered behind `fasol.channels.sms.enabled=false`. Open that file during the demo. It costs ten minutes and it is the single cheapest way to prove "extensible by design" rather than assert it.

---

## 4. Data model (PostgreSQL)

```
farmer(id, name, phone_hash, phone_enc, district_code, created_at)
field_officer(id, name, phone_hash, district_code)
crop(id, code, name_bn, name_en)
disease(id, crop_id, code, name_bn, name_en, description_bn, severity, model_class_label)
symptom(id, code, name_bn, name_en, organ, embedding vector(768))
symptom_phrase(symptom_id, phrase_bn, embedding vector(768))     -- colloquial surface forms
disease_symptom(disease_id, symptom_id, weight)                  -- 0.0–1.0, the KB matcher's core
remedy(id, disease_id, type[CULTURAL|ORGANIC|BIOLOGICAL|CHEMICAL], title_bn,
       steps_bn JSONB, dosage_bn, phi_days, cost_tier, efficacy, source_ref, active)

diagnosis_case(id, farmer_id, crop_id, status, decision_path, created_at)
case_image(case_id, object_key, width, height, quality_score, rejected_reason)
case_audio(case_id, object_key, duration_ms, transcript_bn, asr_confidence)
case_symptom(case_id, symptom_id, score, source[SPEECH|VISION|OFFICER])
case_candidate(case_id, disease_id, confidence, rank, source[MODEL|KB])
analysis_run(case_id, model_id, model_version, prompt_version, latency_ms, raw_output JSONB)

review_task(id, case_id, officer_id, state[PENDING|CLAIMED|DONE], claimed_at, sla_due_at)
advisory(id, case_id, disease_id, remedy_ids[], officer_note_bn, officer_id,
         action[APPROVED|EDITED|REPLACED], published_at)

-- read side
v_officer_queue          (projection: case + top candidate + confidence + age + SLA)
v_farmer_case_history    (projection: case + advisory + status for the farmer's list)
```

Rules: UUIDv7 PKs · Flyway only, no `ddl-auto` · `model_class_label` is the join between the ML label space and your taxonomy (never let model strings leak past `analysis`) · every remedy row carries `source_ref` citing DAE/BRRI/BARI guidance · `phi_days` (pre-harvest interval) is mandatory on chemical remedies.

---

## 5. AI inference sidecar

One Python **FastAPI** container, three endpoints, called from Spring via an HTTP Service Client with Resilience4j (timeout 8 s, circuit breaker, fallback → `UNDETERMINED`).

| Endpoint | Model | Notes |
|---|---|---|
| `POST /v1/vision/classify` | Fine-tuned ViT / MobileNetV3 | §6 |
| `POST /v1/asr/transcribe` | Bangla Whisper / w2v-BERT | §7 |
| `POST /v1/symptoms/extract` | Sentence embeddings + lexicon | §7 |

**Why a sidecar and not DJL/ONNX in Java:** you have 6 days and two backend engineers. The Python ecosystem gets you from model card to working endpoint in under an hour; DJL will cost you most of a day in tensor-shape debugging. The `VisionModelPort` interface means moving to in-JVM ONNX later is an adapter swap — say exactly that if a judge asks why there's Python in a Java project.

**Replay mode is mandatory.** `fasol.ai.mode=replay` serves recorded responses keyed by image SHA-256. Build it Day 2. Demo in replay unless the live path has been stable for a full day.

---

## 6. Answer: which free model for crop/symptom classification

### Recommended path — fine-tune on a Bangladeshi dataset (half a day, free)

**Dataset: Dhan-Shomadhan** — <cite index="32-1,41-1">1,106 rice leaf images collected from rice fields in the Dhaka Division, covering Brown Spot, Leaf Scald, Rice Blast, Rice Tungro and Sheath Blight, captured in both field-background and white-background variants</cite>. On Kaggle and Mendeley Data. This is the single best asset available to you: locally collected, field conditions, and exactly the diseases a Bangladeshi rice farmer faces. Naming it on stage is worth real credibility.

**Supplement: PlantVillage** — <cite index="22-1">54,306 images of healthy and diseased leaves across 14 crop species and 26 diseases, open access</cite>, available as a Hugging Face dataset. **It contains no rice**, which is why you need Dhan-Shomadhan. Take the tomato and potato subsets only.

**Backbone:** `google/vit-base-patch16-224` (Apache-2.0) fine-tuned with `transformers`, or `MobileNetV3`/`microsoft/resnet-50` if you want CPU-fast inference. <cite index="23-1">ViT works well with enough data and compute, ResNet-50 is a reliable choice for leaf disease classification, and MobileNetV2 is the better pick for lightweight, fast deployment</cite>. On a free Colab T4 this trains in 20–40 minutes.

**Class list (14 classes, 3 crops):** rice — brown spot, leaf scald, blast, tungro, sheath blight, healthy · tomato — early blight, late blight, leaf curl virus, septoria, healthy · potato — early blight, late blight, healthy.

### Fallback if training slips — off-the-shelf weights

- `wambugu71/crop_leaf_diseases_vit` — ViT, MIT licence, ~22 MB
- `Daksh159/plant-disease-mobilenetv2` — <cite index="25-1">MobileNetV2 trained on the augmented PlantVillage set for 38-class plant disease classification, aimed at low-compute and real-time inference</cite>
- `NouRed/recognize-plant-diseases-vit` — <cite index="28-1">ViT, 85.8M params</cite>

All PlantVillage-derived, so tomato/potato only. Use these on Day 1 to unblock the backend contract, swap in your fine-tune when it lands.

### Optional second signal — a VLM as symptom describer

An open-weight VLM (Qwen2.5-VL-7B or Gemma 3 vision via Ollama, both free) can convert an image into **visual symptom descriptors** — lesion colour, shape, margin, leaf position, spread pattern — as structured JSON constrained to your `symptom` code list. Feed those into the same KB matcher as the speech-derived symptoms. This is what makes the fallback path (F5) work even when the farmer says nothing.

Only do this if one machine has a GPU and Day 3 is on schedule. It is a genuine wow moment — "the classifier is unsure, so the system falls back to describing what it sees and reasoning over the knowledge base like an agronomist would" — but it is not load-bearing.

### Honesty note for the deck

PlantVillage-trained models are known to degrade on real field photos (uncontrolled background, lighting, occlusion) — this is the documented weakness the LeafNet authors cite when motivating larger datasets. Dhan-Shomadhan's field-background split mitigates it partially. **State this limitation yourself before a judge does**, and point at the approval workflow as the mitigation. Owning a known limitation reads as competence; being caught by it does not.

---

## 7. Answer: what to use for Bangla speech + symptom extraction

### 7.1 Speech-to-text — yes, Whisper, but not vanilla

Vanilla `openai/whisper` (MIT) handles Bengali poorly at small sizes. Three better free options, all on Hugging Face — **run a 20-clip bake-off on Day 1 morning and pick by WER on your own recordings**, not by model card claims:

1. **`sazzadul/Shrutimala_Bangla_ASR`** — <cite index="12-1">a fine-tune of `facebook/w2v-bert-2.0` for Bangla ASR trained on Common Voice 17.0/20.0 and OpenSLR, reporting 11% WER; the card notes it may struggle in noisy environments and with regional dialects outside its training data</cite>. Strongest reported accuracy of the three.
2. **`bengaliAI/tugstugi_bengaliai-regional-asr_whisper-medium`** — <cite index="19-1">a Whisper model trained on regional Bengali speech covering 10 dialects</cite>. **This is the one that matters for your users.** Farmers in Mymensingh or Sylhet do not speak Dhaka standard Bangla, and dialect coverage is the difference between a demo and a product. There is a Hugging Face Space to try it before you commit.
3. **Whisper-small Bangla fine-tunes** — `asif00/whisper-bangla` and `arif11/bangla-ASR-v5` are <cite index="16-1">fine-tunes of `openai/whisper-small` on Common Voice</cite> (Apache-2.0); `ashrafulparan/whisper-small-bangla` is Apache-2.0 and Bengali.AI-tagged. Smallest and fastest — good CPU fallback.

**Runtime:** `faster-whisper` (CTranslate2) for the Whisper-family options — 3–4× faster, much lower memory, same weights. `transformers` pipeline for the w2v-BERT one.

**Fallback in the browser:** the Web Speech API with `lang="bn-BD"` works in Chrome and costs nothing. Wire it as a client-side degraded path if the sidecar is down, and as a "type it instead" text box always.

Record clean 16 kHz mono WAV in the browser (`MediaRecorder` + resample), cap at 30 s, and normalise loudness before sending. Half of ASR quality in a field demo is audio hygiene, not model choice.

### 7.2 Symptom extraction — do NOT use an LLM here

Transcript → symptom codes is a **retrieval problem with a small closed label set**, and treating it as one gives you determinism, explainability, and offline operation. Pipeline:

```
transcript_bn
  → normalise (Unicode NFC, punctuation, ZWNJ, digit forms)
  → embed with LaBSE  or  intfloat/multilingual-e5-base   (both free, both strong on Bangla)
  → cosine kNN (pgvector, HNSW) against symptom_phrase.embedding
  → keep matches ≥ 0.72 → [{symptomCode, score}]
  → weighted match against disease_symptom → ranked diseases
```

The knowledge base carries 5–10 **colloquial** Bangla phrasings per symptom, not textbook terms — "পাতা হলুদ হয়ে যাচ্ছে", "পাতায় বাদামি দাগ পড়ছে", "গাছ শুকিয়ে যাচ্ছে", "শিষ সাদা হয়ে গেছে". Curating those phrases is the highest-value non-code work of the week and it belongs to a native Bangla speaker with an agronomy reference, not to an agent.

Add a **fuzzy keyword layer** underneath (normalised token overlap) so an embedding miss still catches the obvious cases. Belt and braces, 40 lines.

Alternative embedding option worth a look: **`csebuetnlp/banglabert`** — Bangla-native, from BUET, and a nice detail for a Bangladeshi audience. Benchmark it against LaBSE on your own phrase set; pick by numbers.

**Optional LLM layer:** a small instruct model via Ollama (Gemma 3 4B, Qwen2.5 7B — free, local) can do structured symptom extraction when the lexicon returns nothing. Constrain it with a JSON schema listing only your symptom codes, and treat its output as a *suggestion into the same scorer* — never as an authoritative diagnosis, and never as a source of remedy text.

### 7.3 Output side

Bangla TTS for reading the advisory aloud: browser `SpeechSynthesis` with `bn-BD` if the demo machine has a voice installed — verify on the actual machine, support is inconsistent. If it isn't there, drop it silently; do not put it in the demo script unless it was working the day before.

---

## 8. Frontend (1 engineer, 6 days)

**Stack:** React + Vite + TypeScript + Tailwind. Mobile-first, tested at 360 px, 768 px, 1280 px. Bangla UI strings via a simple i18n map with an EN toggle for the judges — build both from Day 1 or you will retrofit strings on Day 5.

**Three surfaces:**

1. **Farmer** — crop picker (large icons, not a dropdown), camera/upload with client-side compression and instant preview, hold-to-record voice with a waveform, live case status, advisory card with numbered steps, pictograms, severity colour, and a visible "verified by [officer name]" stamp.
2. **Officer console** — queue table (sorted by confidence ascending, so the least certain cases surface first — a nice detail to point out), case detail with image zoom, audio player, transcript, extracted symptoms as chips, candidate list with confidence bars, decision-path badge, remedy editor prefilled from the KB, and Approve / Edit / Replace / Reject.
3. **Admin** — CRUD on crops, diseases, symptoms, phrases, remedies; threshold configuration; a stats strip (cases today, approval rate, median review time, model-vs-officer agreement).

**Push:** Web Push (VAPID, service worker) for the real notification, plus **SSE** for live in-app status so the officer queue updates without polling. SSE is 30 lines on both ends and does most of the visible work — build SSE first, Web Push second. The `NotificationChannel` port makes SMS a later adapter.

**Wow, cheaply:**
- Grad-CAM/attention heatmap overlay from the sidecar showing *where* the model looked. One extra endpoint, enormous perceived sophistication, and it genuinely helps the officer.
- Confidence bars with the two thresholds drawn as vertical lines — makes F5's routing visible rather than described.
- **Model-vs-officer agreement rate** on the admin strip. It says "we measure ourselves" and it is the KPI that makes the retraining loop real.

---

## 9. Six-day schedule

Roles: **BE1** = intake, review, notification, platform · **BE2** = analysis, knowledge, AI sidecar · **FE** = all three surfaces. Daily 15-minute integration checkpoint at 17:00; `main` must be demoable every evening.

### Day 1 — Skeleton, contracts, and the ASR bake-off

- **BE1:** Gradle 9 multi-module + convention plugins + version catalog · Spring Boot 4.1.1, Modulith, virtual threads on · `docker-compose` (Postgres+pgvector, MinIO, sidecar) · Flyway baseline with the full schema (§4) · module skeletons with `api/domain/application/infrastructure/web` · `ApplicationModules.verify()` + ArchUnit in CI · MinIO adapter behind `ImageStorePort` · seed identity + auth (phone + OTP stub for farmers, password for officers).
- **BE2:** FastAPI sidecar skeleton with all three endpoints returning stubs · **ASR bake-off**: record 20 Bangla clips on a phone, run the three candidates from §7.1, pick by WER · download Dhan-Shomadhan + PlantVillage tomato/potato, start the fine-tune.
- **FE:** OpenAPI-driven client generation · design system, layout shell, routing, i18n scaffold · farmer upload screen against stubs.
- **Shared (60–90 min, whole team):** lock the 14 disease classes; assign KB content curation to a named person with a DAE/BRRI reference; write the demo script (§11) and pin it.
- **DoD:** `./gradlew build` green, compose up, stub end-to-end call from browser → Spring → sidecar → browser.

### Day 2 — Image path end-to-end

- **BE1:** `POST /api/v1/cases` with idempotency + validation + quality gate · MinIO storage of original + derivative · `DiagnosisCase` aggregate and lifecycle · `CaseSubmitted` event · SSE status endpoint · case history query side.
- **BE2:** real classifier deployed in the sidecar · `VisionModelPort` + HTTP client + Resilience4j · `AnalysisOrchestrator` with `StructuredTaskScope` · **replay mode** · `analysis_run` audit rows.
- **FE:** upload → progress → result flow against the real API; officer queue table (read-only).
- **DoD:** photo in → ranked diagnosis out, live, in the browser. **Screen-record it.** That recording is your insurance.

### Day 3 — Knowledge base + approval workflow

- **BE1:** `review` module — `ReviewTask` creation on `AnalysisCompleted`, claim/approve/edit/replace/reject, `Advisory` aggregate, `AdvisoryApproved` event · officer queue projection · role-based access.
- **BE2:** `knowledge` module — Crop/Disease/Symptom/Remedy aggregates, admin CRUD (FR-13 equivalent), symptom→disease weighted matcher, `ConfidenceRouter` with both thresholds and the three decision paths.
- **FE:** officer console — case detail, candidate list with confidence bars, remedy editor, approve/edit/replace actions.
- **Content:** all 14 diseases with remedies, sources, and PHI values reviewed by a human. **This must finish today.**
- **DoD:** a farmer case flows to the officer, the officer edits the remedy, and the farmer sees the edited version.

### Day 4 — Bangla voice + fallback path + notifications

- **BE1:** `notification` module, Web Push (VAPID) + service worker registration + SSE fan-out · `SmsChannel` stub · farmer advisory view.
- **BE2:** ASR endpoint live with the chosen model · symptom-phrase embeddings loaded into pgvector · `/v1/symptoms/extract` · fusion of speech symptoms with vision candidates · optional VLM descriptor endpoint if ahead of schedule.
- **FE:** voice recording UI with waveform · transcript + extracted symptom chips on the officer console · push permission flow and notification handling · admin screens.
- **DoD:** voice-only submission produces symptoms and a KB-matched shortlist; approving a case fires a push notification the farmer receives.

### Day 5 — Hardening, evidence, freeze

- Grad-CAM overlay endpoint + UI (if Day 4 closed clean).
- Seed 40–60 realistic historical cases so every screen looks inhabited — empty tables kill demos.
- Failure drills, recorded: sidecar down (circuit breaker → `UNDETERMINED`, workflow still functions), MinIO down, oversized/corrupt upload, non-plant photo, silent audio.
- Load test with k6: 200 concurrent submissions, capture p50/p95/p99 and the virtual-thread story. Modest numbers honestly measured beat big numbers claimed.
- Security pass: encrypted phone column, role checks on every endpoint, no PII in logs, upload size/MIME limits, rate limiting, secrets out of the repo.
- Accuracy eval on a held-out set → top-1 and top-3 numbers for the deck.
- **Feature freeze at 18:00.** Day 6 is not a coding day.

### Day 6 — Rehearse, document, buffer

- Three full dress rehearsals on the actual demo machine and network, timed.
- Deck (10 slides): problem → live demo → human-in-the-loop safety architecture → Modulith/CQRS/Clean walkthrough → extensibility (open `SmsChannel`, show the AMQP one-liner) → models and why they're free and local → measurement (accuracy, agreement rate, latency) → roadmap → how 3 engineers + agentic AI did this in 6 days.
- Artefacts: README with one-command startup, ADRs, OpenAPI, ERD, module diagram (Modulith generates it), eval + load reports.
- Afternoon: buffer. Something will break.

---

## 10. Agentic AI workflow

- **Day 1, first 90 minutes:** convert this plan into `docs/requirements.ears.md` in EARS format with IDs. Every generated artifact traces to a requirement ID; no requirement, no code. This is the same discipline as your `bits-ddd` skill and it is what makes parallel agents safe.
- `CLAUDE.md` at root: module map, the dependency rule, published-API-only imports, records over classes, imports not FQNs, constants not literals (event names, error messages, config keys), never invent business rules, DoD = `./gradlew build` clean then tests green.
- **Freeze the module `api` packages — interfaces and event records — before any agent writes a handler.** This is the single highest-value gate: with contracts frozen, agents in separate git worktrees cannot break each other.
- Fan out one agent per module after the domain layer lands. Verification gate per run: compile → unit tests → `ApplicationModules.verify()` → ArchUnit → Testcontainers integration test.
- **Humans own:** the remedy content, dosages, PHI values, Bangla symptom phrases, and both confidence thresholds. Agents may not author agronomic data. Say this on stage — "we used AI aggressively to build the system and not at all to author the agricultural advice" is a strong, honest line.
- Agents write tests alongside each handler, not in a batch at the end.

---

## 11. Demo script (write Day 1, rehearse from Day 4) — target 6 minutes

1. Farmer photographs a clear brown-spot rice leaf → high-confidence diagnosis in ~2 s, Grad-CAM overlay shows the lesions the model focused on. *(PRIMARY path)*
2. Farmer photographs an ambiguous leaf → confidence lands between the thresholds → system asks for symptoms; farmer **speaks in Bangla** → transcript and symptom chips appear → knowledge base produces a re-ranked shortlist. *(SECONDARY path — this is the money shot; explain the two thresholds on screen)*
3. Farmer uploads a blurry or non-crop photo → rejected locally with a Bangla re-capture prompt.
4. Switch to the officer console. Both cases are queued, least-confident first. Officer opens case 2, listens to the audio, **edits the remedy** (changes the dose, adds a cultural control), approves.
5. Switch back to the farmer tab → **push notification arrives** → advisory shown with the officer's name and "verified" stamp. *"No farmer in this system has ever received unverified pesticide advice."*
6. Admin strip: model-vs-officer agreement rate → this is the retraining signal; every officer edit is labelled training data.
7. Architecture: module diagram, open `SmsChannel`, show `spring-modulith-events-amqp` in the build file as a comment, show the read-only DataSource qualifier. 60 seconds, three slides.
8. Kill the sidecar live → case still flows to the officer as `UNDETERMINED`. Degradation, not failure.

---

## 12. Risks

| Risk | Mitigation | Cut trigger |
|---|---|---|
| Classifier weak on real field photos | Two-threshold routing + KB fallback + officer approval; own the limitation in the deck | If top-3 < 60% on Day 3, reframe the UI as "differential diagnosis" and lead the demo with the workflow, not the model |
| Bangla ASR poor on demo-room audio | Day-1 bake-off; loudness normalisation; always-available text box; pre-recorded clean audio in reserve | If WER > 35% on your own clips, demo with pre-recorded audio and say so |
| Content curation slips past Day 3 | Named owner, hard Day-3 deadline | Ship 8 diseases fully sourced rather than 14 half-sourced |
| Web Push permission/HTTPS friction on demo machine | Build SSE first; push is additive | Drop push, keep in-app SSE toast |
| Frontend is the bottleneck (1 FE, 3 surfaces) | Farmer + officer are must-have; admin is stretch | Cut the admin UI to seeded SQL + a read-only stats page |
| Agent-generated code compiles but is wrong | Verification gate per run; humans own domain + content | Drop to agent-assisted rather than agent-led |

---

## 13. First three hours

1. Confirm the team split (2 BE + 1 FE?) and the judging criteria.
2. Write `docs/requirements.ears.md`.
3. Lock the 14 disease classes and name the KB content owner.
4. Record 20 Bangla clips and start the ASR bake-off — this has the longest lead time and the most uncertainty.
5. Scaffold the repo, `CLAUDE.md`, ADR-0001 (Modulith over microservices), ADR-0002 (in-process events over a broker, with the migration path), ADR-0003 (human approval mandatory for all advisories).
6. Freeze the module `api` packages before any agent starts.
