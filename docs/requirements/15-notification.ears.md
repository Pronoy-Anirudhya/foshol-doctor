# 15 — Notification (EARS)

**Module:** `notification` · package `com.rootcause.foshol.notification` · **Agent A5**
**Prefix:** `NOTIFY`

> Read `docs/requirements/00-common.ears.md` first and treat it as immutable. This document and that
> one are together sufficient to implement this module. Where they disagree, `00-common.ears.md`
> wins. Requirements marked `[DERIVED]` extend the plan and carry inline reasoning.

---

## 0. What this module is for

`14-review.ears.md` makes the safety claim true. This module is what makes it **visible**: the moment
a named field officer approves an advisory, the farmer's screen changes. Nothing else in the system
tells the farmer anything.

It is also where the architecture's extensibility stops being an assertion. `NotificationChannel` is
a port with three registered implementations. One is enabled and carries the demo. The other two are
real, compiled, Spring-managed classes that are opened on screen during the presentation to show that
adding SMS or Web Push is an adapter, not a rewrite. They are specified in §4.6 as working code with
defined behaviour — not as placeholders.

---

## 1. Scope

### 1.1 Owned

| Artefact | Reference |
|---|---|
| `notification` table | `00-common.ears.md` §4.7 |
| `NotificationPort` and `AdvisoryNotification` | §6.2 |
| `NotificationChannel` and its three implementations | §6.2, §4.6 below |
| `GET /api/v1/stream` — **the only** Server-Sent Events endpoint in the system | §5 below |
| The SSE emitter registry, heartbeat and connection lifecycle | §4.4 |
| Delivery state machine `PENDING → SENT / FAILED / SKIPPED` | §4.5 |

### 1.2 Explicitly **not** owned

| Not owned | Owner | Note |
|---|---|---|
| Any SSE endpoint of its own in another module | — | **Notification owns all SSE.** `intake` has no stream endpoint; case-status fan-out happens here (`NOTIFY-FR-030`, clarification item 18) |
| Advisory content, disease name, remedy list, officer name | `review` (`14-review.ears.md`) | Read through `ReviewSubmissionApi`; never recomputed and never re-worded |
| Agronomic text of any kind | `knowledge` + `CONTENT-OWNERS.md` | This module composes; it authors nothing (`COMMON-CON-003`) |
| Farmer identity, language preference, phone number | `identity` (`10-identity.ears.md`) | Read through `FarmerLookupApi` |
| `diagnosis_case` and its status transitions | `intake` (`11-intake.ears.md`) | Notification reacts to `CaseStatusChanged`; it never sets a status |
| The officer queue and its projection | `review` | The officer stream carries a nudge, not queue data (`NOTIFY-FR-033`) |
| Bangla UI string catalogue | `CONTENT-OWNERS.md` row set, rendered by `web/src/assets/i18n/bn.json` | Titles and bodies are assembled from fixed keys plus human-supplied values (`NOTIFY-FR-012`) |
| Web Push VAPID keys, service worker, SMS gateway credentials | Nobody — cut | `00-common.ears.md` §1.2 |

`NOTIFY-NFR-001` **THE notification module SHALL write to no table other than `notification`.**

---

## 2. Dependencies

### 2.1 Published interfaces this module calls

```java
// com.rootcause.foshol.identity.api
public interface FarmerLookupApi {
    Optional<FarmerView> findById(UUID farmerId);
    Optional<FarmerView> findByPhone(String e164Phone);
}
public record FarmerView(UUID id, String name, String districtCode, String preferredLanguage) {}

// com.rootcause.foshol.review.api
public interface ReviewSubmissionApi {
    Optional<AdvisoryView> findPublishedAdvisory(UUID caseId);
    List<AdvisoryView> findAdvisoryHistory(UUID caseId);
    Optional<RejectionView> findRejection(UUID caseId);
}
```

Both are permitted by the dependency matrix in `00-common.ears.md` §6.1
(`notification → identity ✓`, `notification → review ✓`).

### 2.2 Published interfaces this module exposes

```java
public interface NotificationPort {
    void publish(AdvisoryNotification notification);
}
public record AdvisoryNotification(UUID notificationId, UUID farmerId, UUID caseId, UUID advisoryId,
                                   NotificationType type, String titleBn, String bodyBn,
                                   Map<String, String> data) {}

public interface NotificationChannel {
    String name();
    boolean enabled();
    boolean supports(UUID farmerId);
    boolean send(AdvisoryNotification notification);
}
```

Frozen in `00-common.ears.md` §6.2 (`COMMON-NFR-041`). No module calls `notification` synchronously
today; `NotificationPort` exists so that a future in-process caller has a seam.

### 2.3 Events consumed

| Event | Publisher | Result |
|---|---|---|
| `AdvisoryApproved` | `review` | `ADVISORY_PUBLISHED` notification (`NOTIFY-FR-002`) |
| `AdvisoryRevised` | `review` | `ADVISORY_REVISED` notification — the farmer's **second** notification (`NOTIFY-FR-003`) |
| `CaseRejected` | `review` | `CASE_REJECTED` notification (`NOTIFY-FR-004`) |
| `CaseStatusChanged` | `intake` | `CASE_STATUS_CHANGED` notification to the farmer, and the officer queue nudge (`NOTIFY-FR-005`, `NOTIFY-FR-033`) |

This module publishes no domain event.

### 2.4 Contract discrepancy — RAISED BY A5, RESOLVED BY A1

`NOTIFY-NFR-002` **THE `notification` module SHALL consume `CaseStatusChanged` from
`com.rootcause.foshol.common.events`, and SHALL hold no type reference into `intake`.**

