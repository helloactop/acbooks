from collections.abc import Sequence
from typing import cast
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.models.reading import Annotation, Bookmark, ReadingPosition, ReadingSession
from app.db.models.storage import BookFile, StorageObject
from app.db.models.sync import SyncChange
from app.modules.books.service import BooksService
from app.modules.reader.service import read_mark, read_position, read_session
from app.modules.sync.schemas import EntityType, SyncBookFile, SyncData, SyncRecord


def records(
    session: Session,
    books: BooksService,
    changes: Sequence[SyncChange],
) -> list[SyncRecord]:
    ids: dict[str, list[str]] = {}
    for change in changes:
        if change.operation == "upsert":
            ids.setdefault(change.entity_type, []).append(change.entity_id)

    data: dict[tuple[str, str], SyncData] = {}
    if keys := ids.get("book"):
        data.update({("book", str(book.id)): book for book in books.sync_details(session, keys)})
    if keys := ids.get("book_file"):
        files = session.execute(
            select(BookFile, StorageObject)
            .join(StorageObject, BookFile.storage_object_id == StorageObject.id)
            .where(BookFile.id.in_(keys))
        )
        for file, storage in files:
            data[("book_file", file.id)] = SyncBookFile(
                id=UUID(file.id),
                book_id=UUID(file.book_id),
                format=file.format,
                file_status=storage.file_status,
                size_bytes=storage.size_bytes,
                sha256=storage.sha256,
            )
    if keys := ids.get("reading_position"):
        for position in session.scalars(
            select(ReadingPosition).where(ReadingPosition.book_file_id.in_(keys))
        ):
            data[("reading_position", position.book_file_id)] = read_position(position)
    if keys := ids.get("reading_session"):
        for record in session.scalars(select(ReadingSession).where(ReadingSession.id.in_(keys))):
            data[("reading_session", record.id)] = read_session(record)
    if keys := ids.get("bookmark"):
        for bookmark in session.scalars(select(Bookmark).where(Bookmark.id.in_(keys))):
            data[("bookmark", bookmark.id)] = read_mark(bookmark)
    if keys := ids.get("annotation"):
        for annotation in session.scalars(select(Annotation).where(Annotation.id.in_(keys))):
            data[("annotation", annotation.id)] = read_mark(annotation)

    result = []
    for change in changes:
        value = data.get((change.entity_type, change.entity_id))
        result.append(
            SyncRecord(
                entity_type=cast(EntityType, change.entity_type),
                entity_id=UUID(change.entity_id),
                operation="upsert" if value is not None else "delete",
                revision=change.seq,
                data=value,
            )
        )
    return result
