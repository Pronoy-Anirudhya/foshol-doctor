"""Shared pytest fixtures. Env is set before the FastAPI app is imported."""

from __future__ import annotations

import io
import os
import struct
import wave
import zlib
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "fixtures"

_MODEL_ENV = {
    "FOSHOL_AI_MODE": "replay",
    "FOSHOL_AI_VISION_RICE_MODEL_ID": "kssrikar4/Rice-Leaf-Disease-Classification",
    "FOSHOL_AI_VISION_RICE_MODEL_REVISION": "02a6e6ea1b5da9b0458b12c4ec8bccd0582a4f26",
    "FOSHOL_AI_VISION_RICE_FALLBACK_MODEL_ID": "prithivMLmods/Rice-Leaf-Disease",
    "FOSHOL_AI_VISION_RICE_FALLBACK_MODEL_REVISION": "170d10e070c308e0e5337690d67371825591f35b",
    "FOSHOL_AI_VISION_SOLANACEAE_MODEL_ID": "Daksh159/plant-disease-mobilenetv2",
    "FOSHOL_AI_VISION_SOLANACEAE_MODEL_REVISION": "d3fb2afc90da83086eff06e9088a889b6c43d4a6",
    "FOSHOL_AI_ASR_MODEL_ID": "ashrafulparan/whisper-small-bangla",
    "FOSHOL_AI_ASR_MODEL_REVISION": "25c88973563146654493b97882fb2806d2fdeaaa",
    "FOSHOL_AI_EMBED_MODEL_ID": "sentence-transformers/LaBSE",
    "FOSHOL_AI_EMBED_MODEL_REVISION": "836121a0533e5664b21c7aacc5d22951f2b8b25b",
    "FOSHOL_AI_VISION_CROP_ROUTES": "rice=rice,tomato=solanaceae,potato=solanaceae",
    "FOSHOL_SIDECAR_FIXTURE_DIR": str(FIXTURES),
}

for key, value in _MODEL_ENV.items():
    os.environ.setdefault(key, value)


def png_bytes(width: int = 1, height: int = 1, rgba: tuple[int, int, int, int] = (200, 16, 16, 255)) -> bytes:
    def chunk(tag: bytes, data: bytes) -> bytes:
        crc = zlib.crc32(tag + data) & 0xFFFFFFFF
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)

    raw = b"".join(b"\x00" + bytes(rgba) * width for _ in range(height))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def wav_bytes(duration_s: float = 0.05, rate: int = 16000) -> bytes:
    frames = int(rate * duration_s)
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(rate)
        handle.writeframes(b"\x00\x00" * frames)
    return buffer.getvalue()


@pytest.fixture
def rice_png() -> bytes:
    return (FIXTURES / "sources" / "synthetic-rice.png").read_bytes()


@pytest.fixture
def tomato_png() -> bytes:
    return (FIXTURES / "sources" / "synthetic-tomato.png").read_bytes()


@pytest.fixture
def client():
    from fastapi.testclient import TestClient

    from app.main import app

    with TestClient(app) as test_client:
        yield test_client
