from pathlib import Path
from uuid import UUID

import pytest
from alembic import command
from fastapi import HTTPException
from fastapi.testclient import TestClient
from pydantic import ValidationError
from test_database import migration_config

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
        readiness = schema["paths"]["/api/v1/ready"]["get"]
        assert readiness["operationId"] == "getReadiness"
        assert readiness["responses"]["200"]["headers"]["X-Request-ID"]["required"]
        assert "503" in readiness["responses"]
        assert client.get("/docs").status_code == 404
        assert client.get("/api/v1/health").headers["X-Request-ID"] != request_id


def test_web_static_serving_and_spa_fallback(tmp_path: Path) -> None:
    dist = tmp_path / "dist"
    (dist / "assets").mkdir(parents=True)
    (dist / "index.html").write_text("<html><body>app-shell</body></html>", encoding="utf-8")
    (dist / "assets" / "app.js").write_text("console.log('app')", encoding="utf-8")

    app = create_app(database_path=tmp_path / "acbooks.db", web_dist_path=dist)
    with TestClient(app) as client:
        index = client.get("/")
        assert index.status_code == 200
        assert "app-shell" in index.text
        assert index.headers["cache-control"] == "no-cache"
        deep_link = client.get("/library")
        assert deep_link.status_code == 200
        assert "app-shell" in deep_link.text
        assert deep_link.headers["cache-control"] == "no-cache"
        asset = client.get("/assets/app.js")
        assert asset.status_code == 200
        assert "console.log" in asset.text
        assert client.get("/assets/missing.js").status_code == 404
        api_missing = client.get("/api/v1/missing")
        assert api_missing.status_code == 404
        assert api_missing.headers["content-type"] == "application/problem+json"

    with pytest.raises(RuntimeError, match="Web build directory"):
        create_app(database_path=tmp_path / "acbooks.db", web_dist_path=tmp_path / "absent")


def test_readiness_requires_database_and_current_migration(tmp_path: Path) -> None:
    database = tmp_path / "acbooks.db"
    config = migration_config(database)
    command.upgrade(config, "head")

    with TestClient(create_app(database_path=database)) as client:
        response = client.get("/api/v1/ready")
        assert response.status_code == 200
        assert response.json() == {"status": "ok", "migration": "current"}
        assert response.headers["cache-control"] == "no-store"

    command.downgrade(config, "0003")
    with TestClient(create_app(database_path=database)) as client:
        response = client.get("/api/v1/ready")
        assert response.status_code == 503
        assert response.headers["content-type"] == "application/problem+json"
        assert response.json()["code"] == "SERVICE_UNAVAILABLE"
        assert client.get("/api/v1/health").status_code == 200

    with TestClient(create_app(database_path=tmp_path / "missing" / "acbooks.db")) as client:
        response = client.get("/api/v1/ready")
        assert response.status_code == 503
        assert response.json()["code"] == "SERVICE_UNAVAILABLE"


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


@pytest.mark.parametrize("code", list(BusinessErrorCode))
def test_business_error_contract(code: BusinessErrorCode) -> None:
    expected_code, status = {
        BusinessErrorCode.REVISION_CONFLICT: ("REVISION_CONFLICT", 409),
        BusinessErrorCode.INVALID_CREDENTIALS: ("INVALID_CREDENTIALS", 401),
        BusinessErrorCode.SETUP_CODE_INVALID: ("SETUP_CODE_INVALID", 403),
        BusinessErrorCode.SETUP_ALREADY_INITIALIZED: ("SETUP_ALREADY_INITIALIZED", 409),
        BusinessErrorCode.RATE_LIMITED: ("RATE_LIMITED", 429),
        BusinessErrorCode.BOOK_NOT_FOUND: ("BOOK_NOT_FOUND", 404),
        BusinessErrorCode.COVER_NOT_FOUND: ("COVER_NOT_FOUND", 404),
        BusinessErrorCode.INVALID_BOOK_FILTER: ("INVALID_BOOK_FILTER", 422),
        BusinessErrorCode.INVALID_COVER: ("INVALID_COVER", 422),
        BusinessErrorCode.BATCH_LIMIT_EXCEEDED: ("BATCH_LIMIT_EXCEEDED", 422),
        BusinessErrorCode.COPY_NOT_FOUND: ("COPY_NOT_FOUND", 404),
        BusinessErrorCode.LOCATION_NOT_FOUND: ("LOCATION_NOT_FOUND", 404),
        BusinessErrorCode.LOCATION_HAS_CHILDREN: ("LOCATION_HAS_CHILDREN", 409),
        BusinessErrorCode.LOCATION_CYCLE: ("LOCATION_CYCLE", 422),
        BusinessErrorCode.FILE_NOT_FOUND: ("FILE_NOT_FOUND", 404),
        BusinessErrorCode.FILE_FORMAT_UNSUPPORTED: ("FILE_FORMAT_UNSUPPORTED", 422),
        BusinessErrorCode.FILE_TOO_LARGE: ("FILE_TOO_LARGE", 413),
        BusinessErrorCode.FILE_DUPLICATE: ("FILE_DUPLICATE", 409),
        BusinessErrorCode.FILE_EXTERNAL_PROTECTED: ("FILE_EXTERNAL_PROTECTED", 409),
        BusinessErrorCode.STORAGE_ROOT_NOT_FOUND: ("STORAGE_ROOT_NOT_FOUND", 404),
        BusinessErrorCode.STORAGE_ROOT_INVALID: ("STORAGE_ROOT_INVALID", 422),
        BusinessErrorCode.STORAGE_ROOT_OVERLAP: ("STORAGE_ROOT_OVERLAP", 409),
        BusinessErrorCode.STORAGE_ROOT_IN_USE: ("STORAGE_ROOT_IN_USE", 409),
        BusinessErrorCode.METADATA_STALE: ("METADATA_STALE", 409),
        BusinessErrorCode.METADATA_UNAVAILABLE: ("METADATA_UNAVAILABLE", 502),
        BusinessErrorCode.IMPORT_FILE_CHANGED: ("IMPORT_FILE_CHANGED", 409),
        BusinessErrorCode.JOB_NOT_FOUND: ("JOB_NOT_FOUND", 404),
    }[code]
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
    assert "Retry-After" not in response.headers


@pytest.mark.parametrize("retry_after", [0, 60])
def test_rate_limit_can_include_retry_delay(retry_after: int) -> None:
    app = create_app()

    @app.get("/rate-limited")
    async def rate_limited() -> None:
        raise BusinessError(BusinessErrorCode.RATE_LIMITED, retry_after=retry_after)

    @app.get("/http-rate-limited")
    async def http_rate_limited() -> None:
        raise HTTPException(429, headers={"Retry-After": str(retry_after)})

    with TestClient(app) as client:
        for path, code in (
            ("/rate-limited", "RATE_LIMITED"),
            ("/http-rate-limited", "TOO_MANY_REQUESTS"),
        ):
            response = client.get(path)
            assert response.status_code == 429
            assert response.headers["Retry-After"] == str(retry_after)
            assert response.headers["content-type"] == "application/problem+json"
            assert response.json()["code"] == code
            assert response.json()["requestId"] == response.headers["X-Request-ID"]


def test_retry_delay_is_only_valid_for_rate_limits() -> None:
    with pytest.raises(ValueError, match="Retry delay"):
        BusinessError(BusinessErrorCode.RATE_LIMITED, retry_after=-1)
    with pytest.raises(ValueError, match="Retry delay"):
        BusinessError(BusinessErrorCode.REVISION_CONFLICT, retry_after=60)
