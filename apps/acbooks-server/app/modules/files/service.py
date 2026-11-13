import hashlib
import logging
import os
import stat as statlib
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Literal, cast
from uuid import UUID, uuid4

from fastapi import UploadFile
from sqlalchemy import Engine

from app.core.errors import BusinessError, BusinessErrorCode
from app.db.models.storage import BookFile, StorageObject
from app.db.session import create_session_factory
from app.modules.books.schemas import BookFile as BookFileDTO
from app.modules.files import repository

logger = logging.getLogger("uvicorn.error")

MAX_UPLOAD_BYTES = 2 * 1024 * 1024 * 1024
CHUNK_SIZE = 1024 * 1024
MEDIA_TYPES = {
    "pdf": "application/pdf",
    "epub": "application/epub+zip",
}
EPUB_MIMETYPE = b"application/epub+zip"
EPUB_CONTAINER = "META-INF/container.xml"
PDF_TAIL_BYTES = 2048
MAX_EPUB_UNCOMPRESSED_BYTES = 4 * 1024 * 1024 * 1024
MAX_EPUB_ENTRIES = 10000
MAX_EPUB_METADATA_BYTES = 1024 * 1024
MAX_REQUEST_BYTES = MAX_UPLOAD_BYTES + 1024 * 1024


class RangeNotSatisfiable(Exception):
    def __init__(self, size: int) -> None:
        self.size = size
        super().__init__(str(size))


@dataclass(frozen=True)
class Content:
    path: Path
    start: int
    length: int
    total_size: int
    partial: bool
    etag: str
    media_type: str
    filename: str


def _looks_like_pdf(path: Path, size: int) -> bool:
    try:
        with path.open("rb") as handle:
            if not handle.read(5).startswith(b"%PDF-"):
                return False
            if size > 0:
                handle.seek(max(0, size - PDF_TAIL_BYTES))
            return b"%%EOF" in handle.read(PDF_TAIL_BYTES)
    except OSError:
        return False


def _looks_like_epub(path: Path) -> bool:
    try:
        with zipfile.ZipFile(path) as archive:
            infos = archive.infolist()
            if len(infos) > MAX_EPUB_ENTRIES:
                return False
            if sum(info.file_size for info in infos) > MAX_EPUB_UNCOMPRESSED_BYTES:
                return False
            try:
                mimetype = archive.getinfo("mimetype")
                container = archive.getinfo(EPUB_CONTAINER)
            except KeyError:
                return False
            if mimetype.file_size > MAX_EPUB_METADATA_BYTES:
                return False
            if container.file_size > MAX_EPUB_METADATA_BYTES:
                return False
            if archive.read(mimetype).strip() != EPUB_MIMETYPE:
                return False
            archive.read(container)
            return True
    except (
        zipfile.BadZipFile,
        zipfile.LargeZipFile,
        KeyError,
        OSError,
        RuntimeError,
        NotImplementedError,
    ):
        return False


def _detect_format(path: Path, size: int) -> str | None:
    if _looks_like_pdf(path, size):
        return "pdf"
    if _looks_like_epub(path):
        return "epub"
    return None


def _resolve_within(root: Path, relative_path: str) -> Path:
    root_resolved = root.resolve()
    candidate = (root_resolved / relative_path).resolve()
    if candidate != root_resolved and root_resolved not in candidate.parents:
        raise BusinessError(BusinessErrorCode.FILE_NOT_FOUND)
    return candidate


def _parse_range(header: str | None, size: int) -> tuple[int, int] | None:
    if header is None or header.strip() == "":
        return None
    value = header.strip()
    if not value.startswith("bytes="):
        raise RangeNotSatisfiable(size)
    spec = value[len("bytes=") :]
    if "," in spec:
        raise RangeNotSatisfiable(size)
    start_text, separator, end_text = spec.partition("-")
    if separator == "":
        raise RangeNotSatisfiable(size)
    try:
        if start_text == "":
            suffix = int(end_text)
            if suffix <= 0:
                raise ValueError
            start = max(0, size - suffix)
            end = size - 1
        else:
            start = int(start_text)
            end = int(end_text) if end_text else size - 1
    except ValueError as error:
        raise RangeNotSatisfiable(size) from error
    if size == 0 or start > end or start >= size:
        raise RangeNotSatisfiable(size)
    return start, min(end, size - 1)


def enforce_request_size(content_length: str | None) -> None:
    if content_length is None:
        return
    try:
        length = int(content_length)
    except ValueError:
        return
    if length > MAX_REQUEST_BYTES:
        raise BusinessError(BusinessErrorCode.FILE_TOO_LARGE)


