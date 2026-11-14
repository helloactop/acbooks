import hashlib
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from uuid import uuid4

from fastapi.testclient import TestClient
from test_book_management import management
from test_books import create_book
from test_files import PDF
from test_web_auth import CREDENTIALS

__all__ = ["management"]


def test_android_file_link_resume_and_sync(management: tuple[TestClient, Path, Path]) -> None:
    client, database, _ = management
    book = create_book(client)["id"]
    other = create_book(client)["id"]
    client.cookies.clear()
    digest = hashlib.sha256(PDF).hexdigest()
    matches = "/api/v1/files/matches"
    assert client.get(matches, params={"sha256": digest}).status_code == 401
    token = client.post("/api/v1/auth/device-login", json=CREDENTIALS)
    assert token.status_code == 200, token.text
    headers = {"Authorization": f"Bearer {token.json()['accessToken']}"}

    def upload(book_id: str, reuse: bool = True):
        return client.post(
            "/api/v1/uploads",
            params={"reuseExisting": reuse},
            headers=headers,
            data={"bookId": book_id},
            files={"file": ("book.pdf", PDF, "application/pdf")},
        )

    with ThreadPoolExecutor(max_workers=2) as workers:
        uploads = list(workers.map(lambda _: upload(book), range(2)))
    assert sorted(response.status_code for response in uploads) == [200, 201]
    file_id = uploads[0].json()["id"]
    assert uploads[1].json()["id"] == file_id
    assert upload(book, reuse=False).status_code == 409
    assert upload(other).status_code == 201

    assert client.get(matches, headers=headers, params={"sha256": "bad"}).status_code == 422
    first = client.get(matches, headers=headers, params={"sha256": digest.upper(), "limit": 1})
    assert first.status_code == 200
    assert first.headers["cache-control"] == "no-store"
    second = client.get(matches, headers=headers, params={"sha256": digest, "offset": 1})
    assert {first.json()["items"][0]["bookId"], second.json()["items"][0]["bookId"]} == {
        book,
        other,
    }
    assert first.json()["nextOffset"] == 1 and second.json()["nextOffset"] is None
    scoped = client.get(matches, headers=headers, params={"sha256": digest, "bookId": book}).json()
    assert [item["id"] for item in scoped["items"]] == [file_id]

    url = uploads[0].headers["location"]
    assert client.get(url).status_code == 401
    initial = client.get(url, headers={**headers, "Range": "bytes=0-9"})
    assert initial.status_code == 206
    assert initial.headers["x-content-sha256"] == digest
    etag = initial.headers["etag"]
    rest = client.get(url, headers={**headers, "Range": "bytes=10-", "If-Range": etag})
    assert rest.status_code == 206
    assert rest.headers["content-range"] == f"bytes 10-{len(PDF) - 1}/{len(PDF)}"
    assert hashlib.sha256(initial.content + rest.content).hexdigest() == digest
    wrong_version = client.get(
        url,
        headers={**headers, "Range": "bytes=10-", "If-Range": '"old"'},
    )
    assert wrong_version.status_code == 200 and wrong_version.content == PDF

    snapshot = client.get("/api/v1/sync/snapshot", headers=headers).json()
    position = {
        "deviceId": str(uuid4()),
        "mutations": [
            {
                "action": "reading_position.save",
                "entityId": file_id,
                "baseRevision": 0,
                "mutationId": str(uuid4()),
                "data": {"locator": {"page": 3}},
            }
        ],
    }
    saved = client.post("/api/v1/sync/mutations", headers=headers, json=position)
    assert saved.status_code == 200, saved.text
    assert saved.json()["receipts"][0]["status"] == "accepted"
    second_login = client.post("/api/v1/auth/device-login", json=CREDENTIALS)
    assert second_login.status_code == 200, second_login.text
    second_device = {"Authorization": f"Bearer {second_login.json()['accessToken']}"}
    delta = client.get(
        "/api/v1/sync/changes",
        headers=second_device,
        params={"after": snapshot["through"]},
    ).json()["items"]
    assert delta[0]["entityId"] == file_id
    assert delta[0]["data"]["locator"] == {"page": 3}

    for path in (database.parent / "library" / book).glob("*.pdf"):
        path.write_bytes(PDF + b"\n")
    changed = client.get(url, headers={**headers, "Range": "bytes=10-", "If-Range": etag})
    assert changed.status_code == 200
    assert changed.content == PDF + b"\n"
    assert changed.headers["etag"].startswith("W/")
    assert "x-content-sha256" not in changed.headers
