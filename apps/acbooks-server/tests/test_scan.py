import os
from collections.abc import Iterator
from io import BytesIO
from pathlib import Path
from typing import Any
from uuid import uuid4
from zipfile import ZipFile

import pytest
from alembic import command
from fastapi.testclient import TestClient
from PIL import Image
from pypdf import PdfWriter
from pypdf.generic import EncodedStreamObject, NameObject
from pypdf.xmp import XmpInformation
from sqlalchemy import func, select
from sqlalchemy.orm import Session
from test_database import migration_config
from test_files import PDF, epub_bytes, upload
from test_setup import (
    SETUP,
    assert_problem,
    challenge,
    make_client,
    payload,
)
from test_web_auth import CREDENTIALS, LOGIN

from app.db.models.books import Book
from app.db.models.storage import BookFile, StorageObject
from app.db.session import create_sqlite_engine
from app.modules.auth.provision import main as provision_installation
from app.modules.imports.service import ScanError, ScanService
from app.workers import queue, registry

ROOTS = "/api/v1/storage-roots"


@pytest.fixture
def env(tmp_path: Path) -> Iterator[tuple[TestClient, Path, Path]]:
    database = tmp_path / "acbooks.db"
    code_path = tmp_path / "secrets" / "setup-code"
    command.upgrade(migration_config(database), "head")
    provision_installation(database, code_path)
    with make_client(
        (database, code_path),
        library_dir=tmp_path / "library",
        covers_dir=tmp_path / "covers",
    ) as client:
        assert (
            client.post(SETUP, json=payload(code_path), headers=challenge(client)).status_code
            == 201
        )
        assert client.post(LOGIN, json=CREDENTIALS, headers=challenge(client)).status_code == 200
        yield client, database, tmp_path


def make_nas(base: Path) -> Path:
    root = base / f"nas-{uuid4().hex}"
    root.mkdir()
    return root


def create_root(
    client: TestClient, path: Path | str, name: str = "NAS", mode: str = "external"
) -> Any:
    return client.post(
        ROOTS,
        json={"name": name, "rootPath": str(path), "mode": mode},
        headers=challenge(client),
    )


def count(session: Session, model: type) -> int:
    return int(session.scalar(select(func.count()).select_from(model)) or 0)


