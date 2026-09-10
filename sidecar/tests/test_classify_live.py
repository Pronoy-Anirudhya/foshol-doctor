"""LIVE classify: mocked vision runtime, LRU cache, LIVE crop routes. No Hugging Face download."""

from __future__ import annotations

from dataclasses import dataclass, field

import pytest

from app.config import VISIONARY_MODEL_ID, reset_settings
from app.errors import SidecarError
from app.live_vision import (
    EFFICIENTNET_LABELS,
    ClassifyCache,
    assert_backend_matches_model,
    classify_live,
    install_live_vision,
)
from app.vision import route_crop
from tests.conftest import png_bytes

VIT_ID = "wambugu71/crop_leaf_diseases_vit"
VIT_REV = "7d5b32bcd6f83a2f57e7e0346358fad276296877"
VISIONARY_REV = "63080391f7d2bdb331ab356b0d1d9b4b603b3946"
LIVE_ROUTES = "rice=rice,tomato=rice,potato=rice,corn=rice,wheat=rice"


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
    monkeypatch.setenv("FOSHOL_SIDECAR_VISION_BACKEND", "vit")
    reset_settings()
    yield
    reset_settings()


def test_live_routes_vit_crops_including_tomato(live_env):
    from app.config import get_settings

    settings = get_settings()
    for crop in ("rice", "tomato", "potato", "corn", "wheat"):
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
            "banana",
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
    monkeypatch.setattr("app.live_vision.load_runtime", lambda spec, threads, backend="vit": fake)

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


def test_vision_backend_defaults_to_vit(live_env):
    from app.config import BACKEND_VIT, get_settings

    assert get_settings().vision_backend == BACKEND_VIT


def test_vision_backend_rejects_unknown(monkeypatch):
    monkeypatch.setenv("FOSHOL_AI_MODE", "live")
    monkeypatch.setenv("FOSHOL_AI_VISION_CROP_ROUTES", LIVE_ROUTES)
    monkeypatch.setenv("FOSHOL_AI_VISION_RICE_MODEL_ID", VIT_ID)
    monkeypatch.setenv("FOSHOL_AI_VISION_RICE_MODEL_REVISION", VIT_REV)
    monkeypatch.setenv("FOSHOL_SIDECAR_VISION_BACKEND", "both")
    reset_settings()
    with pytest.raises(SystemExit, match="vit' or 'visionary"):
        from app.config import load_settings

        load_settings()
    reset_settings()


def test_backend_mismatch_is_refused():
    with pytest.raises(SystemExit, match="requires VisionaryQuant"):
        assert_backend_matches_model("visionary", VIT_ID)
    with pytest.raises(SystemExit, match="cannot load"):
        assert_backend_matches_model("vit", VISIONARY_MODEL_ID)
    assert_backend_matches_model("vit", VIT_ID)
    assert_backend_matches_model("visionary", VISIONARY_MODEL_ID)
    assert len(EFFICIENTNET_LABELS) == 17
    assert EFFICIENTNET_LABELS[3] == "Corn___Northern_Leaf_Blight"
    assert EFFICIENTNET_LABELS[16] == "Wheat___Yellow_Rust"


def test_efficientnet_head_matches_timm_checkpoint_keys():
    pytest.importorskip("timm")
    import timm
    import torch.nn as nn

    model = timm.create_model("efficientnet_b3", pretrained=False)
    in_features = model.classifier.in_features
    model.classifier = nn.Sequential(nn.Linear(in_features, 17))
    keys = model.state_dict().keys()
    assert "conv_stem.weight" in keys
    assert "classifier.0.weight" in keys
    assert "features.0.0.weight" not in keys
    assert "classifier.1.weight" not in keys


@pytest.fixture
def visionary_env(monkeypatch):
    monkeypatch.setenv("FOSHOL_AI_MODE", "live")
    monkeypatch.setenv("FOSHOL_AI_VISION_CROP_ROUTES", LIVE_ROUTES)
    monkeypatch.setenv("FOSHOL_AI_VISION_RICE_MODEL_ID", VISIONARY_MODEL_ID)
    monkeypatch.setenv("FOSHOL_AI_VISION_RICE_MODEL_REVISION", VISIONARY_REV)
    monkeypatch.setenv("FOSHOL_SIDECAR_VISION_BACKEND", "visionary")
    reset_settings()
    yield
    reset_settings()


def test_classify_live_reports_efficientnet_native_labels(visionary_env):
    from app.config import get_settings

    @dataclass
    class FakeEfficientNet:
        model_id: str = VISIONARY_MODEL_ID
        model_version: str = VISIONARY_REV
        architecture: str = "efficientnet_b3"
        labels: list[str] = field(default_factory=lambda: list(EFFICIENTNET_LABELS))
        calls: int = 0

        def predict(self, image_bytes: bytes) -> list[tuple[str, float]]:
            self.calls += 1
            return [
                ("Rice___Leaf_Blast", 0.88),
                ("Rice___Neck_Blast", 0.07),
                ("Rice___Healthy", 0.02),
            ]

    settings = get_settings()
    assert settings.vision_backend == "visionary"
    assert settings.models["vision.rice.primary"].architecture == "efficientnet_b3"
    runtime = FakeEfficientNet()
    cache = ClassifyCache()
    image = png_bytes()
    body = classify_live(settings, runtime, cache, image, "rice", 3, None, "corr-eff")
    assert body["mode"] == "LIVE"
    assert body["model_id"] == VISIONARY_MODEL_ID
    assert body["model_version"] == VISIONARY_REV
    assert body["architecture"] == "efficientnet_b3"
    assert body["predictions"][0]["raw_label"] == "Rice___Leaf_Blast"
    assert body["predictions"][1]["raw_label"] == "Rice___Neck_Blast"
    assert runtime.calls == 1


def test_install_live_vision_loads_only_one_runtime(visionary_env, monkeypatch):
    from app.config import REGISTRY_ROLES, get_settings

    loaded: list[tuple[str, str]] = []

    def fake_load(spec, threads, backend="vit"):
        loaded.append((spec.model_id, backend))

        @dataclass
        class Runtime:
            model_id: str = spec.model_id
            model_version: str = spec.model_version
            architecture: str = "efficientnet_b3"
            labels: list[str] = field(default_factory=lambda: list(EFFICIENTNET_LABELS))

            def predict(self, image_bytes: bytes) -> list[tuple[str, float]]:
                return [("Rice___Healthy", 1.0)]

        return Runtime()

    monkeypatch.setattr("app.live_vision.load_runtime", fake_load)

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
    assert loaded == [(VISIONARY_MODEL_ID, "visionary")]
    assert state.models_loaded == 1
    assert state.loaded_flags["vision.rice.primary"] is True
    assert state.loaded_flags["vision.solanaceae"] is False
    assert state.vision_runtime.architecture == "efficientnet_b3"
    assert len(state.labels["vision.rice.primary"]) == 17
