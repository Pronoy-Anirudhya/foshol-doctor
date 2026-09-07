# ADR-0001: A Spring Modulith modular monolith, not microservices

- **Status:** Accepted
- **Date:** 2026-09-07
- **Deciders:** project lead (human), requirements agent
- **Supersedes / relates to:** plan §2.1, §2.3, §2.4; relates to ADR-0002, ADR-0016; enforced by `COMMON-ARCH-001`, `COMMON-ARCH-002`, `COMMON-ARCH-011`

## Context

Foshol Doctor has six business modules — `identity`, `intake`, `analysis`, `knowledge`, `review`,
`notification` — plus a shared kernel and a Python inference sidecar. Seven agent workstreams build
them in parallel over four days (`00-common.ears.md` §12.1).

Parallel work is usually the argument for microservices: separate deployables, separate repositories,
no shared compiler. But the cost of that separation is paid immediately and in full — service
discovery, per-service schema and migration tooling, distributed tracing, contract tests between
every pair of services, six Dockerfiles, and a `docker compose up` that has to come up in dependency
order on a laptop in front of judges. None of that cost buys a single thing the demo shows.

The real requirement behind "we want microservices" is *enforced module boundaries*. That can be had
without the network. Spring Modulith derives the module graph from package structure and fails the
build when a class reaches into another module's internals or when a cycle appears.

## Decision

One deployable — `app` — containing every business module. Module boundaries are enforced at build
time by `ApplicationModules.of(FosholDoctorApplication.class).verify()` running as an ordinary JUnit
test (`COMMON-ARCH-001`), reinforced by an ArchUnit rule set (`COMMON-ARCH-002` … `COMMON-ARCH-013`)
that additionally enforces the internal layering `web → application → domain`.

Each module is its own Gradle subproject (`COMMON-NFR-007`, ADR-0016) whose `build.gradle` may
declare a dependency only on `:common` and on modules it is permitted to call per the dependency
matrix in `00-common.ears.md` §6.1. A forbidden dependency therefore fails twice: once at the Gradle
level as a missing class, and once in `ModularityTests`.

Cross-module *commands* go through a published interface in the callee's `api` package. Cross-module
*facts* go through a domain event (ADR-0002). Nothing else crosses a boundary.

The inference sidecar is the single exception and is a separate process for a reason that is not
about modularity: it is a different language with a different runtime (`00-common.ears.md` §2.4). It
is reached through `VisionModelPort`, an outbound port, not through a module API.

## Consequences

### Positive

- The architectural claim is a test, not a slide. `./gradlew build` fails on a boundary violation,
  which is exactly the failure mode of seven agents working simultaneously.
- One database, one transaction manager. A case submission that writes `diagnosis_case`,
  `case_image` and `idempotency_key` is one local transaction; there is no saga to get wrong.
- Cross-module foreign keys are real (`00-common.ears.md` §4), so referential integrity is the
  database's problem rather than a reconciliation job's.
- One deployable means `docker compose up` starts four containers, and the live sidecar-kill demo
  kills one of them without taking the application with it.

### Negative / accepted cost

- No independent deployment or scaling per module. Accepted: the deployment target is one laptop
  (plan clarification 22).
- Nothing physically prevents a module from being slow or leaky in a way that hurts the whole
  process. The only guard is the build.
- A shared classpath means a library upgrade is global. The version catalog (ADR-0016) makes that
  explicit rather than accidental.
- `ApplicationModules.verify()` is only as good as the package structure. An agent that puts a class
  in the wrong package can create a legal-looking dependency. `ArchitectureTests` narrows this but
  does not close it entirely.

## Alternatives considered

**Microservices, one per module.** Rejected. Seven deployables, seven schemas, and a distributed
debugging surface on a four-day build. The parallelism it would buy is already available from frozen
`api` packages (`COMMON-NFR-041`).

**A plain monolith with package conventions and code review.** Rejected. With autonomous agents there
is no reviewer in the loop for every commit; a convention that is not a failing test is not a
convention.

**Hexagonal monolith without Modulith.** Rejected as strictly worse: ArchUnit alone can express the
layering but not the module graph, and Modulith additionally gives the event publication registry
that ADR-0002 depends on.

## Revisit when

Revisit if any single module needs a different scaling profile or runtime from the rest — concretely,
if `analysis` needs to run on GPU hardware while the rest stays on CPU, or if inference throughput
requires more than one `analysis` instance while a single `review` instance suffices. Extracting a
module at that point is bounded work precisely because its dependencies are already limited to its
`api` package and its events: the first step is ADR-0002's broker migration, and the second is moving
the module's tables behind its own connection.
