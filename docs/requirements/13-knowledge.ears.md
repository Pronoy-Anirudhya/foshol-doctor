# 13 — Knowledge (EARS)

**Module:** `com.rootcause.foshol.knowledge` · **Gradle project:** `modules/knowledge`
**Owning agent:** A4 · **Requirement prefix:** `KNOWLEDGE`

> Read `00-common.ears.md` first and treat it as immutable. This document references that contract and
> never redefines it. Where the two disagree, `00-common.ears.md` wins.
> Traces to plan §2.2 (`knowledge` module), §4 (schema), §5 (F5/F6), §7.2 (the retrieval pipeline),
> and to clarification items 10, 19 and 24 of the approved plan.

`knowledge` is the **authoritative safety counterweight to the AI**. The project's safety argument —
*"the AI can be wrong, the knowledge base is authoritative"* (plan §2.2) — only holds because the
knowledge-base lookup lives here, in a module that has no model, no HTTP client and no ability to
invent a fact. Everything this module returns is human-supplied content or a deterministic function
of human-supplied content.

---

## 1. Scope

### 1.1 What this module owns

| Owned | Detail |
|---|---|
| Reference taxonomy | `crop`, `disease`, `symptom`, `symptom_phrase` |
| Matching weights | `disease_symptom` |
| Advice content | `remedy` |
| Model taxonomy mapping | `model_label_map` (read side of `COMMON-DATA-010` … `COMMON-DATA-017`) |
| Published read API | `KnowledgeQueryApi` (`00-common.ears.md` §6.2) |
| Published matcher | `SymptomMatchApi` — feature **F5**, the whole of it |
| Content validation | The startup gate that refuses to run against an incomplete knowledge base |
| Read endpoints | `GET /api/v1/crops`, `/crops/{id}/diseases`, `/diseases/{id}`, `/diseases/{id}/remedies`, `/symptoms` |

### 1.2 What this module does **not** own

| Not owned | Owner | Where specified |
|---|---|---|
| Image classification, ASR, orchestration, confidence routing, decision paths | `analysis` | `12-analysis.ears.md` |
| Computing a text embedding at request time | the inference sidecar, reached **only** by `analysis` | `60-inference-sidecar.ears.md` |
| `diagnosis_case`, `case_image`, `case_audio`, idempotency | `intake` | `11-intake.ears.md` |
| `case_symptom`, `case_candidate`, `analysis_run`, `unmapped_labels` | `analysis` | `12-analysis.ears.md` |
| Advisories, the officer queue, remedy *selection* for a case | `review` | `14-review.ears.md` |
| Farmers, officers, JWT, roles | `identity` | `10-identity.ears.md` |
| Authoring any disease name, remedy, dosage, PHI value, citation or Bangla phrase | **humans** | `CONTENT-OWNERS.md`, `COMMON-CON-003` |

> **The boundary that matters most.** `analysis` decides *what to do*; `knowledge` answers *what is
> true according to the curated knowledge base*. `knowledge` never learns, never calls a model, and
> never writes a row outside a Flyway migration.

---

## 2. Dependencies

### 2.1 Interfaces this module calls

**None.** Per the permitted dependency matrix (`00-common.ears.md` §6.1) the `knowledge` row carries a
`✓` against `common` only. `modules/knowledge/build.gradle` declares a dependency on `:common` and on
nothing else.

### 2.2 Events consumed

**None.**

### 2.3 Events published

**None.** Reference data does not change at runtime, so there is nothing to announce.

### 2.4 Interfaces this module publishes

Quoted verbatim from `00-common.ears.md` §6.2 and **FROZEN** under `COMMON-NFR-041`.

```java
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
```

### 2.5 Consumers

`intake` and `analysis` call `KnowledgeQueryApi`; `analysis` calls `SymptomMatchApi`; `review` calls
`KnowledgeQueryApi`. This module makes no assumption about any of them beyond the signatures above.

### 2.6 Error codes used

Declared as constants in `common.ErrorCodes` (A1-owned, §12.1) and referenced, never literal
(`COMMON-ARCH-010`): `ERR_CROP_NOT_FOUND`, `ERR_DISEASE_NOT_FOUND`, `ERR_EMBEDDING_DIMENSION`,
`ERR_KB_CONTENT_INVALID`, `ERR_KB_EMBEDDING_MISSING`, `ERR_MODEL_LABEL_MAP_MISSING`,
`ERR_MODEL_LABEL_MAP_INVALID`.

---

## 3. Domain model

The knowledge base is **reference data, not a lifecycle**. There is no aggregate with a state machine
here; there are immutable entities and one pure function. The interesting domain object is the
matcher.

### 3.1 Entities and value objects

| Type | Kind | Identity | Notes |
|---|---|---|---|
| `Crop` | entity | `id` | `code` is unique and stable; ordered by `display_order` |
| `Disease` | entity | `id` | unique per `(crop_id, code)`; `healthy` diseases carry no weights and no remedies |
| `Symptom` | entity | `id` | `code` unique; `organ` from the `ck_symptom_organ` set |
| `SymptomPhrase` | entity | `id` | belongs to one `Symptom`; unique per `(symptom_id, normalised_bn)` |
| `DiseaseSymptomWeight` | value object | `(diseaseId, symptomId)` | `weight ∈ (0, 1]` |
| `Remedy` | entity | `id` | belongs to one `Disease` |
| `ModelLabelMapping` | value object | `(modelId, modelVersion, rawLabel)` | resolves to one `diseaseId` |
| `NormalisedText` | value object | — | the output of `KNOWLEDGE-FR-017`; carries its token set |
| `SymptomMatch` | value object | `symptomId` | `score`, `matcher ∈ {VECTOR, FUZZY, MANUAL}` |
| `DiseaseScore` | value object | `diseaseId` | normalised score, rank |

### 3.2 Invariants

| # | Invariant | Owner | Enforced by |
|---|---|---|---|
| I1 | A `disease_symptom` weight is greater than 0 and at most 1 | `DiseaseSymptomWeight` | `ck_disease_symptom_weight` + constructor |
| I2 | A `CHEMICAL` remedy has a non-null `phi_days` | `Remedy` | `ck_remedy_phi` (database) — **not** duplicated in Java, see `KNOWLEDGE-DATA-010` |
| I3 | A remedy has a non-blank `source_ref` | `Remedy` | `KNOWLEDGE-DATA-009` startup validation |
| I4 | A non-healthy disease has at least one active remedy | knowledge base as a whole | `KNOWLEDGE-DATA-007` |
| I5 | A non-healthy disease has at least one `disease_symptom` weight | knowledge base as a whole | `KNOWLEDGE-DATA-008` |
| I6 | A healthy disease carries no weights and no remedies, and is never a scored candidate | `DiseaseScore` | `KNOWLEDGE-FR-029` |
| I7 | Every stored embedding has exactly 768 dimensions | `SymptomPhrase`, `Symptom` | `vector(768)` column + `COMMON-NFR-006` |
| I8 | A symptom match score lies in `(0, 1]` and is rounded to 3 decimal places | `SymptomMatch` | `KNOWLEDGE-FR-024` |
| I9 | A disease score lies in `[0, 1]` and is rounded to 4 decimal places | `DiseaseScore` | `KNOWLEDGE-FR-031` |
| I10 | Every `model_label_map` row resolves to an existing, non-deleted disease | `ModelLabelMapping` | `COMMON-DATA-015` via `KNOWLEDGE-DATA-013` |
| I11 | No row of any owned table is written outside a Flyway migration | all | `KNOWLEDGE-DATA-002` |

### 3.3 Specifications

