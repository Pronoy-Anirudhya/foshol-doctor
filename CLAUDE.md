# CLAUDE.md — Foshol Doctor

Operating rules for every agent working in this repository. Read this before you read anything else.
It is short on purpose. The detail lives in `docs/requirements/`.

---

## What this project is

AI-triaged crop-disease diagnosis from photographs and Bangla speech, where **every single case is
approved by a human field officer before any advice reaches the farmer**. Three crops (rice, tomato,
potato), 14 disease classes, one Spring Modulith deployable, a Python inference sidecar, an Angular
frontend.

The sentence the whole system exists to make true:

> **No farmer in this system has ever received unverified pesticide advice.**

The human-in-the-loop approval workflow is the product. The AI is a triage accelerator.

---

## The specification is the source of truth

| Document | What it is |
|---|---|
| `docs/requirements/00-common.ears.md` | **FROZEN.** Schema, published interfaces, events, config, conventions, architectural laws. Read it in full. |
| `docs/requirements/1X-<module>.ears.md` | One per module. You read your own and `00-common`, and nothing else. |
| `docs/requirements/60-inference-sidecar.ears.md` | The Python sidecar. |
| `docs/requirements/70-frontend.ears.md` | The Angular app. |
| `docs/requirements/INDEX.md` | Dependency graph, build order, the 4-day schedule, the demo script. |
| `docs/requirements/CONTENT-OWNERS.md` | Everything you are **not** allowed to author. |
| `docs/adr/` | Why each contested decision went the way it did. |
| `docs/openapi/foshol-api.yaml` | **FROZEN.** The API contract the frontend generates from. |

`docs/fasol-doctor-plan-v2-revised-scope.md` is the historical input plan. It is **superseded** on
naming (Fasol → Foshol), frontend (React → Angular), build (Kotlin DSL → Groovy DSL) and, most
importantly, on machine learning. Use it for background only.

**No requirement, no code.** Every artefact you produce traces to a requirement ID. If what you need
is not specified, raise a blocker — do not invent it.

---

## The five rules that break the build if you ignore them

### 1. Never train a model

There is **no training infrastructure**. No fine-tuning, no distillation, no model bake-off, in any
form — not as code, not as a script, not as a `[DEFERRED]` requirement, not as a comment. All models
are pretrained weights loaded by identifier from configuration (`00-common` §2.4). The plan's
Dhan-Shomadhan fine-tune and ASR bake-off are **struck**. See ADR-0008.

### 2. Never quote someone else's accuracy

No accuracy, WER, F1 or precision figure from a model card, paper or benchmark may appear in the
product, the deck, the README or any requirement. The only permitted source of an accuracy number is
a held-out evaluation produced by this project (`tools/eval.py`, ADR-0011). Until that report exists,
do not quote a number.

### 3. Never author agricultural content

Disease descriptions, remedy text, dosages, pre-harvest intervals, source citations and Bangla
symptom phrases are **human-supplied**. See `docs/requirements/CONTENT-OWNERS.md`. If you are blocked
on missing content, leave `TODO(content-owner: C<n>)` in the migration scaffold and record a blocker.
**Never fill in a plausible-looking value.** A wrong dosage is not a bug, it is harm.

*We used AI aggressively to build the system and not at all to author the agricultural advice.*

### 4. Never touch another agent's files

Ownership is in `00-common` §12.1. The `api` package of every module and every Flyway migration are
**frozen after Day 0**. A change you need in someone else's file is a blocker, not an edit.

### 5. Never work around an architectural law

If you cannot satisfy a requirement without breaking a law in `00-common` §5, stop and record the
blocker. A workaround that violates a law is worse than an unimplemented requirement, because it is
invisible until integration.

---

## Module map

| Module | Owns | Published API |
|---|---|---|
| `common` | Enums, constants, `Uuid7`, **and every domain event record**. Modulith **shared** module. No beans, no logic. | — |
| `identity` | Farmer, FieldOfficer, phone auth, JWT | `FarmerLookupApi`, `OfficerLookupApi` |
| `intake` | `DiagnosisCase`, images, audio, idempotency, case status | `CaseIntakeApi`, `CaseIntakeChannel`; events `CaseSubmitted`, `CaseStatusChanged` |
| `analysis` | Orchestration, vision + ASR + embedding ports, label mapping, `ConfidenceRouter` | `AnalysisApi`; events `AnalysisCompleted`, `AnalysisFailed` |
| `knowledge` | Crop, Disease, Symptom, Remedy, `model_label_map`, the pgvector matcher | `KnowledgeQueryApi`, `SymptomMatchApi` |
| `review` | Officer queue, claim/approve/edit/replace/reject, `Advisory` | `ReviewSubmissionApi`; events `AdvisoryApproved`, `AdvisoryRevised`, `CaseRejected` |
| `notification` | Delivery to the farmer, SSE, channel registry | `NotificationPort`, `NotificationChannel` |

`analysis` and `knowledge` are separate **deliberately**: the AI can be wrong, the knowledge base is
authoritative. That separation is the safety argument.

---

## Coding rules

- **Records over classes** for every DTO, command, query, event and read model.
- **Constants over literals** — event names, error codes, config keys and enumerated strings live in
  `com.rootcause.foshol.common`.
- **Imports, not fully qualified names**, in method bodies and field declarations.
- **One writer per table.** If you need a row in a table you do not own, call the owning module's
  published API. (`review` adding officer symptoms calls `AnalysisApi.recordOfficerSymptoms` — it
  does not `INSERT` into `case_symptom`.)
- **Cross-module references are raw `UUID` columns**, resolved through a published API. Database
  foreign keys across modules are required; JPA associations across modules are forbidden.
- **Every domain event record lives in `com.rootcause.foshol.common.events`**, never in a publisher's
  `api` package, and so does every record an event references. Event flow here is bidirectional in
  two places, so events in publisher packages would make `intake ↔ analysis` and `intake ↔ review`
  into module cycles and fail `ApplicationModules.verify()`. See `COMMON-ARCH-016`–`018`.
- **No business logic in a controller.** Bind, delegate to one handler, map the result.
- **No Spring, JPA or Jackson in `domain`.**
- **No Java preview features.** `StructuredTaskScope` is a preview API on JDK 25 and is forbidden —
  use `Executors.newVirtualThreadPerTaskExecutor()` with `CompletableFuture` and an explicit deadline.
- **Tests alongside each handler**, not batched at the end.
- **Hand-written mappers.** No MapStruct, no ModelMapper.
- British spelling in prose; American spelling only where an API or library forces it.

---

## Definition of Done

Not done until **all ten** are true:

1. `./gradlew build` clean, no new warnings.
2. Unit tests pass — domain invariants and **every** command and query handler.
3. The module's one Testcontainers integration test passes.
4. `ApplicationModules.verify()` passes.
5. `ArchitectureTests` passes.
6. Migrations apply from empty: `docker compose down -v && docker compose up`.
7. Endpoints match `docs/openapi/foshol-api.yaml` exactly.
8. No `TODO`, no commented-out code, no `System.out`, no unused import.
9. Every requirement ID either implemented or listed `[DEFERRED]` in your progress note.
10. No agricultural content authored.

---

## Verification gate

Run this before you claim anything is finished:

```bash
./gradlew clean build
```

```bash
docker compose down -v && docker compose up -d && ./gradlew integrationTest
```

---

## Progress and blockers

Record blockers against the owning requirement and owner in `docs/requirements/` (and
`CONTENT-OWNERS.md` for content). Do not add new files under `docs/` outside your ownership area.

`main` must be demoable at every daily checkpoint. If your work does not pass the gate, it does not
merge.
