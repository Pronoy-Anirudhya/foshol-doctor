"""Replay classify: fixture hit, missing fixture, unknown crop_code."""

from __future__ import annotations

from app.errors import ERR_SIDECAR_FIXTURE_MISSING, ERR_SIDECAR_UNKNOWN_CROP
from app.replay import sha256_hex
from tests.conftest import png_bytes


def test_classify_replay_returns_fixture(client, rice_png: bytes) -> None:
    response = client.post(
        "/v1/vision/classify",
        files={"image": ("leaf.png", rice_png, "application/octet-stream")},
        data={"crop_code": "rice", "top_k": "3"},
        headers={"X-Correlation-Id": "corr-classify-1"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["mode"] == "REPLAY"
    assert body["crop_code"] == "rice"
    assert body["image_sha256"] == sha256_hex(rice_png)
    assert body["model_id"] == "kssrikar4/Rice-Leaf-Disease-Classification"
    assert body["model_version"] == "02a6e6ea1b5da9b0458b12c4ec8bccd0582a4f26"
    assert body["correlation_id"] == "corr-classify-1"
    assert response.headers["X-Correlation-Id"] == "corr-classify-1"
    assert response.headers["X-Foshol-Mode"] == "REPLAY"
    preds = body["predictions"]
    assert 1 <= len(preds) <= 3
    assert preds[0]["rank"] == 1
    assert all(p["confidence"] == round(p["confidence"], 4) for p in preds)
    assert all(0.0 <= p["confidence"] <= 1.0 for p in preds)
    assert [p["rank"] for p in preds] == list(range(1, len(preds) + 1))
    confidences = [p["confidence"] for p in preds]
    assert confidences == sorted(confidences, reverse=True)
    assert all("raw_label" in p for p in preds)


def test_classify_replay_tomato_uses_solanaceae_model(client, tomato_png: bytes) -> None:
    response = client.post(
        "/v1/vision/classify",
        files={"image": ("leaf.png", tomato_png, "image/png")},
        data={"crop_code": "tomato"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["model_id"] == "Daksh159/plant-disease-mobilenetv2"
    assert body["architecture"] == "mobilenetv2"
    assert body["mode"] == "REPLAY"


def test_classify_missing_fixture_returns_404(client) -> None:
    image = png_bytes(width=1, height=1, rgba=(1, 2, 3, 255))
    response = client.post(
        "/v1/vision/classify",
        files={"image": ("missing.png", image, "image/png")},
        data={"crop_code": "rice"},
    )
    assert response.status_code == 404
    assert response.headers["content-type"].startswith("application/problem+json")
    body = response.json()
    assert body["code"] == ERR_SIDECAR_FIXTURE_MISSING
    assert body["status"] == 404
    assert "correlationId" in body
    assert "digest" in body["detail"]


def test_classify_unknown_crop_returns_400(client, rice_png: bytes) -> None:
    response = client.post(
        "/v1/vision/classify",
        files={"image": ("leaf.png", rice_png, "image/png")},
        data={"crop_code": "wheat"},
    )
    assert response.status_code == 400
    assert response.headers["content-type"].startswith("application/problem+json")
    body = response.json()
    assert body["code"] == ERR_SIDECAR_UNKNOWN_CROP
    assert body["status"] == 400


def test_classify_text_file_is_unsupported_media(client) -> None:
    response = client.post(
        "/v1/vision/classify",
        files={"image": ("leaf.jpg", b"not-an-image", "image/jpeg")},
        data={"crop_code": "rice"},
    )
    assert response.status_code == 415
    assert response.json()["code"] == "ERR_SIDECAR_UNSUPPORTED_MEDIA"
