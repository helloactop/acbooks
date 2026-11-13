from collections.abc import Iterator
from hashlib import sha256
from pathlib import Path
from time import time_ns
from typing import Literal

import pytest
from alembic import command
from argon2 import PasswordHasher
from fastapi.testclient import TestClient
from httpx import Response
from sqlalchemy import select
from sqlalchemy.orm import Session
from test_database import migration_config
from test_setup import ORIGIN, PASSWORD, SETUP, assert_problem, challenge, make_client, payload

from app.db.models.auth import AuthAccount, AuthSession, AuthToken
from app.db.session import create_sqlite_engine
from app.modules.auth import csrf, service
from app.modules.auth.provision import main as provision_installation

LOGIN = "/api/v1/auth/login"
LOGOUT = "/api/v1/auth/logout"
ME = "/api/v1/auth/me"
CSRF = "/api/v1/auth/csrf"
COOKIE = "__Host-acbooks-session"
CREDENTIALS = {"username": "  ａｄＭｉｎ  ", "password": PASSWORD}


@pytest.fixture
def auth_paths(tmp_path: Path) -> tuple[Path, Path]:
    paths = tmp_path / "acbooks.db", tmp_path / "secrets" / "setup-code"
    command.upgrade(migration_config(paths[0]), "head")
    provision_installation(*paths)
    with make_client(paths) as client:
        result = client.post(SETUP, json=payload(paths[1]), headers=challenge(client))
        assert result.status_code == 201
    return paths


@pytest.fixture
def browser(auth_paths: tuple[Path, Path]) -> Iterator[TestClient]:
    with make_client(auth_paths) as client:
        yield client


def login(client: TestClient) -> Response:
    response: Response = client.post(LOGIN, json=CREDENTIALS, headers=challenge(client))
    assert response.status_code == 200
    return response


def test_session_is_hashed_survives_restart_and_logout_revokes_only_current(
    auth_paths: tuple[Path, Path], browser: TestClient, caplog: pytest.LogCaptureFixture
) -> None:
    caplog.set_level("INFO", logger="uvicorn.error")
    assert_problem(browser.get(ME), 401, "UNAUTHORIZED")
    assert_problem(
        browser.get(ME, headers={"Authorization": "Bearer invalid"}), 401, "UNAUTHORIZED"
    )
    assert_problem(browser.post(LOGOUT), 401, "UNAUTHORIZED")
    preauth = challenge(browser)
    response = browser.post(LOGIN, json=CREDENTIALS, headers=preauth)
    assert response.status_code == 200
    account = response.json()["account"]
    assert response.json() == {"account": {"id": account["id"], "username": "Admin"}}
    raw = browser.cookies[COOKIE]
    assert len(raw) == 43
    cookie = next(value for value in response.headers.get_list("set-cookie") if COOKIE in value)
    for attr in ("HttpOnly", "Secure", "SameSite=strict", "Path=/", "Max-Age=604800"):
        assert attr in cookie
    assert "Domain=" not in cookie
    assert response.headers["cache-control"] == "no-store"
    assert browser.get(ME).json() == account

    with make_client(auth_paths) as second:
        login(second)
        assert second.cookies[COOKIE] != raw
        with make_client(auth_paths) as restarted:
            restarted.cookies.set(COOKIE, raw)
            assert restarted.get(ME).json() == account
            fresh = restarted.get(CSRF)
            assert fresh.headers["cache-control"] == "no-store"
            assert "set-cookie" not in fresh.headers
            assert_problem(restarted.post(LOGOUT, headers=preauth), 403, "FORBIDDEN")
            logout = restarted.post(LOGOUT, headers=challenge(restarted))
            assert logout.status_code == 204 and logout.content == b""
            assert "Max-Age=0" in logout.headers["set-cookie"]
            assert_problem(restarted.get(ME), 401, "UNAUTHORIZED")
        assert second.get(ME).json() == account
    assert_problem(browser.get(ME), 401, "UNAUTHORIZED")

    engine = create_sqlite_engine(auth_paths[0])
    try:
        with Session(engine) as session:
            rows = session.scalars(select(AuthSession)).all()
            assert len(rows) == 2
            revoked = next(row for row in rows if row.revoked_at is not None)
            assert revoked.session_token_hash == sha256(raw.encode()).hexdigest()
            assert revoked.expires_at - revoked.created_at == 604800000
    finally:
        engine.dispose()
    assert "operation=login result=authenticated" in caplog.text
    assert "operation=logout result=revoked" in caplog.text
    for secret in (raw, PASSWORD, preauth["X-CSRF-Token"]):
        assert secret not in response.text + caplog.text


