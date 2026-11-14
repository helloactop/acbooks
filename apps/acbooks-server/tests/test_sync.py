from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Any
from uuid import uuid4

from alembic import command
from fastapi.testclient import TestClient
from test_book_management import management
from test_books import BOOKS, create_book
from test_database import migration_config
from test_files import PDF, upload
from test_setup import challenge
from test_web_auth import CREDENTIALS

from app.db.session import create_sqlite_engine

__all__ = ["management"]

SYNC = "/api/v1/sync"


def test_upgrade_seeds_existing_book_and_position(tmp_path: Path) -> None:
    path = tmp_path / "upgrade.db"
    config = migration_config(path)
    command.upgrade(config, "0006")
    engine = create_sqlite_engine(path)
    try:
        with engine.begin() as connection:
            for sql in (
                "INSERT INTO books(id,title,created_at,updated_at) VALUES ('book','Existing',1,1)",
                "INSERT INTO storage_roots VALUES ('root','NAS','/nas','external')",
                "INSERT INTO storage_objects (id,root_id,relative_path,size_bytes,mtime_ms)"
                " VALUES ('object','root','one.pdf',1,1)",
                "INSERT INTO book_files VALUES ('file','book','object','pdf')",
                "INSERT INTO reading_positions (id,book_file_id,locator_json,revision,updated_at)"
                """ VALUES ('private-id','file','{"page":3}',4,1)""",
                "INSERT INTO sync_changes(entity_type,entity_id,operation,changed_at)"
                " VALUES ('reading_position','private-id','upsert',1)",
            ):
                connection.exec_driver_sql(sql)
        command.upgrade(config, "head")
        with engine.connect() as connection:
            assert connection.exec_driver_sql(
                "SELECT entity_type, entity_id FROM sync_changes ORDER BY seq"
            ).all() == [("book", "book"), ("book_file", "file"), ("reading_position", "file")]
            assert (
                connection.exec_driver_sql(
                    "SELECT revision FROM reading_positions WHERE id='private-id'"
                ).scalar_one()
                == 4
            )
    finally:
        engine.dispose()


def test_native_bearer_can_sync(management: tuple[TestClient, Path, Path]) -> None:
    client, _, _ = management
    client.cookies.clear()
    assert client.get(f"{SYNC}/snapshot").status_code == 401
    login = client.post("/api/v1/auth/device-login", json=CREDENTIALS)
    assert login.status_code == 200
    headers = {"Authorization": f"Bearer {login.json()['accessToken']}"}
    payload = {
        "deviceId": str(uuid4()),
        "mutations": [
            mutation("book.create", str(uuid4()), data={"title": "Native"}),
        ],
    }
    response = client.post(f"{SYNC}/mutations", headers=headers, json=payload)
    assert response.status_code == 200, response.text
    assert response.json()["receipts"][0]["status"] == "accepted"
    assert client.get(f"{SYNC}/snapshot", headers=headers).json()["items"][0]["data"]["title"] == (
        "Native"
    )


def mutation(action: str, key: str, revision: int = 0, **fields: Any) -> dict[str, Any]:
    return {
        "action": action,
        "entityId": key,
        "baseRevision": revision,
        "mutationId": str(uuid4()),
        **fields,
    }


def submit(client: TestClient, device: str, *items: dict[str, Any]) -> list[dict[str, Any]]:
    response = client.post(
        f"{SYNC}/mutations",
        headers=challenge(client),
        json={"deviceId": device, "mutations": list(items)},
    )
    assert response.status_code == 200, response.text
    receipts: list[dict[str, Any]] = response.json()["receipts"]
    return receipts


def pull(client: TestClient, endpoint: str, **params: Any) -> dict[str, Any]:
    response = client.get(f"{SYNC}/{endpoint}", params=params)
    assert response.status_code == 200, response.text
    assert response.headers["cache-control"] == "no-store"
    result: dict[str, Any] = response.json()
    return result


def test_snapshot_and_catchup_after_concurrent_writes(
    management: tuple[TestClient, Path, Path],
) -> None:
    client, _, _ = management
    first = create_book(client, title="First")
    second = create_book(client, title="Second")
    third = create_book(client, title="Third")
    first_page = pull(client, "snapshot", limit=1)
    assert first_page["items"][0]["entityId"] == first["id"]
    assert first_page["hasMore"]
    assert (
        client.patch(
            f"{BOOKS}/{second['id']}",
            headers=challenge(client),
            json={"title": "Updated"},
        ).status_code
        == 200
    )
    assert client.delete(f"{BOOKS}/{first['id']}", headers=challenge(client)).status_code == 204
    boundary = first_page["through"]
    final = pull(client, "snapshot", through=boundary, after=first_page["nextCursor"])
    assert [row["entityId"] for row in final["items"]] == [third["id"]]
    assert final["nextCursor"] == boundary
    catchup = pull(client, "changes", after=boundary)
    changes = {row["entityId"]: row for row in catchup["items"]}
    assert changes[first["id"]]["operation"] == "delete"
    assert changes[first["id"]]["data"] is None
    assert changes[second["id"]]["data"]["title"] == "Updated"
    assert (
        client.get(f"{SYNC}/changes", params={"after": catchup["through"] + 1}).status_code == 422
    )


