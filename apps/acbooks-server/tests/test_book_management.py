from collections.abc import Iterator
from pathlib import Path
from typing import Any

import pytest
from alembic import command
from fastapi.testclient import TestClient
from sqlalchemy import func, select
from sqlalchemy.orm import Session
from test_books import BOOKS, create_book
from test_database import migration_config
from test_setup import (
    SETUP,
    assert_problem,
    challenge,
    make_client,
    payload,
)
from test_web_auth import CREDENTIALS, LOGIN

from app.db.models.books import (
    Book,
    BookContributor,
    BookIdentifier,
    BookTag,
    ShelfBook,
)
from app.db.models.copies import BookCopy
from app.db.models.reading import Annotation, Bookmark, ReadingSession
from app.db.models.storage import BookFile, StorageObject, StorageRoot
from app.db.session import create_sqlite_engine
from app.modules.auth.provision import main as provision_installation

PNG = b"\x89PNG\r\n\x1a\n" + b"cover-data"
JPEG = b"\xff\xd8\xff\xe0" + b"cover-data"


@pytest.fixture
def management(
    tmp_path: Path,
) -> Iterator[tuple[TestClient, Path, Path]]:
    database = tmp_path / "acbooks.db"
    code_path = tmp_path / "secrets" / "setup-code"
    covers = tmp_path / "covers"
    command.upgrade(migration_config(database), "head")
    provision_installation(database, code_path)
    with make_client((database, code_path), covers_dir=covers) as client:
        setup = client.post(SETUP, json=payload(code_path), headers=challenge(client))
        assert setup.status_code == 201
        assert (
            client.post(LOGIN, json=CREDENTIALS, headers=challenge(client)).status_code
            == 200
        )
        yield client, database, covers


def count(session: Session, model: type[Any], book_id: str) -> int:
    if model is Book:
        condition = Book.id == book_id
    else:
        condition = getattr(model, "book_id") == book_id
    return int(
        session.scalar(
            select(func.count()).select_from(model).where(condition)
        )
        or 0
    )


def test_delete_removes_library_relations_but_keeps_stored_files(
    management: tuple[TestClient, Path, Path],
) -> None:
    browser, database, covers = management
    book = create_book(browser, title="删除我")
    book_id = book["id"]
    upload = browser.put(
        f"{BOOKS}/{book_id}/cover",
        files={"file": ("../evil.png", PNG, "image/png")},
        headers=challenge(browser),
    )
    assert upload.status_code == 200
    cover_file = covers / f"{book_id}.png"
    assert cover_file.is_file()
    assert not (covers / "evil.png").exists()

    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session, session.begin():
            session.add(
                StorageRoot(id="root", name="NAS", root_path="/books", mode="external")
            )
            session.flush()
            session.add(
                StorageObject(
                    id="object",
                    root_id="root",
                    relative_path="book.epub",
                    size_bytes=3,
                    mtime_ms=1,
                )
            )
            session.flush()
            session.add(
                BookFile(
                    id="file", book_id=book_id, storage_object_id="object", format="epub"
                )
            )
            session.add(BookCopy(id="copy", book_id=book_id))
            session.add(
                ReadingSession(
                    id="session",
                    book_id=book_id,
                    started_at=0,
                    ended_at=1000,
                    active_seconds=1,
                    source="manual",
                )
            )
            session.add(
                Bookmark(id="bookmark", book_id=book_id, created_at=1, updated_at=1)
            )
            session.add(
                Annotation(id="annotation", book_id=book_id, created_at=1, updated_at=1)
            )

        response = browser.delete(f"{BOOKS}/{book_id}", headers=challenge(browser))
        assert response.status_code == 204

        with Session(engine) as session:
            for model in (
                Book,
                BookCopy,
                BookFile,
                ReadingSession,
                Bookmark,
                Annotation,
                BookTag,
                ShelfBook,
                BookContributor,
                BookIdentifier,
            ):
                assert count(session, model, book_id) == 0
            assert (
                session.scalar(select(func.count()).select_from(StorageObject)) == 1
            )
        assert not cover_file.exists()
    finally:
        engine.dispose()

    assert browser.get(f"{BOOKS}/{book_id}").status_code == 404


