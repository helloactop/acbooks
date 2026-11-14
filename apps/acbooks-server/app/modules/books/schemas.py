from typing import Annotated, Literal, Self
from uuid import UUID

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    ValidationInfo,
    field_validator,
    model_validator,
)
from pydantic.alias_generators import to_camel

from app.modules.books.filtering import (
    BookFilterFieldType,
    BookFilterOptionSource,
)
from app.modules.books.normalization import (
    clean_text,
    normalize_isbn,
    normalize_name_list,
)
from app.modules.books.types import BookOrder, BookSort, MediaType, ReadingStatus

__all__ = [
    "BatchResult",
    "BookBatchDelete",
    "BookBatchUpdate",
    "BookCopy",
    "BookCopyCreate",
    "BookCreate",
    "BookDetail",
    "BookFile",
    "BookFilterField",
    "BookFilterOption",
    "BookFilterOptionPage",
    "BookFilterSchema",
    "BookIdentifier",
    "BookOrder",
    "BookPage",
    "BookSort",
    "BookSummary",
    "BookUpdate",
    "MediaType",
    "ReadingStatus",
]


class ApiModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="forbid",
        strict=True,
    )


class LaxApiModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="forbid",
        strict=False,
    )


def _unique_ids(value: list[UUID]) -> list[UUID]:
    if len(set(value)) != len(value):
        raise ValueError("Book ids must be unique.")
    return value


class BookCopyCreate(LaxApiModel):
    location_id: UUID | None = None
    acquired_at: int | None = None
    note: Annotated[str | None, Field(max_length=2000)] = None

    @field_validator("note")
    @classmethod
    def validate_note(cls, value: str | None) -> str | None:
        if value is None:
            return None
        return clean_text(value, "note", allow_newlines=True, max_length=2000)


