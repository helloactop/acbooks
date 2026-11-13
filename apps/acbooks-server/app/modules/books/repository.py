from collections import defaultdict
from collections.abc import Sequence
from dataclasses import dataclass
from typing import Any, Literal, cast
from uuid import uuid4

from sqlalchemy import ColumnElement, delete, exists, func, or_, select
from sqlalchemy.orm import Session

from app.db.models.books import (
    Book,
    BookContributor,
    BookIdentifier,
    BookTag,
    Contributor,
    Shelf,
    ShelfBook,
    Tag,
)
from app.db.models.copies import BookCopy, Location
from app.db.models.reading import Annotation, Bookmark, ReadingSession
from app.db.models.storage import BookFile, StorageObject
from app.modules.books.filtering import (
    BookFilterExpression,
    BookFilterOption,
    BookFilterOptionSource,
    compile_filter_expression,
)
from app.modules.books.normalization import escape_like, normalize_isbn
from app.modules.books.types import BookOrder, BookSort, MediaType


@dataclass(frozen=True)
class BookListQuery:
    q: str | None
    reading_status: str | None
    tag: str | None
    shelf_id: str | None
    media_type: MediaType | None
    media_format: str | None
    filter_expression: BookFilterExpression | None
    sort: BookSort
    order: BookOrder
    limit: int
    offset: int


@dataclass(frozen=True)
class CopyRecord:
    id: str
    location_id: str | None
    location_name: str | None
    acquired_at: int | None
    note: str | None


@dataclass(frozen=True)
class FileRecord:
    id: str
    format: str
    file_status: Literal["present", "missing"]
    size_bytes: int


@dataclass(frozen=True)
class BookRecord:
    book: Book
    authors: list[str]
    translators: list[str]
    identifiers: list[tuple[str, str]]
    tags: list[str]
    shelves: list[str]
    copies: list[CopyRecord]
    files: list[FileRecord]


def _escape_pattern(value: str) -> str:
    return f"%{escape_like(value)}%"


def _search_condition(value: str) -> ColumnElement[bool]:
    pattern = _escape_pattern(value)
    isbn_candidate = normalize_isbn(value)
    identifier_values: list[ColumnElement[bool]] = [
        BookIdentifier.value.ilike(pattern, escape="\\")
    ]
    if isbn_candidate is not None:
        identifier_values.append(BookIdentifier.value == isbn_candidate)

    author_match = exists(
        select(1)
        .select_from(BookContributor)
        .join(Contributor, Contributor.id == BookContributor.contributor_id)
        .where(
            BookContributor.book_id == Book.id,
            func.acbooks_casefold(Contributor.name).like(
                func.acbooks_casefold(pattern), escape="\\"
            ),
        )
    )
    identifier_match = exists(
        select(1).where(
            BookIdentifier.book_id == Book.id,
            BookIdentifier.scheme == "isbn",
            or_(*identifier_values),
        )
    )
    return or_(
        func.acbooks_casefold(Book.title).like(
            func.acbooks_casefold(pattern), escape="\\"
        ),
        author_match,
        identifier_match,
    )


def _conditions(query: BookListQuery) -> list[ColumnElement[bool]]:
    conditions: list[ColumnElement[bool]] = []

    if query.q is not None and query.q != "":
        conditions.append(_search_condition(query.q))
    if query.reading_status is not None:
        conditions.append(Book.reading_status == query.reading_status)
    if query.tag is not None:
        conditions.append(
            exists(
                select(1)
                .select_from(BookTag)
                .join(Tag, Tag.id == BookTag.tag_id)
                .where(
                    BookTag.book_id == Book.id,
                    func.acbooks_casefold(Tag.name) == query.tag.casefold(),
                )
            )
        )
    if query.shelf_id is not None:
        conditions.append(
            exists(
                select(1).where(
                    ShelfBook.book_id == Book.id,
                    ShelfBook.shelf_id == query.shelf_id,
                )
            )
        )
    if query.media_type == "physical":
        conditions.append(
            exists(select(1).where(BookCopy.book_id == Book.id))
        )
    elif query.media_type == "digital":
        conditions.append(
            exists(select(1).where(BookFile.book_id == Book.id))
        )
    if query.media_format is not None:
        conditions.append(
            exists(
                select(1).where(
                    BookFile.book_id == Book.id,
                    func.acbooks_casefold(BookFile.format)
                    == query.media_format.casefold(),
                )
            )
        )
    if query.filter_expression is not None:
        smart_filter = compile_filter_expression(query.filter_expression)
        if smart_filter is not None:
            conditions.append(smart_filter)

    return conditions


