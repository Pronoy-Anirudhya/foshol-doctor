# Prompt for the frontend agent — adopt bilingual catalogue API fields

Copy everything below the line into a chat with the expert Angular frontend agent.

---

You are the expert frontend agent for the Foshol Doctor Angular app (pin 22.1.5, signals only, ngx-translate, default locale `bn`).

The BN/EN language toggle already exists and already works for UI chrome (`bn.json` / `en.json`, `LanguageStore`, `foshol.lang`). **Do not rebuild, restyle, or relocate the toggle.** Your job is to bind catalogue *content* to the bilingual fields the backend now returns so that toggling English/Bangla swaps that content instantly, with no refetch and no broken flows.

Do not invent endpoints. Regenerate the HTTP client from the backend OpenAPI with `ng-openapi-gen` (`WEB-NFR-005`); do not hand-edit generated files.

## Working method (mandatory)

1. **Analyse first. Do not edit until the inventory is written.** Search the app for every binding of catalogue text: `nameBn`, `titleBn`, `stepsBn`, `dosageBn`, `descriptionBn`, `rateNotesBn`, crop labels, FAQ/voice-search chips, disease lists, remedy cards, symptom chips, officer suggested-remedy views that reuse the knowledge `Disease` / `Remedy` / `Crop` / `SymptomRef` schemas.
2. Produce a short inventory: file, component, which `*Bn` field is rendered, which bilingual pair to use after the change.
3. Identify what must **not** change: login/OTP, capture/submit case, SSE, JWT handling, queue claim/approve, Grad-CAM, i18n chrome keys, the toggle component itself.
4. Only then apply the smallest possible changes: regenerate the client, add one shared content-locale helper (or extend the existing fallback pipe), switch the inventoried bindings, add `(bn)` marking for fallbacks.
5. After edits, walk farmer FAQ + crop picker + disease/remedy views and any officer screen that lists those same schemas. Confirm toggle still switches chrome. Confirm case capture and review still work.

If a screen shows agronomic text from a schema that **does not** have `*En` in OpenAPI, leave it on `*Bn`. Do not invent English.

## Why this change exists

The toggle could only switch chrome. Catalogue GETs and FAQ voice-search returned Bangla fields, so disease names, crop names, remedy titles/steps/dosages stayed Bangla in English mode.

The backend now returns **both locales in one payload**. `preferred_language` on voice-search is an **ASR hint only**; it does not select UI language. `Accept-Language` may stay in sync with the toggle as you already send it; it does not change which fields are present.

## Contract (already shipped)

Pull / copy `docs/openapi/foshol-api.yaml` from the backend repo and regenerate.

Jackson serialises both the Bangla source field and the English field (with NFR-038 fallback already applied server-side). When `*Fallback` is `true`, the `*En` value **is a copy of Bangla**. You still need the flag so English mode can show the `(bn)` marker (`WEB-UX-015`).

### `Crop` — `GET /api/v1/crops`

| Field | Role |
|---|---|
| `nameBn` | Bangla name (language of record) |
| `nameEn` | English name, or Bangla copy if fallback |
| `nameEnFallback` | `true` when there is no real English |

### `Disease` — `GET /api/v1/crops/{id}/diseases`, `GET /api/v1/diseases/{id}`

| Field | Role |
|---|---|
| `nameBn` / `nameEn` / `nameEnFallback` | Disease name |
| `descriptionBn` / `descriptionEn` / `descriptionEnFallback` | Short description. `descriptionEnFallback` is `true` only when Bangla exists and English does not |

### `Remedy` — `GET /api/v1/diseases/{id}/remedies`

| Field | Role |
|---|---|
| `titleBn` / `titleEn` / `titleEnFallback` | Title |
| `stepsBn` / `stepsEn` / `stepsEnFallback` | Step list (`string[]`). Empty/missing `stepsEn` → server copies `stepsBn` and sets fallback |
| `dosageBn` / `dosageEn` / `dosageEnFallback` | Dosage text. Fallback only when Bangla dosage exists and English does not |
| `rateNotesBn` / `rateNotesEn` / `rateNotesEnFallback` | Rate notes. Same rule as dosage |
| `type`, `phiDays`, `costTier`, `efficacy` | Enums/numbers. Translate **chrome labels** via ngx-translate, never the agronomic strings |

