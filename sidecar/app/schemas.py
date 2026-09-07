"""Pydantic request and response models for the sidecar HTTP surface."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class Prediction(BaseModel):
    raw_label: str
    confidence: float
    rank: int


class ClassifyResponse(BaseModel):
    mode: Literal["REPLAY", "LIVE"]
    crop_code: str
    model_id: str
    model_version: str
    model_role: Literal["primary", "fallback"]
    fallback_used: bool
    fallback_reason: str | None = None
    architecture: str
    image_sha256: str
    predictions: list[Prediction]
    inference_ms: int
    correlation_id: str


class AsrSegment(BaseModel):
    start_ms: int
    end_ms: int
    avg_logprob: float
    no_speech_prob: float


class TranscribeResponse(BaseModel):
    mode: Literal["REPLAY", "LIVE"]
    model_id: str
    model_version: str
    audio_sha256: str
    language: str
    duration_ms: int
    sample_rate_hz: int
    speech_detected: bool
    transcript: str
    confidence: float
    segments: list[AsrSegment]
    inference_ms: int
    correlation_id: str


class EmbedRequest(BaseModel):
    texts: list[str] = Field(min_length=1)


class EmbedResponse(BaseModel):
    mode: Literal["REPLAY", "LIVE"]
    model_id: str
    model_version: str
    dimension: int
    normalised: bool
    embeddings: list[list[float]]
    inference_ms: int
    correlation_id: str


class HealthResponse(BaseModel):
    status: str
    mode: str
    warm: bool
    models_loaded: int
    models_expected: int
    degraded_reasons: list[str]


class ModelRoleView(BaseModel):
    role: str
    model_id: str
    model_version: str
    architecture: str
    source: str
    loaded: bool
    warm: bool
    num_labels: int
    loaded_at: str | None
    labels: list[str] = Field(default_factory=list)


class ModelsResponse(BaseModel):
    mode: str
    models: list[ModelRoleView]
    inference_ms: int
    correlation_id: str
