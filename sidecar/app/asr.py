"""Bangla ASR: replay fixtures, live-mode unavailability, confidence formula."""

from __future__ import annotations

import math
import time
from typing import Any

from app.config import ROLE_ASR_BANGLA, Settings
from app.errors import (
    fixture_missing,
    model_unavailable,
    payload_too_large,
    undecodable,
    unsupported_media,
)
from app.replay import FixtureStore, sha256_hex
from app.sniff import ALLOWED_AUDIO_TYPES, sniff_audio, wav_duration_seconds


def asr_confidence(segments: list[dict[str, Any]]) -> float:
    """Duration-weighted exp(mean logprob), clamped to [0,1], three decimal places."""
    if not segments:
        return 0.0
    weighted = 0.0
    total = 0.0
    for segment in segments:
        duration = max(int(segment["end_ms"]) - int(segment["start_ms"]), 0)
        weighted += float(segment["avg_logprob"]) * duration
        total += duration
    if total <= 0:
        return 0.0
    value = math.exp(weighted / total)
    return round(min(max(value, 0.0), 1.0), 3)


def read_audio(settings: Settings, data: bytes) -> str:
    if len(data) > settings.max_audio_bytes:
        raise payload_too_large("audio exceeds FOSHOL_SIDECAR_MAX_AUDIO_BYTES")
    media = sniff_audio(data)
    if media is None or media not in ALLOWED_AUDIO_TYPES:
        raise unsupported_media("sniffed audio type is not allowed")
    duration = wav_duration_seconds(data)
    if duration is not None and duration > settings.max_audio_seconds:
        raise payload_too_large("audio exceeds FOSHOL_SIDECAR_MAX_AUDIO_SECONDS")
    if media == "audio/wav" and duration is None:
        raise undecodable("WAV bytes could not be decoded")
    return media


def transcribe_replay(
    settings: Settings,
    store: FixtureStore,
    audio: bytes,
    language: str | None,
    correlation_id: str,
) -> dict[str, Any]:
    started = time.perf_counter()
    read_audio(settings, audio)
    spec = settings.models[ROLE_ASR_BANGLA]
    digest = sha256_hex(audio)
    body = store.load_json(store.transcribe_path(digest))
    if body is None:
        raise fixture_missing(digest)
    segments = list(body.get("segments") or [])
    speech = bool(body.get("speech_detected", bool(segments)))
    transcript = str(body.get("transcript") or "")
    if not speech:
        transcript = ""
        confidence = 0.0
    elif "confidence" in body:
        confidence = round(float(body["confidence"]), 3)
    else:
        confidence = asr_confidence(segments)
    inference_ms = max(int((time.perf_counter() - started) * 1000), 0)
    return {
        "mode": "REPLAY",
        "model_id": spec.model_id,
        "model_version": spec.model_version,
        "audio_sha256": digest,
        "language": language or settings.asr_language,
        "duration_ms": int(body.get("duration_ms") or 0),
        "sample_rate_hz": int(body.get("sample_rate_hz") or 16000),
        "speech_detected": speech,
        "transcript": transcript,
        "confidence": confidence,
        "segments": segments,
        "inference_ms": inference_ms,
        "correlation_id": correlation_id,
    }


def transcribe_live_unavailable() -> None:
    raise model_unavailable("asr weights are not loaded")
