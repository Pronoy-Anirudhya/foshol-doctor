# CONTENT-OWNERS — the register of everything an agent may not author

**Status:** Open. **Owner of this file:** platform agent (A1). **Enforces:** `COMMON-CON-003`.

> **The rule.** We used AI aggressively to build this system and not at all to author the
> agricultural advice inside it. Every line of Java, Groovy, TypeScript, Python and SQL *structure* in
> this repository may be written by an agent. Not one disease name, remedy step, dosage,
> pre-harvest interval, citation, Bangla symptom phrase or threshold value may be.
>
> **Why.** A language model will produce a fluent, well-formatted, entirely plausible spray schedule
> for rice blast. It will look exactly like the real one. Nobody reading the migration file can tell
> the difference, and the failure lands on a smallholder who sprays the wrong chemical at the wrong
> rate, or harvests inside a pre-harvest interval that was invented. The whole product rests on one
> sentence — *no farmer in this system has ever received unverified pesticide advice*
> (`00-common.ears.md` §1) — and an agent that fabricates a dosage has broken that sentence before the
> officer workflow ever gets a chance to hold it. Officer approval (ADR-0003) protects against a wrong
> *model output*. Nothing protects against wrong *knowledge base content*, because the officer is
> reading from it.
>
> An agent may specify the **structure, source and validation** of this content, and may write the
> migration **scaffold** with `TODO(content-owner: C<n>)` markers. It may not write the values. See
> the escalation procedure at the foot of this document.
>
> Fictional farmer and officer personas for demo seed data are **exempt** — they are not agronomic
> content (clarification 23).

Deadlines are day numbers on the four-day schedule; **Day 0** is the platform gate that runs before
any module agent starts (`00-common.ears.md` §12.2).

---

## The register

