import json
from collections.abc import Iterator
from datetime import UTC, datetime
from pathlib import Path
from time import sleep
from typing import Any
from uuid import UUID, uuid4

import pytest
from alembic import command
from fastapi.testclient import TestClient
from sqlalchemy import func, select
from sqlalchemy.orm import Session
from test_database import migration_config
from test_setup import (
    ORIGIN,
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
    Contributor,
    Shelf,
    Tag,
)
from app.db.models.copies import BookCopy
from app.db.models.reading import ReadingSession
from app.db.models.storage import BookFile, StorageObject, StorageRoot
from app.db.session import create_sqlite_engine
from app.modules.auth.provision import main as provision_installation

BOOKS = "/api/v1/books"


@pytest.fixture
def paths(tmp_path: Path) -> tuple[Path, Path]:
    paths = tmp_path / "acbooks.db", tmp_path / "secrets" / "setup-code"
    command.upgrade(migration_config(paths[0]), "head")
    provision_installation(*paths)
    return paths


@pytest.fixture
def browser(paths: tuple[Path, Path]) -> Iterator[TestClient]:
    with make_client(paths) as client:
        setup = client.post(SETUP, json=payload(paths[1]), headers=challenge(client))
        assert setup.status_code == 201
        login = client.post(LOGIN, json=CREDENTIALS, headers=challenge(client))
        assert login.status_code == 200
        yield client


def book_payload(**changes: object) -> dict[str, Any]:
    body: dict[str, Any] = {
        "title": "呐喊",
        "description": "一部短篇小说集。",
        "authors": ["鲁迅"],
        "translators": [],
        "isbn": "978-7-02-000220-7",
        "readingStatus": "unread",
        "tags": ["文学"],
        "shelves": ["书房"],
    }
    body.update(changes)
    return body


def create_book(client: TestClient, **changes: object) -> dict[str, Any]:
    response = client.post(
        BOOKS, json=book_payload(**changes), headers=challenge(client)
    )
    assert response.status_code == 201, response.text
    result: dict[str, Any] = response.json()
    return result


def test_create_book_persists_metadata_and_does_not_merge_isbn(
    paths: tuple[Path, Path], browser: TestClient
) -> None:
    response = browser.post(BOOKS, json=book_payload(), headers=challenge(browser))
    assert response.status_code == 201, response.text
    book = response.json()
    assert response.headers["location"] == f"/api/v1/books/{book['id']}"
    UUID(book["id"])
    assert book["title"] == "呐喊"
    assert book["description"] == "一部短篇小说集。"
    assert book["authors"] == ["鲁迅"]
    assert book["translators"] == []
    assert book["isbn"] == "9787020002207"
    assert book["identifiers"] == [{"scheme": "isbn", "value": "9787020002207"}]
    assert book["readingStatus"] == "unread"
    assert book["tags"] == ["文学"]
    assert book["shelves"] == ["书房"]
    assert book["copyCount"] == 0
    assert book["fileCount"] == 0
    assert book["formats"] == []
    assert book["copies"] == []
    assert book["files"] == []
    assert book["createdAt"] == book["updatedAt"] > 0

    detail = browser.get(f"{BOOKS}/{book['id']}")
    assert detail.status_code == 200
    assert detail.json()["description"] == "一部短篇小说集。"

    second = create_book(browser, title="同名不同书")
    assert second["id"] != book["id"]
    listing = browser.get(BOOKS)
    assert listing.status_code == 200
    assert listing.json()["total"] == 2

    engine = create_sqlite_engine(paths[0])
    try:
        with Session(engine) as session:
            assert session.scalar(select(func.count()).select_from(Book)) == 2
            assert session.scalar(select(func.count()).select_from(BookIdentifier)) == 2
            assert session.scalar(select(func.count()).select_from(Contributor)) == 1
            assert session.scalar(select(func.count()).select_from(Tag)) == 1
            assert session.scalar(select(func.count()).select_from(Shelf)) == 1
            roles = {
                (name, role)
                for name, role in session.execute(
                    select(Contributor.name, BookContributor.role).join(
                        BookContributor,
                        BookContributor.contributor_id == Contributor.id,
                    )
                )
            }
            assert ("鲁迅", "author") in roles
    finally:
        engine.dispose()


