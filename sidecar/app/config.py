"""Sidecar configuration. The only module that reads environment variables (SIDECAR-NFR-020)."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

ROLE_VISION_RICE_PRIMARY = "vision.rice.primary"
ROLE_VISION_RICE_FALLBACK = "vision.rice.fallback"
ROLE_VISION_SOLANACEAE = "vision.solanaceae"
ROLE_ASR_BANGLA = "asr.bangla"
ROLE_EMBED_TEXT = "embed.text"

REGISTRY_ROLES = (
    ROLE_VISION_RICE_PRIMARY,
    ROLE_VISION_RICE_FALLBACK,
    ROLE_VISION_SOLANACEAE,
    ROLE_ASR_BANGLA,
    ROLE_EMBED_TEXT,
)

ARCHITECTURE_BY_ROLE = {
    ROLE_VISION_RICE_PRIMARY: "swin",
    ROLE_VISION_RICE_FALLBACK: "siglip2",
    ROLE_VISION_SOLANACEAE: "mobilenetv2",
    ROLE_ASR_BANGLA: "whisper",
    ROLE_EMBED_TEXT: "sentence-transformer",
}

FAMILY_RICE = "rice"
FAMILY_SOLANACEAE = "solanaceae"

_ALIASES: dict[str, tuple[str, ...]] = {
    "FOSHOL_AI_VISION_RICE_MODEL_ID": ("FOSHOL_RICE_MODEL_ID",),
    "FOSHOL_AI_VISION_RICE_MODEL_REVISION": ("FOSHOL_RICE_MODEL_REVISION",),
    "FOSHOL_AI_VISION_RICE_FALLBACK_MODEL_ID": ("FOSHOL_RICE_FALLBACK_MODEL_ID",),
    "FOSHOL_AI_VISION_RICE_FALLBACK_MODEL_REVISION": ("FOSHOL_RICE_FALLBACK_MODEL_REVISION",),
    "FOSHOL_AI_VISION_SOLANACEAE_MODEL_ID": ("FOSHOL_SOLANACEAE_MODEL_ID",),
    "FOSHOL_AI_VISION_SOLANACEAE_MODEL_REVISION": ("FOSHOL_SOLANACEAE_MODEL_REVISION",),
    "FOSHOL_AI_ASR_MODEL_ID": ("FOSHOL_ASR_MODEL_ID",),
    "FOSHOL_AI_ASR_MODEL_REVISION": ("FOSHOL_ASR_MODEL_REVISION",),
    "FOSHOL_AI_EMBED_MODEL_ID": ("FOSHOL_EMBED_MODEL_ID",),
    "FOSHOL_AI_EMBED_MODEL_REVISION": ("FOSHOL_EMBED_MODEL_REVISION",),
}

_REQUIRED_BY_ROLE: dict[str, tuple[str, str]] = {
    ROLE_VISION_RICE_PRIMARY: (
        "FOSHOL_AI_VISION_RICE_MODEL_ID",
        "FOSHOL_AI_VISION_RICE_MODEL_REVISION",
    ),
    ROLE_VISION_RICE_FALLBACK: (
        "FOSHOL_AI_VISION_RICE_FALLBACK_MODEL_ID",
        "FOSHOL_AI_VISION_RICE_FALLBACK_MODEL_REVISION",
    ),
    ROLE_VISION_SOLANACEAE: (
        "FOSHOL_AI_VISION_SOLANACEAE_MODEL_ID",
        "FOSHOL_AI_VISION_SOLANACEAE_MODEL_REVISION",
    ),
    ROLE_ASR_BANGLA: ("FOSHOL_AI_ASR_MODEL_ID", "FOSHOL_AI_ASR_MODEL_REVISION"),
    ROLE_EMBED_TEXT: ("FOSHOL_AI_EMBED_MODEL_ID", "FOSHOL_AI_EMBED_MODEL_REVISION"),
}

_settings: "Settings | None" = None


def _read_env(name: str, default: str | None = None) -> str | None:
    if name in os.environ:
        return os.environ[name]
    for alias in _ALIASES.get(name, ()):
        if alias in os.environ:
            return os.environ[alias]
    return default


def _read_int(name: str, default: int) -> int:
    raw = _read_env(name)
    if raw is None or raw == "":
        return default
    return int(raw)


def _read_float(name: str, default: float) -> float:
    raw = _read_env(name)
    if raw is None or raw == "":
        return default
    return float(raw)


def _default_fixture_dir() -> Path:
    return Path(__file__).resolve().parent.parent / "fixtures"


def parse_crop_routes(raw: str) -> dict[str, str]:
    routes: dict[str, str] = {}
    for part in raw.split(","):
        token = part.strip()
        if not token:
            continue
        if "=" not in token:
            raise SystemExit(f"FOSHOL_AI_VISION_CROP_ROUTES is malformed: {token!r}")
        crop_code, family = token.split("=", 1)
        crop = crop_code.strip()
        fam = family.strip()
        if not crop or fam not in (FAMILY_RICE, FAMILY_SOLANACEAE):
            raise SystemExit(f"FOSHOL_AI_VISION_CROP_ROUTES has an unusable mapping: {token!r}")
        routes[crop] = fam
    if not routes:
        raise SystemExit("FOSHOL_AI_VISION_CROP_ROUTES must contain at least one mapping")
    return routes


@dataclass(frozen=True)
class ModelSpec:
    role: str
    model_id: str
    model_version: str
    architecture: str


@dataclass(frozen=True)
class Settings:
    mode: str
    crop_routes: dict[str, str]
    vision_top_k: int
    max_concurrent: int
    queue_timeout_ms: int
    max_image_bytes: int
    max_audio_bytes: int
    max_audio_seconds: int
    asr_language: str
    asr_target_lufs: float
    explain_alpha: float
    explain_max_edge_px: int
    embed_max_batch: int
    embed_max_chars: int
    torch_threads: int
    fixture_dir: Path
    models: dict[str, ModelSpec]

    @property
    def mode_header(self) -> str:
        return self.mode.upper()

    @property
    def is_replay(self) -> bool:
        return self.mode == "replay"

    @property
    def is_live(self) -> bool:
        return self.mode == "live"


def _require(role: str, id_key: str, rev_key: str) -> tuple[str, str]:
    model_id = (_read_env(id_key) or "").strip()
    revision = (_read_env(rev_key) or "").strip()
    missing: list[str] = []
    if not model_id:
        missing.append(id_key)
    if not revision:
        missing.append(rev_key)
    if missing:
        joined = ", ".join(missing)
        raise SystemExit(f"required model identifier or revision unset for {role}: {joined}")
    return model_id, revision


def load_settings() -> Settings:
    mode_raw = (_read_env("FOSHOL_AI_MODE", "replay") or "replay").strip().lower()
    if mode_raw not in ("replay", "live"):
        raise SystemExit("FOSHOL_AI_MODE must be 'replay' or 'live'")

    routes_raw = _read_env(
        "FOSHOL_AI_VISION_CROP_ROUTES",
        "rice=rice,tomato=solanaceae,potato=solanaceae",
    )
    crop_routes = parse_crop_routes(routes_raw)

    models: dict[str, ModelSpec] = {}
    for role, (id_key, rev_key) in _REQUIRED_BY_ROLE.items():
        if role == ROLE_VISION_RICE_PRIMARY:
            model_id = (_read_env(id_key) or "").strip()
            revision = (_read_env(rev_key) or "").strip()
            if not model_id or not revision:
                # Primary rice may be absent; fallback covers rice (SIDECAR-FR-020).
                continue
            models[role] = ModelSpec(role, model_id, revision, ARCHITECTURE_BY_ROLE[role])
            continue
        model_id, revision = _require(role, id_key, rev_key)
        models[role] = ModelSpec(role, model_id, revision, ARCHITECTURE_BY_ROLE[role])

    if ROLE_VISION_RICE_PRIMARY not in models and ROLE_VISION_RICE_FALLBACK not in models:
        raise SystemExit(
            "required model identifier or revision unset for vision.rice.primary "
            "(and vision.rice.fallback is also missing)"
        )

    fixture_raw = _read_env("FOSHOL_SIDECAR_FIXTURE_DIR")
    fixture_dir = Path(fixture_raw) if fixture_raw else _default_fixture_dir()

    return Settings(
        mode=mode_raw,
        crop_routes=crop_routes,
        vision_top_k=_read_int("FOSHOL_SIDECAR_VISION_TOP_K", 5),
        max_concurrent=_read_int("FOSHOL_SIDECAR_MAX_CONCURRENT", 2),
        queue_timeout_ms=_read_int("FOSHOL_SIDECAR_QUEUE_TIMEOUT_MS", 2000),
        max_image_bytes=_read_int("FOSHOL_SIDECAR_MAX_IMAGE_BYTES", 8_388_608),
        max_audio_bytes=_read_int("FOSHOL_SIDECAR_MAX_AUDIO_BYTES", 4_194_304),
        max_audio_seconds=_read_int("FOSHOL_SIDECAR_MAX_AUDIO_SECONDS", 30),
        asr_language=(_read_env("FOSHOL_SIDECAR_ASR_LANGUAGE", "bn") or "bn"),
        asr_target_lufs=_read_float("FOSHOL_SIDECAR_ASR_TARGET_LUFS", -23.0),
        explain_alpha=_read_float("FOSHOL_SIDECAR_EXPLAIN_ALPHA", 0.45),
        explain_max_edge_px=_read_int("FOSHOL_SIDECAR_EXPLAIN_MAX_EDGE_PX", 1024),
        embed_max_batch=_read_int("FOSHOL_SIDECAR_EMBED_MAX_BATCH", 32),
        embed_max_chars=_read_int("FOSHOL_SIDECAR_EMBED_MAX_CHARS", 2000),
        torch_threads=_read_int("FOSHOL_SIDECAR_TORCH_THREADS", 4),
        fixture_dir=fixture_dir,
        models=models,
    )


def get_settings() -> Settings:
    global _settings
    if _settings is None:
        _settings = load_settings()
    return _settings


def reset_settings() -> None:
    global _settings
    _settings = None
