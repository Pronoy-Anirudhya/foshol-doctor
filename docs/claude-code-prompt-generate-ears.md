# Prompt: Generate the EARS requirement suite for Fasol Doctor

> Paste this into Claude Code at the repo root, with the plan file present.
> Recommended: run it in Plan Mode first so you can review before any file is written.

---

You are acting as the lead requirements engineer for a 4-day, agentically-developed build. Your output will be the **sole specification** that autonomous coding agents read. No human will write application code. If something is not in your documents, it will not exist in the product; if something is ambiguous in your documents, an agent will guess, and the guesses will not agree with each other.

## Input

Read `./fasol-doctor-plan-v2-revised-scope.md` in full before doing anything else. Also read the original BRD at `./GM_Mehrab_Hossen_2312_Fasol_doctor.pdf` if present, for background only — **the plan supersedes the BRD wherever they conflict.**

## Objective

Produce a complete EARS-format requirements suite under `docs/requirements/` that is sufficient for parallel autonomous implementation, and a compressed **4-day** execution schedule (the plan is written for 6 days — you must re-fit it, see "Compression mandate").

## Phase 0 — Analysis

Before writing anything, build an internal model of:

- Every module, its boundary, what it owns, and what it publishes.
- Every cross-module interaction, classified as either a published-interface call or an asynchronous domain event.
- Every entity, its fields, types, constraints, and relationships.
- Every external dependency (PostgreSQL, pgvector, MinIO, the Python inference sidecar, the ML models).
- Every wow factor listed under "Compression mandate" below.
- Every place the plan is ambiguous, underspecified, or self-contradictory.

## Phase 1 — Clarification gate (MANDATORY STOP)

**Do not write a single file until this phase is resolved.**

Present a numbered list of every decision you cannot make safely from the plan alone. For each item give: the question, why it blocks or risks the build, the options, and **your recommended default with a one-line justification**. Let me answer with "1a, 2 default, 3b…" or accept all defaults in one word.

Ask about anything genuinely underdetermined. At minimum, resolve these — the plan does not settle them:

1. **Authentication.** Farmer login is described as "phone + OTP stub". What exactly? Real OTP, a fixed dev code, or seeded sessions with a user-picker? What secures the officer and admin roles — session cookie or JWT? Any password policy?
2. **Multi-image cases.** The plan allows 1–3 images. Does the classifier run per image and aggregate, or on a single primary image? What aggregation rule?
3. **Language of record.** Is Bangla or English the source of truth for disease/remedy content, and what happens when a translation is missing? Is the EN toggle full or partial?
4. **Officer assignment.** Is the queue a shared pool with claim-based locking, or are cases routed to an officer by district? What happens to a claimed case that is never completed — is there a timeout and re-queue?
5. **Reject path.** When an officer rejects a case, what does the farmer see, and can they resubmit against the same case or must they create a new one?
6. **Case immutability.** After an advisory is published, can the officer revise it? If yes, does the farmer get a second notification and is the prior version retained?
7. **Symptom sources.** Section 3 lists symptom sources SPEECH, VISION and OFFICER. Is the optional VLM visual-descriptor path (plan §6) in scope for 4 days or explicitly deferred?
8. **Confidence semantics.** Is the routing threshold applied to raw softmax, a calibrated score, or top1-minus-top2 margin? Calibration matters for the two-threshold demo to behave predictably.
9. **Idempotency.** Client-generated key, or server-side dedupe on image hash + farmer + time window?
10. **Audit and soft-delete.** Which entities need created/updated-by auditing and soft deletion, and is there an admin-visible audit log?
11. **Seed data volume and provenance.** The plan calls for 40–60 historical cases. Are these generated with real images from the datasets, and who are the fictional farmers and officers?
12. **Sidecar contract ownership.** Does the Python sidecar get its own EARS document with its own agent, or is it specified inside the `analysis` domain document?
13. **Test depth.** What is the mandatory floor — unit tests on domain and handlers only, or also Testcontainers integration tests per module? This materially changes the 4-day budget.
14. **Deployment target.** Local `docker compose` only, or must it run on a cloud host for judging?

