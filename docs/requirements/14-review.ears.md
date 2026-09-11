# 14 — Review (EARS)

**Module:** `review` · package `com.rootcause.foshol.review` · **Agent A5**
**Prefix:** `REVIEW` · **Feature:** F7 — every case enters the field-officer approval workflow.

> Read `docs/requirements/00-common.ears.md` first and treat it as immutable. This document and that
> one are together sufficient to implement this module. Where they disagree, `00-common.ears.md`
> wins. Requirements marked `[DERIVED]` extend the plan and carry inline reasoning.

---

## 0. Why this module exists

Every other module in Foshol Doctor is an accelerator. **This one is the product.**

The AI narrows fourteen disease classes down to a short, ranked, evidence-carrying shortlist. It then
hands that shortlist to a human being, who decides. Nothing reaches a farmer until a named field
officer has looked at the photographs, listened to the audio, read the extracted symptoms, and put
their name on the answer. The single sentence this whole system exists to make true is:

> **No farmer in this system has ever received unverified pesticide advice.**

That sentence is not marketing. It is an invariant, and it is enforced here. It holds only if
**every** case — high confidence, low confidence, no confidence, unmapped model label, dead sidecar,
failed analysis — arrives in a human queue. `REVIEW-FR-001` … `REVIEW-FR-005` are what make it true,
and `ReviewTaskUniversalityIT` (§8) is what proves it on every build.

---

## 1. Scope

### 1.1 Owned

| Artefact | Reference |
|---|---|
| `review_task` table | `00-common.ears.md` §4.6 |
| `advisory` table | §4.6 |
| `advisory_remedy` table | §4.6 |
| `case_rejection` table | §4.6 |
| `p_officer_queue` projection table | §4.8 |
| `ReviewSubmissionApi` and its view records | §6.2 |
| Events `AdvisoryApproved`, `AdvisoryRevised`, `CaseRejected` | §6.3 |
| Endpoints under `/api/v1/review/**`, `/api/v1/advisories/**`, `GET /api/v1/cases/{id}/advisory`, `GET /api/v1/admin/stats` | §5 below |
| The claim TTL sweeper | `REVIEW-FR-045` |

### 1.2 Explicitly **not** owned

| Not owned | Owner | Why it matters here |
|---|---|---|
| `diagnosis_case`, its status column and its state machine | `intake` (`11-intake.ears.md`) | Review never writes case status; it publishes an event and intake advances the case |
| Creating a replacement case after a rejection | `intake` | Rejection is terminal here; the new case is an intake concern (§4.7) |
| `case_symptom` | `analysis` (`12-analysis.ears.md`) | Officer symptom chips go through `AnalysisApi.recordOfficerSymptoms` — never a direct write (§4.8) |
| `case_candidate`, `analysis_run`, confidence and decision-path computation | `analysis` | Review reads them; it never recomputes a confidence or re-routes a case |
| `disease`, `remedy`, `symptom` content, `phi_days`, `source_ref` | `knowledge` (`13-knowledge.ears.md`) and `CONTENT-OWNERS.md` | Review selects remedies; it never authors agronomic text (`COMMON-CON-003`) |
| Officer and farmer records, authentication, JWT issuance | `identity` (`10-identity.ears.md`) | Review resolves an officer by id; it never stores officer data |
| Delivering anything to a farmer | `notification` (`15-notification.ears.md`) | Review publishes an event and stops. It has no channel, no SSE, no transport |
| `p_farmer_case_history` | `intake` | Different projection, different owner |
| Admin CRUD on knowledge content | Nobody — cut, `[DEFERRED]` in `13-knowledge.ears.md` | Admin surface is stats, KPIs, district case list and bulk reject |

`REVIEW-NFR-001` **THE review module SHALL NOT execute an `INSERT`, `UPDATE` or `DELETE` against any
table it does not own.** *(One writer per table is what allows seven agents to work in parallel
without a merge conflict becoming a data-corruption bug.)*

---

## 2. Dependencies

### 2.1 Published interfaces this module calls

Signatures are quoted verbatim from `00-common.ears.md` §6.2 and are **frozen** (`COMMON-NFR-041`).
Every call below is permitted by the dependency matrix in §6.1.

```java
CaseIntakeApi     .findById(UUID caseId) -> Optional<CaseSummary>        // intake.api
CaseIntakeApi     .isOwnedBy(UUID caseId, UUID farmerId) -> boolean
AnalysisApi       .findByCaseId(UUID caseId) -> Optional<AnalysisView>   // analysis.api
AnalysisApi       .recordOfficerSymptoms(UUID caseId, List<UUID> symptomIds) -> void
KnowledgeQueryApi .findDiseaseById(UUID diseaseId) -> Optional<DiseaseView>   // knowledge.api
KnowledgeQueryApi .listActiveRemedies(UUID diseaseId) -> List<RemedyView>
KnowledgeQueryApi .listSymptoms() -> List<SymptomRefView>
OfficerLookupApi  .findById(UUID officerId) -> Optional<OfficerView>     // identity.api
OfficerLookupApi  .findActiveByDistrict(String districtCode) -> List<OfficerView>  // identity.api
FarmerLookupApi   .findById(UUID farmerId) -> Optional<FarmerView>       // identity.api, REVIEW-NFR-002
```

Method signatures, parameter types and return types are exactly as declared in `00-common.ears.md`
§6.2; the abbreviated form above exists only to show which subset this module uses. The `View`
records (`CaseSummary`, `AnalysisView`, `DiseaseView`, `RemedyView`, `SymptomRefView`, `OfficerView`,
`FarmerView`) are used as published and are not re-declared here.

`REVIEW-NFR-002` `[DERIVED]` **THE review module SHALL resolve `p_officer_queue.farmer_name` through
`FarmerLookupApi.findById`.** *(The projection column in `00-common.ears.md` §4.8 requires a farmer
name; `CaseSummary` and `AnalysisCompleted` both carry only `farmerId`. `review → identity` is
already `✓` in the §6.1 matrix, so this needs no contract change — it is recorded here only because
the module brief listed four callees and this is the fifth.)*

### 2.2 Events consumed

| Event | Publisher | What this module does |
|---|---|---|
| `AnalysisCompleted` | `analysis` | Creates the `ReviewTask` and the queue row (`REVIEW-FR-001`) |
| `AnalysisFailed` | `analysis` | Creates the `ReviewTask` anyway, with no confidence (`REVIEW-FR-002`) |

### 2.3 Events published

```java
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

### 2.4 Day-0 items owned by A1

`REVIEW-NFR-003` **THE error codes `ERR_REVIEW_TASK_NOT_FOUND`, `ERR_CLAIM_NOT_HELD`,
`ERR_CLAIM_CONFLICT`, `ERR_TASK_TERMINAL`, `ERR_ADVISORY_NOT_FOUND`, `ERR_ADVISORY_REQUIRES_REMEDY`,
`ERR_REMEDY_DISEASE_MISMATCH`, `ERR_REMEDY_PHI_MISSING`, `ERR_UNKNOWN_SYMPTOM` and
`ERR_QUEUE_SORT_NOT_SUPPORTED` SHALL be declared in `common.ErrorCodes`** (`COMMON-ARCH-010`).
A5 raises these to A1 at Day 0; A5 does not add them itself (`COMMON-NFR-040`).

---

## 3. Domain model

### 3.1 `ReviewTask` — aggregate root

| Field | Type | Notes |
|---|---|---|
| `id` | `ReviewTaskId` (UUIDv7) | |
| `caseId` | `UUID` | raw id, not a JPA association (`COMMON-ARCH-006`) |
| `officerId` | `UUID`, nullable | set only while `CLAIMED` |
| `state` | `ReviewState` — `PENDING`, `CLAIMED`, `DONE`, `REJECTED` | |
| `priorityConfidence` | `BigDecimal(5,4)`, nullable | the top-1 model confidence; **null means "we have no idea"** |
| `claimedAt` | `Instant`, nullable | |
| `slaDueAt` | `Instant` | |
| `requeueCount` | `short` | incremented by the sweeper |
| `version` | `int` | JPA `@Version`; the optimistic lock behind claiming |

**Invariants** (each is a unit test on the aggregate, §8.1):

- `INV-RT-1` A case has **at most one** `ReviewTask`. Enforced by `review_task.case_id UNIQUE`.
- `INV-RT-2` `state = CLAIMED` **if and only if** `officerId` and `claimedAt` are both non-null.
  Enforced additionally by `ck_review_claim`.
- `INV-RT-3` `DONE` and `REJECTED` are **terminal**. No transition leaves them.
- `INV-RT-4` Only the officer named in `officerId` may act on a `CLAIMED` task.
- `INV-RT-5` A claim is expired when `now − claimedAt > foshol.review.claim.ttl`. An expired claim
  confers no authority even before the sweeper has run.
- `INV-RT-6` `requeueCount` never decreases.

**State machine.**

```
              claim                  approve/edit/replace
   PENDING ─────────────► CLAIMED ─────────────────────────► DONE
      ▲                    │  │
      │  release / sweep   │  │  reject
      └────────────────────┘  └────────────────────────────► REJECTED

   revise (§4.6): DONE ──re-claim──► CLAIMED ──publish v+1──► DONE
