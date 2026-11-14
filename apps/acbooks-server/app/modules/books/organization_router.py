from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Query, Response

from app.api.errors import problem_openapi
from app.modules.auth.dependencies import AuthDependencies
from app.modules.auth.service import WebSession
from app.modules.books.organization_schemas import (
    BookMergePair,
    BookMergePreview,
    BookMergeRequest,
    DuplicatePage,
    FileReassociation,
    OrganizationBookPage,
    OrganizationKind,
    OrganizationName,
    OrganizationPage,
    OrganizationTarget,
)
from app.modules.books.organization_service import BookOrganizationService

Limit = Annotated[int, Query(ge=1, le=100)]
Offset = Annotated[int, Query(ge=0, le=1_000_000)]


def organization_router(
    service: BookOrganizationService, dependencies: AuthDependencies
) -> APIRouter:
    router = APIRouter(
        prefix="/organization",
        tags=["organization"],
        responses={status: problem_openapi(status) for status in (401, 403, 404, 409, 422, 500)},
    )
    Read = Annotated[WebSession, Depends(dependencies.require_session)]
    Write = Annotated[WebSession, Depends(dependencies.require_write)]

    @router.get("/duplicates", operation_id="listDuplicateBooks")
    def duplicates(
        response: Response, current: Read, limit: Limit = 20, offset: Offset = 0
    ) -> DuplicatePage:
        response.headers["Cache-Control"] = "no-store"
        return service.duplicates(limit, offset)

    @router.post("/books/merge-preview", operation_id="previewBookMerge")
    def preview(payload: BookMergePair, response: Response, current: Write) -> BookMergePreview:
        response.headers["Cache-Control"] = "no-store"
        return service.preview(payload)

    @router.post("/books/merge", status_code=204, operation_id="mergeBooks")
    def merge(payload: BookMergeRequest, current: Write) -> Response:
        service.merge(payload)
        return Response(status_code=204, headers={"Cache-Control": "no-store"})

    @router.patch("/files/{file_id}/book", status_code=204, operation_id="reassociateBookFile")
    def reassociate(file_id: UUID, payload: FileReassociation, current: Write) -> Response:
        service.reassociate(file_id, payload)
        return Response(status_code=204, headers={"Cache-Control": "no-store"})

    @router.get("/{kind}", operation_id="listOrganizationEntries")
    def list_entries(
        kind: OrganizationKind,
        response: Response,
        current: Read,
        limit: Limit = 20,
        offset: Offset = 0,
    ) -> OrganizationPage:
        response.headers["Cache-Control"] = "no-store"
        return service.list_entries(kind, limit, offset)

    @router.get("/{kind}/{entry_id}/books", operation_id="listOrganizationBooks")
    def list_books(
        kind: OrganizationKind,
        entry_id: UUID,
        response: Response,
        current: Read,
        limit: Limit = 20,
        offset: Offset = 0,
    ) -> OrganizationBookPage:
        response.headers["Cache-Control"] = "no-store"
        return service.list_books(kind, entry_id, limit, offset)

    @router.patch("/{kind}/{entry_id}", status_code=204, operation_id="renameOrganizationEntry")
    def rename(
        kind: OrganizationKind, entry_id: UUID, payload: OrganizationName, current: Write
    ) -> Response:
        service.rename(kind, entry_id, payload.name)
        return Response(status_code=204, headers={"Cache-Control": "no-store"})

    @router.delete("/{kind}/{entry_id}", status_code=204, operation_id="deleteOrganizationEntry")
    def remove(kind: OrganizationKind, entry_id: UUID, current: Write) -> Response:
        service.remove(kind, entry_id)
        return Response(status_code=204, headers={"Cache-Control": "no-store"})

    @router.post(
        "/{kind}/{entry_id}/merge", status_code=204, operation_id="mergeOrganizationEntries"
    )
    def merge_entries(
        kind: OrganizationKind, entry_id: UUID, payload: OrganizationTarget, current: Write
    ) -> Response:
        service.remove(kind, entry_id, payload.target_id)
        return Response(status_code=204, headers={"Cache-Control": "no-store"})

    return router
