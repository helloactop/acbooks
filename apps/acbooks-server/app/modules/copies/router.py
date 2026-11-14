from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Path, Response

from app.api.errors import problem_openapi
from app.modules.auth.dependencies import AuthDependencies
from app.modules.auth.service import AuthIdentity
from app.modules.books.schemas import BookCopy, BookCopyCreate
from app.modules.copies.schemas import (
    BookCopyUpdate,
    LocationCreate,
    LocationNode,
    LocationUpdate,
)
from app.modules.copies.service import CopiesService

no_store = {"Cache-Control": {"schema": {"type": "string", "const": "no-store"}}}
created_headers = {
    **no_store,
    "Location": {"schema": {"type": "string", "format": "uri-reference"}},
}


def copies_router(
    service: CopiesService, dependencies: AuthDependencies
) -> APIRouter:
    router = APIRouter()

    @router.get(
        "/locations",
        operation_id="listLocations",
        response_model=list[LocationNode],
        tags=["locations"],
        responses={
            200: {"headers": no_store},
            **{status: problem_openapi(status) for status in (401, 500)},
        },
    )
    def list_locations(
        response: Response,
        current: Annotated[AuthIdentity, Depends(dependencies.require_session)],
    ) -> list[LocationNode]:
        response.headers["Cache-Control"] = "no-store"
        return service.list_locations()

    @router.post(
        "/locations",
        status_code=201,
        operation_id="createLocation",
        response_model=LocationNode,
        tags=["locations"],
        responses={
            201: {"headers": created_headers},
            **{
                status: problem_openapi(status)
                for status in (401, 403, 404, 422, 500)
            },
        },
    )
    def create_location(
        payload: LocationCreate,
        response: Response,
        current: Annotated[AuthIdentity, Depends(dependencies.require_write)],
    ) -> LocationNode:
        node = service.create_location(payload)
        response.headers["Cache-Control"] = "no-store"
        response.headers["Location"] = f"/api/v1/locations/{node.id}"
        return node

    @router.patch(
        "/locations/{locationId}",
        operation_id="updateLocation",
        response_model=LocationNode,
        tags=["locations"],
        responses={
            200: {"headers": no_store},
            **{
                status: problem_openapi(status)
                for status in (401, 403, 404, 409, 422, 500)
            },
        },
    )
    def update_location(
        location_id: Annotated[UUID, Path(alias="locationId")],
        payload: LocationUpdate,
        response: Response,
        current: Annotated[AuthIdentity, Depends(dependencies.require_write)],
    ) -> LocationNode:
        response.headers["Cache-Control"] = "no-store"
        return service.update_location(location_id, payload)

    @router.delete(
        "/locations/{locationId}",
        status_code=204,
        operation_id="deleteLocation",
        response_class=Response,
        tags=["locations"],
        responses={
            204: {"headers": no_store},
            **{
                status: problem_openapi(status)
                for status in (401, 403, 404, 409, 500)
            },
        },
    )
    def delete_location(
        location_id: Annotated[UUID, Path(alias="locationId")],
        current: Annotated[AuthIdentity, Depends(dependencies.require_write)],
    ) -> Response:
        service.delete_location(location_id)
        return Response(status_code=204, headers={"Cache-Control": "no-store"})

    @router.get(
        "/books/{bookId}/copies",
        operation_id="listBookCopies",
        response_model=list[BookCopy],
        tags=["copies"],
        responses={
            200: {"headers": no_store},
            **{status: problem_openapi(status) for status in (401, 404, 500)},
        },
    )
    def list_book_copies(
        book_id: Annotated[UUID, Path(alias="bookId")],
        response: Response,
        current: Annotated[AuthIdentity, Depends(dependencies.require_session)],
    ) -> list[BookCopy]:
        response.headers["Cache-Control"] = "no-store"
        return service.list_copies(book_id)

    @router.post(
        "/books/{bookId}/copies",
        status_code=201,
        operation_id="createBookCopy",
        response_model=BookCopy,
        tags=["copies"],
        responses={
            201: {"headers": created_headers},
            **{
                status: problem_openapi(status)
                for status in (401, 403, 404, 422, 500)
            },
        },
    )
    def create_book_copy(
        book_id: Annotated[UUID, Path(alias="bookId")],
        payload: BookCopyCreate,
        response: Response,
        current: Annotated[AuthIdentity, Depends(dependencies.require_write)],
    ) -> BookCopy:
        copy = service.create_copy(book_id, payload)
        response.headers["Cache-Control"] = "no-store"
        response.headers["Location"] = f"/api/v1/books/{book_id}/copies/{copy.id}"
        return copy

    @router.patch(
        "/books/{bookId}/copies/{copyId}",
        operation_id="updateBookCopy",
        response_model=BookCopy,
        tags=["copies"],
        responses={
            200: {"headers": no_store},
            **{
                status: problem_openapi(status)
                for status in (401, 403, 404, 422, 500)
            },
        },
    )
    def update_book_copy(
        book_id: Annotated[UUID, Path(alias="bookId")],
        copy_id: Annotated[UUID, Path(alias="copyId")],
        payload: BookCopyUpdate,
        response: Response,
        current: Annotated[AuthIdentity, Depends(dependencies.require_write)],
    ) -> BookCopy:
        response.headers["Cache-Control"] = "no-store"
        return service.update_copy(book_id, copy_id, payload)

    @router.delete(
        "/books/{bookId}/copies/{copyId}",
        status_code=204,
        operation_id="deleteBookCopy",
        response_class=Response,
        tags=["copies"],
        responses={
            204: {"headers": no_store},
            **{status: problem_openapi(status) for status in (401, 403, 404, 500)},
        },
    )
    def delete_book_copy(
        book_id: Annotated[UUID, Path(alias="bookId")],
        copy_id: Annotated[UUID, Path(alias="copyId")],
        current: Annotated[AuthIdentity, Depends(dependencies.require_write)],
    ) -> Response:
        service.delete_copy(book_id, copy_id)
        return Response(status_code=204, headers={"Cache-Control": "no-store"})

    return router