| Specification | Predicate |
|---|---|
| `ActiveRemedySpec` | `active = true AND deleted_at IS NULL` |
| `LiveContentSpec` | `deleted_at IS NULL` — applied to every read of `crop`, `disease`, `symptom`, `symptom_phrase`, `remedy` |
| `ScorableDiseaseSpec` | `LiveContentSpec AND is_healthy = false AND crop_id = :cropId` |
| `VectorHitSpec` | `1 - (embedding <=> :q) >= foshol.knowledge.match.vector-threshold` |
| `FuzzyHitSpec` | `overlap(phrase, transcript) >= foshol.knowledge.match.fuzzy-threshold` |

---

## 4. Requirements

### 4.1 The symptom matcher — feature F5

This is the module's core and the demo's money shot (plan §11, demo beat 2). The **entire** pipeline
runs inside this module. Everything below is deterministic: the same request against the same
database yields byte-identical output.

#### 4.1.1 Entry, validation and orchestration

`KNOWLEDGE-FR-010` **WHEN `SymptomMatchApi.match` is called, THE knowledge module SHALL execute, in
order, symptom retrieval (`KNOWLEDGE-FR-012`, `KNOWLEDGE-FR-014`), deduplication
(`KNOWLEDGE-FR-016`), officer-symptom admission (`KNOWLEDGE-FR-017`) and weighted disease scoring
(`KNOWLEDGE-FR-018`), and SHALL return a `SymptomMatchResult`.**

`KNOWLEDGE-FR-011` **IF `SymptomMatchRequest.cropId` is null or does not resolve to a non-deleted
`crop` row, THEN THE knowledge module SHALL reject the call with `ERR_CROP_NOT_FOUND` and SHALL
execute no query against `symptom_phrase`.**

`KNOWLEDGE-FR-012` **IF `SymptomMatchRequest.transcriptEmbedding` is non-null and its length is not
768, THEN THE knowledge module SHALL reject the call with `ERR_EMBEDDING_DIMENSION`.**
*(`COMMON-NFR-006`: a vector of the wrong width means the caller is using a different embedding model
from the one that produced the stored vectors, and any similarity computed from it is meaningless.)*

`KNOWLEDGE-NFR-001` **THE knowledge module SHALL NOT declare a dependency on any HTTP client, SHALL
NOT call the inference sidecar, and SHALL NOT compute an embedding.** The caller supplies
`transcriptEmbedding`; `analysis` obtains it from the sidecar's `/v1/embed` endpoint. *(Clarification
item 10: the sidecar gets no database credentials and `knowledge` gets no network. One owner per
concern is what lets A3 and A4 work in parallel.)*

#### 4.1.2 Layer 1 — pgvector HNSW cosine kNN

`KNOWLEDGE-FR-013` **WHILE `SymptomMatchRequest.transcriptEmbedding` is non-null, THE knowledge
module SHALL execute exactly one nearest-neighbour query against `symptom_phrase.embedding` using the
pgvector cosine distance operator `<=>`, limited to `foshol.knowledge.match.knn-limit` rows.**

The statement shape is fixed, because only this shape uses `ix_symptom_phrase_embedding`:

```sql
SELECT sp.id            AS phrase_id,
       sp.symptom_id    AS symptom_id,
       1 - (sp.embedding <=> :queryVector) AS similarity
FROM   symptom_phrase sp
JOIN   symptom s ON s.id = sp.symptom_id
WHERE  sp.embedding  IS NOT NULL
  AND  sp.deleted_at IS NULL
  AND  s.deleted_at  IS NULL
ORDER  BY sp.embedding <=> :queryVector
LIMIT  :knnLimit;
```

`:queryVector` is bound as a `com.pgvector.PGvector` (catalog entry `com.pgvector:pgvector` 0.1.6).

> **Why `1 - distance`.** `<=>` is pgvector's **cosine distance** operator, defined as
> `1 - cosine_similarity`. Cosine **similarity** is therefore `1 - (a <=> b)`, and lies in `[0, 2]`
> reduced to `[0, 1]` for the non-negative embeddings LaBSE produces. Every threshold in this document
> is expressed as a **similarity**, never as a distance.
>
> **Why the filter is not in `WHERE`.** An HNSW index is only used for an `ORDER BY <distance
> operator> … LIMIT n` query. Writing `WHERE 1 - (embedding <=> :q) >= 0.72` would force a sequential
> scan and silently change the demo's latency profile. The threshold is therefore applied *after*
> the `LIMIT`, in Java. Note also that `knn-limit` (25) must stay below pgvector's session
> `hnsw.ef_search` default (40) or recall degrades; the module does not set `ef_search`.

`KNOWLEDGE-FR-014` **THE knowledge module SHALL retain a kNN row as a `VECTOR` match only IF its
similarity is greater than or equal to `foshol.knowledge.match.vector-threshold`, and SHALL discard
every other returned row.**

`KNOWLEDGE-FR-015` **IF `SymptomMatchRequest.transcriptEmbedding` is null, THEN THE knowledge module
SHALL skip the vector layer entirely and SHALL proceed with the fuzzy layer alone.** *(An image-only
case, or a case whose audio branch missed its deadline under `COMMON-NFR-030`, still gets a
knowledge-base opinion.)*

#### 4.1.3 Layer 2 — the fuzzy keyword layer

