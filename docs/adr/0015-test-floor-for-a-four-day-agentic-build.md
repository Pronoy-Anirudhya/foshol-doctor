# ADR-0015: A test floor, not a coverage target

- **Status:** Accepted
- **Date:** 2026-09-07
- **Deciders:** project lead (human), requirements agent
- **Supersedes / relates to:** plan clarification 21; relates to ADR-0001, ADR-0010; Definition of Done in `00-common.ears.md` §11

## Context

Four days, seven agents, one integration gate at the end of each day (`00-common.ears.md` §12.2).
Tests have to do two jobs here, and they are not the same job.

The first is the usual one: catch regressions. The second is specific to an agentic build — **tests
are the only thing standing between a plausible-looking implementation and a wrong one.** An agent
produces code that compiles, reads well, follows this repository's conventions, and is subtly wrong
about a business rule. No human reads every line. A failing test is the only signal that arrives.

That argues for a lot of tests. Against it: test-writing consumes the same four days as
implementation, and an integration test per endpoint would consume most of them. Testcontainers starts
Postgres and MinIO; a suite of dozens of such tests takes long enough that agents stop running it
locally, which is worse than not having it.

So the question is not "how much testing" but **which tests catch the failures this build actually
produces**: a domain invariant implemented wrongly; a handler that does the right thing to the wrong
aggregate; a module boundary crossed; a JPA entity drifted from its migration; an event that never
reaches its listener.

## Decision

A **floor**, stated as an obligation, with no coverage percentage anywhere. `00-common.ears.md` §11
makes every line of it part of the Definition of Done.

1. **Unit tests on every domain invariant.** Every business rule stated as an invariant on the
   aggregate that owns it gets a test that violates it and asserts the exception. Fast, no Spring
   context, and where wrong business logic is caught.
2. **Unit tests on every command handler and every query handler** — not a sample, every one. Handlers
   are the seam where an agent's understanding of a requirement becomes code, and there is one handler
   per requirement-shaped operation. Collaborators are mocked.
3. **`ApplicationModules.verify()` and the ArchUnit rule set, once, in the platform module.**
   `ModularityTests` and `ArchitectureTests` (`COMMON-ARCH-001` … `COMMON-ARCH-013`) run on every
   build — once, centrally, because a boundary violation is a property of the whole graph.
4. **Exactly one Testcontainers integration test per module**, covering that module's primary happy
   path against real Postgres and real MinIO. Six in total. It proves the wiring: the JPA mapping
   matches the Flyway migration (which `ddl-auto=validate` also guards, `COMMON-DATA-002`), the
   repository query runs, the transaction boundary is real.
5. **One end-to-end slice test** — submit → analyse → review → advisory — run in **both** `replay` and
   `live` mode (`COMMON-NFR-024`, ADR-0010). This is what proves the event chain delivers across all
   six modules.

**No per-endpoint integration tests.** Controllers are thin by architectural law (`COMMON-ARCH-009`:
bind, delegate to exactly one handler, map the result), so a test per endpoint would mostly re-test
Spring's binding. Handler unit tests cover the logic; the OpenAPI snapshot covers the contract shape.

## Consequences

### Positive

- The suite stays fast enough that agents run it: unit tests dominate, seven container-based tests in
  total across the whole build.
- The floor is auditable rather than statistical. "Does every handler have a test?" is answerable by
  listing files; "is coverage above 80%?" is answerable by writing tests that assert nothing.
- The failure modes unique to this build — wrong business logic, crossed boundaries — are exactly what
  items 1–3 target.
- One integration test per module is a bounded obligation that does not grow with endpoint count.
- The end-to-end test in both modes is the only thing proving the whole system works, and it runs on
  every push in at least one mode.

### Negative / accepted cost

This section is the point of the ADR. Here is what is **not** verified.

- **Endpoint behaviour is largely untested.** Status codes, the RFC 9457 problem envelope
  (`COMMON-API-002`), `Accept-Language` localisation, pagination defaults and caps, and `413`/`415`
  handling on upload are exercised only where the one integration test or the slice test happens to
  touch them. A controller returning `403` where `COMMON-API-001` requires `404` would likely ship.
- **Authorisation is thinly covered.** `COMMON-SEC-011`'s per-role rules are stated per endpoint and
  tested at almost none of them. A farmer reading another farmer's case is the failure this leaves
  most exposed. Ownership checks live in handlers, which are unit tested — but the filter chain wiring
  is not.
- **Only the happy path is integration tested per module.** Failure paths through real infrastructure
  — MinIO unavailable during submission (`COMMON-NFR-036`), the sidecar circuit opening
  (`COMMON-NFR-035`), a migration conflict — are mocked or untested.
- **Concurrency is untested**: the optimistic-lock version columns, the claim TTL sweeper, and two
  officers claiming the same task have no test that exercises them concurrently.
- **The frontend has no automated tests in this floor**; `web/` is verified by the slice test's
  backend contract and by hand.
- **Performance budgets (`00-common.ears.md` §10.2) are asserted nowhere.** No test fails when p95
  regresses.

**Why that risk is accepted** — two specific reasons, not a general shrug. First, mandatory human
review (ADR-0003) backstops the whole category of correctness bugs that would otherwise reach a
farmer: a wrong candidate ranking or a bad remedy prefill is caught by an officer, because an officer
sees every case. That is *not* true of authorisation bugs, which is why the exposure above is named
rather than waved away. Second, the deployment target is one laptop running one demo (clarification
22): the threat model contains no adversary and the load model is a handful of concurrent cases. Both
assumptions are stated so that the moment either changes, this ADR is what gets revisited — not the
moment someone merely feels the suite is thin.

## Alternatives considered

**A coverage percentage — 80% line coverage as a gate.** Rejected. Gameable by construction: an agent
asked to raise coverage writes tests that execute code without asserting behaviour.

**An integration test per endpoint.** Rejected on time and suite runtime; it would mostly test Spring.

**Unit tests only, no Testcontainers.** Rejected. Everything mocked verifies nothing about JPA
mappings, migrations or transactions — precisely what breaks when seven agents write entities against
one schema.

**Contract tests between modules.** Rejected as redundant: `api` packages are frozen
(`COMMON-NFR-041`) and shared at compile time, so the compiler is the contract test.

**Testing after the build, on Day 4.** Rejected outright. Tests written afterwards document what the
code does rather than what it should do, which for agent-written code is worthless.

## Revisit when

Revisit the moment the deployment target changes — the first run reachable by anyone outside the
project team. The two named exposures then become unacceptable in a specific order: authorisation
tests per endpoint first (a farmer must not read another farmer's case), then failure-path integration
tests. Separately, revisit if a defect escapes to an integration checkpoint twice in the same
category; two escapes of one kind is evidence the floor is missing a rung, and the fix is to add that
specific test class, not to raise a coverage number.
