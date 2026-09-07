# ADR-0009: A `model_label_map` table, not a `model_class_label` column

- **Status:** Accepted
- **Date:** 2026-09-07
- **Deciders:** project lead (human), requirements agent
- **Supersedes / relates to:** **supersedes plan §4's `disease.model_class_label`**; relates to ADR-0008, ADR-0010; enforced by `COMMON-DATA-010` … `COMMON-DATA-017`

## Context

Plan §4 puts a single `model_class_label` column on the `disease` table and states the rule it serves:
that column is the join between the ML label space and our taxonomy, and model strings must never leak
past `analysis`. The rule is right. The column cannot implement it.

This build runs **three** vision models (ADR-0008) with three disjoint label spaces that agree neither
with each other nor with our 14-class taxonomy: a Swin-Tiny rice classifier with 6 classes, a SigLIP2
rice classifier with 5 used as the rice fallback, and a MobileNetV2 model trained on a large
multi-crop public dataset of which we use only the tomato and potato classes. A `disease` row for rice
blast would need to carry the primary rice model's label *and* the fallback's. One column holds one
string.

Worse, label strings are model-version-specific: a re-uploaded checkpoint can rename a class, reorder
the index-to-label map, or change casing and separators, and nothing about `disease.model_class_label`
records which model or version the stored string belonged to. The resulting failure is silent — a
candidate whose raw label does not match is quietly absent, the case scores lower than it should, and
it routes to `UNDETERMINED` for a reason nobody can see in the data.

## Decision

**Replace the column with a table keyed by model identity, and treat the mapping as a first-class
contract rather than a lookup detail** (`00-common.ears.md` §4.3):

```sql
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

`COMMON-DATA-010`: every raw label of every configured vision model maps onto exactly one
`disease.id`, keyed by `(model_id, model_version, raw_label)`.

**Seeded, never written at runtime.** `COMMON-DATA-011` restricts population to
`V17__ref_model_label_map.sql`. Deciding that a model's class corresponds to a taxonomy entry is a
judgement about disease identity, not a string-matching exercise, so the mapping is human-authored
content (`CONTENT-OWNERS.md` C13) and the migration ships as a scaffold with `TODO(content-owner)`
markers until it is filled.

**The lookup key comes from the sidecar's response, not from configuration.** `COMMON-DATA-017`: the
sidecar reports the `model_id` and `model_version` that actually produced each classification, and
`analysis` uses those values. If the sidecar has been reconfigured, the mapping follows the model that
ran rather than the model Spring believes is configured.

**Unmapped output is routed and recorded, never silently dropped.** `COMMON-DATA-012`: an unmapped raw
label causes the candidate to be discarded from `case_candidate`, the label to be appended to
`analysis_run.unmapped_labels`, and a `WARN` logged with the correlation id. `COMMON-DATA-013`: if
*every* candidate is unmapped the case routes to `UNDETERMINED` — an unmapped result is an unknown
result, never a confident diagnosis and never an empty result that looks like success.

**Raw labels stay inside `analysis`.** `COMMON-DATA-016` forbids persisting one on `case_candidate`,
`advisory` or either projection, and forbids returning one from any endpoint except
`GET /api/v1/cases/{id}/analysis`, the transparency view the officer sees.

**Two startup validations, both fail-fast.** `COMMON-DATA-014`: every model configured under
`foshol.ai.vision.*` has at least one mapping row, else the application fails to start with
`ERR_MODEL_LABEL_MAP_MISSING`. `COMMON-DATA-015`: every `disease_id` resolves to an existing,
non-deleted `disease`, else `ERR_MODEL_LABEL_MAP_INVALID`. A configuration change pointing at an
unmapped model is a boot failure, which is loud, rather than a run of cases mysteriously landing in
`UNDETERMINED`, which is silent.

## Consequences

### Positive

- Three models coexist without contorting the taxonomy, and a fourth is a migration rather than a
  schema change.
- Version awareness means a re-uploaded checkpoint with renamed classes is caught at startup or
  recorded per case, not absorbed.
- `unmapped_labels` turns a whole class of silent degradation into queryable data.
- `disease` describes diseases and carries no ML implementation detail.
- The mapping is human-reviewable content in one migration file — exactly where a domain expert can
  check it.

### Negative / accepted cost

- One more table, migration, startup check and join. The join is on a unique index over tens of rows,
  so the runtime cost is negligible; the conceptual cost — agents must remember it exists — is real.
- The mapping is content, so it blocks on a human. Until C13 lands, no vision candidate maps and every
  case routes to `UNDETERMINED`. That is correct behaviour and also a hard dependency in the critical
  path.
- `model_version` must be something the sidecar reports stably. A value that changes per container
  start makes every lookup miss.
- Two models covering the same crop can map the same disease from two label spaces — many-to-one by
  design — and nothing checks that their mappings are mutually consistent in meaning.
- **A mapping row that is simply wrong passes both validations happily.** They check existence and
  referential integrity, not correctness. Only human review of the migration catches a mis-mapping,
  and mandatory officer approval (ADR-0003) is what stops it reaching a farmer.

## Alternatives considered

**Keep `disease.model_class_label`.** Rejected: one column cannot express three label spaces, and it
records neither model nor version.

**An array column `model_class_labels text[]`.** Rejected. It expresses multiplicity but still loses
model identity and version, cannot be foreign-key checked, and makes the reverse lookup a scan.

**Map in code — a Java map or a properties file.** Rejected. It puts agronomic judgement in source
where the content owner cannot see it, cannot be foreign-key checked against `disease`, and makes
adding a model a code change rather than a data change.

**Normalise raw labels heuristically — lowercase, strip separators, fuzzy-match to `disease.code`.**
Rejected, and this is the tempting one. It would work for most labels and fail silently for the rest,
mapping a label to a plausible-looking wrong disease. Explicit mapping fails loudly; heuristic mapping
fails quietly, and a quiet failure here is a wrong diagnosis in front of an officer.

**Let the sidecar return taxonomy codes directly.** Rejected: it moves our taxonomy into the Python
container, contrary to ADR-0007's boundary, and makes changing the mapping a sidecar deployment.

## Revisit when

Revisit when the number of configured vision models exceeds what one migration file can be reviewed as
— practically, more than four or five — or the first time a model id is changed in configuration and
the deployment fails to start with `ERR_MODEL_LABEL_MAP_MISSING` because nobody remembered the
mapping. The answer then is a small admin-facing mapping surface with the same validations, not
runtime writes, which `COMMON-DATA-011` forbids for good reason.
