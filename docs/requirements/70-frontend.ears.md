# 70 — Frontend (EARS)

**Module prefix:** `WEB` · **Owner:** agent A6 · **Path:** `web/` (exclusive write access, `COMMON-NFR-040`)

> Read `00-common.ears.md` first and treat it as immutable. That document plus this one are together
> sufficient to build the frontend. Traces to plan §8 and the demo script in plan §11. Requirements
> marked `[DERIVED]` extend the plan and carry inline reasoning.

**Blocking precondition.** The Angular version is pinned as `22.1.5` in
`00-common.ears.md` §2.3. Per `COMMON-NFR-003` agent A1 replaces that token at kickoff; per
`COMMON-NFR-004` **A6 is blocked and SHALL NOT begin implementation while the token is still
present.** Do not guess a version.

---

## 1. Scope

One Angular single-page application serving **three surfaces** from one bundle: the **farmer** app,
the **officer console**, and an **admin dashboard** (stats strip, district case list, bulk reject).
It renders state the backend produced,
captures farmer input, and makes the two-threshold routing and the human approval step *visible*.

### 1.1 This module owns

`web/` in its entirety — routing, layout shell, all three surfaces, the client-side capture pipeline
(image compression, local quality pre-filter, audio capture and normalisation), all client-side state
(Angular signals), the SSE consumer, the `bn.json` / `en.json` UI catalogues, and regeneration (never
hand-editing, `COMMON-NFR-043`) of `web/src/app/generated/`.

### 1.2 This module does **not** own

| Not owned | Owner |
|---|---|
| Any endpoint, path, status code or payload schema | the owning backend module; frozen in `docs/openapi/foshol-api.yaml` (A1) |
| Decision path, confidence values, thresholds, routing rule | `12-analysis.ears.md` |
| The officer queue order | `14-review.ears.md`, fixed by `REVIEW-FR-030` |
| SSE event production, fan-out, heartbeat | `15-notification.ears.md` |
| The authoritative image quality gate | `11-intake.ears.md` |
| Disease, symptom, remedy and severity content, Bangla phrases | human-supplied, `CONTENT-OWNERS.md` (`COMMON-CON-003`) |
| Grad-CAM generation | `60-inference-sidecar.ears.md`; the frontend only displays the stored overlay |
| Any Java, Python, Gradle or migration file | A1–A5, A7 |

`WEB-NFR-001` **THE frontend SHALL contain no business rule that duplicates a backend rule.** Where
the backend has decided something — decision path, severity, claim state, queue order — the frontend
renders the server's value and SHALL NOT recompute it. *(Two implementations of one rule diverge, and
the one on screen is then a lie about the one in the database.)*

---

## 2. Dependencies

### 2.1 Technology, pinned

Restated from `00-common.ears.md` §2.2 — reference only, do not vary: **Angular
`22.1.5`** with standalone components and an exact pin (no `^`, no `~`) ·
**Angular signals** for state · Tailwind CSS 4.x · **ngx-translate** · **`ng-openapi-gen`** ·
Angular CLI (esbuild) · no charting library, confidence bars are CSS.

`WEB-NFR-002` **THE frontend SHALL hold all application state in Angular signals and SHALL NOT
introduce NgRx, NgRx SignalStore, Akita, Elf, MobX, Redux or any other third-party state-management
library.** *(Stated as a requirement because an agent reaching for "the standard Angular state
solution" will install NgRx unprompted, and four days cannot absorb that surface area.)*

`WEB-NFR-003` **THE frontend SHALL use RxJS only where the Angular API in use requires an
`Observable`** — `HttpClient` and router events — and SHALL convert to a signal at the store
boundary. No RxJS store, no `BehaviorSubject` acting as state, no long-lived manual subscription
outside the SSE client (§4.7).

`WEB-NFR-004` **THE frontend SHALL use ngx-translate for every user-visible string and SHALL NOT use
Angular's `$localize` / `i18n` attribute mechanism.** *(`$localize` is build-time: a BN/EN toggle
would need two builds and a reload to switch, which kills the judge-facing toggle. ngx-translate swaps
catalogues at runtime in one click.)*

`WEB-NFR-005` **THE frontend SHALL generate its HTTP client with `ng-openapi-gen` from
`docs/openapi/foshol-api.yaml` into `web/src/app/generated/`, SHALL commit that output, and SHALL
regenerate rather than edit it (`COMMON-NFR-043`).**

`WEB-NFR-006` **IF a needed operation, field or enum value is absent from the generated client, THEN
THE frontend agent SHALL raise a blocker per `COMMON-NFR-046` and SHALL NOT hand-write a request, a
URL or a model type to work around it.**

`WEB-NFR-007` **THE frontend SHALL add no runtime dependency beyond those in §2.1.** A needed library
is a blocker, not a choice. *(Frontend expression of `COMMON-NFR-001`.)*

`WEB-NFR-008` **THE frontend SHALL build with zero TypeScript errors under `strict` and zero template
errors under `strictTemplates`.**

### 2.2 Contracts consumed

The frontend consumes JSON over HTTP; the generated models mirror the records frozen in
`00-common.ears.md` §6.2. The three the frontend is built around, quoted so this document stands
alone:

```java
public record AnalysisView(UUID caseId, DecisionPath decisionPath, AiMode mode,
                           BigDecimal top1Confidence, BigDecimal top2Confidence, BigDecimal margin,
                           List<CandidateView> candidates, List<SymptomView> symptoms,
                           String transcriptBn, BigDecimal asrConfidence, String gradcamObjectKey,
                           List<String> unmappedLabels, String visionModelId,
                           String visionModelVersion, int latencyMs, String errorCode) {}
public record CandidateView(UUID diseaseId, String diseaseCode, String diseaseNameBn,
                            BigDecimal confidence, int rank, CandidateSource source) {}
```

Also consumed unchanged: `CaseSummary`, `CaseImageRef`, `CaseAudioRef`, `SymptomView`,
`AdvisoryView`, `RejectionView`, `CropView`, `DiseaseView`, `RemedyView`, `RemedyRefView`, and the
enumerations
`CaseStatus` · `DecisionPath` · `CandidateSource` · `SymptomSource` · `AdvisoryAction` ·
`RemedyType` · `Severity` · `AiMode` · `RejectionReason` · `NotificationType` · `Role`.

`WEB-DATA-001` **THE frontend SHALL represent every enumerated value as the TypeScript union type
generated from the OpenAPI schema and SHALL NOT declare a string literal for an enum value outside
it.** *(Frontend expression of `COMMON-ARCH-010`.)*

### 2.3 Events consumed

Over SSE only, from `GET /api/v1/stream`. Event types are the `NotificationType` values
`ADVISORY_PUBLISHED` · `ADVISORY_REVISED` · `CASE_REJECTED` · `CASE_STATUS_CHANGED`. The stream, its
payloads, its heartbeat (`foshol.channels.sse.heartbeat`) and its timeout
(`foshol.channels.sse.timeout`) are specified by `15-notification.ears.md` (`NOTIFY-*`) and are **not
restated here**. The frontend publishes no events.

---

## 3. Client-side state model

The nine-section structure's "domain model", adapted: the frontend owns no aggregate. It owns
**signal stores** and **view models**. Each store is a standalone `providedIn: 'root'` injectable
exposing `readonly` signals and `computed` derivations; mutation happens only through its own methods.

| Store | Signals | Invariants |
|---|---|---|
| `SessionStore` | `token`, `role`, `subjectId`, `displayName`, `isAuthenticated` (computed) | token in memory only (§6); `role` read from the JWT claim, never from a route |
| `LanguageStore` | `current` (`'bn' \| 'en'`), `catalogueLoaded` | defaults to `bn`; only `bn` and `en` representable |
| `CaseDraftStore` | `cropId`, `images[]`, `audio`, `noteBn`, `idempotencyKey`, `submitState` | between `foshol.intake.min-images` and `foshol.intake.max-images` before submit is enabled; one key per attempt |
| `CaseStatusStore` | `byCaseId`, `lastEventAt` | status only takes the value the server sent; the client invents no transition |
| `QueueStore` | `rows[]`, `page`, `total`, `loading`, `staleSince` | rows held in server order, never re-sorted client-side (`REVIEW-FR-030`) |
| `CaseReviewStore` | `analysis`, `candidates[]`, `symptoms[]`, `remedyDraft`, `claim` | `remedyDraft` is never discarded by a background refresh |
| `SseStore` | `connectionState`, `retryAttempt`, `lastEventId` | `connectionState ∈ {CONNECTING, OPEN, RETRYING, CLOSED}` |
| `StatsStore` | `casesToday`, `casesThisMonth`, `casesThisYear`, `casesLifetime`, `approvalRate`, `medianReviewMs`, `agreementRate`, `rejectionRate`, `thresholdHigh`, `thresholdLow` | read-only; no setter exposed |

