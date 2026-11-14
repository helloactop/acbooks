import json
from time import time_ns
from typing import TypeVar, overload
from uuid import UUID, uuid4

from pydantic import ValidationError
from sqlalchemy import Engine, select
from sqlalchemy.orm import Session

from app.core.errors import BusinessError, BusinessErrorCode
from app.db.models.books import Book
from app.db.models.reading import Annotation, Bookmark, ReadingPosition, ReadingSession
from app.db.models.storage import BookFile
from app.db.session import create_session_factory
from app.modules.reader import repository
from app.modules.reader.schemas import (
    AnnotationCreate,
    AnnotationRead,
    AnnotationUpdate,
    BookmarkCreate,
    BookmarkRead,
    BookmarkUpdate,
    Mutation,
    PositionRead,
    PositionWrite,
    ReadingAccepted,
    ReadingDuplicate,
    ReadingPage,
    SessionCreate,
    SessionFields,
    SessionRead,
    SessionUpdate,
)

Mark = TypeVar("Mark", Bookmark, Annotation)
Record = TypeVar("Record", ReadingSession, Bookmark, Annotation)
ENTITY_TYPES = {
    ReadingSession: "reading_session",
    ReadingPosition: "reading_position",
    Bookmark: "bookmark",
    Annotation: "annotation",
}


class PositionConflict(Exception):
    def __init__(self, current: PositionRead | None) -> None:
        self.current = current
        super().__init__("Reading position has changed.")


def _now() -> int:
    return time_ns() // 1_000_000


def _require_book(session: Session, book_id: UUID) -> None:
    if session.get(Book, str(book_id)) is None:
        raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)


def _require_file(session: Session, file_id: UUID, book_id: UUID | None = None) -> None:
    file = session.get(BookFile, str(file_id))
    if file is None or (book_id is not None and file.book_id != str(book_id)):
        raise BusinessError(BusinessErrorCode.FILE_NOT_FOUND)


def _record(session: Session, model: type[Record], book_id: UUID, item_id: UUID) -> Record:
    row = session.get(model, str(item_id))
    if row is None or row.book_id != str(book_id):
        raise BusinessError(BusinessErrorCode.READING_RECORD_NOT_FOUND)
    return row


def _mutation(session: Session, payload: Mutation, now: int) -> bool:
    return repository.record_mutation(
        session, str(payload.device_id), str(payload.mutation_id), now
    )


def _change(
    session: Session,
    row: ReadingSession | Bookmark | Annotation | ReadingPosition,
    operation: str,
    now: int,
) -> None:
    repository.log_change(session, ENTITY_TYPES[type(row)], row.id, operation, now)


def _session(row: ReadingSession) -> SessionRead:
    return SessionRead.model_validate(row, from_attributes=True)


def _position(row: ReadingPosition) -> PositionRead:
    return PositionRead(
        book_file_id=UUID(row.book_file_id),
        locator=json.loads(row.locator_json),
        percent=row.percent,
        revision=row.revision,
        updated_at=row.updated_at,
    )


@overload
def read_mark(row: Bookmark) -> BookmarkRead: ...


@overload
def read_mark(row: Annotation) -> AnnotationRead: ...


def read_mark(row: Bookmark | Annotation) -> BookmarkRead | AnnotationRead:
    fields = {
        "id": row.id,
        "book_id": row.book_id,
        "book_file_id": row.book_file_id,
        "locator": json.loads(row.locator_json) if row.locator_json is not None else None,
        "created_at": row.created_at,
        "updated_at": row.updated_at,
    }
    if isinstance(row, Bookmark):
        return BookmarkRead.model_validate({**fields, "label": row.label})
    return AnnotationRead.model_validate({**fields, "quote": row.quote, "note": row.note})


