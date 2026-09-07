# 10 — Identity (EARS)

**Module:** `identity` · **Package:** `com.rootcause.foshol.identity` · **Agent:** A1 (platform + identity)
**Prefix:** `IDENTITY` · **Depends on:** `00-common.ears.md` (FROZEN — read it first)

> This document plus `00-common.ears.md` is sufficient to implement the module. Nothing else is
> needed and nothing else may be assumed. Where this document and `00-common.ears.md` disagree,
> `00-common.ears.md` wins.
> Traces to plan §2.4 (`identity` module row) and to clarification item 1 of the approved plan
> (*"one mechanism for all roles"*).

---

## 1. Scope

### 1.1 What this module owns

| Owned | Detail |
|---|---|
| Tables | `farmer`, `field_officer`, `otp_challenge` (`00-common` §4.2) |
| Farmer authentication | Phone → one-time code → JWT |
| Officer and admin authentication | Seeded username + BCrypt password → JWT |
| Token issuance | HS256 signing, claim set, TTL |
| Token validation | The single servlet filter that authenticates every `/api/v1` request |
| Role model | `FARMER`, `OFFICER`, `ADMIN` (`common.Role`) and the authority mapping |
| Phone confidentiality | AES-256-GCM encryption at rest, SHA-256 hash index |
| Published APIs | `FarmerLookupApi`, `OfficerLookupApi` (`00-common` §6.2) |
| Endpoint | `GET /api/v1/me` |

`identity` is the **only** module that reads or writes `farmer`, `field_officer` and `otp_challenge`,
and the **only** module that mints or verifies a JWT.

### 1.2 What this module does **not** own