A5 originally raised this as a Day-0 blocker: `CaseStatusChanged` was declared in
`com.rootcause.foshol.intake.api`, yet the §6.1 matrix showed `—` for `notification → intake`, so
`ApplicationModules.verify()` would fail. **The blocker was correct, and investigating it found a
larger problem** — the event flow is bidirectional in two places (`intake ↔ analysis` and
`intake ↔ review`), so events living in publisher `api` packages produced **two genuine module
cycles** that would have failed the build on Day 1.

A1 resolved it at the root rather than by widening the matrix: **every event record and every event
payload record now lives in the shared `common` module** — `COMMON-ARCH-016`, `COMMON-ARCH-017` and
`COMMON-ARCH-018`. Consuming an event is now always a dependency on `common`, which is a Modulith
shared module and can never form a cycle. The matrix cell stays `—` and is correct: `notification`
calls no method on `intake`.

No requirement in this document changed; only the import path did. `NOTIFY-FR-005` and
`NOTIFY-FR-033` are unblocked.

### 2.5 Day-0 items owned by A1

`NOTIFY-NFR-003` **THE error codes `ERR_STREAM_SUBJECT_MISMATCH` and `ERR_STREAM_LIMIT_EXCEEDED`, and
the config-key constants for `foshol.channels.sse.enabled`, `.heartbeat`, `.timeout`,
`foshol.channels.webpush.enabled` and `foshol.channels.sms.enabled`, SHALL be declared in
`common.ConfigKeys` and `common.ErrorCodes`** (`COMMON-ARCH-010`). Raised to A1 by A5.

---

## 3. Domain model

### 3.1 `Notification` — aggregate root

| Field | Type | Notes |
|---|---|---|
| `id` | UUIDv7 | |
| `farmerId`, `caseId` | `UUID` | raw ids (`COMMON-ARCH-006`) |
| `advisoryId` | `UUID`, nullable | null for `CASE_STATUS_CHANGED` |
| `channel` | `SSE`, `WEB_PUSH`, `SMS` | which channel accepted the delivery attempt |
| `type` | `ADVISORY_PUBLISHED`, `ADVISORY_REVISED`, `CASE_REJECTED`, `CASE_STATUS_CHANGED` | |
| `titleBn`, `bodyBn` | `String` | assembled, never authored (§4.2) |
| `payload` | `jsonb` | flat `Map<String,String>` the client uses to route |
| `state` | `PENDING`, `SENT`, `FAILED`, `SKIPPED` | §4.5 |
| `attempts` | `short` | |
| `deliveredAt` | `Instant`, nullable | set only on `SENT` |

**Invariants:**

- `INV-N-1` `deliveredAt` is non-null **if and only if** `state = SENT`.
- `INV-N-2` `state = SKIPPED` implies `attempts = 0` — nothing was tried.
- `INV-N-3` `titleBn` and `bodyBn` are non-blank and NFC-normalised (`COMMON-NFR-013`).
- `INV-N-4` `advisoryId` is non-null for `ADVISORY_PUBLISHED` and `ADVISORY_REVISED`.
- `INV-N-5` A `(farmerId, caseId, type, dedupeKey)` tuple has at most one row (`NOTIFY-DATA-001`).

### 3.2 `NotificationChannel` — outbound port

A Spring bean. Four methods, all cheap and none of which may throw
(`NOTIFY-FR-023`). `name()` is a stable identifier matching `ck_notification_channel`
(`SSE`, `WEB_PUSH`, `SMS`).

### 3.3 `SseSubscription` — in-memory value

`subjectId` (farmer or officer id), `role`, `emitter`, `connectedAt`, `lastEventId`. Held in a
`ConcurrentHashMap<UUID, List<SseSubscription>>`. Not persisted; a restart drops every connection and
clients reconnect (`NOTIFY-FR-040`).

---

## 4. Requirements

### 4.1 Event consumption

`NOTIFY-FR-001` **WHEN the notification module consumes a domain event naming a farmer, THE
notification module SHALL create exactly one `notification` row per consulted channel that accepts
the delivery, in `state = PENDING`, before any delivery is attempted.**

`NOTIFY-FR-002` **WHEN the notification module consumes `AdvisoryApproved`, THE notification module
SHALL create a notification of type `ADVISORY_PUBLISHED` for the event's `farmerId`, carrying the
`advisoryId`, and SHALL deliver it through the channel registry (`NOTIFY-FR-020`).**

`NOTIFY-FR-003` **WHEN the notification module consumes `AdvisoryRevised`, THE notification module
SHALL create a notification of type `ADVISORY_REVISED` for the event's `farmerId`, carrying the new
`advisoryId` and the new `version` in `payload`, and SHALL deliver it.**
*(This is the farmer's second notification, required by the append-only advisory model —
`REVIEW-FR-062`. A revision that produced no notification would mean a farmer acting on advice that
has since been corrected.)*

`NOTIFY-FR-004` **WHEN the notification module consumes `CaseRejected`, THE notification module SHALL
create a notification of type `CASE_REJECTED` for the event's `farmerId`, with `advisory_id` null,
carrying `reasonCode` in `payload`, and SHALL deliver it.**

`NOTIFY-FR-005` **WHEN the notification module consumes `CaseStatusChanged`, THE notification module
SHALL create a notification of type `CASE_STATUS_CHANGED` for the event's `farmerId`, carrying
`fromStatus` and `toStatus` in `payload`, and SHALL deliver it.** *(Blocked on `NOTIFY-NFR-002`.)*

`NOTIFY-FR-006` **IF `FarmerLookupApi.findById` returns empty for an event's `farmerId`, THEN THE
notification module SHALL write no `notification` row, SHALL log a `WARN` with the correlation id,
and SHALL NOT throw.** *(A missing farmer is a data defect upstream. Failing the listener would make
Modulith retry it forever on every restart.)*

