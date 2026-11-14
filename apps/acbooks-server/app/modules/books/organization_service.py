from hashlib import sha256
from time import time_ns
from typing import cast
from uuid import UUID, uuid4

from sqlalchemy import Engine, Select, Table, delete, func, select, update
from sqlalchemy.orm import Session

from app.core.errors import BusinessError, BusinessErrorCode
from app.db.models.books import Book, BookContributor, BookTag, Contributor, Shelf, ShelfBook, Tag
from app.db.models.storage import BookFile
from app.db.session import create_session_factory
from app.modules.books import organization_repository as organization
from app.modules.books import repository
from app.modules.books.organization_schemas import (
    BookMergePair,
    BookMergePreview,
    BookMergeRequest,
    DuplicatePage,
    DuplicatePair,
    FileReassociation,
    MatchReason,
    MergeBook,
    MergeConflict,
    MergeField,
    OrganizationBook,
    OrganizationBookPage,
    OrganizationEntry,
    OrganizationKind,
    OrganizationPage,
)
from app.modules.books.repository import BookRecord
from app.modules.metadata.locks import (
    AUTHOR,
    COVER,
    DESCRIPTION,
    ISBN,
    PLACEHOLDER_TITLE,
    SERIES,
    TRANSLATOR,
)

GROUPS = {
    "tag": (cast(Table, Tag.__table__), cast(Table, BookTag.__table__), "tag_id"),
    "shelf": (cast(Table, Shelf.__table__), cast(Table, ShelfBook.__table__), "shelf_id"),
    "author": (
        cast(Table, Contributor.__table__),
        cast(Table, BookContributor.__table__),
        "contributor_id",
    ),
    "translator": (
        cast(Table, Contributor.__table__),
        cast(Table, BookContributor.__table__),
        "contributor_id",
    ),
}


ROLE_LOCKS = {"author": AUTHOR, "translator": TRANSLATOR}
MERGE_LOCKS: dict[MergeField, int] = {
    "description": DESCRIPTION,
    "series": SERIES,
    "cover": COVER,
    "authors": AUTHOR,
    "translators": TRANSLATOR,
    "isbn": ISBN,
}


def _scope(kind: OrganizationKind) -> dict[str, str]:
    return {"role": kind} if kind in ("author", "translator") else {}


def _linked_books(kind: OrganizationKind, entry_id: str) -> Select[str]:
    _, links, key = GROUPS[kind]
    return select(links.c.book_id).where(
        links.c[key] == entry_id,
        *(links.c[k] == v for k, v in _scope(kind).items()),
    )


def _entry(session: Session, kind: OrganizationKind, entry_id: str) -> tuple[Table, str]:
    table, _, _ = GROUPS[kind]
    name = session.scalar(select(table.c.name).where(table.c.id == entry_id))
    if name is None or (
        _scope(kind) and session.scalar(_linked_books(kind, entry_id).limit(1)) is None
    ):
        raise BusinessError(BusinessErrorCode.ORGANIZATION_ENTRY_NOT_FOUND)
    return table, name


def _touch(session: Session, book_ids: Select[str], locks: int = 0) -> None:
    session.execute(
        update(Book)
        .where(Book.id.in_(book_ids))
        .values(
            updated_at=func.max(Book.updated_at + 1, time_ns() // 1_000_000),
            metadata_locks=Book.metadata_locks.bitwise_or(locks),
        )
    )


def _book(session: Session, book_id: UUID) -> Book:
    book = session.get(Book, str(book_id))
    if book is None:
        raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)
    return book


def _label(record: BookRecord) -> MergeBook:
    return MergeBook(
        id=UUID(record.book.id),
        title=record.book.title,
        authors=record.authors,
        translators=record.translators,
    )


def _metadata(record: BookRecord) -> dict[MergeField, str | list[str] | None]:
    book = record.book
    return {
        "title": book.title,
        "description": book.description,
        "series": book.series,
        "reading_status": book.reading_status,
        "cover": book.cover_path,
        "authors": record.authors,
        "translators": record.translators,
        "isbn": [value for scheme, value in record.identifiers if scheme == "isbn"],
    }