Belt and braces (plan §7.2, "add a fuzzy keyword layer underneath so an embedding miss still catches
the obvious cases"). It is a pure function over in-memory data and needs no database round trip.

`KNOWLEDGE-FR-016` **WHEN the application starts, THE knowledge module SHALL load every non-deleted
`symptom_phrase` row as the tuple `(id, symptomId, normalisedBn)` into an immutable in-memory index,
and SHALL NOT reload it thereafter.** *(Legal because the table is written only by Flyway —
`KNOWLEDGE-DATA-002`. It makes the fuzzy layer unit-testable without a database and keeps the whole
match to at most two SQL statements, `KNOWLEDGE-NFR-003`.)*

`KNOWLEDGE-FR-017` **THE knowledge module SHALL normalise `SymptomMatchRequest.transcriptBn` with the
normalisation of `COMMON-NFR-013` before fuzzy matching, applying exactly these steps in this order:**

1. Unicode **NFC** normalisation.
2. Removal of every `U+200C` (ZWNJ) and `U+200D` (ZWJ) code point.
3. Folding of Bengali digits `U+09E6`–`U+09EF` to ASCII `0`–`9`.
4. Replacement of every Unicode punctuation or symbol code point — including the Bengali danda
   `U+0964` and double danda `U+0965` — with a single space `U+0020`.
5. Lowercasing (affects embedded Latin only; Bangla has no case).
6. Collapsing of every whitespace run to one `U+0020`, then trimming.

`KNOWLEDGE-FR-018` **THE knowledge module SHALL implement that normalisation as a pure static function
in its `domain` package, and the same function SHALL be the definition against which
`symptom_phrase.normalised_bn` is verified by `KNOWLEDGE-DATA-012`.** *(One definition, verified
against the migration, so a normalisation change cannot silently desynchronise code from data.)*

`KNOWLEDGE-FR-019` **THE knowledge module SHALL compute the fuzzy overlap of a stored phrase against
the transcript as**

> Let `T` be the set of distinct tokens obtained by splitting the normalised transcript on `U+0020`.
> Let `P` be the set of distinct tokens obtained by splitting `symptom_phrase.normalised_bn` on
> `U+0020`.
>
> ```
> overlap(P, T) = |P ∩ T| / |P|
> ```
>
> `overlap` is 0 when `P` is empty.

*(The denominator is `|P|`, not `|P ∪ T|`. A colloquial three-word phrase fully present inside a
forty-word transcript is a complete hit and must score 1.0; Jaccard would score it 0.07 and the layer
would never fire.)*

`KNOWLEDGE-FR-020` **THE knowledge module SHALL retain a phrase as a `FUZZY` match only IF
`overlap(P, T)` is greater than or equal to `foshol.knowledge.match.fuzzy-threshold`.**

#### 4.1.4 Deduplication

`KNOWLEDGE-FR-021` **THE knowledge module SHALL reduce all `VECTOR` and `FUZZY` hits to at most one
entry per `symptom_id`, whose `score` is the maximum score across that symptom's hits and whose
`matcher` is the layer that produced that maximum.**

`KNOWLEDGE-FR-022` **IF a symptom's maximum score is produced by both layers at equal value, THEN THE
knowledge module SHALL record `matcher = 'VECTOR'`.** *(A deterministic tie-break; the demo must be
reproducible.)*

`KNOWLEDGE-FR-023` **THE knowledge module SHALL order deduplicated matches by `score` descending,
breaking ties by `symptom.code` ascending, and SHALL retain at most
`foshol.knowledge.match.max-symptoms` of them.**

`KNOWLEDGE-FR-024` **THE knowledge module SHALL round every `MatchedSymptom.score` to 3 decimal places
using `RoundingMode.HALF_UP`.** *(So the value fits `case_symptom.score numeric(4,3)` without the
caller re-rounding and changing the number.)*

#### 4.1.5 Officer-supplied symptoms

`KNOWLEDGE-FR-025` **`[DERIVED]` THE knowledge module SHALL admit every id in
`SymptomMatchRequest.officerSymptomIds` that resolves to a non-deleted `symptom` row as a match with
`score = 1.000` and `matcher = 'MANUAL'`, and SHALL exempt those matches from the
`foshol.knowledge.match.max-symptoms` cap.** *(The frozen contract carries `officerSymptomIds` but the
plan never says how they score. An officer asserting a symptom by hand is ground truth, so it enters
at 1.000; and a limit that could silently discard an officer's own input would be a safety defect.
`MANUAL` is already an allowed value of `ck_case_symptom_matcher`.)*

`KNOWLEDGE-FR-026` **IF an officer-supplied symptom was also matched by the vector or fuzzy layer,
THEN THE knowledge module SHALL keep the `MANUAL` entry and SHALL discard the automatic one.**

`KNOWLEDGE-FR-027` **IF an id in `officerSymptomIds` does not resolve to a non-deleted `symptom` row,
THEN THE knowledge module SHALL ignore that id and SHALL log a `WARN` carrying the correlation id.**

#### 4.1.6 Weighted scoring over `disease_symptom`

`KNOWLEDGE-FR-028` **THE knowledge module SHALL score every candidate disease as the weighted sum of
its matched symptom scores, normalised by the total weight of that disease:**

> Let `M` be the final matched-symptom set, `m(s)` the score of symptom `s` in `M`, and `w(d, s)` the
> `disease_symptom.weight` of symptom `s` for disease `d`. Let `S(d)` be every symptom that has a
> `disease_symptom` row for `d`.
>
> ```
>              Σ  over s ∈ S(d) ∩ M   of   w(d, s) × m(s)
> score(d) =  ───────────────────────────────────────────
>                    Σ  over s ∈ S(d)   of   w(d, s)
> ```

*(The denominator is the disease's **total** weight, not the matched weight. Normalising by the total
makes scores comparable across diseases with different numbers of symptoms: a disease with two
symptoms of which both matched scores 1.0, and a disease with ten symptoms of which two matched does
not. Normalising by the matched weight instead would score both 1.0 and would rank a one-symptom
coincidence above a nine-symptom agreement.)*

`KNOWLEDGE-FR-029` **THE knowledge module SHALL restrict scoring to diseases satisfying
`ScorableDiseaseSpec` — `crop_id` equal to `SymptomMatchRequest.cropId`, `deleted_at IS NULL`, and
`is_healthy = false`.**

`KNOWLEDGE-FR-030` **THE knowledge module SHALL execute scoring as exactly one SQL statement of this
shape**, with the matched symptoms passed as a `VALUES` list:

```sql
WITH matched(symptom_id, score) AS (VALUES (?::uuid, ?::numeric) /* … */)
SELECT d.id, d.code, d.name_bn,
       SUM(ds.weight * COALESCE(m.score, 0)) / SUM(ds.weight) AS score
FROM   disease d
JOIN   disease_symptom ds ON ds.disease_id = d.id
LEFT   JOIN matched m ON m.symptom_id = ds.symptom_id
WHERE  d.crop_id = :cropId
  AND  d.deleted_at IS NULL
  AND  d.is_healthy = false
GROUP  BY d.id, d.code, d.name_bn
HAVING SUM(ds.weight * COALESCE(m.score, 0)) > 0
ORDER  BY score DESC, d.code ASC;
```

`KNOWLEDGE-FR-031` **THE knowledge module SHALL assign `ScoredDisease.rank` from 1 in the order
produced by `KNOWLEDGE-FR-030`, and SHALL round `ScoredDisease.score` to 4 decimal places using
`RoundingMode.HALF_UP`.** *(4 places so the value fits `case_candidate.confidence numeric(5,4)`
unchanged.)*

`KNOWLEDGE-FR-032` **THE knowledge module SHALL exclude from `SymptomMatchResult.diseases` every
disease whose score is 0.**

#### 4.1.7 Inconclusiveness — the trigger for `UNDETERMINED`

`KNOWLEDGE-FR-033` **IF no symptom matched, THEN THE knowledge module SHALL set
`SymptomMatchResult.inconclusive = true` and SHALL return empty `symptoms` and `diseases` lists.**

`KNOWLEDGE-FR-034` **IF the rank-1 disease score is below
`foshol.knowledge.match.inconclusive-score-min`, THEN THE knowledge module SHALL set
`SymptomMatchResult.inconclusive = true` while still returning the scored diseases.** *(Analysis reads
this flag and routes the case to `UNDETERMINED` — plan §5's third decision path. The scores are still
returned so the officer console can show what the knowledge base thought, even when it was not
confident enough to pre-fill anything.)*

`KNOWLEDGE-FR-035` **WHEN a match returns `inconclusive = true`, THE knowledge module SHALL log one
`WARN` line carrying the correlation id, the crop id and the count of matched symptoms.**

`KNOWLEDGE-FR-036` **THE knowledge module SHALL produce identical output for identical input**, with
no dependence on wall-clock time, iteration order of a hash-based collection, or database row order
beyond the `ORDER BY` clauses stated above.

`KNOWLEDGE-FR-037` **THE `SymptomMatchApi` implementation SHALL perform no write of any kind**, and
SHALL execute against the `@ReadOnlyDataSource`-qualified `DataSource` (`COMMON-ARCH-007`).

### 4.2 Read APIs

`KNOWLEDGE-FR-040` **THE knowledge module SHALL return from `listCrops()` every `crop` row satisfying
`LiveContentSpec`, ordered by `display_order` ascending then `code` ascending.**

`KNOWLEDGE-FR-041` **THE knowledge module SHALL return from `findCropById` and `findCropByCode` the
matching non-deleted `crop`, and `Optional.empty()` when there is none.**

`KNOWLEDGE-FR-042` **THE knowledge module SHALL return from `findDiseaseById` the matching non-deleted
`disease`, and `Optional.empty()` when there is none.**

`KNOWLEDGE-FR-043` **THE knowledge module SHALL return from `listDiseasesByCrop` every non-deleted
`disease` of that crop, ordered by `is_healthy` ascending then `code` ascending.** *(Healthy last: it
is a taxonomy member, not a diagnosis anyone is looking for.)*

`KNOWLEDGE-FR-044` **THE knowledge module SHALL return from `listActiveRemedies` every `remedy` of
that disease satisfying `ActiveRemedySpec` — `active = true AND deleted_at IS NULL` — ordered by
`display_order` ascending then `id` ascending.**

`KNOWLEDGE-FR-045` **THE knowledge module SHALL parse `remedy.steps_bn` from its `jsonb` array into
`RemedyView.stepsBn` preserving element order**, and SHALL never return `null` for that field.

`KNOWLEDGE-FR-046` **THE knowledge module SHALL return from `listSymptoms()` every non-deleted
`symptom` row as a `SymptomRefView`, ordered by `code` ascending, and SHALL NOT include the embedding
in the view.**

`KNOWLEDGE-FR-047` **IF a caller passes a null identifier to any `KnowledgeQueryApi` lookup, THEN THE
knowledge module SHALL return `Optional.empty()` or an empty list and SHALL NOT throw.**

`KNOWLEDGE-FR-048` **THE knowledge module SHALL resolve `resolveModelLabel(modelId, modelVersion,
rawLabel)` to the `model_label_map.disease_id` whose `(model_id, model_version, raw_label)` matches
exactly, comparing all three case-sensitively, and SHALL return `Optional.empty()` when there is no
such row.** *(`COMMON-DATA-010`. Case-sensitive because a model's label space is a literal set of
strings, and a case-insensitive match could silently collide two distinct labels.)*

`KNOWLEDGE-FR-049` **WHEN the application starts, THE knowledge module SHALL load the whole of
`model_label_map` into an immutable in-memory map keyed by `(model_id, model_version, raw_label)`, and
`resolveModelLabel` SHALL be served from that map with no database round trip.** *(Legal because
`COMMON-DATA-011` forbids runtime writes; and this call happens once per candidate per case on the
critical path.)*

### 4.3 `[DEFERRED]` — admin write surface

Cut candidate #1 (clarification item 19, `00-common.ears.md` §1.2). The seam is kept visible here so
that adding it later is an additive change, not a redesign.

`KNOWLEDGE-API-020` **`[DEFERRED]` WHERE the admin content API is included, THE knowledge module SHALL
expose `POST /api/v1/admin/crops`, `PUT /api/v1/admin/crops/{id}` and `DELETE
/api/v1/admin/crops/{id}`.**

`KNOWLEDGE-API-021` **`[DEFERRED]` WHERE the admin content API is included, THE knowledge module SHALL
expose `POST`, `PUT` and `DELETE` on `/api/v1/admin/diseases`.**

`KNOWLEDGE-API-022` **`[DEFERRED]` WHERE the admin content API is included, THE knowledge module SHALL
expose `POST`, `PUT` and `DELETE` on `/api/v1/admin/symptoms` and
`/api/v1/admin/symptoms/{id}/phrases`.**

`KNOWLEDGE-API-023` **`[DEFERRED]` WHERE the admin content API is included, THE knowledge module SHALL
expose `POST`, `PUT` and `DELETE` on `/api/v1/admin/diseases/{id}/remedies`.**

`KNOWLEDGE-API-024` **`[DEFERRED]` WHERE the admin content API is included, THE knowledge module SHALL
implement `DELETE` as a soft delete setting `deleted_at`, and SHALL populate `created_by` and
`updated_by` from the authenticated principal.**

`KNOWLEDGE-API-025` **`[DEFERRED]` WHERE the admin content API is included, THE knowledge module SHALL
recompute `symptom_phrase.normalised_bn` and `symptom_phrase.embedding` on every phrase write.**
*(Which is precisely why it is deferred: a write path needs an embedding, an embedding needs the
sidecar, and `KNOWLEDGE-NFR-001` says this module has no HTTP client. Admitting the admin API means
introducing an inbound port that `analysis` fills — a real design change, not a controller.)*

`KNOWLEDGE-SEC-002` **THE knowledge module SHALL expose no `POST`, `PUT`, `PATCH` or `DELETE` endpoint
in this build**, and any such request under `/api/v1/crops`, `/api/v1/diseases` or `/api/v1/symptoms`
SHALL be answered `405`.

**Why content is seeded by migration instead.** `COMMON-CON-003` forbids an agent from authoring
agronomic content, so the content must arrive from a human either way. Delivering it as
`V10`–`V17` (`COMMON-DATA-004`) makes it reviewable in a pull request, reproducible from an empty
database (`COMMON-DATA-006`), identical in every profile, and impossible to mutate accidentally
during a demo. A CRUD UI would have bought a screen nobody demos and a write path that could corrupt
the one authoritative dataset in the system.

### 4.4 Content governance and validation

`KNOWLEDGE-DATA-001` **THE knowledge module SHALL own, and be the only module that writes JPA
mappings for, the tables `crop`, `disease`, `symptom`, `symptom_phrase`, `disease_symptom`, `remedy`
and `model_label_map`, as defined in `00-common.ears.md` §4.3.**

`KNOWLEDGE-DATA-002` **THE knowledge module SHALL treat every owned table as read-only at runtime**,
populated exclusively by the Flyway migrations `V10__ref_crops.sql` … `V17__ref_model_label_map.sql`.
There is no runtime insert, update or delete path in this build.

`KNOWLEDGE-DATA-003` **THE content of every owned table SHALL be human-supplied per `COMMON-CON-003`
and `docs/requirements/CONTENT-OWNERS.md`**, and THE knowledge module's requirements SHALL specify
only its **structure, source and validation**. No agent authors a disease name, a remedy, a dosage, a
pre-harvest interval, a citation or a Bangla phrase.

`KNOWLEDGE-DATA-004` **THE `disease` table SHALL be validated at startup against `COMMON-DATA-001`**
(the 14 classes of `00-common.ears.md` §2.5), and a mismatch SHALL fail startup with
`ERR_KB_CONTENT_INVALID`.

The startup gate below is one `ApplicationRunner` in `knowledge`. It runs unconditionally, in every
profile, and a failure aborts the context — the application does not start against an incomplete
knowledge base.

`KNOWLEDGE-DATA-007` **WHEN the application starts, THE knowledge module SHALL verify that every
non-deleted `disease` with `is_healthy = false` has at least one `remedy` satisfying
`ActiveRemedySpec`, and IF any does not, THEN THE application SHALL fail to start with
`ERR_KB_CONTENT_INVALID`.** *(A disease that can be diagnosed but carries no advice is a case the
officer cannot close. Better to refuse to start than to discover it on stage.)*

`KNOWLEDGE-DATA-008` **WHEN the application starts, THE knowledge module SHALL verify that every
non-deleted `disease` with `is_healthy = false` has at least one `disease_symptom` row, and IF any
does not, THEN THE application SHALL fail to start with `ERR_KB_CONTENT_INVALID`.** *(A disease with
no weights can never be scored by `KNOWLEDGE-FR-028`, so F5 would silently be unable to reach it.)*

`KNOWLEDGE-DATA-009` **WHEN the application starts, THE knowledge module SHALL verify that every
non-deleted `remedy` has a `source_ref` that is non-empty after trimming, and IF any does not, THEN
THE application SHALL fail to start with `ERR_KB_CONTENT_INVALID`.** *(The column is `NOT NULL` but an
empty string satisfies that. Every piece of advice a farmer receives must be traceable to a named
source; the citations are human-supplied per `CONTENT-OWNERS.md`.)*

`KNOWLEDGE-DATA-010` **THE knowledge module SHALL rely on the database constraint `ck_remedy_phi`
(`00-common.ears.md` §4.3) to guarantee that every `CHEMICAL` remedy has a non-null `phi_days`, and
SHALL NOT re-implement that check in Java.** *(One enforcement point. The constraint is applied to
every insert including the seed migration, which a Java-only check would not be.)*

`KNOWLEDGE-DATA-011` **WHEN the application starts, THE knowledge module SHALL verify that every
non-deleted `symptom_phrase` has a non-null `embedding`, and IF any does not, THEN THE application
SHALL fail to start with `ERR_KB_EMBEDDING_MISSING`.** *(A phrase with no vector is invisible to the
kNN layer. Without this gate F5 degrades quietly to fuzzy-only and nobody notices until the demo.)*

`KNOWLEDGE-DATA-012` **THE knowledge module SHALL verify in its integration test that
`symptom_phrase.normalised_bn` equals the output of `KNOWLEDGE-FR-017`'s normalisation applied to
`symptom_phrase.phrase_bn`, for every row.** *(Catches drift between the migration that computed the
normalised form and the code that normalises the transcript at query time — a defect that would
disable the fuzzy layer without any error.)*

`KNOWLEDGE-DATA-013` **WHEN the application starts, THE knowledge module SHALL implement
`COMMON-DATA-014` by verifying that every model identifier configured under `foshol.ai.vision.*` has
at least one `model_label_map` row, failing startup with `ERR_MODEL_LABEL_MAP_MISSING` otherwise.**

`KNOWLEDGE-DATA-014` **WHEN the application starts, THE knowledge module SHALL implement
`COMMON-DATA-015` by verifying that every `model_label_map.disease_id` resolves to an existing,
non-deleted `disease` row, failing startup with `ERR_MODEL_LABEL_MAP_INVALID` otherwise.**

`KNOWLEDGE-DATA-015` **THE knowledge module SHALL apply `LiveContentSpec` to every read of every owned
table**, so a soft-deleted row is invisible to `KnowledgeQueryApi`, to `SymptomMatchApi` and to every
endpoint. *(The `deleted_at` columns exist today with nothing writing them — the seam for
`KNOWLEDGE-API-024`.)*

`KNOWLEDGE-DATA-016` **THE knowledge module SHALL NOT return a raw model label string from any
endpoint or any `api` record**, in support of `COMMON-DATA-016`. `resolveModelLabel` accepts a raw
label as an argument and returns a `UUID`; it never returns one.

### 4.5 Embeddings

`KNOWLEDGE-DATA-020` **THE `symptom.embedding` and `symptom_phrase.embedding` columns SHALL hold
768-dimensional vectors** (`vector(768)`, `00-common.ears.md` §4.3), matching `COMMON-NFR-006`.

`KNOWLEDGE-DATA-021` **THE vectors SHALL be computed offline by `tools/` using the model identified by
`foshol.ai.embed.model-id` and SHALL be loaded by the migration `V16__ref_symptom_embeddings.sql`.**
THE knowledge module SHALL NOT compute, refresh or write an embedding at runtime.

`KNOWLEDGE-DATA-022` **IF the value of `foshol.ai.embed.model-id` changes, THEN the stored embeddings
SHALL be regenerated and reloaded by a new Flyway migration, and the change SHALL NOT be treated as a
configuration change alone.** *(`COMMON-NFR-006`. Vectors from two different models are not comparable
under cosine similarity; mixing them silently corrupts every kNN result. A dimensionality change is
additionally a `vector(n)` column change — schema, not config. Per `COMMON-NFR-042` the module agent
raises a blocker; A1 writes the migration.)*

`KNOWLEDGE-DATA-023` **`[DERIVED]` THE migration `V16__ref_symptom_embeddings.sql` SHALL record, in a
leading SQL comment, the exact embedding model identifier and the `tools/` script that produced the
vectors it loads.** *(There is no column recording provenance and adding one is not worth a schema
change; a comment is greppable, is reviewed in the pull request, and is the only way a later reader
can tell which model a stored vector came from.)*

`KNOWLEDGE-DATA-024` **THE symptom matcher SHALL query `symptom_phrase.embedding` only, and SHALL NOT
query `symptom.embedding`.** *(`symptom.embedding` and its HNSW index `ix_symptom_embedding` exist in
the frozen schema; stating this explicitly stops an agent inventing a second retrieval path whose
results would have to be reconciled with the first.)*

### 4.6 Language

`KNOWLEDGE-UX-001` **THE knowledge module SHALL treat the `*_bn` column as the value of record for
every crop, disease, symptom and remedy field** (`COMMON-NFR-037`), and SHALL never require a `*_en`
value to be present.

`KNOWLEDGE-UX-002` **`[DERIVED]` THE `api` view records (`CropView`, `DiseaseView`, `SymptomRefView`)
SHALL carry the `*_en` column value verbatim, including `null`**, and the English fallback of
`COMMON-NFR-038` SHALL be applied in the module's `web` response records. *(The `api` records are
frozen by `COMMON-NFR-041` and carry no `<field>Fallback` companion field, so the flag cannot live
there. Resolving the fallback inside the frozen record would destroy the information that a fallback
happened. The seam is therefore the web mapper.)*