def test_login_prunes_expired_and_revoked_credentials(
    auth_paths: tuple[Path, Path],
) -> None:
    now = time_ns() // 1_000_000
    engine = create_sqlite_engine(auth_paths[0])
    try:
        with Session(engine) as session, session.begin():
            account = session.scalars(select(AuthAccount)).one()
            session.add_all(
                [
                    AuthSession(
                        id="expired",
                        account_id=account.id,
                        session_token_hash=sha256(b"expired").hexdigest(),
                        created_at=now - 2_000,
                        expires_at=now - 1_000,
                    ),
                    AuthSession(
                        id="revoked",
                        account_id=account.id,
                        session_token_hash=sha256(b"revoked").hexdigest(),
                        created_at=now - 2_000,
                        expires_at=now + 60_000,
                        revoked_at=now - 1_000,
                    ),
                    AuthSession(
                        id="active",
                        account_id=account.id,
                        session_token_hash=sha256(b"active").hexdigest(),
                        created_at=now - 2_000,
                        expires_at=now + 60_000,
                    ),
                    AuthToken(
                        id="stale-token",
                        account_id=account.id,
                        token_hash=sha256(b"token").hexdigest(),
                        kind="device",
                        scope="full",
                        label="Reader",
                        created_at=now - 2_000,
                        expires_at=now - 1_000,
                    ),
                ]
            )

        with make_client(auth_paths) as browser:
            login(browser)

        with Session(engine) as session:
            session_ids = set(session.scalars(select(AuthSession.id)).all())
            assert len(session_ids) == 2
            assert "active" in session_ids
            assert session.get(AuthToken, "stale-token") is None
    finally:
        engine.dispose()


