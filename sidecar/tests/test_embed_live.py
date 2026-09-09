"""LIVE embeddings: mocked LaBSE runtime, SHA-256 cache. No Hugging Face download."""

from __future__ import annotations

import math
from dataclasses import dataclass

import pytest

from app.config import reset_settings
from app.embed import (
    EMBED_DIM,
    EmbedCache,
    embed_live,
    hash_unit_vector,
    install_live_embed,
    require_embed_dim,
)
from app.errors import ERR_SIDECAR_EMBED_DIM_MISMATCH, ERR_SIDECAR_PAYLOAD_TOO_LARGE, SidecarError
from app.live_vision import install_live_vision
from app.text import normalise_for_embed

EMBED_ID = "sentence-transformers/LaBSE"
EMBED_REV = "836121a0533e5664b21c7aacc5d22951f2b8b25b"
VIT_ID = "wambugu71/crop_leaf_diseases_vit"
VIT_REV = "7d5b32bcd6f83a2f57e7e0346358fad276296877"


@dataclass
class FakeEmbedRuntime:
    model_id: str = EMBED_ID
    model_version: str = EMBED_REV
    dimension: int = EMBED_DIM
    calls: int = 0

    def embed(self, texts: list[str]) -> list[list[float]]:
        self.calls += 1
        return [hash_unit_vector(text) for text in texts]


@dataclass
class FakeVisionRuntime:
    model_id: str = VIT_ID
    model_version: str = VIT_REV
    architecture: str = "vit"
    labels: list[str] | None = None

    def __post_init__(self) -> None:
        if self.labels is None:
            self.labels = ["Rice___Leaf_Blast"]

    def predict(self, image_bytes: bytes) -> list[tuple[str, float]]:
        return [("Rice___Leaf_Blast", 0.9)]


@dataclass
class FakeAsrRuntime:
    model_id: str = "ashrafulparan/whisper-small-bangla"
    model_version: str = "25c88973563146654493b97882fb2806d2fdeaaa"

    def transcribe(self, samples: list[float], language: str) -> list[dict]:
        return []


@pytest.fixture
def live_env(monkeypatch):
    monkeypatch.setenv("FOSHOL_AI_MODE", "live")
    monkeypatch.setenv("FOSHOL_AI_VISION_RICE_MODEL_ID", VIT_ID)
    monkeypatch.setenv("FOSHOL_AI_VISION_RICE_MODEL_REVISION", VIT_REV)
    reset_settings()
    yield
    reset_settings()


def test_embed_live_768d_l2_and_cache(live_env):
    from app.config import get_settings

    settings = get_settings()
    runtime = FakeEmbedRuntime()
    cache = EmbedCache()
    first = embed_live(settings, runtime, cache, ["পাতা"], "corr-1")
    second = embed_live(settings, runtime, cache, ["পাতা"], "corr-2")
    assert first["mode"] == "LIVE"
    assert first["model_id"] == EMBED_ID
    assert first["model_version"] == EMBED_REV
    assert first["dimension"] == EMBED_DIM
    assert first["normalised"] is True
    assert len(first["embeddings"]) == 1
    vector = first["embeddings"][0]
    assert len(vector) == EMBED_DIM
    norm = math.sqrt(sum(v * v for v in vector))
    assert abs(norm - 1.0) < 1e-6
    assert runtime.calls == 1
    assert second["embeddings"][0] == first["embeddings"][0]


def test_embed_live_nfc_nfd_share_cache(live_env):
    from app.config import get_settings

    settings = get_settings()
    runtime = FakeEmbedRuntime()
    cache = EmbedCache()
    nfd = "cafe\u0301"
    nfc = "caf\u00e9"
    assert nfd != nfc
    assert normalise_for_embed(nfd) == normalise_for_embed(nfc)
    a = embed_live(settings, runtime, cache, [nfc], "corr-n1")
    b = embed_live(settings, runtime, cache, [nfd], "corr-n2")
    assert a["embeddings"][0] == b["embeddings"][0]
    assert runtime.calls == 1


def test_embed_live_batch_too_large(live_env):
    from app.config import get_settings

    settings = get_settings()
    with pytest.raises(SidecarError) as raised:
        embed_live(settings, FakeEmbedRuntime(), EmbedCache(), ["x"] * 33, "corr-b")
    assert raised.value.code == ERR_SIDECAR_PAYLOAD_TOO_LARGE
    assert raised.value.status == 413


def test_require_embed_dim_mismatch_exits():
    with pytest.raises(SystemExit) as raised:
        require_embed_dim(512)
    assert ERR_SIDECAR_EMBED_DIM_MISMATCH in str(raised.value)


def test_install_live_embed_marks_loaded(live_env, monkeypatch):
    from app.config import REGISTRY_ROLES, get_settings

    fake = FakeEmbedRuntime()
    monkeypatch.setattr("app.embed.load_embed_runtime", lambda spec, threads: fake)

    class State:
        def __init__(self):
            self.settings = get_settings()
            self.loaded_flags = {role: False for role in REGISTRY_ROLES}
            self.loaded_flags["vision.rice.primary"] = True
            self.loaded_flags["asr.bangla"] = True
            self.loaded_at = {role: None for role in REGISTRY_ROLES}
            self.labels = {role: [] for role in REGISTRY_ROLES}
            self.models_loaded = 2
            self.degraded_reasons = [
                "vision.rice.fallback not loaded",
                "vision.solanaceae not loaded",
                "embed.text not loaded",
            ]
            self.embed_runtime = None
            self.embed_cache = None

    state = State()
    install_live_embed(state)
    assert state.embed_runtime is fake
    assert state.loaded_flags["embed.text"] is True
    assert state.models_loaded == 3
    assert "embed.text not loaded" not in state.degraded_reasons
    assert "vision.solanaceae not loaded" in state.degraded_reasons


def test_live_installers_load_vit_asr_embed_only(live_env, monkeypatch):
    from app.asr import install_live_asr
    from app.config import REGISTRY_ROLES, get_settings

    monkeypatch.setattr("app.live_vision.load_runtime", lambda spec, threads: FakeVisionRuntime())
    monkeypatch.setattr("app.asr.load_asr_runtime", lambda spec, threads: FakeAsrRuntime())
    monkeypatch.setattr("app.embed.load_embed_runtime", lambda spec, threads: FakeEmbedRuntime())

    class State:
        def __init__(self):
            self.settings = get_settings()
            self.loaded_flags = {role: False for role in REGISTRY_ROLES}
            self.loaded_at = {role: None for role in REGISTRY_ROLES}
            self.labels = {role: [] for role in REGISTRY_ROLES}
            self.models_loaded = 0
            self.degraded_reasons = []
            self.vision_runtime = None
            self.classify_cache = None
            self.asr_runtime = None
            self.asr_cache = None
            self.embed_runtime = None
            self.embed_cache = None

    state = State()
    install_live_vision(state)
    install_live_asr(state)
    install_live_embed(state)
    assert state.models_loaded == 3
    assert state.loaded_flags["vision.rice.primary"] is True
    assert state.loaded_flags["asr.bangla"] is True
    assert state.loaded_flags["embed.text"] is True
    assert state.loaded_flags["vision.rice.fallback"] is False
    assert state.loaded_flags["vision.solanaceae"] is False
    assert state.degraded_reasons == [
        "vision.rice.fallback not loaded",
        "vision.solanaceae not loaded",
    ]
