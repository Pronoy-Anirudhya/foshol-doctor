"""LIVE vision: load the ViT once, classify from memory, cache predictions by digest."""

from __future__ import annotations

import time
from collections import OrderedDict
from dataclasses import dataclass
from io import BytesIO
from typing import Any, Protocol

from app.config import ROLE_VISION_RICE_PRIMARY, ModelSpec, Settings
from app.errors import SidecarError, inference_failed, model_unavailable, undecodable
from app.replay import sha256_hex
from app.vision import _clip_predictions, read_image, route_crop

CACHE_MAX = 64


class VisionRuntime(Protocol):
    model_id: str
    model_version: str
    architecture: str
    labels: list[str]

    def predict(self, image_bytes: bytes) -> list[tuple[str, float]]:
        ...


@dataclass
class TransformersRuntime:
    model_id: str
    model_version: str
    architecture: str
    labels: list[str]
    processor: Any
    model: Any

    def predict(self, image_bytes: bytes) -> list[tuple[str, float]]:
        from PIL import Image, UnidentifiedImageError
        import torch
        from torch.nn.functional import softmax

        try:
            image = Image.open(BytesIO(image_bytes)).convert("RGB")
        except (UnidentifiedImageError, OSError) as exc:
            raise undecodable("image bytes could not be decoded") from exc
        inputs = self.processor(images=image, return_tensors="pt")
        with torch.no_grad():
            logits = self.model(**inputs).logits
            probs = softmax(logits, dim=-1)[0]
        ranked: list[tuple[str, float]] = []
        for index, confidence in enumerate(probs.tolist()):
            label = self.labels[index] if index < len(self.labels) else str(index)
            ranked.append((label, float(confidence)))
        ranked.sort(key=lambda item: item[1], reverse=True)
        return ranked


class ClassifyCache:
    def __init__(self, max_size: int = CACHE_MAX) -> None:
        self._max = max_size
        self._hits: OrderedDict[tuple[str, str, int], list[dict[str, Any]]] = OrderedDict()

    def get(self, digest: str, crop_code: str, top_k: int) -> list[dict[str, Any]] | None:
        key = (digest, crop_code, top_k)
        if key not in self._hits:
            return None
        self._hits.move_to_end(key)
        return [dict(row) for row in self._hits[key]]

    def put(self, digest: str, crop_code: str, top_k: int, predictions: list[dict[str, Any]]) -> None:
        key = (digest, crop_code, top_k)
        self._hits[key] = [dict(row) for row in predictions]
        self._hits.move_to_end(key)
        while len(self._hits) > self._max:
            self._hits.popitem(last=False)


def load_runtime(spec: ModelSpec, torch_threads: int) -> VisionRuntime:
    import torch
    from transformers import AutoImageProcessor, AutoModelForImageClassification

    torch.set_num_threads(max(torch_threads, 1))
    processor = AutoImageProcessor.from_pretrained(spec.model_id, revision=spec.model_version)
    model = AutoModelForImageClassification.from_pretrained(spec.model_id, revision=spec.model_version)
    model.eval()
    id2label = {int(k): str(v) for k, v in model.config.id2label.items()}
    labels = [id2label[i] for i in range(len(id2label))]
    architecture = str(getattr(model.config, "model_type", None) or "vit")
    runtime = TransformersRuntime(
        model_id=spec.model_id,
        model_version=spec.model_version,
        architecture=architecture,
        labels=labels,
        processor=processor,
        model=model,
    )
    _warmup(runtime)
    return runtime


def _warmup(runtime: VisionRuntime) -> None:
    from PIL import Image

    buffer = BytesIO()
    Image.new("RGB", (224, 224), color=(16, 120, 16)).save(buffer, format="PNG")
    runtime.predict(buffer.getvalue())


def install_live_vision(state: Any) -> None:
    settings: Settings = state.settings
    spec = settings.models.get(ROLE_VISION_RICE_PRIMARY)
    if spec is None:
        raise SystemExit("LIVE mode requires vision.rice.primary (the ViT) to be configured")
    runtime = load_runtime(spec, settings.torch_threads)
    state.vision_runtime = runtime
    state.classify_cache = ClassifyCache()
    state.loaded_flags[ROLE_VISION_RICE_PRIMARY] = True
    state.loaded_at[ROLE_VISION_RICE_PRIMARY] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    state.labels[ROLE_VISION_RICE_PRIMARY] = list(runtime.labels)
    state.models_loaded = 1
    state.degraded_reasons = [
        "vision.rice.fallback not loaded",
        "vision.solanaceae not loaded",
        "asr.bangla not loaded",
        "embed.text not loaded",
    ]


def classify_live(
    settings: Settings,
    runtime: VisionRuntime,
    cache: ClassifyCache,
    image: bytes,
    crop_code: str,
    top_k: int,
    model_role: str | None,
    correlation_id: str,
) -> dict[str, Any]:
    started = time.perf_counter()
    read_image(settings, image)
    route = route_crop(
        settings,
        crop_code,
        model_role,
        primary_usable=True,
        fallback_usable=False,
        solanaceae_usable=False,
    )
    if route.role != ROLE_VISION_RICE_PRIMARY:
        raise model_unavailable("LIVE vision loads only the ViT (vision.rice.primary)")
    digest = sha256_hex(image)
    cached = cache.get(digest, crop_code, top_k)
    if cached is None:
        try:
            ranked = runtime.predict(image)
        except SidecarError:
            raise
        except Exception as exc:
            raise inference_failed("ViT forward pass failed") from exc
        raw = [{"raw_label": label, "confidence": confidence, "rank": 0} for label, confidence in ranked]
        predictions = _clip_predictions(raw, top_k)
        cache.put(digest, crop_code, top_k, predictions)
    else:
        predictions = cached
    inference_ms = max(int((time.perf_counter() - started) * 1000), 0)
    return {
        "mode": "LIVE",
        "crop_code": crop_code,
        "model_id": runtime.model_id,
        "model_version": runtime.model_version,
        "model_role": route.model_role,
        "fallback_used": route.fallback_used,
        "fallback_reason": route.fallback_reason,
        "architecture": runtime.architecture,
        "image_sha256": digest,
        "predictions": predictions,
        "inference_ms": inference_ms,
        "correlation_id": correlation_id,
    }