def test_login_failure_is_uniform_and_throttled(
    browser: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    now = 100.0
    monkeypatch.setattr(service, "monotonic", lambda: now)
    headers = challenge(browser)
    unknown = browser.post(LOGIN, json={**CREDENTIALS, "username": "unknown"}, headers=headers)
    assert_problem(unknown, 401, "INVALID_CREDENTIALS")
    first = browser.post(LOGIN, json=CREDENTIALS, headers=headers)
    assert_problem(first, 429, "RATE_LIMITED")
    assert first.headers["retry-after"] == "1"
    now += 1
    wrong = browser.post(LOGIN, json={**CREDENTIALS, "password": "wrong"}, headers=headers)
    assert_problem(wrong, 401, "INVALID_CREDENTIALS")
    assert {**wrong.json(), "requestId": None} == {**unknown.json(), "requestId": None}
    second = browser.post(LOGIN, json=CREDENTIALS, headers=headers)
    assert_problem(second, 429, "RATE_LIMITED")
    assert second.headers["retry-after"] == "2"
    assert_problem(browser.get(ME), 401, "UNAUTHORIZED")
    now += 2
    assert browser.post(LOGIN, json=CREDENTIALS, headers=headers).status_code == 200


def test_csrf_checks_origin_expiry_and_session_binding(
    browser: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    now = 1_800_000_000.0
    monkeypatch.setattr(csrf, "time", lambda: now)
    headers = challenge(browser)
    for invalid in (
        {"Origin": ORIGIN},
        {**headers, "Origin": "https://evil.example", "Referer": ORIGIN},
        {**headers, "X-CSRF-Token": "forged"},
    ):
        assert_problem(browser.post(LOGIN, json=CREDENTIALS, headers=invalid), 403, "FORBIDDEN")
    headers.pop("Origin")
    headers["Referer"] = ORIGIN + "/login"
    assert browser.post(LOGIN, json=CREDENTIALS, headers=headers).status_code == 200
    with TestClient(browser.app, base_url=ORIGIN) as second:
        login(second)
        other_token = challenge(second)
        assert_problem(browser.post(LOGOUT, headers=other_token), 403, "FORBIDDEN")
        assert second.post(LOGOUT, headers=other_token).status_code == 204
    current = challenge(browser)
    assert_problem(browser.post(LOGOUT, headers={"Origin": ORIGIN}), 403, "FORBIDDEN")
    assert_problem(
        browser.post(LOGOUT, headers={**current, "Origin": "https://evil.example"}),
        403,
        "FORBIDDEN",
    )
    now += csrf.CHALLENGE_TTL
    assert_problem(browser.post(LOGOUT, headers=current), 403, "FORBIDDEN")
    assert browser.get(ME).status_code == 200
    assert browser.post(LOGOUT, headers=challenge(browser)).status_code == 204


def test_relogin_rotates_session_and_old_csrf(browser: TestClient) -> None:
    login(browser)
    original_cookie = browser.cookies[COOKIE]
    original_csrf = challenge(browser)
    assert browser.post(LOGIN, json=CREDENTIALS, headers=original_csrf).status_code == 200
    assert browser.cookies[COOKIE] != original_cookie
    assert_problem(browser.post(LOGOUT, headers=original_csrf), 403, "FORBIDDEN")
    browser.cookies.clear()
    browser.cookies.set(COOKIE, original_cookie)
    assert_problem(browser.get(ME), 401, "UNAUTHORIZED")


def test_failed_session_rotation_preserves_existing_session(
    browser: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    login(browser)
    original_cookie = browser.cookies[COOKIE]

    def fail_insert(
        session: Session,
        _session_id: str,
        _account_id: str,
        _token_hash: str,
        _now: int,
        _expires_at: int,
    ) -> None:
        session.flush()
        raise RuntimeError("Simulated session write failure")

    with monkeypatch.context() as patch:
        patch.setattr(service, "add_session", fail_insert)
        failed = browser.post(LOGIN, json=CREDENTIALS, headers=challenge(browser))
        assert_problem(failed, 500, "INTERNAL_ERROR")
    assert browser.cookies[COOKIE] == original_cookie
    assert browser.get(ME).status_code == 200
    assert browser.post(LOGIN, json=CREDENTIALS, headers=challenge(browser)).status_code == 200
    assert browser.cookies[COOKIE] != original_cookie


def test_expired_session_cannot_authenticate_or_logout(
    auth_paths: tuple[Path, Path], monkeypatch: pytest.MonkeyPatch
) -> None:
    now = 1_800_000_000_000_000_000
    monkeypatch.setenv("ACBOOKS_WEB_SESSION_TTL_SECONDS", "10")
    monkeypatch.setattr(service, "time_ns", lambda: now)
    with make_client(auth_paths) as browser:
        response = login(browser)
        assert "Max-Age=10;" in response.headers["set-cookie"]
        headers = challenge(browser)
        now += 10 * 1_000_000_000
        assert_problem(browser.get(ME), 401, "UNAUTHORIZED")
        assert_problem(browser.post(LOGOUT, headers=headers), 401, "UNAUTHORIZED")
        assert "__Host-acbooks-preauth=" in browser.get(CSRF).headers["set-cookie"]


def test_password_change_during_login_rejects_old_verification(
    auth_paths: tuple[Path, Path], browser: TestClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    engine = create_sqlite_engine(auth_paths[0])
    verify = PasswordHasher.verify

    def update_after_verification(
        hasher: PasswordHasher, password_hash: str | bytes, password: str | bytes
    ) -> Literal[True]:
        result = verify(hasher, password_hash, password)
        with Session(engine) as session, session.begin():
            account = session.scalars(select(AuthAccount)).one()
            account.password_changed_at += 1
        return result

    monkeypatch.setattr(PasswordHasher, "verify", update_after_verification)
    try:
        response = browser.post(LOGIN, json=CREDENTIALS, headers=challenge(browser))
        assert_problem(response, 401, "INVALID_CREDENTIALS")
        assert COOKIE not in browser.cookies
        with Session(engine) as session:
            assert session.scalars(select(AuthSession)).first() is None
    finally:
        engine.dispose()


def test_local_http_development_uses_unprefixed_cookie(auth_paths: tuple[Path, Path]) -> None:
    with make_client(auth_paths, "http://127.0.0.1:8000", "127.0.0.1") as browser:
        response = browser.post(LOGIN, json=CREDENTIALS, headers=challenge(browser))
        assert response.status_code == 200
        cookie = response.headers["set-cookie"]
        assert cookie.startswith("acbooks-session=")
        assert "__Host-" not in cookie
        assert "Secure" not in cookie
        assert "HttpOnly" in cookie and "SameSite=strict" in cookie
        assert browser.get(ME).json()["username"] == "Admin"
        assert browser.post(LOGOUT, headers=challenge(browser)).status_code == 204
        assert_problem(browser.get(ME), 401, "UNAUTHORIZED")


def test_web_auth_openapi(browser: TestClient) -> None:
    paths = browser.get("/openapi.json").json()["paths"]
    assert paths[LOGIN]["post"]["operationId"] == "login"
    assert paths[ME]["get"]["security"] == [{"WebSession": []}]
    logout = paths[LOGOUT]["post"]
    assert logout["security"] == [{"WebSession": []}]
    assert logout["parameters"][0]["required"]
    assert logout["parameters"][0]["name"] == "X-CSRF-Token"
    assert "content" not in logout["responses"]["204"]