`WEB-DATA-002` **THE frontend SHALL expose every store signal as `readonly`, SHALL mutate state only
through a method on the owning store, and SHALL derive with `computed()` every value that can be
derived.** *(A writable signal handed to a template is a cross-component write path with no name.)*

`WEB-DATA-004` **THE `CaseDraftStore` SHALL hold at most `foshol.intake.max-images` images and SHALL
refuse an additional image with an in-place message rather than discarding one silently.**

`WEB-DATA-005` **THE frontend SHALL generate one `Idempotency-Key` (UUID) per submission attempt,
SHALL send it on `POST /api/v1/cases`, and SHALL reuse it for every retry of that same attempt**, a
new key being generated only when the draft content changes. *(Server contract: plan clarification
14; a key reused with a different body returns `409`.)*

`WEB-DATA-006` **THE frontend SHALL treat every received instant as UTC and SHALL render it in
`Asia/Dhaka` (`foshol.i18n.display-zone`, `COMMON-NFR-011`).** Time-zone conversion happens here and
nowhere else in the system.

### 3.1 Client configuration constants

Numbers the client needs that no server property defines. They live in one file,
`web/src/app/core/app-config.ts`, each named, each commented with the server property it mirrors.

| Constant | Value | Mirrors / note |
|---|---|---|
| `capture.maxEdgePx` | 1600 | compression target; must exceed `capture.minEdgePx` |
| `capture.jpegQuality` | 0.82 | chosen to stay under `foshol.intake.max-image-bytes` |
| `capture.minEdgePx` · `capture.blurVarianceMin` | — | mirror `foshol.intake.quality.min-edge-px` · `.blur-variance-min` |
| `capture.vegetationCoverageMin` | 0.12 | `[DERIVED]`, see `WEB-FR-124` |
| `audio.sampleRateHz` · `audio.channels` · `audio.targetPeakDbfs` | 16000 · 1 · −3.0 | ASR input rate, mono, normalisation target |
| `audio.maxSeconds` | — | mirrors `foshol.intake.max-audio-seconds` |
| `sse.initialRetryMs` · `sse.maxRetryMs` · `sse.backoffMultiplier` · `sse.jitterRatio` | 1000 · 30000 · 2 · 0.2 | reconnect backoff |
| `queue.pageSize` | 20 | `COMMON` §8.2 default, max 100 |
| `ui.spinnerDelayMs` | 300 | delay before a loading indicator appears |

`WEB-NFR-009` **THE frontend SHALL define every threshold, limit, interval and size as a named
constant in `app-config.ts` and SHALL NOT place a numeric literal in a component or service.**
*(Frontend expression of `COMMON-NFR-020`.)*

`WEB-NFR-010` **IF a constant mirrors a server property, THEN THE frontend SHALL prefer the value the
API supplies at runtime and SHALL use the local constant only as a fallback.**

`WEB-NFR-011` `[DERIVED]` **THE officer console SHALL read both routing thresholds from the
`thresholds` object on the `GET /api/v1/cases/{caseId}/analysis` response and SHALL NOT hard-code
either value.**

*(Reasoning: the confidence bar draws both thresholds as vertical lines — a protected wow factor —
and a hard-coded line that disagrees with the routing is worse than no line at all.)*

> **Blocker raised by A6, RESOLVED by A1.** This requirement originally warned that no
> `OFFICER`-visible endpoint might expose both thresholds, since `REVIEW-FR-070` exposes them only on
> the `ADMIN`-scoped stats response. `docs/openapi/foshol-api.yaml` in fact carries a `Thresholds`
> schema on **`AnalysisDetail`**, which every officer loads for every case, as well as on
> `AdminStats`. Bind to `analysis.thresholds`; no admin role is needed and no blocker remains.

---

## 4. Requirements

### 4.1 Shell, routing and cross-cutting behaviour

`WEB-NFR-012` **THE frontend SHALL be one Angular application built from `web/`, with standalone
components and no `NgModule` beyond what the CLI scaffolds.**

`WEB-FR-001` **THE frontend SHALL route by role**: `/farmer/**` for `FARMER`, `/officer/**` for
`OFFICER` and `ADMIN`, `/admin/**` for `ADMIN` only, `/auth/**` unauthenticated.

`WEB-FR-002` **IF an unauthenticated user requests a guarded route, THEN THE frontend SHALL redirect
to that surface's login and SHALL retain the requested URL for restoration after login.**

`WEB-FR-003` **IF an authenticated user requests a route their role does not permit, THEN THE
frontend SHALL show a "not permitted" page and SHALL NOT issue the request.** *(The server enforces
this too, `COMMON-SEC-011`; the client avoids a pointless `403`.)*

`WEB-FR-004` **THE frontend SHALL lazy-load the officer and admin route groups**, so the farmer
bundle does not carry the console.

`WEB-FR-005` **WHEN a response carries an RFC 9457 problem document (`COMMON-API-002`), THE frontend
SHALL display its `title` and `detail`, and SHALL display the `correlationId` in copyable form on
error pages.** *(The demo machine has no log aggregator; the correlation id on screen is how a
failure gets diagnosed in the room.)*

`WEB-FR-006` **THE frontend SHALL send `X-Correlation-Id` when continuing a known interaction, SHALL
adopt the returned value otherwise (`COMMON-NFR-016`), and SHALL send `Accept-Language` matching
`LanguageStore.current` on every request** (`COMMON-API-003`).

### 4.2 Internationalisation

`WEB-UX-010` **THE frontend SHALL ship `assets/i18n/bn.json` and `assets/i18n/en.json` from the first
commit of the first component, and SHALL add a key to both files in the same change that introduces
it.** *(Plan §8: "build both from Day 1 or you will retrofit strings on Day 5." A four-day build has
no last day to spare, and a screen written with hard-coded Bangla is rewritten, not translated.)*

`WEB-UX-011` **THE frontend SHALL default to Bangla (`foshol.i18n.default-locale`) and SHALL treat
Bangla as the language of record (`COMMON-NFR-037`).**

`WEB-UX-012` **WHEN the user activates the language toggle, THE frontend SHALL switch every UI string
without a page reload and without losing unsaved form state.**

`WEB-UX-013` **THE frontend SHALL contain no user-visible string literal in a template or component
class; every string SHALL be a translation key resolved through ngx-translate.**

`WEB-UX-014` **IF a translation key is missing from the active catalogue, THEN THE frontend SHALL
fall back to the Bangla catalogue and SHALL render the raw key in a visibly marked style outside
production builds.** *(A silently blank label is the failure mode that survives to the demo.)*

`WEB-UX-015` **WHEN a content field arrives with its sibling `<field>Fallback` flag `true`
(`COMMON-NFR-038`), THE frontend SHALL render the Bangla value with a visible `(bn)` text marker
beside it and an accessible description stating that no English translation exists.**

`WEB-UX-016` **THE frontend SHALL NOT translate content fields.** Disease, symptom, remedy and
rejection text renders exactly as returned. *(An agent-authored translation of agronomic content is
agent-authored agronomic content — `COMMON-CON-003`.)*

`WEB-UX-017` **THE `bn.json` and `en.json` catalogues SHALL contain identical key sets** (tested,
`WEB-TEST-003`).

### 4.3 Authentication and session

`WEB-FR-010` **THE farmer login SHALL collect a phone number, request an OTP, then collect a
`foshol.auth.otp.length`-digit code.** **WHEN `POST /api/v1/auth/otp/request` returns `202`, THE
frontend SHALL proceed to the code step. WHEN it returns `404` `ERR_FARMER_NOT_FOUND`, THE frontend
SHALL remain on the phone step and SHALL display the problem `detail`.**

`WEB-FR-011` **WHILE OTP requests are rate-limited (`429`, `COMMON-SEC-015`), THE frontend SHALL
disable the request control and SHALL display the wait derived from `Retry-After`.**

`WEB-FR-012` **THE officer and admin login SHALL collect a username and password and exchange them
for a JWT.**

`WEB-FR-013` **WHEN any response returns `401`, THE frontend SHALL clear the session, navigate to the
current surface's login, and retain the attempted route.**

`WEB-SEC-001` **THE frontend SHALL hold the JWT in a private signal inside `SessionStore` in memory
only**, and SHALL NOT write it to `localStorage`, `sessionStorage`, IndexedDB, a cookie, the URL, the
document title or any log statement.

> **The storage decision, stated plainly.** The token lives in a JavaScript variable for the lifetime
> of the tab and nowhere else. **Trade-off:** a refresh or a new tab loses the session and the user
> logs in again — a real cost, accepted because (a) there is no refresh token and no server session
> (`COMMON-SEC-012`), so a lost token costs exactly one login, (b) demo re-login is a fixed dev OTP or
> a seeded password and takes seconds, and (c) persistent storage adds a token-theft surface that
> outlives the tab, while a cookie adds a CSRF surface and a `SameSite`/HTTPS problem on the demo
> machine. In-memory storage is **not** immunity to XSS; it removes persistence and CSRF, and that is
> the whole of the claim.

