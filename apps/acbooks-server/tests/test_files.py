import errno
import io
import os
import sqlite3
import threading
import zipfile
from collections.abc import Iterator
from pathlib import Path
from typing import Any, cast
from uuid import UUID

import pytest
from alembic import command
from fastapi import UploadFile
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

from app.db.models.reading import ReadingPosition
from app.db.models.storage import BookFile, StorageObject, StorageRoot
from app.db.session import create_sqlite_engine
from app.modules.auth.provision import main as provision_installation
from app.modules.files import repository as files_repository
from app.modules.files import service as files_service
from app.modules.files.service import FilesService

UPLOADS = "/api/v1/uploads"
EXTERNAL_FILE_ID = "11111111-1111-4111-8111-111111111111"
ESCAPE_FILE_ID = "22222222-2222-4222-8222-222222222222"
PDF = b"%PDF-1.4\n" + b"A" * 2000 + b"\n%%EOF\n"


def epub_bytes() -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("mimetype", "application/epub+zip")
        archive.writestr("META-INF/container.xml", "<container/>")
    return buffer.getvalue()


@pytest.fixture
def env(tmp_path: Path) -> Iterator[tuple[TestClient, Path, Path]]:
    database = tmp_path / "acbooks.db"
    code_path = tmp_path / "secrets" / "setup-code"
    library = tmp_path / "library"
    command.upgrade(migration_config(database), "head")
    provision_installation(database, code_path)
    with make_client((database, code_path), library_dir=library) as client:
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
        yield client, database, library


def upload(
    client: TestClient,
    book_id: str,
    content: bytes,
    filename: str = "book.pdf",
    media_type: str = "application/pdf",
) -> Any:
    return client.post(
        UPLOADS,
        data={"bookId": book_id},
        files={"file": (filename, content, media_type)},
        headers=challenge(client),
    )


def count(session: Session, model: type) -> int:
    return int(session.scalar(select(func.count()).select_from(model)) or 0)


