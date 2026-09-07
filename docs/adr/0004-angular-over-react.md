# ADR-0004: Angular with signals, not React + Vite

- **Status:** Accepted
- **Date:** 2026-09-07
- **Deciders:** project lead (human), requirements agent
- **Supersedes / relates to:** **supersedes plan §8**; relates to ADR-0016; recorded in `00-common.ears.md` §2.2, §2.3; enforced by `COMMON-NFR-003`, `COMMON-NFR-004`, `COMMON-NFR-039`, `COMMON-NFR-043`

## Context

Plan §8 specifies React + Vite + TypeScript + Tailwind for three surfaces: the farmer app, the officer
console, and a read-only admin stats page. The project lead directed Angular instead. This ADR records
that decision and the two sub-decisions that follow from it — state management and
internationalisation — so no agent re-litigates any of them mid-build.

The frontend is one agent's workstream (A6) against a frozen OpenAPI snapshot. What matters for a
four-day parallel build is not framework preference but how much of the surface is generated,
conventional and unarguable. Angular's opinionatedness — one router, one HTTP client, one CLI, one
project layout — is an asset when the author is an agent, because there are fewer decisions available
to make inconsistently across three surfaces.

## Decision

**Angular, standalone components, pinned to an exact version.** No `^` or `~` on any `@angular/*`
package. The exact version is resolved by `ng version` at kickoff and written into
`00-common.ears.md` §2.3 by the platform agent (`COMMON-NFR-003`); until then the document carries the
token `22.1.5`, and `COMMON-NFR-004` requires A6 to treat itself as blocked
while that token is still present. An agent guessing a version number is a build break on Day 2.

**State: Angular signals. No NgRx, no RxJS store, no third-party state library.** The application's
state is a queue, a case, an advisory draft and an SSE stream. Signals cover that with no store-shaped
indirection between a component and the API client; NgRx would add actions, reducers, effects and
selectors around what is fundamentally request/response plus a server-sent event stream.

**API client: `ng-openapi-gen`, generated from `docs/openapi/foshol-api.yaml`.** The generated output
in `web/src/app/generated/` is never hand-edited (`COMMON-NFR-043`). The snapshot is frozen on Day 0,
so A6 builds against types that exist before any backend module does.

**i18n: `ngx-translate`, not Angular's built-in `$localize`.** This is the sub-decision with a real
technical argument behind it. `$localize` is build-time: each locale is a separate compiled bundle, so
switching language means loading a different build and both surfaces ship twice. `ngx-translate`
resolves strings at runtime from `assets/i18n/bn.json` and `en.json`, which makes the BN/EN toggle
(`COMMON-NFR-039`) a button that flips a signal rather than a reload into a second deployment. On
stage, a judge who reads no Bangla needs to flip that switch mid-sentence and see the same screen in
English. That single interaction is worth more than `$localize`'s compile-time extraction guarantees.

Bangla is the language of record for content (`COMMON-NFR-037`); the toggle covers UI chrome fully and
content best-effort, with `<field>Fallback: true` marking a Bangla value shown in an English UI
(`COMMON-NFR-038`).

**Styling: Tailwind CSS 4.x. Charts: none** — the confidence bars showing both thresholds are CSS,
which is fewer bytes and more controllable than a chart library for two vertical rules on a bar.

## Consequences

### Positive

- One router, one HTTP client, one CLI, one testing setup. An agent has fewer opportunities to invent
  a second way of doing anything.
- The generated client means the frontend contract cannot drift from the backend contract silently —
  regeneration either compiles or it does not.
- Runtime language switching is a first-class demo beat rather than a build artefact.
- Signals keep the state layer small enough to read in one sitting, which matters when one agent owns
  three surfaces in four days.
- Exact pinning removes the commonest Day-2 frontend failure: a transitive minor bump that changes
  template compilation.

### Negative / accepted cost

- The plan, its schedule and its component sketches are written for React. Every §8 reference must be
  read as advisory, and this ADR is the only place the discrepancy is reconciled.
- Angular's initial bundle is larger than a comparable Vite + React build. Irrelevant on a local demo
  machine; it would matter on a rural mobile connection, which is the actual deployment context and is
  out of scope here. We note it rather than pretend it away.
- `ngx-translate` gives up compile-time detection of missing keys — a missing key renders as the key.
  Mitigation is a key-parity lint over `bn.json`/`en.json`, not a compiler.
- Signals are comparatively new, and much Angular material an agent may draw on predates them and
  reaches for RxJS `BehaviorSubject`. `00-common.ears.md` §2.2 states the rule flatly for that reason.
- Service worker and Web Push are `[DEFERRED]`, so the app is not installable. Deliberate
  (clarification 18), not an oversight.

## Alternatives considered

**React + Vite as the plan specifies.** Rejected by the project lead. Recorded here so the deviation
is visible rather than discovered.

**Angular with NgRx.** Rejected. The state is small and mostly server-owned; a store would add four
concepts per feature and buy nothing this application needs.

**Angular with `$localize`.** Rejected on the runtime-toggle argument above. Two builds to show a
judge one screen in two languages is the wrong trade.

**A hand-written API client.** Rejected. Generation from the frozen snapshot is what lets A6 start
before the backend exists.

## Revisit when

Revisit the i18n choice if a third locale is added *and* bundle size on a metered mobile connection
becomes a measured problem — at that point `$localize`'s per-locale bundles become the right trade and
the runtime toggle can be dropped, because judges are no longer the audience. Revisit the state choice
the first time two unrelated surfaces need to share mutable client-side state that neither owns; until
then, signals are sufficient and a store is speculative.
