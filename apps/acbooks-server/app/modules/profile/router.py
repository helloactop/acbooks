from typing import Annotated

from fastapi import APIRouter, Depends, File, Response, UploadFile
from fastapi.responses import FileResponse

from app.api.errors import problem_openapi
from app.modules.auth.dependencies import AuthDependencies
from app.modules.auth.schemas import AccountResponse
from app.modules.auth.service import AuthIdentity
from app.modules.profile.service import MAX_AVATAR_BYTES, ProfileService

no_store = {"Cache-Control": {"schema": {"type": "string", "const": "no-store"}}}


def profile_router(service: ProfileService, dependencies: AuthDependencies) -> APIRouter:
    router = APIRouter(tags=["profile"])

    @router.get(
        "/auth/me/avatar",
        operation_id="getAccountAvatar",
        response_class=FileResponse,
        responses={
            200: {"content": {"image/jpeg": {}}},
            401: problem_openapi(401),
            404: problem_openapi(404),
            500: problem_openapi(500),
        },
    )
    def get_avatar(
        current: Annotated[AuthIdentity, Depends(dependencies.require_session)],
    ) -> FileResponse:
        path, media_type = service.get_avatar(current)
        return FileResponse(
            path,
            media_type=media_type,
            headers={"Cache-Control": "private, max-age=0, must-revalidate"},
        )

    @router.put(
        "/auth/me/avatar",
        operation_id="uploadAccountAvatar",
        response_model=AccountResponse,
        responses={
            200: {"headers": no_store},
            **{status: problem_openapi(status) for status in (401, 403, 413, 422, 500)},
        },
    )
    def upload_avatar(
        response: Response,
        current: Annotated[AuthIdentity, Depends(dependencies.require_write)],
        file: Annotated[UploadFile, File()],
    ) -> AccountResponse:
        content = file.file.read(MAX_AVATAR_BYTES + 1)
        response.headers["Cache-Control"] = "no-store"
        return service.save_avatar(current, content)

    @router.delete(
        "/auth/me/avatar",
        operation_id="deleteAccountAvatar",
        response_model=AccountResponse,
        responses={
            200: {"headers": no_store},
            **{status: problem_openapi(status) for status in (401, 403, 500)},
        },
    )
    def delete_avatar(
        response: Response,
        current: Annotated[AuthIdentity, Depends(dependencies.require_write)],
    ) -> AccountResponse:
        response.headers["Cache-Control"] = "no-store"
        return service.delete_avatar(current)

    return router