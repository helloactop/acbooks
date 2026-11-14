from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Query
from sqlalchemy import Engine

from app.modules.auth.dependencies import AuthDependencies
from app.modules.auth.service import AuthIdentity
from app.modules.metadata.schemas import (
    AppliedMetadata,
    BatchRequest,
    Candidate,
    MetadataJob,
    MetadataSettings,
)
from app.modules.metadata.service import MetadataService
from app.workers import queue


def metadata_router(
    engine: Engine,
    service: MetadataService,
    dependencies: AuthDependencies,
) -> APIRouter:
    router = APIRouter(tags=["metadata"])

    @router.get("/metadata/candidates", operation_id="searchMetadata")
    def search(
        current: Annotated[AuthIdentity, Depends(dependencies.require_session)],
        q: Annotated[str, Query(min_length=1, max_length=200)],
    ) -> list[Candidate]:
        return service.search(q.strip()) if q.strip() else []

    @router.post("/books/{book_id}/metadata", operation_id="applyMetadata")
    def apply(
        book_id: UUID,
        candidate: Candidate,
        current: Annotated[AuthIdentity, Depends(dependencies.require_write)],
    ) -> AppliedMetadata:
        return AppliedMetadata(fields=service.apply(str(book_id), candidate))

    @router.post("/metadata/jobs", status_code=202, operation_id="startMetadataJob")
    def batch(
        body: BatchRequest,
        current: Annotated[AuthIdentity, Depends(dependencies.require_write)],
    ) -> MetadataJob:
        return MetadataJob(
            job_id=queue.enqueue(
                engine,
                "metadata_fill",
                {"bookIds": list(dict.fromkeys(map(str, body.book_ids)))},
            )
        )

    @router.get("/metadata/settings", operation_id="getMetadataSettings")
    def settings(
        current: Annotated[AuthIdentity, Depends(dependencies.require_session)],
    ) -> MetadataSettings:
        return service.settings()

    @router.patch("/metadata/settings", operation_id="setMetadataSettings")
    def save_settings(
        body: MetadataSettings,
        current: Annotated[AuthIdentity, Depends(dependencies.require_write)],
    ) -> MetadataSettings:
        service.save_settings(body)
        return body

    return router