def test_batch_update_status_tags_and_shelves(
    management: tuple[TestClient, Path, Path],
) -> None:
    browser, _, _ = management
    first = create_book(browser, title="批量一", tags=["旧"], shelves=["旧架"])
    second = create_book(browser, title="批量二", tags=["旧"], shelves=["旧架"])

    response = browser.patch(
        f"{BOOKS}/batch-update",
        json={
            "bookIds": [first["id"], second["id"]],
            "readingStatus": "done",
            "addTags": ["新", "旧"],
            "removeTags": ["旧"],
            "addShelves": ["新架"],
            "removeShelves": ["旧架"],
        },
        headers=challenge(browser),
    )
    assert response.status_code == 200
    assert response.json() == {"updated": 2, "deleted": 0}

    for book in (first, second):
        detail = browser.get(f"{BOOKS}/{book['id']}").json()
        assert detail["readingStatus"] == "done"
        assert detail["tags"] == ["新"]
        assert detail["shelves"] == ["新架"]

    empty = browser.patch(
        f"{BOOKS}/batch-update",
        json={"bookIds": [first["id"]]},
        headers=challenge(browser),
    )
    assert_problem(empty, 422, "VALIDATION_ERROR")
    duplicate = browser.patch(
        f"{BOOKS}/batch-update",
        json={"bookIds": [first["id"], first["id"]], "readingStatus": "reading"},
        headers=challenge(browser),
    )
    assert_problem(duplicate, 422, "VALIDATION_ERROR")


def test_batch_update_is_atomic_when_a_book_is_missing(
    management: tuple[TestClient, Path, Path],
) -> None:
    browser, _, _ = management
    book = create_book(browser, title="保留")
    response = browser.patch(
        f"{BOOKS}/batch-update",
        json={
            "bookIds": [book["id"], "00000000-0000-4000-8000-000000000000"],
            "readingStatus": "done",
        },
        headers=challenge(browser),
    )
    assert_problem(response, 404, "BOOK_NOT_FOUND")
    assert browser.get(f"{BOOKS}/{book['id']}").json()["readingStatus"] == "unread"


def test_batch_delete_reports_missing_books_and_keeps_others(
    management: tuple[TestClient, Path, Path],
) -> None:
    browser, _, _ = management
    first = create_book(browser, title="删除一")
    second = create_book(browser, title="删除二")
    keep = create_book(browser, title="保留")

    response = browser.post(
        f"{BOOKS}/batch-delete",
        json={"bookIds": [first["id"], second["id"]]},
        headers=challenge(browser),
    )
    assert response.status_code == 200
    assert response.json() == {"updated": 0, "deleted": 2}
    assert browser.get(f"{BOOKS}/{first['id']}").status_code == 404
    assert browser.get(f"{BOOKS}/{keep['id']}").status_code == 200

    repeated = browser.post(
        f"{BOOKS}/batch-delete",
        json={"bookIds": [first["id"], keep["id"]]},
        headers=challenge(browser),
    )
    assert_problem(repeated, 404, "BOOK_NOT_FOUND")
    assert browser.get(f"{BOOKS}/{keep['id']}").status_code == 200


def test_cover_upload_replace_delete_and_fallback(
    management: tuple[TestClient, Path, Path],
) -> None:
    browser, _, covers = management
    book = create_book(browser, title="封面")
    book_id = book["id"]
    assert book["hasCover"] is False

    uploaded = browser.put(
        f"{BOOKS}/{book_id}/cover",
        files={"file": ("cover.png", PNG, "image/png")},
        headers=challenge(browser),
    )
    assert uploaded.status_code == 200, uploaded.text
    detail = uploaded.json()
    assert detail["hasCover"] is True
    assert detail["coverUpdatedAt"] is not None
    assert (covers / f"{book_id}.png").read_bytes() == PNG
    assert browser.get(f"{BOOKS}").json()["items"][0]["hasCover"] is True

    cover = browser.get(f"{BOOKS}/{book_id}/cover")
    assert cover.status_code == 200
    assert cover.headers["content-type"] == "image/png"
    assert cover.content == PNG

    replaced = browser.put(
        f"{BOOKS}/{book_id}/cover",
        files={"file": ("cover.jpg", JPEG, "image/jpeg")},
        headers=challenge(browser),
    )
    assert replaced.status_code == 200
    assert (covers / f"{book_id}.jpg").is_file()
    assert not (covers / f"{book_id}.png").exists()
    assert browser.get(f"{BOOKS}/{book_id}/cover").content == JPEG

    invalid = browser.put(
        f"{BOOKS}/{book_id}/cover",
        files={"file": ("cover.txt", b"not an image", "text/plain")},
        headers=challenge(browser),
    )
    assert_problem(invalid, 422, "INVALID_COVER")
    too_large = browser.put(
        f"{BOOKS}/{book_id}/cover",
        files={"file": ("cover.png", PNG + b"x" * (5 * 1024 * 1024), "image/png")},
        headers=challenge(browser),
    )
    assert_problem(too_large, 422, "INVALID_COVER")

    removed = browser.delete(f"{BOOKS}/{book_id}/cover", headers=challenge(browser))
    assert removed.status_code == 200
    assert removed.json()["hasCover"] is False
    assert not (covers / f"{book_id}.jpg").exists()
    assert_problem(
        browser.get(f"{BOOKS}/{book_id}/cover"), 404, "COVER_NOT_FOUND"
    )
