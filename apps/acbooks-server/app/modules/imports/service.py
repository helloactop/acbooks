import hashlib
import os
import stat as statlib
from dataclasses import dataclass
from pathlib import Path
from time import time_ns
from uuid import uuid4

from sqlalchemy import Engine, select
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.core.errors import BusinessError, BusinessErrorCode
from app.db.models.books import Book
from app.db.models.storage import BookFile, StorageObject, StorageRoot
from app.db.session import create_session_factory
from app.modules.books import repository as books
from app.modules.books.schemas import BookCreate
from app.modules.books.service import publish_cover
from app.modules.files.service import _detect_format as detect_format
from app.modules.imports import repository
from app.modules.imports.schemas import PendingFile, PendingFilePreview, PendingFiles, ResolvedFile
from app.modules.metadata.extract import EmbeddedMetadata
from app.modules.metadata.locks import PLACEHOLDER_TITLE
from app.modules.metadata.service import MetadataService

SUPPORTED_SUFFIXES = {".pdf": "pdf", ".epub": "epub"}
SKIP_SUFFIXES = {".part", ".tmp", ".crdownload", ".download"}
MAX_SCAN_ENTRIES = 100_000
HASH_CHUNK = 1024 * 1024


class ScanError(RuntimeError):
    def __init__(self, code: str) -> None:
        self.code = code
        super().__init__(code)


@dataclass(frozen=True)
class ScanObject:
    id: str
    size_bytes: int
    sha256: str | None
    file_status: str
    mtime_ms: int


def _hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(HASH_CHUNK), b""):
            digest.update(chunk)
    return digest.hexdigest()