| ID | Artefact | Where it lands | Structure required | Source required | Validation | Owner | Deadline |
|---|---|---|---|---|---|---|---|
| **C1** | The **14 disease definitions**: `code`, `name_bn`, `name_en`, `description_bn`, `severity`, `is_healthy` | `disease` · `V11__ref_diseases.sql` | Exactly 14 rows, one per class in `00-common.ears.md` §2.5; `code` unique per crop, stable, `[a-z0-9_]`; `severity ∈ LOW\|MODERATE\|HIGH\|CRITICAL\|NONE`; `is_healthy = true` on the three healthy classes and their `severity = NONE`; `description_bn` 1–3 sentences, farmer-readable, no remedy content | Named DAE / BRRI / BARI publication or a Bangladeshi extension agronomist, recorded per row in the migration comment | `COMMON-DATA-001` (exactly 14 rows); `ck_disease_severity`; `uq_disease_crop_code`; human sign-off that each `name_bn` is the term a farmer uses, not a transliteration | `TODO(assign)` | Day 0 for `code` / `name_bn` / `severity`; Day 1 for `description_bn` |
| **C2** | **Crop rows**: `code`, `name_bn`, `name_en`, `icon_key`, `display_order` | `crop` · `V10__ref_crops.sql` | Exactly 3 rows (rice, tomato, potato); `code` unique and stable — it is the join key used by every fixture and by `p_officer_queue.crop_code` | Same as C1 | `uq` on `code`; every `disease.crop_id` resolves; `icon_key` exists in `web/src/app/assets` | `TODO(assign)` | Day 0 |
| **C3** | **Symptom codes, Bangla names and organs**: `code`, `name_bn`, `name_en`, `organ` | `symptom` · `V12__ref_symptoms.sql` | Closed vocabulary, fixed on Day 1 and not extended afterwards; `code` unique, stable, `[a-z0-9_]`; `organ ∈ LEAF\|STEM\|ROOT\|PANICLE\|FRUIT\|TUBER\|WHOLE`; `name_bn` is the *reference* term (the colloquial forms are C4) | Same as C1 | `ck_symptom_organ`; every symptom referenced by at least one `disease_symptom` row (C5); no orphan symptom | `TODO(assign)` | Day 0 |
| **C4** | **5–10 colloquial Bangla phrases per symptom**: `phrase_bn`, `normalised_bn` | `symptom_phrase` · `V13__ref_symptom_phrases.sql` | 5–10 rows per C3 symptom; each is a **surface form a farmer actually says**, not a textbook term; whole clauses welcome; regional variants encouraged and marked in the migration comment; `normalised_bn` per `COMMON-NFR-013` (NFC, ZWNJ/ZWJ stripped, Bengali digits → ASCII) | **A native Bangla speaker working with an agronomy reference.** Not a translation tool, not a dictionary, not an agent | `uq_symptom_phrase (symptom_id, normalised_bn)`; ≥5 rows per symptom checked by query before Day 2; spot-read aloud with a Bangla speaker who did not write them | `TODO(assign)` | Day 1 |
| **C5** | **`disease_symptom` weights** | `disease_symptom` · `V14__ref_disease_symptom_weights.sql` | One row per plausible (disease, symptom) pair; `weight ∈ (0,1]` expressing how indicative the symptom is *of that disease*, not how common it is; every non-healthy disease has ≥3 weighted symptoms | Same as C1 — this is a diagnostic judgement, not a modelling choice | `ck_disease_symptom_weight`; every disease reachable; a query showing no two diseases with identical weight vectors (which would make them indistinguishable to `SymptomMatchApi`) | `TODO(assign)` | Day 1 |
| **C6** | **Remedy content**: `title_bn`, `steps_bn`, `dosage_bn`, `cost_tier`, `efficacy`, `type`, `display_order` | `remedy` · `V15__ref_remedies.sql` | ≥1 remedy per non-healthy disease, and ≥1 **non-chemical** remedy per non-healthy disease; `steps_bn` a JSON **array** of short imperative Bangla steps; `type ∈ CULTURAL\|ORGANIC\|BIOLOGICAL\|CHEMICAL`; `cost_tier` and `efficacy ∈ LOW\|MEDIUM\|HIGH`; `dosage_bn` states quantity **and** unit **and** area or volume basis | Named DAE / BRRI / BARI guidance document, per remedy — quoted, not paraphrased from memory | `ck_remedy_type`, `ck_remedy_cost`, `ck_remedy_efficacy`, `ck_remedy_steps` (`jsonb_typeof = 'array'`); query asserting every non-healthy disease has a non-chemical option; second-reader sign-off on every `dosage_bn` | `TODO(assign)` | Day 2 |
| **C7** | **`phi_days` on every chemical remedy** | `remedy.phi_days` · `V15__ref_remedies.sql` | Mandatory and non-null wherever `type = 'CHEMICAL'`; integer days; must come from the same source as the dosage it accompanies — a PHI paired with a dosage from a different product is wrong even when both are individually real | The product label or the DAE / BRRI / BARI guidance cited in C8 for that same remedy row | `ck_remedy_phi` (`type <> 'CHEMICAL' OR phi_days IS NOT NULL`) — enforced by the database, not only in Java; plus explicit human sign-off per chemical row | `TODO(assign)` | Day 2 — **blocks the `PRIMARY` path demo** |
| **C8** | **`source_ref` citations** | `remedy.source_ref` · `V15__ref_remedies.sql` | `NOT NULL` on every remedy; identifies publication, issuing body (DAE / BRRI / BARI), and year or edition; specific enough that a reader can find the page | The actual document. A citation to a document nobody opened is worse than none | `NOT NULL` at the schema level; every distinct `source_ref` checked against a real, retrievable document before Day 3 | `TODO(assign)` | Day 2 |
| **C9** | **Fixed Bangla UI strings** | `web/src/app/assets/i18n/bn.json` (and `en.json`) | Every UI string keyed; `bn.json` and `en.json` have identical key sets; Bangla is the language of record (`COMMON-NFR-037`); no string concatenated from fragments in code | Native Bangla speaker; register appropriate to a farmer, not to an office | Key-parity check between `bn.json` and `en.json` in CI; runtime BN/EN toggle walked end to end on both farmer and officer surfaces (`COMMON-NFR-039`) | `TODO(assign)` | Day 1 draft, Day 3 final |
| **C10** | **Bangla rejection messages, one per reason code** | `case_rejection.message_bn` (template per code) · officer console | One template per `ck_rejection_reason` value: `BLURRY_IMAGE`, `NOT_A_CROP`, `WRONG_CROP`, `INSUFFICIENT_DETAIL`, `INAUDIBLE_AUDIO`, `OTHER`; each says **what to do next**, since rejection is terminal and the farmer must submit a new case (ADR-0012); no blame, no jargon | Native Bangla speaker; wording reviewed by whoever will act as the field officer in the demo | All six codes covered; each read by a Bangla speaker who did not write it; `OTHER` requires free text and must not be sent empty | `TODO(assign)` | Day 2 |
| **C11** | **Both confidence threshold values** | `foshol.analysis.confidence.high` / `.low` · `app/src/main/resources/application.properties` | Two decimals in `[0,1]` with `low < high`; the plan's starting values are `0.75` and `0.45` and are **defaults awaiting confirmation, not decisions**; displayed read-only on the admin stats page (clarification 20) | Project lead, on the evidence available — the replay fixtures on Day 1, `docs/eval-report.md` when it exists (ADR-0011). Never an agent, and never a number an agent "tuned" | Startup assertion `low < high`; the three prepared demo cases must route `PRIMARY`, `SECONDARY`, `UNDETERMINED` under the chosen pair; any change re-verified against the fixtures | `TODO(assign)` | Day 0 provisional, Day 3 confirmed |
| **C12** | **Bangla notification templates** | `notification.title_bn` / `body_bn` (template per type) · `V7__notification.sql` consumers | One title and body per `ck_notification_type` value: `ADVISORY_PUBLISHED`, `ADVISORY_REVISED`, `CASE_REJECTED`, `CASE_STATUS_CHANGED`; placeholders named, not positional; `ADVISORY_REVISED` must make clear that earlier advice **has changed** (ADR-0013) | Native Bangla speaker | All four types covered; SSE delivery of each type walked end to end; no template containing a phone number, object key or raw model label (`COMMON-SEC-001`, `COMMON-DATA-016`) | `TODO(assign)` | Day 2 |
| **C13** | **`model_label_map` rows**: `(model_id, model_version, raw_label) → disease_id` | `model_label_map` · `V17__ref_model_label_map.sql` | Every raw label of every model configured under `foshol.ai.vision.*` (`00-common.ears.md` §2.4), keyed by the model id **and version the sidecar reports** (`COMMON-DATA-017`); many-to-one permitted; a raw label with no true taxonomy equivalent is left unmapped **deliberately** and will route to `UNDETERMINED` (`COMMON-DATA-013`) | The model's published label list, read directly, plus an agronomist's judgement that the class means the same disease our taxonomy means. **Deciding two label strings denote the same disease is an agronomic judgement, not string matching** (ADR-0009) | `COMMON-DATA-014` (every configured model has ≥1 row, else `ERR_MODEL_LABEL_MAP_MISSING` at startup); `COMMON-DATA-015` (every `disease_id` resolves, else `ERR_MODEL_LABEL_MAP_INVALID`); `uq_model_label`. Note both checks verify *existence*, never *correctness* — only human review catches a mis-mapping | `TODO(assign)` | Day 0 — **the application will not start without it** |
| **C14** | **Held-out evaluation ground-truth labels** | `tools/eval.py` input set → `docs/eval-report.md` | ≥100 images per crop, held out and never used to tune a threshold or select a model; labelled against **our own 14-class taxonomy** (C1), not any model's native labels; per-class counts recorded alongside rates so nobody quotes a percentage computed over a handful of images | A human labeller with an agronomy reference; image provenance and licence recorded per source | ≥100 per crop asserted by the harness; report carries the model ids, versions and mode (ADR-0010) that produced it; **this is the only accuracy source permitted anywhere** (`COMMON-CON-002`, ADR-0011) | `TODO(assign)` | Day 3 |
| **C15** | **Remedy rate amounts** (`rate_amount`, `rate_unit`, `rate_basis`, optional `rate_notes_bn`) for FO dose suggestions | `remedy` · `V104__remedy_rate_columns.sql` | Nullable until signed off; when present: `rate_unit ∈ ML\|G\|KG\|L`, `rate_basis ∈ PER_DECIMAL\|PER_SQ_M\|PER_HECTARE\|PER_ACRE\|PER_SQ_FT\|FIXED`; values must come from the same source as C6/C7 for that row — **never agent-invented** | Named DAE / BRRI / BARI product label or guidance cited in C8 | Schema checks on enums; second-reader sign-off per chemical row that participates in auto-calc; empty rates → FO sees no `computedDose` (visible gap) | `TODO(assign)` | Day 2+ |