`KNOWLEDGE-UX-003` **IF an HTTP response field derived from a `*_en` column would be null, THEN THE
knowledge module SHALL emit the corresponding `*_bn` value in its place and SHALL set the sibling
boolean `<field>Fallback` to `true`**, per `COMMON-NFR-038`.

`KNOWLEDGE-UX-004` **THE knowledge module SHALL set every `<field>Fallback` boolean to `false` when
the English value is present**, and SHALL emit the field on every response regardless of value.
*(An absent flag and a `false` flag must not be distinguishable by the frontend.)*

---

## 5. API surface

All endpoints are read-only, live under `/api/v1`, and follow `00-common.ears.md` §8.2 and §8.3.

`KNOWLEDGE-SEC-001` **THE knowledge module SHALL require an authenticated principal on every
endpoint** (`COMMON-SEC-010`) and SHALL permit any of `FARMER`, `OFFICER` and `ADMIN`. *(The taxonomy
is public product content, not case data; the farmer crop picker and the officer remedy editor read
the same rows.)*

`KNOWLEDGE-SEC-003` **THE knowledge module SHALL expose no farmer, officer, case, advisory or
personally identifying data through any endpoint or `api` record.**

`KNOWLEDGE-API-001` **THE knowledge module SHALL expose `GET /api/v1/crops`** returning `200` with the
paginated envelope of `00-common.ears.md` §8.2 over `CropResponse`, ordered per `KNOWLEDGE-FR-040`.

