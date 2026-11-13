import json
import logging
import os
import tempfile
from pathlib import Path
from time import time_ns
from typing import cast
from uuid import UUID, uuid4

from sqlalchemy import Engine

from app.core.errors import BusinessError, BusinessErrorCode
from app.db.models.books import Book
from app.db.session import create_session_factory
from app.modules.books import repository
from app.modules.books.filtering import (
    FILTER_FIELDS,
    MAX_FILTER_CONDITIONS,
    STATIC_FILTER_OPTIONS,
    BookFilterExpression,
    BookFilterOptionSource,
    InvalidBookFilter,
    parse_filter_expression,
)
from app.modules.books.repository import BookListQuery, BookRecord
from app.modules.books.schemas import (
    BatchResult,
    BookBatchDelete,
    BookBatchUpdate,
    BookCopy,
    BookCreate,
    BookDetail,
    BookFile,
    BookFilterField,
    BookFilterOption,
    BookFilterOptionPage,
    BookFilterSchema,
    BookIdentifier,
    BookPage,
    BookSummary,
    BookUpdate,
    ReadingStatus,
)
from app.modules.books.types import BookOrder, BookSort, MediaType

logger = logging.getLogger("uvicorn.error")
MAX_COVER_BYTES = 5 * 1024 * 1024
COVER_MEDIA_TYPES = {
    "jpg": "image/jpeg",
    "png": "image/png",
    "webp": "image/webp",
}


def _cover_suffix(content: bytes) -> str | None:
    if content.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if content.startswith(b"\xff\xd8\xff"):
        return "jpg"
    if len(content) >= 12 and content[:4] == b"RIFF" and content[8:12] == b"WEBP":
        return "webp"
    return None


def _merged_names(
    current: list[str], add: list[str], remove: list[str]
) -> list[str]:
    removed = {name.casefold() for name in remove}
    merged = [name for name in current if name.casefold() not in removed]
    seen = {name.casefold() for name in merged}
    for name in add:
        key = name.casefold()
        if key not in seen and key not in removed:
            merged.append(name)
            seen.add(key)
    return merged


