from collections.abc import Sequence
from dataclasses import dataclass
from typing import Any
from uuid import uuid4

from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.db.models.books import Book
from app.db.models.reading import ReadingPosition
from app.db.models.storage import BookFile, StorageObject, StorageRoot


def get_book(session: Session, book_id: str) -> Book | None:
    return session.get(Book, book_id)


def ensure_managed_root(session: Session, root_path: str) -> StorageRoot:
    root = session.scalars(
        select(StorageRoot)
        .where(StorageRoot.mode == "managed", StorageRoot.root_path == root_path)
        .order_by(StorageRoot.id)
        .limit(1)
    ).first()
    if root is None:
        root = StorageRoot(id=str(uuid4()), name="library", root_path=root_path, mode="managed")
        session.add(root)
        session.flush()
    return root


def add_storage_object(session: Session, storage_object: StorageObject) -> None:
    session.add(storage_object)
    session.flush()


def add_book_file(session: Session, book_file: BookFile) -> None:
    session.add(book_file)
    session.flush()


@dataclass(frozen=True)
class FileRecord:
    id: str
    book_id: str
    format: str
    file_status: str
    size_bytes: int
    storage_object_id: str
    sha256: str | None
    mtime_ms: int
    root_mode: str
    root_path: str
    relative_path: str


def _records(rows: Sequence[Any]) -> list[FileRecord]:
    return [
        FileRecord(
            id=row[0],
            book_id=row[1],
            format=row[2],
            file_status=row[3],
            size_bytes=row[4],
            storage_object_id=row[5],
            sha256=row[6],
            mtime_ms=row[7],
            root_mode=row[8],
            root_path=row[9],
            relative_path=row[10],
        )
        for row in rows
    ]


def _select_records() -> Any:
    return (
        select(
            BookFile.id,
            BookFile.book_id,
            BookFile.format,
            StorageObject.file_status,
            StorageObject.size_bytes,
            StorageObject.id,
            StorageObject.sha256,
            StorageObject.mtime_ms,
            StorageRoot.mode,
            StorageRoot.root_path,
            StorageObject.relative_path,
        )
        .join(StorageObject, StorageObject.id == BookFile.storage_object_id)
        .join(StorageRoot, StorageRoot.id == StorageObject.root_id)
    )


def list_book_files(session: Session, book_id: str) -> list[FileRecord]:
    rows = session.execute(
        _select_records().where(BookFile.book_id == book_id).order_by(BookFile.id)
    ).all()
    return _records(rows)


def match_sha(
    session: Session,
    sha256: str,
    book_id: str | None,
    limit: int,
    offset: int,
    *,
    present_only: bool = False,
) -> list[FileRecord]:
    query = _select_records().where(StorageObject.sha256 == sha256)
    if book_id is not None:
        query = query.where(BookFile.book_id == book_id)
    if present_only:
        query = query.where(StorageObject.file_status == "present")
    query = query.order_by(BookFile.id).offset(offset).limit(limit)
    return _records(session.execute(query).all())


def get_file(session: Session, file_id: str) -> FileRecord | None:
    row = session.execute(_select_records().where(BookFile.id == file_id)).first()
    if row is None:
        return None
    return _records([row])[0]


def delete_file_rows(session: Session, file_id: str, storage_object_id: str) -> None:
    # reading_positions restrict book_files, so remove the position first.
    session.execute(delete(ReadingPosition).where(ReadingPosition.book_file_id == file_id))
    session.execute(delete(BookFile).where(BookFile.id == file_id))
    session.execute(delete(StorageObject).where(StorageObject.id == storage_object_id))


def unlink_book_file(session: Session, file_id: str) -> None:
    # Detach the book-file association but keep the storage object and file.
    session.execute(delete(ReadingPosition).where(ReadingPosition.book_file_id == file_id))
    session.execute(delete(BookFile).where(BookFile.id == file_id))
