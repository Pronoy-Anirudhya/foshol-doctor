# ADR-0013: Advisories are append-only and versioned, never updated in place

- **Status:** Accepted
- **Date:** 2026-09-07
- **Deciders:** project lead (human), requirements agent
- **Supersedes / relates to:** plan clarification 6; relates to ADR-0003, ADR-0012; schema in `00-common.ears.md` §4.6; event `AdvisoryRevised` in §6.3

## Context

An officer publishes an advisory and then needs to change it. This happens for ordinary reasons: a
second officer spots a misdiagnosis, the farmer sends more information, a dosage was entered wrongly,
or the officer reconsiders. The plan already declares an `AdvisoryRevised` event
(`00-common.ears.md` §6.3), so revision is in scope; what it does not say is what happens to the
advisory that was already published.

The naive implementation is an `UPDATE`: one statement, one row per case, every read stays trivial. It
is also unacceptable here, for a reason specific to this product. The advisory is pesticide advice a
farmer may already have acted on. If it is overwritten, then afterwards nobody — not the farmer, not
the officer, not the system — can answer "what was this farmer told, and when?" A farmer who sprayed
on Tuesday on advice silently corrected on Wednesday has no way to show what they were told, and the
system has no way to explain the discrepancy. For a product whose entire premise is that a human
verified every piece of advice (ADR-0003), destroying the record of what that human said is
self-defeating.

## Decision

**A revision inserts a new `advisory` row. It never updates the existing one.**
(`00-common.ears.md` §4.6.)

- `advisory.version smallint NOT NULL DEFAULT 1`, `CHECK (version >= 1)`;
- `advisory.supersedes_id uuid REFERENCES advisory(id)`;
- `CONSTRAINT uq_advisory_case_version UNIQUE (case_id, version)`;
- `CONSTRAINT ck_advisory_supersedes CHECK ((version = 1) = (supersedes_id IS NULL))`.

That last constraint does the real work: exactly two shapes are legal — version 1 with no predecessor,
or version *n* naming one. A row claiming to be a revision without naming what it revised, or a first
advisory claiming a predecessor, cannot be written. The chain is database-enforced, not conventional.

The remedy list is a join table, `advisory_remedy (advisory_id, remedy_id, display_order)`, so a
revised advisory gets its own rows and the prior list is untouched. This is also why the plan's
`remedy_ids[]` array column was replaced (`00-common.ears.md` §4.6, `[DERIVED]`): an array cannot be
foreign-key checked, and a versioned array would have to be copied wholesale anyway.

**Prior versions are retained and readable.** `ReviewSubmissionApi` exposes both shapes —
`findPublishedAdvisory(caseId)` returns the current version, `findAdvisoryHistory(caseId)` returns
every version in order — and `ix_advisory_case (case_id, version DESC)` serves both.
`p_farmer_case_history.advisory_version` carries the current version so the farmer's list shows it
without a join.

**A revision sends a second notification.** `review` publishes `AdvisoryRevised`, carrying both
`advisoryId` and `supersededAdvisoryId`, and `notification` sends an `ADVISORY_REVISED` message. A
farmer who has already been told something is told again when it changes; a silent correction to
pesticide advice is the failure this decision exists to prevent. `created_by` and `updated_by` are
populated on every row (clarification 15), so each version records who wrote it as well as which
officer it is attributed to.

## Consequences

### Positive

- **"The farmer can always see what they were told, and when."** That sentence is the safety pitch and
  it is now a property of the schema rather than an intention.
- Revision history is free evidence: an officer reviewing a chain sees what changed and who changed
  it, without an audit log — fortunate, since the admin-visible audit log is out of scope
  (`00-common.ears.md` §1.2).
- Append-only rows are never contended: a revision is an insert, so there is no lost-update race
  between two officers and no optimistic-lock retry on `advisory`.
- The `AdvisoryRevised` event the plan already declared now has a meaning, and carries enough
  information for a consumer to describe the change.
- The second notification means a correction is pushed, not merely available.

### Negative / accepted cost

- Every read of "the current advisory" must select the maximum version rather than the only row.
  `findPublishedAdvisory` encapsulates it, but any query that forgets is subtly wrong — it returns a
  superseded advisory and looks correct. This is the sharpest edge in the decision.
- `advisory_remedy` rows are duplicated per version. Storage is irrelevant at this scale; the cost is
  that a naive count of `advisory_remedy` overstates.
- Two notifications for one case can read as noise if an officer revises twice in quick succession.
  There is deliberately no debounce — suppressing a correction is worse than sending two.
- A farmer may act on version 1 before version 2 arrives. Versioning records the discrepancy; it does
  not prevent it, and nothing in this build measures the window.
- `p_farmer_case_history` holds a single `advisory_id` and version, so the projection shows the current
  version only. History requires the API call.

## Alternatives considered

**Update the advisory in place.** Rejected on the argument above: it destroys the record of what a
farmer was told about a pesticide decision.

**Update in place with a separate `advisory_audit` table.** Rejected. The same data in two shapes, an
audit table that is write-only and untested, and history readable only by reconstructing diffs.
Versioned rows *are* the audit trail.

**Soft-delete the old advisory and insert a new one.** Rejected. `deleted_at` on knowledge entities
means "no longer offered"; here it would mean the prior advisory is gone rather than superseded, which
is the wrong semantics for something a farmer has already read. Case data is never deleted.

**Reject the case and require a new one, as with rejection (ADR-0012).** Rejected. Rejection is for a
submission that cannot be diagnosed; a revision corrects advice on a submission that was perfectly
diagnosable. Forcing a farmer to resubmit photographs to fix a dosage typo is absurd.

**Mutable `advisory` plus event sourcing from `AdvisoryApproved` / `AdvisoryRevised`.** Rejected as
over-engineering: the event registry (ADR-0002) is a delivery mechanism, not a system of record.

## Revisit when

Revisit if an advisory chain routinely exceeds two or three versions on one case — that pattern says
the officer needs a draft state *before* publication rather than more versions after it, and the fix
is a draft, not a schema change. Separately, revisit the projection when a farmer-facing "what changed"
view is requested: `p_farmer_case_history` carries only the current version, and answering that
question well needs the diff between versions, which nothing computes today.