`WEB-SEC-002` **THE frontend SHALL attach the JWT as an `Authorization: Bearer` header via an
`HttpInterceptor` and SHALL NOT place a token, phone number, OTP code or object key in a URL, query
parameter or route fragment.** *(Also why the SSE client is fetch-based — `WEB-FR-350`.)*

`WEB-SEC-003` **THE frontend SHALL send the `Authorization` header only to the configured API origin**
and SHALL NOT attach it to a presigned object-store URL, which carries its own authorisation
(`COMMON-SEC-016`).

`WEB-SEC-004` **WHEN the user logs out, THE frontend SHALL clear every store holding session, case
and draft media state and SHALL close the SSE connection.**

`WEB-SEC-005` **THE frontend SHALL NOT render any server-supplied string as HTML.** All content is
interpolated as text; `innerHTML` and `bypassSecurityTrustHtml` are forbidden.

`WEB-SEC-006` **THE frontend SHALL display a phone number only as its last four digits after
authentication and SHALL never echo a submitted OTP code.**

### 4.4 Farmer surface

Traces to plan §8 surface 1 and demo beats 1–3 and 5.

**Crop selection**

`WEB-FR-100` **THE crop picker SHALL present each crop from the crops endpoint as a large touch
target carrying the crop's `iconKey` pictogram and its `nameBn`, and SHALL NOT use a `<select>`
dropdown.** *(Plan §8: "large icons, not a dropdown" — the user may be low-literacy, on a phone, in a
field.)*

`WEB-FR-101` **WHILE no crop is selected, THE frontend SHALL keep the capture step disabled.**

**Image capture**

`WEB-FR-110` **THE capture control SHALL offer both device camera and file selection**, using a file
input with `capture` on mobile viewports, and SHALL accept only types in
`foshol.intake.allowed-image-types`.

`WEB-FR-111` **WHEN an image is chosen, THE frontend SHALL render a preview thumbnail immediately,
before any network call.**

`WEB-FR-112` **WHEN an image is chosen, THE frontend SHALL re-encode it client-side to at most
`capture.maxEdgePx` on the longest edge at `capture.jpegQuality`, applying and then stripping EXIF
orientation together with all other EXIF metadata, and SHALL upload the re-encoded image.** *(A 12 MP
phone photo exceeds `foshol.intake.max-image-bytes` and would fail with `413` after a long upload on
field data. GPS coordinates in a photograph are farmer location data this system has no requirement
to collect.)*

`WEB-FR-114` **IF the re-encoded image still exceeds `foshol.intake.max-image-bytes`, THEN THE
frontend SHALL reduce quality stepwise and SHALL reject the image with a message naming the limit if
it cannot be brought under it.**

`WEB-FR-115` **THE frontend SHALL accept between `foshol.intake.min-images` and
`foshol.intake.max-images` images per case and SHALL allow removal and reordering of previews before
submission.**

**Local quality gate — demo beat 3**

`WEB-FR-120` **WHEN an image is chosen, THE frontend SHALL evaluate it locally for minimum edge
length and blur before any upload begins.**

`WEB-FR-121` **IF the image's shorter edge is below `capture.minEdgePx`, THEN THE frontend SHALL
reject it locally and SHALL display the Bangla re-capture prompt.**

`WEB-FR-122` **IF the image's computed blur variance is below `capture.blurVarianceMin`, THEN THE
frontend SHALL reject it locally and SHALL display the Bangla re-capture prompt naming blur as the
reason.**

`WEB-FR-123` **THE local rejection message SHALL be visible before any upload request for that image
has completed.** *(This is demo beat 3. A rejection that arrives after a round trip is a server
rejection wearing a client's clothes, and on a slow connection the demo shows a spinner instead of
the point being made.)*

`WEB-FR-124` `[DERIVED]` **IF the image's estimated vegetation coverage is below
`capture.vegetationCoverageMin`, THEN THE frontend SHALL display the same re-capture prompt with a
"not a crop photograph" reason AND SHALL offer a "send anyway" control.** *(Reasoning: plan §11 beat
3 requires a local reject for a "blurry **or non-crop**" photo, but no client-side model exists to
classify "crop"; a hue and saturation coverage heuristic on the downscaled canvas is the honest
approximation. Because the heuristic is weak it must never permanently block a farmer — the override
makes a false positive cost one extra tap. Blur and size rejections get no override, because the
server gate would reject those anyway.)*

`WEB-FR-125` **THE frontend SHALL NOT treat its local verdict as authoritative.** The server-side
quality gate in `11-intake.ears.md` remains the decision of record; an officer rejection is handled
by `WEB-FR-160`.

**Voice capture**

`WEB-FR-130` **THE recorder SHALL capture audio only while the record control is held** and SHALL
stop on release.

`WEB-FR-131` **THE recorder SHALL request `audio.sampleRateHz` mono capture (`audio.channels`)
through `MediaRecorder` with matching `getUserMedia` constraints, and SHALL resample client-side if
the device grants a different rate.** *(The ASR model consumes 16 kHz mono; 48 kHz stereo wastes
upload budget and forces a server-side resample.)*

`WEB-FR-132` **WHILE recording, THE frontend SHALL render a live waveform driven by an `AnalyserNode`
and SHALL display elapsed seconds against `foshol.intake.max-audio-seconds`.**

`WEB-FR-133` **WHEN the recording reaches `foshol.intake.max-audio-seconds`, THE frontend SHALL stop
recording automatically and SHALL keep what was captured.**

`WEB-FR-134` **WHEN recording stops, THE frontend SHALL normalise the clip's loudness to
`audio.targetPeakDbfs` before upload.** *(Plan §12 names loudness normalisation as the mitigation for
demo-room audio; a clip recorded at arm's length in a noisy room is quiet, and quiet input is the
cheapest cause of a bad transcript.)*

`WEB-FR-135` **THE frontend SHALL allow playback, discard and re-record before submission, and SHALL
upload only a type listed in `foshol.intake.allowed-audio-types`.**

`WEB-FR-136` **IF microphone permission is denied or no microphone exists, THEN THE frontend SHALL
hide the recorder, SHALL display a one-line explanation, and SHALL leave the text box
(`WEB-FR-140`) as the described path.**

**Mobile capture — the farmer records on a phone**

> The farmer surface is used on a phone held in the field, not on the demo laptop. Every requirement
> below exists because a desktop-Chromium-only capture pipeline fails on a real device in a specific,
> reproducible way. Traces to `COMMON-NFR-047`.

`WEB-FR-137` `[DERIVED]` **IF `window.isSecureContext` is false or `navigator.mediaDevices` is
undefined, THEN THE frontend SHALL hide the recorder, SHALL display a diagnostic naming the insecure
origin as the cause, and SHALL NOT render a record control that cannot function.**

*(`getUserMedia` exists only in a secure context — HTTPS, or the `localhost` exemption. A phone
loading an `http://` LAN address gets `navigator.mediaDevices === undefined`. Without this check the
farmer sees a normal record button that does nothing when held, which reads as a broken app rather
than a misconfigured origin. See `COMMON-SEC-018`; the operator fix is to serve over HTTPS, not to
change the client.)*

`WEB-FR-138` `[DERIVED]` **THE recorder SHALL select its container by querying
`MediaRecorder.isTypeSupported` over the candidates in `foshol.intake.allowed-audio-types`, in
preference order `audio/webm;codecs=opus` then `audio/mp4` then `audio/ogg`, and SHALL upload the
first supported type.**

*(Safari's `MediaRecorder` produces `audio/mp4` (AAC) and does not support `audio/webm` at all.
Hard-coding `audio/webm` yields an empty or rejected recording on every iPhone. `audio/mp4` is in the
allowed list for this reason — the server sniffs magic bytes per `COMMON-SEC-014`, so the client's
declared type is advisory and the two must genuinely agree.)*

`WEB-FR-139` `[DERIVED]` **IF no candidate container is supported, THEN THE frontend SHALL hide the
recorder and fall back to the text box (`WEB-FR-140`)** rather than uploading a payload the server
will reject with `415`.

`WEB-FR-142` `[DERIVED]` **WHEN the farmer first presses the record control, THE frontend SHALL
resume the `AudioContext` inside that user-gesture handler.**

*(iOS Safari creates every `AudioContext` in the `suspended` state and resumes it only from a user
gesture. A context resumed on component initialisation stays suspended, and the `AnalyserNode`
waveform of `WEB-FR-132` renders as a flat line while recording appears to work — a confusing
half-failure. Recording is hold-to-record, so the gesture is already there; it must be the thing that
resumes the context.)*

`WEB-FR-143` `[DERIVED]` **THE record control SHALL be driven by Pointer Events with pointer
capture**, and SHALL treat `pointercancel` and `pointerleave` as a release that keeps the audio
captured so far.