```

### 3.2 `Advisory` — aggregate root

| Field | Type | Notes |
|---|---|---|
| `id` | `AdvisoryId` | |
| `caseId`, `diseaseId`, `officerId` | `UUID` | |
| `action` | `AdvisoryAction` — `APPROVED`, `EDITED`, `REPLACED` | |
| `officerNoteBn` | `String`, nullable | officer-authored, never agent-authored |
| `version` | `short`, ≥ 1 | |
| `supersedesId` | `UUID`, nullable | |
| `remedies` | ordered `List<AdvisoryRemedy>` | `advisory_remedy` join rows |
| `publishedAt` | `Instant` | |

**Invariants:**

- `INV-AD-1` `version = 1` **if and only if** `supersedesId` is null. Enforced additionally by
  `ck_advisory_supersedes`.
- `INV-AD-2` `(caseId, version)` is unique (`uq_advisory_case_version`).
- `INV-AD-3` An advisory is **append-only**: once written, no field is ever updated and no row is
  ever deleted. A correction is a new row.
- `INV-AD-4` Every remedy referenced belongs to `diseaseId`.
- `INV-AD-5` An advisory whose disease is not a healthy class references **at least one** remedy.
- `INV-AD-6` Every referenced remedy of type `CHEMICAL` carries a non-null `phiDays`.
- `INV-AD-7` The publishing officer held a live claim on the case's `ReviewTask` at publication.

### 3.3 `CaseRejection` — entity

`caseId` (unique), `officerId`, `reasonCode` (`RejectionReason`), `messageBn`, `createdAt`.

- `INV-CR-1` A case has at most one rejection (`case_rejection.case_id UNIQUE`).
- `INV-CR-2` A case has either an advisory history or a rejection, never both.
- `INV-CR-3` `messageBn` is non-blank and officer-authored.

### 3.4 Specifications

| Specification | Rule |
|---|---|
| `TaskIsClaimable` | `state = PENDING`, or `state = CLAIMED` by the same officer with a live claim |
| `ClaimIsHeldBy(officerId)` | `state = CLAIMED` ∧ `officerId` matches ∧ claim not expired (`INV-RT-5`) |
| `RemediesBelongToDisease` | every `remedyId` ∈ `KnowledgeQueryApi.listActiveRemedies(diseaseId)` |
| `AdvisoryHasRequiredRemedy` | `DiseaseView.healthy()` ∨ `remedies.size() ≥ 1` |
| `ChemicalRemedyHasPhi` | ∀ r ∈ remedies : `r.type() ≠ CHEMICAL` ∨ `r.phiDays() ≠ null` |
| `TaskIsTerminal` | `state ∈ {DONE, REJECTED}` |

---

## 4. Requirements

> ID blocks are allocated **by topic**, not by narrative order. They are not read in numeric
> sequence.

### 4.1 Every case gets a ReviewTask — the invariant this module exists for

`REVIEW-FR-001` **WHEN the review module consumes `AnalysisCompleted`, THE review module SHALL create
exactly one `review_task` row for the case with `state = PENDING`, `priority_confidence` set to the
event's `top1Confidence`, and `sla_due_at` set to the event's `occurredAt` plus `foshol.review.sla`.**

`REVIEW-FR-002` **WHEN the review module consumes `AnalysisFailed`, THE review module SHALL create
exactly one `review_task` row for the case with `state = PENDING`, `priority_confidence` set to
`NULL`, and `sla_due_at` set to the event's `occurredAt` plus `foshol.review.sla`.**
*(A null confidence sorts first under `NULLS FIRST` — a case the machine could not read is the most
urgent thing an officer can be shown, not the least.)*

`REVIEW-FR-003` **THE review module SHALL hold, as an invariant, that every `diagnosis_case` which
has reached a terminal analysis outcome has exactly one `review_task` row — on every decision path,
in every analysis mode, with no exception.** This covers `PRIMARY`, `SECONDARY` and `UNDETERMINED`;
it covers a case whose model output was entirely unmapped (`COMMON-DATA-013`); and it covers a case
analysed while the sidecar was unreachable and the circuit was open (`COMMON-NFR-035`).
*(This is the requirement the product's safety claim rests on. It is verified by
`ReviewTaskUniversalityIT`, §8.3.)*

`REVIEW-FR-004` **IF `AnalysisCompleted` or `AnalysisFailed` is delivered more than once for the same
`caseId`, THEN THE review module SHALL leave the existing `review_task` and `p_officer_queue` rows
unchanged and SHALL NOT create a second task.** *(`COMMON-ARCH-015`; Modulith republishes
outstanding events on restart, so this path runs in the demo, not only in theory.)*

`REVIEW-FR-005` **IF an enrichment call to `CaseIntakeApi`, `AnalysisApi`, `KnowledgeQueryApi`,
`FarmerLookupApi` or `OfficerLookupApi` fails while handling `AnalysisCompleted` or `AnalysisFailed`,
THEN THE review module SHALL still create the `review_task` row, SHALL write the
`p_officer_queue` row with the fields it could resolve, SHALL log a `WARN` carrying the correlation
id, and SHALL NOT abandon task creation.** *(Task creation is the safety invariant. A missing crop
name is a cosmetic defect; a missing task is a farmer receiving nothing, or worse, something
unverified.)*

`REVIEW-FR-006` **THE review module SHALL write `review_task.created_by` and `updated_by` as
`'system'` on task creation** (`COMMON-DATA` audit columns, §4.6).

### 4.2 The `p_officer_queue` projection

`REVIEW-FR-010` **WHEN a `review_task` row is created, THE review module SHALL insert the
corresponding `p_officer_queue` row in the same transaction**, populating `case_id`,
`review_task_id`, `farmer_name`, `crop_code`, `crop_name_bn`, `district_code`, `decision_path`,
`top_disease_id`, `top_disease_name_bn`, `top_confidence`, `image_count`, `has_audio`,
`analysis_mode`, `state`, `officer_id`, `is_resubmission`, `submitted_at` and `sla_due_at`.

`REVIEW-FR-011` **THE review module SHALL set `p_officer_queue.top_disease_id` and `top_confidence`
from the rank-1 candidate of `AnalysisCompleted.candidates()`, and SHALL set both to `NULL` when the
event carries no candidate.**

`REVIEW-FR-012` **THE review module SHALL set `p_officer_queue.is_resubmission` to true when
`CaseSummary.parentCaseId()` is non-null, and false otherwise.**

`REVIEW-FR-013` **THE review module SHALL set `p_officer_queue.analysis_mode` from
`AnalysisCompleted.mode()`, and to `NULL` when the row originates from `AnalysisFailed`.**

`REVIEW-FR-014` **WHEN a `review_task` changes state, is claimed, is released, is swept or reaches a
terminal state, THE review module SHALL update `p_officer_queue.state`, `officer_id` and `updated_at`
in the same transaction as the task change.**

`REVIEW-FR-015` **THE review module SHALL serve the officer queue exclusively from
`p_officer_queue`, and SHALL NOT join `diagnosis_case`, `case_candidate`, `analysis_run` or any
`identity` table at queue read time.** *(Plan §2.2: projections are fed by listeners, not by joins at
read time.)*

`REVIEW-FR-016` **THE review module SHALL NOT persist a raw model label string on `p_officer_queue`**
(`COMMON-DATA-016`).

### 4.3 Queue ordering — newest submitted first, and not negotiable

`REVIEW-FR-030` **THE review module SHALL order the officer queue by `submitted_at DESC`
(newest case first), and SHALL use no other ordering.**
The order is backed by `ix_officer_queue_latest` on `p_officer_queue (submitted_at DESC)`
(`V111__officer_queue_latest_first.sql`). Farmer and admin lists already use the same newest-first
rule; the officer queue SHALL match them.

*Why this is fixed:* officers working the shared pool see the latest intake first, consistent with
the farmer and admin consoles. Client `sort` / `order` query parameters remain rejected
(`REVIEW-FR-031`).

`REVIEW-FR-031` **IF a request to `GET /api/v1/review/queue` carries a `sort` or `order` query
parameter, THEN THE review module SHALL return `400` with code `ERR_QUEUE_SORT_NOT_SUPPORTED`.**
*(Sorting is not client-controlled — `00-common.ears.md` §8.2. Refusing loudly is better than
silently ignoring: an agent building the frontend must find this out at once.)*

`REVIEW-FR-032` **THE review module SHALL accept an optional `state` filter on the queue with the
values `PENDING`, `CLAIMED` and `ALL`, defaulting to `PENDING`, and an optional boolean `mine`
filter which restricts results to tasks claimed by the calling officer.**

`REVIEW-FR-033` **THE review module SHALL paginate the queue with `page` and `size` per
`00-common.ears.md` §8.2**, `size` defaulting to `20` and capped at `100`.

`REVIEW-NFR-010` **THE officer queue query SHALL be satisfied by an index scan of
`ix_officer_queue_priority`**, verified by an `EXPLAIN` assertion in the module's integration test,
and SHALL meet the 300 ms p95 budget in `00-common.ears.md` §10.2.

### 4.4 Claim-based locking, TTL and SLA

Clarification item 4. The queue is a **shared pool**: any active officer may claim any pending task.
There is no per-officer inbox.

`REVIEW-FR-040` **WHEN an officer claims a `PENDING` review task, THE review module SHALL set
`state = CLAIMED`, `officer_id` to the calling officer, and `claimed_at` to the current instant,
using an optimistic lock on `review_task.version`.**

`REVIEW-FR-041` **IF the optimistic lock on `review_task.version` fails during a claim, THEN THE
review module SHALL return `409` with code `ERR_CLAIM_CONFLICT` and SHALL NOT modify the task.**
*(Two officers hitting Claim on the same row in the same second is the ordinary case in a shared
pool, not an exotic one.)*

`REVIEW-FR-042` **IF an officer claims a task already `CLAIMED` by a different officer whose claim
has not expired, THEN THE review module SHALL return `409` with code `ERR_CLAIM_CONFLICT`.**

`REVIEW-FR-043` **WHEN the officer who already holds a live claim claims the same task again, THE
review module SHALL refresh `claimed_at`, SHALL leave `requeue_count` unchanged, and SHALL return
`200`.** *(Claiming is idempotent for the holder, so a double-click or a page refresh is harmless.)*

`REVIEW-FR-044` **WHEN the officer holding a claim releases the task, THE review module SHALL set
`state = PENDING`, SHALL clear `officer_id` and `claimed_at`, and SHALL NOT increment
`requeue_count`.** *(A voluntary release is not a failure.)*

`REVIEW-FR-045` **WHILE the application is running, THE review module SHALL run a scheduled sweeper
at a fixed delay of `foshol.review.sweeper.interval`, which for every task in `state = CLAIMED` whose
`claimed_at` is older than `foshol.review.claim.ttl` SHALL set `state = PENDING`, SHALL clear
`officer_id` and `claimed_at`, SHALL increment `requeue_count`, SHALL update the matching
`p_officer_queue` row, and SHALL log one `WARN` line per swept task carrying the case id.**

`REVIEW-FR-046` **THE claim sweeper SHALL NOT modify a task in `state = DONE`, `REJECTED` or
`PENDING`.**

`REVIEW-FR-047` **THE claim sweeper SHALL select expired tasks with `FOR UPDATE SKIP LOCKED` and
SHALL be safe to run concurrently with officer claim and release requests**, producing the same
result whether it runs once or many times over the same expired task.

`REVIEW-FR-048` **THE review module SHALL restrict the officer queue, claims, task detail,
admin stats, admin case list, and admin KPI queries to tasks whose `district_code` matches the
calling officer's or admin's `field_officer.district_code` (loaded from identity, never from the
request body).** Cross-district access SHALL return `404` `ERR_REVIEW_TASK_NOT_FOUND` (or empty
stats / queue / case page), never a national view. There is no national admin in this build.
*Exception to `00-common.ears.md` §1.2: district routing is now in scope. Geography lives on
`geo_division` / `geo_district`; cases snapshot farmer codes at submit.*

`REVIEW-FR-049` **THE review module SHALL set `review_task.sla_due_at` from `foshol.review.sla` at
creation and SHALL NOT recompute it on claim, release or sweep.** *(The SLA clock belongs to the
farmer's wait, not to the officer's attempts.)*

`REVIEW-UX-001` **THE review module SHALL expose `sla_due_at` and `requeue_count` on every queue row
and task detail response**, so the officer console can mark an overdue or repeatedly-abandoned case.

`REVIEW-FR-090` **THE review module SHALL compute `assignment_due_at` at task creation as working time
from `foshol.review.kpi.assignment-sla`, `work-start`, `work-end`, `work-days` and `zone`.** The
assignment clock starts when the review task becomes `PENDING`.

`REVIEW-FR-091` **WHEN an officer first claims a task in an assignment window, THE review module SHALL
set `resolution_due_at` to working time `foshol.review.kpi.resolution-sla` after that claim and SHALL
NOT change it on holder re-claim or same-district transfer.**

`REVIEW-FR-092` **WHEN a live claim is released or TTL-swept, THE review module SHALL open a new
assignment window from now and SHALL cancel an unbreached resolution KPI.** A resolution KPI already
overdue remains recorded.

`REVIEW-FR-093` **THE review module SHALL persist one `kpi_breach` row per `(review_task_id, kind,
window_started_at)`.** Assignment breaches have no officer; resolution breaches record the holder.

`REVIEW-FR-094` **THE review module SHALL publish `KpiWarningIssued` once per claim window when now is
within `foshol.review.kpi.warn-before` of `resolution_due_at`.**

`REVIEW-FR-095` **THE review module SHALL expose district-scoped `GET /api/v1/admin/kpis` and
`GET /api/v1/admin/kpis/breaches`, and `GET /api/v1/review/kpi-warnings` for the caller.**

`REVIEW-FR-096` **WHEN an officer holding a live claim transfers a task, THE review module SHALL move
the claim to an active same-district `OFFICER`, keep `resolution_due_at`, refresh `claimed_at`, and
publish `ReviewTaskTransferred`.**

`REVIEW-FR-097` **THE review module SHALL expose `GET /api/v1/review/officers` from
`OfficerLookupApi.findActiveByDistrict`, excluding the caller.**

`REVIEW-FR-098` **THE review module SHALL accept bulk transfer, approve and reject (cap
`foshol.review.bulk.max-size`) with per-item success or failure in one response.**

`REVIEW-FR-099` **THE review module SHALL compute `casesThisMonth` and `casesThisYear` as the count
of `diagnosis_case` rows in the caller's district whose `created_at` falls on or after the start of
the current calendar month and year respectively in `foshol.i18n.display-zone`, converted to UTC,
and SHALL compute `casesLifetime` as the count of all `diagnosis_case` rows in that district.**

`REVIEW-FR-100` **THE review module SHALL compute `rejectionRate` as the share of district
`review_task` rows in `state = REJECTED` among rows in `state ∈ {DONE, REJECTED}`**, returning
`null` when the denominator is zero (`REVIEW-FR-075`).

`REVIEW-FR-101` **THE review module SHALL expose `GET /api/v1/admin/cases` (role `ADMIN` only),
paginated, ordered by `submitted_at` descending, restricted to the caller's district, with optional
filters `period` (`TODAY`\|`MONTH`\|`YEAR`\|`LIFETIME`, default `LIFETIME`), `state`
(`PENDING`\|`CLAIMED`\|`DONE`\|`REJECTED`\|`ALL`, default `ALL`), `kpi` (`ASSIGNMENT`\|`RESOLUTION`),
`officerId`, `cropCode`, `decisionPath`, `resubmission`, `page` and `size`.** A `kpi` filter SHALL
restrict to tasks that have a matching `kpi_breach` row. This list is not the officer queue and
SHALL NOT use `REVIEW-FR-030` ordering.

`REVIEW-FR-102` **THE review module SHALL return `OfficerQueueRow` items from `GET /api/v1/admin/cases`
so a selected row can be opened via `GET /api/v1/review/tasks/{id}`.**

`REVIEW-FR-103` **WHEN `POST /api/v1/review/tasks/bulk-reject` carries root `reasonCode` and
`messageBn`, THE review module SHALL apply them to every item that omits those fields.** Per-item
values, when present, SHALL override the root. A blank or >500-character message SHALL fail as
`REVIEW-FR-022`. The root fields SHALL NOT be treated as agronomic defaults — the caller supplies
them (`COMMON-CON-003`).

`REVIEW-FR-104` **WHEN bulk-rejecting a `PENDING` task, THE review module SHALL claim the task for
the caller and then reject it in the same per-item attempt.** A task `CLAIMED` by another officer
SHALL fail that item with `ERR_CLAIM_CONFLICT`. A terminal task SHALL fail that item with
`ERR_TASK_TERMINAL`. Single-task `POST …/reject` is unchanged and still requires a live claim.

### 4.5 The four officer actions

The officer console offers exactly four terminal actions. Three write an `advisory`; one writes a
`case_rejection`.

| Action | Diagnosis | Remedies | Note | `advisory.action` | Row written |
|---|---|---|---|---|---|
| **Approve** | the model's top-ranked disease, unchanged | the KB list for that disease, unchanged | optional | `APPROVED` | `advisory` |
| **Edit** | the model's top-ranked disease, unchanged | a modified selection | optional | `EDITED` | `advisory` |
| **Replace** | a **different** disease chosen by the officer | remedies for the chosen disease | optional | `REPLACED` | `advisory` |
| **Reject** | none | none | required Bangla message | — | `case_rejection` |

`REVIEW-FR-050` **WHEN an officer holding a live claim submits an approval whose `diseaseId` equals
the rank-1 `MODEL` candidate and whose remedy id set equals `KnowledgeQueryApi.listActiveRemedies` for
that disease, THE review module SHALL write an `advisory` row with `action = 'APPROVED'`.**

`REVIEW-FR-051` **WHEN an officer holding a live claim submits an approval whose `diseaseId` equals
the rank-1 `MODEL` candidate but whose remedy id set differs from the knowledge-base list, or which
carries an `officerNoteBn`, THE review module SHALL write an `advisory` row with
`action = 'EDITED'`.**

`REVIEW-FR-052` **WHEN an officer holding a live claim submits an approval whose `diseaseId` differs
from the rank-1 `MODEL` candidate, or where the case has no `MODEL` candidate at all, THE review
module SHALL write an `advisory` row with `action = 'REPLACED'`.**
*(An `UNDETERMINED` case has nothing for the officer to agree with, so every diagnosis on it is a
replacement. This keeps `REVIEW-FR-071`'s agreement-rate denominator honest.)*

`REVIEW-FR-053` **THE review module SHALL derive `advisory.action` itself from the submitted payload
per `REVIEW-FR-050` … `REVIEW-FR-052`, and SHALL NOT accept `action` as a client-supplied field.**
*(The label on the officer's decision is evidence. A client that could set it could flatter the
agreement rate.)*

`REVIEW-FR-054` **WHEN an `advisory` row is written, THE review module SHALL insert one
`advisory_remedy` row per selected remedy, preserving the submitted order in `display_order`.**

`REVIEW-FR-055` **WHEN an `advisory` row is written, THE review module SHALL set the associated
`review_task` to `state = DONE` and SHALL update the `p_officer_queue` row, in the same
transaction.**

`REVIEW-FR-056` **WHEN an `advisory` row is written, THE review module SHALL publish
`AdvisoryApproved` carrying the advisory id, case id, farmer id, officer id, officer name, disease
id, Bangla disease name, action and version.** *(One event for all three writing actions; the
`action` field distinguishes them. `AdvisoryRevised` is reserved for versions above 1 —
`REVIEW-FR-062`.)*

`REVIEW-FR-057` **THE review module SHALL return, on the task detail response, the knowledge-base
remedy list for the rank-1 candidate disease as `suggestedRemedies`**, obtained from
`KnowledgeQueryApi.listActiveRemedies`, so the console can pre-fill the remedy editor without a
second round trip. *(Pre-fill is a convenience. The officer's submitted selection is what is
persisted — the pre-fill is never taken as consent.)*

`REVIEW-FR-058` **IF an approval is submitted for a task the caller does not hold, or whose claim has
expired, THEN THE review module SHALL return `409` with code `ERR_CLAIM_NOT_HELD` and SHALL write no
row.**

`REVIEW-FR-059` **IF an approval or rejection is submitted for a task in `state = DONE` or
`REJECTED`, THEN THE review module SHALL return `409` with code `ERR_TASK_TERMINAL`.**

### 4.6 Advisory versioning — append-only

Clarification item 6.

`REVIEW-FR-060` **WHEN an officer revises a published advisory, THE review module SHALL insert a
**new** `advisory` row with `version` set to the previous row's `version` plus one and `supersedes_id`
set to the previous row's id.**

`REVIEW-FR-061` **THE review module SHALL retain every superseded `advisory` row and every
`advisory_remedy` row unchanged, and SHALL return the full history through
`ReviewSubmissionApi.findAdvisoryHistory(caseId)` ordered by `version` ascending.**
*(The farmer, and an auditor, can always see what they were told and when. That is the safety pitch,
and a soft-deleted or overwritten advisory would destroy it.)*

`REVIEW-FR-062` **WHEN an `advisory` row with `version > 1` is written, THE review module SHALL
publish `AdvisoryRevised` instead of `AdvisoryApproved`**, carrying the new advisory id, the
superseded advisory id, and the new version. The farmer therefore receives a **second**
notification (`NOTIFY-FR-003`).

`REVIEW-FR-063` **THE review module SHALL NOT issue an `UPDATE` or `DELETE` against `advisory` or
`advisory_remedy` after insertion.** *(`INV-AD-3`. Enforced as a unit test on the repository and
visible in the integration test, which asserts the superseded row is still byte-identical after a
revision.)*

`REVIEW-FR-064` **THE review module SHALL treat the highest-`version` advisory for a case as the
published advisory, and `ReviewSubmissionApi.findPublishedAdvisory(caseId)` SHALL return that row.**

`REVIEW-DATA-001` **THE review module SHALL write `supersedes_id` as `NULL` for `version = 1` and as
non-null for every version above 1**, matching `ck_advisory_supersedes`
(`(version = 1) = (supersedes_id IS NULL)`). A violation is a constraint failure, not a silent
inconsistency.

`REVIEW-FR-065` **IF a revision is requested for a case with no published advisory, THEN THE review
module SHALL return `409` with code `ERR_ADVISORY_NOT_FOUND`.**

`REVIEW-FR-066` `[DERIVED]` **WHEN a revision is requested, THE review module SHALL re-claim the
case's `review_task` for the requesting officer, transitioning it `DONE → CLAIMED`, and SHALL return
it to `DONE` when the new advisory version is published.**
*(Reasoning: `INV-AD-7` says an advisory may only be published by an officer holding the claim. If
revision bypassed the claim, the system would have two publication rules and only one of them
testable. Re-claiming keeps a single rule true everywhere, needs no schema change — `ck_review_state`
already permits `CLAIMED` — and means a second officer cannot revise the same advisory concurrently.
If the re-claim fails the optimistic lock, `REVIEW-FR-041` applies.)*

### 4.7 Rejection is terminal

Clarification item 5.

`REVIEW-FR-020` **WHEN an officer holding a live claim rejects a case, THE review module SHALL write
one `case_rejection` row carrying `officer_id`, a `reason_code` drawn from `ck_rejection_reason`, and
a non-blank Bangla `message_bn`, and SHALL NOT write an `advisory` row.**

`REVIEW-FR-021` **THE review module SHALL accept as `reason_code` only `BLURRY_IMAGE`, `NOT_A_CROP`,
`WRONG_CROP`, `INSUFFICIENT_DETAIL`, `INAUDIBLE_AUDIO` or `OTHER`**, and SHALL return `400` for any
other value. *(The list is `ck_rejection_reason` in `00-common.ears.md` §4.6; it is not restated as a
new enumeration here.)*

`REVIEW-FR-022` **THE review module SHALL require `message_bn` to be non-blank, NFC-normalised
(`COMMON-NFR-013`), and at most 500 characters**, and SHALL return `400` otherwise. The message is
**officer-authored**; the module supplies no default text and no template
(`COMMON-CON-003`, `CONTENT-OWNERS.md`).

`REVIEW-FR-023` **WHEN a `case_rejection` row is written, THE review module SHALL set the associated
`review_task` to `state = REJECTED`, SHALL update the `p_officer_queue` row, and SHALL publish
`CaseRejected`, all in the same transaction.**

`REVIEW-FR-024` **THE review module SHALL treat `REJECTED` as terminal: no claim, release, approval,
rejection or revision SHALL be accepted for the task thereafter, and each SHALL return `409` with
code `ERR_TASK_TERMINAL`.** *(An immutable case is a far simpler state machine for agents working in
parallel — and it means a rejected case can never quietly acquire an advisory later.)*

`REVIEW-FR-025` **THE review module SHALL expose the rejection to the farmer through
`ReviewSubmissionApi.findRejection(caseId)` and `GET /api/v1/cases/{id}/advisory`**, returning the
reason code, the officer's name and the Bangla message.

`REVIEW-FR-026` **THE review module SHALL NOT create the farmer's replacement case.** The
"submit a new case" affordance creates a fresh `diagnosis_case` carrying `parent_case_id`, and that
is owned by intake — see the resubmission requirements in `11-intake.ears.md` (`INTAKE-FR-016`,
`INTAKE-FR-017`) and the `[DERIVED]` `diagnosis_case.parent_case_id` column in `00-common.ears.md`
§4.4. Review's only involvement is `REVIEW-FR-012`, which surfaces the resubmission flag on the
queue so the officer can see the case has a history.

### 4.8 Officer symptom entry

`REVIEW-FR-080` **WHEN an officer holding a live claim adds symptom chips to a case, THE review
module SHALL call `AnalysisApi.recordOfficerSymptoms(caseId, symptomIds)` and SHALL return `204`.**

`REVIEW-FR-081` **THE review module SHALL NOT insert, update or delete a `case_symptom` row.**

*Why:* `case_symptom` has exactly one writer, the `analysis` module, and that is deliberate. The
table carries a `source` discriminator (`SPEECH|VISION|OFFICER`) and a `uq_case_symptom
(case_id, symptom_id, source)` constraint, and the analysis module is the only place that knows how
officer-supplied symptoms interact with the speech-derived ones, how the knowledge-base rescore is
triggered, and how `analysis_run` should record it. Two writers to one table means two agents
guessing at each other's conflict semantics — precisely the failure `COMMON-NFR-001` and
`REVIEW-NFR-001` exist to prevent. Review states the officer's intent; analysis owns the write.

`REVIEW-FR-082` **IF any submitted `symptomId` is not present in `KnowledgeQueryApi.listSymptoms`,
THEN THE review module SHALL return `400` with code `ERR_UNKNOWN_SYMPTOM` and SHALL make no call to
`AnalysisApi`.**

`REVIEW-FR-083` **IF symptom entry is attempted on a task the caller does not hold, THEN THE review
module SHALL return `409` with code `ERR_CLAIM_NOT_HELD`.**

### 4.9 Safety invariants

`REVIEW-SEC-001` **THE review module SHALL publish an advisory only when the publishing officer holds
a live, unexpired claim on the case's `review_task`.** *(`INV-AD-7`. This is the technical form of
"a named human decided". Without it, an advisory could be created by any authenticated officer, or by
a replayed request after a claim expired, and the safety sentence would be false.)*

`REVIEW-DATA-002` **THE review module SHALL reject an advisory whose disease is not a healthy class
and which references no remedy, with `400` and code `ERR_ADVISORY_REQUIRES_REMEDY`.**
Healthiness is read from `KnowledgeQueryApi.findDiseaseById(diseaseId).healthy()`; the module never
infers it from a disease name or code. *(A healthy diagnosis with no remedy is the correct answer. A
diseased diagnosis with no remedy is an empty advisory delivered to a farmer.)*

`REVIEW-DATA-003` **THE review module SHALL reject an advisory referencing a remedy whose
`RemedyView.diseaseId()` differs from the advisory's `diseaseId`, with `400` and code
`ERR_REMEDY_DISEASE_MISMATCH`.**

`REVIEW-DATA-004` **THE review module SHALL reject an advisory referencing a remedy of type
`CHEMICAL` whose `phiDays` is null, with `400` and code `ERR_REMEDY_PHI_MISSING`.**
The database expresses the same rule as `ck_remedy_phi` on the `remedy` table
(`type <> 'CHEMICAL' OR phi_days IS NOT NULL`, `00-common.ears.md` §4.3). This requirement is the
application-layer guard in front of it, so a farmer never sees a pesticide instruction without a
pre-harvest interval even if a future migration weakens the constraint.

`REVIEW-DATA-005` **THE review module SHALL populate `advisory.disease_id` on every advisory it
writes**, notwithstanding that the column is nullable in the schema. *(The nullable column reserves
room for a future "no diagnosis, advice only" advisory. Nothing in this build writes one.)*

`REVIEW-SEC-002` **THE review module SHALL require the role `OFFICER` or `ADMIN` on every endpoint
under `/api/v1/review/**` and on `POST /api/v1/advisories/{id}/revise`**, returning `403` otherwise
(`COMMON-SEC-011`).

`REVIEW-SEC-003` **THE review module SHALL permit a `FARMER` to read `GET /api/v1/cases/{id}/advisory`
only for a case `CaseIntakeApi.isOwnedBy` confirms is theirs, and SHALL return `404` — not `403` —
otherwise** (`COMMON-API-001`).

`REVIEW-SEC-004` **THE review module SHALL require the role `ADMIN` on `GET /api/v1/admin/stats`,
`GET /api/v1/admin/kpis`, `GET /api/v1/admin/kpis/breaches` and `GET /api/v1/admin/cases`**,
returning `403` for `OFFICER` and `FARMER`.

`REVIEW-SEC-005` **THE review module SHALL NOT include a farmer's phone number in any response,
projection row or log line** (`COMMON-SEC-001`, `COMMON-SEC-013`). `p_officer_queue` carries
`farmer_name` only.

### 4.10 Admin stats — the measurement that makes the loop real

Clarification items 19 and 20. Knowledge CRUD remains cut. The admin surface is district-scoped
stats (including time-window counts and rejection rate), KPI failure counts, a filtered case list,
and bulk reject. There is no national admin.

`REVIEW-FR-070` **THE review module SHALL compute the admin statistics by an aggregate SQL query over
`diagnosis_case`, `review_task`, `advisory` and `case_candidate`, and SHALL NOT maintain a statistics
projection table.** *(Confirms `00-common.ears.md` §4.8. Four numbers over a few thousand rows do not
justify a projection, and a stale stats table on stage is worse than a 40 ms query.)*

`REVIEW-FR-071` **THE review module SHALL compute the model-vs-officer agreement rate as the share of
published advisories whose `disease_id` equals the `disease_id` of the case's top-ranked
`case_candidate` with `source = 'MODEL'`.** Precisely:

- **Population:** for each case, the highest-`version` `advisory` row (`REVIEW-FR-064`), restricted to
  cases having at least one `case_candidate` row with `source = 'MODEL'` and `rank = 1`.
- **Numerator:** those where `advisory.disease_id = case_candidate.disease_id`.
- **Denominator:** the population size.
- Rejected cases contribute nothing — they have no advisory.
- Superseded advisory versions contribute nothing — only the current one counts.

`REVIEW-FR-072` **THE review module SHALL compute `casesToday` as the count of `diagnosis_case` rows
whose `created_at` falls on or after the start of the current day in `foshol.i18n.display-zone`,
converted to UTC.**

`REVIEW-FR-073` **THE review module SHALL compute `approvalRate` as the share of published advisories
(highest version per case) whose `action = 'APPROVED'`**, over all time.
*(`EDITED` and `REPLACED` count against it. That is the point: the number is meant to show how often
the human changed the machine's answer.)*

`REVIEW-FR-074` **THE review module SHALL compute `medianReviewMinutes` as the median of
`review_task.updated_at − review_task.created_at`, in minutes, over tasks in `state = DONE` or
`REJECTED`**, using `percentile_cont(0.5)`.

`REVIEW-FR-075` **THE review module SHALL return `null`, not zero, for `approvalRate`,
`agreementRate` or `medianReviewMinutes` when the corresponding denominator is zero**, and the
frontend renders `—`. *(A freshly reset demo database showing "0% agreement" would be a lie told at
the worst possible moment.)*

`REVIEW-FR-076` **THE review module SHALL return the values of `foshol.analysis.confidence.high` and
`foshol.analysis.confidence.low` on the stats response as read-only fields**, so the two-threshold
routing story is visible on screen (clarification item 20). Neither value is writable through any
endpoint; both are properties (`00-common.ears.md` §9.1) and both are human-supplied
(`CONTENT-OWNERS.md`).

`REVIEW-FR-077` `[DEFERRED]` **WHERE admin content management is included, THE review module SHALL
expose write endpoints for the statistics thresholds.** *Deferred per `00-common.ears.md` §1.2 — the
admin CRUD surface is cut. The seam is that the values are already properties, not code.*

**Why the agreement rate is a protected wow factor.** Every time an officer chooses **Edit** or
**Replace**, the system has recorded, with a timestamp, a named human, and a specific case, that the
model was wrong and exactly how it was wrong. That is a labelled example, and it accumulates as a
by-product of doing the work safely — no annotation campaign, no separate tool. The agreement rate is
that corpus expressed as one number, and it is the signal a future retraining effort would be driven
by. **No such work exists in this build** (`COMMON-CON-001` — there is no training infrastructure and
no training task in any form). What this build ships is the measurement, and being able to say "we
measure ourselves, here is the number, here is where the labels come from" is worth more on stage
than any accuracy figure.

### 4.11 UX obligations this module must satisfy

`REVIEW-UX-004` **THE review module SHALL return `analysisMode` (`REPLAY` or `LIVE`) on every review
task detail response, and the officer console SHALL display it on every case detail view.**
This is `COMMON-UX-001` realised in this module; `00-common.ears.md` §4.5 names this requirement by
id. *(So the team never mistakes a fixture for a live inference — especially during the
sidecar-kill demo, when both appear within a minute of each other.)*

`REVIEW-UX-002` **THE review module SHALL return, on the task detail response, the decision path, the
top-1 and top-2 confidences, the margin, the full ranked candidate list, the extracted symptoms, the
transcript and the Grad-CAM object key**, sourced from `AnalysisApi.findByCaseId`, so the console can
draw the confidence bars and the overlay without a second module call. **THE HTTP body SHALL also
carry the OpenAPI `ReviewCaseDetail` envelope** (`task`, `case`, `analysis`, `suggestedDiseaseId`,
`suggestedRemedies`, `priorAdvisory`) with `analysis.hasGradcam` true if and only if
`gradcamObjectKey` is non-null. Overlay PNG bytes remain `GET /api/v1/cases/{caseId}/gradcam`.

`REVIEW-UX-003` **THE review module SHALL return `isResubmission` and, when present, the parent case
id on task detail**, so the officer can see that they previously asked this farmer for a better
photograph.

---

## 5. API surface

Base path `/api/v1`. All responses are JSON; all errors are RFC 9457 problem documents
(`COMMON-API-002`). `401` on a missing or invalid JWT is universal and is not repeated per row.

| Method | Path | Request | Response | Authorisation | Errors beyond `401` |
|---|---|---|---|---|---|
| `GET` | `/review/queue` | `state` (`PENDING`\|`CLAIMED`\|`ALL`, default `PENDING`), `mine`, `page`, `size` | `200`, page of `OfficerQueueRow` | `OFFICER` or `ADMIN` — rows for the caller's district only (`REVIEW-FR-048`) | `400` `ERR_QUEUE_SORT_NOT_SUPPORTED` |
| `GET` | `/review/tasks/{id}` | — | `200`, OpenAPI `ReviewCaseDetail` plus flat `ReviewTaskDetailView` fields | `OFFICER`, `ADMIN` | `404` `ERR_REVIEW_TASK_NOT_FOUND` |
| `POST` | `/review/tasks/{id}/claim` | empty body | `200`, `ReviewTaskDetailView` with `claimExpiresAt` | any `OFFICER` or `ADMIN` | `409` `ERR_CLAIM_CONFLICT`, `ERR_TASK_TERMINAL` |
| `POST` | `/review/tasks/{id}/release` | empty body | `204` | **the claim holder only** | `409` `ERR_CLAIM_NOT_HELD`, `ERR_TASK_TERMINAL` |
| `POST` | `/review/tasks/{id}/approve` | `{diseaseId, remedyIds[], officerNoteBn?}` | `201` + `Location: /api/v1/cases/{caseId}/advisory`, `AdvisoryView` | **the claim holder only** | `400` `ERR_ADVISORY_REQUIRES_REMEDY`, `ERR_REMEDY_DISEASE_MISMATCH`, `ERR_REMEDY_PHI_MISSING`; `409` `ERR_CLAIM_NOT_HELD`, `ERR_TASK_TERMINAL`, `ERR_CLAIM_CONFLICT` |
| `POST` | `/review/tasks/{id}/reject` | `{reasonCode, messageBn}` | `204` | **the claim holder only** | `400` (unknown reason code, blank or >500-char message); `409` `ERR_CLAIM_NOT_HELD`, `ERR_TASK_TERMINAL` |
| `POST` | `/review/tasks/{id}/symptoms` | `{symptomIds[]}` | `204` | **the claim holder only** | `400` `ERR_UNKNOWN_SYMPTOM`; `409` `ERR_CLAIM_NOT_HELD` |
| `POST` | `/advisories/{id}/revise` | as `approve`; `{id}` is the current published advisory | `201`, `AdvisoryView` with `version = n+1`, `supersedesId = {id}` | `OFFICER` or `ADMIN`, and the re-claim of `REVIEW-FR-066` must succeed | `409` `ERR_ADVISORY_NOT_FOUND`, `ERR_CLAIM_CONFLICT`; plus every `approve` validation error |
| `GET` | `/cases/{id}/advisory` | — | `200`, `AdvisoryView` + `history[]`, or `RejectionView` | `FARMER` for their own case only — `404`, never `403` (`REVIEW-SEC-003`); `OFFICER`, `ADMIN` for the same district only | `404` when the case has neither |
| `GET` | `/admin/stats` | — | `200`, stats object (§5.2) | **`ADMIN` only** | `403` for `OFFICER` and `FARMER` |
| `GET` | `/admin/kpis` | — | `200`, assignment and resolution failure counts | **`ADMIN` only** | `403` |
| `GET` | `/admin/cases` | `period`, `state`, `kpi`, `officerId`, `cropCode`, `decisionPath`, `resubmission`, `page`, `size` | `200`, page of `OfficerQueueRow` | **`ADMIN` only** | `403` |
| `POST` | `/review/tasks/bulk-reject` | `{reasonCode?, messageBn?, items[]}` | `200`, `BulkOperationResult` | `OFFICER` or `ADMIN` | `400` `ERR_BULK_TOO_LARGE` |

`REVIEW-FR-053` forbids an `action` field on the approve and revise bodies; the server derives it.
Ordering is not expressible on the queue request (`REVIEW-FR-030`, `REVIEW-FR-031`).

### 5.1 Response shapes

`OfficerQueueRow` — `caseId, reviewTaskId, farmerName, cropCode, cropNameBn, cropNameEn,
cropNameEnFallback, districtCode, decisionPath, topDiseaseId, topDiseaseNameBn, topDiseaseNameEn,
topDiseaseNameEnFallback, topConfidence, imageCount, hasAudio, analysisMode, state, officerId,
isResubmission, requeueCount, submittedAt, slaDueAt`. Blank `*En` copies the Bangla value and sets
the matching `*Fallback` flag (`COMMON-NFR-038`). English is resolved at read time from
`KnowledgeQueryApi`; queue projection columns stay Bangla-only.

`ReviewTaskDetailView` — every `OfficerQueueRow` field, plus `analysisMode` (`REVIEW-UX-004`),
`top1Confidence`, `top2Confidence`, `margin`, `candidates[]`, `symptoms[]`, `transcriptBn`,
`asrConfidence`, `gradcamObjectKey`, `hasGradcam` (true iff `gradcamObjectKey` is non-null),
`images[]` with presigned URLs (`COMMON-SEC-016`), `audio`,
`parentCaseId`, `suggestedRemedies[]` (`REVIEW-FR-057`), `suggestedDiseaseId`, `claimedBy`,
`claimExpiresAt` (`claimedAt + foshol.review.claim.ttl`), and `publishedAdvisory` when one exists.

`GET /review/tasks/{id}` JSON is that flat view **and** the OpenAPI `ReviewCaseDetail` envelope:
`task` (`ReviewTask`), `case` (`CaseDetail`, image ids without object keys), `analysis`
(`AnalysisDetail` including `hasGradcam` and `thresholds`), `suggestedDiseaseId`,
`suggestedRemedies`, `priorAdvisory` (same object as `publishedAdvisory`). The officer console
loads overlay bytes from `GET /api/v1/cases/{caseId}/gradcam` when `analysis.hasGradcam` is true.

`AdvisoryView` includes `diseaseNameEn` / `diseaseNameEnFallback`. `RemedyRefView` includes
`titleEn`, `titleEnFallback`, `stepsEn`, `stepsEnFallback`, `dosageEn`, `dosageEnFallback`,
`rateNotesEn`, `rateNotesEnFallback`. Task `candidates[]` / `symptoms[]` carry the same
`*En` / `*Fallback` fields as analysis HTTP.

### 5.2 `GET /api/v1/admin/stats`

```json
{
  "casesToday": 0,
  "casesThisMonth": 0,
  "casesThisYear": 0,
  "casesLifetime": 0,
  "approvalRate": null,
  "medianReviewMinutes": null,
  "agreementRate": null,
  "agreementSampleSize": 0,
  "rejectionRate": null,
  "confidenceHigh": 0.75,
  "confidenceLow": 0.45
}
```

`confidenceHigh` and `confidenceLow` are read-only reflections of
`foshol.analysis.confidence.high` / `.low` (`REVIEW-FR-076`). **Authorisation:** `ADMIN` only.

---

## 6. Persistence

### 6.1 Tables owned

`review_task` · `advisory` · `advisory_remedy` · `case_rejection` · `p_officer_queue`. Defined once,
in `00-common.ears.md` §4.6 and §4.8, created by `V6__review.sql` and `V8__projections.sql`. **This
document defines no schema** (`COMMON-NFR-042`).

### 6.2 Query patterns

| Pattern | Access | Index |
|---|---|---|
| Officer queue page | read-only `DataSource` (`COMMON-ARCH-007`) | `ix_officer_queue_priority` |
| "My claimed tasks" | read-only | `ix_officer_queue_officer` |
| Task detail by id | read-only + API calls to intake/analysis/knowledge | PK |
| Claim / release / approve / reject | write `DataSource`, optimistic lock on `version` | PK |
| Sweeper scan | write, `FOR UPDATE SKIP LOCKED` | `ix_review_task_claimed` |
| SLA-overdue scan | read-only | `ix_review_task_state` |
| Current advisory for a case | read-only, `ORDER BY version DESC LIMIT 1` | `ix_advisory_case` |
| Advisory history | read-only, `ORDER BY version ASC` | `ix_advisory_case` |
| Admin stats | read-only, single aggregate query | `ix_case_candidate_case`, `ix_advisory_case` |

`REVIEW-NFR-011` **THE review module SHALL execute every read in §6.2 through the
`@ReadOnlyDataSource`-qualified `DataSource` and SHALL NOT load a JPA entity from a query handler**
(`COMMON-ARCH-007`).

`REVIEW-NFR-012` **THE review module SHALL define its JPA entities as types distinct from its domain
aggregates, mapped by hand-written static mappers** (`COMMON-NFR-010`).

---

## 7. Acceptance criteria

Each maps one-to-one onto a test method.

| Req | Given | When | Then |
|---|---|---|---|
| `REVIEW-FR-001` | a case analysed on the `PRIMARY` path | `AnalysisCompleted` is published | one `review_task` exists, `PENDING`, `priority_confidence` = the event's top-1, `sla_due_at` = `occurredAt + PT4H` |
| `REVIEW-FR-002` | a case whose analysis threw | `AnalysisFailed` is published | one `review_task` exists, `PENDING`, `priority_confidence` is null |
| `REVIEW-FR-003` | one case per decision path plus one with an open sidecar circuit plus one wholly-unmapped case | all analyses complete | `count(review_task) = count(diagnosis_case)`, and every case id appears exactly once |
| `REVIEW-FR-004` | a task already created for case X | `AnalysisCompleted` for X is republished | still exactly one `review_task` and one `p_officer_queue` row; no second event side effect |
| `REVIEW-FR-005` | `KnowledgeQueryApi` stubbed to throw | `AnalysisCompleted` is published | the `review_task` row exists; `crop_name_bn` is empty; one `WARN` was logged |
| `REVIEW-FR-030` | five queue rows with distinct `submitted_at` values | the queue is read | order is newest `submitted_at` first (`submitted_at DESC`) |
| `REVIEW-FR-031` | any officer | `GET /review/queue?sort=confidence,desc` | `400` with code `ERR_QUEUE_SORT_NOT_SUPPORTED` |
| `REVIEW-FR-040` | a `PENDING` task | officer A claims it | `CLAIMED`, `officer_id = A`, `claimed_at` set, `version` incremented, queue row updated |
| `REVIEW-FR-041` | officers A and B holding the same stale `version` | both submit a claim | one succeeds; the other receives `409 ERR_CLAIM_CONFLICT`; the task is claimed exactly once |
| `REVIEW-FR-043` | a task claimed by A 3 minutes ago | A claims again | `200`, `claimed_at` refreshed, `requeue_count` unchanged |
| `REVIEW-FR-044` | a task claimed by A | A releases it | `PENDING`, `officer_id` null, `requeue_count` unchanged |
| `REVIEW-FR-045` | a task claimed at `now − PT16M` with `foshol.review.claim.ttl=PT15M` | the sweeper runs | `PENDING`, `officer_id` null, `requeue_count` = 1, one `WARN` logged |
| `REVIEW-FR-046` | a `DONE` task with an old `claimed_at` | the sweeper runs | the task is untouched |
| `REVIEW-FR-050` | a claimed task whose rank-1 model candidate is D with KB remedies `{r1,r2}` | approve `{D, [r1,r2]}` | `advisory.action = 'APPROVED'`, task `DONE`, `AdvisoryApproved` published |
| `REVIEW-FR-051` | same | approve `{D, [r1]}` | `advisory.action = 'EDITED'` |
| `REVIEW-FR-052` | same | approve `{E, [r3]}` where E ≠ D | `advisory.action = 'REPLACED'` |
| `REVIEW-FR-053` | same | approve with `"action":"APPROVED"` in the body while the payload implies `EDITED` | the stored action is `EDITED`; the client field is ignored |
| `REVIEW-FR-058` | a task claimed by A | B approves it | `409 ERR_CLAIM_NOT_HELD`; no `advisory` row |
| `REVIEW-FR-060` | a case with advisory v1 | officer revises it | a v2 row exists with `supersedes_id = v1.id`; v1 is unchanged |
| `REVIEW-FR-061` | a case with v1 and v2 | `findAdvisoryHistory` | both rows returned, ascending by version |
| `REVIEW-FR-062` | a case with advisory v1 | a revision is published | `AdvisoryRevised` is published and `AdvisoryApproved` is not |
| `REVIEW-FR-063` | a case with v1 and v2 | any revision path runs | no `UPDATE` or `DELETE` statement touched `advisory`; v1 is byte-identical |
| `REVIEW-DATA-001` | — | an advisory with `version = 2` and null `supersedes_id` is attempted | the insert fails on `ck_advisory_supersedes` |
| `REVIEW-FR-065` | a case with no advisory | revise is called | `409 ERR_ADVISORY_NOT_FOUND` |
| `REVIEW-FR-066` | a `DONE` task | officer A revises | the task passes through `CLAIMED` and returns to `DONE`; concurrent revision by B gets `409` |
| `REVIEW-FR-020` | a claimed task | reject `{BLURRY_IMAGE, "…"}` | one `case_rejection` row; **no** `advisory` row; `CaseRejected` published |
| `REVIEW-FR-022` | a claimed task | reject with a blank `messageBn` | `400`; no row written |
| `REVIEW-FR-024` | a `REJECTED` task | approve, claim, reject or revise | each returns `409 ERR_TASK_TERMINAL` |
| `REVIEW-FR-080` | a claimed task | `POST …/symptoms` with two valid ids | `204`; `AnalysisApi.recordOfficerSymptoms` called once with those ids |
| `REVIEW-FR-081` | the module under test | any officer action | no SQL statement issued by `review` targets `case_symptom` |
| `REVIEW-FR-082` | a claimed task | `POST …/symptoms` with an unknown id | `400 ERR_UNKNOWN_SYMPTOM`; `AnalysisApi` not called |
| `REVIEW-SEC-001` | a task whose claim expired 1 minute ago | the former holder approves | `409 ERR_CLAIM_NOT_HELD` |
| `REVIEW-DATA-002` | a non-healthy disease | approve with an empty `remedyIds` | `400 ERR_ADVISORY_REQUIRES_REMEDY` |
| `REVIEW-DATA-002` | a healthy-class disease | approve with an empty `remedyIds` | `201`; the advisory is created |
| `REVIEW-DATA-003` | disease D and a remedy belonging to E | approve `{D, [remedyOfE]}` | `400 ERR_REMEDY_DISEASE_MISMATCH` |
| `REVIEW-DATA-004` | a `CHEMICAL` remedy with null `phiDays` | approve referencing it | `400 ERR_REMEDY_PHI_MISSING` |
| `REVIEW-SEC-003` | farmer F1's case | farmer F2 reads `/cases/{id}/advisory` | `404`, not `403` |
| `REVIEW-SEC-004` | an `OFFICER` token | `GET /admin/stats` | `403` |
| `REVIEW-FR-070` | any dataset | stats are read | the response is produced by one aggregate query; no stats table exists in the schema |
| `REVIEW-FR-071` | 4 cases with a rank-1 `MODEL` candidate; advisories agree on 3 | stats are read | `agreementRate = 0.75`, `agreementSampleSize = 4` |
| `REVIEW-FR-071` | a case with a v1 disagreeing and a v2 agreeing | stats are read | only v2 counts; the case is an agreement |
| `REVIEW-FR-075` | an empty database | stats are read | `approvalRate`, `agreementRate`, `medianReviewMinutes`, `rejectionRate` are `null`; `casesToday` is `0` |
| `REVIEW-FR-076` | properties `high=0.75`, `low=0.45` | stats are read | `confidenceHigh = 0.75`, `confidenceLow = 0.45` |
| `REVIEW-FR-099` | cases created last month and this month | stats are read | `casesThisMonth` counts only this month in the display zone |
| `REVIEW-FR-100` | two `DONE` and one `REJECTED` task in district | stats are read | `rejectionRate = 1/3` |
| `REVIEW-FR-101` | cases in two districts | admin in district A lists cases | only district A rows, newest `submitted_at` first |
| `REVIEW-FR-103` | bulk-reject with root message and items omitting `messageBn` | reject | each success uses the root message |
| `REVIEW-FR-104` | a `PENDING` task | admin bulk-rejects it | the task is claimed then `REJECTED` |
| `REVIEW-UX-004` | a case analysed in replay mode | task detail is read | `analysisMode = "REPLAY"` |

---

## 8. Test requirements

Honouring the test floor in `00-common.ears.md` §11 and clarification item 21.

### 8.1 Unit tests — domain invariants

One test class per aggregate, no Spring context.

- `ReviewTaskTest` — `INV-RT-1` … `INV-RT-6`, every legal and every illegal transition of the state
  machine in §3.1, claim expiry arithmetic against a fixed `Clock`.
- `AdvisoryTest` — `INV-AD-1` … `INV-AD-7`, version/supersedes pairing, append-only enforcement.
- `CaseRejectionTest` — `INV-CR-1` … `INV-CR-3`.
- `SpecificationsTest` — each specification in §3.4 with a passing and a failing case.

### 8.2 Unit tests — every command and query handler

Mockito doubles for `CaseIntakeApi`, `AnalysisApi`, `KnowledgeQueryApi`, `OfficerLookupApi`,
`FarmerLookupApi` and the repositories. Handlers: `ClaimReviewTask`, `ReleaseReviewTask`,
`ApproveCase`, `RejectCase`, `ReviseAdvisory`, `RecordOfficerSymptoms`, `SweepExpiredClaims`;
queries: `OfficerQueue`, `ReviewTaskDetail`, `CaseAdvisory`, `AdminStats`, `AdminCases`.

`REVIEW-NFR-020` **THE module SHALL contain one unit test class per command handler and per query
handler, each covering the happy path and every error branch that handler can produce.**

### 8.3 Integration test — exactly one, Testcontainers

`REVIEW-NFR-021` **THE module SHALL contain exactly one Testcontainers integration test,
`ReviewTaskUniversalityIT`**, running against `pgvector/pgvector:pg17` with the real Flyway
migrations. It is the module's primary happy path **and** the proof of `REVIEW-FR-003`:

1. Publish `AnalysisCompleted` for a `PRIMARY` case, a `SECONDARY` case and an `UNDETERMINED` case.
2. Publish `AnalysisCompleted` with an `error_code` set, simulating the open-circuit path
   (`COMMON-NFR-035`).
3. Publish `AnalysisFailed` for a fifth case.
4. Assert `count(review_task) = 5` and that every case id appears exactly once.
5. Read the queue and assert the order of `REVIEW-FR-030`, newest `submitted_at` first.
6. Claim, approve with an edited remedy set, assert `action = 'EDITED'`, task `DONE`,
   `AdvisoryApproved` recorded in the Modulith event publication registry.
7. Revise; assert v2, `supersedes_id`, v1 unchanged, `AdvisoryRevised` recorded.
8. Reject a different case; assert `case_rejection` written, no advisory, task `REJECTED`, and that a
   subsequent approve returns `409`.
9. Expire a claim by moving the test `Clock`, run the sweeper, assert `PENDING` and
   `requeue_count = 1` (`foshol.review.sweeper.interval=PT5S` under the `test` profile).
10. Read `/admin/stats` and assert the agreement rate against the fixture's known answer.

### 8.4 Fixtures

| Fixture | Contents |
|---|---|
| `ReviewFixtures.analysisCompleted(path, top1)` | a valid `AnalysisCompleted` for each decision path |
| `ReviewFixtures.officer()` / `.secondOfficer()` | two fictional officers (personas are exempt from `COMMON-CON-003`) |
| Knowledge stub | one non-healthy disease with a `CULTURAL` and a `CHEMICAL` remedy carrying `phiDays`, one `CHEMICAL` remedy with null `phiDays`, one healthy-class disease, one remedy belonging to a *different* disease |
| Mutable `Clock` | a `TestClock` bean so claim TTL and SLA are testable without sleeping |

`REVIEW-NFR-022` **THE module SHALL obtain the current instant from an injected `Clock` bean and
SHALL NOT call `Instant.now()` or `LocalDateTime.now()` directly.** *(Otherwise the claim TTL and the
SLA are untestable without wall-clock sleeps, and a four-day build cannot afford flaky time tests.)*

`REVIEW-NFR-023` **THE module SHALL author no agronomic content in any fixture** — no disease
description, no remedy text, no dosage, no PHI value beyond an arbitrary integer used to exercise
`REVIEW-DATA-004` (`COMMON-CON-003`).

---

## 9. Agent execution notes

### 9.1 Preconditions

A5 does not start until: `00-common.ears.md` is frozen; `V6__review.sql` and `V8__projections.sql`
have been applied by A1; the `api` packages of `intake`, `analysis`, `knowledge` and `identity` exist
with the §6.2 signatures compiling (stub bodies are sufficient); and the error-code constants of
`REVIEW-NFR-003` are in `common.ErrorCodes`.

### 9.2 Implementation order

1. `api/` — `ReviewSubmissionApi`, `AdvisoryView`, `RemedyRefView`, `RejectionView`,
   `AdvisoryApproved`, `AdvisoryRevised`, `CaseRejected`. **Publish this first**; A5's notification
   work and A6's frontend both block on it.
2. `domain/` — `ReviewTask`, `Advisory`, `AdvisoryRemedy`, `CaseRejection`, the enums (imported from
   `common`), the specifications of §3.4, the domain exceptions. No Spring
   (`COMMON-ARCH-004`). Write `ReviewTaskTest` and `AdvisoryTest` alongside.
3. `infrastructure/` — JPA entities, repositories, hand-written mappers.
4. `infrastructure/AnalysisEventListener` — `REVIEW-FR-001` … `REVIEW-FR-006`, `REVIEW-FR-010` …
   `REVIEW-FR-013`. **This is the safety invariant; do it before any endpoint.**
5. `application/query/` — `OfficerQueueQueryHandler` (`REVIEW-FR-030`),
   `ReviewTaskDetailQueryHandler`.
6. `application/command/` — claim, release, approve, reject in that order; then revise
   (`REVIEW-FR-060` … `REVIEW-FR-066`); then symptoms.
7. `infrastructure/ClaimSweeper` — `@Scheduled(fixedDelayString = "${foshol.review.sweeper.interval}")`.
8. `application/query/AdminStatsQueryHandler` — `REVIEW-FR-070` … `REVIEW-FR-076`, `REVIEW-FR-099`,
   `REVIEW-FR-100`. `AdminCasesQueryHandler` — `REVIEW-FR-101`, `REVIEW-FR-102`.
9. `web/` — the ten controllers of §5, thin (`COMMON-ARCH-009`).
10. `ReviewTaskUniversalityIT` last.

### 9.3 Traps

- **Do not** write `case_symptom`, `diagnosis_case.status`, or any `identity` or `knowledge` table.
- **Do not** update an `advisory` row. Ever. A revision is an insert.
- **Do not** accept `action` from the client (`REVIEW-FR-053`).
- **Do not** add a `sort` parameter to the queue because the frontend asked for one — raise a blocker
  and point at `REVIEW-FR-030`.
- **Do not** add a Flyway migration (`COMMON-NFR-042`). If you need a column, you have misread §4.6.
- **Do not** author Bangla rejection text, disease text or remedy text (`COMMON-CON-003`).
- Reconcile the forward references `INTAKE-FR-016` / `INTAKE-FR-017` in `REVIEW-FR-026` against
  `11-intake.ears.md` at the Day-1 checkpoint; if intake numbered them differently, this document is
  corrected, not intake.

### 9.4 Local Definition of Done

`00-common.ears.md` §11 in full, plus:

1. `ReviewTaskUniversalityIT` green, including step 4's `count(review_task) = count(diagnosis_case)`.
2. Every requirement in §4 implemented or listed as `[DEFERRED]` in `docs/progress/a5.md`.
3. All ten endpoints match `docs/openapi/foshol-api.yaml` exactly.
4. No SQL issued by this module writes to a table outside §6.1 — asserted by an ArchUnit-style check
   on the repository interfaces.
5. `/admin/stats` returns four numbers and two thresholds against the `demo` seed data, and the
   agreement rate is not null.

`REVIEW-FR-090` … `REVIEW-FR-098` (working-hours assignment/resolution KPIs, district KPI dashboard,
same-district transfer, bulk transfer/approve/reject) are specified in the Day-N additive contract
and implemented in `V108` and the review/notification modules.

---

## 10. Requirement index

| Category | IDs | Count |
|---|---|---|
| Functional | `REVIEW-FR-001` … `-006`, `-010` … `-016`, `-020` … `-026`, `-030` … `-033`, `-040` … `-049`, `-050` … `-059`, `-060` … `-066`, `-070` … `-077`, `-080` … `-083`, `-090` … `-104` | 84 |
| Data | `REVIEW-DATA-001` … `REVIEW-DATA-005` | 5 |
| Security | `REVIEW-SEC-001` … `REVIEW-SEC-005` | 5 |
| Non-functional | `REVIEW-NFR-001` … `-003`, `-010` … `-012`, `-020` … `-023` | 10 |
| UX | `REVIEW-UX-001` … `REVIEW-UX-004` | 4 |
| **Total** | | **87** |

`[DEFERRED]`: `REVIEW-FR-077` (threshold write endpoints).
`[DERIVED]`: `REVIEW-NFR-002` (`FarmerLookupApi` for the projection's farmer name),
`REVIEW-FR-066` (revision re-claims the task).
