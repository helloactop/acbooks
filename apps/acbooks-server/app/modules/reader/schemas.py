import json
from typing import Annotated, Generic, Literal, Self, TypeVar
from uuid import UUID

from pydantic import Field, JsonValue, field_validator, model_validator

from app.api.errors import Problem
from app.modules.books.schemas import LaxApiModel

Timestamp = Annotated[int, Field(strict=True, ge=0, le=9_007_199_254_740_991)]
Seconds = Annotated[int, Field(strict=True, ge=0, le=9_007_199_254_740)]
Revision = Annotated[int, Field(strict=True, ge=0, le=9_007_199_254_740_991)]
Percent = Annotated[float, Field(strict=True, ge=0, le=100, allow_inf_nan=False)]
Label = Annotated[str, Field(max_length=200)]
Quote = Annotated[str, Field(max_length=20_000)]
Note = Annotated[str, Field(max_length=20_000)]
Locator = dict[str, JsonValue]
Source = Literal["automatic", "manual"]


def validate_locator(value: Locator | None) -> Locator | None:
    try:
        size = len(json.dumps(value, ensure_ascii=True, allow_nan=False).encode())
    except (ValueError, RecursionError) as exc:
        raise ValueError("Invalid locator JSON.") from exc
    if size > 32_768:
        raise ValueError("Locator exceeds 32 KiB.")
    return value


class Mutation(LaxApiModel):
    device_id: UUID
    mutation_id: UUID


class SessionFields(LaxApiModel):
    started_at: Timestamp
    ended_at: Timestamp | None = None
    active_seconds: Seconds = 0

    @model_validator(mode="after")
    def check_duration(self) -> Self:
        if self.ended_at is not None:
            elapsed = self.ended_at - self.started_at
            if elapsed < 0 or self.active_seconds * 1000 > elapsed:
                raise ValueError("Active seconds must fit within the session interval.")
        return self


class SessionCreate(SessionFields, Mutation):
    book_file_id: UUID | None = None
    source: Source = "manual"

    @model_validator(mode="after")
    def check_source(self) -> Self:
        if self.source == "automatic" and self.book_file_id is None:
            raise ValueError("An automatic session requires a book file.")
        if self.source == "manual" and self.ended_at is None:
            raise ValueError("A manual session requires an end time.")
        return self


class SessionUpdate(LaxApiModel):
    started_at: Timestamp | None = None
    ended_at: Timestamp | None = None
    active_seconds: Seconds | None = None

    @model_validator(mode="after")
    def non_empty(self) -> Self:
        if not self.model_fields_set:
            raise ValueError("Supply at least one field.")
        for field in ("started_at", "active_seconds"):
            if field in self.model_fields_set and getattr(self, field) is None:
                raise ValueError(f"{field} cannot be null.")
        return self


class SessionRead(LaxApiModel):
    id: UUID
    book_id: UUID
    book_file_id: UUID | None
    device_id: UUID | None
    started_at: int
    ended_at: int | None
    active_seconds: int
    source: Source


class PositionWrite(Mutation):
    revision: Revision
    locator: Locator
    percent: Percent | None = None

    _check_locator = field_validator("locator")(validate_locator)


class PositionRead(LaxApiModel):
    book_file_id: UUID
    locator: Locator
    percent: float | None
    revision: int
    updated_at: int


class PositionConflictProblem(Problem):
    current: PositionRead | None


class MarkFields(LaxApiModel):
    book_file_id: UUID | None = None
    locator: Locator | None = None

    _check_locator = field_validator("locator")(validate_locator)


class BookmarkUpdate(MarkFields):
    label: Label | None = None


class BookmarkCreate(BookmarkUpdate, Mutation):
    pass


class AnnotationUpdate(MarkFields):
    quote: Quote | None = None
    note: Note | None = None


class AnnotationCreate(AnnotationUpdate, Mutation):
    pass


class BookmarkRead(BookmarkUpdate):
    id: UUID
    book_id: UUID
    created_at: int
    updated_at: int


class AnnotationRead(AnnotationUpdate):
    id: UUID
    book_id: UUID
    created_at: int
    updated_at: int


Item = TypeVar("Item")


class ReadingPage(LaxApiModel, Generic[Item]):
    items: list[Item]
    total: int
    limit: int
    offset: int


class ReadingAccepted(LaxApiModel, Generic[Item]):
    status: Literal["accepted"] = "accepted"
    item: Item


class ReadingDuplicate(LaxApiModel):
    status: Literal["duplicate"] = "duplicate"
