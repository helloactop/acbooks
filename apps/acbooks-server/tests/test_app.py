from uuid import UUID

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.main import create_app


def test_health_and_openapi(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("ACBOOKS_ENABLE_DOCS", "false")
    with TestClient(create_app()) as client:
        response = client.get("/api/v1/health", headers={"X-Request-ID": "caller-controlled"})
        assert response.status_code == 200
        assert response.json() == {"status": "ok"}
        request_id = response.headers["X-Request-ID"]
        assert UUID(hex=request_id).version == 4
        schema = client.get("/openapi.json").json()
        operation = schema["paths"]["/api/v1/health"]["get"]
        assert operation["operationId"] == "getHealth"
        assert operation["responses"]["200"]["headers"]["X-Request-ID"]["required"]
        problem = operation["responses"]["500"]["content"]["application/problem+json"]
        assert "requestId" in problem["schema"]["required"]
        assert client.get("/docs").status_code == 404
        assert client.get("/api/v1/health").headers["X-Request-ID"] != request_id


def test_environment_configuration(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("ACBOOKS_ENABLE_DOCS", "true")
    with TestClient(create_app()) as client:
        assert client.get("/docs").status_code == 200
    monkeypatch.setenv("ACBOOKS_ENABLE_DOCS", "invalid")
    with pytest.raises(ValidationError):
        create_app()


@pytest.mark.parametrize(
    ("method", "path", "status", "code"),
    [
        ("GET", "/missing", 404, "NOT_FOUND"),
        ("POST", "/api/v1/health", 405, "METHOD_NOT_ALLOWED"),
        ("GET", "/validation?count=private-input", 422, "VALIDATION_ERROR"),
        ("GET", "/http-error", 401, "UNAUTHORIZED"),
        ("GET", "/crash", 500, "INTERNAL_ERROR"),
    ],
)
def test_problem_responses(
    method: str, path: str, status: int, code: str, caplog: pytest.LogCaptureFixture
) -> None:
    app = create_app()

    @app.get("/validation")
    async def validate(count: int) -> int:
        return count

    @app.get("/http-error")
    async def reject() -> None:
        raise HTTPException(401, "private-input", headers={"WWW-Authenticate": "Bearer"})

    @app.get("/crash")
    async def crash() -> None:
        raise RuntimeError("private-input")

    with TestClient(app) as client:
        response = client.request(method, path)
    assert response.status_code == status
    assert response.headers["content-type"] == "application/problem+json"
    body = response.json()
    assert body.keys() == {"type", "title", "status", "code", "detail", "requestId"}
    assert body["status"] == status
    assert body["code"] == code
    assert body["requestId"] == response.headers["X-Request-ID"]
    assert "private-input" not in response.text
    assert "private-input" not in caplog.text
    if status == 405:
        assert response.headers["allow"] == "GET"
    if status == 401:
        assert response.headers["www-authenticate"] == "Bearer"
    if status == 500:
        assert body["requestId"] in caplog.text
