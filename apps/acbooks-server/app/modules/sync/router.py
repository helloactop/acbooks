from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Query, Response

from app.api.errors import problem_openapi
from app.modules.auth.dependencies import AuthDependencies
from app.modules.sync.schemas import SyncBatch, SyncBatchResult, SyncPage
from app.modules.sync.service import InvalidCursor, SyncService


def sync_router(service: SyncService, dependencies: AuthDependencies) -> APIRouter:
    def private(response: Response) -> None:
        response.headers["Cache-Control"] = "no-store"

    router = APIRouter(
        prefix="/sync",
        tags=["sync"],
        dependencies=[Depends(private)],
        responses={status: problem_openapi(status) for status in (401, 403, 422, 500)},
    )
    cursor = Annotated[int, Query(ge=0, le=9_007_199_254_740_991)]
    boundary = Annotated[int | None, Query(ge=0, le=9_007_199_254_740_991)]
    page_size = Annotated[int, Query(ge=1, le=100)]

    def pull(after: int, through: int | None, limit: int, snapshot: bool) -> SyncPage:
        try:
            return service.pull(after, through, limit, snapshot)
        except InvalidCursor as error:
            raise HTTPException(422, detail="Invalid sync cursor.") from error

    @router.get(
        "/snapshot",
        operation_id="getSyncSnapshot",
        dependencies=[Depends(dependencies.require_session)],
    )
    def snapshot(after: cursor = 0, through: boundary = None, limit: page_size = 50) -> SyncPage:
        return pull(after, through, limit, True)

    @router.get(
        "/changes",
        operation_id="getSyncChanges",
        dependencies=[Depends(dependencies.require_session)],
    )
    def changes(after: cursor = 0, through: boundary = None, limit: page_size = 50) -> SyncPage:
        return pull(after, through, limit, False)

    @router.post(
        "/mutations",
        operation_id="submitSyncMutations",
        dependencies=[Depends(dependencies.require_write)],
    )
    def mutations(payload: SyncBatch) -> SyncBatchResult:
        return service.submit(payload)

    return router