`KNOWLEDGE-API-002` **THE knowledge module SHALL expose `GET /api/v1/crops/{id}/diseases`** returning
`200` with the paginated envelope over `DiseaseResponse` ordered per `KNOWLEDGE-FR-043`, and `404`
with `ERR_CROP_NOT_FOUND` when the crop does not exist.

`KNOWLEDGE-API-003` **THE knowledge module SHALL expose `GET /api/v1/diseases/{id}`** returning `200`
with a `DiseaseResponse`, and `404` with `ERR_DISEASE_NOT_FOUND` when it does not exist.

`KNOWLEDGE-API-004` **THE knowledge module SHALL expose `GET /api/v1/diseases/{id}/remedies`**
returning `200` with the paginated envelope over `RemedyResponse` ordered per `KNOWLEDGE-FR-044`, and
`404` with `ERR_DISEASE_NOT_FOUND` when the disease does not exist.

`KNOWLEDGE-API-005` **THE knowledge module SHALL expose `GET /api/v1/symptoms`** returning `200` with
the paginated envelope over `SymptomResponse` ordered per `KNOWLEDGE-FR-046`.

`KNOWLEDGE-API-006` **IF a path variable is not a syntactically valid UUID, THEN THE knowledge module
SHALL return `400` as an RFC 9457 problem document** (`COMMON-API-002`).

