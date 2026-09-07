"""Health, models, and correlation behaviour in replay mode."""


def test_health_replay_up(client) -> None:
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["mode"] == "REPLAY"
    assert body["warm"] is True
    assert body["models_expected"] == 5
    assert "models_loaded" in body
    assert "degraded_reasons" in body
    assert response.headers["X-Foshol-Mode"] == "REPLAY"
    assert "X-Correlation-Id" in response.headers


def test_models_lists_five_roles(client) -> None:
    response = client.get("/v1/models")
    assert response.status_code == 200
    body = response.json()
    assert body["mode"] == "REPLAY"
    roles = [row["role"] for row in body["models"]]
    assert roles == [
        "vision.rice.primary",
        "vision.rice.fallback",
        "vision.solanaceae",
        "asr.bangla",
        "embed.text",
    ]
    rice = body["models"][0]
    assert rice["model_id"] == "kssrikar4/Rice-Leaf-Disease-Classification"
    assert rice["model_version"] == "02a6e6ea1b5da9b0458b12c4ec8bccd0582a4f26"
    assert rice["loaded"] is False


def test_symptoms_extract_does_not_exist(client) -> None:
    response = client.post("/v1/symptoms/extract", json={"text": "x"})
    assert response.status_code == 404
    assert response.headers["content-type"].startswith("application/problem+json")