Add every additional ambiguity you find. Do not pad the list with questions the plan already answers.

## Phase 2 — Write `docs/requirements/00-common.ears.md`

This is the shared contract. Every domain agent reads it first and treats it as immutable. It must contain, in full detail:

- **Product context and the 4-day scope boundary** — what is in, what is explicitly deferred, and the one-line reason for each deferral.
- **Technology baseline** — exact versions: Java 25, Spring Boot 4.1.1, Spring Modulith, Gradle 9 Kotlin DSL, PostgreSQL 17 + pgvector, MinIO, React/Vite/TS/Tailwind, Python/FastAPI sidecar. Pin every library that an agent would otherwise choose arbitrarily (validation, mapping, testing, HTTP client, migration tool).
- **Repository and folder structure** — the complete tree, to file level for shared infrastructure. Gradle multi-module layout, `buildSrc` convention plugins, version catalog, the per-module `api/domain/application/infrastructure/web` layout, frontend structure, sidecar structure, `docker-compose.yml`, `docs/`.
- **Architectural laws** — the dependency rule, the published-API-only import rule, CQRS command/query separation, the read-only DataSource qualifier, records over classes, constants over literals, no `ddl-auto`, no cross-module entity references, no business logic in controllers. Each law must be stated as a testable requirement with the ArchUnit or Modulith assertion that enforces it.
- **Complete entity schema** — every table from plan §4 with exact column names, SQL types, nullability, defaults, constraints, indexes (including the HNSW vector indexes), and foreign keys. Include the Flyway migration file names and ordering. This section is the single most important thing you write: it is what lets six agents work simultaneously without colliding.
- **Cross-module contracts** — every published interface with its exact Java signature, and every domain event as a `record` with exact fields. Specify which module publishes and which consume. Include the constants class for event names and config keys.
- **Shared conventions** — package naming, class naming, REST URL and verb conventions, HTTP status code policy, the error response envelope (RFC 9457 problem details), pagination, sorting, date/time handling (UTC storage, Asia/Dhaka display), UUIDv7 generation, validation annotations, logging format and levels, correlation ID propagation.
- **Configuration** — the full `application.properties` for every profile (`local`, `demo`, `test`), every externalised threshold and toggle, and the secrets policy.
- **Cross-cutting non-functional requirements** — security (authn/authz, encryption at rest for PII, upload limits, rate limiting), performance budgets, observability (Actuator, structured logs, correlation IDs), resilience (Resilience4j configuration per outbound call), and i18n.
- **Definition of Done for every module** — build clean, tests green, Modulith verification passes, ArchUnit passes, migrations apply from empty, endpoints match the OpenAPI contract.
- **Agent coordination protocol** — file ownership per module (which agent may touch which paths), the shared-file rule (only the platform agent edits `buildSrc`, the version catalog, `docker-compose.yml`, and shared migrations), the integration checkpoint procedure, and how an agent records progress and blockers.

## Phase 3 — Write one EARS document per domain

Create `docs/requirements/1X-<module>.ears.md` for each of: `identity`, `intake`, `analysis`, `knowledge`, `review`, `notification`, plus `70-frontend.ears.md`, and `60-inference-sidecar.ears.md` (unless clarification 12 folds it into `analysis`).

Each document must be independently implementable by an agent that has read only `00-common.ears.md` and its own file. Each must contain:

1. **Scope** — what this module owns, and an explicit list of what it does not own with a pointer to the module that does.
2. **Dependencies** — exactly which published interfaces it calls and which events it consumes and publishes, quoting the signatures from the common doc. No other module's internals may be referenced.
3. **Domain model** — aggregates, entities, value objects, enums, invariants, and specifications. State every business rule as an invariant on the aggregate that owns it.
4. **Requirements in EARS syntax** — see conventions below. Cover happy paths, state transitions, validation, error handling, and unwanted behaviour.
5. **API surface** — every endpoint with method, path, request and response schema, status codes, error cases, and authorisation rule.
6. **Persistence** — which tables it owns and its query patterns, referencing (never redefining) the common schema.
7. **Acceptance criteria** — a Given/When/Then scenario per requirement, written so it maps one-to-one onto a test method.
8. **Test requirements** — what must be unit tested, what must be integration tested, and the fixtures needed.
9. **Agent execution notes** — implementation order within the module, which files to create in which sequence, and the local Definition of Done.