def test_upload_lists_files_and_stores_managed_root(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, database, library = env
    book = create_book(client, title="带电子文件的书")

    response = upload(client, book["id"], PDF)
    assert response.status_code == 201, response.text
    created = response.json()
    assert created["format"] == "pdf"
    assert created["fileStatus"] == "present"
    assert created["sizeBytes"] == len(PDF)
    assert response.headers["location"] == f"/api/v1/files/{created['id']}/content"

    epub = epub_bytes()
    second = upload(
        client, book["id"], epub, filename="book.epub", media_type="application/epub+zip"
    )
    assert second.status_code == 201, second.text
    assert second.json()["format"] == "epub"

    listing = client.get(f"{BOOKS}/{book['id']}/files")
    assert listing.status_code == 200
    assert {item["format"] for item in listing.json()} == {"pdf", "epub"}

    detail = client.get(f"{BOOKS}/{book['id']}").json()
    assert detail["fileCount"] == 2
    assert sorted(detail["formats"]) == ["epub", "pdf"]

    stored = list(library.rglob("*.pdf")) + list(library.rglob("*.epub"))
    assert len(stored) == 2

    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session:
            root = session.scalars(
                select(StorageRoot).where(StorageRoot.mode == "managed")
            ).one()
            assert root.root_path == str(library.resolve())
            assert count(session, StorageObject) == 2
            assert count(session, BookFile) == 2
    finally:
        engine.dispose()


def test_download_full_range_and_unsatisfiable(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, _database, _library = env
    book = create_book(client, title="下载")
    created = upload(client, book["id"], PDF).json()

    full = client.get(f"/api/v1/files/{created['id']}/content")
    assert full.status_code == 200
    assert full.content == PDF
    assert full.headers["content-type"] == "application/pdf"
    assert full.headers["accept-ranges"] == "bytes"
    assert full.headers["content-length"] == str(len(PDF))
    assert full.headers["etag"].startswith('"')
    assert "attachment" in full.headers["content-disposition"]
    assert "content-encoding" not in full.headers

    partial = client.get(
        f"/api/v1/files/{created['id']}/content",
        headers={"Range": "bytes=0-3"},
    )
    assert partial.status_code == 206
    assert partial.content == PDF[:4]
    assert partial.headers["content-range"] == f"bytes 0-3/{len(PDF)}"
    assert partial.headers["content-length"] == "4"

    suffix = client.get(
        f"/api/v1/files/{created['id']}/content",
        headers={"Range": "bytes=-4"},
    )
    assert suffix.status_code == 206
    assert suffix.content == PDF[-4:]

    invalid = client.get(
        f"/api/v1/files/{created['id']}/content",
        headers={"Range": f"bytes={len(PDF) + 10}-"},
    )
    assert invalid.status_code == 416
    assert invalid.headers["content-range"] == f"bytes */{len(PDF)}"


def test_upload_rejects_unsupported_format_and_missing_book(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, _database, library = env
    book = create_book(client, title="格式")
    assert_problem(
        upload(client, book["id"], b"not a real ebook", filename="x.txt"),
        422,
        "FILE_FORMAT_UNSUPPORTED",
    )
    assert_problem(
        upload(client, "00000000-0000-4000-8000-000000000000", PDF),
        404,
        "BOOK_NOT_FOUND",
    )
    assert list(library.rglob("*.tmp")) == []


def test_upload_rejects_duplicate_and_too_large(
    env: tuple[TestClient, Path, Path], monkeypatch: pytest.MonkeyPatch
) -> None:
    client, _database, library = env
    book = create_book(client, title="重复")
    assert upload(client, book["id"], PDF).status_code == 201
    assert_problem(upload(client, book["id"], PDF), 409, "FILE_DUPLICATE")
    assert len(list(library.rglob("*.pdf"))) == 1

    monkeypatch.setattr(files_service, "MAX_UPLOAD_BYTES", 8)
    assert_problem(upload(client, book["id"], PDF), 413, "FILE_TOO_LARGE")


def test_delete_managed_file_removes_rows_and_position(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, database, library = env
    book = create_book(client, title="删除")
    created = upload(client, book["id"], PDF).json()

    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session, session.begin():
            session.add(
                ReadingPosition(
                    id="pos",
                    book_file_id=created["id"],
                    locator_json="{}",
                    percent=10.0,
                    revision=1,
                    updated_at=1,
                )
            )
    finally:
        engine.dispose()

    removed = client.delete(
        f"/api/v1/files/{created['id']}", headers=challenge(client)
    )
    assert removed.status_code == 204
    assert list(library.rglob("*.pdf")) == []
    assert client.get(f"{BOOKS}/{book['id']}/files").json() == []

    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session:
            assert count(session, BookFile) == 0
            assert count(session, StorageObject) == 0
            assert count(session, ReadingPosition) == 0
    finally:
        engine.dispose()


def test_delete_rejects_external_file(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, database, _library = env
    book = create_book(client, title="外部")
    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session, session.begin():
            session.add(
                StorageRoot(
                    id="ext", name="nas", root_path="/mnt/books", mode="external"
                )
            )
            session.flush()
            session.add(
                StorageObject(
                    id="extobj",
                    root_id="ext",
                    relative_path="a.pdf",
                    size_bytes=3,
                    sha256=None,
                    file_status="present",
                    mtime_ms=1,
                )
            )
            session.flush()
            session.add(
                BookFile(
                    id=EXTERNAL_FILE_ID,
                    book_id=book["id"],
                    storage_object_id="extobj",
                    format="pdf",
                )
            )
    finally:
        engine.dispose()

    assert_problem(
        client.delete(
            f"/api/v1/files/{EXTERNAL_FILE_ID}", headers=challenge(client)
        ),
        409,
        "FILE_EXTERNAL_PROTECTED",
    )
    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session:
            assert count(session, BookFile) == 1
            assert count(session, StorageObject) == 1
    finally:
        engine.dispose()


def test_download_rejects_path_escape(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, database, library = env
    book = create_book(client, title="越界")
    library.mkdir(parents=True, exist_ok=True)
    outside = library.parent / "outside.pdf"
    outside.write_bytes(b"%PDF-1.4 outside")
    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session, session.begin():
            session.add(
                StorageRoot(
                    id="man",
                    name="library",
                    root_path=str(library.resolve()),
                    mode="managed",
                )
            )
            session.flush()
            session.add(
                StorageObject(
                    id="escape",
                    root_id="man",
                    relative_path="../outside.pdf",
                    size_bytes=outside.stat().st_size,
                    sha256=None,
                    file_status="present",
                    mtime_ms=1,
                )
            )
            session.flush()
            session.add(
                BookFile(
                    id=ESCAPE_FILE_ID,
                    book_id=book["id"],
                    storage_object_id="escape",
                    format="pdf",
                )
            )
    finally:
        engine.dispose()

    assert_problem(
        client.get(f"/api/v1/files/{ESCAPE_FILE_ID}/content"),
        404,
        "FILE_NOT_FOUND",
    )


def test_upload_requires_csrf_and_session(env: tuple[TestClient, Path, Path]) -> None:
    client, _database, _library = env
    book = create_book(client, title="鉴权")
    assert (
        client.post(
            UPLOADS,
            data={"bookId": book["id"]},
            files={"file": ("book.pdf", PDF, "application/pdf")},
        ).status_code
        == 403
    )


def test_files_endpoints_require_session(tmp_path: Path) -> None:
    database = tmp_path / "acbooks.db"
    code_path = tmp_path / "secrets" / "setup-code"
    command.upgrade(migration_config(database), "head")
    provision_installation(database, code_path)
    with make_client((database, code_path), library_dir=tmp_path / "library") as client:
        missing = "00000000-0000-4000-8000-000000000000"
        assert client.get(f"/api/v1/books/{missing}/files").status_code == 401
        assert client.get(f"/api/v1/files/{missing}/content").status_code == 401


def test_upload_cleans_up_published_file_when_indexing_fails(
    env: tuple[TestClient, Path, Path], monkeypatch: pytest.MonkeyPatch
) -> None:
    client, _database, library = env
    book = create_book(client, title="回滚")

    def boom(*_args: object, **_kwargs: object) -> None:
        raise RuntimeError("index failure")

    monkeypatch.setattr(files_repository, "add_book_file", boom)
    response = upload(client, book["id"], PDF)
    assert response.status_code == 500

    assert list(library.rglob("*.pdf")) == []
    assert list(library.rglob(".upload-*")) == []
    assert client.get(f"{BOOKS}/{book['id']}/files").json() == []


def test_files_openapi_contract(env: tuple[TestClient, Path, Path]) -> None:
    client, _database, _library = env
    paths = client.get("/openapi.json").json()["paths"]
    assert paths["/api/v1/books/{bookId}/files"]["get"]["operationId"] == "listBookFiles"
    assert paths["/api/v1/uploads"]["post"]["operationId"] == "uploadBookFile"
    assert (
        paths["/api/v1/files/{bookFileId}/content"]["get"]["operationId"]
        == "getBookFileContent"
    )
    assert (
        paths["/api/v1/files/{bookFileId}"]["delete"]["operationId"]
        == "deleteBookFile"
    )


SYMLINK_FILE_ID = "33333333-3333-4333-8333-333333333333"


class FakeUpload:
    def __init__(self, content: bytes, chunk: int = 512 * 1024) -> None:
        self._content = content
        self._pos = 0
        self._chunk = chunk
        self.file = self

    def read(self, _size: int = -1) -> bytes:
        if self._pos >= len(self._content):
            return b""
        piece = self._content[self._pos : self._pos + self._chunk]
        self._pos += len(piece)
        return piece


class BrokenReadUpload:
    def __init__(self) -> None:
        self._calls = 0
        self.file = self

    def read(self, _size: int = -1) -> bytes:
        self._calls += 1
        if self._calls == 1:
            return b"%PDF-1.4\n" + b"A" * 64
        raise OSError("client disconnected")


def service_for(database: Path, library: Path) -> tuple[FilesService, Any]:
    engine = create_sqlite_engine(database)
    return FilesService(engine, library), engine


def test_upload_interruption_cleans_up(env: tuple[TestClient, Path, Path]) -> None:
    client, database, library = env
    book = create_book(client, title="中断")
    service, engine = service_for(database, library)
    try:
        with pytest.raises(OSError):
            service.upload(UUID(book["id"]), cast(UploadFile, BrokenReadUpload()))
    finally:
        engine.dispose()
    assert list(library.rglob("*.pdf")) == []
    assert list(library.rglob(".upload-*")) == []
    assert client.get(f"{BOOKS}/{book['id']}/files").json() == []


def test_upload_disk_full_cleans_up(
    env: tuple[TestClient, Path, Path], monkeypatch: pytest.MonkeyPatch
) -> None:
    client, database, library = env
    book = create_book(client, title="磁盘满")
    real_fdopen = os.fdopen

    class FullWriter:
        def __init__(self, handle: Any) -> None:
            self._handle = handle
            self._writes = 0

        def write(self, data: bytes) -> int:
            self._writes += 1
            if self._writes > 1:
                raise OSError(errno.ENOSPC, "No space left on device")
            return int(self._handle.write(data))

        def __enter__(self) -> "FullWriter":
            return self

        def __exit__(self, *_exc: object) -> None:
            self._handle.close()

    monkeypatch.setattr(
        os,
        "fdopen",
        lambda fd, mode: FullWriter(real_fdopen(fd, mode)),
    )
    service, engine = service_for(database, library)
    content = b"%PDF-1.4\n" + b"A" * (1024 * 1024)
    try:
        with pytest.raises(OSError):
            service.upload(
                UUID(book["id"]),
                cast(UploadFile, FakeUpload(content, chunk=512 * 1024)),
            )
    finally:
        engine.dispose()
    assert list(library.rglob("*.pdf")) == []
    assert list(library.rglob(".upload-*")) == []
    assert client.get(f"{BOOKS}/{book['id']}/files").json() == []


def test_upload_publish_failure_cleans_up(
    env: tuple[TestClient, Path, Path], monkeypatch: pytest.MonkeyPatch
) -> None:
    client, database, library = env
    book = create_book(client, title="发布失败")

    def boom(*_args: object, **_kwargs: object) -> None:
        raise OSError(errno.ENOSPC, "publish failed")

    monkeypatch.setattr(os, "link", boom)
    service, engine = service_for(database, library)
    try:
        with pytest.raises(OSError):
            service.upload(UUID(book["id"]), cast(UploadFile, FakeUpload(PDF)))
    finally:
        engine.dispose()
    assert list(library.rglob("*.pdf")) == []
    assert list(library.rglob(".upload-*")) == []
    assert client.get(f"{BOOKS}/{book['id']}/files").json() == []


def test_oversized_upload_cleans_up(
    env: tuple[TestClient, Path, Path], monkeypatch: pytest.MonkeyPatch
) -> None:
    client, _database, library = env
    book = create_book(client, title="超大清理")
    monkeypatch.setattr(files_service, "MAX_UPLOAD_BYTES", 8)
    assert_problem(upload(client, book["id"], PDF), 413, "FILE_TOO_LARGE")
    assert list(library.rglob("*")) == []
    assert client.get(f"{BOOKS}/{book['id']}/files").json() == []


def test_download_missing_file_then_delete(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, _database, library = env
    book = create_book(client, title="缺失文件")
    created = upload(client, book["id"], PDF).json()
    for path in library.rglob("*.pdf"):
        path.unlink()
    assert_problem(
        client.get(f"/api/v1/files/{created['id']}/content"), 404, "FILE_NOT_FOUND"
    )
    removed = client.delete(
        f"/api/v1/files/{created['id']}", headers=challenge(client)
    )
    assert removed.status_code == 204
    assert client.get(f"{BOOKS}/{book['id']}/files").json() == []


def test_upload_ignores_client_filename(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, _database, library = env
    book = create_book(client, title="恶意文件名")
    response = client.post(
        UPLOADS,
        data={"bookId": book["id"]},
        files={"file": ("../../evil.pdf", PDF, "application/pdf")},
        headers=challenge(client),
    )
    assert response.status_code == 201
    assert not (library.parent / "evil.pdf").exists()
    assert len(list(library.rglob("*.pdf"))) == 1
    created = response.json()
    assert client.get(f"/api/v1/files/{created['id']}/content").content == PDF


def test_download_rejects_symlink_escape(
    env: tuple[TestClient, Path, Path],
) -> None:
    if os.name == "nt":
        pytest.skip("symlink creation requires privileges on Windows")
    client, database, library = env
    book = create_book(client, title="符号链接")
    library.mkdir(parents=True, exist_ok=True)
    outside = library.parent / "outside"
    outside.mkdir()
    (outside / "secret.pdf").write_bytes(b"%PDF-x")
    (library / "link").symlink_to(outside, target_is_directory=True)
    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session, session.begin():
            session.add(
                StorageRoot(
                    id="man",
                    name="library",
                    root_path=str(library.resolve()),
                    mode="managed",
                )
            )
            session.flush()
            session.add(
                StorageObject(
                    id="lnk",
                    root_id="man",
                    relative_path="link/secret.pdf",
                    size_bytes=6,
                    sha256=None,
                    file_status="present",
                    mtime_ms=1,
                )
            )
            session.flush()
            session.add(
                BookFile(
                    id=SYMLINK_FILE_ID,
                    book_id=book["id"],
                    storage_object_id="lnk",
                    format="pdf",
                )
            )
    finally:
        engine.dispose()

    assert_problem(
        client.get(f"/api/v1/files/{SYMLINK_FILE_ID}/content"),
        404,
        "FILE_NOT_FOUND",
    )


def test_external_file_download_allowed_but_not_deleted(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, database, library = env
    book = create_book(client, title="外部下载")
    nas = library.parent / "nas"
    nas.mkdir()
    (nas / "a.pdf").write_bytes(PDF)
    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session, session.begin():
            session.add(
                StorageRoot(
                    id="nas-root",
                    name="nas",
                    root_path=str(nas.resolve()),
                    mode="external",
                )
            )
            session.flush()
            session.add(
                StorageObject(
                    id="nas-obj",
                    root_id="nas-root",
                    relative_path="a.pdf",
                    size_bytes=len(PDF),
                    sha256=None,
                    file_status="present",
                    mtime_ms=1,
                )
            )
            session.flush()
            session.add(
                BookFile(
                    id=EXTERNAL_FILE_ID,
                    book_id=book["id"],
                    storage_object_id="nas-obj",
                    format="pdf",
                )
            )
    finally:
        engine.dispose()

    content = client.get(f"/api/v1/files/{EXTERNAL_FILE_ID}/content")
    assert content.status_code == 200
    assert content.content == PDF
    assert_problem(
        client.delete(
            f"/api/v1/files/{EXTERNAL_FILE_ID}", headers=challenge(client)
        ),
        409,
        "FILE_EXTERNAL_PROTECTED",
    )


def test_delete_is_idempotent(env: tuple[TestClient, Path, Path]) -> None:
    client, _database, library = env
    book = create_book(client, title="幂等删除")
    created = upload(client, book["id"], PDF).json()
    assert (
        client.delete(
            f"/api/v1/files/{created['id']}", headers=challenge(client)
        ).status_code
        == 204
    )
    assert list(library.rglob("*.pdf")) == []
    assert_problem(
        client.delete(
            f"/api/v1/files/{created['id']}", headers=challenge(client)
        ),
        404,
        "FILE_NOT_FOUND",
    )


def test_concurrent_delete_single_success(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, database, library = env
    code_path = database.parent / "secrets" / "setup-code"
    book = create_book(client, title="并发删除")
    created = upload(client, book["id"], PDF).json()
    second = make_client((database, code_path), library_dir=library)
    try:
        assert (
            second.post(
                LOGIN, json=CREDENTIALS, headers=challenge(second)
            ).status_code
            == 200
        )
        results: list[int] = []

        def run(target: TestClient) -> None:
            response = target.delete(
                f"/api/v1/files/{created['id']}", headers=challenge(target)
            )
            results.append(response.status_code)

        threads = [
            threading.Thread(target=run, args=(client,)),
            threading.Thread(target=run, args=(second,)),
        ]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()
        assert sorted(results) == [204, 404]
        assert list(library.rglob("*.pdf")) == []
    finally:
        second.close()


def test_old_database_upgrade_preserves_files(tmp_path: Path) -> None:
    database = tmp_path / "old.db"
    config = migration_config(database)
    command.upgrade(config, "0004")
    connection = sqlite3.connect(database)
    try:
        connection.execute(
            "INSERT INTO books (id, title, created_at, updated_at) "
            "VALUES ('book-0', 'Old', 1, 1)"
        )
        connection.execute(
            "INSERT INTO storage_roots (id, name, root_path, mode) "
            "VALUES ('root', 'library', '/tmp/library', 'managed')"
        )
        connection.execute(
            "INSERT INTO storage_objects "
            "(id, root_id, relative_path, size_bytes, file_status, mtime_ms) "
            "VALUES ('obj', 'root', 'book/a.pdf', 6, 'present', 1)"
        )
        connection.execute(
            "INSERT INTO book_files (id, book_id, storage_object_id, format) "
            "VALUES ('file', 'book-0', 'obj', 'pdf')"
        )
        connection.commit()
    finally:
        connection.close()

    command.upgrade(config, "head")

    connection = sqlite3.connect(database)
    try:
        assert connection.execute("SELECT COUNT(*) FROM book_files").fetchone()[0] == 1
        assert (
            connection.execute("SELECT COUNT(*) FROM storage_objects").fetchone()[0]
            == 1
        )
        columns = {row[1] for row in connection.execute("PRAGMA table_info(books)")}
        assert "cover_updated_at" in columns
    finally:
        connection.close()


NONREGULAR_FILE_ID = "44444444-4444-4444-8444-444444444444"
EXTERNAL_UNLINK_ID = "55555555-5555-4555-8555-555555555555"
EXTERNAL_PURGE_ID = "66666666-6666-4666-8666-666666666666"


def epub_with(*entries: tuple[str, bytes]) -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        for name, data in entries:
            archive.writestr(name, data)
    return buffer.getvalue()


def test_upload_rejects_pdf_without_eof(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, _database, _library = env
    book = create_book(client, title="坏PDF")
    assert_problem(
        upload(client, book["id"], b"%PDF-1.4\nno eof marker"),
        422,
        "FILE_FORMAT_UNSUPPORTED",
    )


def test_upload_rejects_epub_without_container(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, _database, _library = env
    book = create_book(client, title="坏EPUB")
    data = epub_with(("mimetype", b"application/epub+zip"))
    assert_problem(
        upload(client, book["id"], data, "x.epub", "application/epub+zip"),
        422,
        "FILE_FORMAT_UNSUPPORTED",
    )


def test_upload_enforces_epub_resource_limits(
    env: tuple[TestClient, Path, Path], monkeypatch: pytest.MonkeyPatch
) -> None:
    client, _database, _library = env
    book = create_book(client, title="EPUB限制")
    data = epub_with(
        ("mimetype", b"application/epub+zip"),
        ("META-INF/container.xml", b"<container/>"),
        ("content.xhtml", b"x" * 100),
    )

    monkeypatch.setattr(files_service, "MAX_EPUB_ENTRIES", 2)
    assert_problem(
        upload(client, book["id"], data, "x.epub", "application/epub+zip"),
        422,
        "FILE_FORMAT_UNSUPPORTED",
    )

    monkeypatch.setattr(files_service, "MAX_EPUB_ENTRIES", 10000)
    monkeypatch.setattr(files_service, "MAX_EPUB_UNCOMPRESSED_BYTES", 10)
    assert_problem(
        upload(client, book["id"], data, "x.epub", "application/epub+zip"),
        422,
        "FILE_FORMAT_UNSUPPORTED",
    )

    monkeypatch.setattr(
        files_service, "MAX_EPUB_UNCOMPRESSED_BYTES", 4 * 1024 * 1024 * 1024
    )
    monkeypatch.setattr(files_service, "MAX_EPUB_METADATA_BYTES", 4)
    assert_problem(
        upload(client, book["id"], data, "x.epub", "application/epub+zip"),
        422,
        "FILE_FORMAT_UNSUPPORTED",
    )


def test_upload_enforces_http_request_size(
    env: tuple[TestClient, Path, Path], monkeypatch: pytest.MonkeyPatch
) -> None:
    client, _database, library = env
    book = create_book(client, title="接收层限流")
    monkeypatch.setattr(files_service, "MAX_REQUEST_BYTES", 100)
    assert_problem(upload(client, book["id"], PDF), 413, "FILE_TOO_LARGE")
    assert list(library.rglob("*")) == []


def test_unlink_keeps_storage_object_and_original(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, database, library = env
    book = create_book(client, title="解除关联")
    created = upload(client, book["id"], PDF).json()

    response = client.delete(
        f"/api/v1/files/{created['id']}?purge=false", headers=challenge(client)
    )
    assert response.status_code == 204
    assert client.get(f"{BOOKS}/{book['id']}/files").json() == []
    assert len(list(library.rglob("*.pdf"))) == 1
    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session:
            assert count(session, BookFile) == 0
            assert count(session, StorageObject) == 1
    finally:
        engine.dispose()


def test_external_can_unlink_but_not_purge(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, database, library = env
    book = create_book(client, title="外部解除")
    nas = library.parent / "nas"
    nas.mkdir()
    (nas / "a.pdf").write_bytes(PDF)
    (nas / "b.pdf").write_bytes(PDF)
    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session, session.begin():
            session.add(
                StorageRoot(
                    id="nas-root",
                    name="nas",
                    root_path=str(nas.resolve()),
                    mode="external",
                )
            )
            session.flush()
            for suffix, object_id in (("a", "obj-a"), ("b", "obj-b")):
                session.add(
                    StorageObject(
                        id=object_id,
                        root_id="nas-root",
                        relative_path=f"{suffix}.pdf",
                        size_bytes=len(PDF),
                        sha256=None,
                        file_status="present",
                        mtime_ms=1,
                    )
                )
            session.flush()
            session.add(
                BookFile(
                    id=EXTERNAL_UNLINK_ID,
                    book_id=book["id"],
                    storage_object_id="obj-a",
                    format="pdf",
                )
            )
            session.add(
                BookFile(
                    id=EXTERNAL_PURGE_ID,
                    book_id=book["id"],
                    storage_object_id="obj-b",
                    format="pdf",
                )
            )
    finally:
        engine.dispose()

    unlink = client.delete(
        f"/api/v1/files/{EXTERNAL_UNLINK_ID}?purge=false",
        headers=challenge(client),
    )
    assert unlink.status_code == 204
    purge = client.delete(
        f"/api/v1/files/{EXTERNAL_PURGE_ID}?purge=true",
        headers=challenge(client),
    )
    assert_problem(purge, 409, "FILE_EXTERNAL_PROTECTED")

    assert (nas / "a.pdf").is_file()
    assert (nas / "b.pdf").is_file()
    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session:
            assert count(session, StorageObject) == 2
            assert count(session, BookFile) == 1
    finally:
        engine.dispose()


def test_download_rejects_non_regular_file(
    env: tuple[TestClient, Path, Path],
) -> None:
    client, database, library = env
    book = create_book(client, title="非普通文件")
    library.mkdir(parents=True, exist_ok=True)
    (library / "subdir").mkdir()
    engine = create_sqlite_engine(database)
    try:
        with Session(engine) as session, session.begin():
            session.add(
                StorageRoot(
                    id="man",
                    name="library",
                    root_path=str(library.resolve()),
                    mode="managed",
                )
            )
            session.flush()
            session.add(
                StorageObject(
                    id="dir-obj",
                    root_id="man",
                    relative_path="subdir",
                    size_bytes=0,
                    sha256=None,
                    file_status="present",
                    mtime_ms=1,
                )
            )
            session.flush()
            session.add(
                BookFile(
                    id=NONREGULAR_FILE_ID,
                    book_id=book["id"],
                    storage_object_id="dir-obj",
                    format="pdf",
                )
            )
    finally:
        engine.dispose()

    assert_problem(
        client.get(f"/api/v1/files/{NONREGULAR_FILE_ID}/content"),
        404,
        "FILE_NOT_FOUND",
    )