`NOTIFY-FR-007` **THE notification module SHALL carry the consumed event's `correlationId` on every
notification row's `payload`, on every SSE frame it emits, and in every log line it writes**
(`COMMON-NFR-016`).

`NOTIFY-FR-008` **THE notification module SHALL handle every event in an
`@ApplicationModuleListener`, and SHALL NOT call any review, intake or identity API from inside a
synchronous publisher transaction.**

### 4.2 Content assembly — composition, never authorship

`NOTIFY-FR-010` **THE notification module SHALL assemble the body of an `ADVISORY_PUBLISHED` or
`ADVISORY_REVISED` notification from `ReviewSubmissionApi.findPublishedAdvisory(caseId)`, using
`AdvisoryView.diseaseNameBn()`, `AdvisoryView.officerName()` and the size of
`AdvisoryView.remedies()`.**

`NOTIFY-FR-011` **THE notification module SHALL assemble the body of a `CASE_REJECTED` notification
from `ReviewSubmissionApi.findRejection(caseId)`, using `RejectionView.messageBn()` — the officer's
own words — and `RejectionView.officerName()`.**

`NOTIFY-FR-012` **THE notification module SHALL compose every notification title and body from a
fixed Bangla UI string template identified by a message key, into which only human-supplied values
are substituted, and SHALL NOT generate, translate, paraphrase or summarise any text.**

The message keys are `notify.advisory.published`, `notify.advisory.revised`,
`notify.case.rejected`, `notify.case.status`; the Bangla strings behind them are **human-supplied**
and listed in `docs/requirements/CONTENT-OWNERS.md`. The only substituted values are the disease
name, the officer name, the remedy count, and the officer's rejection message — every one of which
originated from a human. This module therefore authors **no** agronomic content
(`COMMON-CON-003`), and there is no path by which it could.

`NOTIFY-FR-013` **IF a message key has no value for the farmer's `preferredLanguage`, THEN THE
notification module SHALL fall back to the Bangla string** (`COMMON-NFR-037`, `COMMON-NFR-038`).

`NOTIFY-FR-014` **THE notification module SHALL truncate an assembled `title_bn` to 200 characters to
match the column width, truncating on a grapheme boundary**, and SHALL NOT truncate `body_bn`.

`NOTIFY-FR-015` **THE notification module SHALL NOT place a farmer's phone number, a JWT, a MinIO
object key or a presigned URL in `payload`, in an SSE frame, or in a log line**
(`COMMON-SEC-001`, `COMMON-SEC-016`).

### 4.3 The channel registry

`NOTIFY-FR-020` **THE notification module SHALL discover every `NotificationChannel` implementation
as a Spring bean, SHALL order them by `@Order`, and SHALL consult them in that order for every
notification.** `SseChannel` carries `@Order(10)`, `WebPushChannel` `@Order(20)`, `SmsChannel`
`@Order(30)`.

`NOTIFY-FR-021` **THE notification module SHALL skip a channel whose `enabled()` returns false or
whose `supports(farmerId)` returns false, without creating a row for it and without calling
`send`.**

`NOTIFY-FR-022` **WHEN a channel's `send` returns true, THE notification module SHALL record the row
as `SENT` with `channel` set to that channel's `name()` and `delivered_at` set, and SHALL stop
consulting further channels for that notification.** *(First success wins. The registry is a fallback
chain, not a broadcast.)*

`NOTIFY-FR-023` **IF a channel's `send` throws, THEN THE notification module SHALL treat it as a
false return, SHALL log the exception at `WARN`, and SHALL continue to the next channel.** *(A
channel is a third-party integration point. One misbehaving adapter must not lose a farmer's
advisory.)*

`NOTIFY-FR-024` **IF every consulted channel is disabled or unsupported, THEN THE notification module
SHALL write one row with `channel` set to `SSE`, `state = SKIPPED` and `attempts = 0`.**
*(The record of "we had something to tell this farmer and no way to tell them" is more valuable than
no record. `SSE` is used as the channel discriminator because `ck_notification_channel` admits no
`NONE` value and this document may not change the schema.)*

`NOTIFY-FR-025` **IF every consulted channel returned false or threw, THEN THE notification module
SHALL write one row with `state = FAILED` and `attempts` set to the number of channels actually
called.**

`NOTIFY-FR-026` **THE notification module SHALL NOT retry a `FAILED` notification on a schedule.**
*(An SSE delivery failure means the farmer's browser is not connected. The client refetches its case
list on reconnect — `NOTIFY-FR-042` — which is a correct and much simpler recovery than a delivery
retry queue. This is a deliberate scope choice, recorded so no agent adds one.)*

### 4.4 SSE — the only live transport in scope

Clarification item 18: **SSE only**. There is no Web Push, no polling fallback, and no second stream
endpoint anywhere in the system.

`NOTIFY-FR-030` **THE notification module SHALL expose exactly one Server-Sent Events endpoint,
`GET /api/v1/stream`, producing `text/event-stream`, which fans out both case status changes and
advisory notifications to the authenticated subject.**
*(One endpoint, one connection per browser tab, one place where the fan-out logic lives. Two stream
endpoints — one in intake for status, one here for advisories — would mean two connection lifecycles,
two heartbeats and two reconnect strategies for one screen.)*

`NOTIFY-FR-031` **THE notification module SHALL derive the stream's subject from the JWT `sub` and
`role` claims, and SHALL NOT accept a subject id as a query parameter.**

