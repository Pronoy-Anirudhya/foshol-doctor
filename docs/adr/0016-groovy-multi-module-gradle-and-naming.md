# ADR-0016: Groovy multi-module Gradle, `buildSrc` conventions, and the `foshol` naming rules

- **Status:** Accepted
- **Date:** 2026-09-07
- **Deciders:** project lead (human), requirements agent
- **Supersedes / relates to:** **deviates from the generation prompt's "Gradle 9 Kotlin DSL"**, on user instruction; plan clarification 16; relates to ADR-0001, ADR-0004; recorded in `00-common.ears.md` §2.1, §3, §8.1, §9

## Context

Three decisions travel together here because they share one purpose: making seven agents produce one
coherent build rather than seven compatible-looking ones.

**Build language.** The generation prompt specifies Gradle 9 with the Kotlin DSL. The repository as it
stands is a single-module Groovy `build.gradle` on Gradle 9.7.1. Converting buys type-safe build
scripts and IDE completion, and costs a day plus a class of errors agents get wrong from stale
material — the two DSLs are similar enough to be confused and different enough to fail.

**Project layout.** ADR-0001 puts every module in one deployable, but that does not settle whether the
Gradle build is one project or many. One project is simpler; many make the dependency matrix in
`00-common.ears.md` §6.1 enforceable by the build itself, not only by ArchUnit.

**Naming.** The project was renamed from "Fasol Doctor" to **Foshol Doctor**. The plan predates the
rename and still says `fasol.*` for configuration and `com.fasol.*` for packages. Two spellings of the
same prefix, in a codebase seven agents write in parallel, is a guaranteed set of properties that
silently never bind.

## Decision

**Gradle 9.7.1, Groovy DSL, on user instruction, deliberately and recorded.**
`00-common.ears.md` §2.1 carries the note in the document agents actually read, ending "**Do not 'fix'
this**" — because an agent meeting a Groovy build in a project whose prompt says Kotlin will otherwise
helpfully convert it.

**Multi-project: one Gradle subproject per Modulith module** (`COMMON-NFR-007`), plus `common`, `app`
and `buildSrc`. Each module's `build.gradle` depends only on `:common` and on the modules it may call
per §6.1, so a forbidden dependency is a missing symbol at compile time, before `ArchitectureTests`
ever runs. The architecture is enforced twice at two stages, and the first failure is the fastest and
clearest.

**`buildSrc` convention plugins, in Groovy**, owned exclusively by the platform agent:
`foshol.java-conventions` (Java 25 toolchain, encoding, compiler args, test configuration, **no
preview features** per `COMMON-NFR-002`), `foshol.module-conventions` (module dependencies plus
ArchUnit) and `foshol.spring-conventions` (Boot and Modulith BOM wiring). A module's `build.gradle` is
then a plugin line and a short dependency list, with almost nothing in it to get creatively wrong.

**`gradle/libs.versions.toml` is the version catalog, and it is the mechanism this ADR exists for.**
`COMMON-NFR-001`: every dependency version is declared there and **no `build.gradle` contains a
version literal**. Only the platform agent may add to it; an agent needing a library not listed raises
a blocker rather than choosing one.

This is the concrete problem it solves. Seven agents each need a JSON library, a test assertion
library, a UUID library. Left alone they will each pick a reasonable version, and the build will
resolve some arbitrary set of them — the classic outcome being an integration checkpoint where two
modules compile alone and fail together, with a `NoSuchMethodError` that takes an hour to attribute.
The catalog makes that structurally impossible: one place a version can be written, one agent who may
write there.

**Naming, fixed once.** Base package **`com.rootcause.foshol`**, group `com.rootcause`, module
packages `com.rootcause.foshol.<module>` — not `com.fasol.*`. Configuration prefix **`foshol.`**, never
`fasol.` (`00-common.ears.md` §9): **there is no `fasol.` property in this system.** Every property
name is also a constant in `common.ConfigKeys`, and `COMMON-ARCH-010` forbids configuration string
literals outside `common`, so a typo in a property name is a compile error rather than a value that
silently falls back to a default. The table in `00-common.ears.md` §8.1 fixes the rest:
`SubmitCaseCommand`, `SubmitCaseCommandHandler`, `OfficerQueueQuery`, `VisionModelPort`,
`HttpVisionModelAdapter`, `DiagnosisCaseEntity`, singular snake_case tables, `p_` for projections,
`ck_`/`uq_`/`ix_` for constraints.

## Consequences

### Positive

- No build-script conversion, and no agent writing Kotlin DSL syntax into a Groovy file or the reverse.
- The dependency matrix is enforced by the compiler as well as by ArchUnit, and the compiler fails
  first.
- Convention plugins make a new module three lines of build script; cross-module inconsistency in
  compiler flags or test configuration is not possible.
- One catalog, one owner: seven agents cannot choose seven library versions.
- One spelling of the base package and the config prefix, both greppable — a `fasol` hit anywhere in
  the repository is a defect, and the check is `grep -ri fasol`.
- `ConfigKeys` plus `COMMON-ARCH-010` turns property-name typos into compile errors.

### Negative / accepted cost

- Groovy build scripts are dynamically typed: a misspelled configuration name fails at configuration
  time with a less helpful message than Kotlin DSL would give, and IDE completion is weaker.
- The deviation from the generation prompt must be re-explained wherever the prompt is read. It is
  recorded in three places precisely because one is not enough.
- Multi-project Gradle has higher configuration overhead and a slower clean build. At this scale,
  seconds.
- The catalog is a serialisation point: every new dependency waits on the platform agent. That is the
  intended cost, and it will occasionally block an agent for real.
- `buildSrc` changes invalidate the build cache for everything, so an edit there is felt by all seven
  workstreams.
- The plan text remains full of `fasol.*` and `com.fasol.*`; anyone reading it for traceability must
  translate, and this ADR is the only reconciliation.

## Alternatives considered

**Kotlin DSL as the prompt specifies.** Rejected on user instruction and conversion cost. The type
safety is real; a day of conversion plus agents mixing two DSL dialects is worse on this schedule.

**Single-project Gradle with source sets per module.** Rejected. The boundary would then be enforced
only by ArchUnit, losing the earlier compile-time failure, and Modulith's module detection works more
naturally with real project boundaries.

**No `buildSrc`; repeat configuration in each module's `build.gradle`.** Rejected: seven copies of one
block is seven chances to drift, and drift in compiler args or test configuration is exactly what
produces "works in my module".

**No version catalog; inline versions plus a dependency-locking plugin.** Rejected. Locking records
what was resolved; the catalog decides what may be requested. The problem is agents choosing, not
resolution being unstable.

**Keep `fasol.*` for continuity with the plan.** Rejected. Two spellings in one codebase is worse than
one inconsistency with a historical document that `00-common.ears.md` supersedes anyway.

## Revisit when

Revisit the DSL choice only outside a delivery window — if the project continues past this build and
someone has a clear day, the conversion is mechanical and the type safety is worth having; mid-build it
is not. Revisit the catalog's single-owner rule if the platform agent becomes a measured bottleneck,
which has an observable signal: a blocker recorded in `docs/progress/<agent>.md` waiting on a catalog
addition for more than half a day. The answer then is a faster path for adding a version, not more
places a version may be written.
