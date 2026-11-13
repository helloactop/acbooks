from typing import Any, Literal
from uuid import UUID

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