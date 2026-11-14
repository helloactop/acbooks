from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Path, Response

from app.api.errors import problem_openapi
from app.modules.auth.dependencies import AuthDependencies
from app.modules.auth.service import AuthIdentity
from app.modules.library.schemas import StorageRootCreate, StorageRootNode
from app.modules.library.service import LibraryService

no_store = {"Cache-Control": {"schema": {"type": "string", "const": "no-store"}}}
created_headers = {
    **no_store,
    "Location": {"schema": {"type": "string", "format": "uri-reference"}},
}


def library_router(
    service: LibraryService, dependencies: AuthDependencies
) -> APIRouter:
    router = APIRouter(tags=["library"])

    @router.get(
        "/storage-roots",
        operation_id="listStorageRoots",
        response_model=list[StorageRootNode],
        responses={
            200: {"headers": no_store},
            **{status: problem_openapi(status) for status in (401, 500)},
        },
    )
    def list_storage_roots(
        response: Response,
        current: Annotated[AuthIdentity, Depends(dependencies.require_session)],
    ) -> list[StorageRootNode]:
        response.headers["Cache-Control"] = "no-store"
        return service.list_roots()

    @router.post(
        "/storage-roots",
        status_code=201,
        operation_id="createStorageRoot",
        response_model=StorageRootNode,
        responses={
            201: {"headers": created_headers},
            **{
                status: problem_openapi(status)
                for status in (401, 403, 409, 422, 500)
            },
        },
    )
    def create_storage_root(
        payload: StorageRootCreate,
        response: Response,
        current: Annotated[AuthIdentity, Depends(dependencies.require_write)],
    ) -> StorageRootNode:
        node = service.create_root(payload)
        response.headers["Cache-Control"] = "no-store"
        response.headers["Location"] = f"/api/v1/storage-roots/{node.id}"
        return node

    @router.delete(
        "/storage-roots/{rootId}",
        status_code=204,
        operation_id="deleteStorageRoot",
        response_class=Response,
        responses={
            204: {"headers": no_store},
            **{
                status: problem_openapi(status)
                for status in (401, 403, 404, 409, 500)
            },
        },
    )
    def delete_storage_root(
        root_id: Annotated[UUID, Path(alias="rootId")],
        current: Annotated[AuthIdentity, Depends(dependencies.require_write)],
    ) -> Response:
        service.delete_root(root_id)
        return Response(status_code=204, headers={"Cache-Control": "no-store"})

    return router