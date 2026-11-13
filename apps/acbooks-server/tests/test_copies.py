import json
from collections.abc import Iterator
from pathlib import Path
from typing import Any
from uuid import uuid4

import pytest
from alembic import command
from fastapi.testclient import TestClient
from test_books import BOOKS, book_payload, create_book
from test_database import migration_config
from test_setup import (
    SETUP,
    assert_problem,
    challenge,
    make_client,
    payload,
)
from test_web_auth import CREDENTIALS, LOGIN

from app.db.session import create_sqlite_engine
from app.modules.auth.provision import main as provision_installation

LOCATIONS = "/api/v1/locations"


@pytest.fixture
def env(tmp_path: Path) -> Iterator[tuple[TestClient, Path]]:
    database = tmp_path / "acbooks.db"
    code_path = tmp_path / "secrets" / "setup-code"
    command.upgrade(migration_config(database), "head")
    provision_installation(database, code_path)
    with make_client((database, code_path)) as client:
        assert (
            client.post(
                SETUP, json=payload(code_path), headers=challenge(client)
            ).status_code
            == 201
        )
        assert (
            client.post(LOGIN, json=CREDENTIALS, headers=challenge(client)).status_code
            == 200
        )
        yield client, database


def create_location(
    client: TestClient, name: str, parent_id: str | None = None
) -> dict[str, Any]:
    body: dict[str, Any] = {"name": name}
    if parent_id is not None:
        body["parentId"] = parent_id
    response = client.post(LOCATIONS, json=body, headers=challenge(client))
    assert response.status_code == 201, response.text
    result: dict[str, Any] = response.json()
    return result


def create_copy(client: TestClient, book_id: str, **fields: Any) -> dict[str, Any]:
    response = client.post(
        f"{BOOKS}/{book_id}/copies", json=fields, headers=challenge(client)
    )
    assert response.status_code == 201, response.text
    result: dict[str, Any] = response.json()
    return result


def filtered_titles(client: TestClient, condition: dict[str, Any]) -> set[str]:
    response = client.get(
        BOOKS,
        params={"filters": json.dumps({"combinator": "ALL", "conditions": [condition]})},
    )
    assert response.status_code == 200, response.text
    return {item["title"] for item in response.json()["items"]}


def test_location_hierarchy_crud(env: tuple[TestClient, Path]) -> None:
    client, _database = env
    room = create_location(client, "书房")
    shelf = create_location(client, "书架A", room["id"])
    level = create_location(client, "第一层", shelf["id"])
    assert room["parentId"] is None
    assert shelf["parentId"] == room["id"]
    assert level["parentId"] == shelf["id"]
    assert room["copyCount"] == 0

    listing = client.get(LOCATIONS)
    assert listing.status_code == 200
    assert listing.headers["cache-control"] == "no-store"
    assert {node["id"] for node in listing.json()} == {
        room["id"],
        shelf["id"],
        level["id"],
    }

    renamed = client.patch(
        f"{LOCATIONS}/{shelf['id']}",
        json={"name": "书架B"},
        headers=challenge(client),
    )
    assert renamed.status_code == 200
    assert renamed.json()["name"] == "书架B"
    assert renamed.json()["parentId"] == room["id"]

    moved = client.patch(
        f"{LOCATIONS}/{level['id']}",
        json={"parentId": None},
        headers=challenge(client),
    )
    assert moved.status_code == 200
    assert moved.json()["parentId"] is None


def test_location_cycle_is_rejected(env: tuple[TestClient, Path]) -> None:
    client, _database = env
    top = create_location(client, "房间")
    middle = create_location(client, "书架", top["id"])
    leaf = create_location(client, "层", middle["id"])

    self_parent = client.patch(
        f"{LOCATIONS}/{top['id']}",
        json={"parentId": top["id"]},
        headers=challenge(client),
    )
    assert_problem(self_parent, 422, "LOCATION_CYCLE")

    descendant_parent = client.patch(
        f"{LOCATIONS}/{top['id']}",
        json={"parentId": leaf["id"]},
        headers=challenge(client),
    )
    assert_problem(descendant_parent, 422, "LOCATION_CYCLE")

    unchanged = client.get(LOCATIONS)
    by_id = {node["id"]: node for node in unchanged.json()}
    assert by_id[top["id"]]["parentId"] is None
    assert by_id[middle["id"]]["parentId"] == top["id"]