class BookCreate(ApiModel):
    title: Annotated[str, Field(min_length=1, max_length=500)]
    series: Annotated[str | None, Field(max_length=200)] = None
    description: Annotated[str | None, Field(max_length=20000)] = None
    authors: Annotated[list[str], Field(max_length=20)] = Field(default_factory=list)
    translators: Annotated[list[str], Field(max_length=20)] = Field(default_factory=list)
    isbn: Annotated[str | None, Field(max_length=32)] = None
    reading_status: ReadingStatus | None = None
    tags: Annotated[list[str], Field(max_length=20)] = Field(default_factory=list)
    shelves: Annotated[list[str], Field(max_length=20)] = Field(default_factory=list)
    copies: Annotated[list[BookCopyCreate], Field(max_length=20)] = Field(
        default_factory=list
    )

    @field_validator("title")
    @classmethod
    def validate_title(cls, value: str) -> str:
        return clean_text(value, "title", max_length=500)

    @field_validator("series")
    @classmethod
    def validate_series(cls, value: str | None) -> str | None:
        return clean_text(value, "series", max_length=200) if value is not None else None

    @field_validator("description")
    @classmethod
    def validate_description(cls, value: str | None) -> str | None:
        if value is None:
            return None
        return clean_text(
            value, "description", allow_newlines=True, max_length=20000
        )

    @field_validator("authors", "translators", "tags", "shelves")
    @classmethod
    def validate_names(
        cls, value: list[str], info: ValidationInfo
    ) -> list[str]:
        return normalize_name_list(value, info.field_name or "names")

    @field_validator("isbn")
    @classmethod
    def validate_isbn(cls, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = normalize_isbn(value)
        if normalized is None:
            raise ValueError("Invalid ISBN.")
        return normalized


class BookUpdate(ApiModel):
    title: Annotated[str | None, Field(min_length=1, max_length=500)] = None
    series: Annotated[str | None, Field(max_length=200)] = None
    description: Annotated[str | None, Field(max_length=20000)] = None
    authors: Annotated[list[str] | None, Field(max_length=20)] = None
    translators: Annotated[list[str] | None, Field(max_length=20)] = None
    isbn: Annotated[str | None, Field(max_length=32)] = None
    reading_status: ReadingStatus | None = None
    tags: Annotated[list[str] | None, Field(max_length=20)] = None
    shelves: Annotated[list[str] | None, Field(max_length=20)] = None

    @field_validator("title")
    @classmethod
    def validate_title(cls, value: str | None) -> str | None:
        if value is None:
            return None
        return clean_text(value, "title", max_length=500)

    @field_validator("series")
    @classmethod
    def validate_series(cls, value: str | None) -> str | None:
        return clean_text(value, "series", max_length=200) if value is not None else None

    @field_validator("description")
    @classmethod
    def validate_description(cls, value: str | None) -> str | None:
        if value is None:
            return None
        return clean_text(
            value, "description", allow_newlines=True, max_length=20000
        )

    @field_validator("authors", "translators", "tags", "shelves")
    @classmethod
    def validate_names(
        cls, value: list[str] | None, info: ValidationInfo
    ) -> list[str] | None:
        if value is None:
            return None
        return normalize_name_list(value, info.field_name or "names")

    @field_validator("isbn")
    @classmethod
    def validate_isbn(cls, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = normalize_isbn(value)
        if normalized is None:
            raise ValueError("Invalid ISBN.")
        return normalized

    @model_validator(mode="after")
    def validate_patch(self) -> Self:
        if not self.model_fields_set:
            raise ValueError("At least one field must be provided.")

        for field in (
            "title",
            "authors",
            "translators",
            "reading_status",
            "tags",
            "shelves",
        ):
            if field in self.model_fields_set and getattr(self, field) is None:
                raise ValueError(f"{field} cannot be null.")
        return self


class BookBatchUpdate(LaxApiModel):
    book_ids: Annotated[list[UUID], Field(min_length=1, max_length=100)]
    reading_status: ReadingStatus | None = None
    add_tags: Annotated[list[str], Field(max_length=20)] = Field(default_factory=list)
    remove_tags: Annotated[list[str], Field(max_length=20)] = Field(
        default_factory=list
    )
    add_shelves: Annotated[list[str], Field(max_length=20)] = Field(
        default_factory=list
    )
    remove_shelves: Annotated[list[str], Field(max_length=20)] = Field(
        default_factory=list
    )

    @field_validator("book_ids")
    @classmethod
    def validate_ids(cls, value: list[UUID]) -> list[UUID]:
        return _unique_ids(value)

    @field_validator("add_tags", "remove_tags", "add_shelves", "remove_shelves")
    @classmethod
    def validate_names(
        cls, value: list[str], info: ValidationInfo
    ) -> list[str]:
        return normalize_name_list(value, info.field_name or "names")

    @model_validator(mode="after")
    def validate_changes(self) -> Self:
        if (
            self.reading_status is None
            and not self.add_tags
            and not self.remove_tags
            and not self.add_shelves
            and not self.remove_shelves
        ):
            raise ValueError("At least one change must be provided.")
        return self


class BookBatchDelete(LaxApiModel):
    book_ids: Annotated[list[UUID], Field(min_length=1, max_length=100)]

    @field_validator("book_ids")
    @classmethod
    def validate_ids(cls, value: list[UUID]) -> list[UUID]:
        return _unique_ids(value)


class BatchResult(ApiModel):
    updated: int = 0
    deleted: int = 0


class BookIdentifier(ApiModel):
    scheme: str
    value: str


class BookCopy(ApiModel):
    id: UUID
    location_id: UUID | None
    location_name: str | None
    acquired_at: int | None
    note: str | None


class BookFile(ApiModel):
    id: UUID
    format: str
    file_status: Literal["present", "missing"]
    size_bytes: int


class BookSummary(ApiModel):
    id: UUID
    title: str
    authors: list[str]
    translators: list[str]
    isbn: str | None
    reading_status: ReadingStatus
    tags: list[str]
    shelves: list[str]
    copy_count: int
    file_count: int
    formats: list[str]
    has_cover: bool
    cover_updated_at: int | None
    created_at: int
    updated_at: int


class BookDetail(BookSummary):
    series: str | None = None
    description: str | None
    identifiers: list[BookIdentifier]
    copies: list[BookCopy]
    files: list[BookFile]


class BookPage(ApiModel):
    items: list[BookSummary]
    total: int
    limit: int
    offset: int


class BookFilterOption(ApiModel):
    value: str
    label: str
    count: int | None = None


class BookFilterField(ApiModel):
    key: str
    group: str
    type: BookFilterFieldType
    operators: list[str]
    option_source: BookFilterOptionSource | None = None
    allow_custom: bool = False
    unit: str | None = None
    options: list[BookFilterOption] = Field(default_factory=list)


class BookFilterSchema(ApiModel):
    fields: list[BookFilterField]
    max_conditions: int


class BookFilterOptionPage(ApiModel):
    source: BookFilterOptionSource
    query: str
    options: list[BookFilterOption]
    has_more: bool
    index_ready: bool
