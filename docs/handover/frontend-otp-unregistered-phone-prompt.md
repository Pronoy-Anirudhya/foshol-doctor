# Prompt for the frontend agent — farmer OTP request 404

Copy everything below the line into a chat with the Angular frontend agent.

---

You are working on the Foshol Doctor Angular farmer app (pin 22.1.5, signals only, ngx-translate, default locale `bn`). Do not invent endpoints. Regenerate the HTTP client from the backend OpenAPI with `ng-openapi-gen` (`WEB-NFR-005`); do not hand-edit generated files.

## Problem

Farmer login currently treats `POST /api/v1/auth/otp/request` as success whenever it is not a 4xx/5xx the UI already handles. The backend used to return **202** for every well-formed phone, including numbers that are not a registered farmer. The UI then opened the OTP step. Submitting any code called `POST /api/v1/auth/otp/verify`, which returned **401** `ERR_OTP_INVALID` (no challenge exists). That looked like “Unauthorized” after the user had already left the phone screen.

## Backend contract (already shipped)

`POST /api/v1/auth/otp/request` — no bearer, body `{ "phone": "<E.164>" }`.

| Status | Code | What you must do |
|---|---|---|
| **202** | — | Phone is a registered farmer. A challenge was created. Proceed to the OTP code step. Use `expiresInSeconds` and `otpDeliveryMode` (`DEV_FIXED` or `SMS`) for helper text. Never display the code. |
| **404** | `ERR_FARMER_NOT_FOUND` | Phone is **not** a registered farmer. **Stay on the phone step.** Do **not** navigate to OTP. Show RFC 9457 `detail` (and `title` if you already do for other problems). Do not echo the submitted phone (`WEB-SEC-006` last-four only after auth). |
| **400** | `ERR_PHONE_INVALID` | Stay on the phone step; show `detail`. |
| **429** | `ERR_OTP_RATE_LIMITED` | Stay on the phone step; disable the request control; show wait from `Retry-After` (`WEB-FR-011`). |
| **503** | `ERR_OTP_DISABLED` | Stay on the phone step; show `detail`. |

Problem body shape: `application/problem+json` with `code`, `detail`, `title`, `status`, `correlationId`. Show `detail` to the user; keep `correlationId` copyable if that pattern already exists.

`POST /api/v1/auth/otp/verify` is **unchanged**. **401** still means invalid / expired / too many attempts (`ERR_OTP_INVALID`, `ERR_OTP_EXPIRED`, `ERR_OTP_ATTEMPTS_EXCEEDED`). Do not use verify to discover whether the number is registered.

## Requirements

`WEB-FR-010`: collect phone, request OTP, then collect the `foshol.auth.otp.length`-digit code. **When request returns 202, go to the code step. When it returns 404 `ERR_FARMER_NOT_FOUND`, remain on the phone step and display the problem `detail`.**

`WEB-FR-013`: **401** clears session and returns to that surface’s login. **Do not treat 404 on otp/request as 401.** A 404 here is “this number is not a farmer”, not “the session is invalid”. Applying FR-013 to 404 would bounce or clear state incorrectly.

Do not duplicate backend rules (`WEB-NFR-001`): the UI must not guess whether a number is registered except by this response.

## Implementation notes

1. Pull / copy the updated `docs/openapi/foshol-api.yaml` from the backend repo (otp/request now documents **404** `NotFound`). Regenerate the client so `requestOtp` is typed for 404.
2. In the farmer login phone step, only advance to OTP on **202**.
3. On **404** `ERR_FARMER_NOT_FOUND`, keep the phone field, show `detail`, leave submit enabled (user can correct the number).
4. Catalogue keys in both `bn.json` and `en.json` (`WEB-UX-017`) if you add a fallback string; prefer server `detail` as the primary message.
5. Do not put the phone, OTP, or token in the URL (`WEB-SEC-002`).

## Done when

- Unregistered number: request OTP → remains on phone screen with a not-found message; OTP screen never opens.
- Seeded / registered farmer: 202 → OTP screen as today → verify still works.
- Malformed phone still 400 on the phone step; 429 still honours `Retry-After`.
- A 401 from **verify** still uses the existing OTP-error / unauthorized path, not the new 404 copy.
