from sqlalchemy import ColumnElement, false, func, select
from sqlalchemy.orm import Session

from app.core.errors import BusinessError, BusinessErrorCode
from app.db.models.books import Book, BookIdentifier
from app.db.models.storage import BookFile, StorageObject, StorageRoot
from app.modules.imports.schemas import ImportCandidate, MatchReason


def storage_exists(session: Session, root_id: str, relative_path: str) -> bool:
    return (
        session.scalar(
            select(StorageObject.id).where(
                StorageObject.root_id == root_id,
                StorageObject.relative_path == relative_path,
            )
        )
        is not None
    )


def _unlinked() -> ColumnElement[bool]:
    return ~select(BookFile.id).where(BookFile.storage_object_id == StorageObject.id).exists()


def pending(
    session: Session,
    root_id: str,
    limit: int,
    offset: int,
) -> tuple[list[StorageObject], int]:
    filters = (
        StorageObject.root_id == root_id,
        StorageObject.file_status == "present",
        _unlinked(),
    )
    total = session.scalar(select(func.count()).select_from(StorageObject).where(*filters)) or 0
    items = session.scalars(
        select(StorageObject)
        .where(*filters)
        .order_by(StorageObject.relative_path, StorageObject.id)
        .offset(offset)
        .limit(limit)
    )
    return list(items), total


def get_pending(session: Session, object_id: str) -> tuple[StorageObject, StorageRoot]:
    row = session.execute(
        select(StorageObject, StorageRoot)
        .join(StorageRoot, StorageRoot.id == StorageObject.root_id)
        .where(StorageObject.id == object_id, StorageObject.file_status == "present", _unlinked())
    ).first()
    if row is None:
        raise BusinessError(BusinessErrorCode.FILE_NOT_FOUND)
    return row[0], row[1]


def candidates(
    session: Session,
    title: str,
    isbn: str | None,
    sha256: str | None,
) -> list[ImportCandidate]:
    same_title = Book.title.collate("NOCASE") == title
    same_isbn = (
        select(BookIdentifier.id)
        .where(
            BookIdentifier.book_id == Book.id,
            BookIdentifier.scheme == "isbn",
            BookIdentifier.value == isbn,
        )
        .exists()
    )
    same_hash = (
        select(BookFile.id)
        .join(StorageObject, StorageObject.id == BookFile.storage_object_id)
        .where(
            BookFile.book_id == Book.id,
            false() if sha256 is None else StorageObject.sha256 == sha256,
        )
        .exists()
    )
    rows = session.execute(
        select(Book.id, Book.title, same_title, same_isbn, same_hash)
        .where(same_title | same_isbn | same_hash)
        .order_by(same_hash.desc(), same_isbn.desc(), Book.title, Book.id)
        .limit(20)
    )
    result = []
    for book_id, name, title_match, isbn_match, hash_match in rows:
        reasons: list[MatchReason] = []
        if title_match:
            reasons.append("title")
        if isbn_match:
            reasons.append("isbn")
        if hash_match:
            reasons.append("sha256")
        result.append(ImportCandidate(book_id=book_id, title=name, reasons=reasons))
    return result
