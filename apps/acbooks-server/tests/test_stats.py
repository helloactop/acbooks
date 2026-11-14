from datetime import datetime
from pathlib import Path
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from test_book_management import management
from test_books import BOOKS, create_book
from test_setup import assert_problem, challenge

__all__ = ["management"]


def millis(value: str) -> int:
    return int(datetime.fromisoformat(value).timestamp() * 1000)


def add(client: TestClient, book_id: str, start: str, end: str, seconds: int) -> str:
    response = client.post(
        f"{BOOKS}/{book_id}/reading-sessions",
        headers=challenge(client),
        json={
            "deviceId": str(uuid4()),
            "mutationId": str(uuid4()),
            "startedAt": millis(start),
            "endedAt": millis(end),
            "activeSeconds": seconds,
        },
    )
    assert response.status_code == 200, response.text
    return str(response.json()["item"]["id"])


def test_stats_scope_overlap_gap_and_mutations(
    management: tuple[TestClient, Path, Path],
) -> None:
    client, _, _ = management
    book = create_book(client)["id"]
    other = create_book(client)["id"]
    add(client, book, "2026-11-14T23:30+08:00", "2026-11-15T00:30+08:00", 1800)
    add(client, other, "2026-11-15T00:00+08:00", "2026-11-15T00:15+08:00", 900)
    newest = add(client, book, "2026-11-15T01:00+08:00", "2026-11-15T01:10+08:00", 600)
    query = {"startDate": "2026-11-15", "endDate": "2026-11-16", "timeZone": "Asia/Shanghai"}
    response = client.get("/api/v1/stats/overview", params=query)
    assert response.status_code == 200, response.text
    assert response.headers["cache-control"] == "no-store"
    overview = response.json()
    assert overview["activeSeconds"] == 1950
    assert overview["sessionCount"] == 2
    assert overview["averageGapSeconds"] == 1800
    assert overview["hourlySeconds"][:2] == [1350, 600]
    daily_url = "/api/v1/stats/daily"
    daily_query = {"day": query["startDate"], "timeZone": query["timeZone"]}
    daily = client.get(daily_url, params=daily_query)
    assert daily.status_code == 200, daily.text
    assert daily.json()["activeSeconds"] == 1950
    assert daily.json()["sessionCount"] == 2
    assert {item["id"]: item["activeSeconds"] for item in daily.json()["books"]["items"]} == {
        book: 1050,
        other: 900,
    }
    scoped = client.get(daily_url, params={**daily_query, "bookId": book}).json()
    assert scoped["activeSeconds"] == 1500
    assert scoped["books"]["total"] == 1
    second = client.get(daily_url, params={**daily_query, "limit": 1, "offset": 1}).json()
    assert second["books"]["total"] == 2
    assert second["books"]["items"][0]["id"] == other
    assert overview["typeSeconds"] == {"manual": 1950, "electronic": 0, "unknown": 0}
    assert overview["days"] == [
        {"date": "2026-11-15", "sessionCount": 2, "activeSeconds": 1950, "averageGapSeconds": 1800}
    ]
    path = f"{BOOKS}/{book}/stats"
    assert client.get(path, params=query).json()["activeSeconds"] == 1500
    timeline = client.get("/api/v1/stats/timeline", params={**query, "bookId": book, "limit": "1"})
    assert timeline.status_code == 200, timeline.text
    assert timeline.json()["total"] == 2
    assert timeline.json()["items"][0]["id"] == newest
    mutation = f"{BOOKS}/{book}/reading-sessions/{newest}"
    assert (
        client.patch(mutation, headers=challenge(client), json={"activeSeconds": 300}).status_code
        == 200
    )
    assert client.get(path, params=query).json()["activeSeconds"] == 1200
    assert client.delete(mutation, headers=challenge(client)).status_code == 204
    assert client.get(path, params=query).json()["activeSeconds"] == 900
    assert_problem(client.get(f"{BOOKS}/{uuid4()}/stats", params=query), 404, "BOOK_NOT_FOUND")
    assert_problem(
        client.get("/api/v1/stats/overview", params={**query, "timeZone": "invalid"}),
        422,
        "VALIDATION_ERROR",
    )
    assert_problem(
        client.get("/api/v1/stats/overview", params={**query, "endDate": "2028-01-01"}),
        422,
        "VALIDATION_ERROR",
    )


def test_history_and_inactive_timeline(management: tuple[TestClient, Path, Path]) -> None:
    client, _, _ = management
    book = create_book(client)["id"]
    other = create_book(client)["id"]
    add(client, other, "2000-01-01T12:00Z", "2000-01-01T12:30Z", 1800)
    add(client, book, "2022-01-01T00:30Z", "2022-01-01T01:30Z", 3600)
    inactive = add(client, book, "2024-01-01T00:00Z", "2024-01-01T00:00Z", 0)
    query = {"timeZone": "America/New_York"}
    overview = client.get(f"{BOOKS}/{book}/stats", params=query)
    assert overview.status_code == 200, overview.text
    assert overview.json()["startDate"] == "2021-12-31"
    assert overview.json()["activeSeconds"] == 3600
    assert client.get("/api/v1/stats/overview", params=query).json()["activeSeconds"] == 5400

    extended = {**query, "startDate": "2021-12-31", "endDate": "2024-01-02"}
    assert client.get(f"{BOOKS}/{book}/stats", params=extended).json()["sessionCount"] == 1
    assert_problem(
        client.get("/api/v1/stats/timeline", params={**query, "startDate": "2022-01-01"}),
        422,
        "VALIDATION_ERROR",
    )
    timeline = "/api/v1/stats/timeline"
    scoped = {**query, "bookId": book}
    assert client.get(timeline, params=scoped).json()["total"] == 1
    page = client.get(timeline, params={**scoped, "includeInactive": "true", "limit": 1}).json()
    assert page["total"] == 2
    assert page["items"][0]["id"] == inactive


@pytest.mark.parametrize(
    ("day", "next_day", "start", "end", "seconds", "hour_one", "hour_two"),
    [
        (
            "2026-03-08",
            "2026-03-09",
            "2026-03-08T01:00-05:00",
            "2026-03-08T04:00-04:00",
            7200,
            3600,
            0,
        ),
        (
            "2026-11-01",
            "2026-11-02",
            "2026-11-01T01:00-04:00",
            "2026-11-01T02:00-05:00",
            7200,
            7200,
            0,
        ),
    ],
)
def test_stats_dst(
    management: tuple[TestClient, Path, Path],
    day: str,
    next_day: str,
    start: str,
    end: str,
    seconds: int,
    hour_one: int,
    hour_two: int,
) -> None:
    client, _, _ = management
    book = create_book(client)["id"]
    add(client, book, start, end, seconds)
    response = client.get(
        f"{BOOKS}/{book}/stats",
        params={
            "startDate": day,
            "endDate": next_day,
            "timeZone": "America/New_York",
        },
    )
    assert response.status_code == 200, response.text
    result = response.json()
    assert result["activeSeconds"] == seconds
    assert result["hourlySeconds"][1:3] == [hour_one, hour_two]
