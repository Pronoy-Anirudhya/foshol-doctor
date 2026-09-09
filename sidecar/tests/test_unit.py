"""Unit tests that do not need the HTTP app."""

from __future__ import annotations

import os

import pytest

from app.asr import asr_confidence
from app.config import reset_settings
from app.errors import STATUS_BY_CODE
from app.sniff import sniff_audio, sniff_image
from app.text import normalise_for_embed
from app.vision import FALLBACK_REQUESTED, route_crop
from tests.conftest import png_bytes, wav_bytes


def test_sniff_png_and_reject_text() -> None:
    assert sniff_image(png_bytes()) == "image/png"
    assert sniff_image(b"hello") is None
    assert sniff_audio(wav_bytes()) == "audio/wav"
    assert sniff_audio(b"ID3") is None


def test_normalise_nfc_zwnj_digits() -> None:
    raw = "পাতা\u200c ১২"
    assert "\u200c" not in normalise_for_embed(raw)
    assert normalise_for_embed(raw).endswith(" 12")
    nfd = "চাষ"
    assert normalise_for_embed(nfd) == normalise_for_embed("চাষ")


def test_asr_confidence_formula() -> None:
    segments = [
        {"start_ms": 0, "end_ms": 1000, "avg_logprob": -0.2},
        {"start_ms": 1000, "end_ms": 3000, "avg_logprob": -0.5},
    ]
    mean = ((-0.2) * 1000 + (-0.5) * 2000) / 3000
    import math

    expected = round(min(max(math.exp(mean), 0.0), 1.0), 3)
    assert asr_confidence(segments) == expected
    assert asr_confidence([]) == 0.0


def test_error_codes_have_status() -> None:
    assert STATUS_BY_CODE["ERR_SIDECAR_UNKNOWN_CROP"] == 400
    assert STATUS_BY_CODE["ERR_SIDECAR_FIXTURE_MISSING"] == 404
    assert STATUS_BY_CODE["ERR_SIDECAR_MODEL_UNAVAILABLE"] == 503
    assert STATUS_BY_CODE["ERR_SIDECAR_BUSY"] == 429


def test_route_unknown_crop() -> None:
    from app.config import get_settings
    from app.errors import SidecarError

    settings = get_settings()
    with pytest.raises(SidecarError) as raised:
        route_crop(
            settings,
            "banana",
            None,
            primary_usable=True,
            fallback_usable=True,
            solanaceae_usable=True,
        )
    assert raised.value.code == "ERR_SIDECAR_UNKNOWN_CROP"


def test_route_rice_fallback_requested() -> None:
    from app.config import get_settings

    settings = get_settings()
    route = route_crop(
        settings,
        "rice",
        "fallback",
        primary_usable=True,
        fallback_usable=True,
        solanaceae_usable=True,
    )
    assert route.fallback_used is True
    assert route.fallback_reason == FALLBACK_REQUESTED
    assert route.model_id == "prithivMLmods/Rice-Leaf-Disease"


def test_missing_asr_id_exits(monkeypatch) -> None:
    from app.config import load_settings

    monkeypatch.delenv("FOSHOL_AI_ASR_MODEL_ID", raising=False)
    monkeypatch.delenv("FOSHOL_ASR_MODEL_ID", raising=False)
    monkeypatch.setenv("FOSHOL_AI_ASR_MODEL_ID", "")
    reset_settings()
    with pytest.raises(SystemExit) as raised:
        load_settings()
    assert "asr.bangla" in str(raised.value)
    reset_settings()
    # Restore for later tests in this process.
    os.environ["FOSHOL_AI_ASR_MODEL_ID"] = "ashrafulparan/whisper-small-bangla"