def _order_by(query: BookListQuery) -> list[ColumnElement[Any]]:
    if query.sort == "title":
        primary: ColumnElement[Any] = Book.title.collate("NOCASE")
    elif query.sort == "author":
        author_name = (
            select(Contributor.name)
            .join(BookContributor, BookContributor.contributor_id == Contributor.id)
            .where(
                BookContributor.book_id == Book.id,
                BookContributor.role == "author",
            )
            .order_by(Contributor.name.collate("NOCASE"), Contributor.id)
            .limit(1)
            .correlate(Book)
            .scalar_subquery()
        )
        primary = func.coalesce(author_name, "")
    elif query.sort == "updatedAt":
        primary = cast(ColumnElement[Any], Book.updated_at)
    else:
        primary = cast(ColumnElement[Any], Book.created_at)

    direction = primary.desc() if query.order == "desc" else primary.asc()
    return [direction, Book.id.asc()]


def list_filter_options(
    session: Session,
    *,
    source: BookFilterOptionSource,
    query: str,
    limit: int,
) -> tuple[list[BookFilterOption], bool]:
    pattern = f"%{escape_like(query)}%" if query else None

    if source in {"authors", "translators"}:
        role = "author" if source == "authors" else "translator"
        count = func.count(func.distinct(BookContributor.book_id))
        statement = (
            select(Contributor.name, count)
            .select_from(BookContributor)
            .join(Contributor, Contributor.id == BookContributor.contributor_id)
            .where(BookContributor.role == role)
            .group_by(Contributor.name)
            .order_by(
                count.desc(),
                func.acbooks_casefold(Contributor.name),
            )
        )
        if pattern is not None:
            statement = statement.where(
                func.acbooks_casefold(Contributor.name).like(
                    func.acbooks_casefold(pattern), escape="\\"
                )
            )
    elif source == "tags":
        count = func.count(func.distinct(BookTag.book_id))
        statement = (
            select(Tag.name, count)
            .select_from(BookTag)
            .join(Tag, Tag.id == BookTag.tag_id)
            .group_by(Tag.name)
            .order_by(count.desc(), func.acbooks_casefold(Tag.name))
        )
        if pattern is not None:
            statement = statement.where(
                func.acbooks_casefold(Tag.name).like(
                    func.acbooks_casefold(pattern), escape="\\"
                )
            )
    elif source == "shelves":
        count = func.count(func.distinct(ShelfBook.book_id))
        statement = (
            select(Shelf.name, count)
            .select_from(ShelfBook)
            .join(Shelf, Shelf.id == ShelfBook.shelf_id)
            .group_by(Shelf.name)
            .order_by(count.desc(), func.acbooks_casefold(Shelf.name))
        )
        if pattern is not None:
            statement = statement.where(
                func.acbooks_casefold(Shelf.name).like(
                    func.acbooks_casefold(pattern), escape="\\"
                )
            )
    else:
        normalized_format = func.acbooks_casefold(BookFile.format)
        count = func.count(func.distinct(BookFile.book_id))
        statement = (
            select(normalized_format, count)
            .group_by(normalized_format)
            .order_by(count.desc(), normalized_format)
        )
        if pattern is not None:
            statement = statement.where(
                normalized_format.like(
                    func.acbooks_casefold(pattern), escape="\\"
                )
            )

    rows = session.execute(statement.limit(limit + 1)).all()
    has_more = len(rows) > limit
    options = [
        BookFilterOption(
            value=str(row[0]),
            label=str(row[0]).upper() if source == "formats" else str(row[0]),
            count=int(row[1]),
        )
        for row in rows[:limit]
    ]
    return options, has_more


