import hashlib
import os
import stat as statlib
from dataclasses import dataclass
from pathlib import Path
from time import time_ns
from uuid import uuid4

from sqlalchemy import Engine, select

from app.db.models.books import Book
from app.db.models.storage import BookFile, StorageObject, StorageRoot
from app.db.session import create_session_factory
from app.modules.files.service import _detect_format as detect_format

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
    def __init__(self, engine: Engine) -> None:
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
        if not root_path.is_dir():
            raise ScanError("STORAGE_ROOT_UNAVAILABLE")

        files = self._collect(root_path)
        counters = {
            "scanned": len(files),
            "created": 0,
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
                self._process(root.id, path, rel, deep, counters)
            except Exception:  # noqa: BLE001 - one bad file must not abort the scan
                counters["errors"] += 1
        self._reconcile(root.id, seen, counters)
        return {"rootId": root_id, **counters}

    def _collect(self, root_path: Path) -> list[Path]:
        results: list[Path] = []

        def onerror(_error: OSError) -> None:
            raise ScanError("SCAN_FAILED")

        for current, dirs, names in os.walk(
            root_path, followlinks=False, onerror=onerror
        ):
            dirs[:] = [
                name
                for name in dirs
                if not name.startswith(".")
                and not (Path(current) / name).is_symlink()
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
        return results

    def _process(
        self,
        root_id: str,
        path: Path,
        rel: str,
        deep: bool,
        counters: dict[str, int],
    ) -> None:
        try:
            info = path.stat()
        except OSError:
            counters["errors"] += 1
            return
        if not statlib.S_ISREG(info.st_mode):
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
        if self._create(root_id, rel, path, file_format, size, mtime_ms, sha256):
            counters["created"] += 1
        else:
            counters["unchanged"] += 1

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

    def _create(
        self,
        root_id: str,
        rel: str,
        path: Path,
        file_format: str,
        size: int,
        mtime_ms: int,
        sha256: str,
    ) -> bool:
        now = time_ns() // 1_000_000
        with self.write_sessions.begin() as session:
            duplicate = session.scalar(
                select(StorageObject.id)
                .where(
                    StorageObject.root_id == root_id,
                    StorageObject.relative_path == rel,
                )
                .limit(1)
            )
            if duplicate is not None:
                return False
            object_id = str(uuid4())
            book_id = str(uuid4())
            session.add(
                StorageObject(
                    id=object_id,
                    root_id=root_id,
                    relative_path=rel,
                    size_bytes=size,
                    sha256=sha256,
                    file_status="present",
                    mtime_ms=mtime_ms,
                )
            )
            session.flush()
            session.add(
                Book(
                    id=book_id,
                    title=(path.stem.strip() or path.name)[:500],
                    description=None,
                    reading_status="unread",
                    cover_path=None,
                    created_at=now,
                    updated_at=now,
                )
            )
            session.flush()
            session.add(
                BookFile(
                    id=str(uuid4()),
                    book_id=book_id,
                    storage_object_id=object_id,
                    format=file_format,
                )
            )
            return True

    def _reconcile(
        self, root_id: str, seen: set[str], counters: dict[str, int]
    ) -> None:
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