def test_create_book_defaults_to_unread(browser: TestClient) -> None:
    body = book_payload()
    body.pop("readingStatus")
    response = browser.post(BOOKS, json=body, headers=challenge(browser))
    assert response.status_code == 201, response.text
    assert response.json()["readingStatus"] == "unread"


def test_list_books_searches_filters_sorts_and_paginates(
    paths: tuple[Path, Path], browser: TestClient
) -> None:
    alpha = create_book(
        browser,
        title="Alpha",
        authors=["Author One"],
        translators=["Translator One"],
        isbn="978-0-306-40615-7",
        readingStatus="unread",
        tags=["fiction"],
        shelves=["Shelf A"],
    )
    beta = create_book(
        browser,
        title="Beta",
        authors=["Author Two"],
        isbn="0306406152",
        readingStatus="reading",
        tags=["science"],
        shelves=["Shelf B"],
    )
    create_book(
        browser,
        title="Gamma",
        authors=["Author One"],
        isbn=None,
        readingStatus="done",
        tags=["fiction"],
        shelves=["Shelf A"],
    )

    engine = create_sqlite_engine(paths[0])
    try:
        with Session(engine) as session, session.begin():
            session.add(
                BookCopy(
                    id=str(uuid4()),
                    book_id=alpha["id"],
                    location_id=None,
                    acquired_at=None,
                    note="副本",
                )
            )
            root_id = str(uuid4())
            object_id = str(uuid4())
            session.add(
                StorageRoot(
                    id=root_id,
                    name="Test root",
                    root_path="/tmp/acbooks-books-test",
                    mode="managed",
                )
            )
            session.flush()
            session.add(
                StorageObject(
                    id=object_id,
                    root_id=root_id,
                    relative_path="beta.epub",
                    size_bytes=123,
                    sha256=None,
                    file_status="present",
                    mtime_ms=0,
                )
            )
            session.flush()
            session.add(
                BookFile(
                    id=str(uuid4()),
                    book_id=beta["id"],
                    storage_object_id=object_id,
                    format="epub",
                )
            )
        with Session(engine) as session:
            shelf_a = session.scalar(
                select(Shelf.id).where(Shelf.name == "Shelf A")
            )
        assert shelf_a is not None
    finally:
        engine.dispose()

    page = browser.get(BOOKS)
    assert page.status_code == 200
    assert page.json()["total"] == 3
    assert page.json()["limit"] == 20
    assert page.json()["offset"] == 0

    assert [
        item["title"] for item in browser.get(BOOKS, params={"q": "Alpha"}).json()["items"]
    ] == ["Alpha"]
    assert (
        browser.get(BOOKS, params={"q": "Author One"}).json()["total"] == 2
    )
    assert (
        browser.get(BOOKS, params={"q": "Translator One"}).json()["total"] == 1
    )
    assert (
        browser.get(BOOKS, params={"q": "978-0-306-40615-7"}).json()["total"] == 1
    )
    assert (
        browser.get(BOOKS, params={"readingStatus": "reading"}).json()["total"]
        == 1
    )
    assert browser.get(BOOKS, params={"tag": "fiction"}).json()["total"] == 2
    assert browser.get(BOOKS, params={"shelfId": shelf_a}).json()["total"] == 2
    assert browser.get(BOOKS, params={"mediaType": "physical"}).json()["total"] == 1
    assert browser.get(BOOKS, params={"mediaType": "digital"}).json()["total"] == 1
    assert browser.get(BOOKS, params={"format": "epub"}).json()["total"] == 1

    titles = [
        item["title"]
        for item in browser.get(
            BOOKS, params={"sort": "title", "order": "asc"}
        ).json()["items"]
    ]
    assert titles == ["Alpha", "Beta", "Gamma"]
    authors = [
        item["authors"][0]
        for item in browser.get(
            BOOKS, params={"sort": "author", "order": "asc"}
        ).json()["items"]
    ]
    assert authors == ["Author One", "Author One", "Author Two"]

    first_page = browser.get(BOOKS, params={"limit": 2, "offset": 0}).json()
    assert len(first_page["items"]) == 2
    assert first_page["total"] == 3
    second_page = browser.get(BOOKS, params={"limit": 2, "offset": 2}).json()
    assert len(second_page["items"]) == 1
    assert browser.get(BOOKS, params={"offset": 1_000_001}).status_code == 422

    items = {item["id"]: item for item in page.json()["items"]}
    assert items[alpha["id"]]["copyCount"] == 1
    assert items[alpha["id"]]["fileCount"] == 0
    assert items[beta["id"]]["fileCount"] == 1
    assert items[beta["id"]]["formats"] == ["epub"]

    alpha_detail = browser.get(f"{BOOKS}/{alpha['id']}").json()
    assert len(alpha_detail["copies"]) == 1
    assert alpha_detail["copies"][0]["note"] == "副本"
    assert alpha_detail["copies"][0]["locationName"] is None
    beta_detail = browser.get(f"{BOOKS}/{beta['id']}").json()
    assert beta_detail["files"] == [
        {
            "id": beta_detail["files"][0]["id"],
            "format": "epub",
            "fileStatus": "present",
            "sizeBytes": 123,
        }
    ]