def get_book(session: Session, book_id: str) -> Book | None:
    return session.get(Book, book_id)


def get_books(session: Session, book_ids: Sequence[str]) -> list[Book]:
    return list(session.scalars(select(Book).where(Book.id.in_(book_ids))).all())


def delete_books(session: Session, book_ids: Sequence[str]) -> None:
    for table in (
        Annotation,
        Bookmark,
        ReadingSession,
        BookFile,
        BookCopy,
        BookContributor,
        BookIdentifier,
        BookTag,
        ShelfBook,
    ):
        session.execute(delete(table).where(table.book_id.in_(book_ids)))
    session.execute(delete(Book).where(Book.id.in_(book_ids)))


def add_book(session: Session, book: Book) -> None:
    session.add(book)
    session.flush()


def find_books(
    session: Session, query: BookListQuery
) -> tuple[list[Book], int]:
    conditions = _conditions(query)
    total = session.scalar(
        select(func.count()).select_from(Book).where(*conditions)
    )
    books = session.scalars(
        select(Book)
        .where(*conditions)
        .order_by(*_order_by(query))
        .limit(query.limit)
        .offset(query.offset)
    ).all()
    return list(books), int(total or 0)


def _find_or_create_contributors(
    session: Session, names: list[str]
) -> list[Contributor]:
    contributors: list[Contributor] = []
    for name in names:
        contributor = session.scalars(
            select(Contributor)
            .where(func.acbooks_casefold(Contributor.name) == name.casefold())
            .order_by(Contributor.id)
            .limit(1)
        ).first()
        if contributor is None:
            contributor = Contributor(id=str(uuid4()), name=name)
            session.add(contributor)
            session.flush()
        contributors.append(contributor)
    return contributors


def _find_or_create_tags(session: Session, names: list[str]) -> list[Tag]:
    tags: list[Tag] = []
    for name in names:
        tag = session.scalars(
            select(Tag)
            .where(func.acbooks_casefold(Tag.name) == name.casefold())
            .order_by(Tag.id)
            .limit(1)
        ).first()
        if tag is None:
            tag = Tag(id=str(uuid4()), name=name)
            session.add(tag)
            session.flush()
        tags.append(tag)
    return tags


def _find_or_create_shelves(session: Session, names: list[str]) -> list[Shelf]:
    shelves: list[Shelf] = []
    for name in names:
        shelf = session.scalars(
            select(Shelf)
            .where(func.acbooks_casefold(Shelf.name) == name.casefold())
            .order_by(Shelf.id)
            .limit(1)
        ).first()
        if shelf is None:
            shelf = Shelf(id=str(uuid4()), name=name)
            session.add(shelf)
            session.flush()
        shelves.append(shelf)
    return shelves


def replace_contributors(
    session: Session, book_id: str, role: str, names: list[str]
) -> None:
    session.execute(
        delete(BookContributor).where(
            BookContributor.book_id == book_id,
            BookContributor.role == role,
        )
    )
    if not names:
        return
    session.add_all(
        BookContributor(
            book_id=book_id,
            contributor_id=contributor.id,
            role=role,
        )
        for contributor in _find_or_create_contributors(session, names)
    )


def replace_identifiers(
    session: Session, book_id: str, isbn: str | None
) -> None:
    session.execute(
        delete(BookIdentifier).where(
            BookIdentifier.book_id == book_id,
            BookIdentifier.scheme == "isbn",
        )
    )
    if isbn is not None:
        session.add(
            BookIdentifier(
                id=str(uuid4()),
                book_id=book_id,
                scheme="isbn",
                value=isbn,
            )
        )


def replace_tags(session: Session, book_id: str, names: list[str]) -> None:
    session.execute(delete(BookTag).where(BookTag.book_id == book_id))
    if not names:
        return
    session.add_all(
        BookTag(book_id=book_id, tag_id=tag.id)
        for tag in _find_or_create_tags(session, names)
    )


