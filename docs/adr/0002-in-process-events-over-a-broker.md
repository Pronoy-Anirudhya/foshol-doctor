# ADR-0002: In-process application events instead of RabbitMQ

- **Status:** Accepted
- **Date:** 2026-09-07
- **Deciders:** project lead (human), requirements agent
- **Supersedes / relates to:** plan §2.1; relates to ADR-0001; enforced by `COMMON-ARCH-014`, `COMMON-ARCH-015`

## Context

The original architecture sketch included RabbitMQ for cross-module communication. Plan §2.1 already
argued it out: inside one deployable (ADR-0001) a broker buys nothing demonstrable and costs topology
declaration, dead-letter configuration, consumer idempotency by hand, one more container in
`docker-compose.yml`, and an entire class of "why did the message not arrive" debugging.

But the pipeline genuinely is event-driven. `CaseSubmitted` starts analysis; `AnalysisCompleted`
creates the review task and advances case status; `AdvisoryApproved` notifies the farmer and advances
status again (`00-common.ears.md` §6.3). Three modules react to facts published by others, and two
projection tables are fed by listeners rather than by joins at read time. Losing an event mid-flight
loses a farmer's case: the review task is never created, the case sits in `ANALYSED` forever, and
nobody notices until the demo.

So the question is not "events or no events" — it is "which transport, and what durability".

## Decision

**Transport: Spring Modulith application events.** Cross-module facts are `record` types in the
publishing module's `api` package, consumed with `@ApplicationModuleListener` — which is
`@Async` + `@TransactionalEventListener(AFTER_COMMIT)` + transaction propagation. Cross-module
commands are direct calls on published interfaces (`00-common.ears.md` §6.2). There is no third
mechanism.

**Durability: take `spring-modulith-events-jpa`.** `COMMON-ARCH-014` puts the JPA event publication
registry on the classpath with
`spring.modulith.events.jdbc.schema-initialization.enabled=true` and
`spring.modulith.events.republish-outstanding-events-on-restart=true`. Modulith persists each
publication before delivery and republishes incomplete ones on restart. That is outbox semantics for
**one dependency and one property**, with no handler code, no `failed_event` table and no admin
replay endpoint. Plan §2.1 called this optional, worth twenty minutes if Day 2 ran ahead; we take it
unconditionally, because losing a case is the one failure this system cannot present on stage.

Because the registry can redeliver, `COMMON-ARCH-015` requires every listener to be idempotent: a
republished event must produce no duplicate row and no duplicate notification.

**The migration path is concrete, and is the answer given on stage.** Adding
`spring-modulith-events-amqp` and annotating an event with `@Externalized` publishes it to a broker
**without touching a single handler**: the publishing side is unchanged, the receiving side becomes a
listener on the broker rather than on the in-process multicaster. The event record — already a
`record` in an `api` package, already carrying `correlationId` and `occurredAt` — is already the wire
format. The cost of the migration is a dependency, a routing-key annotation, and a broker.

## Consequences

### Positive

- Zero broker operations. One fewer container, no topology bootstrap, no queue that exists on one
  machine and not another.
- Events are ordinary Java types checked by the compiler. A renamed field on `AnalysisCompleted`
  breaks the build rather than producing a silently unparseable message at runtime.
- Restart safety is real and testable: kill the application between publication and delivery, restart,
  and the listener runs.

### Negative / accepted cost

- Delivery is in-process. If the JVM is gone, nothing is delivered until it comes back — acceptable
  because there is exactly one JVM.
- The event publication registry adds two tables and a small write per event on the hot path of every
  case submission. Measured against the 800 ms budget for `POST /api/v1/cases`
  (`00-common.ears.md` §10.2), this is affordable; it is not free.
- Idempotent listeners are a discipline, not a mechanism. `COMMON-ARCH-015` states the rule; only the
  module's own tests enforce it.

## Alternatives considered

**RabbitMQ now.** Rejected per plan §2.1. Would add a container, a topology, DLQ handling and manual
consumer idempotency, in exchange for a capability nothing in scope uses.

**Plain Spring `ApplicationEventPublisher` with no registry.** Rejected. It is the same programming
model with none of the durability; an event lost during a restart is a case lost silently.

**`@Retryable` with exponential backoff plus a `failed_event` table and an admin replay endpoint** —
plan §2.1's stated fall-back. Rejected: strictly more code, more schema and more UI than one
dependency and one property, and the admin surface it needs is itself out of scope (clarification 19).

## Revisit when

Revisit when a consumer must live outside this deployable — a second service, a different language,
or an analytics sink — or when an event must survive the deployable being down rather than merely
restarting. The trigger is concrete: the first time a requirement names a consumer that is not one of
the six modules in `00-common.ears.md` §6.1, add `spring-modulith-events-amqp`, annotate the events
that must cross the boundary, and leave the handlers alone.