def filter_query(*conditions: dict[str, Any], combinator: str = "ALL") -> str:
    return json.dumps(
        {"combinator": combinator, "conditions": list(conditions)}
    )


def test_smart_filters_compile_all_any_and_typed_operators(
    paths: tuple[Path, Path], browser: TestClient
) -> None:
    alpha = create_book(
        browser,
        title="Alpha",
        authors=["Author One"],
        isbn="978-0-306-40615-7",
        readingStatus="unread",
        tags=["fiction"],
        shelves=["Shelf A"],
    )
    beta = create_book(
        browser,
        title="Beta",
        authors=["Author Two"],
        isbn=None,
        readingStatus="reading",
        tags=["science"],
        shelves=["Shelf B"],
    )
    create_book(
        browser,
        title="Gamma",
        authors=["Author One"],
        isbn=None,
        readingStatus="done",
        tags=["fiction"],
        shelves=["Shelf A"],
    )

    engine = create_sqlite_engine(paths[0])
    try:
        with Session(engine) as session, session.begin():
            session.add(
                BookCopy(
                    id=str(uuid4()),
                    book_id=alpha["id"],
                    location_id=None,
                    acquired_at=None,
                    note=None,
                )
            )
            root_id = str(uuid4())
            object_id = str(uuid4())
            session.add(
                StorageRoot(
                    id=root_id,
                    name="Root",
                    root_path="/tmp/acbooks-filter-test",
                    mode="managed",
                )
            )
            session.flush()
            session.add(
                StorageObject(
                    id=object_id,
                    root_id=root_id,
                    relative_path="beta.epub",
                    size_bytes=10,
                    sha256=None,
                    file_status="present",
                    mtime_ms=0,
                )
            )
            session.flush()
            session.add(
                BookFile(
                    id=str(uuid4()),
                    book_id=beta["id"],
                    storage_object_id=object_id,
                    format="epub",
                )
            )
    finally:
        engine.dispose()

    def total(query: str, **params: object) -> int:
        response = browser.get(
            BOOKS, params={"filters": query, **params}
        )
        assert response.status_code == 200, response.text
        return int(response.json()["total"])

    assert total(
        filter_query(
            {"field": "tag", "operator": "equals", "value": "fiction"},
            {"field": "readingStatus", "operator": "equals", "value": "unread"},
        )
    ) == 1
    assert total(
        filter_query(
            {"field": "title", "operator": "contains", "value": "Alpha"},
            {"field": "title", "operator": "contains", "value": "Gamma"},
            combinator="ANY",
        )
    ) == 2
    assert total(
        filter_query(
            {"field": "tag", "operator": "not_equals", "value": "fiction"}
        )
    ) == 1
    assert total(
        filter_query(
            {"field": "format", "operator": "equals", "value": "EPUB"}
        )
    ) == 1
    assert total(
        filter_query(
            {
                "field": "copyCount",
                "operator": "greater_or_equal",
                "value": "1",
            }
        )
    ) == 1
    assert total(
        filter_query(
            {"field": "fileCount", "operator": "is_empty"}
        )
    ) == 2
    created_date = datetime.fromtimestamp(
        alpha["createdAt"] / 1000, tz=UTC
    ).date().isoformat()
    assert total(
        filter_query(
            {
                "field": "createdAt",
                "operator": "between",
                "value": [created_date, created_date],
            }
        )
    ) == 3
    assert total(
        filter_query(
            {"field": "hasCover", "operator": "is_false"}
        )
    ) == 3
    assert total(
        filter_query(
            {
                "field": "isbn",
                "operator": "equals",
                "value": "978-0-306-40615-7",
            }
        )
    ) == 1
    assert total(
        filter_query(
            {
                "field": "author",
                "operator": "equals",
                "value": "author one",
            }
        )
    ) == 2
    assert total(filter_query()) == 3


