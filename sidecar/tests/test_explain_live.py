"""LIVE explain: Grad-CAM overlay PNG, headers, label check, unavailable role."""

from __future__ import annotations

from io import BytesIO
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

pytest.importorskip("PIL")
from PIL import Image, ImageDraw

from app.config import reset_settings
from app.errors import ERR_SIDECAR_BAD_REQUEST, ERR_SIDECAR_MODEL_UNAVAILABLE
from app.live_vision import (
    ClassifyCache,
    EfficientNetRuntime,
    ExplainCache,
    TransformersRuntime,
    compose_overlay,
    explain_live,
)
from tests.conftest import png_bytes

VIT_ID = "wambugu71/crop_leaf_diseases_vit"
VIT_REV = "7d5b32bcd6f83a2f57e7e0346358fad276296877"
TINY_LABELS = ["Rice___Leaf_Blast", "Rice___Healthy", "Rice___Brown_Spot"]


def _blob(width: int, height: int) -> bytes:
    image = Image.new("RGB", (width, height), (12, 90, 18))
    ImageDraw.Draw(image).ellipse(
        (width // 6, height // 6, (5 * width) // 6, (5 * height) // 6),
        fill=(190, 40, 30),
    )
    buffer = BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


def _png_size(data: bytes) -> tuple[int, int]:
    with Image.open(BytesIO(data)) as image:
        image.load()
        return image.size


def tiny_efficientnet_runtime():
    pytest.importorskip("torch")
    import torch.nn as nn
    from torchvision import transforms

    class TinyCamNet(nn.Module):
        def __init__(self, num_classes: int) -> None:
            super().__init__()
            self.conv_head = nn.Conv2d(3, 8, kernel_size=3, padding=1)
            self.bn2 = nn.Identity()
            self.act2 = nn.ReLU()
            self.pool = nn.AdaptiveAvgPool2d(1)
            self.classifier = nn.Linear(8, num_classes)
            nn.init.constant_(self.conv_head.weight, 0.05)
            nn.init.constant_(self.conv_head.bias, 0.01)
            nn.init.zeros_(self.classifier.weight)
            self.classifier.weight.data[0].fill_(0.2)
            nn.init.zeros_(self.classifier.bias)

        def forward(self, x):
            x = self.act2(self.bn2(self.conv_head(x)))
            return self.classifier(self.pool(x).flatten(1))

    model = TinyCamNet(len(TINY_LABELS))
    model.eval()
    transform = transforms.Compose(
        [
            transforms.Resize((32, 32)),
            transforms.ToTensor(),
        ]
    )
    return EfficientNetRuntime(
        model_id="tiny-cam",
        model_version="test",
        architecture="efficientnet_b3",
        labels=list(TINY_LABELS),
        transform=transform,
        model=model,
    )


@pytest.fixture
def live_env(monkeypatch):
    monkeypatch.setenv("FOSHOL_AI_MODE", "live")
    monkeypatch.setenv("FOSHOL_AI_VISION_CROP_ROUTES", "rice=rice,tomato=solanaceae,potato=solanaceae")
    monkeypatch.setenv("FOSHOL_AI_VISION_RICE_MODEL_ID", VIT_ID)
    monkeypatch.setenv("FOSHOL_AI_VISION_RICE_MODEL_REVISION", VIT_REV)
    monkeypatch.setenv("FOSHOL_SIDECAR_VISION_BACKEND", "vit")
    reset_settings()
    yield
    reset_settings()


def _install_tiny(state) -> None:
    runtime = tiny_efficientnet_runtime()
    state.vision_runtime = runtime
    state.classify_cache = ClassifyCache()
    state.explain_cache = ExplainCache()
    state.loaded_flags["vision.rice.primary"] = True
    state.labels["vision.rice.primary"] = list(runtime.labels)
    state.models_loaded = 1


@pytest.fixture
def live_explain_client(live_env, monkeypatch):
    monkeypatch.setattr("app.main.install_live_vision", _install_tiny)
    monkeypatch.setattr("app.main.install_live_asr", lambda state: None)
    monkeypatch.setattr("app.main.install_live_embed", lambda state: None)
    from app.main import app

    with TestClient(app) as client:
        yield client


@pytest.fixture
def live_explain_uninstalled(live_env, monkeypatch):
    monkeypatch.setattr("app.main.install_live_vision", lambda state: None)
    monkeypatch.setattr("app.main.install_live_asr", lambda state: None)
    monkeypatch.setattr("app.main.install_live_embed", lambda state: None)
    from app.main import app

    with TestClient(app) as client:
        yield client


def test_explain_missing_runtime_is_unavailable(live_explain_uninstalled, rice_png: bytes) -> None:
    response = live_explain_uninstalled.post(
        "/v1/vision/explain",
        files={"image": ("leaf.png", rice_png, "image/png")},
        data={"crop_code": "rice"},
    )
    assert response.status_code == 503
    assert response.json()["code"] == ERR_SIDECAR_MODEL_UNAVAILABLE


def test_explain_live_png_keeps_aspect_and_edge(live_explain_client) -> None:
    image = _blob(800, 400)
    response = live_explain_client.post(
        "/v1/vision/explain",
        files={"image": ("leaf.png", image, "image/png")},
        data={"crop_code": "rice"},
    )
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("image/png")
    width, height = _png_size(response.content)
    assert width == 800
    assert height == 400
    assert max(width, height) <= 1024
    assert response.content[:8] == b"\x89PNG\r\n\x1a\n"
    assert len(response.content) > 483


def test_explain_live_downscales_longest_edge(live_env) -> None:
    pytest.importorskip("torch")

    from app.config import get_settings

    settings = get_settings()
    runtime = tiny_efficientnet_runtime()
    cache = ExplainCache()
    image = _blob(2000, 800)
    png, _headers, _ms = explain_live(settings, runtime, cache, image, "rice", None, None, None)
    width, height = _png_size(png)
    assert max(width, height) == 1024
    assert width / height == pytest.approx(2000 / 800, rel=0.02)


def test_explain_live_byte_identical(live_explain_client) -> None:
    image = _blob(96, 64)
    first = live_explain_client.post(
        "/v1/vision/explain",
        files={"image": ("leaf.png", image, "image/png")},
        data={"crop_code": "rice", "target_label": "Rice___Leaf_Blast"},
    )
    second = live_explain_client.post(
        "/v1/vision/explain",
        files={"image": ("leaf.png", image, "image/png")},
        data={"crop_code": "rice", "target_label": "Rice___Leaf_Blast"},
    )
    assert first.status_code == 200
    assert second.status_code == 200
    assert first.content == second.content


def test_explain_unknown_label_is_400(live_explain_client, rice_png: bytes) -> None:
    response = live_explain_client.post(
        "/v1/vision/explain",
        files={"image": ("leaf.png", rice_png, "image/png")},
        data={"crop_code": "rice", "target_label": "not-a-real-label"},
    )
    assert response.status_code == 400
    assert response.json()["code"] == ERR_SIDECAR_BAD_REQUEST


def test_explain_live_headers(live_explain_client) -> None:
    image = _blob(48, 48)
    response = live_explain_client.post(
        "/v1/vision/explain",
        files={"image": ("leaf.png", image, "image/png")},
        data={"crop_code": "rice", "target_label": "Rice___Healthy"},
    )
    assert response.status_code == 200
    assert response.headers["X-Foshol-Model-Id"] == "tiny-cam"
    assert response.headers["X-Foshol-Model-Version"] == "test"
    assert response.headers["X-Foshol-Target-Label"] == "Rice___Healthy"
    assert response.headers["X-Foshol-Method"] == "gradcam"
    assert response.headers["X-Foshol-Mode"] == "LIVE"


def test_explain_unavailable_role(live_explain_client, rice_png: bytes) -> None:
    response = live_explain_client.post(
        "/v1/vision/explain",
        files={"image": ("leaf.png", rice_png, "image/png")},
        data={"crop_code": "tomato"},
    )
    assert response.status_code == 503
    assert response.json()["code"] == ERR_SIDECAR_MODEL_UNAVAILABLE


def test_act2_matches_forward_features() -> None:
    pytest.importorskip("timm")
    pytest.importorskip("torch")
    import timm
    import torch

    from app.live_vision import _efficientnet_cam_target

    model = timm.create_model("efficientnet_b3", pretrained=False)
    model.eval()
    tensor = torch.zeros(1, 3, 300, 300)
    target = _efficientnet_cam_target(model)
    captured: list[torch.Tensor] = []
    handle = target.register_forward_hook(lambda _m, _i, output: captured.append(output))
    with torch.no_grad():
        features = model.forward_features(tensor)
    handle.remove()
    assert captured
    assert features.shape == captured[0].shape
    assert torch.equal(features, captured[0])
    assert features.shape[-2:] == (10, 10)


def test_compose_overlay_is_precomposited_rgb() -> None:
    pytest.importorskip("torch")
    import torch

    cam = torch.linspace(0, 1, 8).reshape(2, 4)
    png = compose_overlay(_blob(40, 20), cam, max_edge=1024, alpha=0.45)
    with Image.open(BytesIO(png)) as image:
        assert image.mode == "RGB"
        assert image.size == (40, 20)
        extrema = image.getextrema()
        assert extrema[0][0] < extrema[0][1] or extrema[1][0] < extrema[1][1]


def test_vit_style_gradcam_produces_png(live_env) -> None:
    pytest.importorskip("torch")
    import torch
    import torch.nn as nn
    from torchvision import transforms

    from app.config import get_settings

    class TinyVit(nn.Module):
        def __init__(self) -> None:
            super().__init__()
            self.patch = nn.Conv2d(3, 8, kernel_size=8, stride=8)
            self.cls_token = nn.Parameter(torch.zeros(1, 1, 8))
            self.vit = SimpleNamespace(layernorm=nn.LayerNorm(8))
            self.add_module("vit_ln", self.vit.layernorm)
            self.classifier = nn.Linear(8, len(TINY_LABELS))
            nn.init.xavier_uniform_(self.patch.weight)

        def forward(self, pixel_values=None, **_kwargs):
            tokens = self.patch(pixel_values).flatten(2).transpose(1, 2)
            cls = self.cls_token.expand(tokens.size(0), -1, -1)
            seq = torch.cat([cls, tokens], dim=1)
            seq = self.vit.layernorm(seq)
            return SimpleNamespace(logits=self.classifier(seq[:, 0]))

    class FakeProcessor:
        def __call__(self, images, return_tensors="pt"):
            tensor = transforms.Compose(
                [transforms.Resize((32, 32)), transforms.ToTensor()]
            )(images).unsqueeze(0)
            return {"pixel_values": tensor}

    model = TinyVit()
    model.eval()
    runtime = TransformersRuntime(
        model_id="tiny-vit",
        model_version="test",
        architecture="vit",
        labels=list(TINY_LABELS),
        processor=FakeProcessor(),
        model=model,
    )
    settings = get_settings()
    png, headers, _ms = explain_live(
        settings, runtime, ExplainCache(), _blob(64, 48), "rice", None, None, None
    )
    assert headers["X-Foshol-Method"] == "gradcam"
    assert headers["X-Foshol-Mode"] == "LIVE"
    assert _png_size(png) == (64, 48)


def test_explain_method_includes_efficientnet_and_vit() -> None:
    from app.vision import EXPLAIN_METHOD

    assert EXPLAIN_METHOD["efficientnet_b3"] == "gradcam"
    assert EXPLAIN_METHOD["vit"] == "gradcam"