| Not owned | Owner |
|---|---|
| Per-resource ownership checks (a farmer reading another farmer's case) | The module that owns the resource — `11-intake.ears.md` (`INTAKE-SEC-004`), `14-review.ears.md` |
| Case submission and the `20 requests per farmer per hour` limit of `COMMON-SEC-015` | `11-intake.ears.md` |
| Officer queue, claims, advisories | `14-review.ears.md` |
| Any notification, including delivery of an OTP over SMS | `15-notification.ears.md` (`SmsChannel` is a disabled stub) |
| Crop, disease, symptom, remedy reference data | `13-knowledge.ears.md` |
| The frontend login screens and the BN/EN toggle | `70-frontend.ears.md` |
| The `district_code` routing rule | Nobody — deferred (`00-common` §1.2); the column exists and is unused |

### 1.3 Events

`identity` **publishes no domain event and consumes no domain event.** It is a synchronous
query-and-authenticate module only. This is deliberate: an identity change in this scope is a
migration, not a runtime fact.

---

## 2. Dependencies

### 2.1 Modules called

**None.** Per the dependency matrix (`00-common` §6.1) the `identity` row permits `common` only.
`identity` imports `com.rootcause.foshol.common` for `Role`, `Uuid7`, `ErrorCodes`, `ConfigKeys` and
`CorrelationId`, and nothing else.

### 2.2 Interfaces this module publishes

Quoted verbatim from `00-common` §6.2 and **frozen** (`COMMON-NFR-041`):

```java
// ── com.rootcause.foshol.identity.api ─────────────────────────────────────────
public interface FarmerLookupApi {
    Optional<FarmerView> findById(UUID farmerId);
    Optional<FarmerView> findByPhone(String e164Phone);
}
public record FarmerView(UUID id, String name, String districtCode, String preferredLanguage) {}

public interface OfficerLookupApi {
    Optional<OfficerView> findById(UUID officerId);
    List<OfficerView> findActiveByDistrict(String districtCode);
}
public record OfficerView(UUID id, String name, String districtCode, String role, boolean active) {}
```

Callers today: `intake` (`FarmerLookupApi`), `review` (both), `notification` (`FarmerLookupApi`).

### 2.3 Configuration consumed

Every property is defined in `00-common` §9 and is read through a constant in `common.ConfigKeys`
(`COMMON-ARCH-010`).

| Property | Purpose |
|---|---|
| `foshol.auth.jwt.issuer` | `iss` claim |
| `foshol.auth.jwt.ttl` | `exp` claim offset |
| `foshol.auth.jwt.secret` | HS256 key |
| `foshol.auth.otp.enabled` | Master switch for the farmer OTP path |
| `foshol.auth.otp.ttl` | `otp_challenge.expires_at` offset |
| `foshol.auth.otp.max-attempts` | Verification attempt ceiling |
| `foshol.auth.otp.length` | Generated code length |
| `foshol.auth.otp.dev-code` | Fixed code under `local` / `demo` / `test` only |
| `foshol.crypto.phone.key` | AES-256-GCM key for `phone_enc` |
| `foshol.i18n.default-locale` | Fallback locale for `COMMON-API-003` |

---

## 3. Domain model

### 3.1 Aggregates

| Aggregate root | Table | Identity | Lifecycle in this scope |
|---|---|---|---|
| `Farmer` | `farmer` | `FarmerId` (UUIDv7) | Created by migration only; read-only at runtime |
| `FieldOfficer` | `field_officer` | `OfficerId` (UUIDv7) | Created by migration only; read-only at runtime |
| `OtpChallenge` | `otp_challenge` | `ChallengeId` (UUIDv7) | Created, attempted, consumed or expired at runtime |

### 3.2 Value objects

| Type | Contents | Notes |
|---|---|---|
| `PhoneNumber` | Normalised E.164 string | Never persisted in plaintext, never logged, never in a URL |
| `PhoneHash` | 64 lowercase hex characters | The only lookup key for a phone number |
| `EncryptedPhone` | `byte[]` — IV ‖ ciphertext ‖ tag | Persisted to `phone_enc` |
| `OtpCode` | Numeric string of `foshol.auth.otp.length` | Held in memory for the length of one request |
| `AuthToken` | Compact JWS string + `Instant expiresAt` | Returned to the caller, never stored |
| `AuthenticatedPrincipal` | `UUID subjectId`, `Role role` | Placed in the Spring Security context |

### 3.3 Enums

`Role` — `FARMER`, `OFFICER`, `ADMIN` — lives in `common.Role` (`COMMON-ARCH-013`), **not** in this
module. `farmer` rows carry no `role` column; the role of a farmer subject is implicitly `FARMER`.
`field_officer.role` is constrained to `OFFICER` or `ADMIN` by `ck_officer_role`.

### 3.4 Invariants

Each invariant is enforced in the aggregate, not only by an annotation (`COMMON-NFR-014`).

| # | Aggregate | Invariant |
|---|---|---|
| INV-1 | `Farmer` | `phoneHash` is unique across all farmers (`farmer.phone_hash UNIQUE`) |
| INV-2 | `Farmer` | `preferredLanguage` ∈ {`bn`, `en`} |
| INV-3 | `FieldOfficer` | `username` is unique and `role` ∈ {`OFFICER`, `ADMIN`} |
| INV-4 | `FieldOfficer` | An officer with `active = false` can never be authenticated |
| INV-5 | `OtpChallenge` | A challenge is verifiable only while `consumed_at IS NULL` **and** `expires_at > now()` **and** `attempts < foshol.auth.otp.max-attempts` |
| INV-6 | `OtpChallenge` | Consumption is irreversible: once `consumed_at` is set it is never cleared |
| INV-7 | `OtpChallenge` | At most one verifiable challenge exists per `phone_hash` at any instant (`IDENTITY-FR-004`) |
| INV-8 | `OtpChallenge` | `attempts` only ever increases, and only on a failed verification |

### 3.5 Specifications

| Specification | Predicate |
|---|---|
| `VerifiableChallengeSpec` | INV-5, evaluated against a supplied `Instant` — never `Instant.now()` inside the domain, so it is unit-testable with a fixed clock |
| `AuthenticableOfficerSpec` | `active = true` **and** `role` ∈ {`OFFICER`, `ADMIN`} |
| `OtpRateLimitSpec` | Count of `otp_challenge` rows for a `phone_hash` created inside the `COMMON-SEC-015` window is below the `COMMON-SEC-015` ceiling |

---

## 4. Requirements

### 4.1 Phone normalisation and confidentiality

`IDENTITY-SEC-001` **THE identity module SHALL normalise every supplied phone number to E.164 form
before it is hashed, encrypted or compared**, rejecting any value that does not normalise to a valid
E.164 string with `400` and code `ERR_PHONE_INVALID`.
*(`[DERIVED]` — `00-common` §4.2 states that `phone_hash` is the SHA-256 of "the E.164 phone number"
but does not say who produces the E.164 form. Without one normalisation point, `01712…`, `+8801712…`
and `8801712…` hash to three different farmers.)*

`IDENTITY-SEC-002` **THE identity module SHALL compute `phone_hash` as the lowercase hexadecimal
SHA-256 of the normalised E.164 string**, and SHALL use it as the sole lookup key for a phone number.
(Implements `COMMON-SEC-013`.)

`IDENTITY-SEC-003` **THE identity module SHALL store `phone_enc` as AES-256-GCM ciphertext of the
normalised E.164 string**, using a fresh random 96-bit initialisation vector per encryption, a 128-bit
authentication tag, and the key from `foshol.crypto.phone.key`; the persisted `bytea` is the
initialisation vector followed by the ciphertext and tag. (Implements `COMMON-SEC-013`.)

`IDENTITY-SEC-004` **IF `foshol.crypto.phone.key` does not Base64-decode to exactly 32 bytes, THEN
THE application SHALL fail to start with error code `ERR_PHONE_KEY_INVALID`.**
*(`[DERIVED]` — AES-256 requires a 256-bit key. A short key silently degrades to a weaker cipher or
throws at first use, i.e. during the demo rather than at boot.)*

`IDENTITY-SEC-005` **THE identity module SHALL NOT return a plaintext phone number in any response
body, header, problem document, log line or metric tag.** (Reinforces `COMMON-SEC-001`.)

### 4.2 Farmer authentication — one-time code

`IDENTITY-FR-001` **WHEN a client posts `POST /api/v1/auth/otp/request` with a normalisable phone
number, THE identity module SHALL respond `202 Accepted` regardless of whether that phone number
resolves to a `farmer` row.**
*(`[DERIVED]` — farmer accounts are seeded and finite, so a distinguishable "unknown phone" response
would let anyone enumerate the demo's farmers. The plan is silent; not leaking is the safe default.)*

`IDENTITY-FR-002` **WHEN the requested phone hash resolves to a `farmer` row, THE identity module
SHALL insert one `otp_challenge` row with `expires_at = now() + foshol.auth.otp.ttl`, `attempts = 0`
and `consumed_at = NULL`.**

`IDENTITY-FR-003` **WHILE `foshol.auth.otp.dev-code` is set, THE identity module SHALL use that value
as the challenge code**; otherwise it SHALL generate a code of `foshol.auth.otp.length` decimal digits
from a cryptographically secure random source. (Clarification item 1.)

`IDENTITY-SEC-006` **THE identity module SHALL store `otp_challenge.code_hash` as the lowercase
hexadecimal SHA-256 of the concatenation of the challenge id, the `phone_hash` and the code, and SHALL
NOT store the code itself.**
*(`[DERIVED]` — `00-common` §4.2 fixes the column as `char(64)` but does not state its derivation.
Including the challenge id makes the digest unique per challenge, so a six-digit code is not a
rainbow-table lookup. The column width is unchanged.)*

`IDENTITY-FR-004` **WHEN a new challenge is created for a phone hash, THE identity module SHALL set
`consumed_at = now()` on every other challenge for that phone hash that is not yet consumed.**
*(Upholds INV-7 so "the current challenge" is never ambiguous.)*

`IDENTITY-SEC-007` **IF a phone hash has already produced the number of `otp_challenge` rows permitted
by `COMMON-SEC-015` inside its window, THEN THE identity module SHALL respond `429` with code
`ERR_OTP_RATE_LIMITED` and a `Retry-After` header**, and SHALL NOT create a further challenge.
*(The count is taken over `otp_challenge (phone_hash, created_at DESC)` using index `ix_otp_phone`; no
additional table and no in-memory state is introduced, so the limit survives a restart.)*

> **Externalisation gap — RESOLVED by A1.** This document originally raised a blocker: `COMMON-SEC-015`
> stated the ceiling and window as literals, which `COMMON-NFR-020` forbids. A1 has since added
> `foshol.auth.otp.rate-limit.max-requests` (`3`) and `foshol.auth.otp.rate-limit.window` (`PT10M`) to
> `00-common` §9.1, and `COMMON-SEC-015` now reads from them. Bind to the properties, not the numbers.

`IDENTITY-UX-001` **THE `POST /api/v1/auth/otp/request` response SHALL carry `expiresInSeconds` and an
`otpDeliveryMode` of `DEV_FIXED` or `SMS`**, so the frontend can render the correct Bangla helper text
without the server disclosing the code.
*(`[DERIVED]` — there is no SMS gateway in scope, so without this the farmer login screen has no
legitimate way to explain where the code comes from. The code itself is never returned.)*

`IDENTITY-FR-005` **WHEN a client posts `POST /api/v1/auth/otp/verify` with a phone number and a code
that matches the verifiable challenge for that phone hash, THE identity module SHALL respond `200`
with a JWT whose `role` claim is `FARMER` and whose `sub` claim is the `farmer.id`.**

`IDENTITY-FR-006` **WHEN a verification succeeds, THE identity module SHALL set `consumed_at = now()`
on that challenge**, so the same code can never be redeemed twice.

`IDENTITY-FR-007` **IF the supplied code does not match the verifiable challenge, THEN THE identity
module SHALL increment `attempts` by one and SHALL respond `401` with code `ERR_OTP_INVALID`.**

`IDENTITY-FR-008` **IF a failed verification raises `attempts` to `foshol.auth.otp.max-attempts`,
THEN THE identity module SHALL set `consumed_at = now()` on that challenge and SHALL respond `401`
with code `ERR_OTP_ATTEMPTS_EXCEEDED`.**

`IDENTITY-FR-009` **IF the only challenge for a phone hash has `expires_at` in the past, THEN THE
identity module SHALL respond `401` with code `ERR_OTP_EXPIRED`.**

`IDENTITY-FR-010` **IF no challenge exists for the supplied phone hash, or every challenge for it is
consumed, THEN THE identity module SHALL respond `401` with code `ERR_OTP_INVALID`.**
*(The same code as `IDENTITY-FR-007`, deliberately: a caller must not be able to tell "no such farmer"
from "wrong code".)*

`IDENTITY-NFR-001` **THE identity module SHALL compare `code_hash` values with a constant-time
comparison**, so that verification time does not reveal how many leading digits were correct.

`IDENTITY-FR-011` **WHERE `foshol.auth.otp.enabled` is `false`, THE identity module SHALL respond
`503` with code `ERR_OTP_DISABLED` to both OTP endpoints**, and SHALL leave officer login unaffected.

### 4.3 Officer and admin authentication — password

`IDENTITY-FR-012` **WHEN a client posts `POST /api/v1/auth/officer/login` with a username and password
that match an active `field_officer` row, THE identity module SHALL respond `200` with a JWT whose
`sub` claim is the `field_officer.id` and whose `role` claim is that row's `role`.**

`IDENTITY-SEC-008` **THE identity module SHALL verify passwords with BCrypt against
`field_officer.password_hash` at the Spring Security `BCryptPasswordEncoder` default strength, and
SHALL NOT impose a password-complexity, rotation or reuse policy.** (Clarification item 1 — there is
no self-service password change in scope, so a policy would be unenforceable theatre.)

`IDENTITY-FR-013` **IF the supplied username matches no `field_officer` row, THEN THE identity module
SHALL still perform one BCrypt comparison against a fixed non-matching hash before responding `401`
with code `ERR_INVALID_CREDENTIALS`.** *(Equal response time for unknown and known usernames.)*

`IDENTITY-FR-014` **IF the supplied password does not match, THEN THE identity module SHALL respond
`401` with code `ERR_INVALID_CREDENTIALS`.**

`IDENTITY-FR-015` **IF the matched `field_officer` row has `active = false`, THEN THE identity module
SHALL respond `401` with code `ERR_ACCOUNT_INACTIVE` and SHALL NOT issue a token.** (INV-4.)

### 4.4 Token issuance and validation

`IDENTITY-SEC-009` **THE identity module SHALL issue a compact JWS signed with HS256 carrying exactly
the claims `iss` (`foshol.auth.jwt.issuer`), `sub` (the subject id as a string), `role`, `iat` and
`exp` (`iat + foshol.auth.jwt.ttl`).** (Implements `COMMON-SEC-012`. No other claim is added; a claim
an agent adds today is a claim another module will read tomorrow.)

`IDENTITY-SEC-010` **IF `foshol.auth.jwt.secret` is shorter than 32 bytes, THEN THE application SHALL
fail to start with error code `ERR_JWT_SECRET_TOO_SHORT`.**
*(`[DERIVED]` — HS256 with a key shorter than its 256-bit output is a downgrade the JWT library will
not refuse. Failing at boot is the only place this is cheap to notice.)*

`IDENTITY-SEC-011` **THE identity module SHALL provide the single Spring Security filter chain for the
application, SHALL permit `/api/v1/auth/**`, `/actuator/health` and the springdoc endpoints without
authentication, and SHALL require an authenticated principal for every other `/api/v1` path.**
(Implements `COMMON-SEC-010`.)

`IDENTITY-FR-016` **WHEN a request carries `Authorization: Bearer <token>`, THE identity module SHALL
verify the signature, the `iss` claim and the `exp` claim before the request reaches any controller.**

`IDENTITY-FR-017` **WHEN a token is verified, THE identity module SHALL populate the Spring Security
`Authentication` with the `sub` claim as the principal name and a single granted authority
`ROLE_<role>`.**
*(`[DERIVED]` — `00-common` §6.2 publishes no "who is calling" interface, yet `intake` and `review`
both need the caller's id. Standing the caller up in the Spring Security context is how they obtain it
without importing `identity` internals, which `COMMON-ARCH-002` forbids. Every other module reads it
via `@AuthenticationPrincipal` or `SecurityContextHolder`.)*

`IDENTITY-FR-018` **IF the `Authorization` header is absent, malformed, or carries a token whose
signature or issuer does not verify, THEN THE identity module SHALL respond `401` as an RFC 9457
problem document with code `ERR_TOKEN_INVALID`.** (`COMMON-API-002`.)

`IDENTITY-FR-019` **IF a token's `exp` claim is in the past, THEN THE identity module SHALL respond
`401` with code `ERR_TOKEN_EXPIRED`.**

`IDENTITY-SEC-012` **IF an authenticated principal lacks the role required by an endpoint, THEN THE
identity module SHALL respond `403` with code `ERR_FORBIDDEN`.** Ownership failures are **not** handled
here — the owning module returns `404` per `COMMON-API-001`.

`IDENTITY-NFR-002` **THE identity module SHALL configure the filter chain as
`SessionCreationPolicy.STATELESS` with CSRF disabled and CORS restricted per `COMMON-SEC-017`**, and
SHALL create no `HttpSession` on any path.

`IDENTITY-SEC-013` **THE identity module SHALL issue no refresh token and SHALL maintain no
revocation list**; a compromised token is valid until `exp`. `[DEFERRED]` — token revocation, a
`jti` denylist and refresh-token rotation. *Seam: the claim set of `IDENTITY-SEC-009` is the extension
point; adding `jti` is one claim and one lookup.*

### 4.5 Startup guards

`IDENTITY-SEC-014` **IF `foshol.auth.otp.dev-code` is set while the active profile is neither `local`,
`demo` nor `test`, THEN THE application SHALL fail to start with error code
`ERR_DEV_OTP_IN_NON_DEV_PROFILE`.** (Implements `COMMON-SEC-003`. The check is an
`ApplicationListener<ApplicationEnvironmentPreparedEvent>`-equivalent guard owned by this module, and
it runs before any bean that could serve a request.)

`IDENTITY-NFR-003` **THE identity module SHALL evaluate `IDENTITY-SEC-004`, `IDENTITY-SEC-010` and
`IDENTITY-SEC-014` at startup and SHALL report every failure it finds, not only the first**, so a
misconfigured demo machine is fixed in one pass.

### 4.6 Account provisioning

`IDENTITY-DATA-001` **THE `farmer` and `field_officer` tables SHALL be populated exclusively by Flyway
migration, and THE identity module SHALL perform no `INSERT`, `UPDATE` or `DELETE` against either table
at runtime.** Demo personas are seeded by `V100__seed_demo_identities.sql` under the `demo` profile
(`COMMON-DATA-005`); integration tests insert their own fixtures directly.

`IDENTITY-DATA-002` **THE seeded farmer and officer personas SHALL be fictional**, and are exempt from
`COMMON-CON-003` because they are not agronomic content.

`IDENTITY-DATA-003` **THE seeded `field_officer.password_hash` values SHALL be BCrypt hashes of a
demo-only password recorded in `README.md`.** The `demo` profile is never deployed outside the demo
machine, so this is not a secret within the meaning of `COMMON-SEC-002`; no `local` or `test` profile
seeds an officer password.

`IDENTITY-FR-020` `[DEFERRED]` **THE system SHALL NOT expose farmer self-registration, officer
self-registration, password change, password reset or account deactivation endpoints.** Every account
in scope is seeded by migration.
*Seam: the aggregates, the phone-hash uniqueness invariant and the BCrypt encoder are all present, so
self-registration is one command handler and one endpoint per role. It is out of scope because the
demo has a fixed cast and account creation carries a verification burden (`IDENTITY-FR-001`'s
non-enumeration property) that four days does not buy.*

### 4.7 Published API behaviour

`IDENTITY-API-001` **THE identity module SHALL implement `FarmerLookupApi.findById` to return a
`FarmerView` for an existing `farmer` row and `Optional.empty()` otherwise.**

`IDENTITY-API-002` **THE identity module SHALL implement `FarmerLookupApi.findByPhone` by normalising
and hashing the supplied number per `IDENTITY-SEC-001` and `IDENTITY-SEC-002` and looking up
`farmer.phone_hash`**, and SHALL NOT decrypt `phone_enc` to perform the comparison.

`IDENTITY-API-003` **THE identity module SHALL implement `OfficerLookupApi.findById` to return an
`OfficerView` for an existing `field_officer` row, including rows with `active = false`**, so that a
historical advisory can still render its author's name.

`IDENTITY-API-004` **THE identity module SHALL implement `OfficerLookupApi.findActiveByDistrict` to
return every `field_officer` row with the given `district_code` and `active = true`, ordered by
`name`.** `[DEFERRED]` — no caller invokes it in this scope; it is the district-routing seam recorded
in `00-common` §1.2 and it is implemented and unit-tested so the seam is real.

`IDENTITY-API-005` **THE identity module SHALL NOT include a phone number in `FarmerView` or
`OfficerView`.** (The frozen records carry none; this requirement exists so no agent proposes adding
one.)

---

## 5. API surface

Base path `/api/v1`. Every error body is RFC 9457 (`COMMON-API-002`), localised per
`COMMON-API-003`. Every response carries `X-Correlation-Id` (`COMMON-NFR-016`).

### 5.1 `POST /api/v1/auth/otp/request`

| | |
|---|---|
| Auth | None (`COMMON-SEC-010`) |
| Request | `{ "phone": "+8801XXXXXXXXX" }` — `@NotBlank` |
| Success | `202` · `{ "expiresInSeconds": 300, "otpDeliveryMode": "DEV_FIXED" }` |
| `400` | `ERR_PHONE_INVALID` — not normalisable to E.164 (`IDENTITY-SEC-001`) |
| `429` | `ERR_OTP_RATE_LIMITED` + `Retry-After` (`IDENTITY-SEC-007`) |
| `503` | `ERR_OTP_DISABLED` (`IDENTITY-FR-011`) |

The response is identical for a known and an unknown phone number (`IDENTITY-FR-001`).

### 5.2 `POST /api/v1/auth/otp/verify`

| | |
|---|---|
| Auth | None |
| Request | `{ "phone": "+8801XXXXXXXXX", "code": "123456" }` — both `@NotBlank`, `code` `@Pattern` digits |
| Success | `200` · `{ "token": "<jws>", "expiresAt": "2026-09-07T18:00:00Z", "role": "FARMER", "subjectId": "018f…" }` |
| `400` | `ERR_PHONE_INVALID` |
| `401` | `ERR_OTP_INVALID` · `ERR_OTP_EXPIRED` · `ERR_OTP_ATTEMPTS_EXCEEDED` |
| `503` | `ERR_OTP_DISABLED` |

### 5.3 `POST /api/v1/auth/officer/login`

| | |
|---|---|
| Auth | None |
| Request | `{ "username": "…", "password": "…" }` — both `@NotBlank` |
| Success | `200` · same body shape as §5.2, `role` ∈ {`OFFICER`, `ADMIN`} |
| `401` | `ERR_INVALID_CREDENTIALS` · `ERR_ACCOUNT_INACTIVE` |

### 5.4 `GET /api/v1/me`

| | |
|---|---|
| Auth | Any authenticated role |
| Success | `200` |
| `401` | `ERR_TOKEN_INVALID` · `ERR_TOKEN_EXPIRED` |
| `404` | `ERR_SUBJECT_NOT_FOUND` — a valid token whose `sub` no longer resolves |

```json
{ "id": "018f…", "role": "FARMER", "name": "…", "districtCode": "DHA",
  "preferredLanguage": "bn", "username": null }
```

`username` is populated for `OFFICER` and `ADMIN` and null for `FARMER`; `preferredLanguage` is
populated for `FARMER` and null otherwise. No phone number appears (`IDENTITY-SEC-005`).

`IDENTITY-API-006` **THE `GET /api/v1/me` response SHALL be derived from the database row identified
by the token's `sub` claim, not from the token itself**, so a seeded change to a name or district is
visible without re-authentication.

---

## 6. Persistence

### 6.1 Tables owned

`farmer`, `field_officer`, `otp_challenge` — defined in `00-common` §4.2 and **never redefined here**.
Indexes: `ix_otp_phone` (`00-common` §4.9).

`IDENTITY-DATA-004` **THE identity module SHALL map each table to a JPA entity in
`infrastructure` distinct from its domain aggregate** (`COMMON-NFR-010`), with a hand-written static
mapper and no MapStruct.

`IDENTITY-DATA-005` **THE identity module SHALL treat `farmer.phone_enc` and
`field_officer.phone_enc` as write-never columns at runtime** and SHALL read them only where a
plaintext phone is genuinely required. No code path in this scope requires one.

### 6.2 Query patterns

| Query | Access path | Index |
|---|---|---|
| Farmer by phone | `SELECT … FROM farmer WHERE phone_hash = ?` | `farmer.phone_hash UNIQUE` |
| Farmer by id | primary key | PK |
| Officer by username | `SELECT … FROM field_officer WHERE username = ?` | `field_officer.username UNIQUE` |
| Active officers by district | `WHERE district_code = ? AND active` | sequential scan — the table holds single digits of rows |
| Verifiable challenge | `WHERE phone_hash = ? AND consumed_at IS NULL ORDER BY created_at DESC LIMIT 1` | `ix_otp_phone` |
| Rate-limit count | `WHERE phone_hash = ? AND created_at > ?` | `ix_otp_phone` |

`IDENTITY-DATA-006` **THE identity module SHALL execute every read on the query side through the
`@ReadOnlyDataSource`-qualified `DataSource` and SHALL NOT load an aggregate in a query handler**
(`COMMON-ARCH-007`).

`IDENTITY-DATA-007` **WHEN a challenge is created, THE identity module SHALL perform the supersede of
`IDENTITY-FR-004` and the insert in one transaction**, so INV-7 cannot be broken by two concurrent
requests.

---

## 7. Acceptance criteria

One scenario per requirement, each mapping onto exactly one test method.

| Requirement | Given | When | Then |
|---|---|---|---|
| `IDENTITY-SEC-001` | the phone `01712345678` | it is normalised | the result is `+8801712345678` and hashes identically to `+8801712345678` |
| `IDENTITY-SEC-002` | a known E.164 number | `phone_hash` is computed twice | both results are the same 64 lowercase hex characters |
| `IDENTITY-SEC-003` | a phone number and a 32-byte key | it is encrypted twice | the two `phone_enc` values differ, and both decrypt to the original |
| `IDENTITY-SEC-004` | `foshol.crypto.phone.key` decoding to 16 bytes | the context starts | startup fails with `ERR_PHONE_KEY_INVALID` |
| `IDENTITY-SEC-005` | any endpoint in §5 | it responds | no field, header or log line contains a phone number |
| `IDENTITY-FR-001` | a phone with no `farmer` row | `POST /auth/otp/request` | `202`, and no `otp_challenge` row exists |
| `IDENTITY-FR-002` | a seeded farmer | `POST /auth/otp/request` | one challenge exists with `expires_at ≈ now + foshol.auth.otp.ttl` |
| `IDENTITY-FR-003` | `foshol.auth.otp.dev-code=000000` under `test` | a challenge is created | verifying with `000000` succeeds |
| `IDENTITY-SEC-006` | a challenge with a known code | the row is read | `code_hash` is neither the code nor the SHA-256 of the code alone |
| `IDENTITY-FR-004` | one verifiable challenge | a second request for the same phone | the first challenge has `consumed_at` set and no longer verifies |
| `IDENTITY-SEC-007` | the `COMMON-SEC-015` ceiling already reached | one more request | `429`, `ERR_OTP_RATE_LIMITED`, `Retry-After` present, no new row |
| `IDENTITY-UX-001` | `foshol.auth.otp.dev-code` set | `POST /auth/otp/request` | `otpDeliveryMode = DEV_FIXED` and the body contains no code |
| `IDENTITY-FR-005` | a verifiable challenge | verify with the correct code | `200`, token `role = FARMER`, `sub` = the farmer id |
| `IDENTITY-FR-006` | a successful verification | the same code is submitted again | `401` `ERR_OTP_INVALID` |
| `IDENTITY-FR-007` | a verifiable challenge | verify with a wrong code | `401` `ERR_OTP_INVALID` and `attempts = 1` |
| `IDENTITY-FR-008` | `attempts = max-attempts − 1` | one more wrong code | `401` `ERR_OTP_ATTEMPTS_EXCEEDED` and `consumed_at` set |
| `IDENTITY-FR-009` | a challenge whose `expires_at` has passed | verify with the correct code | `401` `ERR_OTP_EXPIRED` |
| `IDENTITY-FR-010` | no challenge for the phone | verify | `401` `ERR_OTP_INVALID` |
| `IDENTITY-NFR-001` | two wrong codes differing in the first and last digit | both are verified | the comparison implementation is the constant-time helper (asserted by unit test on the helper) |
| `IDENTITY-FR-011` | `foshol.auth.otp.enabled=false` | either OTP endpoint | `503` `ERR_OTP_DISABLED`; officer login still returns `200` |
| `IDENTITY-FR-012` | an active officer and correct password | officer login | `200` with `role` equal to the row's `role` |
| `IDENTITY-SEC-008` | a seeded BCrypt hash | the encoder verifies it | verification succeeds and no policy rejects the password |
| `IDENTITY-FR-013` | an unknown username | officer login | `401` `ERR_INVALID_CREDENTIALS` and a BCrypt comparison was performed |
| `IDENTITY-FR-014` | a known username, wrong password | officer login | `401` `ERR_INVALID_CREDENTIALS` |
| `IDENTITY-FR-015` | an officer with `active = false` and the correct password | officer login | `401` `ERR_ACCOUNT_INACTIVE`, no token issued |
| `IDENTITY-SEC-009` | an issued token | its claims are decoded | exactly `iss`, `sub`, `role`, `iat`, `exp` are present and `exp − iat = foshol.auth.jwt.ttl` |
| `IDENTITY-SEC-010` | a 16-byte JWT secret | the context starts | startup fails with `ERR_JWT_SECRET_TOO_SHORT` |
| `IDENTITY-SEC-011` | no token | `GET /api/v1/me` and `GET /actuator/health` | `401` for the first, `200` for the second |
| `IDENTITY-FR-016` | a token signed with a different secret | any protected endpoint | `401` `ERR_TOKEN_INVALID` |
| `IDENTITY-FR-017` | a valid `OFFICER` token | a protected endpoint | the principal name is the `sub` and the authority is `ROLE_OFFICER` |
| `IDENTITY-FR-018` | no `Authorization` header | a protected endpoint | `401` with `application/problem+json` and `ERR_TOKEN_INVALID` |
| `IDENTITY-FR-019` | a token whose `exp` has passed | a protected endpoint | `401` `ERR_TOKEN_EXPIRED` |
| `IDENTITY-SEC-012` | a `FARMER` token | an `ADMIN`-only endpoint | `403` `ERR_FORBIDDEN` |
| `IDENTITY-NFR-002` | any authenticated request | it completes | no `Set-Cookie` header and no `HttpSession` was created |
| `IDENTITY-SEC-013` | a valid unexpired token | it is presented after logout | it still authenticates (documented behaviour, not a defect) |
| `IDENTITY-SEC-014` | `foshol.auth.otp.dev-code` set with profile `prod` | the context starts | startup fails with `ERR_DEV_OTP_IN_NON_DEV_PROFILE` |
| `IDENTITY-NFR-003` | a short JWT secret **and** a bad phone key | the context starts | the failure message names both problems |
| `IDENTITY-DATA-001` | the running application | any identity code path executes | no SQL write is issued against `farmer` or `field_officer` |
| `IDENTITY-API-001` | a seeded farmer id | `findById` | a `FarmerView` with the row's name, district and language |
| `IDENTITY-API-002` | a seeded farmer's phone in national form | `findByPhone` | the same `FarmerView` as `findById` |
| `IDENTITY-API-003` | an inactive officer id | `findById` | an `OfficerView` with `active = false` |
| `IDENTITY-API-004` | two active and one inactive officer in one district | `findActiveByDistrict` | two views, ordered by name |
| `IDENTITY-API-005` | any `FarmerView` or `OfficerView` | it is serialised | no phone field exists |
| `IDENTITY-API-006` | a valid farmer token and a changed `farmer.name` | `GET /api/v1/me` | the response carries the new name |

---

## 8. Test requirements

Honouring the test floor of `00-common` §11 and clarification item 21.

### 8.1 Unit tests (no Spring context)

| Target | Must assert |
|---|---|
| `PhoneNumber` normalisation | INV-1 inputs above, plus rejection of non-normalisable strings |
| `PhoneCipher` | round-trip, distinct IVs, tag failure on tampered ciphertext, `IDENTITY-SEC-004` |
| `OtpChallenge` aggregate | INV-5 … INV-8 against a fixed `Clock` — expiry, consumption, attempt ceiling |
| `VerifiableChallengeSpec`, `AuthenticableOfficerSpec`, `OtpRateLimitSpec` | true and false branch each |
| `JwtIssuer` / `JwtVerifier` | `IDENTITY-SEC-009`, wrong secret, wrong issuer, expired token |
| **Every** command handler | `RequestOtpCommandHandler`, `VerifyOtpCommandHandler`, `OfficerLoginCommandHandler` |
| **Every** query handler | `CurrentSubjectQueryHandler`, `FarmerLookupQueryHandler`, `OfficerLookupQueryHandler` |
| Startup guards | `IDENTITY-SEC-004`, `IDENTITY-SEC-010`, `IDENTITY-SEC-014`, `IDENTITY-NFR-003` |

### 8.2 Integration test — exactly one (`00-common` §11.3)

`IdentityIntegrationTest` on Testcontainers PostgreSQL, profile `test`:

> Insert one farmer and one active officer → `POST /auth/otp/request` → assert one `otp_challenge`
> row → `POST /auth/otp/verify` with `foshol.auth.otp.dev-code` (`000000` under `test`) → assert `200`
> and a `FARMER` token → call `GET /api/v1/me` with it → assert `200` and the seeded name →
> `POST /auth/officer/login` → assert an `OFFICER` token → call `GET /api/v1/me` with it →
> assert `username` is populated.

No per-endpoint integration tests. Every negative path in §7 is a unit or `@WebMvcTest` slice test.

### 8.3 Fixtures

| Fixture | Contents |
|---|---|
| `IdentityFixtures.farmer()` | Fictional farmer, fixed phone `+8801700000001`, district `DHA`, `bn` |
| `IdentityFixtures.officer()` | Fictional officer, `role = OFFICER`, `active = true` |
| `IdentityFixtures.admin()` | Fictional admin, `role = ADMIN` |
| `IdentityFixtures.inactiveOfficer()` | `active = false` |
| `FixedClock` | `2026-01-01T00:00:00Z`, so expiry assertions are exact |
| Test keys | 32-byte Base64 phone key and 32-byte JWT secret in `application-test.properties` overrides |

---

## 9. Agent execution notes

### 9.1 Implementation order

1. `common` additions land first (A1 owns them): `Role`, `ErrorCodes` entries listed in §5,
   `ConfigKeys` entries listed in §2.3. **Nothing else starts until these compile.**
2. `identity/domain/` — `Farmer`, `FieldOfficer`, `OtpChallenge`, the value objects of §3.2, the three
   specifications, the domain exceptions. No Spring, no JPA (`COMMON-ARCH-004`). Unit tests alongside.
3. `identity/application/port/` — `PhoneCipherPort`, `OtpCodeGeneratorPort`, `TokenIssuerPort`.
4. `identity/application/command/` — the three command handlers of §8.1, with unit tests.
5. `identity/application/query/` — the three query handlers, with unit tests.
6. `identity/infrastructure/` — JPA entities `FarmerEntity`, `FieldOfficerEntity`,
   `OtpChallengeEntity`, their repositories, hand-written mappers, `AesGcmPhoneCipher`,
   `Auth0JwtAdapter`, and the startup guards of §4.5.
7. `identity/api/` implementations — `FarmerLookupService`, `OfficerLookupService`. **The `api`
   package interfaces themselves are frozen Day-0 artefacts; implement, never edit
   (`COMMON-NFR-041`).**
8. `identity/web/` — `AuthController`, `MeController`, request/response records, `SecurityConfig`,
   `JwtAuthenticationFilter`, the RFC 9457 entry points for `401` and `403`.
9. `IdentityIntegrationTest` last.
10. Update `docs/openapi/foshol-api.yaml` for the four endpoints of §5 — A1 owns that file and
    `identity` is an A1 module, so this is in-scope for the same agent.

### 9.2 Ordering constraint for other agents

`intake`, `review` and `notification` cannot authenticate a request until step 8 lands.
**Land steps 1–8 before the Day-1 checkpoint** (`00-common` §12.2); every other workstream's
integration test depends on a working token.

### 9.3 Local Definition of Done

All ten clauses of `00-common` §11, plus:

1. `IDENTITY-FR-001` … `IDENTITY-FR-019`, every `SEC`, `API`, `DATA`, `NFR` and `UX` requirement above
   is implemented, or listed as `[DEFERRED]` in `docs/progress/a1.md` (`COMMON-NFR-045`).
2. `IDENTITY-FR-020` and `IDENTITY-SEC-013` remain `[DEFERRED]` — the seams stay visible, the code
   does not appear.
3. A `grep` of the module's sources and test resources finds no plaintext phone number outside
   `IdentityFixtures` and no OTP code outside the test properties.
4. Starting the application with a short JWT secret, a bad phone key, or `dev-code` under a
   non-development profile fails at boot with the stated error code — verified by hand once and by
   test always.
5. The four endpoints of §5 match `docs/openapi/foshol-api.yaml` in path, method, status codes and
   schema.

### 9.4 Requirement index

| Category | IDs | Count |
|---|---|---|
| `FR` | `IDENTITY-FR-001` … `IDENTITY-FR-020` | 20 |
| `SEC` | `IDENTITY-SEC-001` … `IDENTITY-SEC-014` | 14 |
| `API` | `IDENTITY-API-001` … `IDENTITY-API-006` | 6 |
| `DATA` | `IDENTITY-DATA-001` … `IDENTITY-DATA-007` | 7 |
| `NFR` | `IDENTITY-NFR-001` … `IDENTITY-NFR-003` | 3 |
| `UX` | `IDENTITY-UX-001` | 1 |
| **Total** | | **51** |