`KNOWLEDGE-API-007` **THE knowledge module SHALL expose no endpoint for `SymptomMatchApi`.** The
matcher is an in-process contract used by `analysis`; exposing it would publish an unreviewed
diagnosis path straight to a client.

### 5.1 Response records (module-owned, `web` package)

| Record | Fields |
|---|---|
| `CropResponse` | `id`, `code`, `nameBn`, `nameEn`, `nameEnFallback`, `iconKey`, `displayOrder` |
| `DiseaseResponse` | `id`, `cropId`, `code`, `nameBn`, `nameEn`, `nameEnFallback`, `descriptionBn`, `severity`, `healthy` |
| `RemedyResponse` | `id`, `diseaseId`, `type`, `titleBn`, `stepsBn`, `dosageBn`, `phiDays`, `costTier`, `efficacy`, `sourceRef`, `displayOrder` |
| `SymptomResponse` | `id`, `code`, `nameBn`, `nameEn`, `nameEnFallback`, `organ` |

| Endpoint | Method | Auth | 200 | 400 | 401 | 404 |
|---|---|---|---|---|---|---|
| `/api/v1/crops` | GET | any role | page of `CropResponse` | — | yes | — |
| `/api/v1/crops/{id}/diseases` | GET | any role | page of `DiseaseResponse` | bad UUID | yes | `ERR_CROP_NOT_FOUND` |
| `/api/v1/diseases/{id}` | GET | any role | `DiseaseResponse` | bad UUID | yes | `ERR_DISEASE_NOT_FOUND` |
| `/api/v1/diseases/{id}/remedies` | GET | any role | page of `RemedyResponse` | bad UUID | yes | `ERR_DISEASE_NOT_FOUND` |
| `/api/v1/symptoms` | GET | any role | page of `SymptomResponse` | — | yes | — |

---

## 6. Persistence

### 6.1 Tables owned

`crop`, `disease`, `symptom`, `symptom_phrase`, `disease_symptom`, `remedy`, `model_label_map` — all
defined in `00-common.ears.md` §4.3 (`V3__knowledge.sql`), indexed in §4.9. **This document defines no
schema.**

Indexes this module depends on: `ix_symptom_phrase_embedding` (HNSW, `vector_cosine_ops`),
`ix_symptom_phrase_symptom`, `ix_disease_symptom_symptom`, `ix_remedy_disease` (partial, `active AND
deleted_at IS NULL`).

### 6.2 Query patterns

| Pattern | Statement | Index |
|---|---|---|
| Phrase kNN | `ORDER BY embedding <=> :q LIMIT :knnLimit` | `ix_symptom_phrase_embedding` |
| Disease scoring | one grouped join over `disease` × `disease_symptom` scoped by `crop_id` | `disease` PK, `ix_disease_symptom_symptom` |
| Active remedies | `WHERE disease_id = ? AND active AND deleted_at IS NULL ORDER BY display_order` | `ix_remedy_disease` |
| Diseases by crop | `WHERE crop_id = ? AND deleted_at IS NULL` | `disease` PK / `uq_disease_crop_code` |
| Label resolution | in-memory map loaded once at startup (`KNOWLEDGE-FR-049`) | none needed |
| Phrase index | in-memory list loaded once at startup (`KNOWLEDGE-FR-016`) | none needed |

`KNOWLEDGE-NFR-002` **THE knowledge module SHALL execute every query through the
`@ReadOnlyDataSource`-qualified `DataSource`** (`COMMON-ARCH-007`), and its query handlers SHALL NOT
load a JPA entity.

`KNOWLEDGE-NFR-003` **THE knowledge module SHALL execute at most two SQL statements per
`SymptomMatchApi.match` call** — one kNN statement and one scoring statement. *(A structural budget
rather than a millisecond budget: it is deterministic, it is assertable with a statement counter, and
it prevents the N+1 that a per-symptom lookup would introduce.)*

`KNOWLEDGE-NFR-004` **THE knowledge module SHALL bind the query vector as a `com.pgvector.PGvector`
parameter and SHALL NOT interpolate it into SQL as a string.**

`KNOWLEDGE-NFR-005` **THE knowledge module SHALL declare a Gradle dependency on `:common` only**
(`COMMON-NFR-007`, §6.1 dependency matrix).

`KNOWLEDGE-NFR-006` **IF any startup validation of §4.4 fails, THEN THE application SHALL terminate
with a non-zero exit status and SHALL log the failing requirement id and the offending row
identifiers.** *(Row identifiers only — never remedy text; the log must stay readable and must not
become a channel for content.)*

---

## 7. Acceptance criteria

Each scenario maps one-to-one onto a test method.

**Matcher — retrieval**

- `KNOWLEDGE-FR-010` — Given a valid request, When `match` is called, Then a `SymptomMatchResult` is
  returned with non-null `symptoms` and `diseases` lists.
- `KNOWLEDGE-FR-011` — Given a `cropId` that matches no crop, When `match` is called, Then
  `ERR_CROP_NOT_FOUND` is raised and no query touches `symptom_phrase`.
- `KNOWLEDGE-FR-012` — Given a `transcriptEmbedding` of length 512, When `match` is called, Then
  `ERR_EMBEDDING_DIMENSION` is raised.
- `KNOWLEDGE-FR-013` — Given a 768-d embedding, When `match` is called, Then exactly one statement
  containing `<=>` is executed with `LIMIT` equal to `foshol.knowledge.match.knn-limit`.
- `KNOWLEDGE-FR-014` — Given kNN rows with similarities 0.90, 0.75 and 0.60 and a threshold of 0.72,
  When the vector layer filters, Then two matches survive.
- `KNOWLEDGE-FR-015` — Given a null embedding and a transcript, When `match` is called, Then no
  statement containing `<=>` is executed and fuzzy matches are still returned.
- `KNOWLEDGE-FR-016` — Given a started application, When the phrase index is inspected, Then it holds
  exactly the non-deleted `symptom_phrase` rows and a second call reloads nothing.
- `KNOWLEDGE-FR-017` — Given a string containing a ZWNJ, a Bengali digit and a danda, When it is
  normalised, Then the ZWNJ is absent, the digit is ASCII, the danda is a space, and no whitespace run
  exceeds one character.
- `KNOWLEDGE-FR-019` — Given a phrase of 3 tokens of which 2 appear in the transcript, When overlap is
  computed, Then it equals 0.667 (2/3); given a phrase of 0 tokens, Then it equals 0.
- `KNOWLEDGE-FR-020` — Given overlaps of 0.667 and 0.5 with a threshold of 0.60, When the fuzzy layer
  filters, Then one match survives.

**Matcher — deduplication and scoring**

- `KNOWLEDGE-FR-021` — Given symptom S matched by vector at 0.80 and by fuzzy at 0.90, When
  deduplication runs, Then one entry for S remains with score 0.900 and matcher `FUZZY`.
- `KNOWLEDGE-FR-022` — Given symptom S matched by both layers at 0.80, When deduplication runs, Then
  matcher is `VECTOR`.
