# ADR-0005: One JWT mechanism for all roles, with a fixed development OTP

- **Status:** Accepted
- **Date:** 2026-09-07
- **Deciders:** project lead (human), requirements agent
- **Supersedes / relates to:** plan clarification 1; relates to ADR-0015; enforced by `COMMON-SEC-002`, `COMMON-SEC-003`, `COMMON-SEC-010` … `COMMON-SEC-015`

## Context

Three roles use the system: `FARMER`, `OFFICER` and `ADMIN` (`common.Role`). Farmers are identified by
phone number — they have no email and will not manage a password. Officers and admins are staff with
credentials. The obvious design is two mechanisms: OTP for farmers, password for staff.

Two mechanisms means two filter chains, two token shapes, two sets of test fixtures, and an open
question for every agent that touches an endpoint about which one applies. Where seven agents work in
parallel against frozen contracts, an ambiguity like that is the kind of thing two agents resolve
differently and nobody notices until integration.

There is also a delivery problem. Real OTP delivery needs an SMS gateway: an account, a sender id
registered with the operator, credentials in the environment, a per-message cost, and a network
dependency in the middle of a live demo. SMS as a *channel* is explicitly out of scope
(`00-common.ears.md` §1.2); making it a hard dependency of logging in would be incoherent.

## Decision

**One mechanism: a stateless JWT for every role.** `COMMON-SEC-012` — HS256, signed with
`foshol.auth.jwt.secret`, carrying `sub`, `role` and `exp`, valid for `foshol.auth.jwt.ttl` (`PT8H`).
No refresh token, no server-side session. One filter chain, one principal shape, one test fixture.

**Two credential paths converge on the same token.** A farmer authenticates phone → OTP challenge →
code; an officer or admin authenticates with a seeded username and BCrypt password. Both are issued
the same JWT with the appropriate role claim. Staff accounts are seeded by migration; there is no
registration endpoint and no password policy, because there is no self-service account creation in
scope (clarification 19).

**OTP verification uses a fixed development code.** `foshol.auth.otp.dev-code` is `123456` under
`local` and `demo`, `000000` under `test` (`00-common.ears.md` §9.2). The challenge machinery is real
regardless: `otp_challenge` rows carry `code_hash`, `attempts`, `expires_at` and `consumed_at`;
`foshol.auth.otp.ttl=PT5M` and `foshol.auth.otp.max-attempts=5` are enforced; the request endpoint is
rate-limited to 3 per phone hash per 10 minutes (`COMMON-SEC-015`). Only *delivery* is stubbed — the
demo exercises the same code path a real deployment would run, with the code logged rather than sent.

**The fail-fast guard is the load-bearing part of this decision.** `COMMON-SEC-003`: if
`foshol.auth.otp.dev-code` is set while the active profile is neither `local`, `demo` nor `test`, the
application fails to start with `ERR_DEV_OTP_IN_NON_DEV_PROFILE`. A fixed OTP is a total
authentication bypass for anyone who knows a phone number. The mitigation is not a comment in the
code; it is that the application refuses to boot. A misconfigured deployment becomes a startup
failure, which is loud, rather than an open door, which is silent.

Phone numbers are never stored in plaintext: `phone_hash` (hex SHA-256 of the E.164 number) is the
only lookup key, `phone_enc` is AES-256-GCM ciphertext under `foshol.crypto.phone.key`
(`COMMON-SEC-013`), and `COMMON-SEC-001` forbids logging the number, the OTP or the JWT at any level
in any profile. No secret value lives in the repository (`COMMON-SEC-002`).

## Consequences

### Positive

- One filter chain, one token, one fixture. An agent writing an endpoint has no auth decision to make.
- The demo cannot fail on an SMS gateway, an operator outage or a roaming handset.
- Stateless tokens mean no session store and no session-expiry bug at minute fifty-nine of a
  sixty-minute demo.
- The OTP domain model is complete, so replacing the stub is additive rather than a rewrite.
- `COMMON-SEC-003` converts the most dangerous configuration in the system into a boot failure.

### Negative / accepted cost

- **Anyone who can reach a `local` or `demo` instance and knows a seeded farmer's phone number is that
  farmer.** This is a total bypass and we state it plainly. It is acceptable only because the guard
  makes it impossible outside those three profiles, and because the target is one laptop.
- No refresh token, so a session ends hard at eight hours with no silent renewal. Eight hours
  comfortably exceeds any demo or field session.
- No revocation: a leaked JWT is valid until `exp`, and rotating the secret invalidates every token at
  once.
- HS256 means the signing key is also the verification key. Fine for one deployable; it would have to
  become RS256 the moment a second service verifies tokens.
- The OTP rate limit is per phone hash, so it does not by itself stop enumeration of phone numbers.
  `COMMON-API-001`'s 404-not-403 rule limits what enumeration reveals; it does not prevent the attempt.

## Alternatives considered

**Two mechanisms — OTP for farmers, form login for staff.** Rejected: two filter chains and two
fixtures for no functional gain, on a build whose principal risk is agents diverging.

**A real SMS gateway for OTP.** Rejected: an account, a registered sender id, per-message cost and a
live network dependency during the demo, in service of a channel that is out of scope.

**Randomly generated OTP printed to the log.** Rejected in favour of a fixed code. It is marginally
more realistic and materially worse to demonstrate — the presenter reads a code off a log tail on a
projector — and the fixed code is what makes the end-to-end slice test deterministic (ADR-0015).

**OAuth2 / OIDC via an external identity provider.** Rejected: another container, another redirect
flow, and a login screen we do not control, for three roles and no federation requirement.

## Revisit when

Revisit when the system is deployed anywhere a person outside the project team can reach it — not "at
production", but the first non-`local`, non-`demo` run. Three things change together at that point: an
`SmsOtpChannel` replaces the logging stub, `foshol.auth.otp.dev-code` leaves the active profile
(`COMMON-SEC-003` will otherwise stop the boot, which is the intended behaviour), and HS256 is
reconsidered against RS256 if more than one process verifies the token.