def test_location_delete_rules(env: tuple[TestClient, Path]) -> None:
    client, database = env
    room = create_location(client, "书房")
    shelf = create_location(client, "书架", room["id"])
    book = create_book(client, title="实体书")
    copy = create_copy(client, book["id"], locationId=shelf["id"], note="精装")

    blocked = client.delete(f"{LOCATIONS}/{room['id']}", headers=challenge(client))
    assert_problem(blocked, 409, "LOCATION_HAS_CHILDREN")

    removed = client.delete(f"{LOCATIONS}/{shelf['id']}", headers=challenge(client))
    assert removed.status_code == 204
    assert removed.headers["cache-control"] == "no-store"

    copies = client.get(f"{BOOKS}/{book['id']}/copies")
    assert copies.status_code == 200
    assert copies.json()[0]["id"] == copy["id"]
    assert copies.json()[0]["locationId"] is None
    assert copies.json()[0]["locationName"] is None
    assert copies.json()[0]["note"] == "精装"

    engine = create_sqlite_engine(database)
    try:
        with engine.connect() as connection:
            assert (
                connection.exec_driver_sql(
                    "SELECT COUNT(*) FROM locations"
                ).scalar_one()
                == 1
            )
    finally:
        engine.dispose()

    assert_problem(
        client.delete(f"{LOCATIONS}/{shelf['id']}", headers=challenge(client)),
        404,
        "LOCATION_NOT_FOUND",
    )


def test_location_missing_parent_and_target(env: tuple[TestClient, Path]) -> None:
    client, _database = env
    missing = str(uuid4())
    assert_problem(
        client.post(
            LOCATIONS, json={"name": "孤儿", "parentId": missing}, headers=challenge(client)
        ),
        404,
        "LOCATION_NOT_FOUND",
    )
    assert_problem(
        client.patch(
            f"{LOCATIONS}/{missing}", json={"name": "改名"}, headers=challenge(client)
        ),
        404,
        "LOCATION_NOT_FOUND",
    )
    assert_problem(
        client.delete(f"{LOCATIONS}/{missing}", headers=challenge(client)),
        404,
        "LOCATION_NOT_FOUND",
    )


def test_copy_crud(env: tuple[TestClient, Path]) -> None:
    client, _database = env
    location = create_location(client, "书房")
    book = create_book(client, title="实体书")
    copy = create_copy(
        client,
        book["id"],
        locationId=location["id"],
        acquiredAt=1_700_000_000_000,
        note="初版",
    )
    assert copy["locationId"] == location["id"]
    assert copy["locationName"] == "书房"
    assert copy["acquiredAt"] == 1_700_000_000_000
    assert copy["note"] == "初版"

    detail = client.get(f"{BOOKS}/{book['id']}")
    assert detail.json()["copyCount"] == 1
    assert detail.json()["copies"][0]["id"] == copy["id"]

    updated = client.patch(
        f"{BOOKS}/{book['id']}/copies/{copy['id']}",
        json={"note": "修订", "locationId": None, "acquiredAt": None},
        headers=challenge(client),
    )
    assert updated.status_code == 200
    assert updated.json()["note"] == "修订"
    assert updated.json()["locationId"] is None
    assert updated.json()["acquiredAt"] is None

    removed = client.delete(
        f"{BOOKS}/{book['id']}/copies/{copy['id']}", headers=challenge(client)
    )
    assert removed.status_code == 204
    assert client.get(f"{BOOKS}/{book['id']}/copies").json() == []
    assert client.get(f"{BOOKS}/{book['id']}").json()["copyCount"] == 0


def test_copy_error_paths(env: tuple[TestClient, Path]) -> None:
    client, _database = env
    book = create_book(client, title="实体书")
    other = create_book(client, title="另一本")
    copy = create_copy(client, book["id"], note="副本")
    missing = str(uuid4())

    assert_problem(
        client.post(
            f"{BOOKS}/{missing}/copies", json={}, headers=challenge(client)
        ),
        404,
        "BOOK_NOT_FOUND",
    )
    assert_problem(
        client.post(
            f"{BOOKS}/{book['id']}/copies",
            json={"locationId": missing},
            headers=challenge(client),
        ),
        404,
        "LOCATION_NOT_FOUND",
    )
    assert_problem(
        client.patch(
            f"{BOOKS}/{other['id']}/copies/{copy['id']}",
            json={"note": "越界"},
            headers=challenge(client),
        ),
        404,
        "COPY_NOT_FOUND",
    )
    assert_problem(
        client.delete(
            f"{BOOKS}/{book['id']}/copies/{missing}", headers=challenge(client)
        ),
        404,
        "COPY_NOT_FOUND",
    )
    assert_problem(
        client.get(f"{BOOKS}/{missing}/copies"), 404, "BOOK_NOT_FOUND"
    )