class ScanService:
    def __init__(self, engine: Engine, covers_dir: Path | None = None) -> None:
        self.covers_dir = covers_dir if covers_dir is not None else Settings().covers_dir
        self.read_sessions = create_session_factory(engine)
        self.write_sessions = create_session_factory(
            engine.execution_options(sqlite_begin_immediate=True)
        )

    def run(self, root_id: str, deep: bool) -> dict[str, object]:
        with self.read_sessions() as session:
            root = session.get(StorageRoot, root_id)
        if root is None:
            raise ScanError("STORAGE_ROOT_NOT_FOUND")
        root_path = Path(root.root_path)
        if not root_path.is_dir() or root_path.resolve() != root_path:
            raise ScanError("STORAGE_ROOT_UNAVAILABLE")

        files = self._collect(root_path)
        counters = {
            "scanned": len(files),
            "created": 0,
            "pending": 0,
            "updated": 0,
            "unchanged": 0,
            "missing": 0,
            "skipped": 0,
            "errors": 0,
        }
        seen: set[str] = set()
        for path in files:
            rel = path.relative_to(root_path).as_posix()
            seen.add(rel)
            try:
                self._process(root.id, root_path, path, rel, deep, counters)
            except Exception:  # noqa: BLE001 - one bad file must not abort the scan
                counters["errors"] += 1
        if root_path.is_dir() and root_path.resolve() == root_path and seen:
            self._reconcile(root.id, seen, counters)
        return {"rootId": root_id, **counters}

    def _collect(self, root_path: Path) -> list[Path]:
        results: list[Path] = []

        def onerror(_error: OSError) -> None:
            raise ScanError("SCAN_FAILED")

        for current, dirs, names in os.walk(root_path, followlinks=False, onerror=onerror):
            dirs[:] = [
                name
                for name in dirs
                if not name.startswith(".") and not (Path(current) / name).is_symlink()
            ]
            for name in names:
                if name.startswith("."):
                    continue
                path = Path(current) / name
                suffix = path.suffix.lower()
                if suffix in SKIP_SUFFIXES or suffix not in SUPPORTED_SUFFIXES:
                    continue
                if path.is_symlink():
                    continue
                results.append(path)
                if len(results) > MAX_SCAN_ENTRIES:
                    raise ScanError("SCAN_LIMIT_EXCEEDED")
        return sorted(results)

    def _process(
        self,
        root_id: str,
        root_path: Path,
        path: Path,
        rel: str,
        deep: bool,
        counters: dict[str, int],
    ) -> None:
        try:
            info = self._file_stat(root_path, path)
        except OSError:
            counters["errors"] += 1
            return
        size = info.st_size
        mtime_ms = int(info.st_mtime * 1000)
        if size == 0:
            counters["skipped"] += 1
            return

        existing = self._existing(root_id, rel)
        if (
            existing is not None
            and not deep
            and existing.file_status == "present"
            and existing.size_bytes == size
            and existing.mtime_ms == mtime_ms
        ):
            counters["unchanged"] += 1
            return

        try:
            sha256 = _hash_file(path)
        except OSError:
            counters["errors"] += 1
            return

        if existing is not None:
            self._check_stable(root_path, path, info)
            if (
                existing.sha256 == sha256
                and existing.size_bytes == size
                and existing.mtime_ms == mtime_ms
                and existing.file_status == "present"
            ):
                counters["unchanged"] += 1
                return
            self._update(existing.id, size, mtime_ms, sha256)
            counters["updated"] += 1
            return

        file_format = detect_format(path, size)
        if file_format is None:
            counters["skipped"] += 1
            return
        metadata = MetadataService.extract(path, file_format)
        self._check_stable(root_path, path, info)
        result = self._create(root_id, rel, file_format, size, mtime_ms, sha256, metadata)
        counters[result] += 1

    @staticmethod
    def _file_stat(root: Path, path: Path) -> os.stat_result:
        if path.resolve() != path or not path.is_relative_to(root):
            raise OSError("File is outside the registered path")
        result = path.stat(follow_symlinks=False)
        if not statlib.S_ISREG(result.st_mode):
            raise OSError("File is not regular")
        return result

    @classmethod
    def _check_stable(cls, root: Path, path: Path, previous: os.stat_result) -> None:
        current = cls._file_stat(root, path)
        if (current.st_dev, current.st_ino, current.st_size, current.st_mtime_ns) != (
            previous.st_dev,
            previous.st_ino,
            previous.st_size,
            previous.st_mtime_ns,
        ):
            raise ScanError("SCAN_FILE_CHANGED")

    def _existing(self, root_id: str, rel: str) -> ScanObject | None:
        with self.read_sessions() as session:
            row = session.execute(
                select(
                    StorageObject.id,
                    StorageObject.size_bytes,
                    StorageObject.sha256,
                    StorageObject.file_status,
                    StorageObject.mtime_ms,
                ).where(
                    StorageObject.root_id == root_id,
                    StorageObject.relative_path == rel,
                )
            ).first()
        if row is None:
            return None
        return ScanObject(
            id=row[0],
            size_bytes=row[1],
            sha256=row[2],
            file_status=row[3],
            mtime_ms=row[4],
        )

    def _update(self, object_id: str, size: int, mtime_ms: int, sha256: str) -> None:
        with self.write_sessions.begin() as session:
            obj = session.get(StorageObject, object_id)
            if obj is None:
                return
            obj.size_bytes = size
            obj.mtime_ms = mtime_ms
            obj.sha256 = sha256
            obj.file_status = "present"

    def _save_cover(self, book_id: str, metadata: EmbeddedMetadata) -> Path | None:
        if metadata.cover is None:
            return None
        try:
            return publish_cover(self.covers_dir, book_id, metadata.cover)
        except BusinessError:
            return None

    @staticmethod
    def _add_book(
        session: Session,
        book_id: str,
        payload: BookCreate,
        cover: Path | None,
        placeholder_title: bool,
    ) -> None:
        now = time_ns() // 1_000_000
        books.add_book(
            session,
            Book(
                id=book_id,
                title=payload.title,
                description=payload.description,
                series=payload.series,
                metadata_locks=PLACEHOLDER_TITLE if placeholder_title else 0,
                reading_status="unread",
                cover_path=cover.name if cover else None,
                cover_updated_at=now if cover else None,
                created_at=now,
                updated_at=now,
            ),
        )
        books.replace_contributors(session, book_id, "author", payload.authors)
        books.replace_contributors(session, book_id, "translator", payload.translators)
        books.replace_identifiers(session, book_id, payload.isbn)

    def _create(
        self,
        root_id: str,
        rel: str,
        file_format: str,
        size: int,
        mtime_ms: int,
        sha256: str,
        metadata: EmbeddedMetadata,
    ) -> str:
        payload = metadata.book(rel)
        book_id = str(uuid4())
        cover = self._save_cover(book_id, metadata)
        keep_cover = False
        try:
            with self.write_sessions.begin() as session:
                if repository.storage_exists(session, root_id, rel):
                    return "unchanged"
                obj = StorageObject(
                    id=str(uuid4()),
                    root_id=root_id,
                    relative_path=rel,
                    size_bytes=size,
                    sha256=sha256,
                    file_status="present",
                    mtime_ms=mtime_ms,
                )
                session.add(obj)
                session.flush()
                if repository.candidates(session, payload.title, payload.isbn, sha256):
                    return "pending"
                self._add_book(session, book_id, payload, cover, metadata.title is None)
                session.add(
                    BookFile(
                        id=str(uuid4()),
                        book_id=book_id,
                        storage_object_id=obj.id,
                        format=file_format,
                    )
                )
                if payload.isbn is not None:
                    MetadataService.enqueue_auto_fill(session, book_id)
            keep_cover = True
            return "created"
        finally:
            if cover is not None and not keep_cover:
                cover.unlink(missing_ok=True)

    def _inspect_pending(
        self,
        obj: StorageObject,
        root: StorageRoot,
    ) -> tuple[str, EmbeddedMetadata, os.stat_result]:
        path = Path(root.root_path) / obj.relative_path
        try:
            info = self._file_stat(Path(root.root_path), path)
            if (info.st_size, int(info.st_mtime * 1000)) != (obj.size_bytes, obj.mtime_ms):
                raise ScanError("SCAN_FILE_CHANGED")
            file_format = detect_format(path, info.st_size)
            if file_format is None or _hash_file(path) != obj.sha256:
                raise ScanError("SCAN_FILE_CHANGED")
            metadata = MetadataService.extract(path, file_format)
            self._check_stable(Path(root.root_path), path, info)
            return file_format, metadata, info
        except (OSError, ScanError) as error:
            raise BusinessError(BusinessErrorCode.IMPORT_FILE_CHANGED) from error

    def pending(self, root_id: str, limit: int, offset: int) -> PendingFiles:
        with self.read_sessions() as session:
            root = session.get(StorageRoot, root_id)
            if root is None:
                raise BusinessError(BusinessErrorCode.STORAGE_ROOT_NOT_FOUND)
            objects, total = repository.pending(session, root_id, limit, offset)
        return PendingFiles(
            items=[
                PendingFile(id=obj.id, relative_path=obj.relative_path, size_bytes=obj.size_bytes)
                for obj in objects
            ],
            total=total,
            limit=limit,
            offset=offset,
        )

    def preview(self, object_id: str) -> PendingFilePreview:
        with self.read_sessions() as session:
            obj, root = repository.get_pending(session, object_id)
        file_format, metadata, _ = self._inspect_pending(obj, root)
        payload = metadata.book(obj.relative_path)
        with self.read_sessions() as session:
            candidates = repository.candidates(session, payload.title, payload.isbn, obj.sha256)
        return PendingFilePreview(
            id=obj.id,
            relative_path=obj.relative_path,
            size_bytes=obj.size_bytes,
            format=file_format,
            title=payload.title,
            authors=payload.authors,
            description=payload.description,
            isbn=payload.isbn,
            candidates=candidates,
        )

    def resolve(self, object_id: str, target_id: str | None) -> ResolvedFile:
        with self.read_sessions() as session:
            obj, root = repository.get_pending(session, object_id)
        file_format, metadata, info = self._inspect_pending(obj, root)
        payload = metadata.book(obj.relative_path)
        book_id = target_id or str(uuid4())
        file_id = str(uuid4())
        cover = self._save_cover(book_id, metadata) if target_id is None else None
        try:
            try:
                self._check_stable(
                    Path(root.root_path), Path(root.root_path) / obj.relative_path, info
                )
            except (OSError, ScanError) as error:
                raise BusinessError(BusinessErrorCode.IMPORT_FILE_CHANGED) from error
            with self.write_sessions.begin() as session:
                current, current_root = repository.get_pending(session, object_id)
                if (
                    current.root_id,
                    current.relative_path,
                    current.sha256,
                    current.mtime_ms,
                    current.size_bytes,
                    current_root.root_path,
                ) != (
                    obj.root_id,
                    obj.relative_path,
                    obj.sha256,
                    obj.mtime_ms,
                    obj.size_bytes,
                    root.root_path,
                ):
                    raise BusinessError(BusinessErrorCode.IMPORT_FILE_CHANGED)
                if target_id is None:
                    self._add_book(session, book_id, payload, cover, metadata.title is None)
                    if payload.isbn is not None:
                        MetadataService.enqueue_auto_fill(session, book_id)
                elif books.get_book(session, book_id) is None:
                    raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)
                session.add(
                    BookFile(
                        id=file_id,
                        book_id=book_id,
                        storage_object_id=object_id,
                        format=file_format,
                    )
                )
        except Exception:
            if cover is not None:
                cover.unlink(missing_ok=True)
            raise
        return ResolvedFile(book_id=book_id, file_id=file_id)

    def _reconcile(self, root_id: str, seen: set[str], counters: dict[str, int]) -> None:
        with self.write_sessions.begin() as session:
            objects = session.scalars(
                select(StorageObject).where(
                    StorageObject.root_id == root_id,
                    StorageObject.file_status == "present",
                )
            ).all()
            for obj in objects:
                if obj.relative_path not in seen:
                    obj.file_status = "missing"
                    counters["missing"] += 1
