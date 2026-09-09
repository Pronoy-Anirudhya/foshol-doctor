"""Vision classification routing, replay serving, and live-mode inference."""

from __future__ import annotations

import time
from dataclasses import dataclass
from typing import Any

from app.config import (
    FAMILY_RICE,
    ROLE_VISION_RICE_FALLBACK,
    ROLE_VISION_RICE_PRIMARY,
    ROLE_VISION_SOLANACEAE,
    Settings,
)
from app.errors import (
    bad_request,
    fixture_missing,
    model_unavailable,
    payload_too_large,
    undecodable,
    unknown_crop,
    unsupported_media,
)
from app.replay import FixtureStore, sha256_hex
from app.sniff import ALLOWED_IMAGE_TYPES, image_decodable, sniff_image

FALLBACK_PRIMARY_NOT_LOADED = "PRIMARY_NOT_LOADED"
FALLBACK_PRIMARY_INFERENCE_ERROR = "PRIMARY_INFERENCE_ERROR"
FALLBACK_REQUESTED = "REQUESTED"

EXPLAIN_METHOD = {
    "swin": "gradcam",
    "siglip2": "attention-pool",
    "mobilenetv2": "gradcam",
}


@dataclass(frozen=True)
class VisionRoute:
    role: str
    model_id: str
    model_version: str
    architecture: str
    model_role: str
    fallback_used: bool
    fallback_reason: str | None


def route_crop(
    settings: Settings,
    crop_code: str,
    model_role: str | None,
    *,
    primary_usable: bool,
    fallback_usable: bool,
    solanaceae_usable: bool,
) -> VisionRoute:
    if not crop_code:
        raise bad_request("crop_code is required")
    family = settings.crop_routes.get(crop_code)
    if family is None:
        raise unknown_crop(crop_code)

    if family == FAMILY_RICE:
        requested_fallback = model_role == "fallback"
        if model_role not in (None, "", "primary", "fallback"):
            raise bad_request("model_role must be 'primary' or 'fallback'")
        if requested_fallback:
            spec = settings.models.get(ROLE_VISION_RICE_FALLBACK)
            if spec is None or not fallback_usable:
                raise model_unavailable("vision.rice.fallback is not usable")
            return VisionRoute(
                role=spec.role,
                model_id=spec.model_id,
                model_version=spec.model_version,
                architecture=spec.architecture,
                model_role="fallback",
                fallback_used=True,
                fallback_reason=FALLBACK_REQUESTED,
            )
        if primary_usable and ROLE_VISION_RICE_PRIMARY in settings.models:
            spec = settings.models[ROLE_VISION_RICE_PRIMARY]
            return VisionRoute(
                role=spec.role,
                model_id=spec.model_id,
                model_version=spec.model_version,
                architecture=spec.architecture,
                model_role="primary",
                fallback_used=False,
                fallback_reason=None,
            )
        spec = settings.models.get(ROLE_VISION_RICE_FALLBACK)
        if spec is not None and fallback_usable:
            return VisionRoute(
                role=spec.role,
                model_id=spec.model_id,
                model_version=spec.model_version,
                architecture=spec.architecture,
                model_role="fallback",
                fallback_used=True,
                fallback_reason=FALLBACK_PRIMARY_NOT_LOADED,
            )
        raise model_unavailable("neither rice vision model is usable")

    spec = settings.models.get(ROLE_VISION_SOLANACEAE)
    if spec is None or not solanaceae_usable:
        raise model_unavailable("vision.solanaceae is not usable")
    return VisionRoute(
        role=spec.role,
        model_id=spec.model_id,
        model_version=spec.model_version,
        architecture=spec.architecture,
        model_role="primary",
        fallback_used=False,
        fallback_reason=None,
    )


def read_image(settings: Settings, data: bytes) -> str:
    if len(data) > settings.max_image_bytes:
        raise payload_too_large("image exceeds FOSHOL_SIDECAR_MAX_IMAGE_BYTES")
    media = sniff_image(data)
    if media is None or media not in ALLOWED_IMAGE_TYPES:
        raise unsupported_media("sniffed image type is not allowed")
    if not image_decodable(data, media):
        raise undecodable("image bytes could not be decoded")
    return media


def _clip_predictions(raw: list[Any], top_k: int) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    for row in raw:
        items.append(
            {
                "raw_label": str(row["raw_label"]),
                "confidence": round(float(row["confidence"]), 4),
                "rank": int(row.get("rank") or 0),
            }
        )
    items.sort(key=lambda p: p["confidence"], reverse=True)
    clipped = items[:top_k]
    for index, pred in enumerate(clipped, start=1):
        pred["rank"] = index
    return clipped


def classify_replay(
    settings: Settings,
    store: FixtureStore,
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
        fallback_usable=True,
        solanaceae_usable=True,
    )
    digest = sha256_hex(image)
    body = store.load_json(store.classify_path(digest))
    if body is None:
        raise fixture_missing(digest)
    predictions = _clip_predictions(body.get("predictions") or [], top_k)
    inference_ms = max(int((time.perf_counter() - started) * 1000), 0)
    return {
        "mode": "REPLAY",
        "crop_code": crop_code,
        "model_id": route.model_id,
        "model_version": route.model_version,
        "model_role": route.model_role,
        "fallback_used": route.fallback_used,
        "fallback_reason": route.fallback_reason,
        "architecture": route.architecture,
        "image_sha256": digest,
        "predictions": predictions,
        "inference_ms": inference_ms,
        "correlation_id": correlation_id,
    }


def explain_replay(
    settings: Settings,
    store: FixtureStore,
    image: bytes,
    crop_code: str,
    model_role: str | None,
    target_label: str | None,
) -> tuple[bytes, dict[str, str]]:
    read_image(settings, image)
    route = route_crop(
        settings,
        crop_code,
        model_role,
        primary_usable=True,
        fallback_usable=True,
        solanaceae_usable=True,
    )
    digest = sha256_hex(image)
    if target_label:
        classify_body = store.load_json(store.classify_path(digest))
        if classify_body is not None:
            labels = {str(row["raw_label"]) for row in classify_body.get("predictions") or []}
            if labels and target_label not in labels:
                raise bad_request("target_label is not in the routed model's label space")
    png = store.load_bytes(store.explain_path(digest))
    if png is None:
        raise fixture_missing(digest)
    method = EXPLAIN_METHOD.get(route.architecture, "gradcam")
    headers = {
        "X-Foshol-Model-Id": route.model_id,
        "X-Foshol-Model-Version": route.model_version,
        "X-Foshol-Target-Label": target_label or "",
        "X-Foshol-Method": method,
        "X-Foshol-Mode": "REPLAY",
        "Content-Type": "image/png",
    }
    return png, headers


def classify_live_unavailable() -> None:
    raise model_unavailable("vision weights are not loaded")


def explain_live_unavailable() -> None:
    raise model_unavailable("vision weights are not loaded")
