from typing import Annotated, Literal, TypeAlias
from uuid import UUID

from pydantic import Field

from app.core.errors import BusinessErrorCode
from app.modules.books.organization_schemas import FileReassociation
from app.modules.books.schemas import BookCreate, BookDetail, BookFile, BookUpdate, LaxApiModel
from app.modules.reader.schemas import (
    AnnotationRead,
    AnnotationUpdate,
    BookmarkRead,
    BookmarkUpdate,
    Locator,
    Percent,
    PositionRead,
    Revision,
    SessionFields,
    SessionRead,
    SessionUpdate,
    Source,
)

EntityType: TypeAlias = Literal[
    "book", "book_file", "reading_position", "reading_session", "bookmark", "annotation"
]


class SyncBookFile(BookFile):
    book_id: UUID
    sha256: str | None


SyncData: TypeAlias = (
    BookDetail | SyncBookFile | PositionRead | SessionRead | BookmarkRead | AnnotationRead
)


class SyncRecord(LaxApiModel):
    entity_type: EntityType
    entity_id: UUID
    revision: int
    operation: Literal["upsert", "delete"]
    data: SyncData | None


class SyncPage(LaxApiModel):
    items: list[SyncRecord]
    next_cursor: int
    through: int
    has_more: bool


class MutationBase(LaxApiModel):
    mutation_id: UUID
    entity_id: UUID
    base_revision: Revision


class CreateBook(MutationBase):
    action: Literal["book.create"]
    data: BookCreate


class UpdateBook(MutationBase):
    action: Literal["book.update"]
    data: BookUpdate


class MoveFile(MutationBase):
    action: Literal["book_file.move"]
    data: FileReassociation


class SavePositionData(LaxApiModel):
    locator: Locator
    percent: Percent | None = None


class SavePosition(MutationBase):
    action: Literal["reading_position.save"]
    data: SavePositionData


class CreateSessionData(SessionFields):
    book_file_id: UUID | None = None
    source: Source = "manual"


class ReaderMutation(MutationBase):
    book_id: UUID


class CreateSession(ReaderMutation):
    action: Literal["reading_session.create"]
    data: CreateSessionData


class UpdateSession(ReaderMutation):
    action: Literal["reading_session.update"]
    data: SessionUpdate


class WriteBookmark(ReaderMutation):
    action: Literal["bookmark.create", "bookmark.update"]
    data: BookmarkUpdate


class WriteAnnotation(ReaderMutation):
    action: Literal["annotation.create", "annotation.update"]
    data: AnnotationUpdate


class DeleteCatalogItem(MutationBase):
    action: Literal["book.delete", "book_file.delete", "reading_position.delete"]


class DeleteReadingRecord(ReaderMutation):
    action: Literal["reading_session.delete", "bookmark.delete", "annotation.delete"]


Mutation: TypeAlias = Annotated[
    CreateBook
    | UpdateBook
    | MoveFile
    | SavePosition
    | CreateSession
    | UpdateSession
    | WriteBookmark
    | WriteAnnotation
    | DeleteCatalogItem
    | DeleteReadingRecord,
    Field(discriminator="action"),
]


class SyncBatch(LaxApiModel):
    device_id: UUID
    mutations: Annotated[list[Mutation], Field(min_length=1, max_length=50)]


class MutationReceipt(LaxApiModel):
    mutation_id: UUID
    status: Literal["accepted", "conflict", "rejected", "duplicate"]
    code: BusinessErrorCode | Literal["MUTATION_ID_REUSED", "VALIDATION_ERROR"] | None = None
    current: SyncRecord | None = None


class SyncBatchResult(LaxApiModel):
    receipts: list[MutationReceipt]