@pytest.mark.parametrize(
    "payload",
    [
        "{not-json",
        json.dumps(
            {
                "combinator": "ALL",
                "conditions": [
                    {"field": "unknown", "operator": "equals", "value": "x"}
                ],
            }
        ),
        json.dumps(
            {
                "combinator": "ALL",
                "conditions": [
                    {"field": "title", "operator": "between", "value": "x"}
                ],
            }
        ),
        json.dumps(
            {
                "combinator": "ALL",
                "conditions": [
                    {
                        "field": "copyCount",
                        "operator": "greater_than",
                        "value": "not-a-number",
                    }
                ],
            }
        ),
        json.dumps(
            {
                "combinator": "ALL",
                "conditions": [
                    {
                        "field": "copyCount",
                        "operator": "between",
                        "value": ["5", "1"],
                    }
                ],
            }
        ),
        json.dumps(
            {
                "combinator": "ALL",
                "conditions": [
                    {
                        "field": "createdAt",
                        "operator": "between",
                        "value": ["2026-12-31", "2026-01-01"],
                    }
                ],
            }
        ),
        json.dumps(
            {
                "combinator": "ALL",
                "conditions": [
                    {"field": "title", "operator": "is_empty", "value": None}
                ]
                * 31,
            }
        ),
    ],
)
def test_smart_filters_reject_invalid_rules(
    browser: TestClient, payload: str
) -> None:
    response = browser.get(BOOKS, params={"filters": payload})
    assert_problem(response, 422, "INVALID_BOOK_FILTER")


def test_smart_filter_equals_and_patterns_treat_values_literally(
    browser: TestClient,
) -> None:
    literal = create_book(browser, title="100%_match", isbn=None)
    create_book(browser, title="100Xmatch", isbn=None)

    def titles(filters: str) -> list[str]:
        response = browser.get(BOOKS, params={"filters": filters})
        assert response.status_code == 200, response.text
        return [item["title"] for item in response.json()["items"]]

    assert titles(
        filter_query(
            {"field": "title", "operator": "equals", "value": "100%_match"}
        )
    ) == [literal["title"]]
    assert titles(
        filter_query(
            {
                "field": "title",
                "operator": "not_equals",
                "value": "100%_match",
            }
        )
    ) == ["100Xmatch"]
    assert titles(
        filter_query(
            {"field": "title", "operator": "contains", "value": "%_"}
        )
    ) == [literal["title"]]
    assert titles(
        filter_query(
            {
                "field": "title",
                "operator": "starts_with",
                "value": "100%",
            }
        )
    ) == [literal["title"]]
    assert titles(
        filter_query(
            {
                "field": "title",
                "operator": "ends_with",
                "value": "_match",
            }
        )
    ) == [literal["title"]]