**Hackathon only — does not satisfy C15.** [`V105__demo_remedy_rates.sql`](../../app/src/main/resources/db/migration/V105__demo_remedy_rates.sql) fills DEMO numbers on selected V18 remedy rows so the officer UI can show `computedDose`. Those values are **not** C15 sign-off, **not** production KDB, and must not be quoted as DAE/BRRI/BARI rates. Replace with human-owned C15 before any real farmer deployment.
| **C16** | **Field area / crop quantity unit catalogue** used on `diagnosis_case` and speech extraction | `common.FieldAreaUnit` · `common.CropQuantityUnit` · Flyway checks | Area: `DECIMAL\|SQ_M\|SQ_FT\|HECTARE\|ACRE`; quantity: `KG\|TON\|PLANTS\|BIGHAS_EQUIV`; Bangla surface forms for speech extraction listed in analysis extractor tests | Agronomist + Bangla speaker confirm unit names farmers use | Constraint checks; extractor golden transcripts | `TODO(assign)` | Day 2 |

---

## Row notes

**On C4, and why it is the most valuable work of the week.** The `SECONDARY` path — the one that fires
when the model is uncertain and the knowledge base takes over — is only as good as these phrases. A
farmer says *the leaves are turning yellow* in whatever words their district uses; the vector kNN in
`knowledge` (ADR-0007) matches that utterance against `symptom_phrase.embedding`. Seed that table with
textbook terminology and the match fails silently on every real recording, the case falls to
`UNDETERMINED`, and the fallback path we built the whole `knowledge` module for never demonstrably
works. The plan is emphatic on this point and it is right: curating these phrases is the highest-value
non-code work of the week, and it belongs to a native Bangla speaker with an agronomy reference — not
to an agent, and not to a translation tool.