def test_inline_copies_on_create(env: tuple[TestClient, Path]) -> None:
    client, _database = env
    location = create_location(client, "书房")
    book = create_book(
        client,
        title="带副本的书",
        copies=[
            {"locationId": location["id"], "acquiredAt": 1_700_000_000_000, "note": "上册"},
            {"note": "下册"},
        ],
    )
    assert book["copyCount"] == 2
    assert len(book["copies"]) == 2
    by_note = {copy["note"]: copy for copy in book["copies"]}
    assert by_note["上册"]["locationName"] == "书房"
    assert by_note["上册"]["acquiredAt"] == 1_700_000_000_000
    assert by_note["下册"]["locationId"] is None


def test_inline_copies_atomic_on_invalid_location(
    env: tuple[TestClient, Path],
) -> None:
    client, _database = env
    before = client.get(BOOKS).json()["total"]
    response = client.post(
        BOOKS,
        json=book_payload(title="不会创建", copies=[{"locationId": str(uuid4())}]),
        headers=challenge(client),
    )
    assert_problem(response, 404, "LOCATION_NOT_FOUND")
    assert client.get(BOOKS).json()["total"] == before


def test_location_filter_matches_subtree(env: tuple[TestClient, Path]) -> None:
    client, _database = env
    room = create_location(client, "书房")
    shelf = create_location(client, "书架", room["id"])
    level = create_location(client, "层", shelf["id"])

    at_level = create_book(client, title="层上的书")
    create_copy(client, at_level["id"], locationId=level["id"])
    at_room = create_book(client, title="房间里的书")
    create_copy(client, at_room["id"], locationId=room["id"])
    at_shelf = create_book(client, title="书架上的书")
    create_copy(client, at_shelf["id"], locationId=shelf["id"])
    unlocated = create_book(client, title="无位置的书")
    create_copy(client, unlocated["id"])

    assert filtered_titles(
        client, {"field": "location", "operator": "equals", "value": room["id"]}
    ) == {"层上的书", "房间里的书", "书架上的书"}
    assert filtered_titles(
        client, {"field": "location", "operator": "equals", "value": shelf["id"]}
    ) == {"层上的书", "书架上的书"}
    assert filtered_titles(
        client, {"field": "location", "operator": "equals", "value": level["id"]}
    ) == {"层上的书"}
    assert filtered_titles(
        client, {"field": "location", "operator": "not_equals", "value": room["id"]}
    ) == {"无位置的书"}
    assert filtered_titles(
        client, {"field": "location", "operator": "is_not_empty"}
    ) == {"层上的书", "房间里的书", "书架上的书"}
    assert filtered_titles(
        client, {"field": "location", "operator": "is_empty"}
    ) == {"无位置的书"}


def test_filter_schema_and_options_include_locations(
    env: tuple[TestClient, Path],
) -> None:
    client, _database = env
    schema = client.get(f"{BOOKS}/filter-schema").json()
    field = next(item for item in schema["fields"] if item["key"] == "location")
    assert field["type"] == "select"
    assert field["optionSource"] == "locations"
    assert "equals" in field["operators"]

    room = create_location(client, "书房")
    shelf = create_location(client, "书架A", room["id"])
    book = create_book(client, title="实体书")
    create_copy(client, book["id"], locationId=shelf["id"])

    options = client.get(
        f"{BOOKS}/filter-options", params={"source": "locations"}
    ).json()
    assert options["source"] == "locations"
    by_value = {option["value"]: option for option in options["options"]}
    assert by_value[shelf["id"]]["label"] == "书房 / 书架A"
    assert by_value[shelf["id"]]["count"] == 1
    assert by_value[room["id"]]["label"] == "书房"
    assert by_value[room["id"]]["count"] == 0


def test_location_write_requires_csrf(env: tuple[TestClient, Path]) -> None:
    client, _database = env
    assert client.post(LOCATIONS, json={"name": "无CSRF"}).status_code == 403


def test_locations_require_session(tmp_path: Path) -> None:
    database = tmp_path / "acbooks.db"
    code_path = tmp_path / "secrets" / "setup-code"
    command.upgrade(migration_config(database), "head")
    provision_installation(database, code_path)
    with make_client((database, code_path)) as client:
        assert client.get(LOCATIONS).status_code == 401