from typing import Annotated, Literal
from uuid import UUID

from pydantic import Field, field_validator

from app.modules.books.normalization import clean_text
from app.modules.books.schemas import ApiModel, LaxApiModel

StorageMode = Literal["managed", "external"]

__all__ = ["StorageMode", "StorageRootCreate", "StorageRootNode"]


class StorageRootCreate(LaxApiModel):
    name: Annotated[str, Field(min_length=1, max_length=200)]
    root_path: Annotated[str, Field(min_length=1, max_length=4096)]
    mode: StorageMode

    @field_validator("name")
    @classmethod
    def validate_name(cls, value: str) -> str:
        return clean_text(value, "name", max_length=200)

    @field_validator("root_path")
    @classmethod
    def validate_path(cls, value: str) -> str:
        return clean_text(value, "rootPath", max_length=4096)


class StorageRootNode(ApiModel):
    id: UUID
    name: str
    root_path: str
    mode: StorageMode
    file_count: int