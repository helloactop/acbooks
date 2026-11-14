from sqlalchemy import Select, func, or_, select
from sqlalchemy.orm import Session

from app.db.models.books import Book
from app.db.models.reading import ReadingSession as Reading
from app.db.models.storage import BookFile
from app.modules.reader.schemas import SessionRead
from app.modules.stats.schemas import TimelineSession


def matching(start: int, end: int, book_id: str | None) -> Select[Reading]:
    statement = select(Reading).where(
        Reading.started_at < end,
        Reading.ended_at > start,
        Reading.active_seconds > 0,
    )
    if book_id is not None:
        statement = statement.where(Reading.book_id == book_id)
    return statement


def earliest_start(db: Session, end: int, book_id: str | None) -> int | None:
    statement = select(func.min(Reading.started_at)).where(Reading.started_at < end)
    if book_id is not None:
        statement = statement.where(Reading.book_id == book_id)
    return db.scalar(statement)


def books_by_id(db: Session, ids: list[str]) -> dict[str, Book]:
    return {book.id: book for book in db.scalars(select(Book).where(Book.id.in_(ids)))}


def previous_ends(db: Session, start: int, book_ids: set[str]) -> dict[str, int]:
    if not book_ids:
        return {}
    statement = (
        select(Reading.book_id, func.max(Reading.ended_at))
        .where(
            Reading.started_at < start,
            Reading.ended_at.is_not(None),
            Reading.active_seconds > 0,
        )
        .group_by(Reading.book_id)
    )
    return {
        book: end for book, end in db.execute(statement) if book in book_ids and end is not None
    }


def timeline(
    db: Session,
    start: int,
    end: int,
    book_id: str | None,
    limit: int,
    offset: int,
    include_inactive: bool,
) -> tuple[list[TimelineSession], int]:
    sessions = matching(start, end, book_id)
    if include_inactive:
        sessions = select(Reading).where(
            Reading.started_at < end,
            or_(Reading.started_at >= start, Reading.ended_at > start),
        )
        if book_id is not None:
            sessions = sessions.where(Reading.book_id == book_id)
    statement = (
        sessions.add_columns(Book.title, BookFile.format)
        .join(Book, Book.id == Reading.book_id)
        .outerjoin(BookFile, BookFile.id == Reading.book_file_id)
    )
    total = db.scalar(select(func.count()).select_from(statement.subquery())) or 0
    page = statement.order_by(Reading.started_at.desc(), Reading.id).limit(limit).offset(offset)
    return [
        TimelineSession.model_validate(
            {
                **{name: getattr(row, name) for name in SessionRead.model_fields},
                "book_title": title,
                "file_format": file_format,
            }
        )
        for row, title, file_format in db.execute(page)
    ], total
