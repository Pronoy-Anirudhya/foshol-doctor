"""768-d text embeddings. Replay uses fixtures; LIVE loads LaBSE once."""

from __future__ import annotations

import hashlib
import math
import time
from collections import OrderedDict
from typing import Any, Protocol

from app.config import ROLE_EMBED_TEXT, ModelSpec, Settings
from app.errors import (
    ERR_SIDECAR_EMBED_DIM_MISMATCH,
    SidecarError,
    bad_request,
    inference_failed,
    model_unavailable,
    payload_too_large,
)
from app.replay import FixtureStore, sha256_hex
from app.text import normalise_for_embed

EMBED_DIM = 768
CACHE_MAX = 64


def l2_normalise(values: list[float]) -> list[float]:
    norm = math.sqrt(sum(v * v for v in values))
    if norm == 0.0:
        out = [0.0] * len(values)
        out[0] = 1.0
        return out
    return [v / norm for v in values]


def hash_unit_vector(text: str, dim: int = EMBED_DIM) -> list[float]:
    seed = hashlib.sha256(text.encode("utf-8")).digest()
    raw = bytearray()
    counter = 0
    while len(raw) < dim * 4:
        raw.extend(hashlib.sha256(seed + counter.to_bytes(4, "big")).digest())
        counter += 1
    values: list[float] = []
    for index in range(dim):
        unsigned = int.from_bytes(raw[index * 4 : index * 4 + 4], "big")
        values.append((unsigned / 2**32) * 2.0 - 1.0)
    return l2_normalise(values)


def require_embed_dim(dimension: int) -> None:
    if dimension != EMBED_DIM:
        raise SystemExit(
            f"{ERR_SIDECAR_EMBED_DIM_MISMATCH}: embed.text produced {dimension}, expected {EMBED_DIM}"
        )


def _validate_embed_batch(settings: Settings, texts: list[str]) -> None:
    if not texts:
        raise bad_request("texts must contain at least one string")
    if len(texts) > settings.embed_max_batch:
        raise payload_too_large("batch exceeds FOSHOL_SIDECAR_EMBED_MAX_BATCH")
    for text in texts:
        if len(text) > settings.embed_max_chars:
            raise payload_too_large("a text exceeds FOSHOL_SIDECAR_EMBED_MAX_CHARS")


def embed_replay(
    settings: Settings,
    store: FixtureStore,
    texts: list[str],
    correlation_id: str,
) -> dict[str, Any]:
    started = time.perf_counter()
    _validate_embed_batch(settings, texts)

    spec = settings.models[ROLE_EMBED_TEXT]
    vectors: list[list[float]] = []
    for text in texts:
        normalised = normalise_for_embed(text)
        digest = sha256_hex(normalised.encode("utf-8"))
        body = store.load_json(store.embed_path(digest))
        if body is not None and body.get("embeddings"):
            vector = [float(v) for v in body["embeddings"][0]]
            if len(vector) != EMBED_DIM:
                vector = hash_unit_vector(normalised)
            else:
                vector = l2_normalise(vector)
        else:
            vector = hash_unit_vector(normalised)
        vectors.append(vector)

    inference_ms = max(int((time.perf_counter() - started) * 1000), 0)
    return {
        "mode": "REPLAY",
        "model_id": spec.model_id,
        "model_version": spec.model_version,
        "dimension": EMBED_DIM,
        "normalised": True,
        "embeddings": vectors,
        "inference_ms": inference_ms,
        "correlation_id": correlation_id,
    }


def embed_live_unavailable() -> None:
    raise model_unavailable("embedding weights are not loaded")


class EmbedRuntime(Protocol):
    model_id: str
    model_version: str
    dimension: int

    def embed(self, texts: list[str]) -> list[list[float]]:
        ...


class EmbedCache:
    def __init__(self, max_size: int = CACHE_MAX) -> None:
        self._max = max_size
        self._hits: OrderedDict[str, list[float]] = OrderedDict()

    def get(self, digest: str) -> list[float] | None:
        if digest not in self._hits:
            return None
        self._hits.move_to_end(digest)
        return list(self._hits[digest])

    def put(self, digest: str, vector: list[float]) -> None:
        self._hits[digest] = list(vector)
        self._hits.move_to_end(digest)
        while len(self._hits) > self._max:
            self._hits.popitem(last=False)