class ReaderService:
    def __init__(self, engine: Engine) -> None:
        self.read = create_session_factory(engine)
        self.write = create_session_factory(engine.execution_options(sqlite_begin_immediate=True))

    def sessions(self, book_id: UUID, limit: int, offset: int) -> ReadingPage[SessionRead]:
        with self.read() as session:
            _require_book(session, book_id)
            rows, total = repository.list_records(
                session,
                select(ReadingSession)
                .where(ReadingSession.book_id == str(book_id))
                .order_by(ReadingSession.started_at.desc(), ReadingSession.id),
                limit,
                offset,
            )
            return ReadingPage[SessionRead](
                items=[_session(row) for row in rows],
                total=total,
                limit=limit,
                offset=offset,
            )

    def create_session(
        self,
        book_id: UUID,
        payload: SessionCreate,
    ) -> ReadingAccepted[SessionRead] | ReadingDuplicate:
        now = _now()
        with self.write.begin() as session:
            if not _mutation(session, payload, now):
                return ReadingDuplicate()
            _require_book(session, book_id)
            if payload.book_file_id is not None:
                _require_file(session, payload.book_file_id, book_id)
            row = ReadingSession(
                id=str(uuid4()),
                book_id=str(book_id),
                device_id=str(payload.device_id),
                book_file_id=str(payload.book_file_id)
                if payload.book_file_id is not None
                else None,
                started_at=payload.started_at,
                ended_at=payload.ended_at,
                active_seconds=payload.active_seconds,
                source=payload.source,
            )
            session.add(row)
            _change(session, row, "upsert", now)
            return ReadingAccepted[SessionRead](item=_session(row))

    def update_session(self, book_id: UUID, item_id: UUID, payload: SessionUpdate) -> SessionRead:
        with self.write.begin() as session:
            row = _record(session, ReadingSession, book_id, item_id)
            fields = {
                "started_at": row.started_at,
                "ended_at": row.ended_at,
                "active_seconds": row.active_seconds,
            }
            try:
                next_fields = SessionFields.model_validate(
                    {**fields, **payload.model_dump(exclude_unset=True)}
                )
            except ValidationError as exc:
                raise BusinessError(BusinessErrorCode.INVALID_READING_RECORD) from exc
            if row.source == "manual" and next_fields.ended_at is None:
                raise BusinessError(BusinessErrorCode.INVALID_READING_RECORD)
            row.started_at = next_fields.started_at
            row.ended_at = next_fields.ended_at
            row.active_seconds = next_fields.active_seconds
            _change(session, row, "upsert", _now())
            return _session(row)

    def get_position(self, file_id: UUID) -> PositionRead | None:
        with self.read() as session:
            _require_file(session, file_id)
            row = repository.get_position(session, str(file_id))
            return _position(row) if row is not None else None

    def save_position(
        self,
        file_id: UUID,
        payload: PositionWrite,
    ) -> ReadingAccepted[PositionRead] | ReadingDuplicate:
        now = _now()
        with self.write.begin() as session:
            if not _mutation(session, payload, now):
                return ReadingDuplicate()
            _require_file(session, file_id)
            row = repository.get_position(session, str(file_id))
            current_revision = row.revision if row is not None else 0
            if current_revision != payload.revision:
                raise PositionConflict(_position(row) if row is not None else None)
            if row is None:
                row = ReadingPosition(id=str(uuid4()), book_file_id=str(file_id))
                session.add(row)
            row.locator_json = json.dumps(payload.locator, allow_nan=False)
            row.percent = payload.percent
            row.revision = current_revision + 1
            row.updated_at = now
            _change(session, row, "upsert", now)
            return ReadingAccepted[PositionRead](item=_position(row))

    def list_marks(
        self, model: type[Mark], book_id: UUID, limit: int, offset: int
    ) -> tuple[list[Mark], int]:
        with self.read() as session:
            _require_book(session, book_id)
            return repository.list_records(
                session,
                select(model)
                .where(model.book_id == str(book_id))
                .order_by(model.created_at.desc(), model.id),
                limit,
                offset,
            )

    def create_mark(
        self,
        model: type[Mark],
        book_id: UUID,
        payload: BookmarkCreate | AnnotationCreate,
    ) -> Mark | None:
        now = _now()
        with self.write.begin() as session:
            if not _mutation(session, payload, now):
                return None
            _require_book(session, book_id)
            row = model(id=str(uuid4()), book_id=str(book_id), created_at=now, updated_at=now)
            self._apply_mark(session, row, book_id, payload)
            session.add(row)
            _change(session, row, "upsert", now)
            session.flush()
            session.expunge(row)
            return row

    def update_mark(
        self,
        model: type[Mark],
        book_id: UUID,
        item_id: UUID,
        payload: BookmarkUpdate | AnnotationUpdate,
    ) -> Mark:
        with self.write.begin() as session:
            row = _record(session, model, book_id, item_id)
            if not payload.model_fields_set:
                raise BusinessError(BusinessErrorCode.INVALID_READING_RECORD)
            self._apply_mark(session, row, book_id, payload)
            row.updated_at = max(row.updated_at + 1, _now())
            _change(session, row, "upsert", row.updated_at)
            session.flush()
            session.expunge(row)
            return row

    @staticmethod
    def _apply_mark(
        session: Session,
        row: Bookmark | Annotation,
        book_id: UUID,
        payload: BookmarkUpdate | AnnotationUpdate,
    ) -> None:
        if "book_file_id" in payload.model_fields_set:
            if payload.book_file_id is not None:
                _require_file(session, payload.book_file_id, book_id)
            row.book_file_id = (
                str(payload.book_file_id) if payload.book_file_id is not None else None
            )
        if "locator" in payload.model_fields_set:
            row.locator_json = json.dumps(payload.locator) if payload.locator is not None else None
        if isinstance(row, Bookmark) and isinstance(payload, BookmarkUpdate):
            if "label" in payload.model_fields_set:
                row.label = payload.label
            if not row.locator_json and not (row.label or "").strip():
                raise BusinessError(BusinessErrorCode.INVALID_READING_RECORD)
        if isinstance(row, Annotation) and isinstance(payload, AnnotationUpdate):
            for field in ("quote", "note"):
                if field in payload.model_fields_set:
                    setattr(row, field, getattr(payload, field))
            if not ((row.quote or "").strip() or (row.note or "").strip()):
                raise BusinessError(BusinessErrorCode.INVALID_READING_RECORD)

    def delete(self, model: type[Record], book_id: UUID, item_id: UUID) -> None:
        with self.write.begin() as session:
            row = _record(session, model, book_id, item_id)
            _change(session, row, "delete", _now())
            session.delete(row)
