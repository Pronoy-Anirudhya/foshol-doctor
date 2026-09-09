"""LIVE explain: multipart is accepted; overlay stays MODEL_UNAVAILABLE."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.config import reset_settings
from app.errors import ERR_SIDECAR_MODEL_UNAVAILABLE


@pytest.fixture
def live_explain_client(monkeypatch):
    monkeypatch.setenv("FOSHOL_AI_MODE", "live")
    reset_settings()
    monkeypatch.setattr("app.main.install_live_vision", lambda state: None)
    monkeypatch.setattr("app.main.install_live_asr", lambda state: None)
    monkeypatch.setattr("app.main.install_live_embed", lambda state: None)
    from app.main import app

    with TestClient(app) as client:
        yield client
    reset_settings()


def test_explain_live_multipart_is_not_422(live_explain_client, rice_png: bytes) -> None:
    response = live_explain_client.post(
        "/v1/vision/explain",
        files={"image": ("leaf.png", rice_png, "image/png")},
        data={"crop_code": "rice"},
    )
    assert response.status_code != 422
    assert response.status_code == 503
    assert response.json()["code"] == ERR_SIDECAR_MODEL_UNAVAILABLE