def replace_shelves(session: Session, book_id: str, names: list[str]) -> None:
    session.execute(delete(ShelfBook).where(ShelfBook.book_id == book_id))
    if not names:
        return
    session.add_all(
        ShelfBook(book_id=book_id, shelf_id=shelf.id)
        for shelf in _find_or_create_shelves(session, names)
    )


def load_records(
    session: Session, books: Sequence[Book]
) -> list[BookRecord]:
    if not books:
        return []

    ids = [book.id for book in books]
    authors: dict[str, list[str]] = defaultdict(list)
    translators: dict[str, list[str]] = defaultdict(list)
    identifiers: dict[str, list[tuple[str, str]]] = defaultdict(list)
    tags: dict[str, list[str]] = defaultdict(list)
    shelves: dict[str, list[str]] = defaultdict(list)
    copies: dict[str, list[CopyRecord]] = defaultdict(list)
    files: dict[str, list[FileRecord]] = defaultdict(list)

    for book_id, role, name in session.execute(
        select(BookContributor.book_id, BookContributor.role, Contributor.name)
        .join(Contributor, Contributor.id == BookContributor.contributor_id)
        .where(BookContributor.book_id.in_(ids))
        .order_by(Contributor.name.collate("NOCASE"), Contributor.id)
    ):
        if role == "author":
            authors[book_id].append(name)
        elif role == "translator":
            translators[book_id].append(name)

    for book_id, scheme, value in session.execute(
        select(BookIdentifier.book_id, BookIdentifier.scheme, BookIdentifier.value)
        .where(BookIdentifier.book_id.in_(ids))
        .order_by(BookIdentifier.scheme, BookIdentifier.value, BookIdentifier.id)
    ):
        identifiers[book_id].append((scheme, value))

    for book_id, name in session.execute(
        select(BookTag.book_id, Tag.name)
        .join(Tag, Tag.id == BookTag.tag_id)
        .where(BookTag.book_id.in_(ids))
        .order_by(Tag.name.collate("NOCASE"), Tag.id)
    ):
        tags[book_id].append(name)

    for book_id, name in session.execute(
        select(ShelfBook.book_id, Shelf.name)
        .join(Shelf, Shelf.id == ShelfBook.shelf_id)
        .where(ShelfBook.book_id.in_(ids))
        .order_by(Shelf.name.collate("NOCASE"), Shelf.id)
    ):
        shelves[book_id].append(name)

    for copy_id, book_id, location_id, location_name, acquired_at, note in (
        session.execute(
            select(
                BookCopy.id,
                BookCopy.book_id,
                BookCopy.location_id,
                Location.name,
                BookCopy.acquired_at,
                BookCopy.note,
            )
            .outerjoin(Location, Location.id == BookCopy.location_id)
            .where(BookCopy.book_id.in_(ids))
            .order_by(BookCopy.id)
        )
    ):
        copies[book_id].append(
            CopyRecord(
                id=copy_id,
                location_id=location_id,
                location_name=location_name,
                acquired_at=acquired_at,
                note=note,
            )
        )

    for file_id, book_id, file_format, file_status, size_bytes in (
        session.execute(
            select(
                BookFile.id,
                BookFile.book_id,
                BookFile.format,
                StorageObject.file_status,
                StorageObject.size_bytes,
            )
            .join(StorageObject, StorageObject.id == BookFile.storage_object_id)
            .where(BookFile.book_id.in_(ids))
            .order_by(BookFile.format, BookFile.id)
        )
    ):
        files[book_id].append(
            FileRecord(
                id=file_id,
                format=file_format,
                file_status=file_status,
                size_bytes=size_bytes,
            )
        )

    return [
        BookRecord(
            book=book,
            authors=authors[book.id],
            translators=translators[book.id],
            identifiers=identifiers[book.id],
            tags=tags[book.id],
            shelves=shelves[book.id],
            copies=copies[book.id],
            files=files[book.id],
        )
        for book in books
    ]