- `KNOWLEDGE-FR-023` — Given 10 distinct matched symptoms and `max-symptoms` of 8, When deduplication
  runs, Then 8 remain, they are the 8 highest scoring, and equal scores are ordered by `code`.
- `KNOWLEDGE-FR-024` — Given a raw score of 0.72349, When the result is built, Then the score is
  0.723; given 0.72350, Then it is 0.724.
- `KNOWLEDGE-FR-025` — Given `officerSymptomIds` containing 3 valid ids and 10 automatic matches with
  `max-symptoms` of 8, When `match` runs, Then 11 symptoms are returned and the 3 have score 1.000 and
  matcher `MANUAL`.
- `KNOWLEDGE-FR-026` — Given symptom S both officer-supplied and vector-matched at 0.80, When `match`
  runs, Then exactly one entry for S is returned, with matcher `MANUAL`.
- `KNOWLEDGE-FR-027` — Given an unknown id in `officerSymptomIds`, When `match` runs, Then it is
  ignored, no exception is thrown, and one `WARN` is logged.
- `KNOWLEDGE-FR-028` — Given disease D with weights {S1: 0.8, S2: 0.4} and matches {S1: 1.0}, When
  scoring runs, Then `score(D) = 0.8 / 1.2 = 0.6667`.
- `KNOWLEDGE-FR-029` — Given a matched symptom shared by a disease of another crop, When scoring runs,
  Then that disease is absent from the result.
- `KNOWLEDGE-FR-031` — Given scores 0.90 and 0.90 for diseases with codes `b` and `a`, When ranks are
  assigned, Then `a` is rank 1 and `b` is rank 2.
- `KNOWLEDGE-FR-032` — Given a disease of the crop with no matched symptom, When scoring runs, Then it
  is absent from `diseases`.
- `KNOWLEDGE-FR-033` — Given a transcript matching nothing and no officer symptoms, When `match` runs,
  Then `inconclusive` is true and both lists are empty.
- `KNOWLEDGE-FR-034` — Given a top score of 0.2500 and `inconclusive-score-min` of 0.30, When `match`
  runs, Then `inconclusive` is true and `diseases` is non-empty.
- `KNOWLEDGE-FR-035` — Given an inconclusive result, When `match` returns, Then exactly one `WARN`
  carrying the correlation id is logged.
- `KNOWLEDGE-FR-036` — Given one request executed twice, When the results are compared, Then they are
  equal field for field including list order.
- `KNOWLEDGE-FR-037` — Given a `match` call, When the transaction is inspected, Then it is read-only
  and no row was written.

**Read APIs**

- `KNOWLEDGE-FR-040` — Given crops with `display_order` 2, 1, 1, When `listCrops` runs, Then order is
  by `display_order` then `code`.
- `KNOWLEDGE-FR-041` / `-042` — Given an unknown id, When the lookup runs, Then `Optional.empty()` is
  returned.
- `KNOWLEDGE-FR-043` — Given a crop with a healthy and a non-healthy disease, When
  `listDiseasesByCrop` runs, Then the healthy one is last.
- `KNOWLEDGE-FR-044` — Given remedies with `active=false`, `deleted_at` set, and two active ones with
  `display_order` 2 and 1, When `listActiveRemedies` runs, Then only the two active ones are returned,
  ordered 1 then 2.
- `KNOWLEDGE-FR-045` — Given `steps_bn` of `["a","b"]`, When mapped, Then `stepsBn` is `["a","b"]` in
  that order and never null.
- `KNOWLEDGE-FR-046` — Given symptoms, When `listSymptoms` runs, Then results are ordered by `code`
  and no view exposes an embedding.
- `KNOWLEDGE-FR-047` — Given a null id, When any lookup runs, Then an empty result is returned and no
  exception is thrown.
- `KNOWLEDGE-FR-048` — Given a mapping row for `("m","1","Leaf_Blast")`, When `resolveModelLabel` is
  called with `("m","1","leaf_blast")`, Then `Optional.empty()` is returned.
- `KNOWLEDGE-FR-049` — Given a started application, When `resolveModelLabel` is called, Then no SQL
  statement is executed.

**Content governance**

- `KNOWLEDGE-DATA-004` — Given a `disease` table with 13 rows, When the application starts, Then it
  fails with `ERR_KB_CONTENT_INVALID`.
- `KNOWLEDGE-DATA-007` — Given a non-healthy disease with no active remedy, When the application
  starts, Then it fails with `ERR_KB_CONTENT_INVALID` naming that disease id.
- `KNOWLEDGE-DATA-008` — Given a non-healthy disease with no `disease_symptom` row, When the
  application starts, Then it fails with `ERR_KB_CONTENT_INVALID`.
- `KNOWLEDGE-DATA-009` — Given a remedy whose `source_ref` is `"   "`, When the application starts,
  Then it fails with `ERR_KB_CONTENT_INVALID`.
- `KNOWLEDGE-DATA-010` — Given an attempted insert of a `CHEMICAL` remedy with null `phi_days`, When
  the statement executes, Then the database rejects it via `ck_remedy_phi`.
- `KNOWLEDGE-DATA-011` — Given a `symptom_phrase` with a null embedding, When the application starts,
  Then it fails with `ERR_KB_EMBEDDING_MISSING`.
- `KNOWLEDGE-DATA-012` — Given every seeded phrase, When each `phrase_bn` is normalised in Java, Then
  the result equals the stored `normalised_bn`.
- `KNOWLEDGE-DATA-013` — Given a configured vision model with no mapping row, When the application
  starts, Then it fails with `ERR_MODEL_LABEL_MAP_MISSING`.
- `KNOWLEDGE-DATA-014` — Given a mapping row pointing at an absent disease, When the application
  starts, Then it fails with `ERR_MODEL_LABEL_MAP_INVALID`.
- `KNOWLEDGE-DATA-015` — Given a soft-deleted remedy and disease, When any read or match runs, Then
  neither appears in any result.
- `KNOWLEDGE-DATA-023` — Given `V16__ref_symptom_embeddings.sql`, When its first lines are read, Then
  they name the embedding model id and the generating script.
- `KNOWLEDGE-DATA-024` — Given a `match` call, When executed SQL is captured, Then no statement
  references `symptom.embedding`.

**API, security and language**

- `KNOWLEDGE-API-001` … `-005` — Given an authenticated caller, When each endpoint is called, Then
  `200` and the documented body shape are returned.
- `KNOWLEDGE-API-002` / `-004` — Given an unknown crop or disease id, When called, Then `404` with the
  documented error code in an RFC 9457 body.
- `KNOWLEDGE-API-006` — Given the path `/api/v1/diseases/not-a-uuid`, When called, Then `400` with a
  problem document.
- `KNOWLEDGE-SEC-001` — Given no `Authorization` header, When any endpoint is called, Then `401`.
- `KNOWLEDGE-SEC-002` — Given `POST /api/v1/crops`, When called by an `ADMIN`, Then `405`.
- `KNOWLEDGE-UX-003` — Given a crop whose `name_en` is null, When `GET /api/v1/crops` is called, Then
  `nameEn` equals `nameBn` and `nameEnFallback` is `true`.
- `KNOWLEDGE-UX-004` — Given a crop whose `name_en` is present, When called, Then `nameEnFallback` is
  present and `false`.

---

## 8. Test requirements

### 8.1 Unit tests (no Spring context, no database)

