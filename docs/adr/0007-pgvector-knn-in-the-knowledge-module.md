# ADR-0007: The sidecar embeds; the `knowledge` module retrieves

- **Status:** Accepted
- **Date:** 2026-09-07
- **Deciders:** project lead (human), requirements agent
- **Supersedes / relates to:** **resolves a contradiction between plan §5 and plan §7.2**; clarification 10; relates to ADR-0001, ADR-0008; interfaces in `00-common.ears.md` §6.2

## Context

The plan contradicts itself about where symptom retrieval happens, and it is the kind of contradiction
that only surfaces at integration.

- **Plan §5** gives the sidecar an endpoint `POST /v1/symptoms/extract`, whose name says the sidecar
  turns a transcript into symptom codes.
- **Plan §7.2** then specifies the pipeline for exactly that operation: normalise the transcript,
  embed it, run a cosine kNN with pgvector HNSW against `symptom_phrase.embedding`, keep matches above
  `foshol.knowledge.match.vector-threshold`, then score `disease_symptom` weights.

Both cannot hold. `symptom_phrase` is owned by `knowledge` (`00-common.ears.md` §4.3). For the sidecar
to run that query it would need database credentials, a Postgres driver, SQL, and knowledge of a
schema owned by another agent — and two components would then read and reason about the same table.
With seven agents in parallel, a table with two readers that each encode assumptions about its shape
is a guaranteed divergence.

There is also a product argument. The pitch is "the AI can be wrong, the knowledge base is
authoritative". That only holds if the knowledge base lookup lives in the knowledge base module. If
retrieval sits in the Python container next to the models, then the fallback for an uncertain model is
*another model's opinion*, and the distinction the product rests on disappears.

## Decision

**Split at the embedding boundary. The sidecar produces vectors; `knowledge` uses them.**

The sidecar's endpoints are `POST /v1/vision/classify`, `/v1/vision/explain`, `/v1/asr/transcribe` and
`/v1/embed`. There is **no** `/v1/symptoms/extract`. `/v1/embed` takes text and returns a
768-dimensional vector (`COMMON-NFR-006`); that is the whole of its involvement in symptom matching.

**The sidecar gets no database credentials.** No Postgres driver, no connection string in its
environment, no link to the database container in `docker-compose.yml`. This is not a convention; it
is an absence, which is the only kind of boundary an agent cannot accidentally cross.

**`knowledge` owns retrieval in full**, behind the frozen `SymptomMatchApi`:

```java
public interface SymptomMatchApi { SymptomMatchResult match(SymptomMatchRequest request); }
public record SymptomMatchRequest(UUID cropId, String transcriptBn,
                                  float[] transcriptEmbedding, List<UUID> officerSymptomIds) {}
```

Inside that call sit three layers, all owned by A4: **vector kNN** over the HNSW index
`ix_symptom_phrase_embedding`, limited by `foshol.knowledge.match.knn-limit` and kept above
`foshol.knowledge.match.vector-threshold`; the **fuzzy token-overlap** layer from plan §7.2, gated by
`foshol.knowledge.match.fuzzy-threshold`, so an embedding miss still catches the obvious phrasing
(`MatchedSymptom.matcher` records which layer matched, and the officer sees it); and **weighted
disease scoring** over `disease_symptom.weight`, capped at `foshol.knowledge.match.max-symptoms` and
returning `inconclusive` when nothing clears `foshol.knowledge.match.inconclusive-score-min`.

`analysis` calls `/v1/embed` through its outbound port, passes the resulting `float[]` to
`SymptomMatchApi`, and never touches `symptom_phrase` — the dependency matrix (`00-common.ears.md`
§6.1) permits `analysis → knowledge` and nothing else in that direction.

**One writer and one owner per table is what makes parallel agents safe.** `symptom`,
`symptom_phrase`, `disease_symptom` and `model_label_map` each have exactly one module that reads them
with intent and one migration series that writes them. Every other consumer goes through
`KnowledgeQueryApi` or `SymptomMatchApi`.

## Consequences

### Positive

- The sidecar is a stateless function of its inputs, which makes it trivially restartable — exactly
  what the sidecar-kill demo requires.
- The knowledge base stays authoritative in the architecture, not just in the pitch: when the model is
  uncertain, the system falls back to human-curated content, and the code shows that.
- Threshold tuning for symptom matching is one module's concern and one property group's concern,
  not split across a Java service and a Python file.
- The `SECONDARY` path is fully testable without any model running, because its input is a vector the
  test supplies directly.
- No schema knowledge leaves the JVM, so a migration by A1 cannot silently break a file A7 owns.

### Negative / accepted cost

- One extra hop: `analysis → sidecar /v1/embed → analysis → knowledge`. Against
  `foshol.analysis.deadline=PT12S` this is noise, but it is a real hop and it can fail.
- A `float[768]` crosses a module boundary in `SymptomMatchRequest`. That is a raw-array parameter on a
  published interface, and it means `knowledge` trusts `analysis` to have produced the vector with the
  configured model. Nothing validates that beyond dimensionality.
- The embedding model id lives in `foshol.ai.embed.model-id`, but the vectors in
  `symptom_phrase.embedding` were produced by whatever model ran at seed time. Changing the model
  without re-running `V16__ref_symptom_embeddings.sql` degrades matching silently rather than raising
  an error. This is a real sharp edge and it is not currently guarded.
- Retrieval in Java means reimplementing normalisation (`COMMON-NFR-013`: NFC, ZWNJ/ZWJ stripping,
  Bengali digit forms) that a Python pipeline would get from a library — roughly forty lines,
  conceptually duplicated with the sidecar's own text handling.

## Alternatives considered

**Sidecar owns retrieval, as plan §5 implies.** Rejected: database credentials in the inference
container, two readers of `symptom_phrase`, and the "knowledge base is authoritative" argument
undermined.

**Retrieval in Java without the sidecar — embed in the JVM too.** Rejected. Running the embedding
model in-process means DJL or ONNX Runtime and a day of tensor-shape debugging (plan §5), for a
component the sidecar already provides.

**Cache embeddings in `analysis` and let it query `symptom_phrase` directly.** Rejected: the same
two-readers problem, now inside the JVM — and `COMMON-ARCH-002` would fail the build anyway.

**Put symptom matching in `analysis`, leaving `knowledge` a pure lookup service.** Rejected. It puts
the knowledge base's core scoring rule in the module named after the AI, which is the wrong home for
"the authoritative fallback".

## Revisit when

Revisit if the embedding call becomes the dominant cost of the `SECONDARY` path — concretely, if the
`foshol.ai.call` timer tagged `embed` exceeds a quarter of `foshol.analysis.deadline` at p95. The fix
then is caching embeddings for repeated transcripts, not moving retrieval into the sidecar.
Separately, revisit the moment anything other than `knowledge` needs to read `symptom_phrase`: that is
the signal that `SymptomMatchApi` is missing an operation, and the answer is to add one to the API,
never to add a second reader.