def _preview(session: Session, source: Book, target: Book) -> BookMergePreview:
    left, right = repository.load_records(session, [source, target])
    before, after = _metadata(left), _metadata(right)
    reading = organization.reading_counts(session, source.id)
    conflicts = []
    for field, value in before.items():
        other = after[field]
        if value != other:
            if field == "cover":
                value, other = ("present" if value else None), ("present" if other else None)
            conflicts.append(MergeConflict(field=field, source=value, target=other))
    preview = BookMergePreview(
        source=_label(left),
        target=_label(right),
        conflicts=conflicts,
        file_count=len(left.files),
        copy_count=len(left.copies),
        reading_count=sum(reading),
        revision="",
    )
    snapshot = (
        preview.model_dump_json(),
        before,
        after,
        source.updated_at,
        target.updated_at,
        source.cover_updated_at,
        target.cover_updated_at,
        source.metadata_locks,
        target.metadata_locks,
        left.files,
        left.copies,
        left.tags,
        left.shelves,
        left.identifiers,
        right.identifiers,
        reading,
    )
    preview.revision = sha256(repr(snapshot).encode()).hexdigest()
    return preview


class BookOrganizationService:
    def __init__(self, engine: Engine) -> None:
        self.read_sessions = create_session_factory(engine)
        self.write_sessions = create_session_factory(
            engine.execution_options(sqlite_begin_immediate=True)
        )

    def list_entries(self, kind: OrganizationKind, limit: int, offset: int) -> OrganizationPage:
        table, links, key = GROUPS[kind]
        counts = (
            select(links.c[key].label("id"), func.count().label("book_count"))
            .where(*(links.c[k] == v for k, v in _scope(kind).items()))
            .group_by(links.c[key])
            .subquery()
        )
        query = select(table.c.id, table.c.name, func.coalesce(counts.c.book_count, 0)).join(
            counts, table.c.id == counts.c.id, isouter=not bool(_scope(kind))
        )
        with self.read_sessions() as session:
            total = session.scalar(select(func.count()).select_from(query.subquery())) or 0
            rows = session.execute(
                query.order_by(func.acbooks_casefold(table.c.name), table.c.id)
                .limit(limit)
                .offset(offset)
            )
            return OrganizationPage(
                total=total,
                items=[
                    OrganizationEntry(id=UUID(entry_id), name=name, book_count=count)
                    for entry_id, name, count in rows
                ],
            )

    def list_books(
        self, kind: OrganizationKind, entry_id: UUID, limit: int, offset: int
    ) -> OrganizationBookPage:
        with self.read_sessions() as session:
            _entry(session, kind, str(entry_id))
            query = select(Book.id, Book.title).where(
                Book.id.in_(_linked_books(kind, str(entry_id)))
            )
            total = session.scalar(select(func.count()).select_from(query.subquery())) or 0
            rows = session.execute(
                query.order_by(Book.title.collate("NOCASE"), Book.id).limit(limit).offset(offset)
            )
            return OrganizationBookPage(
                total=total,
                items=[OrganizationBook(id=UUID(book_id), title=title) for book_id, title in rows],
            )

    def rename(self, kind: OrganizationKind, entry_id: UUID, name: str) -> None:
        source = str(entry_id)
        with self.write_sessions.begin() as session:
            table, old_name = _entry(session, kind, source)
            if name == old_name:
                return
            if (
                session.scalar(
                    select(table.c.id)
                    .where(
                        table.c.id != source, func.acbooks_casefold(table.c.name) == name.casefold()
                    )
                    .limit(1)
                )
                is not None
            ):
                raise BusinessError(BusinessErrorCode.ORGANIZATION_CONFLICT)
            _, links, key = GROUPS[kind]
            if (
                kind in ("author", "translator")
                and session.scalar(
                    select(links.c.book_id)
                    .where(links.c[key] == source, links.c.role != kind)
                    .limit(1)
                )
                is not None
            ):
                # A shared contributor must have separate names when only one role is renamed.
                replacement = str(uuid4())
                session.execute(table.insert().values(id=replacement, name=name))
                _touch(session, _linked_books(kind, source), ROLE_LOCKS.get(kind, 0))
                session.execute(
                    update(links)
                    .where(links.c[key] == source, links.c.role == kind)
                    .values({key: replacement})
                )
            else:
                _touch(session, _linked_books(kind, source), ROLE_LOCKS.get(kind, 0))
                session.execute(update(table).where(table.c.id == source).values(name=name))

    def remove(self, kind: OrganizationKind, entry_id: UUID, target_id: UUID | None = None) -> None:
        source = str(entry_id)
        with self.write_sessions.begin() as session:
            table, _ = _entry(session, kind, source)
            _, links, key = GROUPS[kind]
            if target_id is not None:
                if target_id == entry_id:
                    raise BusinessError(BusinessErrorCode.ORGANIZATION_CONFLICT)
                _entry(session, kind, str(target_id))
                organization.copy_links(session, links, key, source, str(target_id), **_scope(kind))
            _touch(
                session,
                _linked_books(kind, source),
                ROLE_LOCKS.get(kind, 0),
            )
            if _scope(kind):
                session.execute(delete(links).where(links.c[key] == source, links.c.role == kind))
                if (
                    session.scalar(select(links.c.book_id).where(links.c[key] == source).limit(1))
                    is not None
                ):
                    return
            session.execute(delete(table).where(table.c.id == source))

    def reassociate(self, file_id: UUID, payload: FileReassociation) -> None:
        with self.write_sessions.begin() as session:
            file = session.get(BookFile, str(file_id))
            if file is None:
                raise BusinessError(BusinessErrorCode.FILE_NOT_FOUND)
            target = _book(session, payload.target_book_id)
            if file.book_id == target.id:
                return
            if file.book_id != str(payload.source_book_id):
                raise BusinessError(BusinessErrorCode.ORGANIZATION_STALE)
            _touch(session, select(Book.id).where(Book.id.in_((file.book_id, target.id))))
            organization.move_file(session, file, target.id)

    def preview(self, payload: BookMergePair) -> BookMergePreview:
        with self.read_sessions() as session:
            return _preview(
                session,
                _book(session, payload.source_book_id),
                _book(session, payload.target_book_id),
            )

    def merge(self, payload: BookMergeRequest) -> None:
        with self.write_sessions.begin() as session:
            source = _book(session, payload.source_book_id)
            target = _book(session, payload.target_book_id)
            preview = _preview(session, source, target)
            if preview.revision != payload.revision:
                raise BusinessError(BusinessErrorCode.ORGANIZATION_STALE)
            for field in ("title", "description", "series", "reading_status"):
                if field in payload.use_source:
                    setattr(target, field, getattr(source, field))
            if "cover" in payload.use_source:
                target.cover_path = source.cover_path
                target.cover_updated_at = max(
                    (target.cover_updated_at or 0) + 1, time_ns() // 1_000_000
                )
            if "title" in payload.use_source:
                target.metadata_locks &= ~PLACEHOLDER_TITLE
            for conflict in preview.conflicts:
                target.metadata_locks |= MERGE_LOCKS.get(conflict.field, 0)
            target.metadata_locks |= source.metadata_locks & ~PLACEHOLDER_TITLE
            target.updated_at = max(target.updated_at + 1, time_ns() // 1_000_000)
            roles = {
                role
                for field, role in (("authors", "author"), ("translators", "translator"))
                if field in payload.use_source
            }
            organization.merge_relations(
                session, source, target, roles, "isbn" in payload.use_source
            )

    def duplicates(self, limit: int, offset: int) -> DuplicatePage:
        with self.read_sessions() as session:
            rows, total = organization.duplicate_pairs(session, limit, offset)
            ids = list({book_id for left, right, _ in rows for book_id in (left, right)})
            records = repository.load_records(session, repository.get_books(session, ids))
            books = {record.book.id: _label(record) for record in records}
            return DuplicatePage(
                total=total,
                items=[
                    DuplicatePair(
                        source=books[left],
                        target=books[right],
                        reasons=cast(list[MatchReason], sorted(reasons.split(","))),
                    )
                    for left, right, reasons in rows
                ],
            )
