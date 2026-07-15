import hmac

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.security.internal_auth import require_internal_token


def test_healthz_remains_public_when_internal_token_is_configured(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_TOKEN", "internal-test-token")

    response = TestClient(app).get("/healthz")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_internal_readiness_verifies_token_and_redis(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_TOKEN", "submission-internal-secret")
    monkeypatch.setattr("app.main.memory_store.is_redis_ready", lambda: True)
    client = TestClient(app)

    unauthorized = client.get("/internal/readiness")
    ready = client.get(
        "/internal/readiness",
        headers={"X-AI-Internal-Token": "submission-internal-secret"},
    )

    assert unauthorized.status_code == 401
    assert ready.status_code == 200
    assert ready.json() == {"status": "UP", "redis": "UP"}


def test_internal_routes_require_configured_matching_token(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_TOKEN", "internal-test-token")
    client = TestClient(app)

    missing = client.get("/internal/rag/capabilities")
    wrong = client.get(
        "/internal/rag/capabilities",
        headers={"X-AI-Internal-Token": "wrong"},
    )
    valid = client.get(
        "/internal/rag/capabilities",
        headers={
            "X-AI-Internal-Token": "internal-test-token",
            "Authorization": "Bearer user-token",
        },
    )

    assert missing.status_code == 401
    assert wrong.status_code == 401
    assert valid.status_code == 200


def test_missing_server_configuration_fails_closed(monkeypatch):
    monkeypatch.delenv("AI_INTERNAL_TOKEN", raising=False)

    response = TestClient(app).post(
        "/internal/chat",
        headers={
            "X-AI-Internal-Token": "anything",
            "Authorization": "Bearer user-token",
        },
        json={"input": "你好"},
    )

    assert response.status_code == 401


def test_internal_token_uses_constant_time_comparison(monkeypatch):
    observed = []
    original = hmac.compare_digest

    def recording_compare(left, right):
        observed.append((left, right))
        return original(left, right)

    monkeypatch.setenv("AI_INTERNAL_TOKEN", "internal-test-token")
    monkeypatch.setattr(hmac, "compare_digest", recording_compare)

    require_internal_token("internal-test-token")

    assert observed == [("internal-test-token", "internal-test-token")]


def test_export_capability_route_does_not_require_java_internal_secret(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_TOKEN", "internal-test-token")

    response = TestClient(app).get(
        "/internal/rag/exports/not-a-real-export.md",
        headers={"X-AI-Export-Capability": "invalid"},
    )

    assert response.status_code in {403, 404, 410}


@pytest.mark.parametrize(
    "method,path",
    [
        ("post", "/internal/rag/exports/not-a-real-export.md"),
        ("get", "/internal/rag/exports/not-a-real-export.md/extra"),
        ("get", "/internal/rag/exports"),
    ],
)
def test_only_exact_export_download_route_bypasses_java_internal_secret(
    monkeypatch,
    method,
    path,
):
    monkeypatch.setenv("AI_INTERNAL_TOKEN", "internal-test-token")

    response = TestClient(app).request(method, path)

    assert response.status_code == 401


@pytest.mark.parametrize(
    "method,path,payload",
    [
        ("post", "/internal/chat", {"input": "test"}),
        ("post", "/internal/chat/stream", {"input": "test"}),
        ("post", "/internal/rag/query", {"input": "test"}),
        ("post", "/internal/rag/query/stream", {"input": "test"}),
        ("post", "/internal/images/generate", {}),
        ("post", "/internal/images/batch", {}),
        ("get", "/internal/images/tasks/task-1", None),
        ("post", "/internal/videos/generate", {}),
        ("post", "/internal/videos/batch", {}),
        ("get", "/internal/videos/tasks/task-1", None),
        ("get", "/internal/models/providers", None),
        ("post", "/internal/models/vision/test", {}),
        ("post", "/internal/rag/pdf/convert", {}),
        ("post", "/internal/rag/ppt/convert", {}),
    ],
)
def test_entire_java_to_python_internal_surface_rejects_missing_token(
    monkeypatch,
    method,
    path,
    payload,
):
    monkeypatch.setenv("AI_INTERNAL_TOKEN", "internal-test-token")

    response = TestClient(app).request(method, path, json=payload)

    assert response.status_code == 401
