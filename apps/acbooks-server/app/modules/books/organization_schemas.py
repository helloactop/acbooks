from typing import Literal, Self
from uuid import UUID

from pydantic import Field, field_validator, model_validator

from app.modules.books.normalization import clean_text
from app.modules.books.schemas import LaxApiModel

OrganizationKind = Literal["tag", "shelf", "author", "translator"]
MergeField = Literal[
    "title", "description", "series", "reading_status", "cover", "authors", "translators", "isbn"
]
MatchReason = Literal["isbn", "external_id", "title", "author", "sha256"]


class OrganizationEntry(LaxApiModel):
    id: UUID
    name: str
    book_count: int


class OrganizationPage(LaxApiModel):
    items: list[OrganizationEntry]
    total: int


class OrganizationName(LaxApiModel):
    name: str

    @field_validator("name")
    @classmethod
    def validate_name(cls, value: str) -> str:
        return clean_text(value, "name", max_length=200)


class OrganizationTarget(LaxApiModel):
    target_id: UUID


class FileReassociation(LaxApiModel):
    source_book_id: UUID
    target_book_id: UUID


class BookMergePair(FileReassociation):
    @model_validator(mode="after")
    def distinct_books(self) -> Self:
        if self.source_book_id == self.target_book_id:
            raise ValueError("Choose two different books.")
        return self


class BookMergeRequest(BookMergePair):
    revision: str = Field(pattern=r"^[0-9a-f]{64}$")
    use_source: set[MergeField] = Field(default_factory=set)


class MergeBook(LaxApiModel):
    id: UUID
    title: str
    authors: list[str]
    translators: list[str]


class MergeConflict(LaxApiModel):
    field: MergeField
    source: str | list[str] | None
    target: str | list[str] | None


class BookMergePreview(LaxApiModel):
    source: MergeBook
    target: MergeBook
    conflicts: list[MergeConflict]
    file_count: int
    copy_count: int
    reading_count: int
    revision: str


class DuplicatePair(LaxApiModel):
    source: MergeBook
    target: MergeBook
    reasons: list[MatchReason]


class DuplicatePage(LaxApiModel):
    items: list[DuplicatePair]
    total: int


class OrganizationBook(LaxApiModel):
    id: UUID
    title: str


class OrganizationBookPage(LaxApiModel):
    items: list[OrganizationBook]
    total: int
