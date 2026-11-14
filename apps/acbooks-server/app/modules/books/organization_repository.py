from typing import cast

from sqlalchemy import Table, delete, func, literal, select, union_all, update
from sqlalchemy.dialects.sqlite import insert
from sqlalchemy.orm import Session

from app.db.models.books import (
    Book,
    BookContributor,
    BookIdentifier,
    BookTag,
    ShelfBook,
)
from app.db.models.copies import BookCopy
from app.db.models.reading import Annotation, Bookmark, ReadingPosition, ReadingSession
from app.db.models.storage import BookFile, StorageObject

READING = (
    cast(Table, ReadingSession.__table__),
    cast(Table, Bookmark.__table__),
    cast(Table, Annotation.__table__),
)


def copy_links(
    session: Session, table: Table, column: str, source: str, target: str, **scope: str
) -> None:
    columns = [item.name for item in table.primary_key]
    rows = select(*(literal(target) if key == column else table.c[key] for key in columns))
    rows = rows.where(table.c[column] == source, *(table.c[k] == v for k, v in scope.items()))
    session.execute(insert(table).from_select(columns, rows).on_conflict_do_nothing())


def move_file(session: Session, file: BookFile, target_id: str) -> None:
    if file.book_id == target_id:
        return
    references: list[tuple[Table, list[str]]] = []
    for table in READING:
        linked = table.c.book_file_id == file.id
        ids = list(session.scalars(select(table.c.id).where(linked)))
        if ids:
            references.append((table, ids))
            session.execute(update(table).where(linked).values(book_file_id=None))
    file.book_id = target_id
    session.flush()
    for table, ids in references:
        # Bound batches also support SQLite builds with small parameter limits.
        for offset in range(0, len(ids), 500):
            session.execute(
                update(table)
                .where(table.c.id.in_(ids[offset : offset + 500]))
                .values(book_file_id=file.id, book_id=target_id)
            )


def merge_relations(
    session: Session, source: Book, target: Book, use_source_roles: set[str], source_isbn: bool
) -> None:
    for file in session.scalars(select(BookFile).where(BookFile.book_id == source.id)):
        move_file(session, file, target.id)
    for table in (*READING, cast(Table, BookCopy.__table__)):
        session.execute(update(table).where(table.c.book_id == source.id).values(book_id=target.id))
    for table in (cast(Table, BookTag.__table__), cast(Table, ShelfBook.__table__)):
        copy_links(session, table, "book_id", source.id, target.id)
    contributors = cast(Table, BookContributor.__table__)
    for role in use_source_roles:
        session.execute(
            delete(contributors).where(
                contributors.c.book_id == target.id, contributors.c.role == role
            )
        )
    incoming = select(literal(target.id), contributors.c.contributor_id, contributors.c.role).where(
        contributors.c.book_id == source.id,
        (contributors.c.role.in_(use_source_roles))
        | (~contributors.c.role.in_(("author", "translator"))),
    )
    session.execute(
        insert(contributors)
        .from_select(["book_id", "contributor_id", "role"], incoming)
        .on_conflict_do_nothing()
    )
    identifiers = cast(Table, BookIdentifier.__table__)
    if source_isbn:
        session.execute(
            delete(identifiers).where(
                identifiers.c.book_id == target.id, identifiers.c.scheme == "isbn"
            )
        )
    existing = identifiers.alias()
    same_identifier = (
        select(1)
        .where(
            existing.c.book_id == target.id,
            existing.c.scheme == identifiers.c.scheme,
            existing.c.value == identifiers.c.value,
        )
        .exists()
    )
    session.execute(
        update(identifiers)
        .where(
            identifiers.c.book_id == source.id,
            literal(source_isbn) | (identifiers.c.scheme != "isbn"),
            ~same_identifier,
        )
        .values(book_id=target.id)
    )
    session.delete(source)


def reading_counts(session: Session, book_id: str) -> list[int]:
    counts = [
        session.scalar(select(func.count()).select_from(table).where(table.c.book_id == book_id))
        or 0
        for table in READING
    ]
    file_ids = select(BookFile.id).where(BookFile.book_id == book_id)
    counts.append(
        session.scalar(
            select(func.count())
            .select_from(ReadingPosition)
            .where(ReadingPosition.book_file_id.in_(file_ids))
        )
        or 0
    )
    return counts


def duplicate_pairs(
    session: Session, limit: int, offset: int
) -> tuple[list[tuple[str, str, str]], int]:
    identifiers = select(
        BookIdentifier.book_id,
        func.acbooks_casefold(BookIdentifier.scheme).label("namespace"),
        BookIdentifier.value.label("key"),
        func.iif(
            func.acbooks_casefold(BookIdentifier.scheme) == "isbn", "isbn", "external_id"
        ).label("reason"),
    ).where(BookIdentifier.value != "")
    # Namespace and value stay separate so IDs from different providers never match.
    keys = union_all(
        identifiers,
        select(
            Book.id, literal(""), func.acbooks_casefold(func.trim(Book.title)), literal("title")
        ),
        select(BookFile.book_id, literal(""), StorageObject.sha256, literal("sha256"))
        .join(StorageObject, StorageObject.id == BookFile.storage_object_id)
        .where(StorageObject.sha256.is_not(None), StorageObject.sha256 != ""),
    ).cte("duplicate_keys")
    other = keys.alias()
    matches = (
        select(
            keys.c.book_id.label("source_id"),
            other.c.book_id.label("target_id"),
            func.group_concat(func.distinct(keys.c.reason)).label("reasons"),
            func.max(keys.c.reason.in_(("isbn", "external_id", "sha256"))).label("strong"),
        )
        .join(
            other,
            (keys.c.book_id < other.c.book_id)
            & (keys.c.reason == other.c.reason)
            & (keys.c.namespace == other.c.namespace)
            & (keys.c.key == other.c.key),
        )
        .group_by(keys.c.book_id, other.c.book_id)
        .cte("duplicate_pairs")
    )
    total = session.scalar(select(func.count()).select_from(matches)) or 0
    page = (
        select(matches)
        .order_by(matches.c.strong.desc(), matches.c.source_id, matches.c.target_id)
        .offset(offset)
        .limit(limit)
        .subquery()
    )
    left_author = cast(Table, BookContributor.__table__).alias()
    right_author = cast(Table, BookContributor.__table__).alias()
    shared_author = (
        select(1)
        .select_from(
            left_author.join(
                right_author, left_author.c.contributor_id == right_author.c.contributor_id
            )
        )
        .where(
            left_author.c.book_id == page.c.source_id,
            right_author.c.book_id == page.c.target_id,
            left_author.c.role == "author",
            right_author.c.role == "author",
        )
        .exists()
    )
    rows = session.execute(
        select(page.c.source_id, page.c.target_id, page.c.reasons, shared_author).order_by(
            page.c.strong.desc(), page.c.source_id, page.c.target_id
        )
    )
    return [
        (left, right, reasons + (",author" if author else ""))
        for left, right, reasons, author in rows
    ], total
