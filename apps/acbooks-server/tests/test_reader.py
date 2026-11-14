from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import func, select
from sqlalchemy.orm import Session
from test_book_management import management
from test_books import BOOKS, create_book
from test_files import PDF, upload
from test_setup import assert_problem, challenge

from app.db.models.sync import SyncChange, SyncMutation
from app.db.session import create_sqlite_engine

__all__ = ["management"]


def test_manual_session_crud_and_concurrent_duplicate(
    management: tuple[TestClient, Path, Path],
) -> None:
    client, database, _ = management
    book = create_book(client)
    other = create_book(client)
    url = f"{BOOKS}/{book['id']}/reading-sessions"
    payload = {
        "deviceId": str(uuid4()),
        "mutationId": str(uuid4()),
        "startedAt": 1000,
        "endedAt": 11_000,
        "activeSeconds": 8,
    }
    assert_problem(client.post(url, json=payload), 403, "FORBIDDEN")
    headers = challenge(client)

    def submit() -> dict[str, object]:
        response = client.post(url, json=payload, headers=headers)
        assert response.status_code == 200, response.text
        result: dict[str, object] = response.json()
        return result

    with ThreadPoolExecutor(max_workers=2) as pool:
        responses = list(pool.map(lambda _: submit(), range(2)))
    assert {item["status"] for item in responses} == {"accepted", "duplicate"}
    listed = client.get(f"{url}?limit=1").json()
    assert listed["total"] == 1
    row = listed["items"][0]
    assert row["source"] == "manual" and row["bookFileId"] is None
    item_url = f"{url}/{row['id']}"
    assert_problem(
        client.patch(item_url, json={"endedAt": 2000}, headers=headers),
        422,
        "INVALID_READING_RECORD",
    )
    assert client.get(url).json()["items"][0]["endedAt"] == 11_000
    assert (
        client.patch(item_url, json={"activeSeconds": 3}, headers=headers).json()["activeSeconds"]
        == 3
    )
    assert_problem(
        client.delete(f"{BOOKS}/{other['id']}/reading-sessions/{row['id']}", headers=headers),
        404,
        "READING_RECORD_NOT_FOUND",
    )
    assert client.delete(item_url, headers=headers).status_code == 204
    assert submit() == {"status": "duplicate"}
    assert client.get(url).json()["total"] == 0
    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session:
            assert session.scalar(select(func.count()).select_from(SyncMutation)) == 1
            assert session.scalars(
                select(SyncChange.operation)
                .where(
                    SyncChange.entity_type == "reading_session", SyncChange.entity_id == row["id"]
                )
                .order_by(SyncChange.seq)
            ).all() == [
                "upsert",
                "upsert",
                "delete",
            ]
    finally:
        engine.dispose()


def test_positions_keep_opaque_locators_and_retry_after_conflict(
    management: tuple[TestClient, Path, Path],
) -> None:
    client, _, _ = management
    book = create_book(client)
    uploaded = upload(client, book["id"], PDF)
    assert uploaded.status_code == 201, uploaded.text
    file_id = uploaded.json()["id"]
    url = f"/api/v1/reading-positions/{file_id}"
    assert client.get(url).json() is None
    locator = {"href": "OPS/c1.xhtml", "locations": {"progression": 0.5}, "text": ["上", "下"]}
    payload = {
        "deviceId": str(uuid4()),
        "mutationId": str(uuid4()),
        "revision": 0,
        "percent": 35,
        "locator": locator,
    }
    headers = challenge(client)
    accepted = client.put(url, json=payload, headers=headers)
    assert accepted.status_code == 200, accepted.text
    position = accepted.json()["item"]
    assert position["locator"] == locator and position["revision"] == 1
    assert accepted.headers["cache-control"] == "no-store"
    assert client.put(url, json=payload, headers=headers).json() == {"status": "duplicate"}

    stale = {**payload, "deviceId": str(uuid4()), "percent": 70}
    response = client.put(url, json=stale, headers=headers)
    assert_problem(response, 409, "REVISION_CONFLICT")
    assert response.json()["current"] == position
    assert client.get(url).json() == position
    recovered = client.put(url, json={**stale, "revision": 1}, headers=headers)
    assert recovered.json()["item"]["revision"] == 2
    assert recovered.json()["item"]["percent"] == 70
    assert (
        client.put(
            url,
            json={**stale, "revision": 2, "percent": 101},
            headers=headers,
        ).status_code
        == 422
    )
    schema = client.get("/openapi.json").json()
    conflict = schema["paths"]["/api/v1/reading-positions/{bookFileId}"]["put"]["responses"]["409"]
    assert set(conflict["content"]) == {"application/problem+json"}
    assert "current" in conflict["content"]["application/problem+json"]["schema"]["properties"]