class BooksService:
    def __init__(self, engine: Engine, covers_dir: Path) -> None:
        self.read_sessions = create_session_factory(engine)
        self.write_sessions = create_session_factory(
            engine.execution_options(sqlite_begin_immediate=True)
        )
        self.covers_dir = covers_dir

    @staticmethod
    def _isbn(record: BookRecord) -> str | None:
        for scheme, value in record.identifiers:
            if scheme == "isbn":
                return value
        return None

    def _summary(self, record: BookRecord) -> BookSummary:
        return BookSummary(
            id=UUID(record.book.id),
            title=record.book.title,
            authors=record.authors,
            translators=record.translators,
            isbn=self._isbn(record),
            reading_status=cast(ReadingStatus, record.book.reading_status),
            tags=record.tags,
            shelves=record.shelves,
            copy_count=len(record.copies),
            file_count=len(record.files),
            formats=sorted({file.format for file in record.files}),
            has_cover=bool(
                record.book.cover_path is not None
                and record.book.cover_path.strip()
            ),
            cover_updated_at=record.book.cover_updated_at,
            created_at=record.book.created_at,
            updated_at=record.book.updated_at,
        )

    def _detail(self, record: BookRecord) -> BookDetail:
        summary = self._summary(record)
        return BookDetail(
            **summary.model_dump(),
            description=record.book.description,
            identifiers=[
                BookIdentifier(scheme=scheme, value=value)
                for scheme, value in record.identifiers
            ],
            copies=[
                BookCopy(
                    id=UUID(copy.id),
                    location_id=(
                        UUID(copy.location_id)
                        if copy.location_id is not None
                        else None
                    ),
                    location_name=copy.location_name,
                    acquired_at=copy.acquired_at,
                    note=copy.note,
                )
                for copy in record.copies
            ],
            files=[
                BookFile(
                    id=UUID(file.id),
                    format=file.format,
                    file_status=file.file_status,
                    size_bytes=file.size_bytes,
                )
                for file in record.files
            ],
        )

    @staticmethod
    def _filter_expression(filters: str | None) -> BookFilterExpression | None:
        if filters is None or not filters.strip():
            return None
        try:
            return parse_filter_expression(json.loads(filters))
        except (json.JSONDecodeError, InvalidBookFilter) as error:
            raise BusinessError(BusinessErrorCode.INVALID_BOOK_FILTER) from error

    def get_filter_schema(self) -> BookFilterSchema:
        return BookFilterSchema(
            fields=[
                BookFilterField(
                    key=field.key,
                    group=field.group,
                    type=field.field_type,
                    operators=list(field.operators),
                    option_source=field.option_source,
                    allow_custom=field.allow_custom,
                    unit=field.unit,
                    options=[
                        BookFilterOption(
                            value=option.value,
                            label=option.label,
                            count=option.count,
                        )
                        for option in STATIC_FILTER_OPTIONS.get(field.key, ())
                    ],
                )
                for field in FILTER_FIELDS
            ],
            max_conditions=MAX_FILTER_CONDITIONS,
        )

    def get_filter_options(
        self,
        *,
        source: BookFilterOptionSource,
        query: str,
        limit: int,
    ) -> BookFilterOptionPage:
        normalized = query.strip()
        with self.read_sessions() as session:
            options, has_more = repository.list_filter_options(
                session,
                source=source,
                query=normalized,
                limit=limit,
            )
        return BookFilterOptionPage(
            source=source,
            query=normalized,
            options=[
                BookFilterOption(
                    value=option.value,
                    label=option.label,
                    count=option.count,
                )
                for option in options
            ],
            has_more=has_more,
            index_ready=True,
        )

    def list_books(
        self,
        *,
        limit: int,
        offset: int,
        q: str | None,
        reading_status: ReadingStatus | None,
        tag: str | None,
        shelf_id: UUID | None,
        media_type: MediaType | None,
        media_format: str | None,
        filters: str | None,
        sort: BookSort,
        order: BookOrder,
    ) -> BookPage:
        query = BookListQuery(
            q=q.strip() if q is not None and q.strip() else None,
            reading_status=reading_status,
            tag=tag.strip() if tag is not None and tag.strip() else None,
            shelf_id=str(shelf_id) if shelf_id is not None else None,
            media_type=media_type,
            media_format=(
                media_format.strip().lower()
                if media_format is not None and media_format.strip()
                else None
            ),
            filter_expression=self._filter_expression(filters),
            sort=sort,
            order=order,
            limit=limit,
            offset=offset,
        )
        with self.read_sessions() as session:
            books, total = repository.find_books(session, query)
            records = repository.load_records(session, books)
            items = [self._summary(record) for record in records]

        return BookPage(
            items=items,
            total=total,
            limit=limit,
            offset=offset,
        )

    def get_book(self, book_id: UUID) -> BookDetail:
        with self.read_sessions() as session:
            book = repository.get_book(session, str(book_id))
            if book is None:
                raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)
            record = repository.load_records(session, [book])[0]
            return self._detail(record)

    def create_book(self, payload: BookCreate) -> BookDetail:
        now = time_ns() // 1_000_000
        book_id = str(uuid4())

        with self.write_sessions.begin() as session:
            book = Book(
                id=book_id,
                title=payload.title,
                description=payload.description,
                reading_status=payload.reading_status or "unread",
                cover_path=None,
                created_at=now,
                updated_at=now,
            )
            repository.add_book(session, book)
            repository.replace_contributors(
                session, book_id, "author", payload.authors
            )
            repository.replace_contributors(
                session, book_id, "translator", payload.translators
            )
            repository.replace_identifiers(session, book_id, payload.isbn)
            repository.replace_tags(session, book_id, payload.tags)
            repository.replace_shelves(session, book_id, payload.shelves)
            session.flush()
            record = repository.load_records(session, [book])[0]
            return self._detail(record)

    def update_book(self, book_id: UUID, payload: BookUpdate) -> BookDetail:
        fields = payload.model_fields_set
        now = time_ns() // 1_000_000
        key = str(book_id)

        with self.write_sessions.begin() as session:
            book = repository.get_book(session, key)
            if book is None:
                raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)

            if "title" in fields:
                assert payload.title is not None
                book.title = payload.title
            if "description" in fields:
                book.description = payload.description
            if "reading_status" in fields:
                assert payload.reading_status is not None
                book.reading_status = payload.reading_status

            book.updated_at = now

            if "authors" in fields:
                assert payload.authors is not None
                repository.replace_contributors(
                    session, key, "author", payload.authors
                )
            if "translators" in fields:
                assert payload.translators is not None
                repository.replace_contributors(
                    session, key, "translator", payload.translators
                )
            if "isbn" in fields:
                repository.replace_identifiers(session, key, payload.isbn)
            if "tags" in fields:
                assert payload.tags is not None
                repository.replace_tags(session, key, payload.tags)
            if "shelves" in fields:
                assert payload.shelves is not None
                repository.replace_shelves(session, key, payload.shelves)

            session.flush()
            record = repository.load_records(session, [book])[0]
            return self._detail(record)

    def batch_update(self, payload: BookBatchUpdate) -> BatchResult:
        ids = [str(book_id) for book_id in payload.book_ids]
        now = time_ns() // 1_000_000
        with self.write_sessions.begin() as session:
            books = repository.get_books(session, ids)
            if len(books) != len(ids):
                raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)
            records = repository.load_records(session, books)
            for record in records:
                book = record.book
                if payload.reading_status is not None:
                    book.reading_status = payload.reading_status
                if payload.add_tags or payload.remove_tags:
                    repository.replace_tags(
                        session,
                        book.id,
                        _merged_names(
                            record.tags, payload.add_tags, payload.remove_tags
                        ),
                    )
                if payload.add_shelves or payload.remove_shelves:
                    repository.replace_shelves(
                        session,
                        book.id,
                        _merged_names(
                            record.shelves,
                            payload.add_shelves,
                            payload.remove_shelves,
                        ),
                    )
                book.updated_at = now
        return BatchResult(updated=len(ids))

    def batch_delete(self, payload: BookBatchDelete) -> BatchResult:
        ids = [str(book_id) for book_id in payload.book_ids]
        covers: list[str] = []
        with self.write_sessions.begin() as session:
            books = repository.get_books(session, ids)
            if len(books) != len(ids):
                raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)
            covers = [
                book.cover_path
                for book in books
                if book.cover_path is not None and book.cover_path.strip()
            ]
            repository.delete_books(session, ids)
        self._remove_cover_files(covers)
        return BatchResult(deleted=len(ids))

    def delete_book(self, book_id: UUID) -> None:
        self.batch_delete(BookBatchDelete(book_ids=[book_id]))

    def _cover_file(self, name: str) -> Path | None:
        filename = Path(name).name
        if filename != name:
            return None
        candidate = (self.covers_dir / filename).resolve()
        if candidate.parent != self.covers_dir.resolve():
            return None
        return candidate

    def _remove_cover_files(
        self, names: list[str], *, keep: str | None = None
    ) -> None:
        for name in names:
            path = self._cover_file(name)
            if path is None or path.name == keep:
                continue
            try:
                path.unlink(missing_ok=True)
            except OSError as error:
                logger.warning(
                    "operation=cover_cleanup result=failed error=%s",
                    type(error).__qualname__,
                )

    def _remove_all_covers(self, book_id: UUID, keep: str | None = None) -> None:
        self._remove_cover_files(
            [f"{book_id}.{suffix}" for suffix in COVER_MEDIA_TYPES], keep=keep
        )

    def save_cover(self, book_id: UUID, content: bytes) -> BookDetail:
        if not content or len(content) > MAX_COVER_BYTES:
            raise BusinessError(BusinessErrorCode.INVALID_COVER)
        suffix = _cover_suffix(content)
        if suffix is None:
            raise BusinessError(BusinessErrorCode.INVALID_COVER)
        with self.read_sessions() as session:
            if repository.get_book(session, str(book_id)) is None:
                raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)

        self.covers_dir.mkdir(parents=True, exist_ok=True)
        target = self.covers_dir / f"{book_id}.{suffix}"
        descriptor, temp_name = tempfile.mkstemp(
            prefix=f".{book_id}.", suffix=".tmp", dir=self.covers_dir
        )
        temp = Path(temp_name)
        try:
            os.close(descriptor)
            temp.write_bytes(content)
            os.replace(temp, target)
        finally:
            temp.unlink(missing_ok=True)

        now = time_ns() // 1_000_000
        try:
            with self.write_sessions.begin() as session:
                book = repository.get_book(session, str(book_id))
                if book is None:
                    raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)
                book.cover_path = target.name
                book.cover_updated_at = now
                book.updated_at = now
                session.flush()
                record = repository.load_records(session, [book])[0]
                detail = self._detail(record)
        except Exception:
            target.unlink(missing_ok=True)
            raise
        self._remove_all_covers(book_id, keep=target.name)
        return detail

    def delete_cover(self, book_id: UUID) -> BookDetail:
        now = time_ns() // 1_000_000
        with self.write_sessions.begin() as session:
            book = repository.get_book(session, str(book_id))
            if book is None:
                raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)
            book.cover_path = None
            book.cover_updated_at = now
            book.updated_at = now
            session.flush()
            record = repository.load_records(session, [book])[0]
            detail = self._detail(record)
        self._remove_all_covers(book_id)
        return detail

    def get_cover(self, book_id: UUID) -> tuple[Path, str]:
        with self.read_sessions() as session:
            book = repository.get_book(session, str(book_id))
            if book is None:
                raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)
            name = book.cover_path
        if name is None or not name.strip():
            raise BusinessError(BusinessErrorCode.COVER_NOT_FOUND)
        path = self._cover_file(name)
        if path is None or not path.is_file():
            raise BusinessError(BusinessErrorCode.COVER_NOT_FOUND)
        media_type = COVER_MEDIA_TYPES.get(
            path.suffix.lstrip(".").lower(), "application/octet-stream"
        )
        return path, media_type