def test_smart_filter_dates_use_utc_day_boundaries(
    paths: tuple[Path, Path], browser: TestClient
) -> None:
    book = create_book(browser, title="UTC boundary", isbn=None)
    timestamp = int(
        datetime(2025, 1, 1, 23, 30, tzinfo=UTC).timestamp() * 1000
    )
    engine = create_sqlite_engine(paths[0])
    try:
        with Session(engine) as session, session.begin():
            stored = session.get(Book, book["id"])
            assert stored is not None
            stored.created_at = timestamp
            stored.updated_at = timestamp
    finally:
        engine.dispose()

    def total(day: str) -> int:
        response = browser.get(
            BOOKS,
            params={
                "filters": filter_query(
                    {
                        "field": "createdAt",
                        "operator": "equals",
                        "value": day,
                    }
                )
            },
        )
        assert response.status_code == 200, response.text
        return int(response.json()["total"])

    assert total("2025-01-01") == 1
    assert total("2025-01-02") == 0


def test_filter_schema_and_options_use_real_library_data(
    browser: TestClient,
) -> None:
    create_book(
        browser,
        title="Alpha",
        authors=["Author One"],
        readingStatus="unread",
        tags=["fiction"],
        shelves=["Shelf A"],
    )
    create_book(
        browser,
        title="Beta",
        authors=["Author One"],
        readingStatus="reading",
        tags=["fiction", "science"],
        shelves=["Shelf B"],
    )
    create_book(
        browser,
        title="Gamma",
        authors=["Author Two"],
        readingStatus="done",
        tags=["science"],
        shelves=["Shelf A"],
    )

    schema_response = browser.get(f"{BOOKS}/filter-schema")
    assert schema_response.status_code == 200
    assert schema_response.headers["cache-control"] == "no-store"
    schema = schema_response.json()
    assert schema["maxConditions"] == 30
    fields = {field["key"]: field for field in schema["fields"]}
    assert fields["title"]["type"] == "text"
    assert fields["author"]["optionSource"] == "authors"
    assert "contains" in fields["title"]["operators"]
    assert fields["readingStatus"]["options"] == [
        {"value": "unread", "label": "Unread", "count": None},
        {"value": "reading", "label": "Reading", "count": None},
        {"value": "done", "label": "Read", "count": None},
    ]

    tags = browser.get(
        f"{BOOKS}/filter-options",
        params={"source": "tags", "query": "fic"},
    )
    assert tags.status_code == 200
    assert tags.headers["cache-control"] == "no-store"
    assert tags.json()["options"] == [
        {"value": "fiction", "label": "fiction", "count": 2}
    ]
    assert tags.json()["hasMore"] is False
    assert tags.json()["indexReady"] is True

    authors = browser.get(
        f"{BOOKS}/filter-options", params={"source": "authors"}
    ).json()
    assert authors["options"][0] == {
        "value": "Author One",
        "label": "Author One",
        "count": 2,
    }
    shelves = browser.get(
        f"{BOOKS}/filter-options",
        params={"source": "shelves", "query": "shelf"},
    ).json()
    assert {option["value"] for option in shelves["options"]} == {
        "Shelf A",
        "Shelf B",
    }
    limited = browser.get(
        f"{BOOKS}/filter-options",
        params={"source": "authors", "limit": 1},
    ).json()
    assert len(limited["options"]) == 1
    assert limited["hasMore"] is True

    assert_problem(
        browser.get(
            f"{BOOKS}/filter-options", params={"source": "unknown"}
        ),
        422,
        "VALIDATION_ERROR",
    )