`NOTIFY-SEC-001` **WHILE a `FARMER` subject is connected, THE notification module SHALL emit only
events whose `farmerId` equals that subject's id.** *(Per-subject filtering. A farmer must never see
another farmer's case id, disease or officer name.)*

`NOTIFY-SEC-002` **WHILE an `OFFICER` or `ADMIN` subject is connected, THE notification module SHALL
emit only `queue` events (`NOTIFY-FR-033`) and `kpi` events (`NOTIFY-FR-056`), and SHALL NOT emit any
farmer-addressed advisory notification.**

`NOTIFY-FR-032` **THE notification module SHALL emit an event named `advisory` to a farmer subject
for every notification whose type is `ADVISORY_PUBLISHED`, `ADVISORY_REVISED` or `CASE_REJECTED`, and
an event named `case-status` for `CASE_STATUS_CHANGED`.** The `data` payload is
`{ notificationId, caseId, advisoryId?, type, titleBn, bodyBn, correlationId }`.

`NOTIFY-FR-033` `[DERIVED]` **WHEN the notification module consumes `CaseStatusChanged`, THE
notification module SHALL additionally emit an event named `queue` to every connected `OFFICER` and
`ADMIN` subject, carrying `{ caseId, toStatus, correlationId }` and nothing else.**
*(Reasoning: the officer console must refresh its queue without polling — plan §8. The only event
this module is permitted to consume that marks a case entering, moving through or leaving the queue
is `CaseStatusChanged`: `→ ANALYSED` means a new task; `→ ADVISED` or `→ REJECTED` means one has
left. Consuming `AnalysisCompleted` directly would require `notification → analysis`, which the §6.1
matrix forbids. The frame deliberately carries no farmer name, crop or confidence — the console
refetches `GET /api/v1/review/queue`, which is the single ordered source of truth,
`REVIEW-FR-030`.)*

`NOTIFY-FR-034` **THE notification module SHALL NOT persist a `notification` row for a `queue`
or `kpi` event.** *(`notification.farmer_id` is `NOT NULL`; an officer nudge has no farmer recipient.
Officer live events are transient by design.)*

