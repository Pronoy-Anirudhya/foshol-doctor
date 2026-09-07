# ADR-0014: Client-supplied `Idempotency-Key`, not server-side dedupe on image hash

- **Status:** Accepted
- **Date:** 2026-09-07
- **Deciders:** project lead (human), requirements agent
- **Supersedes / relates to:** plan §3 (idempotency required, mechanism unspecified), clarification 14; relates to ADR-0012; schema in `00-common.ears.md` §4.4

## Context

`POST /api/v1/cases` is a multipart upload of one to three images and optional audio, from a handset
on a rural mobile connection. It returns `202 Accepted`, publishes `CaseSubmitted`, and starts an
analysis pipeline. It is exactly the kind of request that gets retried: the connection drops after the
server has committed but before the response arrives, and the client — or the user, tapping again —
sends it a second time. Unprotected, that produces two cases, two analysis runs, two review tasks and
two officers' worth of work on one problem. Plan §3 requires an idempotency key but names no
mechanism.

The tempting alternative is server-side deduplication: hash the uploaded images and refuse a
submission whose image set matches a recent one from the same farmer. `case_image.sha256` exists and
`ix_case_image_sha` indexes it, so the machinery is already there, and it asks nothing of the client.

**It is wrong here, and the reason is a real workflow, not a hypothetical.** Rejection is terminal
(ADR-0012): an officer rejects a case and the farmer's next step is a new one. Consider
`INSUFFICIENT_DETAIL` — the photograph was perfectly sharp, the officer simply wanted a shot of the
stem as well. The farmer resubmits the *same leaf photograph* plus a new one. Under image-hash dedupe
that legitimate resubmission collides with the rejected case and is refused: the farmer, having just
been asked for more information and provided it, is told they have already submitted this. The same
happens whenever a farmer photographs a recurring problem across a season. Deduplicating on content
conflates "this is the same request" with "this is the same picture", and only the client knows the
answer to the first.

## Decision

**The client generates an `Idempotency-Key` (a UUID) and sends it as a header on
`POST /api/v1/cases`. It is required, not optional.** `IntakeRequest` carries it as
`UUID idempotencyKey` (`00-common.ears.md` §6.2), so it is part of the frozen contract and binds any
future `CaseIntakeChannel` implementation, not just the web adapter.

The server stores the key with the request and its outcome (`00-common.ears.md` §4.4):
`idempotency_key(key, farmer_id, endpoint, request_hash, response_status, response_body, created_at,
expires_at)`, with `key` as the primary key.

| Situation | Response |
|---|---|
| Key unseen | Process normally; store key, request hash, status and body |
| Key seen, `request_hash` matches | Replay the stored status and body — the same `caseId`, not a new one |
| Key seen, `request_hash` differs | `409`, per `00-common.ears.md` §8.2 |

Because the key is the primary key, two concurrent retries race on an insert and the database picks a
winner — no application-level lock. `expires_at` is `created_at + foshol.intake.idempotency.ttl`
(`PT24H`), swept via `ix_idempotency_expiry`; a key reused after expiry is a new request, which is
correct, because a client is not retrying a day later. `request_hash` covers the semantic content of
the submission — farmer, crop, note, parent case, and the SHA-256 of each uploaded part — so an
identical retry replays while a different request reusing a key is caught rather than silently treated
as a duplicate.

**Image SHA-256 is still recorded and still useful.** `case_image.sha256` keys the replay fixture
store (ADR-0010) and lets an officer see that a photograph has appeared before. It is evidence for a
human; it is not a submission gate.

## Consequences

### Positive

- The dropped-connection retry produces exactly one case, and the client gets the original `caseId`
  rather than a second one.
- **A farmer resubmitting the same leaf photograph after a rejection is never blocked.** That workflow
  is a direct consequence of ADR-0012 and is not an edge case.
- The semantics are explicit and testable in three unit tests plus one path in the intake integration
  test: fresh key, replayed key, conflicting key.
- Concurrency is handled by a primary-key constraint rather than by a lock.
- The key lives on the frozen `IntakeRequest`, so an SMS or USSD intake adapter inherits the guarantee
  without redesign.
- Stored responses make a retry cheap: no re-upload processing, no second MinIO write.

### Negative / accepted cost

- **It depends on the client behaving.** A client that generates a fresh key on every tap gets a new
  case every tap. The guarantee is only as good as the frontend, and nothing server-side can detect
  the mistake. This is the central cost and it is accepted knowingly.
- One extra table, one extra write on the submission hot path, and a TTL sweep.
- Response bodies are stored as `jsonb`, so an endpoint whose response shape changes will replay an
  old shape to a retrying client within the TTL window. At 24 hours and one endpoint, tolerable.
- A user who deliberately submits the same photographs twice as two genuine cases can do so, because
  nothing deduplicates content. That is intended, and it does mean duplicate officer work is possible.
- `request_hash` must be computed over the multipart body deterministically. Part ordering and
  filename handling have to be pinned, or an identical retry hashes differently and returns `409` to a
  client that did nothing wrong.

## Alternatives considered

**Server-side dedupe on image SHA-256 within a time window.** Rejected on the resubmission argument
above. It also breaks a farmer photographing a recurring problem across a season, and it answers the
wrong question — content identity is not request identity.

**Server-generated key returned from a `POST /api/v1/cases/prepare` step.** Rejected. It doubles the
round trips on the worst connection in the system, and the prepare call needs its own idempotency
story — the original problem one level down.

**No idempotency; accept duplicates and let officers merge.** Rejected. Officer time is the scarcest
resource in the system (ADR-0003) and duplicates spend it twice.

**Optional `Idempotency-Key`, applied only when present.** Rejected. Optional protection is absent
exactly when a client was written carelessly. Required means one code path and one test fixture.

**Idempotency on every mutating endpoint.** Rejected as scope: only `POST /api/v1/cases` carries a
large, retry-prone, expensive-to-duplicate payload. Officer actions are small, authenticated and
naturally guarded by review-task state transitions returning `409`.

## Revisit when

Revisit if duplicate cases appear in the queue despite the mechanism — that is the signal that the
Angular client is generating a key per tap rather than per submission attempt, and the fix is in
`web/`, not in the schema. Revisit the TTL if a client is observed retrying beyond 24 hours, which
would mean an offline queue exists that this design did not anticipate. Revisit the scope the first
time a second endpoint accepts a large upload.
