# ADR-0008: No model training of any kind — pinned pretrained weights only

- **Status:** Accepted
- **Date:** 2026-09-07
- **Deciders:** project lead (human), requirements agent
- **Supersedes / relates to:** **strikes plan §6 and plan §7.1 entirely**; relates to ADR-0003, ADR-0006, ADR-0011; enforced by `COMMON-CON-001`, `COMMON-NFR-005`, `COMMON-NFR-006`

## Context

Plan §6 recommends fine-tuning a vision backbone on Dhan-Shomadhan, a Bangladeshi rice-leaf dataset —
"half a day, free" on a hosted GPU notebook — supplemented with the tomato and potato subsets of a
public plant-disease dataset. Plan §7.1 recommends a bake-off across three Bangla ASR candidates,
choosing by word error rate on the team's own recordings.

Both are good engineering for the six-day, three-human-engineer project the plan was written for.
Neither is available to this project. **There is no training infrastructure**: no GPU, no dataset
acquisition path, no labelling process, no training script, no experiment tracking, nowhere to store a
checkpoint, and no time in four days to build any of it. The "half a day" estimate assumes someone who
has done it before, on a machine already set up, with the data already split.

The deeper problem is not the estimate. It is what a deferred training task does to an autonomous
agent. This build is executed by seven agents working from these documents. An agent that reads
`[DEFERRED] fine-tune the vision backbone on the field-collected rice dataset` has been handed a task,
a dataset and an implied method — and agents attempt tasks. That attempt would consume a workstream's
day, produce a checkpoint nobody can evaluate, and, worst, leave a weights file with no provenance
that some later step loads because it exists. A `[DEFERRED]` training requirement is precisely the
thing an agent might attempt, which is why it is not deferred. It is struck.

## Decision

**`COMMON-CON-001`: the system contains no model training, fine-tuning, distillation, quantisation or
model-selection benchmark in any form — not as a requirement, not as `[DEFERRED]`, not as `[STRETCH]`,
not as a comment.** Plan §6's fine-tune and plan §7.1's bake-off are **struck, not deferred**: no
seam, no future-work note, no placeholder. Every other cut from the plan keeps its seam visible so the
deferral is honest (`00-common.ears.md` §1.2); these two are the deliberate exception, for the reason
above.

**All weights are pretrained and loaded by identifier from configuration** (`00-common.ears.md` §2.4):

| Role | Model identifier | Property |
|---|---|---|
| Rice vision, primary | `kssrikar4/Rice-Leaf-Disease-Classification` (Swin-Tiny, 6 classes) | `foshol.ai.vision.rice.model-id` |
| Rice vision, fallback | `prithivMLmods/Rice-Leaf-Disease` (SigLIP2, 5 classes) | `foshol.ai.vision.rice.fallback-model-id` |
| Tomato + potato vision | `Daksh159/plant-disease-mobilenetv2` | `foshol.ai.vision.solanaceae.model-id` |
| Bangla ASR | `ashrafulparan/whisper-small-bangla` | `foshol.ai.asr.model-id` |
| Text embedding (768-d) | `sentence-transformers/LaBSE` | `foshol.ai.embed.model-id` |

`COMMON-NFR-005`: the sidecar loads every model by identifier read from configuration at startup and
requires **no source change** to substitute a different identifier. Swapping any of these five is a
property edit and a container restart. The one exception is the embedding model, where
`COMMON-NFR-006` fixes 768 dimensions to match the `vector(768)` columns: substituting a model of
different dimensionality is a schema change requiring a new migration, not a config change.

Because model ids are configuration and three vision models have three disjoint label spaces, the
mapping from raw output onto the 14-class taxonomy is itself data — see ADR-0009.

## Consequences

### Positive

- Four days go to the system rather than to a training run.
- Inference is reproducible: a pinned model id plus replay fixtures (ADR-0010) means the same input
  produces the same output on every machine, which is what makes three rehearsable demo paths possible
  (ADR-0006).
- No agent has a training task to attempt, no half-trained checkpoint enters the repository, and no
  weights file exists whose provenance nobody can state.
- Model choice becomes a reversible decision in a properties file, evaluated against
  `docs/eval-report.md`, rather than an irreversible investment of a day.

### Negative / accepted cost

- **There is no rice model trained on Bangladeshi field images.** The locally collected,
  field-condition rice dataset the plan identified as the single best available asset is not used.
  Naming it on stage would have been worth real credibility, and we give that up.
- The tomato and potato model derives from a controlled-condition public dataset. Such models are
  documented to degrade on real field photographs — uncontrolled background, variable lighting,
  occlusion, several leaves in frame — which is exactly how our users photograph. **We state this
  limitation ourselves rather than wait to be caught by it.**
- The two rice models are independently trained third-party classifiers with 6 and 5 classes, neither
  aligned to our taxonomy without a mapping table. Coverage is not guaranteed: a taxonomy class with
  no mapped raw label simply never appears as a candidate.
- No ASR model was selected on evidence. `ashrafulparan/whisper-small-bangla` is pinned because it is
  Apache-2.0, Bengali and small enough for the demo machine's CPU — not because it beat anything.
  Dialect coverage is unknown to us, and Bangla varies substantially by district.
- We can make no claim about how any of these perform, and may not borrow one (`COMMON-CON-002`,
  ADR-0011). Until `docs/eval-report.md` exists, the correct answer to "how accurate is it" is "we
  have not measured it yet".

**How the design mitigates this — honestly, and only partly.** First, human approval on every case and
every path (ADR-0003) means no model output reaches a farmer: a model that is wrong in the field
produces a bad *suggestion to an officer*, not bad advice to a farmer. That is the mitigation carrying
the weight, and it is why we can ship unknown weights at all. Second, two-threshold routing
(ADR-0006): a model out of its depth tends to produce lower top-1 confidence, routing the case to the
human-curated knowledge base or to the officer with nothing pre-filled — so degraded model quality
degrades into more human work rather than into wrong answers. That is real but partial, and does
nothing about confident errors, which is what an uncalibrated model produces on unfamiliar input.
Third, own-evaluation reporting (ADR-0011) tells us what we actually have. None of these makes the
model good; they make the *system* safe while the model is unknown.

## Alternatives considered

**Fine-tune as the plan recommends.** Rejected: no infrastructure, no time, and an agent-executed
build. Not deferred, because a deferred training task is an invitation.

**Keep it as `[DEFERRED]` with the seam visible**, consistent with every other cut. Rejected on
exactly that reasoning: this is the one cut where a visible seam costs more than it is worth, because
the seam is a task an agent can start.

**Run the ASR bake-off but not the vision fine-tune.** Rejected. A bake-off is a model-selection
benchmark, it consumes a Day-1 morning, and it produces a number we could not quote anywhere outside
`docs/eval-report.md` anyway.

**Use a single multi-crop model instead of three.** Rejected on availability: no pinned open-weight
model covers all 14 classes across the three crops. Three models plus a mapping table (ADR-0009) is
the consequence, not a preference.

## Revisit when

Revisit when **all three** hold, not before: a GPU is available for more than a single session; a
labelled dataset of Bangladeshi field photographs exists with an owner and licence recorded in
`CONTENT-OWNERS.md`; and `docs/eval-report.md` shows a specific named disease class whose top-1
accuracy on our own held-out set is the binding constraint on officer workload. The change is then
scoped and measurable — train for that class, evaluate on the same held-out set, swap
`foshol.ai.vision.*.model-id`. Until all three hold, `COMMON-CON-001` is enforced by review of every
document and every commit.
