"""Embed, explain, and ASR replay endpoints."""

from __future__ import annotations

import math

from app.embed import EMBED_DIM
from app.errors import ERR_SIDECAR_FIXTURE_MISSING, ERR_SIDECAR_PAYLOAD_TOO_LARGE
from tests.conftest import wav_bytes


def test_embed_replay_hash_vectors_are_768d_unit(client) -> None:
    response = client.post("/v1/embed", json={"texts": ["পাতা", "leaf ১"]})
    assert response.status_code == 200
    body = response.json()
    assert body["mode"] == "REPLAY"
    assert body["dimension"] == EMBED_DIM
    assert body["normalised"] is True
    assert len(body["embeddings"]) == 2
    for vector in body["embeddings"]:
        assert len(vector) == EMBED_DIM
        norm = math.sqrt(sum(v * v for v in vector))
        assert abs(norm - 1.0) < 1e-6


def test_embed_nfc_nfd_identical(client) -> None:
    nfd = "cafe\u0301"
    nfc = "caf\u00e9"
    assert nfd != nfc
    a = client.post("/v1/embed", json={"texts": [nfc]}).json()["embeddings"][0]
    b = client.post("/v1/embed", json={"texts": [nfd]}).json()["embeddings"][0]
    assert a == b


def test_embed_batch_too_large(client) -> None:
    response = client.post("/v1/embed", json={"texts": ["x"] * 33})
    assert response.status_code == 413
    assert response.json()["code"] == ERR_SIDECAR_PAYLOAD_TOO_LARGE


def test_explain_json_body_is_bad_request(client) -> None:
    response = client.post(
        "/v1/vision/explain",
        json={"crop_code": "rice", "image_base64": "QQ==", "raw_label": "x"},
    )
    assert response.status_code == 400
    assert response.json()["code"] == "ERR_SIDECAR_BAD_REQUEST"


def test_explain_replay_png(client, rice_png: bytes) -> None:
    response = client.post(
        "/v1/vision/explain",
        files={"image": ("leaf.png", rice_png, "image/png")},
        data={"crop_code": "rice"},
    )
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("image/png")
    assert response.content[:8] == b"\x89PNG\r\n\x1a\n"
    assert response.headers["X-Foshol-Model-Id"]
    assert response.headers["X-Foshol-Model-Version"]
    assert response.headers["X-Foshol-Method"] == "gradcam"
    assert response.headers["X-Foshol-Mode"] == "REPLAY"


def test_explain_missing_fixture(client) -> None:
    from tests.conftest import png_bytes

    image = png_bytes(rgba=(9, 9, 9, 255))
    response = client.post(
        "/v1/vision/explain",
        files={"image": ("leaf.png", image, "image/png")},
        data={"crop_code": "rice"},
    )
    assert response.status_code == 404
    assert response.json()["code"] == ERR_SIDECAR_FIXTURE_MISSING


def test_asr_silent_fixture(client) -> None:
    from pathlib import Path

    audio = (Path(__file__).resolve().parents[1] / "fixtures" / "sources" / "synthetic-silent.wav").read_bytes()
    response = client.post(
        "/v1/asr/transcribe",
        files={"audio": ("clip.wav", audio, "application/octet-stream")},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["mode"] == "REPLAY"
    assert body["speech_detected"] is False
    assert body["transcript"] == ""
    assert body["confidence"] == 0.0
    assert body["language"] == "bn"


def test_asr_missing_fixture(client) -> None:
    audio = wav_bytes(duration_s=0.08)
    response = client.post(
        "/v1/asr/transcribe",
        files={"audio": ("clip.wav", audio, "application/octet-stream")},
    )
    assert response.status_code == 404
    assert response.json()["code"] == ERR_SIDECAR_FIXTURE_MISSING