## Phase 4 — Write `docs/requirements/INDEX.md`

- The module dependency graph and the resulting build order.
- **Which documents can be implemented in parallel and which must wait**, with the specific contract each dependent is waiting on.
- The compressed 4-day schedule with named agent workstreams, checkpoints, and per-day demoable milestones.
- The demo script the whole build serves, restated as acceptance criteria.
- A risk table with the cut trigger for each item.

## EARS conventions (apply strictly)

Use the five EARS patterns and nothing else:

- **Ubiquitous:** `THE <system> SHALL <response>`
- **Event-driven:** `WHEN <trigger>, THE <system> SHALL <response>`
- **State-driven:** `WHILE <state>, THE <system> SHALL <response>`
- **Unwanted behaviour:** `IF <condition>, THEN THE <system> SHALL <response>`
- **Optional feature:** `WHERE <feature is included>, THE <system> SHALL <response>`

Complex requirements may combine `WHEN … WHILE … SHALL`, but never nest more than two conditions — split instead.

Rules:

- Requirement IDs are `<MODULE>-<CATEGORY>-<NNN>`, e.g. `INTAKE-FR-014`, `ANALYSIS-NFR-003`, `REVIEW-SEC-002`. Categories: `FR`, `NFR`, `SEC`, `DATA`, `API`, `UX`.
- Every requirement is atomic, testable, and free of implementation instruction unless the plan mandates the technology.
- Every requirement states an observable system response. "The system shall be scalable" is not a requirement.
- Every numeric threshold, timeout, limit and size is explicit and externalised to configuration, with the property name given.
- Cross-reference related requirements by ID. Never restate a requirement that lives in another document — link to its ID.
- Prefix any requirement that is deferred or optional with `[DEFERRED]` or `[STRETCH]` and keep it in the document so the seam is visible.

## Compression mandate — 6 days into 4

Re-fit the schedule to four days. Compress by removing work, never by assuming agents are faster than they are. Rules:

- **Non-negotiable:** the seven scoped features (F1–F7), the two-threshold routing with all three decision paths, the officer approval workflow, Bangla ASR, the knowledge-base symptom fallback, and web push or SSE notification.
- **Preserve these wow factors** — they are what the demo is judged on:
  - Grad-CAM / attention overlay showing where the model looked
  - Confidence bars with both thresholds drawn, making the routing visible
  - Officer queue sorted least-confident-first
  - Model-vs-officer agreement rate on the admin strip
  - The live sidecar-kill degradation demo
  - The `SmsChannel` stub and the Modulith AMQP one-liner as visible extensibility proof
- **Cut candidates, in this order:** the admin CRUD UI (replace with seeded SQL plus a read-only stats page), the optional VLM descriptor path, Bangla TTS, the k6 load test, the full admin audit log, Web Push (keep SSE).
- Budget the reduced test floor honestly against clarification 13, and state in `INDEX.md` exactly what was cut and why, so nothing disappears silently.

## Hard rules

- **Never invent business rules, agronomic content, remedy text, dosages, pre-harvest intervals, or Bangla symptom phrases.** Where content is needed, write a requirement that specifies its structure, source, and validation, and mark the content itself as human-supplied with an owner and a deadline.
- Every requirement must trace to something in the plan. Where you must extend the plan to make it implementable, mark the requirement `[DERIVED]` and state the reasoning inline.
- Where the plan is silent on something you resolved in Phase 1, record the answer and its rationale in an ADR under `docs/adr/`.
- Do not write application code in this task. Requirements, schema DDL, interface signatures, and configuration are in scope; implementations are not.
- Assume the reader is an agent with no conversational context and no ability to ask follow-up questions.

## Output

After Phase 1 is answered, write all files, then report: the file tree created, the requirement count per document, the parallelisation plan, anything you flagged as `[DERIVED]`, and any residual risk you would want a human to look at before agents start.