def test_storage_root_registration_and_validation(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, _database, tmp_path = env
    nas = make_nas(tmp_path.parent)
    response = create_root(client, nas)
    assert response.status_code == 201, response.text
    node = response.json()
    assert node["rootPath"] == str(nas.resolve())
    assert node["mode"] == "external"
    assert node["fileCount"] == 0
    assert any(item["id"] == node["id"] for item in client.get(ROOTS).json())

    assert_problem(create_root(client, "relative/nas"), 422, "STORAGE_ROOT_INVALID")
    assert_problem(
        create_root(client, tmp_path.parent / "missing-xyz"),
        422,
        "STORAGE_ROOT_INVALID",
    )
    inside = tmp_path / "inside"
    inside.mkdir()
    assert_problem(create_root(client, inside), 409, "STORAGE_ROOT_OVERLAP")
    nested = nas / "sub"
    nested.mkdir()
    assert_problem(create_root(client, nested), 409, "STORAGE_ROOT_OVERLAP")


def test_scan_creates_books_and_is_incremental(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, database, tmp_path = env
    nas = make_nas(tmp_path.parent)
    (nas / "a.pdf").write_bytes(PDF)
    (nas / "bad.pdf").write_bytes(b"not really a pdf")
    (nas / "nested").mkdir()
    (nas / "nested" / "b.epub").write_bytes(epub_bytes())
    (nas / "nested" / "c.txt").write_text("ignore", encoding="utf-8")
    (nas / ".hidden.pdf").write_bytes(PDF)
    node = create_root(client, nas).json()

    original = (nas / "a.pdf").read_bytes()
    engine = create_sqlite_engine(database)
    try:
        service = ScanService(engine)
        first = service.run(node["id"], False)
        assert first["scanned"] == 3
        assert first["created"] == 2
        assert first["skipped"] == 1
        with Session(engine) as session:
            assert count(session, Book) == 2
            assert count(session, BookFile) == 2

        second = service.run(node["id"], False)
        assert second["created"] == 0
        assert second["unchanged"] == 2
        with Session(engine) as session:
            assert count(session, Book) == 2
            assert count(session, StorageObject) == 2

        assert (nas / "a.pdf").read_bytes() == original
        assert (nas / "nested" / "c.txt").is_file()
    finally:
        engine.dispose()


def test_scan_updates_changed_and_marks_missing_without_deleting(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, database, tmp_path = env
    nas = make_nas(tmp_path.parent)
    (nas / "a.pdf").write_bytes(PDF)
    (nas / "b.pdf").write_bytes(PDF + b"\nsecond")
    node = create_root(client, nas).json()
    engine = create_sqlite_engine(database)
    try:
        service = ScanService(engine)
        assert service.run(node["id"], False)["created"] == 2

        (nas / "a.pdf").write_bytes(PDF + b"\nextra")
        changed = service.run(node["id"], False)
        assert changed["updated"] == 1

        (nas / "a.pdf").unlink()
        missing = service.run(node["id"], False)
        assert missing["missing"] == 1
        with Session(engine) as session:
            assert count(session, Book) == 2
            assert count(session, BookFile) == 2
            status = session.scalar(
                select(StorageObject.file_status).where(StorageObject.relative_path == "a.pdf")
            )
            assert status == "missing"
    finally:
        engine.dispose()


def test_offline_root_is_not_marked_missing(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, database, tmp_path = env
    nas = make_nas(tmp_path.parent)
    (nas / "a.pdf").write_bytes(PDF)
    node = create_root(client, nas).json()
    engine = create_sqlite_engine(database)
    try:
        service = ScanService(engine)
        assert service.run(node["id"], False)["created"] == 1
        moved = nas.parent / f"{nas.name}-gone"
        nas.rename(moved)
        with pytest.raises(ScanError) as error:
            service.run(node["id"], False)
        assert error.value.code == "STORAGE_ROOT_UNAVAILABLE"
        with Session(engine) as session:
            status = session.scalar(select(StorageObject.file_status))
            assert status == "present"
    finally:
        engine.dispose()


def test_deep_scan_rehashes_unchanged(env: tuple[TestClient, Path, Path]) -> None:
    client, database, tmp_path = env
    nas = make_nas(tmp_path.parent)
    (nas / "a.pdf").write_bytes(PDF)
    node = create_root(client, nas).json()
    engine = create_sqlite_engine(database)
    try:
        service = ScanService(engine)
        assert service.run(node["id"], False)["created"] == 1
        deep = service.run(node["id"], True)
        assert deep["created"] == 0
        assert deep["updated"] == 0
        assert deep["unchanged"] == 1
    finally:
        engine.dispose()


def test_scan_skips_symlinks(env: tuple[TestClient, Path, Path]) -> None:
    if os.name == "nt":
        pytest.skip("symlink creation requires privileges on Windows")
    client, database, tmp_path = env
    nas = make_nas(tmp_path.parent)
    outside = make_nas(tmp_path.parent)
    (outside / "secret.pdf").write_bytes(PDF)
    (nas / "link.pdf").symlink_to(outside / "secret.pdf")
    (nas / "real.pdf").write_bytes(PDF)
    node = create_root(client, nas).json()
    engine = create_sqlite_engine(database)
    try:
        result = ScanService(engine).run(node["id"], False)
        assert result["scanned"] == 1
        assert result["created"] == 1
    finally:
        engine.dispose()


def test_delete_root_in_use(env: tuple[TestClient, Path, Path]) -> None:
    client, database, tmp_path = env
    nas = make_nas(tmp_path.parent)
    (nas / "a.pdf").write_bytes(PDF)
    node = create_root(client, nas).json()
    empty = make_nas(tmp_path.parent)
    empty_node = create_root(client, empty).json()
    engine = create_sqlite_engine(database)
    try:
        ScanService(engine).run(node["id"], False)
    finally:
        engine.dispose()

    assert (
        client.delete(f"{ROOTS}/{empty_node['id']}", headers=challenge(client)).status_code == 204
    )
    assert_problem(
        client.delete(f"{ROOTS}/{node['id']}", headers=challenge(client)),
        409,
        "STORAGE_ROOT_IN_USE",
    )


def test_scan_job_endpoint_and_worker(env: tuple[TestClient, Path, Path]) -> None:
    client, database, tmp_path = env
    nas = make_nas(tmp_path.parent)
    (nas / "a.pdf").write_bytes(PDF)
    node = create_root(client, nas).json()

    accepted = client.post(f"{ROOTS}/{node['id']}/scan", headers=challenge(client))
    assert accepted.status_code == 202
    job_id = accepted.json()["jobId"]
    again = client.post(f"{ROOTS}/{node['id']}/scan", headers=challenge(client))
    assert again.status_code == 202
    assert again.json()["jobId"] == job_id

    status = client.get(f"/api/v1/jobs/{job_id}")
    assert status.json()["status"] == "queued"

    engine = create_sqlite_engine(database)
    try:
        claimed = queue.claim_next(engine)
        assert claimed is not None and claimed.id == job_id
        result = registry.run_job(engine, claimed)
        queue.finish(engine, job_id, "succeeded", result=result)
    finally:
        engine.dispose()

    done = client.get(f"/api/v1/jobs/{job_id}").json()
    assert done["status"] == "succeeded"
    assert done["result"]["created"] == 1

    assert_problem(
        client.get("/api/v1/jobs/00000000-0000-4000-8000-000000000000"),
        404,
        "JOB_NOT_FOUND",
    )


def test_scan_metadata_and_explicit_duplicate_resolution(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, database, tmp_path = env
    nas = make_nas(tmp_path.parent)
    epub = nas / "01.epub"
    with ZipFile(epub, "w") as archive:
        archive.writestr("mimetype", "application/epub+zip")
        archive.writestr(
            "META-INF/container.xml",
            '<container><rootfiles><rootfile full-path="OPS/package.opf"/></rootfiles></container>',
        )
        archive.writestr(
            "OPS/package.opf",
            '<package xmlns:dc="http://purl.org/dc/elements/1.1/"><metadata>'
            "<dc:title>Embedded title</dc:title><dc:creator>First Author</dc:creator>"
            "<dc:creator>Second Author</dc:creator>"
            "<dc:description>Embedded summary</dc:description>"
            "<dc:identifier>urn:isbn:978-0-306-40615-7</dc:identifier></metadata>"
            '<manifest><item id="cover" href="cover.png" properties="cover-image"/>'
            "</manifest></package>",
        )
        image = BytesIO()
        Image.new("RGB", (4, 6), "blue").save(image, "PNG")
        archive.writestr("OPS/cover.png", image.getvalue())

    root_id = create_root(client, nas).json()["id"]
    engine = create_sqlite_engine(database)
    try:
        scan = ScanService(engine, tmp_path / "covers")
        assert scan.run(root_id, False)["created"] == 1
        with Session(engine) as session:
            book_id = session.scalars(select(Book.id)).one()
        book_url = f"/api/v1/books/{book_id}"
        detail = client.get(book_url).json()
        assert detail["title"] == "Embedded title"
        assert set(detail["authors"]) == {"First Author", "Second Author"}
        assert detail["isbn"] == "9780306406157"
        assert detail["description"] == "Embedded summary"
        assert client.get(f"{book_url}/cover").content == image.getvalue()

        (nas / "02.epub").write_bytes(epub.read_bytes())
        writer = PdfWriter()
        page = writer.add_blank_page(100, 100)
        thumbnail = BytesIO()
        Image.new("RGB", (4, 6), "red").save(thumbnail, "JPEG")
        image_stream = EncodedStreamObject()
        image_stream[NameObject("/Filter")] = NameObject("/DCTDecode")
        image_stream._data = thumbnail.getvalue()
        page[NameObject("/Thumb")] = writer._add_object(image_stream)
        xmp = XmpInformation.create()
        xmp.dc_title = {"x-default": "Another edition"}
        writer.xmp_metadata = xmp
        writer.add_metadata(
            {
                "/Title": "Fallback title",
                "/Author": "PDF Author",
                "/Subject": "PDF summary",
                "/ISBN": "9780306406157",
            }
        )
        writer.write(nas / "03.pdf")
        result = scan.run(root_id, False)
        assert result["pending"] == 2 and result["created"] == 0
        pending = client.get(f"{ROOTS}/{root_id}/unlinked-files?limit=1").json()
        assert pending["total"] == 2 and len(pending["items"]) == 1
        epub_url = "/api/v1/unlinked-files/" + pending["items"][0]["id"]
        candidate = client.get(epub_url).json()["candidates"][0]
        assert candidate["bookId"] == book_id
        assert set(candidate["reasons"]) == {"title", "isbn", "sha256"}
        assert scan.run(root_id, True)["created"] == 0

        assert (
            client.patch(
                book_url,
                json={"title": "Manual title", "description": "Manual summary"},
                headers=challenge(client),
            ).status_code
            == 200
        )
        assert (
            client.post(
                f"{epub_url}/resolve",
                json={"bookId": book_id},
                headers=challenge(client),
            ).json()["bookId"]
            == book_id
        )
        assert_problem(
            client.post(f"{epub_url}/resolve", json={}, headers=challenge(client)),
            404,
            "FILE_NOT_FOUND",
        )
        assert scan.run(root_id, True)["created"] == 0
        updated = client.get(book_url).json()
        assert updated["title"] == "Manual title"
        assert updated["description"] == "Manual summary"

        entries = client.get(f"{ROOTS}/{root_id}/unlinked-files").json()["items"]
        pdf_url = "/api/v1/unlinked-files/" + entries[0]["id"]
        assert client.get(pdf_url).json()["authors"] == ["PDF Author"]
        (nas / "03.pdf").write_bytes(PDF)
        assert_problem(
            client.post(f"{pdf_url}/resolve", json={}, headers=challenge(client)),
            409,
            "IMPORT_FILE_CHANGED",
        )
        writer.write(nas / "03.pdf")
        assert scan.run(root_id, True)["updated"] >= 1
        response = client.post(f"{pdf_url}/resolve", json={}, headers=challenge(client))
        assert response.status_code == 200, response.text
        assert response.json()["bookId"] != book_id
        pdf_book = client.get("/api/v1/books/" + response.json()["bookId"]).json()
        assert pdf_book["title"] == "Another edition"
        assert pdf_book["description"] == "PDF summary"
        assert pdf_book["isbn"] == updated["isbn"]
        assert client.get("/api/v1/books/" + response.json()["bookId"] + "/cover").content == (
            thumbnail.getvalue()
        )
        uploaded = client.post(
            "/api/v1/books",
            json={"title": "User-supplied title"},
            headers=challenge(client),
        ).json()
        assert upload(client, uploaded["id"], (nas / "03.pdf").read_bytes()).status_code == 201
        uploaded_url = "/api/v1/books/" + uploaded["id"]
        imported = client.get(uploaded_url).json()
        assert imported["title"] == "User-supplied title"
        assert imported["authors"] == ["PDF Author"]
        assert imported["isbn"] == "9780306406157"
        assert client.get(uploaded_url + "/cover").content == thumbnail.getvalue()
    finally:
        engine.dispose()


def test_metadata_settings_and_worker_protect_manual_fields(
    env: tuple[TestClient, Path, Path],
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    import json

    from app.modules.metadata import service as metadata

    client, database, _ = env
    settings_url = "/api/v1/metadata/settings"
    assert client.get(settings_url).json()["autoFill"] is False
    settings = {"autoFill": True, "fields": ["authors", "series"]}
    assert client.patch(settings_url, json=settings, headers=challenge(client)).json() == settings
    created = client.post(
        "/api/v1/books",
        json={"title": "Manual title", "isbn": "9780140328721"},
        headers=challenge(client),
    ).json()
    book_url = f"/api/v1/books/{created['id']}"
    assert (
        client.patch(
            book_url,
            json={"series": None},
            headers=challenge(client),
        ).status_code
        == 200
    )

    def remote(host: str, path: str, limit: int) -> bytes:
        assert host == "openlibrary.org" and limit > 0
        if path.startswith("/search.json"):
            return json.dumps({"docs": [{"title": "Found by title"}]}).encode()
        return json.dumps(
            {
                "ISBN:9780140328721": {
                    "title": "External title",
                    "authors": [{"name": "External author"}],
                    "identifiers": {"isbn_13": ["9780140328721"]},
                    "description": "External description",
                    "series": "External series",
                }
            }
        ).encode()

    monkeypatch.setattr(metadata, "_download", remote)
    assert (
        client.get("/api/v1/metadata/candidates", params={"q": "Title"}).json()[0]["title"]
        == "Found by title"
    )
    candidate = client.get("/api/v1/metadata/candidates", params={"q": "9780140328721"}).json()[0]
    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session, session.begin():
            metadata.MetadataService.enqueue_auto_fill(session, created["id"])
        job = queue.claim_next(engine)
        assert job is not None and job.input["automatic"]
        result = registry.run_job(engine, job)
        assert result["items"][0]["fields"] == ["authors"]
        detail = client.get(book_url).json()
        assert detail["title"] == "Manual title"
        assert detail["authors"] == ["External author"]
        assert detail["series"] is None
        assert detail["description"] is None

        response = client.post(
            f"{book_url}/metadata",
            json=candidate,
            headers=challenge(client),
        )
        assert response.json() == {"fields": ["description"]}
        assert client.get(book_url).json()["series"] is None
        batch = client.post(
            "/api/v1/metadata/jobs",
            json={"bookIds": [created["id"]]},
            headers=challenge(client),
        )
        assert batch.status_code == 202
        claimed = queue.claim_next(engine)
        assert claimed is not None
        assert registry.run_job(engine, claimed)["items"][0]["status"] == "unchanged"
    finally:
        engine.dispose()
