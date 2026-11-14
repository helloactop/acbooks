from hashlib import sha256
from pathlib import Path
from uuid import uuid4

from fastapi.testclient import TestClient
from sqlalchemy import select
from sqlalchemy.orm import Session
from test_book_management import management
from test_setup import assert_problem, challenge
from test_web_auth import CREDENTIALS, LOGIN, LOGOUT, ME

from app.db.models.auth import AuthToken
from app.db.session import create_sqlite_engine
from app.modules.auth.service import WebAuthService, now_ms

__all__ = ["management"]

DEVICE = "/api/v1/auth/device-login"
TOKENS = "/api/v1/auth/tokens"
PASSWORD = "/api/v1/auth/password"


def test_native_and_application_credential_boundaries(
    management: tuple[TestClient, Path, Path],
) -> None:
    client, database, _ = management
    browser_cookies = client.cookies.jar
    cookie = next(cookie for cookie in browser_cookies if cookie.name.endswith("acbooks-session"))
    assert_problem(client.post(DEVICE, json=CREDENTIALS), 403, "FORBIDDEN")
    client.cookies.clear()
    result = client.post(DEVICE, json={**CREDENTIALS, "deviceName": "Android"})
    assert result.status_code == 200, result.text
    assert result.headers["cache-control"] == "no-store"
    assert "set-cookie" not in result.headers
    token = result.json()
    bearer = {"Authorization": f"Bearer {token['accessToken']}"}
    assert client.get(ME, headers=bearer).status_code == 200
    assert client.post("/api/v1/books", headers=bearer, json={"title": "A book"}).status_code == 201

    issued = client.post(TOKENS, headers=bearer, json={"label": "OPDS", "scope": "read"})
    assert issued.status_code == 201, issued.text
    secret = issued.json()["token"]
    application = {"Authorization": f"Bearer {secret}"}
    assert_problem(client.get(ME, headers=application), 401, "UNAUTHORIZED")
    assert_problem(client.post(TOKENS, headers=application), 401, "UNAUTHORIZED")
    assert_problem(
        client.get(ME, headers={"Authorization": f"Bearer {cookie.value}"}), 401, "UNAUTHORIZED"
    )
    client.cookies.jar.set_cookie(cookie)
    assert_problem(client.get(ME, headers=bearer), 401, "UNAUTHORIZED")
    assert_problem(client.delete(f"{TOKENS}/{token['tokenId']}"), 403, "FORBIDDEN")
    rows = client.get(TOKENS, params={"kind": "application", "limit": 1})
    assert rows.status_code == 200
    assert rows.json()["items"][0]["label"] == "OPDS"
    assert secret not in rows.text and token["accessToken"] not in rows.text
    assert (
        client.delete(f"{TOKENS}/{token['tokenId']}", headers=challenge(client)).status_code == 204
    )
    client.cookies.clear()
    assert_problem(client.get(ME, headers=bearer), 401, "UNAUTHORIZED")
    engine = create_sqlite_engine(database)
    try:
        auth = WebAuthService(engine, 60, 60, 60)
        assert auth.authenticate_device(secret) is None
        identity = auth.authenticate_application(secret)
        assert identity is not None and identity.scope == "read"
        client.cookies.jar.set_cookie(cookie)
        assert (
            client.delete(f"{TOKENS}/{issued.json()['id']}", headers=challenge(client)).status_code
            == 204
        )
        assert auth.authenticate_application(secret) is None
        with Session(engine) as session:
            app_token = session.scalar(select(AuthToken).where(AuthToken.label == "OPDS"))
            assert (
                app_token is not None
                and app_token.token_hash == sha256(secret.encode()).hexdigest()
            )
    finally:
        engine.dispose()


def test_password_rotation_revokes_every_credential(
    management: tuple[TestClient, Path, Path],
) -> None:
    client, database, _ = management
    cookie = next(
        cookie for cookie in client.cookies.jar if cookie.name.endswith("acbooks-session")
    )
    issued = client.post(TOKENS, headers=challenge(client), json={"label": "NAS", "scope": "read"})
    assert issued.status_code == 201
    client.cookies.clear()
    token = client.post(DEVICE, json=CREDENTIALS).json()
    bearer = {"Authorization": f"Bearer {token['accessToken']}"}
    client.cookies.jar.set_cookie(cookie)
    payload = {"currentPassword": CREDENTIALS["password"], "newPassword": "A new secure password"}
    assert_problem(client.patch(PASSWORD, json=payload), 403, "FORBIDDEN")
    rotated = client.patch(PASSWORD, json=payload, headers=challenge(client))
    assert rotated.status_code == 204
    assert "Max-Age=0" in rotated.headers["set-cookie"]
    assert_problem(client.get(ME, headers=bearer), 401, "UNAUTHORIZED")
    client.cookies.jar.set_cookie(cookie)
    assert_problem(client.get(ME), 401, "UNAUTHORIZED")
    client.cookies.clear()
    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session:
            assert all(row.revoked_at is not None for row in session.scalars(select(AuthToken)))
    finally:
        engine.dispose()
    login = client.post(
        LOGIN, json={**CREDENTIALS, "password": payload["newPassword"]}, headers=challenge(client)
    )
    assert login.status_code == 200
    assert_problem(
        client.delete(f"{TOKENS}/{uuid4()}", headers=challenge(client)), 404, "TOKEN_NOT_FOUND"
    )


def test_bearer_logout_is_local_to_its_credential(
    management: tuple[TestClient, Path, Path],
) -> None:
    client, _, _ = management
    client.cookies.clear()
    first = client.post(DEVICE, json=CREDENTIALS).json()["accessToken"]
    second = client.post(DEVICE, json=CREDENTIALS).json()["accessToken"]
    logout = client.post(LOGOUT, headers={"Authorization": f"Bearer {first}"})
    assert logout.status_code == 204 and "set-cookie" not in logout.headers
    assert_problem(
        client.get(ME, headers={"Authorization": f"Bearer {first}"}), 401, "UNAUTHORIZED"
    )
    assert client.get(ME, headers={"Authorization": f"Bearer {second}"}).status_code == 200


def test_expired_device_token_is_rejected(
    management: tuple[TestClient, Path, Path],
) -> None:
    client, database, _ = management
    client.cookies.clear()
    issued = client.post(DEVICE, json=CREDENTIALS).json()
    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session, session.begin():
            row = session.get(AuthToken, issued["tokenId"])
            assert row is not None
            row.created_at = now_ms() - 10_000
            row.expires_at = row.created_at + 1_000
        headers = {"Authorization": f"Bearer {issued['accessToken']}"}
        assert_problem(client.get(ME, headers=headers), 401, "UNAUTHORIZED")
    finally:
        engine.dispose()
