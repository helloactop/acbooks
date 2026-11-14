from typing import Annotated, Literal
from uuid import UUID

from pydantic import Field

from app.modules.books.schemas import ApiModel, BookCreate


class Candidate(BookCreate):
    source_key: Annotated[str, Field(pattern=r"^/(books/OL[0-9]+M|works/OL[0-9]+W)$")] | None = None
    cover_id: Annotated[int, Field(gt=0, le=2147483647)] | None = None


class BatchRequest(ApiModel):
    book_ids: Annotated[
        list[Annotated[UUID, Field(strict=False)]], Field(min_length=1, max_length=20)
    ]


MetadataField = Literal["title", "authors", "translators", "series", "description", "isbn", "cover"]
DEFAULT_FIELDS: list[MetadataField] = [
    "title",
    "authors",
    "translators",
    "series",
    "description",
    "isbn",
    "cover",
]


class MetadataSettings(ApiModel):
    auto_fill: bool = False
    fields: list[MetadataField] = Field(default_factory=lambda: DEFAULT_FIELDS.copy(), max_length=7)


class AppliedMetadata(ApiModel):
    fields: list[str]


class MetadataJob(ApiModel):
    job_id: str
