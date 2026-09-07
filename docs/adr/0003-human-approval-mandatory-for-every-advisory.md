# ADR-0003: Human approval is mandatory on every case, on every path

- **Status:** Accepted
- **Date:** 2026-09-07
- **Deciders:** project lead (human), requirements agent
- **Supersedes / relates to:** plan §1 (F7), §3; relates to ADR-0006, ADR-0008, ADR-0012, ADR-0013; enforced by `COMMON-NFR-035`

## Context

The system tells farmers what to spray on food crops. A wrong answer is not a bad user experience; it
is a wasted season, money a smallholder does not have, or a pre-harvest interval violated on produce
that goes to market. `00-common.ears.md` §1 states the one sentence the whole system exists to make
true: *no farmer in this system has ever received unverified pesticide advice.*

There is an obvious temptation on a four-day build with a two-threshold router (ADR-0006): let the
`PRIMARY` path — top-1 at or above `foshol.analysis.confidence.high` — publish automatically, and send
only uncertain cases to an officer. It would make the demo faster and the throughput better.

It would also be wrong, for a reason that has nothing to do with caution. The confidence value is a
raw softmax output from a pretrained model we did not train (ADR-0008) and have not calibrated
(clarification 8). A confident wrong answer is exactly the failure mode of an uncalibrated model on
out-of-distribution input, and a Bangladeshi field photograph is out of distribution for weights
trained on controlled-background datasets. High confidence is not evidence of correctness; it is
evidence that the input looked familiar.

## Decision

**Every case reaches a field officer before any advice reaches the farmer. There is no path that
skips review, and no configuration property that can create one.**

- Every terminal analysis outcome creates a `review_task`. `AnalysisCompleted` creates one on all
  three decision paths; `AnalysisFailed` creates an `UNDETERMINED` task rather than none
  (`00-common.ears.md` §6.3).
- `advisory.officer_id` is `NOT NULL` (`00-common.ears.md` §4.6). The schema cannot express an
  advisory that no officer published. This is the load-bearing constraint: the rule is enforced by the
  database, not by a service method an agent might refactor.
- `diagnosis_case.status` reaches `ADVISED` only via `AdvisoryApproved`, which only `review`
  publishes. `notification` is driven by `AdvisoryApproved`, `AdvisoryRevised` and `CaseRejected` —
  never by `AnalysisCompleted` — so no wiring exists by which analysis output alone reaches a farmer.
- The decision path changes what the officer is *shown*, never whether the officer is *involved*.
  `PRIMARY` pre-fills a diagnosis and remedies, `SECONDARY` a knowledge-base shortlist,
  `UNDETERMINED` nothing. All three render the same four actions: Approve, Edit, Replace, Reject.

**The sidecar-down case is explicitly in scope and is the demo's strongest beat.**
`COMMON-NFR-035`: if the sidecar circuit is open or a call fails after retry, `analysis` falls back to
`UNDETERMINED`, records `analysis_run.error_code`, **and still publishes `AnalysisCompleted`** so the
case reaches the officer queue. Killing the inference container mid-demo degrades the system to a
human-only triage queue. It does not drop cases, it does not fail submissions, and it publishes
nothing unreviewed. That is the difference between an AI product and an AI feature: the human path is
the product, and the model is a triage accelerator that sorts the queue.

## Consequences

### Positive

- The safety claim is structural. There is no path to a published advisory that does not pass through
  a `NOT NULL` officer id.
- Model quality stops being a release gate: we can ship pretrained weights of unknown field accuracy
  (ADR-0008) because no output of theirs is authoritative.
- Pull the plug on inference and the product still works, slower — degradation as a feature demo
  rather than an incident.
- The agreement-rate metric comes free, because every case carries both the model's top candidate and
  the officer's decision. The queue sorted least-confident-first (`REVIEW-FR-030`) then sends human
  attention where the model is weakest.

### Negative / accepted cost

- **Throughput is bounded by officer capacity, not by inference capacity.** This is a real product
  limitation and we state it rather than hide it. At demo scale it is invisible; at national scale it
  is the binding constraint.
- Latency to the farmer is human latency. `foshol.review.sla=PT4H` sets the expectation. Every case
  costs officer time, including the ones the model got obviously right, and there is deliberately no
  auto-approve relief valve.
- Officer fatigue is a genuine risk: a reviewer approving a long run of confident cases may start
  rubber-stamping. Queue ordering and the confidence bars mitigate it; nothing here measures it.

## Alternatives considered

**Auto-publish on the `PRIMARY` path, review only `SECONDARY` and `UNDETERMINED`.** Rejected. It makes
correctness depend on threshold calibration we explicitly deferred (ADR-0006), and it puts the system
one property change away from publishing unverified pesticide advice.

**Auto-publish for non-chemical remedies only.** Rejected. It sounds safer and is not: a wrong
cultural remedy still costs a farmer a season, and it is the diagnosis that is wrong, not the remedy
type.

**Post-hoc review — publish immediately, let the officer retract.** Rejected outright. Retraction does
not un-spray a field, and it inverts the sentence the product exists to make true.

**Review as a configurable policy per district or crop.** Rejected: a property that can disable the
safety property is the safety property being optional.

## Revisit when

Revisit only when there is a project-owned evidence base large enough to argue about — concretely,
when `docs/eval-report.md` covers a held-out set drawn from real Bangladeshi field photographs *and*
the accumulated model-vs-officer agreement rate exceeds a threshold the project lead sets in advance,
on a sample large enough to be meaningful per disease class rather than in aggregate. Even then the
first change is not auto-publish: it is auto-publish for a single named disease class with a monitored
retraction rate. This ADR does not authorise that step; it requires a new one.