class FilesService:
    def __init__(self, engine: Engine, library_dir: Path) -> None:
        self.read_sessions = create_session_factory(engine)
        self.write_sessions = create_session_factory(
            engine.execution_options(sqlite_begin_immediate=True)
        )
        self.library_dir = library_dir

    def _root(self) -> Path:
        self.library_dir.mkdir(parents=True, exist_ok=True)
        return self.library_dir.resolve()

    def list_files(self, book_id: UUID) -> list[BookFileDTO]:
        key = str(book_id)
        with self.read_sessions() as session:
            if repository.get_book(session, key) is None:
                raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)
            return [
                self._dto(record)
                for record in repository.list_book_files(session, key)
            ]

    def upload(self, book_id: UUID, upload: UploadFile) -> BookFileDTO:
        root = self._root()
        key = str(book_id)
        with self.read_sessions() as session:
            if repository.get_book(session, key) is None:
                raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)

        descriptor, temp_name = tempfile.mkstemp(
            prefix=".upload-", suffix=".tmp", dir=root
        )
        temp = Path(temp_name)
        digest = hashlib.sha256()
        size = 0
        final_path: Path | None = None
        published = False
        try:
            with os.fdopen(descriptor, "wb") as handle:
                while True:
                    chunk = upload.file.read(CHUNK_SIZE)
                    if not chunk:
                        break
                    size += len(chunk)
                    if size > MAX_UPLOAD_BYTES:
                        raise BusinessError(BusinessErrorCode.FILE_TOO_LARGE)
                    digest.update(chunk)
                    handle.write(chunk)

            file_format = _detect_format(temp, size)
            if file_format is None:
                raise BusinessError(BusinessErrorCode.FILE_FORMAT_UNSUPPORTED)
            sha256 = digest.hexdigest()

            with self.read_sessions() as session:
                if repository.book_has_sha(session, key, sha256):
                    raise BusinessError(BusinessErrorCode.FILE_DUPLICATE)

            relative_path = f"{key}/{uuid4().hex}.{file_format}"
            book_dir = root / key
            book_dir.mkdir(exist_ok=True)
            if book_dir.is_symlink() or not book_dir.is_dir():
                raise BusinessError(BusinessErrorCode.FILE_NOT_FOUND)
            final_path = book_dir / Path(relative_path).name
            # Hard-link the verified temp file into place; fails rather than overwrites.
            os.link(temp, final_path)
            published = True
            if final_path.is_symlink() or not final_path.is_file():
                raise BusinessError(BusinessErrorCode.FILE_NOT_FOUND)
            try:
                identity = os.path.samefile(temp, final_path)
            except OSError as error:
                raise BusinessError(BusinessErrorCode.FILE_NOT_FOUND) from error
            if not identity:
                raise BusinessError(BusinessErrorCode.FILE_NOT_FOUND)
            mtime_ms = int(final_path.stat().st_mtime * 1000)

            with self.write_sessions.begin() as session:
                root_row = repository.ensure_managed_root(session, str(root))
                if repository.get_book(session, key) is None:
                    raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)
                if repository.book_has_sha(session, key, sha256):
                    raise BusinessError(BusinessErrorCode.FILE_DUPLICATE)
                storage_object = StorageObject(
                    id=str(uuid4()),
                    root_id=root_row.id,
                    relative_path=relative_path,
                    size_bytes=size,
                    sha256=sha256,
                    file_status="present",
                    mtime_ms=mtime_ms,
                )
                repository.add_storage_object(session, storage_object)
                book_file = BookFile(
                    id=str(uuid4()),
                    book_id=key,
                    storage_object_id=storage_object.id,
                    format=file_format,
                )
                repository.add_book_file(session, book_file)
                return BookFileDTO(
                    id=UUID(book_file.id),
                    format=file_format,
                    file_status="present",
                    size_bytes=size,
                )
        except BaseException:
            if published and final_path is not None:
                final_path.unlink(missing_ok=True)
            raise
        finally:
            temp.unlink(missing_ok=True)

    def content(self, file_id: UUID, range_header: str | None) -> Content:
        with self.read_sessions() as session:
            record = repository.get_file(session, str(file_id))
        if record is None:
            raise BusinessError(BusinessErrorCode.FILE_NOT_FOUND)
        path = _resolve_within(Path(record.root_path), record.relative_path)
        try:
            stat_result = path.stat()
        except OSError as error:
            raise BusinessError(BusinessErrorCode.FILE_NOT_FOUND) from error
        if not statlib.S_ISREG(stat_result.st_mode):
            raise BusinessError(BusinessErrorCode.FILE_NOT_FOUND)
        size = stat_result.st_size
        media_type = MEDIA_TYPES.get(record.format, "application/octet-stream")
        etag = record.sha256 or f"{size}-{int(stat_result.st_mtime)}"
        parsed = _parse_range(range_header, size)
        if parsed is None:
            start, length, partial = 0, size, False
        else:
            start, end = parsed
            length, partial = end - start + 1, True
        return Content(
            path=path,
            start=start,
            length=length,
            total_size=size,
            partial=partial,
            etag=etag,
            media_type=media_type,
            filename=f"{record.book_id}.{record.format}",
        )

    def delete(self, file_id: UUID, purge: bool = True) -> None:
        key = str(file_id)
        path: Path | None = None
        with self.write_sessions.begin() as session:
            record = repository.get_file(session, key)
            if record is None:
                raise BusinessError(BusinessErrorCode.FILE_NOT_FOUND)
            if purge:
                if record.root_mode != "managed":
                    raise BusinessError(BusinessErrorCode.FILE_EXTERNAL_PROTECTED)
                path = _resolve_within(
                    Path(record.root_path), record.relative_path
                )
                repository.delete_file_rows(
                    session, key, record.storage_object_id
                )
            else:
                # Detach the book-file association but keep the storage object
                # and original file so a later scan can re-index it.
                repository.unlink_book_file(session, key)
        if purge and path is not None:
            try:
                path.unlink(missing_ok=True)
            except OSError as error:
                logger.warning(
                    "operation=file_delete result=cleanup_failed error=%s",
                    type(error).__qualname__,
                )

    @staticmethod
    def _dto(record: repository.FileRecord) -> BookFileDTO:
        return BookFileDTO(
            id=UUID(record.id),
            format=record.format,
            file_status=cast(
                Literal["present", "missing"], record.file_status
            ),
            size_bytes=record.size_bytes,
        )