**On the embeddings.** `V16__ref_symptom_embeddings.sql` populates `symptom.embedding` and
`symptom_phrase.embedding`. Those vectors are **generated** by running C3 and C4 text through
`foshol.ai.embed.model-id`; they are not authored and carry no register row. They are, however,
strictly downstream of C4: the embeddings are only as good as the phrases, and re-running C4 means
re-running V16.

**On C11 and the word "default".** `0.75` and `0.45` appear throughout `00-common.ears.md` §9.1 and in
ADR-0006. They are in the properties file so the system boots, not because anyone has decided they are
right. An agent may read them, must externalise them (`COMMON-NFR-020`), and must not change them.

**On what is *not* on this register.** Fictional farmer and officer personas in
`V100__seed_demo_identities.sql`, case notes on generated historical cases, and the choice of which
dataset images to attach to a seeded case are all agent-authorable (clarification 23) — provided every
seeded advisory references only human-authored remedy rows from C6.

---

## Escalation — what an agent does when the content is not there yet

This will happen. The migrations are scaffolded on Day 0 and most of the content lands on Days 1–3, so
an agent building the `PRIMARY` path on Day 2 against an empty `remedy` table is the expected case, not
a failure.

**The procedure, in order:**

1. **Leave the marker.** In the migration scaffold, at the exact row or block that is missing, write
   `TODO(content-owner: C<n>)` naming the register row — for example
   `-- TODO(content-owner: C7) phi_days for every CHEMICAL remedy`. One marker per missing artefact.
   Never a marker without a `C<n>`.
2. **Record the blocker.** Add a line to `docs/progress/<agent>.md` under `## Blocked`, per
   `COMMON-NFR-045`: the requirement ID, the register row, who owns the unblock, and since when.
3. **Keep going where you can.** Build everything the missing content does not gate. A missing
   `dosage_bn` does not stop the remedy query handler, its unit tests, or the officer console's remedy
   editor from being finished against an empty result set.
4. **Stop where you cannot.** If the requirement genuinely cannot be satisfied without the content,
   stop and leave it unimplemented. `COMMON-NFR-046`: an agent that cannot satisfy a requirement
   without violating an architectural law, a frozen contract or `COMMON-CON-003` stops and records the
   blocker — it does not work around it.

**What an agent must never do.** Not once, not as a placeholder, not "to unblock the build", not
marked with a comment saying it is temporary:

- write a disease description, remedy step, dosage, PHI value, cost tier, efficacy rating or
  `source_ref` citation;
- write a Bangla symptom phrase, rejection message or notification body;
- choose or adjust a confidence threshold;
- add a `model_label_map` row by guessing which raw label means which disease;
- generate ground-truth labels for the evaluation set.

**A plausible-looking value is the specific failure mode this register exists to prevent.** An empty
table is visible: a query returns nothing, a test fails, a page is blank, and somebody asks why. A
fabricated dosage is invisible: it renders correctly, it passes every constraint in the schema, it
reads fluently to anyone who is not an agronomist, and the first person positioned to catch it is a
farmer with a sprayer. `TODO(content-owner: C<n>)` and a recorded blocker are always the correct
output. Inventing the value never is.

> **On the Definition of Done's no-`TODO` rule.** `00-common.ears.md` §11 item 8 forbids `TODO` in a
> completed module. `TODO(content-owner: C<n>)` is the single permitted exception and it lives **only
> in the Flyway migration scaffolds owned by A1** — never in Java, TypeScript or Python source. A
> module agent blocked on content stops (step 4) and records it; it does not stub the value and leave
> a marker in its own code.
