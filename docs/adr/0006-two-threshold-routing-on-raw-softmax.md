# ADR-0006: Two-threshold routing on raw softmax top-1; margin shown, not used

- **Status:** Accepted
- **Date:** 2026-09-07
- **Deciders:** project lead (human), requirements agent
- **Supersedes / relates to:** plan §3, clarification 8; relates to ADR-0003, ADR-0008, ADR-0011; properties in `00-common.ears.md` §9.1

## Context

The pipeline must route a case down one of three paths — `PRIMARY`, `SECONDARY`, `UNDETERMINED` — on
the strength of the vision model's output. Plan §3 fixes two thresholds rather than one, and the
reasoning is sound: a single threshold makes the fallback feel binary and arbitrary, while two produce
three visibly different behaviours and therefore three distinct demo cases.

What the plan does not settle is *which number* the thresholds are applied to. The candidates are raw
softmax top-1; temperature-scaled softmax, which needs a held-out calibration set and a fitting step;
`margin = top1 − top2`, which is arguably a better uncertainty signal than absolute probability; and
entropy over the full distribution. The last three are all more principled than the first. The reason
we do not take them is not that they are wrong; it is that this build has four days, no calibration
set, and a demo whose value depends on three cases behaving identically every time.

## Decision

**Routing compares raw softmax top-1 confidence against two configured thresholds.**

| Condition | Path |
|---|---|
| `top1 ≥ foshol.analysis.confidence.high` (`0.75`) | `PRIMARY` — model diagnosis, remedies from the knowledge base |
| `foshol.analysis.confidence.low` (`0.45`) `≤ top1 < high` | `SECONDARY` — knowledge-base symptom match merged with vision candidates, re-ranked |
| `top1 < low`, **or** the knowledge-base match is inconclusive | `UNDETERMINED` — nothing pre-filled, the officer diagnoses |

Both values are properties, never literals (`COMMON-NFR-020`), and both are displayed read-only on the
admin stats page so the two-threshold story is on screen rather than in a slide (clarification 20).
The officer console draws both as vertical rules on the confidence bar, so a reader can see where a
case sat relative to the boundaries.

**`margin = top1 − top2` is computed, persisted in `analysis_run.margin`, carried on
`AnalysisCompleted`, displayed to the officer — and it does not route.** It is decision support for a
human, not an input to a branch. An officer seeing `top1 = 0.78, margin = 0.02` can tell that the
model nearly picked something else, which is exactly when a human should look harder. Making that same
observation route the case would mean two interacting thresholds, four behaviours instead of three,
and a rule harder to explain than it is worth.

**`foshol.analysis.confidence.temperature` exists and defaults to `1.0`.** The knob is present and the
division is in the code path; at `1.0` it is the identity. Calibration itself is `[DEFERRED]`
(`00-common.ears.md` §1.2) because it needs a calibration set the project will not have in time.
Leaving the property in place makes calibration later a fitted number in a properties file rather than
a change to the routing code.

**Why reproducibility beats calibration here.** The demo runs three prepared cases and must produce
`PRIMARY`, `SECONDARY` and `UNDETERMINED` in that order every time, including on the run where the
sidecar is killed. Raw softmax in replay mode (ADR-0010) is deterministic: the same image SHA-256
returns the same fixture, the same top-1, the same threshold crossing. A calibration step introduces a
fitted parameter that depends on which set was used and when it was last refitted — and a refit on the
morning of the demo silently moves a case across a boundary. A calibrated model that occasionally
routes case 2 as `PRIMARY` is worse for this build than an uncalibrated one that never does, because
the human approval gate (ADR-0003) already absorbs the correctness risk calibration would reduce.

## Consequences

### Positive

- Three deterministic, rehearsable paths. Case 2 is `SECONDARY` on every run, on every machine.
- The rule is two comparisons: explainable in one sentence and testable in three unit tests.
- Both thresholds are properties, so the demo can be re-tuned against the actual fixtures without a
  rebuild — and the tuning is visible on the admin page rather than buried.
- Margin gives the officer a genuine second signal at zero routing complexity.
- The temperature knob keeps calibration a one-property change, so deferring it costs nothing later.

### Negative / accepted cost

- **Raw softmax confidence is not a probability of correctness.** Neural classifiers are commonly
  overconfident, and a model applied to inputs unlike its training data can be confidently wrong. We
  are routing on a number that does not mean what its scale suggests, and we say so.
- `0.75` and `0.45` are not derived from anything. They are the plan's values and they are content
  owned by a human (`CONTENT-OWNERS.md` C11), not a fitted result.
- Because confidence is uncalibrated, the same threshold means different things for different models —
  and this build runs three vision models (ADR-0009) with three label spaces and three training
  histories. One global pair of thresholds is a simplification.
- Margin displayed but not routing may read as an omission to anyone who has not read this ADR.
- Multi-image aggregation is `MAX` per class (`foshol.analysis.multi-image.aggregation`), which is
  explainable and never dilutes one good photograph — and also means one confidently misclassified
  image can carry a case to `PRIMARY` alone.

## Alternatives considered

**One threshold.** Rejected per plan §3: binary, arbitrary-feeling, and it collapses two of the three
demo cases into one.

**Route on margin, or on `top1` and `margin` jointly.** Rejected. Better uncertainty signal, worse
explanation, and it turns three paths into a two-dimensional decision that cannot be drawn on a
confidence bar. Margin earns its place as a human-facing signal instead. Entropy is rejected for the
same reasons and is not displayable at all.

**Calibrate first, then set thresholds.** Rejected on time and on determinism. It needs a held-out
calibration set, a fitting step, and re-derivation whenever a model id changes — and the safety
argument it would strengthen is already carried by mandatory human review (ADR-0003).

## Revisit when

Revisit when `docs/eval-report.md` exists over a held-out set of at least 100 images per crop
(ADR-0011) **and** its confusion matrix shows a disease class whose errors cluster on one side of a
threshold — that is, when there is project-owned evidence that a specific threshold sits in the wrong
place for a specific class. The first move then is fitting `temperature` on the held-out set, which
changes one property; only if that fails to separate the classes does the routing rule itself come
back up for discussion.
