from collections.abc import Iterator
from pathlib import Path as FsPath
from typing import Annotated, Any
from uuid import UUID

from fastapi import (
    APIRouter,
    Depends,
    File,
    Form,
    HTTPException,
    Path,
    Query,
    Request,
    Response,
    UploadFile,
)
from fastapi.responses import StreamingResponse

from app.api.errors import problem_openapi
from app.modules.auth.dependencies import AuthDependencies
from app.modules.auth.service import WebSession
from app.modules.books.schemas import BookFile
from app.modules.files import service as files_service
from app.modules.files.service import FilesService, RangeNotSatisfiable

no_store = {"Cache-Control": {"schema": {"type": "string", "const": "no-store"}}}
created_headers = {
    **no_store,
    "Location": {"schema": {"type": "string", "format": "uri-reference"}},
}
file_responses: dict[int | str, dict[str, Any]] = {
    200: {
        "description": "File content",
        "content": {"application/octet-stream": {}},
    },
    206: {
        "description": "Partial content",
        "content": {"application/octet-stream": {}},
    },
    **{status: problem_openapi(status) for status in (401, 404, 416, 500)},
}


def _iter_file(path: FsPath, start: int, length: int) -> Iterator[bytes]:
    remaining = length
    with path.open("rb") as handle:
        handle.seek(start)
        while remaining > 0:
            data = handle.read(min(64 * 1024, remaining))
            if not data:
                break
            remaining -= len(data)
            yield data


def files_router(
    service: FilesService, dependencies: AuthDependencies
) -> APIRouter:
    router = APIRouter(tags=["files"])

    @router.get(
        "/books/{bookId}/files",
        operation_id="listBookFiles",
        response_model=list[BookFile],
        responses={
            200: {"headers": no_store},
            **{status: problem_openapi(status) for status in (401, 404, 500)},
        },
    )
    def list_book_files(
        book_id: Annotated[UUID, Path(alias="bookId")],
        response: Response,
        current: Annotated[WebSession, Depends(dependencies.require_session)],
    ) -> list[BookFile]:
        response.headers["Cache-Control"] = "no-store"
        return service.list_files(book_id)

    @router.post(
        "/uploads",
        status_code=201,
        operation_id="uploadBookFile",
        response_model=BookFile,
        responses={
            201: {"headers": created_headers},
            **{
                status: problem_openapi(status)
                for status in (401, 403, 404, 413, 422, 500)
            },
        },
    )
    def upload_book_file(
        request: Request,
        response: Response,
        current: Annotated[WebSession, Depends(dependencies.require_write)],
        book_id: Annotated[UUID, Form(alias="bookId")],
        file: Annotated[UploadFile, File()],
    ) -> BookFile:
        files_service.enforce_request_size(request.headers.get("content-length"))
        created = service.upload(book_id, file)
        response.headers["Cache-Control"] = "no-store"
        response.headers["Location"] = f"/api/v1/files/{created.id}/content"
        return created

    @router.get(
        "/files/{bookFileId}/content",
        operation_id="getBookFileContent",
        response_class=StreamingResponse,
        responses=file_responses,
    )
    def get_book_file_content(
        request: Request,
        book_file_id: Annotated[UUID, Path(alias="bookFileId")],
        current: Annotated[WebSession, Depends(dependencies.require_session)],
    ) -> StreamingResponse:
        try:
            item = service.content(book_file_id, request.headers.get("range"))
        except RangeNotSatisfiable as error:
            raise HTTPException(
                status_code=416,
                headers={"Content-Range": f"bytes */{error.size}"},
            ) from error
        headers = {
            "Accept-Ranges": "bytes",
            "Content-Disposition": f'attachment; filename="{item.filename}"',
            "Content-Length": str(item.length),
            "ETag": f'"{item.etag}"',
        }
        if item.partial:
            headers["Content-Range"] = (
                f"bytes {item.start}-{item.start + item.length - 1}/{item.total_size}"
            )
            return StreamingResponse(
                _iter_file(item.path, item.start, item.length),
                status_code=206,
                media_type=item.media_type,
                headers=headers,
            )
        return StreamingResponse(
            _iter_file(item.path, item.start, item.length),
            status_code=200,
            media_type=item.media_type,
            headers=headers,
        )

    @router.delete(
        "/files/{bookFileId}",
        status_code=204,
        operation_id="deleteBookFile",
        response_class=Response,
        responses={
            204: {"headers": no_store},
            **{
                status: problem_openapi(status)
                for status in (401, 403, 404, 409, 500)
            },
        },
    )
    def delete_book_file(
        book_file_id: Annotated[UUID, Path(alias="bookFileId")],
        current: Annotated[WebSession, Depends(dependencies.require_write)],
        purge: Annotated[bool, Query()] = True,
    ) -> Response:
        service.delete(book_file_id, purge)
        return Response(status_code=204, headers={"Cache-Control": "no-store"})

    return router