import json
import os
import stat
from collections.abc import Iterator
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from threading import Barrier
from uuid import UUID

import pytest
from alembic import command
from argon2 import PasswordHasher, extract_parameters
from argon2.exceptions import VerifyMismatchError
from argon2.low_level import Type
from fastapi.testclient import TestClient
from httpx import Response
from sqlalchemy import func, select
from sqlalchemy.orm import Session
from starlette.types import Receive, Scope, Send
from test_database import migration_config

from app.core.config import Settings
from app.db.models.auth import AuthAccount, AuthSession, AuthToken
from app.db.session import create_sqlite_engine
from app.main import create_app
from app.modules.auth import csrf, service
from app.modules.auth.provision import main as provision_installation
from app.modules.auth.repository import add_account
from app.modules.auth.setup_code import provision_setup_code

ORIGIN = "https://books.example"
STATUS = "/api/v1/setup/status"
SETUP = "/api/v1/setup"
CSRF = "/api/v1/auth/csrf"
PASSWORD = "correct horse battery staple 密码"


@pytest.fixture
def paths(tmp_path: Path) -> tuple[Path, Path]:
    database = tmp_path / "acbooks.db"
    code_path = tmp_path / "secrets" / "setup-code"
    command.upgrade(migration_config(database), "head")
    provision_installation(database, code_path)
    return database, code_path


def make_client(
    paths: tuple[Path, Path], origin: str = ORIGIN, address: str = "192.0.2.10"
) -> TestClient:
    database, code_path = paths
    app = create_app(
        Settings(public_origin=origin), database_path=database, setup_code_path=code_path
    )

    async def simulate_client(scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] == "http":
            scope = {**scope, "client": (address, 50000)}
        await app(scope, receive, send)

    return TestClient(simulate_client, base_url=origin)


@pytest.fixture
def client(paths: tuple[Path, Path]) -> Iterator[TestClient]:
    with make_client(paths) as instance:
        yield instance


def challenge(client: TestClient) -> dict[str, str]:
    response = client.get(CSRF)
    assert response.status_code == 200
    assert response.headers["cache-control"] == "no-store"
    assert set(response.json()) == {"csrfToken"}
    return {
        "Origin": str(client.base_url).rstrip("/"),
        "X-CSRF-Token": response.json()["csrfToken"],
    }


def payload(code_path: Path, **changes: str) -> dict[str, str]:
    return {
        "username": "  Ａdmin  ",
        "password": PASSWORD,
        "confirmPassword": PASSWORD,
        "setupCode": code_path.read_text(encoding="ascii"),
    } | changes


def assert_problem(response: Response, status: int, code: str) -> None:
    assert response.status_code == status
    assert response.headers["content-type"] == "application/problem+json"
    assert response.json()["code"] == code
    assert response.json()["requestId"] == response.headers["X-Request-ID"]


def test_setup_persists_only_argon2id_and_stays_closed_after_restart(
    paths: tuple[Path, Path], client: TestClient, caplog: pytest.LogCaptureFixture
) -> None:
    database, code_path = paths
    body = payload(code_path)
    caplog.set_level("INFO", logger="uvicorn.error")
    status = client.get(STATUS)
    assert status.json() == {"initialized": False}
    assert status.headers["cache-control"] == "no-store"
    headers = challenge(client)
    response = client.post(SETUP, json=body, headers=headers)
    assert response.status_code == 201
    assert response.headers["location"] == "/api/v1/auth/me"
    assert response.headers["cache-control"] == "no-store"
    assert "Max-Age=0" in response.headers["set-cookie"]
    result = response.json()
    assert result == {"id": str(UUID(result["id"])), "username": "Admin"}
    assert not code_path.exists()
    provision_installation(database, code_path)
    assert not code_path.exists()

    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session:
            account = session.scalars(select(AuthAccount)).one()
            assert (account.id, account.singleton_slot) == (result["id"], 1)
            assert account.username_normalized == "admin"
            assert account.created_at == account.updated_at == account.password_changed_at
            assert account.created_at > 1_700_000_000_000
            stored_hash = account.password_hash
            params = extract_parameters(stored_hash)
            assert params.type is Type.ID and params.salt_len == 16
            assert params.memory_cost >= 19 * 1024 and params.time_cost >= 2
            assert PasswordHasher().verify(stored_hash, PASSWORD)
            with pytest.raises(VerifyMismatchError):
                PasswordHasher().verify(stored_hash, "wrong password")
            assert service.password_hasher.hash(PASSWORD) != stored_hash
            assert session.scalar(select(func.count()).select_from(AuthSession)) == 0
            assert session.scalar(select(func.count()).select_from(AuthToken)) == 0

        with make_client(paths) as restarted:
            assert restarted.get(STATUS).json() == {"initialized": True}
            repeat = restarted.post(SETUP, json=body, headers=challenge(restarted))
            assert_problem(repeat, 409, "SETUP_ALREADY_INITIALIZED")
        with Session(engine) as session:
            assert session.scalars(select(AuthAccount)).one().password_hash == stored_hash
    finally:
        engine.dispose()

    assert "operation=setup result=created" in caplog.text
    for value in (PASSWORD, body["setupCode"], headers["X-CSRF-Token"], stored_hash):
        assert value not in caplog.text
        assert value not in response.text + status.text + repeat.text