| Target | Must assert |
|---|---|
| `BanglaTextNormaliser` (pure static, `domain`) | every step of `KNOWLEDGE-FR-017`, plus idempotence: `normalise(normalise(x)) == normalise(x)` |
| `FuzzyOverlap` (pure static, `domain`) | the formula of `KNOWLEDGE-FR-019`, including the empty-phrase and empty-transcript cases |
| `SymptomDeduplicator` (`domain`) | `KNOWLEDGE-FR-021` … `-024`, `-026` |
| `DiseaseScorer` (`domain`) | `KNOWLEDGE-FR-028`, `-031`, `-032` against hand-computed weights |
| `InconclusiveSpec` (`domain`) | `KNOWLEDGE-FR-033`, `-034` at, above and below the threshold |
| `SymptomMatchQueryHandler` | orchestration order, `KNOWLEDGE-FR-011`, `-012`, `-015`, `-025`, `-027` with mocked repositories |
| Every other query handler | `listCrops`, `findCropById`, `findCropByCode`, `findDiseaseById`, `listDiseasesByCrop`, `listActiveRemedies`, `listSymptoms`, `resolveModelLabel` — one test per handler per the test floor (clarification item 21) |
| `KnowledgeContentValidator` | one test per failure mode of §4.4, each asserting the error code |
| `KnowledgeWebMapper` | `KNOWLEDGE-UX-003`, `-004` |

Domain invariants I1, I8 and I9 are asserted on the value objects that own them.

### 8.2 Integration test — exactly one, Testcontainers

`KnowledgeIntegrationTest`, `postgres` image `pgvector/pgvector:pg17`, migrations `V1`–`V17` applied
by Flyway. It covers the module's primary happy path — **the matcher end to end** — and additionally
carries the two assertions that only a real database can make:

1. Given a fixture transcript embedding and crop, `SymptomMatchApi.match` returns the expected
   symptoms with the expected matchers and the expected ranked diseases (`KNOWLEDGE-FR-010` …
   `-034` end to end).
2. The HNSW index is used: `EXPLAIN` of the kNN statement contains an index scan on
   `ix_symptom_phrase_embedding` (`KNOWLEDGE-FR-013`).
3. `KNOWLEDGE-DATA-012` — every seeded `normalised_bn` equals the Java normalisation of its
   `phrase_bn`.

Per the test floor there is **exactly one** Testcontainers test in this module. Endpoint coverage is
`@WebMvcTest` with a mocked `KnowledgeQueryApi`, not integration tests.

### 8.3 Fixtures

| Fixture | Content | Source |
|---|---|---|
| `symptom-match-request.json` | a `cropId`, a Bangla transcript, a 768-float vector, an officer symptom id list | generated by `tools/` from the seeded phrases; the transcript is **content** and is human-supplied per `CONTENT-OWNERS.md` |
| `expected-match-result.json` | the expected `SymptomMatchResult` for that request | derived by hand from the seeded weights, recorded once, reviewed by a human |
| `label-map-fixtures.sql` | a deliberately broken `model_label_map` for the two startup-failure tests | authored by A4 — structure only, no agronomic content |
| `bad-content-fixtures.sql` | a disease with no remedy, a remedy with blank `source_ref`, a phrase with a null embedding | authored by A4 — structure only |

`KNOWLEDGE-NFR-007` **THE test fixtures SHALL contain no agent-authored Bangla phrase, disease name or
remedy text** (`COMMON-CON-003`); a fixture requiring such content references the seeded rows by code
rather than restating them.

---

## 9. Agent execution notes

### 9.1 Implementation order

1. `build.gradle` — dependency on `:common` only (`KNOWLEDGE-NFR-005`).
2. `api/` — copy the frozen records and interfaces from `00-common.ears.md` §6.2 **verbatim**. Do not
   add, rename or reorder a component. Stop and raise a blocker if a signature looks wrong
   (`COMMON-NFR-041`).
3. `domain/` — `BanglaTextNormaliser`, `FuzzyOverlap`, `SymptomMatch`, `DiseaseScore`,
   `SymptomDeduplicator`, `DiseaseScorer`, `InconclusiveSpec`, the specifications of §3.3. No Spring,
   no Jakarta, no Jackson (`COMMON-ARCH-004`). **Write the unit tests here, first — this is the
   module's whole risk surface.**
4. `infrastructure/` — JPA entities for the seven owned tables (read-only mappings), Spring Data
   repositories, and `SymptomPhraseVectorRepository` / `DiseaseScoreRepository` as `JdbcClient`-based
   native-query components bound to the read-only `DataSource`.
5. `application/query/` — one query and one handler per `KnowledgeQueryApi` method, plus
   `SymptomMatchQuery` + `SymptomMatchQueryHandler`. There is **no** `application/command/` package in
   this module: it has no commands.
6. `infrastructure/KnowledgeContentValidator` — the §4.4 startup gate, plus the in-memory phrase index
   and label map of `KNOWLEDGE-FR-016` and `-049`.
7. `api` implementations — `KnowledgeQueryApiImpl`, `SymptomMatchApiImpl`, delegating to query
   handlers and nothing else.
8. `web/` — the five controllers, the four response records, `KnowledgeWebMapper` with the
   `COMMON-NFR-038` fallback. Controllers bind, delegate to one handler, and map
   (`COMMON-ARCH-009`).
9. `KnowledgeIntegrationTest`.

### 9.2 Blockers to raise rather than work around

- Reference content (`V10`–`V17`) not yet delivered by `CONTENT-OWNERS.md`: the startup gate of §4.4
  is unconditional by design, so the application will not start. Raise the blocker per
  `COMMON-NFR-046`; **do not** weaken the validation, add a bypass flag, or author placeholder
  agronomic content.
- A needed schema or property change: `COMMON-NFR-042` and `COMMON-NFR-020` — it goes to A1.
- An `api` signature that seems wrong: `COMMON-NFR-041` — it goes to A1.

### 9.3 Local Definition of Done

The global list in `00-common.ears.md` §11, plus:

1. Every unit test of §8.1 passes; the one Testcontainers test of §8.2 passes.
2. `SymptomMatchApi.match` produces identical output on two consecutive runs of the same fixture.
3. The five endpoints match `docs/openapi/foshol-api.yaml` in path, method, status codes and schema.
4. `grep` of `modules/knowledge/` finds no HTTP client, no `RestClient`, no sidecar URL
   (`KNOWLEDGE-NFR-001`).
5. `grep` of `modules/knowledge/` finds no numeric literal for any threshold — all five
   `foshol.knowledge.match.*` properties are read through `common.ConfigKeys` (`COMMON-NFR-020`).
6. No agronomic content was authored anywhere in the module or its fixtures (`COMMON-CON-003`).
7. The `[DEFERRED]` block of §4.3 is present in this document and no admin write endpoint exists in
   code.

---

## 10. Requirement index

| Category | IDs | Count |
|---|---|---|
| Functional | `KNOWLEDGE-FR-010` … `-049` (010–049, non-contiguous) | 38 |
| Data | `KNOWLEDGE-DATA-001` … `-004`, `-007` … `-016`, `-020` … `-024` | 19 |
| API | `KNOWLEDGE-API-001` … `-007`, `-020` … `-025` `[DEFERRED]` | 13 |
| Security | `KNOWLEDGE-SEC-001` … `-003` | 3 |
| Non-functional | `KNOWLEDGE-NFR-001` … `-007` | 7 |
| UX | `KNOWLEDGE-UX-001` … `-004` | 4 |
| **Total** | | **84** |

`[DERIVED]` items in this document: `KNOWLEDGE-FR-025` (officer symptoms score 1.000 as `MANUAL` and
are exempt from the cap), `KNOWLEDGE-DATA-023` (`V16` records its embedding model in a SQL comment),
`KNOWLEDGE-UX-002` (the English fallback flag lives in the `web` response records, not in the frozen
`api` records).
