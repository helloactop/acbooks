from uuid import UUID

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.core.errors import BusinessError, BusinessErrorCode
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
        ("GET", "/explicit-500", 500, "INTERNAL_ERROR"),
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

    @app.get("/explicit-500")
    async def explicit_failure() -> None:
        raise HTTPException(500, "private-input")

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
        exception, frame = (
            ("RuntimeError", "crash") if path == "/crash" else ("HTTPException", "explicit_failure")
        )
        diagnostics = [record for record in caplog.records if "exception=" in record.getMessage()]
        assert len(diagnostics) == 1
        record = diagnostics[0]
        assert record.exc_info is None
        assert f"request_id={body['requestId']}" in record.getMessage()
        assert f"exception={exception}" in record.getMessage()
        assert f"{frame}:" in record.getMessage()
        assert __file__ not in record.getMessage()


@pytest.mark.parametrize(
    ("code", "status", "expected_code"),
    [
        (BusinessErrorCode.REVISION_CONFLICT, 409, "REVISION_CONFLICT"),
        (BusinessErrorCode.INVALID_CREDENTIALS, 401, "INVALID_CREDENTIALS"),
        (BusinessErrorCode.SETUP_CODE_INVALID, 403, "SETUP_CODE_INVALID"),
        (BusinessErrorCode.SETUP_ALREADY_INITIALIZED, 409, "SETUP_ALREADY_INITIALIZED"),
        (BusinessErrorCode.RATE_LIMITED, 429, "RATE_LIMITED"),
    ],
)
def test_business_error_contract(code: BusinessErrorCode, status: int, expected_code: str) -> None:
    app = create_app()

    @app.get("/business-error")
    async def fail() -> None:
        raise BusinessError(code)

    with TestClient(app) as client:
        response = client.get("/business-error")
    assert response.status_code == status
    assert response.headers["content-type"] == "application/problem+json"
    body = response.json()
    assert body["status"] == status
    assert body["code"] == expected_code
    assert body["requestId"] == response.headers["X-Request-ID"]