### `VoiceSearchCandidate` — `POST /api/v1/faq/voice-search`

| Field | Role |
|---|---|
| `nameBn` / `nameEn` / `nameEnFallback` | Chip / candidate label |
| `diseaseId` | Confirm → load remedies. Do not parse names |

Keep `preferred_language` as the ASR language you already send (typically `bn`). **Do not** bind it to the UI toggle.

### `SymptomRef` — `GET /api/v1/symptoms`

`nameBn`, `nameEn`, `nameEnFallback`. Use the same helper as crop/disease names.

## Display rules

Shared helper (pure function or existing fallback pipe). Call it from templates; do not duplicate `if (lang === 'en')` in every component.

Active locale **`bn`** (default, `WEB-UX-011`):

- Render the `*Bn` field (`nameBn`, `descriptionBn`, `titleBn`, `stepsBn`, `dosageBn`, `rateNotesBn`).
- No `(bn)` marker.

Active locale **`en`**:

- Render the matching `*En` field (string or `string[]`).
- If the sibling `*Fallback` is `true`: that value is Bangla. Show it with a visible `(bn)` marker and an accessible description that no English translation exists (`WEB-UX-015`, `COMMON-NFR-038`).
- If `*En` is null/absent (old payload): use `*Bn` and still mark `(bn)`. Never invent English (`WEB-UX-016`).

**Seamless toggle:** both locales are already on the in-memory objects. Switching language must re-read those fields (signals / computed). **Do not** refetch crops, diseases, remedies, or re-run voice-search on toggle. Unsaved form state must survive (`WEB-UX-012`).

Do not concatenate, rewrite, or machine-translate remedy steps or dosages. Do not localise digits inside dosage strings.

## Do not break

- Toggle chrome behaviour, default `bn`, `foshol.lang` persistence, `bn.json`/`en.json` key parity (`WEB-UX-017`).
- FAQ voice-search mechanics: crop + audio → candidates → confirm `diseaseId` → `GET .../remedies`. Only the **labels** change with locale.
- Case capture, OTP, officer queue/review, SSE, presigned media, JWT-in-memory.
- Schemas that are still Bangla-only in OpenAPI, including `ExtractedSymptom.nameBn` on analysis detail. Those are case/review payloads, not the knowledge catalogue. Leave them on `nameBn` unless OpenAPI grows `nameEn`.
- Published advisory / notification body text: only switch if those schemas actually gained `*En` fields (they have not in this change).

## Requirements

- `WEB-UX-012` — toggle switches every visible string with no reload.
- `WEB-UX-013` — no new user-visible string literals; chrome via ngx-translate.
- `WEB-UX-015` / `COMMON-NFR-038` — fallback flag → Bangla + `(bn)` in English mode.
- `WEB-UX-016` — do not translate content fields in the client.
- `WEB-NFR-001` — do not duplicate backend matching.

## Out of scope

- Do not change paths, methods, or status codes.
- Do not send `preferred_language` as a UI-locale switch.
- Do not author agronomic English.
- Do not hand-edit generated OpenAPI client files.

## Done when

- Inventory of `*Bn` catalogue bindings exists and each has a `*En` pair or an explicit “Bangla-only schema, left unchanged” note.
- Locale `bn`: crops, FAQ chips, disease names/descriptions, remedy title/steps/dosage/rate notes, symptom names are Bangla.
- Locale `en`: those surfaces use `*En`; any `*Fallback === true` field shows Bangla plus `(bn)`.
- Toggling does not refetch and does not clear FAQ candidates, selected crop, or remedy list.
- Voice-search still ranks as today; only chip text follows the toggle.
- Confirming a chip still loads `GET /api/v1/diseases/{id}/remedies`.
- Officer (or other) views that consume generated `Disease` / `Remedy` / `Crop` / `SymptomRef` are not stuck on `*Bn`-only.
- Login, capture, review, and chrome toggle still work. `bn.json` and `en.json` key sets stay identical if you add chrome keys.
