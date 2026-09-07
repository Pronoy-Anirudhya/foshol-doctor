"""768-d text embeddings. Replay uses fixtures or deterministic hash unit vectors."""

from __future__ import annotations

import hashlib
import math
import time
from typing import Any

from app.config import ROLE_EMBED_TEXT, Settings
from app.errors import bad_request, model_unavailable, payload_too_large
from app.replay import FixtureStore, sha256_hex
from app.text import normalise_for_embed

EMBED_DIM = 768


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


def embed_replay(
    settings: Settings,
    store: FixtureStore,
    texts: list[str],
    correlation_id: str,
) -> dict[str, Any]:
    started = time.perf_counter()
    if not texts:
        raise bad_request("texts must contain at least one string")
    if len(texts) > settings.embed_max_batch:
        raise payload_too_large("batch exceeds FOSHOL_SIDECAR_EMBED_MAX_BATCH")
    for text in texts:
        if len(text) > settings.embed_max_chars:
            raise payload_too_large("a text exceeds FOSHOL_SIDECAR_EMBED_MAX_CHARS")

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