def test_offline_batch_receipt_conflict_and_reader_validation(
    management: tuple[TestClient, Path, Path],
) -> None:
    client, _, _ = management
    book, foreign, record, marker, note, device = (str(uuid4()) for _ in range(6))
    add = mutation("book.create", book, data={"title": "Offline", "tags": ["Travel"]})
    session = mutation(
        "reading_session.create",
        record,
        bookId=book,
        data={"startedAt": 1_000, "endedAt": 10_000, "activeSeconds": 5},
    )
    bookmark = mutation("bookmark.create", marker, bookId=book, data={"label": "Chapter 1"})
    annotation = mutation("annotation.create", note, bookId=book, data={"note": "Text"})
    invalid = mutation("bookmark.create", str(uuid4()), bookId=foreign, data={"label": "Invalid"})
    batch = (add, session, bookmark, invalid, annotation)
    assert (
        client.post(
            f"{SYNC}/mutations",
            json={"deviceId": device, "mutations": list(batch)},
        ).status_code
        == 403
    )
    results = submit(client, device, *batch)
    assert [receipt["status"] for receipt in results] == [
        "accepted",
        "accepted",
        "accepted",
        "rejected",
        "accepted",
    ]
    assert results[3]["code"] == "BOOK_NOT_FOUND"
    assert submit(client, device, *batch) == results
    assert submit(client, device, {**add, "data": {"title": "Different"}})[0]["code"] == (
        "MUTATION_ID_REUSED"
    )
    assert client.get(f"{BOOKS}/{book}/reading-sessions").json()["total"] == 1
    before = results[0]["current"]["revision"]
    assert (
        client.patch(
            f"{BOOKS}/{book}",
            headers=challenge(client),
            json={"title": "Online"},
        ).status_code
        == 200
    )
    conflict = submit(
        client,
        str(uuid4()),
        mutation(
            "book.update",
            book,
            before,
            data={"title": "Stale"},
        ),
    )[0]
    assert conflict["status"] == "conflict"
    assert conflict["current"]["data"]["title"] == "Online"
    revision = conflict["current"]["revision"]
    assert (
        submit(client, device, mutation("book.delete", book, revision))[0]["status"] == "accepted"
    )
    tombstones = pull(client, "changes", after=revision)["items"]
    assert {(row["entityId"], row["operation"]) for row in tombstones} == {
        (key, "delete") for key in (record, marker, note, book)
    }
    assert submit(client, device, add)[0] == results[0]


def test_file_download_position_and_unlink(
    management: tuple[TestClient, Path, Path],
) -> None:
    client, database, _ = management
    book = create_book(client)["id"]
    response = upload(client, book, PDF)
    assert response.status_code == 201, response.text
    file_id = response.json()["id"]
    snapshot = pull(client, "snapshot")
    file = next(item for item in snapshot["items"] if item["entityType"] == "book_file")
    assert file["entityId"] == file_id
    assert file["data"]["bookId"] == book
    assert "relativePath" not in file["data"]
    assert client.get(f"/api/v1/files/{file_id}/content").content == PDF
    device = str(uuid4())
    position = mutation("reading_position.save", file_id, data={"locator": {"page": 1}})
    with ThreadPoolExecutor(max_workers=2) as pool:
        commits = list(pool.map(lambda _: submit(client, device, position)[0], range(2)))
    assert commits[0] == commits[1]
    assert commits[0]["current"]["data"]["revision"] == 1
    assert submit(client, str(uuid4()), {**position, "mutationId": str(uuid4())})[0]["status"] == (
        "conflict"
    )
    target = create_book(client)["id"]
    note = str(uuid4())
    assert (
        submit(
            client,
            device,
            mutation(
                "annotation.create",
                note,
                bookId=book,
                data={"bookFileId": file_id, "note": "Portable"},
            ),
        )[0]["status"]
        == "accepted"
    )
    moved = submit(
        client,
        device,
        mutation(
            "book_file.move",
            file_id,
            file["revision"],
            data={"sourceBookId": book, "targetBookId": target},
        ),
    )[0]
    assert moved["status"] == "accepted"
    assert moved["current"]["data"]["bookId"] == target
    new_snapshot = pull(client, "snapshot")
    moved_note = next(item for item in new_snapshot["items"] if item["entityId"] == note)
    assert moved_note["data"]["bookId"] == target
    assert moved_note["data"]["bookFileId"] == file_id
    assert any(
        item["entityType"] == "reading_position" and item["entityId"] == file_id
        for item in new_snapshot["items"]
    )
    removed = submit(
        client,
        device,
        mutation("book_file.delete", file_id, moved["current"]["revision"]),
    )[0]
    assert removed["status"] == "accepted"
    delta = pull(client, "changes", after=new_snapshot["through"])["items"]
    assert {(item["entityType"], item["operation"]) for item in delta} == {
        ("book", "upsert"),
        ("book_file", "delete"),
        ("reading_position", "delete"),
        ("annotation", "upsert"),
    }
    assert any(path.read_bytes() == PDF for path in (database.parent / "library").rglob("*.pdf"))

    engine = create_sqlite_engine(database)
    try:
        with engine.connect() as connection:
            transaction = connection.begin()
            connection.exec_driver_sql(
                "UPDATE books SET title = 'Rolled back' WHERE id = ?", (book,)
            )
            transaction.rollback()
        assert pull(client, "changes", after=max(item["revision"] for item in delta))["items"] == []
    finally:
        engine.dispose()