class SentenceTransformersRuntime:
    def __init__(self, model_id: str, model_version: str, dimension: int, model: Any) -> None:
        self.model_id = model_id
        self.model_version = model_version
        self.dimension = dimension
        self.model = model

    def embed(self, texts: list[str]) -> list[list[float]]:
        try:
            vectors = self.model.encode(
                texts,
                normalize_embeddings=True,
                convert_to_numpy=True,
                show_progress_bar=False,
            )
        except SidecarError:
            raise
        except Exception as exc:
            raise inference_failed("embedding forward pass failed") from exc
        return [row.tolist() for row in vectors]


def embed_live(
    settings: Settings,
    runtime: EmbedRuntime,
    cache: EmbedCache,
    texts: list[str],
    correlation_id: str,
) -> dict[str, Any]:
    started = time.perf_counter()
    _validate_embed_batch(settings, texts)
    vectors: list[list[float] | None] = [None] * len(texts)
    misses: list[tuple[int, str]] = []
    miss_texts: list[str] = []
    for index, text in enumerate(texts):
        normalised = normalise_for_embed(text)
        digest = sha256_hex(normalised.encode("utf-8"))
        cached = cache.get(digest)
        if cached is not None:
            vectors[index] = cached
        else:
            misses.append((index, digest))
            miss_texts.append(normalised)
    if miss_texts:
        try:
            encoded = runtime.embed(miss_texts)
        except SidecarError:
            raise
        except Exception as exc:
            raise inference_failed("embedding forward pass failed") from exc
        if len(encoded) != len(miss_texts):
            raise inference_failed("embedding batch size mismatch")
        for (index, digest), vector in zip(misses, encoded):
            if len(vector) != EMBED_DIM:
                raise inference_failed("embedding dimension is not 768")
            unit = l2_normalise(vector)
            cache.put(digest, unit)
            vectors[index] = unit
    filled: list[list[float]] = []
    for vector in vectors:
        if vector is None:
            raise inference_failed("embedding cache assembly failed")
        filled.append(vector)
    inference_ms = max(int((time.perf_counter() - started) * 1000), 0)
    return {
        "mode": "LIVE",
        "model_id": runtime.model_id,
        "model_version": runtime.model_version,
        "dimension": EMBED_DIM,
        "normalised": True,
        "embeddings": filled,
        "inference_ms": inference_ms,
        "correlation_id": correlation_id,
    }


def _mark_role_loaded(state: Any, role: str) -> None:
    state.loaded_flags[role] = True
    state.loaded_at[role] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    state.models_loaded = sum(1 for flag in state.loaded_flags.values() if flag)
    marker = f"{role} not loaded"
    state.degraded_reasons = [reason for reason in state.degraded_reasons if reason != marker]


def _warmup(runtime: EmbedRuntime) -> None:
    runtime.embed(["warmup"])


def load_embed_runtime(spec: ModelSpec, torch_threads: int) -> EmbedRuntime:
    import torch
    from sentence_transformers import SentenceTransformer

    torch.set_num_threads(max(torch_threads, 1))
    model = SentenceTransformer(spec.model_id, revision=spec.model_version)
    dimension = int(model.get_sentence_embedding_dimension() or 0)
    require_embed_dim(dimension)
    runtime = SentenceTransformersRuntime(spec.model_id, spec.model_version, dimension, model)
    _warmup(runtime)
    return runtime


def install_live_embed(state: Any) -> None:
    settings: Settings = state.settings
    spec = settings.models.get(ROLE_EMBED_TEXT)
    if spec is None:
        raise SystemExit("LIVE mode requires embed.text to be configured")
    runtime = load_embed_runtime(spec, settings.torch_threads)
    state.embed_runtime = runtime
    state.embed_cache = EmbedCache()
    _mark_role_loaded(state, ROLE_EMBED_TEXT)
