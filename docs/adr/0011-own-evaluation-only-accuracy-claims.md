# ADR-0011: The only accuracy figure is our own held-out evaluation

- **Status:** Accepted
- **Date:** 2026-09-07
- **Deciders:** project lead (human), requirements agent
- **Supersedes / relates to:** **strikes the accuracy and WER figures quoted in plan §6 and §7.1**; relates to ADR-0003, ADR-0008, ADR-0010; enforced by `COMMON-CON-002`

## Context

The plan quotes figures from model cards and papers: a word error rate for one Bangla ASR candidate,
image and class counts for two datasets, parameter counts for vision backbones. Those belong in a
research note. They must not appear in this product, this deck, or these documents, and the rule is
absolute rather than a matter of care.

A published accuracy figure measures **a specific model, on a specific test split, from a specific
dataset, under a specific labelling scheme**. All four differ here:

- Our taxonomy is 14 classes across three crops, defined by this project. No published model shares
  it; two of our three vision models were trained on class sets that only partly overlap ours, and the
  correspondence is a human judgement recorded in `model_label_map` (ADR-0009).
- Our inputs are photographs taken by farmers on handsets in fields. Published figures for models
  derived from controlled-condition datasets are measured on images that look nothing like that, and
  such models are documented to degrade on real field photographs.
- Our ASR inputs are 30-second Bangla clips recorded in a field, across districts whose dialects differ
  substantially. A figure measured on read speech from a curated corpus does not transfer.
- We trained none of these models (ADR-0008), so we have no visibility into their splits and cannot say
  whether our evaluation images leaked into their training data.

A borrowed number therefore describes a system that is not ours, on data that is not ours, against a
label set that is not ours.

**Borrowed numbers are worse than no numbers.** "We have not measured it yet" is a statement of fact
that costs nothing and invites the follow-up "when will you". Quoting a headline figure and being
asked "on what data?" is a claim that collapses under one question, in front of the people whose trust
the system depends on. And the audience that matters most is not the judges: it is a farmer deciding
whether to spray. A borrowed figure that a farmer reads as a statement about how often this system is
right, when it was measured on someone else's controlled-background dataset, is not marketing — it is
misinformation attached to a pesticide decision.

## Decision

**`COMMON-CON-002`: the system states, displays, logs and documents no accuracy, WER, F1 or precision
figure originating from a published model card, paper or third-party benchmark.** The prohibition
covers the product UI, the deck, the README, every requirements document and every ADR including this
one. The figures in plan §6 and §7.1 do not propagate.

**The single permitted source is `docs/eval-report.md`**, produced by `tools/eval.py` from an
evaluation the project runs itself:

- **at least 100 images per crop**, held out and never used to tune anything;
- labelled against **our own 14-class taxonomy**, not any model's native label space;
- run through the same path a real case takes — same sidecar, same configured model ids, same
  `model_label_map` — so what is measured is the system rather than a model;
- reporting **top-1 and top-3 per crop and a confusion matrix**, so the failure structure is visible
  and not just a scalar;
- recording the mode (ADR-0010) and the model ids and versions that produced it, so it is reproducible
  and dated.

If `docs/eval-report.md` does not exist yet, the complete answer to "how accurate is it" is **"we have
not measured it yet"**. That is not a gap to be papered over with a citation. Ground-truth labels for
the held-out set are human-supplied content (`CONTENT-OWNERS.md` C14).

**What we may say instead is stronger.** The claim this product makes is not about model accuracy. It
is that no farmer receives unverified advice (ADR-0003), that every case reaches an officer even when
inference is down (`COMMON-NFR-035`), and that we will publish what our own evaluation finds —
including a confusion matrix showing where the system is weak. Owning a known limitation reads as
competence; being caught by a borrowed number does not.

## Consequences

### Positive

- Every number in the product, the deck and the documents traces to a file in this repository or is a
  threshold property. That rule is greppable and is part of the review gate.
- The evaluation measures the whole system, mapping table included, so it catches a mis-mapped label
  that a model-level figure never would.
- A confusion matrix per crop is directly actionable: it says which classes to improve, which is a
  better artefact than a headline percentage.
- The team cannot mislead itself, because there is no attractive number lying around to reason from.
- The honesty is defensible under questioning, which is the situation that actually arises.

### Negative / accepted cost

- **For part of the build there is no accuracy figure at all**, and the deck has to say so. That is
  uncomfortable and it is the correct discomfort.
- Building the held-out set is real work: sourcing at least 300 images, holding them out, and having a
  human label them against our taxonomy. It is a hard dependency on a content owner with no agent
  substitute.
- 100 images per crop is a small sample. Per-class figures rest on very few images, so the report must
  state per-class counts alongside rates or someone will quote a rate computed over a handful.
- We cannot compare ourselves to published work, having refused the only common currency for it.
- The rule constrains conversation as well as documents: a team member who has read a model card must
  not repeat its figure in a demo answer. That is a discipline, not a mechanism.

## Alternatives considered

**Quote published figures with a caveat.** Rejected. The caveat is read once and the number is
remembered; in a live answer the caveat is the part that gets dropped.

**Quote nothing and run no evaluation.** Rejected. Honest but useless: no basis for threshold
decisions (ADR-0006) and no way to tell whether a model swap helped.

**Evaluate against a public benchmark split instead of our own held-out set.** Rejected. It measures
performance on data unlike our users' photographs, against a label set unlike our taxonomy, and it
reintroduces the leakage question we cannot answer.

**Use the model-vs-officer agreement rate as the accuracy figure.** Rejected as a substitute, kept as a
complement. Agreement rate is valuable and is on the admin stats page — but officers see the model's
suggestion before deciding, so it is subject to anchoring and is not an independent estimate.

## Revisit when

Revisit the *contents* of the claim whenever `tools/eval.py` is re-run: every model id change
(`foshol.ai.vision.*`) invalidates the existing report, and a new one is required before any figure is
quoted again. Revisit the *rule* only if the project begins training its own models against its own
splits — which ADR-0008 currently forbids — because published comparisons become meaningful rather
than borrowed at that point. Until then `COMMON-CON-002` stands and the review gate greps for stray
percentages.
