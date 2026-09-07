# ADR-0010: Replay and live are both mandatory, and the mode is recorded

- **Status:** Accepted
- **Date:** 2026-09-07
- **Deciders:** project lead (human), requirements agent
- **Supersedes / relates to:** plan §5; relates to ADR-0006, ADR-0008, ADR-0015; enforced by `COMMON-NFR-021` … `COMMON-NFR-024`, `COMMON-UX-001`

## Context

Plan §5 makes replay mandatory and a Day-2 deliverable: `foshol.ai.mode=replay` serves recorded
sidecar responses keyed by image SHA-256, and the demo runs in replay unless the live path has been
stable for a full day. We go further — replay is a **Day-1** deliverable, because every downstream
module's integration test depends on deterministic analysis output, and A2 through A5 cannot finish
without it.

Making replay the default creates a specific hazard, worth naming because teams actually hit it. **A
system that runs only in replay is not a system; it is a very elaborate fixture.** Every path
downstream of `analysis` can be green, every test can pass, the demo can be flawless — and the live
inference path can be broken in a way nobody has exercised since the day it was written. The discovery
moment is a judge asking to try their own photograph.

The second hazard is subtler and worse: **a team that cannot tell which mode produced a result will
mislead itself.** Someone sees a case at `PRIMARY` with high confidence and a Grad-CAM overlay
highlighting exactly the right lesion, and concludes the model is performing well. It was a fixture.
That conclusion then propagates into a threshold change, a slide, or an answer to a judge — and none
of it was ever an inference.

## Decision

**Both modes are fully functional and neither is optional** (`COMMON-NFR-021`).

**Replay** (`COMMON-NFR-022`): while `foshol.ai.mode=replay`, `analysis` obtains every sidecar response
from the fixture store keyed by image SHA-256 — or audio SHA-256 for the ASR branch — and records
`analysis_run.mode = 'REPLAY'`. Fixtures live in `sidecar/fixtures/` and include pre-baked Grad-CAM
PNGs, so the overlay renders in replay too. They are recorded from a live sidecar by
`tools/build_fixtures.py`, so fixture content is *real model output*, not invented data. Replay is the
default under `demo` and `test`.

**Live** (`COMMON-NFR-023`): while `foshol.ai.mode=live`, `analysis` calls the sidecar over HTTP and
records `analysis_run.mode = 'LIVE'`. Live is the default under `local`, so ordinary development
exercises the real path.

**Live is exercised in CI, in its own workflow** (`COMMON-NFR-024`). `verify.yml` runs build, unit
tests, ArchUnit, Modulith and replay-mode integration tests on every push. `verify-live.yml` brings the
sidecar up via `docker compose` with a cached Hugging Face model directory and runs the end-to-end
slice test in `live` mode. It downloads weights and is allowed to be the slow job — the workflow is
written to tolerate that rather than to be fast. Separating them keeps the fast gate fast and the slow
gate existing.

**The mode is recorded on every run and shown to every officer.** `analysis_run.mode` is `NOT NULL`
with a `CHECK` over `('REPLAY','LIVE')`, carried on `AnalysisCompleted` as `AiMode mode`, projected
onto `p_officer_queue.analysis_mode`, exposed on `AnalysisView`, and rendered on every case detail view
(`COMMON-UX-001`). Presenter, reviewer and judge all see the same badge on the same screen.

## Consequences

### Positive

- The demo is deterministic: three prepared cases produce the same three decision paths on every run
  (ADR-0006), independent of machine, CPU load or model download state.
- Downstream modules become testable without any model running — A2's intake test, A5's review test
  and the end-to-end slice test all have deterministic analysis output on Day 1.
- The live path cannot rot unnoticed. A break in HTTP wiring, sidecar schema, timeout handling or
  model loading fails `verify-live.yml`.
- Fixtures are recorded from real inference, so replay reflects what the models actually produce.
- Persisted Grad-CAM overlays (`analysis_run.gradcam_object_key`) render in both modes, so the wow
  factor survives the sidecar being killed mid-demo (`COMMON-NFR-035`).
- Nobody can look at a result without knowing what produced it. That is the whole point of
  `COMMON-UX-001`.

### Negative / accepted cost

- Two paths to maintain and two ways for one feature to behave. The fixture store is real code with
  real bugs, and a fixture-store bug looks exactly like a model bug.
- Fixtures go stale silently. A new demo photograph has a SHA-256 with no fixture, so an unmatched key
  must fail loudly rather than fall through to a default.
- `verify-live.yml` is slow and depends on an external model host. It will occasionally fail for
  reasons unrelated to our code, and the team must resist disabling it when it does.
- Recording fixtures requires a working live sidecar, so replay is downstream of live even though it
  ships first.
- Two modes mean two workflows, two profiles' worth of configuration, and one more column, event field
  and UI element that every module carries through.

## Alternatives considered

**Replay only.** Rejected on the argument above: a system that only ever runs in replay is not a
system, and the failure is discovered by a judge rather than by CI.

**Live only.** Rejected. Non-deterministic demo paths, model download in the critical path of every
test run, a CI suite dominated by inference, and a three-path demo that cannot be rehearsed.

**Live with a stub sidecar in tests — a mock rather than recorded fixtures.** Rejected. A mock returns
data someone invented; a fixture returns data a model produced. When the two disagree, the mock is
wrong, and only the fixture would have caught it.

**Record the mode but do not show it.** Rejected, and this was a live suggestion. It is the cheaper
half of the decision and it drops the part that protects the team from itself: the column without the
badge means the information exists and nobody consults it.

**Choose the mode per request rather than per deployment.** Rejected: it makes the fixture path
reachable in a live deployment, which is a way to serve fabricated inference results to a real user.

## Revisit when

Revisit the replay default for the demo when the live path has run green in `verify-live.yml` for a
full working day *and* the end-to-end p95 in live mode sits inside `foshol.analysis.deadline` on the
actual demo machine — plan §5's two conditions, both measurable. Revisit the fixture store itself the
first time a fixture miss is resolved by adding a fallback response instead of recording a fixture:
that is the point at which replay stops being recorded reality and starts being invented data, and it
must not happen quietly.