def test_patch_book_preserves_copies_files_and_reading_data(
    paths: tuple[Path, Path], browser: TestClient
) -> None:
    book = create_book(browser)
    book_id = book["id"]
    engine = create_sqlite_engine(paths[0])
    try:
        with Session(engine) as session, session.begin():
            session.add(
                BookCopy(
                    id=str(uuid4()),
                    book_id=book_id,
                    location_id=None,
                    acquired_at=None,
                    note="保留副本",
                )
            )
            session.add(
                ReadingSession(
                    id=str(uuid4()),
                    book_id=book_id,
                    book_file_id=None,
                    device_id=None,
                    started_at=1_000,
                    ended_at=1_001_000,
                    active_seconds=1_000,
                    source="manual",
                )
            )
    finally:
        engine.dispose()

    sleep(0.002)
    response = browser.patch(
        f"{BOOKS}/{book_id}",
        json={
            "title": "新标题",
            "description": "新简介",
            "authors": ["新作者"],
            "translators": [],
            "isbn": None,
            "readingStatus": "done",
            "tags": ["新标签"],
            "shelves": ["新书架"],
        },
        headers=challenge(browser),
    )
    assert response.status_code == 200, response.text
    updated = response.json()
    assert updated["title"] == "新标题"
    assert updated["description"] == "新简介"
    assert updated["authors"] == ["新作者"]
    assert updated["translators"] == []
    assert updated["isbn"] is None
    assert updated["identifiers"] == []
    assert updated["readingStatus"] == "done"
    assert updated["tags"] == ["新标签"]
    assert updated["shelves"] == ["新书架"]
    assert updated["copyCount"] == 1
    assert updated["updatedAt"] >= updated["createdAt"]

    partial = browser.patch(
        f"{BOOKS}/{book_id}",
        json={"readingStatus": "reading"},
        headers=challenge(browser),
    )
    assert partial.status_code == 200
    assert partial.json()["title"] == "新标题"
    assert partial.json()["authors"] == ["新作者"]
    assert partial.json()["tags"] == ["新标签"]
    assert partial.json()["copyCount"] == 1

    engine = create_sqlite_engine(paths[0])
    try:
        with Session(engine) as session:
            assert session.scalar(select(func.count()).select_from(BookCopy)) == 1
            assert (
                session.scalar(select(func.count()).select_from(ReadingSession))
                == 1
            )
            names = set(
                session.execute(
                    select(Contributor.name)
                    .join(
                        BookContributor,
                        BookContributor.contributor_id == Contributor.id,
                    )
                    .where(
                        BookContributor.book_id == book_id,
                        BookContributor.role == "author",
                    )
                ).scalars()
            )
            assert names == {"新作者"}
    finally:
        engine.dispose()

    assert_problem(
        browser.patch(
            f"{BOOKS}/{book_id}", json={}, headers=challenge(browser)
        ),
        422,
        "VALIDATION_ERROR",
    )
    assert_problem(
        browser.patch(
            f"{BOOKS}/{book_id}",
            json={"title": None},
            headers=challenge(browser),
        ),
        422,
        "VALIDATION_ERROR",
    )
    assert_problem(
        browser.patch(
            f"{BOOKS}/{book_id}",
            json={"isbn": "not-an-isbn"},
            headers=challenge(browser),
        ),
        422,
        "VALIDATION_ERROR",
    )
    assert_problem(
        browser.patch(
            f"{BOOKS}/{uuid4()}",
            json={"title": "Missing"},
            headers=challenge(browser),
        ),
        404,
        "BOOK_NOT_FOUND",
    )


def test_books_require_session_and_csrf(
    paths: tuple[Path, Path], browser: TestClient
) -> None:
    with make_client(paths) as anonymous:
        assert_problem(anonymous.get(BOOKS), 401, "UNAUTHORIZED")
        assert_problem(
            anonymous.post(BOOKS, json=book_payload()), 401, "UNAUTHORIZED"
        )

    assert browser.get(BOOKS).status_code == 200
    assert_problem(
        browser.post(BOOKS, json=book_payload()),
        403,
        "FORBIDDEN",
    )

    headers = challenge(browser)
    headers["Origin"] = "https://evil.example"
    assert_problem(
        browser.post(BOOKS, json=book_payload(), headers=headers),
        403,
        "FORBIDDEN",
    )
    assert_problem(
        browser.patch(
            f"{BOOKS}/{uuid4()}", json={"title": "Blocked"}, headers={"Origin": ORIGIN}
        ),
        403,
        "FORBIDDEN",
    )
    assert_problem(
        browser.delete(f"{BOOKS}/{uuid4()}"), 403, "FORBIDDEN"
    )


@pytest.mark.parametrize(
    "changes",
    [
        {"title": ""},
        {"title": "   "},
        {"readingStatus": "finished"},
        {"isbn": "not-an-isbn"},
        {"authors": [""]},
        {"unexpected": "value"},
    ],
)
def test_create_book_validation(
    browser: TestClient, changes: dict[str, object]
) -> None:
    response = browser.post(
        BOOKS, json=book_payload(**changes), headers=challenge(browser)
    )
    assert_problem(response, 422, "VALIDATION_ERROR")