@pytest.mark.parametrize(
    "changes",
    [
        {"confirmPassword": "does not match password"},
        {"password": "short", "confirmPassword": "short"},
        {"username": "　 "},
        {"username": "bad\x00name"},
        {"password": "\ud800" * 12, "confirmPassword": "\ud800" * 12},
        {"setupCode": ""},
        {"unexpected": "value"},
    ],
)
def test_invalid_input(
    paths: tuple[Path, Path], client: TestClient, changes: dict[str, str]
) -> None:
    response = client.post(
        SETUP,
        content=json.dumps(payload(paths[1], **changes)),
        headers={**challenge(client), "Content-Type": "application/json"},
    )
    assert_problem(response, 422, "VALIDATION_ERROR")
    assert PASSWORD not in response.text
    assert client.get(STATUS).json() == {"initialized": False}


def test_missing_code_and_backoff(
    paths: tuple[Path, Path], client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    now = 100.0
    monkeypatch.setattr(service, "monotonic", lambda: now)
    body = payload(paths[1])
    headers = challenge(client)
    assert_problem(
        client.post(
            SETUP, json={k: v for k, v in body.items() if k != "setupCode"}, headers=headers
        ),
        422,
        "VALIDATION_ERROR",
    )
    for delay in (1, 2, 4, 8, 45):
        wrong = client.post(SETUP, json={**body, "setupCode": "wrong"}, headers=headers)
        assert_problem(wrong, 403, "SETUP_CODE_INVALID")
        throttled = client.post(SETUP, json=body, headers=headers)
        assert_problem(throttled, 429, "RATE_LIMITED")
        assert throttled.headers["retry-after"] == str(delay)
        now += delay
    assert client.post(SETUP, json=body, headers=headers).status_code == 201


def test_missing_code_file_fails_closed(paths: tuple[Path, Path], client: TestClient) -> None:
    body = payload(paths[1])
    paths[1].unlink()
    assert_problem(
        client.post(SETUP, json=body, headers=challenge(client)), 403, "SETUP_CODE_INVALID"
    )
    assert client.get(STATUS).json() == {"initialized": False}


def test_csrf_rejects_untrusted_forged_unbound_and_expired_requests(
    paths: tuple[Path, Path], client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    now = 1_800_000_000.0
    monkeypatch.setattr(csrf, "time", lambda: now)
    body = payload(paths[1])
    headers = challenge(client)
    token = headers["X-CSRF-Token"]
    cookie = client.cookies.get("__Host-acbooks-preauth")
    assert cookie is not None and cookie != token
    set_cookie = client.get(CSRF).headers["set-cookie"]
    for attribute in ("HttpOnly", "Secure", "SameSite=strict", "Path=/", "Max-Age=600"):
        assert attribute in set_cookie
    assert "Domain=" not in set_cookie
    assert_problem(client.post(SETUP, json=body, headers=headers), 403, "FORBIDDEN")

    headers = challenge(client)
    token = headers["X-CSRF-Token"]
    for invalid in (
        {"Origin": ORIGIN},
        {"X-CSRF-Token": token},
        {**headers, "Origin": "https://books.example.evil", "Referer": ORIGIN},
        {**headers, "Origin": "null"},
        {**headers, "X-CSRF-Token": token[:-1] + ("0" if token[-1] != "0" else "1")},
    ):
        assert_problem(client.post(SETUP, json=body, headers=invalid), 403, "FORBIDDEN")
    client.cookies.clear()
    assert_problem(client.post(SETUP, json=body, headers=headers), 403, "FORBIDDEN")
    headers = challenge(client)
    now += csrf.CHALLENGE_TTL
    assert_problem(client.post(SETUP, json=body, headers=headers), 403, "FORBIDDEN")
    headers = challenge(client)
    assert (
        client.post(
            SETUP,
            json=body,
            headers={"Referer": f"{ORIGIN}/setup", "X-CSRF-Token": headers["X-CSRF-Token"]},
        ).status_code
        == 201
    )


def test_two_app_instances_can_only_create_one_account(
    paths: tuple[Path, Path], monkeypatch: pytest.MonkeyPatch
) -> None:
    barrier = Barrier(2)
    real_hash = PasswordHasher.hash

    def hash_concurrently(
        hasher: PasswordHasher, password: str | bytes, *, salt: bytes | None = None
    ) -> str:
        barrier.wait(timeout=10)
        return real_hash(hasher, password, salt=salt)

    monkeypatch.setattr(PasswordHasher, "hash", hash_concurrently)
    first = payload(paths[1], username="First")
    second = payload(paths[1], username="Second")

    def submit(body: dict[str, str]) -> Response:
        with make_client(paths) as contender:
            response: Response = contender.post(SETUP, json=body, headers=challenge(contender))
            return response

    with ThreadPoolExecutor(max_workers=2) as executor:
        responses = sorted(executor.map(submit, (first, second)), key=lambda r: r.status_code)
    assert responses[0].status_code == 201
    assert_problem(responses[1], 409, "SETUP_ALREADY_INITIALIZED")
    engine = create_sqlite_engine(paths[0])
    try:
        with Session(engine) as session:
            assert (
                session.scalars(select(AuthAccount.username)).one()
                == responses[0].json()["username"]
            )
    finally:
        engine.dispose()


def test_failed_write_rolls_back_and_can_retry(
    paths: tuple[Path, Path],
    client: TestClient,
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    body = payload(paths[1])
    headers = challenge(client)

    def fail_after_insert(
        session: Session,
        account_id: str,
        username: str,
        normalized: str,
        password_hash: str,
        timestamp: int,
    ) -> None:
        add_account(session, account_id, username, normalized, password_hash, timestamp)
        session.flush()
        raise RuntimeError(PASSWORD)

    with monkeypatch.context() as patch:
        patch.setattr(service, "add_account", fail_after_insert)
        failed = client.post(SETUP, json=body, headers=headers)
    assert_problem(failed, 500, "INTERNAL_ERROR")
    assert PASSWORD not in failed.text + caplog.text
    assert client.get(STATUS).json() == {"initialized": False}
    assert paths[1].read_text(encoding="ascii") == body["setupCode"]
    assert client.post(SETUP, json=body, headers=headers).status_code == 201


def test_code_removed_before_transaction_cannot_be_used(
    paths: tuple[Path, Path], client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    body = payload(paths[1])
    real_hash = PasswordHasher.hash

    def remove_code_then_hash(
        hasher: PasswordHasher, password: str | bytes, *, salt: bytes | None = None
    ) -> str:
        paths[1].unlink()
        return real_hash(hasher, password, salt=salt)

    monkeypatch.setattr(PasswordHasher, "hash", remove_code_then_hash)
    response = client.post(SETUP, json=body, headers=challenge(client))
    assert_problem(response, 403, "SETUP_CODE_INVALID")
    assert client.get(STATUS).json() == {"initialized": False}


def test_failed_cleanup_does_not_reopen_setup(
    paths: tuple[Path, Path], client: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    body = payload(paths[1])
    unlink = Path.unlink

    def fail_cleanup(path: Path, missing_ok: bool = False) -> None:
        if path == paths[1]:
            raise PermissionError(body["setupCode"])
        unlink(path, missing_ok=missing_ok)

    with monkeypatch.context() as patch:
        patch.setattr(Path, "unlink", fail_cleanup)
        assert client.post(SETUP, json=body, headers=challenge(client)).status_code == 201
    assert paths[1].exists()
    with make_client(paths) as restarted:
        response = restarted.post(SETUP, json=body, headers=challenge(restarted))
        assert_problem(response, 409, "SETUP_ALREADY_INITIALIZED")
    provision_installation(*paths)
    assert not paths[1].exists()


def test_https_required_outside_loopback(paths: tuple[Path, Path], client: TestClient) -> None:
    headers = challenge(client)
    assert_problem(client.get("http://books.example" + CSRF), 403, "FORBIDDEN")
    assert_problem(
        client.post("http://books.example" + SETUP, json=payload(paths[1]), headers=headers),
        403,
        "FORBIDDEN",
    )
    with make_client(paths, "http://127.0.0.1:8000") as remote:
        assert_problem(remote.get(CSRF), 403, "FORBIDDEN")
    with make_client(paths, "http://127.0.0.1:8000", "127.0.0.1") as local:
        assert (
            local.post(SETUP, json=payload(paths[1]), headers=challenge(local)).status_code == 201
        )


def test_provisioning_is_idempotent_and_private(tmp_path: Path) -> None:
    path = tmp_path / "secrets" / "setup-code"
    with ThreadPoolExecutor(max_workers=2) as executor:
        list(executor.map(provision_setup_code, (path, path)))
    code = path.read_bytes()
    assert len(code) == 43
    assert list(path.parent.iterdir()) == [path]
    provision_setup_code(path)
    assert path.read_bytes() == code
    if os.name != "nt":
        assert stat.S_IMODE(path.stat().st_mode) == 0o600
        assert stat.S_IMODE(path.parent.stat().st_mode) == 0o700
        path.chmod(0o644)
        with pytest.raises(PermissionError):
            provision_setup_code(path)
        path.unlink()
        external = tmp_path / "external"
        external.write_bytes(code)
        path.symlink_to(external)
        with pytest.raises(PermissionError):
            provision_setup_code(path)
        assert external.read_bytes() == code


def test_openapi_contract(client: TestClient) -> None:
    schema = client.get("/openapi.json").json()
    setup = schema["paths"][SETUP]["post"]
    assert setup["operationId"] == "initializeSetup"
    assert set(setup["responses"]) == {"201", "403", "409", "422", "429", "500"}
    assert setup["security"]
    assert "Location" in setup["responses"]["201"]["headers"]
    assert "Retry-After" in setup["responses"]["429"]["headers"]
    assert "application/problem+json" in setup["responses"]["422"]["content"]
    request = schema["components"]["schemas"]["SetupRequest"]
    assert set(request["required"]) == {"username", "password", "confirmPassword", "setupCode"}