`NOTIFY-FR-035` **WHILE an SSE connection is open, THE notification module SHALL emit a comment-only
heartbeat frame every `foshol.channels.sse.heartbeat`.** *(Keeps intermediaries from closing an idle
connection and lets the browser's `EventSource` notice a dead one.)*

`NOTIFY-FR-036` **THE notification module SHALL close an SSE connection after
`foshol.channels.sse.timeout` and SHALL emit a terminal event named `reconnect` immediately before
closing.**

`NOTIFY-FR-037` **WHEN an SSE connection completes, times out or errors, THE notification module
SHALL remove its subscription from the registry.** *(An emitter leak is the standard SSE bug and it
shows up as an OutOfMemoryError twenty minutes into a demo.)*

`NOTIFY-FR-038` **THE notification module SHALL permit at most 5 concurrent SSE subscriptions per
subject, and IF a sixth is opened, THEN THE notification module SHALL close the oldest.**
*(A farmer with several tabs open is normal; an unbounded emitter list per subject is not.)*

`NOTIFY-FR-039` **WHILE `foshol.channels.sse.enabled` is false, THE notification module SHALL return
`503` from `GET /api/v1/stream` and `SseChannel.enabled()` SHALL return false.**

`NOTIFY-NFR-010` **THE notification module SHALL deliver an SSE frame to a connected farmer within
1 s of `AdvisoryApproved` being published** (`00-common.ears.md` §10.2).

`NOTIFY-NFR-011` **THE SSE endpoint SHALL run on a virtual thread** (`spring.threads.virtual.enabled`
is true globally) and SHALL NOT hold a database connection while a connection is idle.

### 4.5 Reconnection and `Last-Event-ID`

`NOTIFY-FR-040` **THE notification module SHALL set the SSE `id:` field of every emitted event to the
`notificationId` for a farmer event, and to a monotonic per-connection counter for a `queue` or
heartbeat event.**

`NOTIFY-FR-041` **WHEN a client reconnects with a `Last-Event-ID` header, THE notification module
SHALL accept the connection, SHALL log the received value at `DEBUG` with the subject id, and SHALL
emit a single event named `resync` as the first frame.**

`NOTIFY-FR-042` **WHEN a client receives a `resync` event, THE client SHALL refetch its current
state** — `GET /api/v1/cases` for a farmer, `GET /api/v1/review/queue` for an officer — rather than
expecting missed events to be replayed. This is the recovery path, and it is exact: both endpoints
return current state, so nothing is lost.

`NOTIFY-FR-043` `[DEFERRED]` **WHERE server-side replay of missed events is included, THE
notification module SHALL emit every notification created after the `Last-Event-ID` value, in
creation order, before resuming the live stream.**
*Deferred, with the reason stated so the seam is visible: replay needs a totally-ordered,
gap-free event log keyed by a cursor. `notification` is indexed by `(farmer_id, created_at DESC)`
(`ix_notification_farmer`) and its ids are UUIDv7 — approximately, not strictly, insertion-ordered
(`COMMON-NFR-012`) — and officer `queue` events are not persisted at all (`NOTIFY-FR-034`), so half
the stream could never be replayed. Building a correct cursor means a new ordered column and a new
migration, which is A1's work and outside the four-day scope. `NOTIFY-FR-041` and `NOTIFY-FR-042`
give correct behaviour without it: the client resynchronises from authoritative state instead of
replaying a partial log.*

### 4.6 The three channels — two of them are the extensibility proof

`NOTIFY-FR-050` **THE notification module SHALL register `SseChannel`, `WebPushChannel` and
`SmsChannel` as Spring beans, all three present in the application context at runtime in every
profile.**

`NOTIFY-FR-051` **THE `SseChannel` SHALL return `name() = "SSE"`, `enabled()` = the value of
`foshol.channels.sse.enabled`, `supports(farmerId)` = true when that farmer has at least one open
subscription, and `send()` = true when the frame was handed to at least one emitter without
error.**

`NOTIFY-FR-052` **THE `WebPushChannel` SHALL be a real, registered, compiled class which returns
`name() = "WEB_PUSH"`, `enabled()` = the value of `foshol.channels.webpush.enabled` (`false` in every
shipped profile), `supports(farmerId)` = false, and whose `send()` SHALL log one `INFO` line naming
the channel, the notification type and the case id, and SHALL return `false`.**

`NOTIFY-FR-053` **THE `SmsChannel` SHALL be a real, registered, compiled class which returns
`name() = "SMS"`, `enabled()` = the value of `foshol.channels.sms.enabled` (`false` in every shipped
profile), `supports(farmerId)` = false, and whose `send()` SHALL log one `INFO` line naming the
channel, the notification type and the case id, and SHALL return `false`.**

`NOTIFY-FR-054` **THE `WebPushChannel.send` and `SmsChannel.send` methods SHALL NOT read the database, SHALL NOT
make a network call, SHALL NOT throw, and SHALL NOT include a phone number or advisory body in the
line they log.**

`NOTIFY-FR-055` **IF `foshol.channels.webpush.enabled` or `foshol.channels.sms.enabled` is set to
true, THEN the corresponding channel SHALL continue to return false from `supports` and `send`, and
SHALL log one `WARN` at startup stating that the channel has no transport configured.**
*(Flipping a flag must not silently swallow a farmer's advisory by claiming a delivery that never
happened.)*

`NOTIFY-FR-056` **WHEN the notification module consumes `KpiWarningIssued`, THE notification module
SHALL emit an event named `kpi` to the assigned officer's open SSE subscriptions only**, carrying
`{ caseId, reviewTaskId, kind: RESOLUTION_WARN, dueAt, correlationId }`, and SHALL NOT write a
`notification` row.

`NOTIFY-FR-057` **WHEN the notification module consumes `ReviewTaskTransferred`, THE notification
module SHALL emit a `queue` event to connected officers and admins in that district.**

**These two classes are not stubs to be filled in later, and must not be described that way in a
comment, a log line or a progress note.** They are the shipped, permanent behaviour of a disabled
adapter, and they are opened on screen during the demo. What they demonstrate is that the
`NotificationChannel` port already has more than one implementation, that adding SMS is writing a new
`@Order(40)` bean and setting a property — not touching the review module, the event, the schema, or
anything a farmer sees. A port with one implementation proves nothing; a port with three, two of them
correctly declining to deliver, proves the boundary is real. The classes are listed by name in
`00-common.ears.md` §1.2 as the seam behind two deferred features, and `NOTIFY-FR-052` …
`NOTIFY-FR-055` are their complete specification.

### 4.7 Delivery states

| State | Cause |
|---|---|
| `PENDING` | The row was written before the channel chain ran. Every row starts here (`NOTIFY-FR-001`). |
| `SENT` | A channel's `send()` returned true. `channel` names it; `delivered_at` is set (`NOTIFY-FR-022`). |
| `FAILED` | Every enabled, supporting channel was called and each returned false or threw (`NOTIFY-FR-025`). |
| `SKIPPED` | No channel was both enabled and supporting, so nothing was called; `attempts = 0` (`NOTIFY-FR-024`). |

`NOTIFY-FR-060` **THE notification module SHALL transition a row from `PENDING` only to `SENT`,
`FAILED` or `SKIPPED`, and SHALL treat all three as terminal.**

`NOTIFY-FR-061` **IF a row is still `PENDING` when the delivery attempt returns, THEN THE
notification module SHALL set it to `FAILED`.** *(A `PENDING` row surviving a completed attempt means
a code path forgot to record its outcome. Defaulting it to `FAILED` makes the defect visible rather
than making a lost notification look pending forever.)*

`NOTIFY-FR-062` **THE notification module SHALL expose the delivery state on
`GET /api/v1/notifications` so a farmer can see what was sent to them and when.**

### 4.8 Idempotency

`NOTIFY-DATA-001` **IF a consumed event is delivered more than once, THEN THE notification module
SHALL create no additional `notification` row and SHALL perform no additional delivery.**
(`COMMON-ARCH-015`.)

`NOTIFY-DATA-002` `[DERIVED]` **THE notification module SHALL, inside the listener's transaction and
before creating a row, check for an existing row with the same `farmer_id`, `case_id`, `type` and
dedupe key, and SHALL abandon the handler when one is found.** The dedupe key is `advisory_id` for
`ADVISORY_PUBLISHED` and `ADVISORY_REVISED`, and `payload->>'toStatus'` for `CASE_STATUS_CHANGED`;
`CASE_REJECTED` needs none because a case has at most one rejection (`INV-CR-1`).
*(Reasoning: the schema in `00-common.ears.md` §4.7 declares no unique constraint that would express
this, and this document may not add a migration (`COMMON-NFR-042`). A guard query inside the
listener's own transaction is sufficient for the single-instance `docker compose` deployment that is
the only deployment target (clarification item 22). A partial unique index would be stronger and is
raised to A1 as an optional Day-0 item; the guard is correct without it.)*

`NOTIFY-DATA-003` **THE notification module SHALL make a repeated SSE emission idempotent from the
client's perspective by carrying `notificationId` in every `advisory` frame**, so a client that
receives a duplicate can discard it on id.

---

## 5. API surface

| # | Method | Path | Produces | Role | Success | Errors |
|---|---|---|---|---|---|---|
| 1 | `GET` | `/api/v1/stream` | `text/event-stream` | `FARMER`, `OFFICER`, `ADMIN` | `200`, open connection | `401`, `503` when SSE disabled |
| 2 | `GET` | `/api/v1/notifications` | `application/json` | `FARMER` (own only) | `200`, paged | `401` |

### 5.1 `GET /api/v1/stream`

Request headers: `Authorization: Bearer <jwt>` (required), `Last-Event-ID` (optional,
`NOTIFY-FR-041`). No query parameters; the subject comes from the token (`NOTIFY-FR-031`).

Emitted events:

| `event:` | Sent to | `data` |
|---|---|---|
| `advisory` | the owning farmer | `notificationId, caseId, advisoryId?, type, titleBn, bodyBn, correlationId` |
| `case-status` | the owning farmer | `notificationId, caseId, fromStatus, toStatus, correlationId` |
| `queue` | every officer / admin | `caseId, toStatus, correlationId` |
| `resync` | the reconnecting subject, once | `{}` |
| `reconnect` | the subject, once, before timeout close | `{}` |
| *(comment frame)* | every subject | heartbeat, no event name |

**Authorisation:** the connection is scoped to the token subject; there is no way to request another
subject's stream (`NOTIFY-SEC-001`, `NOTIFY-SEC-002`). Filtering is applied at emission, not at the
client.

### 5.2 `GET /api/v1/notifications`

Query: `page`, `size` (`00-common.ears.md` §8.2). Returns the page envelope over
`{ id, caseId, advisoryId, type, titleBn, bodyBn, channel, state, deliveredAt, createdAt }`, newest
first, served by `ix_notification_farmer`.

`NOTIFY-SEC-003` **THE notification module SHALL return only rows whose `farmer_id` equals the
calling subject, and SHALL NOT accept a `farmerId` parameter.** An `OFFICER` or `ADMIN` calling this
endpoint receives `403`.

---

## 6. Persistence

### 6.1 Table owned

`notification` — defined once in `00-common.ears.md` §4.7, created by `V7__notification.sql`. This
document defines no schema (`COMMON-NFR-042`).

### 6.2 Query patterns

| Pattern | Access | Index |
|---|---|---|
| Farmer notification list | read-only `DataSource` (`COMMON-ARCH-007`) | `ix_notification_farmer` |
| Idempotency guard (`NOTIFY-DATA-002`) | write, inside the listener transaction | `ix_notification_farmer` |
| Insert + terminal state update | write, one row per notification | PK |

`NOTIFY-NFR-020` **THE notification module SHALL NOT hold a database transaction open across a
channel `send()` call.** *(An SSE write to a slow client would otherwise pin a pooled connection.)*

`NOTIFY-NFR-021` **THE notification module SHALL keep the SSE subscription registry entirely in
memory and SHALL NOT persist connection state.**

---

## 7. Acceptance criteria

| Req | Given | When | Then |
|---|---|---|---|
| `NOTIFY-FR-002` | a connected farmer F with case C | `AdvisoryApproved` for C is published | one `notification` row, type `ADVISORY_PUBLISHED`, `state = SENT`, `channel = SSE`; F's stream receives one `advisory` frame |
| `NOTIFY-FR-003` | case C already has advisory v1 notified | `AdvisoryRevised` v2 is published | a **second** row of type `ADVISORY_REVISED` with the v2 advisory id; F receives a second frame |
| `NOTIFY-FR-004` | connected farmer F | `CaseRejected` is published | one row of type `CASE_REJECTED`, `advisory_id` null, `payload.reasonCode` set; body contains the officer's `messageBn` verbatim |
| `NOTIFY-FR-005` | connected farmer F | `CaseStatusChanged` `ANALYSING → ANALYSED` | one row of type `CASE_STATUS_CHANGED` with both statuses in `payload`; F receives a `case-status` frame |
| `NOTIFY-FR-006` | an event naming an unknown farmer id | the listener runs | no row written; one `WARN` logged; the listener completes normally |
| `NOTIFY-FR-011` | a rejection whose `messageBn` is a fixture string | the notification is assembled | `body_bn` contains that string unmodified — no rewording, no summary |
| `NOTIFY-FR-012` | any event | the notification is assembled | every character of `title_bn` and `body_bn` came from a message-key template or a human-supplied value; no generated prose |
| `NOTIFY-FR-020` | all three channels registered | a notification is delivered | `SseChannel` is consulted first, then `WebPushChannel`, then `SmsChannel` |
| `NOTIFY-FR-021` | webpush and sms disabled | a notification is delivered | neither `send()` is called |
| `NOTIFY-FR-022` | SSE supports the farmer | a notification is delivered | `SseChannel.send` returns true; no further channel is consulted; row is `SENT` |
| `NOTIFY-FR-023` | a channel double that throws | a notification is delivered | the exception is logged at `WARN`; the next channel is consulted; the handler does not throw |
| `NOTIFY-FR-024` | farmer F has no open connection; both other channels disabled | `AdvisoryApproved` is published | one row, `state = SKIPPED`, `attempts = 0` |
| `NOTIFY-FR-025` | SSE enabled and supporting but its emitter fails | delivery runs | one row, `state = FAILED`, `attempts = 1` |
| `NOTIFY-FR-030` | the running application | the route table is inspected | exactly one `text/event-stream` endpoint exists, `GET /api/v1/stream`, and it is owned by `notification` |
| `NOTIFY-SEC-001` | farmers F1 and F2 both connected | an advisory for F1 is published | F1 receives one frame; F2 receives none |
| `NOTIFY-SEC-002` | an officer and a farmer both connected | an advisory for the farmer is published | the officer receives no `advisory` frame |
| `NOTIFY-FR-033` | an officer connected | `CaseStatusChanged` `→ ANALYSED` | the officer receives one `queue` frame carrying only `caseId`, `toStatus`, `correlationId` |
| `NOTIFY-FR-034` | as above | the same event | no `notification` row was written for the officer |
| `NOTIFY-FR-035` | an idle connection, heartbeat `PT20S` | 45 s pass on the test clock | at least two heartbeat frames were emitted |
| `NOTIFY-FR-037` | a connected farmer | the client disconnects | the subscription registry no longer contains that emitter |
| `NOTIFY-FR-038` | 5 open connections for one subject | a sixth is opened | the oldest is closed; exactly 5 remain |
| `NOTIFY-FR-039` | `foshol.channels.sse.enabled=false` | `GET /api/v1/stream` | `503`; `SseChannel.enabled()` is false |
| `NOTIFY-FR-041` | a client reconnecting with `Last-Event-ID: <uuid>` | the connection opens | `200`; the first frame is `resync`; the header value appears in a `DEBUG` line |
| `NOTIFY-FR-052` | the application context | `WebPushChannel` is resolved | the bean exists, `enabled()` is false, `supports()` is false, `send()` logs once and returns false |
| `NOTIFY-FR-053` | the application context | `SmsChannel` is resolved | the bean exists, `enabled()` is false, `supports()` is false, `send()` logs once and returns false |
| `NOTIFY-FR-055` | `foshol.channels.sms.enabled=true` | the context starts and a notification is delivered | one `WARN` at startup; `send()` still returns false; the notification is not marked `SENT` by that channel |
| `NOTIFY-DATA-001` | `AdvisoryApproved` already handled for case C | the same event is republished | still exactly one row; no second SSE frame |
| `NOTIFY-DATA-002` | two `CaseStatusChanged` events for C with different `toStatus` | both are handled | two rows — the dedupe key distinguishes them |
| `NOTIFY-FR-061` | a delivery path that returns without setting a state | the attempt completes | the row is `FAILED`, not `PENDING` |
| `NOTIFY-SEC-003` | farmer F1 | `GET /api/v1/notifications` | only F1's rows; an officer token receives `403` |

---

## 8. Test requirements

### 8.1 Unit tests — domain invariants

- `NotificationTest` — `INV-N-1` … `INV-N-4`, the state machine of §4.7 including `NOTIFY-FR-060`
  and `NOTIFY-FR-061`.
- `NotificationContentAssemblerTest` — `NOTIFY-FR-010` … `NOTIFY-FR-014`, including the assertion
  that the assembler's output contains no character that did not come from a template or an input
  value.

### 8.2 Unit tests — every command and query handler

Mockito doubles for `FarmerLookupApi`, `ReviewSubmissionApi`, the repository and each
`NotificationChannel`. Handlers: `HandleAdvisoryApproved`, `HandleAdvisoryRevised`,
`HandleCaseRejected`, `HandleCaseStatusChanged`, `DeliverNotification`; queries:
`FarmerNotifications`.

`NOTIFY-NFR-030` **THE module SHALL contain one unit test class per event handler and per query
handler, each covering the happy path, the duplicate-event path and every delivery-state branch.**

`NOTIFY-NFR-031` **THE module SHALL contain a dedicated test class per channel** —
`SseChannelTest`, `WebPushChannelTest`, `SmsChannelTest` — asserting `name()`, `enabled()`,
`supports()` and `send()` exactly as specified in `NOTIFY-FR-051` … `NOTIFY-FR-055`. *(The two
disabled channels are demo material; a silent refactor that turns one into a no-op bean must fail the
build.)*

`NOTIFY-NFR-032` **THE module SHALL assert that the application context contains exactly three
`NotificationChannel` beans in the declared `@Order`.**

### 8.3 Integration test — exactly one, Testcontainers

`NOTIFY-NFR-033` **THE module SHALL contain exactly one Testcontainers integration test,
`NotificationFanOutIT`**, against `pgvector/pgvector:pg17` with the real migrations:

1. Open an SSE connection as farmer F1 and another as officer O1.
2. Publish `CaseStatusChanged` for F1's case: assert F1 receives `case-status`, O1 receives `queue`,
   one row for F1, none for O1.
3. Publish `AdvisoryApproved`: assert F1 receives `advisory` carrying the disease name, officer name
   and remedy count from the `ReviewSubmissionApi` stub; row is `SENT` on channel `SSE`.
4. Republish the same event: assert still one row and no second frame (`NOTIFY-DATA-001`).
5. Publish `AdvisoryRevised`: assert a second row and a second frame.
6. Open a second farmer connection F2 and assert it received none of the above (`NOTIFY-SEC-001`).
7. Disconnect F1, publish `CaseRejected`, assert the row is `SKIPPED` with `attempts = 0`.
8. Reconnect F1 with `Last-Event-ID` and assert the first frame is `resync`.
9. Assert `WebPushChannel` and `SmsChannel` are in the context and were never called.

### 8.4 Fixtures

| Fixture | Contents |
|---|---|
| `NotifyFixtures.advisoryView()` | an `AdvisoryView` with a fictional officer name, a placeholder disease name and two remedy refs — **no agronomic text** (`COMMON-CON-003`) |
| `NotifyFixtures.rejectionView()` | a `RejectionView` with a fixture Bangla message string, used verbatim to prove `NOTIFY-FR-011` |
| `RecordingChannel` | a test `NotificationChannel` capturing every call, for the ordering and fallback tests |
| `ThrowingChannel` | a test channel whose `send` throws, for `NOTIFY-FR-023` |
| Mutable `Clock` | so heartbeat and timeout are testable without sleeping (`NOTIFY-NFR-034`) |
| `SseTestClient` | a test client over `WebTestClient` collecting named frames with their `id:` values |

`NOTIFY-NFR-034` **THE module SHALL obtain the current instant from an injected `Clock` bean and
SHALL NOT call `Instant.now()` directly.**

`NOTIFY-NFR-035` **THE module SHALL author no Bangla content in any fixture beyond opaque
placeholder strings**, and SHALL reference `CONTENT-OWNERS.md` for the four message keys of
`NOTIFY-FR-012`.

---

## 9. Agent execution notes

### 9.1 Preconditions

A5 does not start until: `V7__notification.sql` is applied; `review`'s `api` package compiles with
`ReviewSubmissionApi`, `AdvisoryView`, `RejectionView`, `AdvisoryApproved`, `AdvisoryRevised` and
`CaseRejected`; `identity`'s `FarmerLookupApi` compiles; the constants of `NOTIFY-NFR-003` exist; and
**`NOTIFY-NFR-002` has been resolved by A1** — without it, `NOTIFY-FR-005` and `NOTIFY-FR-033` cannot
be built and `ApplicationModules.verify()` will fail.

Build `review` first: this module consumes its events and calls its API, and nothing consumes this
module.

### 9.2 Implementation order

1. `api/` — re-export nothing; `NotificationPort`, `AdvisoryNotification` and `NotificationChannel`
   are already frozen in `00-common.ears.md` §6.2. Create the package and the records to match
   exactly.
2. `domain/` — `Notification` aggregate, `DeliveryState`, `NotificationType` (imported from
   `common`), the invariants of §3.1. No Spring (`COMMON-ARCH-004`). `NotificationTest` alongside.
3. `application/` — `NotificationContentAssembler` (`NOTIFY-FR-010` … `NOTIFY-FR-015`) with its unit
   test, before any transport exists.
4. `infrastructure/sse/` — `SseSubscriptionRegistry`, then `SseChannel`, then the heartbeat scheduler
   (`NOTIFY-FR-035`) and the lifecycle callbacks (`NOTIFY-FR-037`).
5. `infrastructure/channel/` — `WebPushChannel` and `SmsChannel` exactly per `NOTIFY-FR-052` …
   `NOTIFY-FR-055`, with their tests. **Ten minutes of work; do not skip them and do not label them
   as placeholders.**
6. `application/DeliveryService` — the registry walk, `NOTIFY-FR-020` … `NOTIFY-FR-026`, and the
   state machine of §4.7.
7. `infrastructure/ReviewEventListener` and `IntakeEventListener` — `NOTIFY-FR-001` …
   `NOTIFY-FR-008`, with the idempotency guard of `NOTIFY-DATA-002`.
8. `web/StreamController` and `web/NotificationController` — thin (`COMMON-ARCH-009`).
9. `NotificationFanOutIT` last.

### 9.3 Traps

- **Do not** add a second SSE endpoint, and if intake has one, raise a blocker: `NOTIFY-FR-030` gives
  this module all of SSE.
- **Do not** write, translate, shorten or "improve" a Bangla string. Compose from keys
  (`NOTIFY-FR-012`).
- **Do not** describe `WebPushChannel` or `SmsChannel` as a stub or a TODO anywhere — in code, in a
  log line, or in `docs/progress/a5.md`. They are finished. `COMMON-NFR-040` item 8 of the Definition
  of Done forbids a `TODO` in any case.
- **Do not** add a delivery retry scheduler (`NOTIFY-FR-026`).
- **Do not** implement `Last-Event-ID` replay (`NOTIFY-FR-043` is `[DEFERRED]`); emit `resync`.
- **Do not** hold a transaction across `send()` (`NOTIFY-NFR-020`).
- **Do not** copy `CaseStatusChanged` into this module to dodge `NOTIFY-NFR-002`. Raise the blocker.

### 9.4 Local Definition of Done

`00-common.ears.md` §11 in full, plus:

1. `NotificationFanOutIT` green, including steps 4, 6 and 9.
2. Exactly three `NotificationChannel` beans, asserted by test (`NOTIFY-NFR-032`).
3. `GET /api/v1/stream` is the only `text/event-stream` route in the application, asserted against
   the route table.
4. Every requirement in §4 implemented or listed as `[DEFERRED]` in `docs/progress/a5.md`.
5. Both endpoints match `docs/openapi/foshol-api.yaml`.
6. Opening `SmsChannel.java` on a projector produces a class a reviewer would call finished.

---

## 10. Requirement index

| Category | IDs | Count |
|---|---|---|
| Functional | `NOTIFY-FR-001` … `-008`, `-010` … `-015`, `-020` … `-026`, `-030` … `-039`, `-040` … `-043`, `-050` … `-055`, `-060` … `-062` | 44 |
| Data | `NOTIFY-DATA-001` … `NOTIFY-DATA-003` | 3 |
| Security | `NOTIFY-SEC-001` … `NOTIFY-SEC-003` | 3 |
| Non-functional | `NOTIFY-NFR-001` … `-003`, `-010`, `-011`, `-020`, `-021`, `-030` … `-035` | 13 |
| **Total** | | **63** |

`[DEFERRED]`: `NOTIFY-FR-043` (server-side `Last-Event-ID` replay).
`[DERIVED]`: `NOTIFY-FR-033` (officer queue nudge derived from `CaseStatusChanged`),
`NOTIFY-DATA-002` (idempotency guard query in place of a unique index).
**Blocker raised to A1:** `NOTIFY-NFR-002` — the §6.1 dependency matrix and the §6.3 publish/consume
map disagree about `notification → intake`.
