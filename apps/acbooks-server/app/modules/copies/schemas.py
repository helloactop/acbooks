from typing import Annotated, Self
from uuid import UUID

from pydantic import Field, field_validator, model_validator

from app.modules.books.normalization import clean_text
from app.modules.books.schemas import ApiModel, LaxApiModel

__all__ = [
    "BookCopyUpdate",
    "LocationCreate",
    "LocationNode",
    "LocationUpdate",
]


class LocationCreate(LaxApiModel):
    name: Annotated[str, Field(min_length=1, max_length=200)]
    parent_id: UUID | None = None

    @field_validator("name")
    @classmethod
    def validate_name(cls, value: str) -> str:
        return clean_text(value, "name", max_length=200)


class LocationUpdate(LaxApiModel):
    name: Annotated[str | None, Field(min_length=1, max_length=200)] = None
    parent_id: UUID | None = None

    @field_validator("name")
    @classmethod
    def validate_name(cls, value: str | None) -> str | None:
        if value is None:
            return None
        return clean_text(value, "name", max_length=200)

    @model_validator(mode="after")
    def validate_patch(self) -> Self:
        if not self.model_fields_set:
            raise ValueError("At least one field must be provided.")
        if "name" in self.model_fields_set and self.name is None:
            raise ValueError("name cannot be null.")
        return self


class LocationNode(ApiModel):
    id: UUID
    parent_id: UUID | None
    name: str
    copy_count: int


class BookCopyUpdate(LaxApiModel):
    location_id: UUID | None = None
    acquired_at: int | None = None
    note: Annotated[str | None, Field(max_length=2000)] = None

    @field_validator("note")
    @classmethod
    def validate_note(cls, value: str | None) -> str | None:
        if value is None:
            return None
        return clean_text(value, "note", allow_newlines=True, max_length=2000)

    @model_validator(mode="after")
    def validate_patch(self) -> Self:
        if not self.model_fields_set:
            raise ValueError("At least one field must be provided.")
        return self