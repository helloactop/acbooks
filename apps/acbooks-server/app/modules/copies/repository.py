from sqlalchemy import delete, func, select
from sqlalchemy.orm import Session

from app.db.models.books import Book
from app.db.models.copies import BookCopy, Location


def book_exists(session: Session, book_id: str) -> bool:
    return session.get(Book, book_id) is not None


def get_location(session: Session, location_id: str) -> Location | None:
    return session.get(Location, location_id)


def list_locations(session: Session) -> list[Location]:
    return list(
        session.scalars(
            select(Location).order_by(Location.name.collate("NOCASE"), Location.id)
        ).all()
    )


def location_edges(session: Session) -> dict[str, str | None]:
    return {
        location_id: parent_id
        for location_id, parent_id in session.execute(
            select(Location.id, Location.parent_id)
        ).all()
    }


def location_copy_counts(session: Session) -> dict[str, int]:
    return {
        location_id: int(total)
        for location_id, total in session.execute(
            select(BookCopy.location_id, func.count())
            .where(BookCopy.location_id.is_not(None))
            .group_by(BookCopy.location_id)
        ).all()
        if location_id is not None
    }


def has_children(session: Session, location_id: str) -> bool:
    total = session.scalar(
        select(func.count())
        .select_from(Location)
        .where(Location.parent_id == location_id)
    )
    return int(total or 0) > 0


def add_location(session: Session, location: Location) -> None:
    session.add(location)
    session.flush()


def delete_location(session: Session, location_id: str) -> None:
    session.execute(delete(Location).where(Location.id == location_id))


def get_copy(session: Session, copy_id: str) -> BookCopy | None:
    return session.get(BookCopy, copy_id)


def add_copy(session: Session, copy: BookCopy) -> None:
    session.add(copy)
    session.flush()


def delete_copy(session: Session, copy_id: str) -> None:
    session.execute(delete(BookCopy).where(BookCopy.id == copy_id))


def list_book_copies(
    session: Session, book_id: str
) -> list[tuple[BookCopy, str | None]]:
    return [
        (row[0], row[1])
        for row in session.execute(
            select(BookCopy, Location.name)
            .outerjoin(Location, Location.id == BookCopy.location_id)
            .where(BookCopy.book_id == book_id)
            .order_by(BookCopy.id)
        ).all()
    ]