@pytest.mark.parametrize(
    ("resource", "data", "patch"),
    [
        ("bookmarks", {"label": "第七章"}, {"label": "下次从这里读"}),
        ("annotations", {"quote": "划线", "note": "旁注"}, {"note": "更新后的笔记"}),
    ],
)
def test_marks_and_file_ownership(
    management: tuple[TestClient, Path, Path],
    resource: str,
    data: dict[str, str],
    patch: dict[str, str],
) -> None:
    client, _, _ = management
    book = create_book(client)
    other = create_book(client)
    uploaded = upload(client, other["id"], PDF)
    assert uploaded.status_code == 201
    foreign_file_id = uploaded.json()["id"]
    url = f"{BOOKS}/{book['id']}/{resource}"
    payload = {"deviceId": str(uuid4()), "mutationId": str(uuid4()), **data}
    headers = challenge(client)
    assert_problem(
        client.post(url, json={**payload, "bookFileId": foreign_file_id}, headers=headers),
        404,
        "FILE_NOT_FOUND",
    )
    created = client.post(url, json=payload, headers=headers)
    assert created.status_code == 200, created.text
    row = created.json()["item"]
    assert row["bookFileId"] is None
    assert client.post(url, json=payload, headers=headers).json() == {"status": "duplicate"}
    item_url = f"{url}/{row['id']}"
    assert_problem(
        client.patch(item_url, json={"bookFileId": foreign_file_id}, headers=headers),
        404,
        "FILE_NOT_FOUND",
    )
    edited = client.patch(item_url, json=patch, headers=headers).json()
    assert all(edited[key] == value for key, value in patch.items())
    assert edited["updatedAt"] > row["updatedAt"]
    assert client.get(url).json()["items"] == [edited]
    assert client.delete(item_url, headers=headers).status_code == 204
    assert client.get(url).json()["total"] == 0


def test_automatic_sessions_require_matching_file(
    management: tuple[TestClient, Path, Path],
) -> None:
    client, _, _ = management
    book = create_book(client)
    other = create_book(client)
    uploaded = upload(client, book["id"], PDF)
    assert uploaded.status_code == 201
    file_id = uploaded.json()["id"]
    payload = {
        "deviceId": str(uuid4()),
        "mutationId": str(uuid4()),
        "source": "automatic",
        "startedAt": 1000,
    }
    headers = challenge(client)
    assert (
        client.post(
            f"{BOOKS}/{book['id']}/reading-sessions",
            json=payload,
            headers=headers,
        ).status_code
        == 422
    )
    assert_problem(
        client.post(
            f"{BOOKS}/{other['id']}/reading-sessions",
            json={**payload, "bookFileId": file_id},
            headers=headers,
        ),
        404,
        "FILE_NOT_FOUND",
    )
    response = client.post(
        f"{BOOKS}/{book['id']}/reading-sessions",
        json={**payload, "bookFileId": file_id},
        headers=headers,
    )
    assert response.status_code == 200, response.text
    assert response.json()["item"]["endedAt"] is None