*(Touch on a phone is interrupted routinely — a scroll gesture stealing the touch, the system back
swipe, a notification. Without pointer capture the recorder never receives the release, and it either
runs to the `foshol.intake.max-audio-seconds` cap or hangs holding the microphone.)*

`WEB-FR-144` `[DERIVED]` **WHEN the page becomes hidden (`visibilitychange`) or the recording is
interrupted by the operating system, THE frontend SHALL stop recording, SHALL keep what was captured,
and SHALL release the microphone track.**

*(An incoming call or a backgrounded tab suspends media capture on both platforms. Keeping the
partial clip is right — a farmer who was interrupted mid-sentence should not lose the recording — and
releasing the track is what turns off the phone's recording indicator.)*

`WEB-FR-145` `[DERIVED]` **THE frontend SHALL stop every `MediaStreamTrack` when recording ends,
when the capture screen is left, and on submission.** *(A live track leaves the microphone indicator
lit on both iOS and Android, which looks to a farmer — correctly — like the app is still listening.)*

`WEB-FR-146` `[DERIVED]` **THE frontend SHALL NOT rely on the `sampleRate` constraint being honoured**
and SHALL verify the achieved rate from the track settings, resampling client-side per `WEB-FR-131`
when it differs. *(iOS ignores `sampleRate` in `getUserMedia` constraints and commonly grants 44.1 or
48 kHz.)*

`WEB-UX-021` `[DERIVED]` **THE record control SHALL be at least 64 × 64 CSS pixels on touch
viewports** — larger than the 44 px floor of `WEB-UX-033` — and SHALL show an unambiguous held state.
*(It is operated by thumb, one-handed, outdoors, possibly with wet or soiled hands.)*

**The always-available text path**

`WEB-FR-140` **THE frontend SHALL present a free-text Bangla description box on the capture screen at
all times — regardless of microphone availability, recording state or network state — submitted as
`noteBn`.**

`WEB-FR-141` **THE text box SHALL NOT be hidden behind a disclosure control, accordion or secondary
screen.** *(Plan §12 names the always-available text box as the ASR risk mitigation. A degraded path
that must be found is not a degraded path.)*

**Submission, status and history**

`WEB-FR-150` **WHEN the farmer submits, THE frontend SHALL send one multipart request carrying crop,
images, optional audio, optional note and the `Idempotency-Key` header, and SHALL show a submitting
state until the server responds.**

`WEB-FR-151` **WHEN submission returns `202`, THE frontend SHALL navigate to the case status view for
the returned case id.**

`WEB-FR-152` **WHILE a case is not in a terminal status, THE case status view SHALL display the
current `CaseStatus` as a stepper and SHALL update it from SSE events without polling
(`WEB-FR-353`).**

`WEB-FR-153` **THE case history list SHALL show the farmer's cases newest first with crop name,
status, submitted time and a thumbnail where one exists.**

`WEB-FR-154` **THE frontend SHALL load every stored image and audio object through the presigned URL
the API returned (`COMMON-SEC-016`) and SHALL NOT construct an object-store URL.**

**Advisory card — demo beat 5**

`WEB-FR-155` **WHEN an advisory is available, THE advisory card SHALL display the disease name, the
severity, the officer's name, the published time in `Asia/Dhaka`, and a visible "verified by" stamp
naming the officer.** *(This is the sentence the product exists to make true; it belongs on screen,
not in the deck.)*

`WEB-FR-156` **THE advisory card SHALL render each remedy's `stepsBn` as an ordered numbered list in
the order received, one step per line, with a pictogram chosen by `RemedyType` and a severity
indicator carrying both a colour and a text label (`WEB-UX-044`).**

`WEB-FR-157` **WHERE a remedy carries `phiDays`, THE advisory card SHALL display it as a labelled
field.** *(Value and units come from the server; the frontend authors no dosage or interval text —
`COMMON-CON-003`.)*

`WEB-FR-158` **WHEN an advisory has `version > 1`, THE advisory card SHALL display the latest version
with a "revised" marker and SHALL provide access to the prior version.** *(ADR-0013: "the farmer can
always see what they were told, and when".)*

`WEB-FR-160` **WHEN a case is rejected, THE frontend SHALL display the officer's Bangla rejection
message and SHALL offer "submit a new case", opening a fresh draft pre-filled with the same crop and
carrying `parentCaseId`.** *(Rejection is terminal — ADR-0012.)*

### 4.5 Officer console

Traces to plan §8 surface 2 and demo beats 4, 6 and 8.

**Queue**

`WEB-FR-200` **THE queue SHALL display rows exactly in the order the server returned them and SHALL
NOT expose a client-side sort control on any column.** *(`REVIEW-FR-030` fixes the order to
least-confident-first. A sortable header lets an officer — or a judge — destroy the one detail the
queue exists to demonstrate.)*

`WEB-FR-201` **THE queue SHALL show per row: farmer name, crop, top candidate name, top confidence,
decision-path badge, image count, audio presence, submitted time, SLA due time, review state, and a
re-submission marker where `isResubmission` is set.**

`WEB-FR-202` **THE queue SHALL display static text adjacent to the table explaining that the order is
least-confident first.**

`WEB-FR-204` **WHEN an SSE event changes a queued case, THE queue SHALL update the affected row in
place without a full reload and SHALL NOT re-order rows locally**; a change that would require
re-ordering triggers a refetch of the current page.

`WEB-FR-205` **THE queue SHALL provide a manual refresh control at all times.** *(The degraded path
when SSE is disconnected — explicitly not a polling timer, `WEB-FR-356`.)*

**Case detail**

`WEB-FR-210` **THE case detail SHALL display every image with a zoom control** operable by pointer
and by keyboard.

`WEB-FR-211` **WHERE `gradcamObjectKey` is present, THE case detail SHALL offer a Grad-CAM overlay
toggle over the primary image, defaulting to off.**

`WEB-FR-212` **IF `gradcamObjectKey` is absent, THEN THE case detail SHALL hide the toggle rather
than show a disabled or broken control.**

`WEB-FR-213` **THE case detail SHALL provide an audio player for the case audio and SHALL display the
Bangla transcript alongside it.**

`WEB-FR-214` **THE case detail SHALL display each extracted symptom as a chip carrying the symptom
name, its score, and the matcher that found it (`VECTOR`, `FUZZY` or `MANUAL`).** *(An officer's
trust in a symptom depends on how it was found; a fuzzy token match and a vector match are not the
same claim.)*

`WEB-FR-215` **THE case detail SHALL display the decision-path badge with the value `PRIMARY`,
`SECONDARY` or `UNDETERMINED` as received and SHALL NOT compute it (`WEB-NFR-001`).**

`WEB-FR-216` **THE case detail SHALL display the analysis mode badge `REPLAY` or `LIVE` on every
case** (`COMMON-UX-001`).

`WEB-FR-217` **THE case detail SHALL display the model identifier, model version, latency and margin
from `AnalysisView`, and WHERE `unmappedLabels` is non-empty SHALL display the count of unmapped
model outputs.** *(`COMMON-DATA-012` records unmapped labels so they are never silently dropped;
showing the count is what makes that true for a human.)*

`WEB-FR-219` **IF `errorCode` is present on the analysis, THEN THE case detail SHALL display a
degraded-analysis banner naming the code while keeping the four actions available.** *(Demo beat 8:
the sidecar is killed live and the case still reaches the officer as `UNDETERMINED`. The banner turns
a failure into a demonstrated behaviour.)*

**Confidence bars — protected wow factor**

`WEB-FR-220` **THE candidate list SHALL render one horizontal confidence bar per candidate, filled in
proportion to `confidence` over the range 0 to 1.**

`WEB-FR-221` **THE confidence bar SHALL draw two vertical threshold lines, at
`foshol.analysis.confidence.low` and at `foshol.analysis.confidence.high`, on every bar it renders.**
*(Plan §8: this is what makes F5's routing visible rather than described. Both lines, on every bar,
always — a bar without them is a progress indicator, not an explanation.)*

`WEB-FR-222` **THE confidence bar SHALL label each threshold line with its numeric value and SHALL
render the numeric confidence as text beside the bar**, so the routing is readable without perceiving
fill colour or line position (`WEB-UX-044`).

`WEB-FR-223` **THE confidence bar SHALL position each threshold line at exactly the proportional
position of its value and SHALL remain correct when a candidate's confidence equals a threshold
exactly** (tested at both boundaries, `WEB-TEST-001`).

`WEB-FR-224` **THE confidence bar SHALL NOT derive, display or imply a decision path**; the path comes
from `WEB-FR-215`.

**Actions and claim lifecycle**

`WEB-FR-230` **THE officer console SHALL offer exactly four terminal actions on a claimed case:
Approve, Edit, Replace, Reject.**

`WEB-FR-231` **THE remedy editor SHALL open pre-filled with the active remedies of the selected
disease as returned by the API, and SHALL allow the officer to modify step text, add and remove
remedies, and add an officer note.**

`WEB-FR-232` **THE Replace action SHALL allow selection of a different disease from the crop's
disease list and SHALL re-fill the remedy editor from that disease.**

`WEB-FR-233` **THE Reject action SHALL require a `RejectionReason` value and a Bangla message before
it can be submitted.**

`WEB-FR-234` **WHEN an action succeeds, THE frontend SHALL return the officer to the queue and SHALL
show a confirmation naming the case.**

`WEB-FR-235` **IF an action returns `409`, THEN THE frontend SHALL display a state-conflict message,
SHALL refresh the case, and SHALL preserve the officer's unsaved editor content.**

`WEB-FR-240` **THE officer SHALL claim a case before any action control is enabled**, and the console
SHALL show claim state and an explicit release control on the case detail.

`WEB-FR-241` **WHILE a case is claimed by the current officer, THE console SHALL display the claim
time remaining, derived from `foshol.review.claim.ttl` and the claim timestamp.**

`WEB-FR-242` **WHEN the remaining claim time reaches zero, THE console SHALL display a claim-expired
banner, SHALL disable the four actions, SHALL retain the officer's unsaved remedy edits in the form,
and SHALL offer a re-claim control.** *(The sweeper returns an expired claim to `PENDING`, plan
clarification 4. Discarding the officer's typing at that moment is the worst possible response to a
background timer.)*

`WEB-FR-243` **IF a re-claim fails because another officer now holds the case, THEN THE console SHALL
switch the case detail to read-only and SHALL keep the unsaved text visible with an explanation that
it can no longer be submitted.**

`WEB-FR-244` **IF an action is attempted on a case whose claim has expired, THEN THE frontend SHALL
surface the server's `409` per `WEB-FR-235` and SHALL NOT retry automatically.**

### 4.6 Admin surface — stats strip, district case list, bulk reject

`WEB-FR-300` **THE admin surface SHALL consist of a stats strip, a district case list, bulk
reject of selected cases, and the district farmer directory and provision screens of `WEB-FR-310`.**

`WEB-FR-301` **THE stats page SHALL display cases today, approval rate, median review time and
model-vs-officer agreement rate**, sourced from the admin stats endpoint (`REVIEW-FR-070`).

`WEB-FR-302` **THE stats page SHALL display `foshol.analysis.confidence.high` and
`foshol.analysis.confidence.low` as read-only values labelled as the routing thresholds.** *(Plan
clarification 20: the two-threshold story is on screen without an editable control that would need
write endpoints nobody is building.)*

`WEB-FR-303` **THE admin surface SHALL issue writes through bulk reject of selected district
cases** (`REVIEW-FR-103`, `REVIEW-FR-104`) **and through staff farmer provision** (`IDENTITY-FR-021`,
`IDENTITY-FR-025`). Thresholds and stats remain read-only. Knowledge CRUD remains `[DEFERRED]`
(`WEB-FR-900` … `WEB-FR-904`).

`WEB-FR-304` **THE stats page SHALL display the timestamp of the data shown and SHALL offer a manual
refresh.**

`WEB-FR-305` **IF the stats request fails, THEN THE stats page SHALL display the error and the last
successfully loaded values under a stale marker.**

`WEB-FR-306` **THE stats strip SHALL display cases this month, this year and lifetime, and the
rejection rate**, sourced from `GET /api/v1/admin/stats` (`REVIEW-FR-099`, `REVIEW-FR-100`). A null
rate SHALL render as `—`.

`WEB-FR-307` **THE stats strip SHALL display failed assignment KPI count and failed resolution KPI
count**, sourced from `GET /api/v1/admin/kpis` (`REVIEW-FR-095`). Those numbers SHALL NOT be
recomputed on the client.

`WEB-FR-308` **THE admin surface SHALL list cases for the caller's district via
`GET /api/v1/admin/cases`**, with curated filters `period`, `state`, `kpi`, `officerId`, `cropCode`,
`decisionPath` and `resubmission` (`REVIEW-FR-101`). The table SHALL NOT re-sort rows client-side.

`WEB-FR-309` **WHEN the admin selects one or more list rows and confirms bulk reject, THE frontend
SHALL call `POST /api/v1/review/tasks/bulk-reject` with one shared `reasonCode` and `messageBn`
supplied by the admin**, SHALL NOT invent Bangla rejection text (`COMMON-CON-003`), and SHALL show
per-item success and failure from the response.

### 4.6.1 Farmer directory and provision (`OFFICER` and `ADMIN`)

Day-N additive. Officers register farmers in the field after a seed sale; admins use the same APIs
from `/admin/farmers`. Seed commerce itself is out of scope.

`WEB-FR-310` **THE officer console SHALL provide `/officer/farmers` and THE admin surface SHALL
provide `/admin/farmers`, sharing one feature module.** Both surfaces SHALL call the same generated
client operations (`registerFarmer`, `listFarmers`, `getFarmer`, `downloadFarmerImportTemplate`,
`importFarmers`).

`WEB-FR-311` **THE register form SHALL collect phone first, then name, then preferred language
(`bn` default), with division and district locked to `GET /api/v1/me` and not editable.** Submitting
SHALL send `Idempotency-Key` (UUID, one per attempt) on `POST /api/v1/farmers`.

`WEB-FR-312` **THE directory SHALL list the caller's district via `GET /api/v1/farmers` in server
order, with optional name search `q` and optional exact phone lookup `phone`, never both, and SHALL
NEVER display a phone number from a response.** Duplicate register (`409` `ERR_FARMER_PHONE_EXISTS`)
SHALL be shown as "already registered" without revealing another district.

`WEB-FR-313` **THE bulk import screen SHALL download the CSV template, accept a UTF-8 CSV file, POST
it to `/api/v1/farmers/import`, and render per-row `OK`/`FAILED` from `FarmerImportResult`.** The UI
SHALL NOT invent extra CSV columns.

`WEB-FR-314` **THE frontend SHALL lock geo pickers to the principal's `divisionCode` and
`districtCode` even if the geo catalogue returns other districts.**

#### `[DEFERRED]` — admin CRUD screens

Cut per plan clarification 19 (cut candidate #1: highest build cost, lowest demo value). Knowledge
content is loaded by Flyway seed migrations and `KnowledgeQueryApi` is read-only, so these screens
have nothing to call — the corresponding write endpoints are themselves `[DEFERRED]` in
`13-knowledge.ears.md`. The seam is kept visible:

`WEB-FR-900` `[DEFERRED]` **THE admin surface SHALL provide CRUD screens for crops and diseases.**
`WEB-FR-901` `[DEFERRED]` **THE admin surface SHALL provide CRUD screens for symptoms and symptom
phrases.**
`WEB-FR-902` `[DEFERRED]` **THE admin surface SHALL provide CRUD screens for remedies.**
`WEB-FR-903` `[DEFERRED]` **THE admin surface SHALL provide an editable threshold configuration
screen.** *(Thresholds are properties, not data — plan clarification 20.)*
`WEB-FR-904` `[DEFERRED]` **THE admin surface SHALL provide an audit-log viewer.** *(Audit columns
are written; the reader is cut — `00-common` §1.2.)*

### 4.7 SSE client

`WEB-FR-350` **THE frontend SHALL consume `GET /api/v1/stream` using a `fetch`-based streaming reader
and SHALL NOT use `EventSource`.** *(`EventSource` cannot set an `Authorization` header, which would
force the JWT into the query string and violate `WEB-SEC-002`. A fetch reader carries the header.)*

`WEB-FR-351` **WHEN a session becomes authenticated, THE frontend SHALL open exactly one SSE
connection for the whole application**, shared by every surface.

`WEB-FR-352` **THE frontend SHALL handle the event types defined in `15-notification.ears.md` and
SHALL ignore an unrecognised event type without disconnecting.**

`WEB-FR-353` **WHEN a `CASE_STATUS_CHANGED` event arrives, THE frontend SHALL update `CaseStatusStore`
and the farmer's status view with no network round trip.**

`WEB-FR-354` **WHEN an `ADVISORY_PUBLISHED` or `ADVISORY_REVISED` event arrives, THE frontend SHALL
show an in-app toast and SHALL refresh the affected case view.** *(Demo beat 5.)*

`WEB-FR-355` **WHEN the SSE connection closes or errors, THE frontend SHALL reconnect with
exponential backoff from `sse.initialRetryMs` by `sse.backoffMultiplier` up to `sse.maxRetryMs`,
applying `sse.jitterRatio` jitter, and SHALL reset the delay after a successful connection.**

`WEB-FR-356` **THE frontend SHALL NOT poll any endpoint on a timer.** Live updates come from SSE; the
degraded path is the manual refresh control (`WEB-FR-205`, `WEB-FR-304`).

`WEB-FR-357` **WHILE the SSE connection is not open, THE frontend SHALL display a discreet
"reconnecting" indicator and SHALL keep the rest of the UI fully usable.**

`WEB-FR-358` **WHEN the connection reopens after a gap, THE frontend SHALL refetch the data backing
the visible view.** *(A gap means events were missed; the visible view must not stay wrong.)*

`WEB-FR-359` **IF the officer queue receives no queue-affecting events over the stream, THEN THE
frontend agent SHALL raise a blocker per `COMMON-NFR-046` and SHALL NOT introduce polling.**

### 4.8 Responsive layout

`WEB-UX-030` **THE frontend SHALL be authored mobile-first and SHALL be verified at viewport widths
360 px, 768 px and 1280 px** (plan §8).

`WEB-UX-031` **THE frontend SHALL produce no horizontal page scroll at 360 px on any route.** Wide
content scrolls inside its own container.

| Surface | 360 px (base) | 768 px (`md`) | 1280 px (`xl`) |
|---|---|---|---|
| **Farmer** | single column; crop tiles 2-up; capture button fixed within thumb reach; previews as a horizontal strip; advisory steps full width | crop tiles 3-up; capture preview beside controls; advisory card constrained to a readable measure | content capped and centred; advisory card two-column (steps beside remedy meta); history list beside the detail |
| **Officer** | queue as stacked cards, not a table; case detail sections collapsed into an accordion; actions in a sticky bottom bar | queue as a table with a reduced column set (crop, confidence, path, submitted); case detail single column, panels expanded | two-pane: queue left, case detail right; image beside candidate list; full column set including SLA and re-submission marker |
| **Admin** | stat tiles stacked one per row, thresholds below | tiles 2-up | tiles 4-up in one row, the threshold pair as a second row |

`WEB-UX-032` **THE officer queue SHALL render as stacked cards below the `md` breakpoint and as a
table at and above it.** *(A ten-column table at 360 px is unreadable; the console is desktop-first in
practice but must not be broken on a phone.)*

`WEB-UX-033` **THE frontend SHALL keep every primary action reachable without horizontal scrolling at
360 px and SHALL size every interactive target at no less than 44 × 44 CSS pixels on touch
viewports.**

`WEB-UX-034` **THE frontend SHALL use only Tailwind's configured breakpoints and SHALL NOT introduce
an ad-hoc media query in component CSS.**

### 4.9 Accessibility floor

`WEB-UX-040` **THE frontend SHALL make every interactive control reachable and operable by keyboard
alone in a logical tab order** — including the crop picker, image zoom, Grad-CAM toggle, recorder and
the four officer actions.

`WEB-UX-041` **THE frontend SHALL render a visible focus indicator on every focusable element** and
SHALL NOT remove the default outline without replacing it.

`WEB-UX-042` **THE frontend SHALL provide meaningful alternative text for every image**, including
crop and remedy pictograms, uploaded case images and the Grad-CAM overlay.

`WEB-UX-043` **THE frontend SHALL meet a contrast ratio of at least 4.5:1 for body text and 3:1 for
large text, icons and interface component boundaries** (WCAG 2.1 AA). *(Stated as a floor because the
severity palette and the confidence bars are the two places a designer's instinct produces a
low-contrast result.)*

`WEB-UX-044` **THE frontend SHALL NOT use colour as the sole carrier of meaning.** Every severity
indicator carries a text label and a shape or icon; every decision-path badge carries its text value;
every confidence bar carries its numeric value and labelled threshold lines; the `(bn)` fallback
marker is text.

`WEB-UX-045` **THE frontend SHALL set `lang` on the document root to the active locale and SHALL
update it when the language toggles.**

`WEB-UX-046` **THE frontend SHALL announce asynchronous status changes — case transitions, SSE
toasts, action results — through an ARIA live region, and SHALL label every form control visibly or
programmatically with its validation message associated to it.**

### 4.10 Error, loading and degradation behaviour

`WEB-FR-400` **THE frontend SHALL render a skeleton or spinner for any request exceeding
`ui.spinnerDelayMs` and SHALL NOT leave a blank region.**

`WEB-FR-401` **IF a request fails with `503`, THEN THE frontend SHALL display a "temporarily
unavailable" state with a retry control and SHALL NOT retry automatically more than once.**

`WEB-FR-402` **IF the network is offline, THEN THE frontend SHALL display an offline banner and SHALL
preserve the current draft in memory.**

`WEB-FR-403` **IF a submission fails after the request was sent, THEN THE frontend SHALL offer a
retry that reuses the same `Idempotency-Key` (`WEB-DATA-005`).**

`WEB-FR-404` **THE frontend SHALL log no user data to the browser console in a production build.**

### 4.11 `[DEFERRED]` — with reasons

Kept here so each seam stays visible (`00-common` §1.2).

`WEB-FR-910` `[DEFERRED]` **THE frontend SHALL register `@angular/service-worker` and cache the
application shell for offline use.** *Reason: in this build the service worker exists only to carry
Web Push, and Web Push is cut (plan clarification 18) — HTTPS and notification-permission friction on
the demo machine, with SSE carrying the entire live-update beat. Nothing else in four-day scope needs
offline caching. Seam: the package is not installed; adding it is `ng add` plus one manifest.*

`WEB-FR-911` `[DEFERRED]` **THE frontend SHALL subscribe to Web Push (VAPID) and display a push
notification when an advisory is published.** *Reason: as above. Seam: `WebPushChannel` ships as a
real registered `NotificationChannel` behind `foshol.channels.webpush.enabled=false`, so only the
browser half is missing.*

`WEB-FR-912` `[DEFERRED]` **THE advisory card SHALL read the advisory aloud in Bangla using the
browser `SpeechSynthesis` API with `lang="bn-BD"`.** *Reason: plan §7.3 says Bangla voice support is
inconsistent and must be verified on the actual demo machine — which cannot be done ahead of time. A
feature that may silently do nothing on stage is worse than an absent one. Seam: none required; it is
a single component method.*

`WEB-FR-913` `[DEFERRED]` **THE recorder SHALL fall back to the Web Speech API with `lang="bn-BD"`
for client-side transcription when the server ASR path is unavailable.** *Decision and reason — this
is **out of scope**, deliberately. Browser `bn-BD` recognition support is as unverifiable as `bn-BD`
synthesis; it would produce a second transcript with no `asr_confidence`, no `case_audio` row and no
provenance; and the degraded path is already covered by the always-available text box
(`WEB-FR-140`), which costs nothing and always works. In a four-day budget, an unverifiable fallback
for an already-covered path is the definition of work to cut.*

---

## 5. API surface consumed

The frontend **defines no endpoint**. The table is the frontend's view of the frozen contract; where
it and `docs/openapi/foshol-api.yaml` differ, **the snapshot wins**.

`WEB-API-001` **THE frontend SHALL call the backend exclusively through services in
`web/src/app/generated/` and SHALL NOT construct a request URL by string concatenation.** The single
exception is the SSE stream, which is not an OpenAPI operation (`WEB-FR-350`).

| Purpose | Method + path (indicative) | Role | Owner |
|---|---|---|---|
| Request OTP · verify OTP · officer login | `POST /api/v1/auth/otp/request` · `/otp/verify` · `/login` | anonymous | `10-identity` |
| Crops for the picker | `GET /api/v1/crops` | any | `13-knowledge` |
| Submit a case | `POST /api/v1/cases` | `FARMER` | `11-intake` |
| Case history · case detail with presigned media | `GET /api/v1/cases` · `/cases/{id}` | owner | `11-intake` |
| Published advisory and its history | `GET /api/v1/cases/{id}/advisory` | owner, `OFFICER` | `14-review` |
| Officer queue page | `GET /api/v1/review/queue` | `OFFICER` | `14-review` |
| Analysis detail | `GET /api/v1/cases/{id}/analysis` | `OFFICER` | `12-analysis` |
| Claim · release | `POST /api/v1/review/tasks/{id}/claim` · `/release` | `OFFICER` | `14-review` |
| Approve · edit · replace · reject | `POST /api/v1/review/tasks/{id}/…` | `OFFICER` | `14-review` |
| Remedies for a disease | `GET /api/v1/diseases/{id}/remedies` | `OFFICER` | `13-knowledge` |
| Stats and thresholds | `GET /api/v1/admin/stats` | `ADMIN` | `14-review` |
| District KPI failures | `GET /api/v1/admin/kpis` | `ADMIN` | `14-review` |
| District case list | `GET /api/v1/admin/cases` | `ADMIN` | `14-review` |
| Bulk reject | `POST /api/v1/review/tasks/bulk-reject` | `ADMIN` | `14-review` |
| District farmer directory · register · CSV import | `GET/POST /api/v1/farmers` · `/import` · `/import/template` | `OFFICER`, `ADMIN` | `10-identity` |
| Live updates | `GET /api/v1/stream` | authenticated | `15-notification` |

`WEB-API-002` **THE frontend SHALL treat `404` as "not found or not yours" and SHALL NOT infer
existence from it** (`COMMON-API-001`).

`WEB-API-003` **THE frontend SHALL paginate every list request with `page` and `size` — `queue.pageSize`
on the officer queue — and SHALL read `content`, `page`, `size`, `totalElements` and `totalPages`
from the envelope** (`COMMON` §8.2).

`WEB-API-004` **THE frontend SHALL send `Idempotency-Key` on `POST /api/v1/cases` and on
`POST /api/v1/farmers`.**

`WEB-API-005` **IF the generated client and this table disagree, THEN THE frontend SHALL follow the
generated client and THE agent SHALL record the discrepancy as a blocker.**

---

## 6. Client-side storage

The nine-section structure's "persistence", adapted: the frontend owns no table. It owns what
survives a navigation and what survives a tab.

| Item | Where | Lifetime |
|---|---|---|
| JWT, role, subject id, display name | `SessionStore` signals, memory | tab, or until `401` (`WEB-SEC-001`) |
| Language preference | `localStorage` key `foshol.lang` | persistent — non-sensitive, and a judge should not re-toggle after a refresh |
| Draft crop id and note text | `localStorage` key `foshol.draft` | until submit succeeds — a mistyped tap should not cost the typing |
| Draft images and audio (`Blob`) | `CaseDraftStore`, memory | until submit or discard (`WEB-DATA-021`) |
| Idempotency key for the current attempt | `CaseDraftStore`, memory | one submission attempt |
| SSE `lastEventId` | `SseStore`, memory | connection lifetime |
| Queue, case, analysis and stats data | stores, memory | view lifetime — the server is the record |

`WEB-DATA-020` **THE frontend SHALL persist to `localStorage` only the language preference and the
draft crop id and note text, under the keys named above.**

`WEB-DATA-021` **THE frontend SHALL NOT persist image bytes, audio bytes, a transcript, a phone
number, an OTP code, a JWT, a presigned URL, an advisory, or any farmer or officer name to
`localStorage`, `sessionStorage`, IndexedDB, the Cache API or a cookie.** *(Case media is farmer data
guarded by a server-side ownership check; a copy in browser storage outlives the session, escapes
that check, and would still be on a shared demo laptop tomorrow. Presigned URLs are time-limited by
design — `COMMON-SEC-016` — and persisting one defeats the limit.)*

`WEB-DATA-022` **WHEN a case submission succeeds, THE frontend SHALL clear the persisted draft and
SHALL revoke every object URL created for the previews.**

`WEB-DATA-023` **WHEN the user logs out, THE frontend SHALL clear every key it owns in `localStorage`
except the language preference.**

`WEB-DATA-024` **THE frontend SHALL tolerate `localStorage` being unavailable or throwing** and SHALL
render correctly with no stored value.

---

## 7. Acceptance criteria

One scenario per load-bearing requirement, written to map onto a single test method.

| # | Given / When / Then | Covers |
|---|---|---|
| AC-01 | Given the token `22.1.5` is still present, when A6 starts, then it records a blocker and writes no code | `COMMON-NFR-004` |
| AC-02 | Given the capture screen, when it renders, then crops appear as icon tiles and no `<select>` element exists | `WEB-FR-100` |
| AC-03 | Given a 12 MP JPEG, when chosen, then a preview renders before any request and the uploaded blob is ≤ `foshol.intake.max-image-bytes` with longest edge ≤ `capture.maxEdgePx` | `WEB-FR-111`, `WEB-FR-112` |
| AC-04 | Given an image below `capture.blurVarianceMin`, when chosen, then the Bangla re-capture prompt is visible and zero upload requests were issued | `WEB-FR-122`, `WEB-FR-123` |
| AC-05 | Given an image failing the vegetation heuristic, then a "send anyway" control exists; given one failing the blur check, then it does not | `WEB-FR-124` |
| AC-06 | Given the recorder held past `foshol.intake.max-audio-seconds`, when the cap is reached, then recording stops and the clip is retained | `WEB-FR-133` |
| AC-07 | Given a captured clip, when recording stops, then the uploaded clip is mono at `audio.sampleRateHz` with peak at `audio.targetPeakDbfs` | `WEB-FR-131`, `WEB-FR-134` |
| AC-08 | Given microphone permission denied, when the capture screen renders, then the recorder is hidden and the text box is visible with no disclosure interaction | `WEB-FR-136`, `WEB-FR-141` |
| AC-09 | Given confidence exactly `foshol.analysis.confidence.high`, when the bar renders, then two threshold lines exist at their proportional positions and the fill edge coincides with the high line | `WEB-FR-221`, `WEB-FR-223` |
| AC-10 | Given a rendered confidence bar, when colour is removed, then the numeric confidence and both threshold values remain readable as text | `WEB-FR-222`, `WEB-UX-044` |
| AC-11 | Given the queue, when the DOM is inspected, then no column header is interactive and row order equals the server's response order | `WEB-FR-200` |
| AC-12 | Given a case analysed in replay mode, when the officer opens it, then a `REPLAY` badge is displayed | `WEB-FR-216`, `COMMON-UX-001` |
| AC-13 | Given an analysis carrying `errorCode`, when the detail renders, then the degraded banner names the code and the four actions remain available | `WEB-FR-219` |
| AC-14 | Given a claimed case with unsaved edits, when the claim TTL elapses, then the expired banner appears, actions are disabled, edits remain, and re-claim is offered | `WEB-FR-242` |
| AC-15 | Given an expired claim now held by another officer, when re-claim is attempted, then the case becomes read-only and the unsaved text stays visible | `WEB-FR-243` |
| AC-16 | Given a response with `<field>Fallback: true`, when the English catalogue is active, then the Bangla value renders with a `(bn)` text marker and an accessible description | `WEB-UX-015`, `COMMON-NFR-038` |
| AC-17 | Given the app in Bangla, when the toggle is activated, then every chrome string switches with no reload and unsaved form values are unchanged | `WEB-UX-012` |
| AC-18 | Given `bn.json` and `en.json`, when their key sets are compared, then they are identical | `WEB-UX-017` |
| AC-19 | Given an authenticated session, when the SSE connection is inspected, then it was opened by `fetch` with an `Authorization` header and no token appears in any URL | `WEB-FR-350`, `WEB-SEC-002` |
| AC-20 | Given an open stream, when the connection drops three times, then delays follow `sse.initialRetryMs × sse.backoffMultiplier^n` capped at `sse.maxRetryMs` and reset after success | `WEB-FR-355` |
| AC-21 | Given a farmer watching a case, when `CASE_STATUS_CHANGED` arrives, then the stepper advances with no additional request | `WEB-FR-353`, `WEB-FR-356` |
| AC-22 | Given a published advisory, when the farmer views it, then the officer's name and "verified by" stamp are visible and steps render as a numbered list in order | `WEB-FR-155`, `WEB-FR-156` |
| AC-23 | Given a rejected case, when the farmer views it, then the Bangla message shows and "submit a new case" opens a draft carrying `parentCaseId` | `WEB-FR-160` |
| AC-24 | Given the admin stats page, when it renders, then both threshold values are displayed and the only write control is bulk reject | `WEB-FR-302`, `WEB-FR-303` |
| AC-29 | Given the admin dashboard, when it renders, then month, year, lifetime counts and rejection rate are shown, and assignment and resolution KPI failures come from `/admin/kpis` | `WEB-FR-306`, `WEB-FR-307` |
| AC-30 | Given selected district cases and a shared Bangla rejection message typed by the admin, when bulk reject is confirmed, then one bulk-reject request is sent with that message | `WEB-FR-309` |
| AC-25 | Given a logout, when storage is inspected, then only `foshol.lang` remains and the SSE connection is closed | `WEB-DATA-023`, `WEB-SEC-004` |
| AC-26 | Given 360 px, 768 px and 1280 px, when every route renders, then no horizontal page scroll occurs and the queue is cards below `md`, a table at and above | `WEB-UX-031`, `WEB-UX-032` |
| AC-27 | Given any surface, when navigated by keyboard alone, then every action is reachable with a visible focus indicator | `WEB-UX-040`, `WEB-UX-041` |
| AC-28 | Given a retried submission, when the second request is sent, then it carries the same `Idempotency-Key` as the first | `WEB-FR-403` |

---

## 8. Test requirements

The runner is the one the Angular CLI scaffolds for `22.1.5`.

`WEB-NFR-020` **THE frontend SHALL add no testing framework beyond the CLI-scaffolded one** and SHALL
NOT introduce Playwright, Cypress, Selenium or a visual-regression tool (`WEB-NFR-007`).

### 8.1 Mandatory component tests

`WEB-TEST-001` **THE confidence bar SHALL be unit tested** at confidence `0.0000`, exactly
`foshol.analysis.confidence.low`, exactly `foshol.analysis.confidence.high`, one value strictly
between the thresholds, and `1.0000`. Each case asserts: **two** threshold lines exist, each line's
proportional position equals its threshold value, the fill width equals the confidence, and the
numeric confidence and both threshold labels are present as text. *(This is the protected wow factor
and the one place where a rendering bug silently misrepresents the routing.)*

`WEB-TEST-002` **THE quality-gate rejection flow SHALL be unit tested** with a synthetic
below-`capture.minEdgePx` image, a synthetic low-variance (blurred) image, and a passing image. Each
asserts: the rejection prompt renders, the HTTP testing backend recorded **zero** requests, the
prompt text resolves from the Bangla catalogue, and the override control is present for the
vegetation case only.

`WEB-TEST-003` **THE i18n fallback SHALL be unit tested** for (a) a response carrying
`<field>Fallback: true`, asserting the `(bn)` marker and its accessible description render, (b) a key
present in `bn.json` and absent from `en.json`, asserting the Bangla fallback renders, and (c)
catalogue parity — `bn.json` and `en.json` have identical key sets.

### 8.2 Additional unit tests and fixtures

`WEB-TEST-004` **Every signal store SHALL have a unit test for its invariants** — image count cap,
queue order preservation, claim expiry transition, session clearing.

`WEB-TEST-005` **THE SSE reconnect backoff SHALL be unit tested with a fake timer**, asserting the
delay sequence, the cap and the reset.

`WEB-TEST-006` **THE auth interceptor and the role route guards SHALL be unit tested**, asserting
that the header reaches API requests but not presigned object URLs, that no request URL contains the
token, and that each role resolves correctly against each route group.

`WEB-TEST-007` **THE frontend SHALL keep API fixtures in `web/src/testing/fixtures/`**, one JSON file
per response shape, hand-authored to match the OpenAPI schema: a `PRIMARY` case, a `SECONDARY` case
with symptoms and a transcript, an `UNDETERMINED` case carrying `errorCode`, a published advisory, a
revised advisory (`version = 2`), a rejection, a five-row queue page, and an admin stats response.
Fixtures carry no agronomic content beyond placeholder strings (`COMMON-CON-003`).

`WEB-TEST-008` **THE image and audio fixtures SHALL be generated inside the test** — a canvas-drawn
sharp image, the same image convolved to blur, a synthesised tone — rather than committed as
binaries.

`WEB-TEST-009` `[DERIVED]` **THE farmer capture path SHALL be manually verified on at least one
Android Chrome device and at least one iOS Safari device, over the HTTPS origin of
`COMMON-SEC-019`, before the Day 4 feature freeze.** The pass SHALL confirm, per device: the
microphone permission prompt appears; a held recording produces a non-empty clip; the waveform
animates; the negotiated container is accepted by the server with `202` and not `415`; an
interrupted touch stops recording and keeps the clip; the microphone indicator clears afterwards;
and the resulting case reaches the officer queue with a transcript.

*(This is the only requirement in the suite that cannot be discharged by an agent. It needs two
physical phones and a person holding them. It is listed here because F4 — Bangla speech — is a
non-negotiable scoped feature that is used exclusively on mobile, and no automated test in this
budget covers `getUserMedia` on a real device.)*

### 8.3 What is deliberately **not** tested, and why

Stated honestly, because an untested area nobody named is an untested area nobody watches.

| Not tested | Reason |
|---|---|
| End-to-end browser flows (submit → advisory) | The single end-to-end slice test lives in the backend (`00-common` §11); a browser-driven second one costs most of a day and duplicates it. |
| Cross-browser matrix, **except the farmer capture path** | The officer console and admin page are demoed on one Chromium browser and are not cross-browser tested. The **farmer capture path is exempt from this exclusion**: it is used on real phones, so `WEB-FR-137`–`146` MUST be manually verified on one Android Chrome and one iOS Safari device (`WEB-TEST-009`). |
| Real camera and microphone capture (automated) | Not automatable without a device; `getUserMedia` and `MediaRecorder` are stubbed in unit tests. **This remains the largest untested risk in the frontend**, and it is mitigated by the mandatory manual device pass of `WEB-TEST-009` — on two real phones over the HTTPS origin — not by rehearsal on the laptop. |
| Real SSE against a running server | Needs the full stack; covered manually at each integration checkpoint. |
| Visual regression / screenshot diffing | No baseline exists and no time to curate one. |
| Automated accessibility audit | The §4.9 floor is verified by a manual keyboard-and-contrast pass per surface, recorded in the progress note. |
| Responsive rendering at the three widths | Verified manually per `AC-26`; a viewport assertion suite is not worth the day. |
| Performance budgets | `00-common` §10.2 budgets are backend-measured; the frontend is not instrumented. |

---

## 9. Agent execution notes

### 9.1 Preconditions

1. `22.1.5` is resolved (`COMMON-NFR-003`). **Until then, stop.**
2. `docs/openapi/foshol-api.yaml` exists and is frozen.
3. Both confidence thresholds are reachable from an endpoint the `OFFICER` role may call
   (`WEB-NFR-011`) — otherwise raise the blocker before building the confidence bar.

### 9.2 Implementation order

Each step is demoable and unblocks the next.

| Step | Files | Delivers |
|---|---|---|
| 1 | `web/` scaffold, `tailwind.config.js`, `core/app-config.ts`, `assets/i18n/{bn,en}.json` | shell builds; **both catalogues exist before the first component** (`WEB-UX-010`) |
| 2 | `core/api/` — `ng-openapi-gen` run, committed output | typed client |
| 3 | `core/auth/` + `auth/` — `SessionStore`, interceptor, guards, login screens | a session exists |
| 4 | `shared/` — layout shell, language toggle, `(bn)` fallback pipe, severity badge, decision-path badge, **confidence bar** | the wow-factor primitive lands early, with `WEB-TEST-001` |
| 5 | `farmer/` — crop picker, capture + local quality gate, recorder, submit | demo beats 1–3 |
| 6 | `core/sse/` — fetch-based client, backoff, store fan-out | the transport for beat 5 |
| 7 | `farmer/` — case status, history, advisory card | demo beat 5 |
| 8 | `officer/` — queue, case detail, Grad-CAM toggle, symptom chips, remedy editor, four actions, claim lifecycle | demo beats 4 and 8 |
| 9 | `admin/` — stats page | demo beat 6 |
| 10 | responsive pass, accessibility pass, rehearsal at all three widths | plan §8 |

*Steps 5 and 8 are the largest. If a day slips, step 9 is the only one that may be reduced — never
step 4 or step 8.*

### 9.3 Local Definition of Done

In addition to `00-common` §11, the frontend is done when **all** of the following hold.

1. The production build completes with zero errors and zero warnings.
2. The test suite passes, including `WEB-TEST-001`, `WEB-TEST-002` and `WEB-TEST-003`.
3. `bn.json` and `en.json` have identical key sets and no component holds a user-visible literal.
4. `web/src/app/generated/` matches a fresh `ng-openapi-gen` run with no diff (`COMMON-NFR-043`).
5. No `NgModule` beyond the CLI scaffold; no state library; no added runtime dependency.
6. No numeric literal outside `app-config.ts`; every mirrored constant names its server property.
7. Every route renders without horizontal scroll at 360 px, 768 px and 1280 px.
8. Every surface passes a keyboard-only pass with a visible focus indicator throughout.
9. No token, phone number, OTP or object key appears in any URL, log or storage key.
10. Every `[DEFERRED]` requirement in §4.6 and §4.11 is listed as unstarted in
    `docs/progress/a6-frontend.md` (`COMMON-NFR-045`).

---

## 10. Requirement index

| Category | IDs | Count |
|---|---|---|
| Functional | `WEB-FR-001`…`006` · `010`…`013` · `100`…`101` · `110`…`112`, `114`…`115` · `120`…`125` · `130`…`136` · `140`…`141` · `150`…`158`, `160` · `200`…`202`, `204`…`205` · `210`…`217`, `219` · `220`…`224` · `230`…`235` · `240`…`244` · `300`…`305` · `310`…`314` · `350`…`359` · `400`…`404` · `900`…`904` · `910`…`913` | 107 |
| Non-functional | `WEB-NFR-001`…`012`, `WEB-NFR-020` | 13 |
| Data | `WEB-DATA-001`, `002`, `004`…`006`, `020`…`024` | 10 |
| Security | `WEB-SEC-001`…`006` | 6 |
| API | `WEB-API-001`…`005` | 5 |
| UX | `WEB-UX-010`…`017` · `030`…`034` · `040`…`046` | 20 |
| Test | `WEB-TEST-001`…`008` | 8 |
| *of which `[DEFERRED]`* | `WEB-FR-900`…`904`, `910`…`913` | *9* |
| **Total** | | **169** |

`[DERIVED]` items introduced here: **`WEB-NFR-011`** — the confidence thresholds must come from the
API, with a blocker if no officer-visible endpoint exposes them; **`WEB-FR-124`** — a client-side
non-crop heuristic with an override, to satisfy demo beat 3 without a client-side model.
