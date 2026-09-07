# ADR-0012: Rejection is terminal; a resubmission is a new linked case

- **Status:** Accepted
- **Date:** 2026-09-07
- **Deciders:** project lead (human), requirements agent
- **Supersedes / relates to:** plan clarification 5; relates to ADR-0013, ADR-0014; schema in `00-common.ears.md` §4.4, §4.6

## Context

An officer reviewing a case has four actions: Approve, Edit, Replace and Reject. Reject is used when
the submission cannot be diagnosed at all — blurry photograph, not a crop, wrong crop, insufficient
detail, inaudible audio (`case_rejection.reason_code`, `00-common.ears.md` §4.6).

The question is what happens next. Two models are available. **Reopenable:** the case returns to a
state where the farmer can add or replace images, then re-enters analysis and review — one case, many
attempts. **Terminal:** the case ends as `REJECTED` and the farmer submits a new case, linked to the
old one.

Reopenable is what most workflow systems do and what a product manager asks for, because it feels
kinder: "fix your photo" rather than "start again". It is also considerably more state machine.
`diagnosis_case.status` gains a reopen transition; every downstream artefact — `analysis_run`,
`case_candidate`, `case_symptom`, `review_task`, and both projections — must decide whether it is
replaced, versioned or appended; `review_task.case_id` is `UNIQUE`, so a second review needs either a
new row and a relaxed constraint or a reused row with mutated state; and the idempotency story
(ADR-0014) becomes ambiguous, because a resubmission of the same image on a reopened case is both a
legitimate new attempt and a possible duplicate. Every one of those is a decision seven parallel
agents would have to make consistently, from documents, without talking to each other.

## Decision

**Rejection is terminal. `diagnosis_case.status` reaches `REJECTED` and never leaves it.**

The rejection is recorded in `case_rejection` — one row per case, enforced by `case_id … UNIQUE` —
carrying the officer id, a `reason_code` from a fixed set, and a Bangla `message_bn` the farmer
actually reads. Reason codes and their Bangla message templates are human-supplied content
(`CONTENT-OWNERS.md` C10); an agent authors the structure, not the wording.

`review` publishes `CaseRejected`; `notification` messages the farmer; `intake` moves the case to
`REJECTED`. The farmer's case detail view offers one action: **submit a new case**.

**The new case carries `parent_case_id`** — `uuid REFERENCES diagnosis_case(id)`
(`00-common.ears.md` §4.4, `[DERIVED]`), indexed by `ix_case_parent`. It is propagated on
`CaseSubmitted` and surfaces as `p_officer_queue.is_resubmission`, so the officer console shows at a
glance that a case is a second attempt and can link back to what was rejected and why. The chain is
preserved without the state machine ever going backwards.

**Case data is never deleted.** Soft delete (`deleted_at`) exists on knowledge entities only
(clarification 15); a rejected case, its images, its analysis run and its rejection reason are
permanent, so the full history of what a farmer submitted and what an officer said stays readable.

## Consequences

### Positive

- **The state machine is a directed acyclic graph**: `SUBMITTED → ANALYSING → ANALYSED → IN_REVIEW →`
  one of `ADVISED` / `REJECTED` / `FAILED`. No back edge means every module's listener can assume a
  case's status only ever moves forward — worth a great deal to seven agents implementing seven
  listeners from a document.
- Every artefact attached to a case describes exactly one submission. `analysis_run`,
  `case_candidate`, `case_symptom` and `review_task` need no attempt number, and
  `review_task.case_id UNIQUE` holds without qualification.
- The audit trail is naturally immutable: what was submitted, what the model said, what the officer
  said, and what came next, each as its own case.
- Idempotency (ADR-0014) stays simple: a resubmission is a new case with a new key, so there is no need
  to distinguish "duplicate request" from "legitimate second attempt".
- Projections are append-and-update by case id, never rebuild-on-reopen.

### Negative / accepted cost

- **The farmer re-uploads everything**, including the images that were fine, because one of three was
  blurry. On a rural mobile connection that is a real cost in data and patience, and it is the clearest
  price of this decision. Nothing in this build mitigates it — carrying good images forward would be
  genuinely useful and is not in scope.
- The farmer's history grows a chain of cases for what they think of as one problem. `parent_case_id`
  makes the chain visible, but the list is longer and noisier for it.
- A long chain — case 3 whose parent is case 2 whose parent is case 1 — is representable and nothing
  summarises it. The console shows "is a resubmission"; it does not show depth.
- Metrics need care: counting cases overstates distinct farmer problems wherever resubmissions occur.
- The officer cannot ask for one specific thing and keep the rest of the case alive. Reject is
  all-or-nothing.

## Alternatives considered

**Reopenable cases.** Rejected on state-machine complexity. The kinder user experience is real, and it
costs a back edge in the status graph plus a versioning decision on five tables — a poor trade on a
four-day parallel build.

**A `RESUBMISSION_REQUESTED` status that accepts new images onto the same case.** Rejected: this is
the reopenable model with a nicer name, and the same five tables still have to decide what happens to
their existing rows.

**Reject with no link — the farmer simply starts over.** Rejected. Simpler still, and it throws away
the one thing the officer most wants when reviewing the second attempt: what was wrong with the first.

**Let the officer edit the case in place — rotate, crop, drop a bad image.** Rejected. It puts the
officer in the position of authoring the evidence they are about to diagnose from, which is the wrong
role, and it is a substantial UI surface.

## Revisit when

Revisit once `case_rejection` holds enough rows to show that a single reason code dominates. If most
rejections are `BLURRY_IMAGE` on one of three images, the right fix is not a reopenable case: it is
carrying the non-rejected images forward into the child case automatically, which keeps the terminal
state machine and removes most of the re-upload cost. Revisit the terminal model itself only if a use
case appears that genuinely needs one case identity across attempts — an external reference number a
farmer quotes to an extension office, say — since `parent_case_id` chains do not provide one.
