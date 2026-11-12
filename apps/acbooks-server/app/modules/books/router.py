from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Path, Query, Response

from app.api.errors import problem_openapi
from app.modules.auth.dependencies import AuthDependencies
from app.modules.auth.service import WebSession
from app.modules.books.filtering import BookFilterOptionSource
from app.modules.books.schemas import (
    BookCreate,
    BookDetail,
    BookFilterOptionPage,
    BookFilterSchema,
    BookOrder,
    BookPage,
    BookSort,
    BookUpdate,
    MediaType,
    ReadingStatus,
)
from app.modules.books.service import BooksService

no_store = {"Cache-Control": {"schema": {"type": "string", "const": "no-store"}}}
created_headers = {
    **no_store,
    "Location": {"schema": {"type": "string", "format": "uri-reference"}},
}


def books_router(
    service: BooksService, dependencies: AuthDependencies
) -> APIRouter:
    router = APIRouter(prefix="/books", tags=["books"])

    @router.get(
        "",
        operation_id="listBooks",
        response_model=BookPage,
        responses={
            200: {"headers": no_store},
            **{
                status: problem_openapi(status)
                for status in (401, 403, 422, 500)
            },
        },
    )
    def list_books(
        response: Response,
        current: Annotated[WebSession, Depends(dependencies.require_session)],
        q: Annotated[str | None, Query(max_length=200)] = None,
        reading_status: Annotated[
            ReadingStatus | None, Query(alias="readingStatus")
        ] = None,
        tag: Annotated[str | None, Query(max_length=200)] = None,
        shelf_id: Annotated[UUID | None, Query(alias="shelfId")] = None,
        media_type: Annotated[
            MediaType | None, Query(alias="mediaType")
        ] = None,
        media_format: Annotated[
            str | None, Query(alias="format", max_length=32)
        ] = None,
        filters: Annotated[str | None, Query(max_length=20000)] = None,
        sort: Annotated[BookSort, Query()] = "createdAt",
        order: Annotated[BookOrder, Query()] = "desc",
        limit: Annotated[int, Query(ge=1, le=100)] = 20,
        offset: Annotated[int, Query(ge=0)] = 0,
    ) -> BookPage:
        response.headers["Cache-Control"] = "no-store"
        return service.list_books(
            limit=limit,
            offset=offset,
            q=q,
            reading_status=reading_status,
            tag=tag,
            shelf_id=shelf_id,
            media_type=media_type,
            media_format=media_format,
            filters=filters,
            sort=sort,
            order=order,
        )

    @router.post(
        "",
        status_code=201,
        operation_id="createBook",
        response_model=BookDetail,
        responses={
            201: {"headers": created_headers},
            **{
                status: problem_openapi(status)
                for status in (401, 403, 422, 500)
            },
        },
    )
    def create_book(
        payload: BookCreate,
        response: Response,
        current: Annotated[WebSession, Depends(dependencies.require_write)],
    ) -> BookDetail:
        detail = service.create_book(payload)
        response.headers["Cache-Control"] = "no-store"
        response.headers["Location"] = f"/api/v1/books/{detail.id}"
        return detail

    @router.get(
        "/filter-schema",
        operation_id="getBookFilterSchema",
        response_model=BookFilterSchema,
        responses={
            200: {"headers": no_store},
            **{
                status: problem_openapi(status)
                for status in (401, 500)
            },
        },
    )
    def get_filter_schema(
        response: Response,
        current: Annotated[WebSession, Depends(dependencies.require_session)],
    ) -> BookFilterSchema:
        response.headers["Cache-Control"] = "no-store"
        return service.get_filter_schema()

    @router.get(
        "/filter-options",
        operation_id="getBookFilterOptions",
        response_model=BookFilterOptionPage,
        responses={
            200: {"headers": no_store},
            **{
                status: problem_openapi(status)
                for status in (401, 422, 500)
            },
        },
    )
    def get_filter_options(
        response: Response,
        current: Annotated[WebSession, Depends(dependencies.require_session)],
        source: Annotated[BookFilterOptionSource, Query()],
        query: Annotated[str, Query(max_length=100)] = "",
        limit: Annotated[int, Query(ge=1, le=50)] = 20,
    ) -> BookFilterOptionPage:
        response.headers["Cache-Control"] = "no-store"
        return service.get_filter_options(
            source=source, query=query, limit=limit
        )

    @router.get(
        "/{bookId}",
        operation_id="getBook",
        response_model=BookDetail,
        responses={
            200: {"headers": no_store},
            **{
                status: problem_openapi(status)
                for status in (401, 403, 404, 422, 500)
            },
        },
    )
    def get_book(
        book_id: Annotated[UUID, Path(alias="bookId")],
        response: Response,
        current: Annotated[WebSession, Depends(dependencies.require_session)],
    ) -> BookDetail:
        response.headers["Cache-Control"] = "no-store"
        return service.get_book(book_id)

    @router.patch(
        "/{bookId}",
        operation_id="updateBook",
        response_model=BookDetail,
        responses={
            200: {"headers": no_store},
            **{
                status: problem_openapi(status)
                for status in (401, 403, 404, 422, 500)
            },
        },
    )
    def update_book(
        book_id: Annotated[UUID, Path(alias="bookId")],
        payload: BookUpdate,
        response: Response,
        current: Annotated[WebSession, Depends(dependencies.require_write)],
    ) -> BookDetail:
        response.headers["Cache-Control"] = "no-store"
        return service.update_book(book_id, payload)

    return router
