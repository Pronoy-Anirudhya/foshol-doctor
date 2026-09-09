"""LIVE classify: mocked ViT runtime, LRU cache, LIVE crop routes. No Hugging Face download."""

from __future__ import annotations

from dataclasses import dataclass, field

import pytest

from app.config import reset_settings
from app.errors import SidecarError
from app.live_vision import ClassifyCache, classify_live, install_live_vision
from app.vision import route_crop
from tests.conftest import png_bytes

VIT_ID = "wambugu71/crop_leaf_diseases_vit"
VIT_REV = "7d5b32bcd6f83a2f57e7e0346358fad276296877"
LIVE_ROUTES = "rice=rice,potato=rice,corn=rice,wheat=rice"


@dataclass
class FakeRuntime:
    model_id: str = VIT_ID
    model_version: str = VIT_REV
    architecture: str = "vit"
    labels: list[str] = field(default_factory=lambda: ["Rice___Leaf_Blast", "Rice___Healthy"])
    calls: int = 0

    def predict(self, image_bytes: bytes) -> list[tuple[str, float]]:
        self.calls += 1
        return [("Rice___Leaf_Blast", 0.91), ("Rice___Healthy", 0.05), ("Invalid", 0.01)]


@pytest.fixture
def live_env(monkeypatch):
    monkeypatch.setenv("FOSHOL_AI_MODE", "live")
    monkeypatch.setenv("FOSHOL_AI_VISION_CROP_ROUTES", LIVE_ROUTES)
    monkeypatch.setenv("FOSHOL_AI_VISION_RICE_MODEL_ID", VIT_ID)
    monkeypatch.setenv("FOSHOL_AI_VISION_RICE_MODEL_REVISION", VIT_REV)
    reset_settings()
    yield
    reset_settings()


def test_live_routes_vit_crops_and_rejects_tomato(live_env):
    from app.config import get_settings

    settings = get_settings()
    for crop in ("rice", "potato", "corn", "wheat"):
        route = route_crop(
            settings,
            crop,
            None,
            primary_usable=True,
            fallback_usable=False,
            solanaceae_usable=False,
        )
        assert route.role == "vision.rice.primary"
        assert route.model_id == VIT_ID
    with pytest.raises(SidecarError) as raised:
        route_crop(
            settings,
            "tomato",
            None,
            primary_usable=True,
            fallback_usable=False,
            solanaceae_usable=False,
        )
    assert raised.value.code == "ERR_SIDECAR_UNKNOWN_CROP"


def test_classify_live_reports_vit_and_caches(live_env):
    from app.config import get_settings

    settings = get_settings()
    runtime = FakeRuntime()
    cache = ClassifyCache()
    image = png_bytes()
    first = classify_live(settings, runtime, cache, image, "rice", 3, None, "corr-1")
    second = classify_live(settings, runtime, cache, image, "rice", 3, None, "corr-2")
    assert first["mode"] == "LIVE"
    assert first["model_id"] == VIT_ID
    assert first["model_version"] == VIT_REV
    assert first["architecture"] == "vit"
    assert first["image_sha256"]
    assert first["predictions"][0]["raw_label"] == "Rice___Leaf_Blast"
    assert first["predictions"][0]["rank"] == 1
    assert runtime.calls == 1
    assert [row["raw_label"] for row in second["predictions"]] == [
        row["raw_label"] for row in first["predictions"]
    ]


def test_install_live_vision_loads_only_vit(live_env, monkeypatch):
    from app.config import REGISTRY_ROLES, get_settings

    fake = FakeRuntime()
    monkeypatch.setattr("app.live_vision.load_runtime", lambda spec, threads: fake)

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

    state = State()
    install_live_vision(state)
    assert state.models_loaded == 1
    assert state.loaded_flags["vision.rice.primary"] is True
    assert state.loaded_flags["vision.solanaceae"] is False
    assert state.loaded_flags["asr.bangla"] is False
    assert state.vision_runtime is fake
    assert "vision.solanaceae not loaded" in state.degraded_reasons
