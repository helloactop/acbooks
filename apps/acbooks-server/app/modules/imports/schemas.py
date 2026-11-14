from typing import Annotated, Any, Literal
from uuid import UUID

from pydantic import Field

from app.modules.books.schemas import ApiModel

__all__ = ["JobStatus", "ScanAccepted"]


class ScanAccepted(ApiModel):
    job_id: UUID


class JobStatus(ApiModel):
    id: UUID
    type: str
    status: Literal["queued", "running", "succeeded", "failed"]
    created_at: int
    updated_at: int
    result: dict[str, Any] | None = None
    error_code: str | None = None


MatchReason = Literal["title", "isbn", "sha256", "filename"]


class ImportCandidate(ApiModel):
    book_id: str
    title: str
    reasons: list[MatchReason]


class PendingFile(ApiModel):
    id: str
    relative_path: str
    size_bytes: int


class PendingFilePreview(PendingFile):
    format: str
    title: str
    authors: list[str]
    description: str | None
    isbn: str | None
    candidates: list[ImportCandidate]


class PendingFiles(ApiModel):
    items: list[PendingFile]
    total: int
    limit: int
    offset: int


class ResolveFile(ApiModel):
    book_id: Annotated[UUID, Field(strict=False)] | None = None


class ResolvedFile(ApiModel):
    book_id: str
    file_id: str