def test_search_treats_like_wildcards_as_literals(browser: TestClient) -> None:
    create_book(browser, title="100% Real", isbn=None)
    create_book(browser, title="Plain", isbn=None)

    response = browser.get(BOOKS, params={"q": "%"})
    assert response.status_code == 200
    assert response.json()["total"] == 1
    assert response.json()["items"][0]["title"] == "100% Real"


def test_unicode_names_use_casefold_matching(
    paths: tuple[Path, Path], browser: TestClient
) -> None:
    create_book(
        browser,
        title="One",
        authors=["Straße"],
        translators=["Übersetzer"],
        tags=["Straße"],
        shelves=["Straße"],
        isbn=None,
    )
    create_book(
        browser,
        title="Two",
        authors=["STRASSE"],
        translators=["üBERSETZER"],
        tags=["strasse"],
        shelves=["strasse"],
        isbn=None,
    )

    assert browser.get(BOOKS, params={"q": "STRASSE"}).json()["total"] == 2
    assert browser.get(BOOKS, params={"q": "übersetzer"}).json()["total"] == 2
    assert browser.get(BOOKS, params={"tag": "STRASSE"}).json()["total"] == 2

    engine = create_sqlite_engine(paths[0])
    try:
        with Session(engine) as session:
            assert session.scalar(select(func.count()).select_from(Contributor)) == 2
            assert session.scalar(select(func.count()).select_from(Tag)) == 1
            assert session.scalar(select(func.count()).select_from(Shelf)) == 1
    finally:
        engine.dispose()


def test_books_openapi_contract(browser: TestClient) -> None:
    schema = browser.get("/openapi.json").json()
    paths = schema["paths"]
    collection = paths[BOOKS]
    item = paths[f"{BOOKS}/{{bookId}}"]

    assert collection["get"]["operationId"] == "listBooks"
    assert collection["post"]["operationId"] == "createBook"
    assert item["get"]["operationId"] == "getBook"
    assert item["patch"]["operationId"] == "updateBook"
    assert item["delete"]["operationId"] == "deleteBook"
    assert (
        paths[f"{BOOKS}/batch-update"]["patch"]["operationId"]
        == "batchUpdateBooks"
    )
    assert (
        paths[f"{BOOKS}/batch-delete"]["post"]["operationId"]
        == "batchDeleteBooks"
    )
    assert (
        paths[f"{BOOKS}/filter-schema"]["get"]["operationId"]
        == "getBookFilterSchema"
    )
    assert (
        paths[f"{BOOKS}/filter-options"]["get"]["operationId"]
        == "getBookFilterOptions"
    )
    assert (
        paths[f"{BOOKS}/{{bookId}}/cover"]["get"]["operationId"]
        == "getBookCover"
    )

    security = {
        name
        for requirement in item["get"]["security"]
        for name in requirement
    }
    assert "WebSession" in security

    post_security = {
        name
        for requirement in collection["post"]["security"]
        for name in requirement
    }
    assert "WebSession" in post_security
    assert "Location" in collection["post"]["responses"]["201"]["headers"]

    query_parameters = {
        parameter["name"] for parameter in collection["get"]["parameters"]
    }
    assert {
        "q",
        "readingStatus",
        "tag",
        "shelfId",
        "mediaType",
        "format",
        "filters",
        "sort",
        "order",
        "limit",
        "offset",
    } <= query_parameters
    path_parameters = {
        parameter["name"] for parameter in item["get"]["parameters"]
    }
    assert "bookId" in path_parameters

    schemas = schema["components"]["schemas"]
    assert "BookCreate" in schemas
    assert "BookUpdate" in schemas
    assert "BookDetail" in schemas
    assert "BookPage" in schemas
    assert "BookFilterSchema" in schemas
    assert "BookFilterOptionPage" in schemas
    assert "title" in schemas["BookCreate"]["required"]
    assert "readingStatus" in schemas["BookCreate"]["properties"]
