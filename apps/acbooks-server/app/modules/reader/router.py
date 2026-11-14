from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Path, Query, Request, Response
from fastapi.responses import JSONResponse

from app.api.errors import problem_openapi
from app.db.models.reading import Annotation, Bookmark, ReadingSession
from app.modules.auth.dependencies import AuthDependencies
from app.modules.auth.service import WebSession
from app.modules.reader.schemas import (
    AnnotationCreate,
    AnnotationRead,
    AnnotationUpdate,
    BookmarkCreate,
    BookmarkRead,
    BookmarkUpdate,
    PositionConflictProblem,
    PositionRead,
    PositionWrite,
    ReadingAccepted,
    ReadingDuplicate,
    ReadingPage,
    SessionCreate,
    SessionRead,
    SessionUpdate,
)
from app.modules.reader.service import PositionConflict, ReaderService, read_mark

BookId = Annotated[UUID, Path(alias="bookId")]
FileId = Annotated[UUID, Path(alias="bookFileId")]
ItemId = Annotated[UUID, Path(alias="itemId")]
Limit = Annotated[int, Query(ge=1, le=100)]
Offset = Annotated[int, Query(ge=0, le=1_000_000)]
NO_STORE = {"Cache-Control": "no-store"}


def reader_router(service: ReaderService, dependencies: AuthDependencies) -> APIRouter:
    def private(response: Response) -> None:
        response.headers.update(NO_STORE)

    router = APIRouter(
        tags=["reader"],
        dependencies=[Depends(private)],
        responses={status: problem_openapi(status) for status in (401, 403, 404, 422, 500)},
    )
    books = APIRouter(prefix="/books/{bookId}")
    Read = Annotated[WebSession, Depends(dependencies.require_session)]
    Write = Annotated[WebSession, Depends(dependencies.require_write)]

    @books.get("/reading-sessions", operation_id="listReadingSessions")
    def list_sessions(
        book_id: BookId, current: Read, limit: Limit = 20, offset: Offset = 0
    ) -> ReadingPage[SessionRead]:
        return service.sessions(book_id, limit, offset)

    @books.post("/reading-sessions", operation_id="createReadingSession")
    def create_session(
        book_id: BookId, payload: SessionCreate, current: Write
    ) -> ReadingAccepted[SessionRead] | ReadingDuplicate:
        return service.create_session(book_id, payload)

    @books.patch("/reading-sessions/{itemId}", operation_id="updateReadingSession")
    def update_session(
        book_id: BookId, item_id: ItemId, payload: SessionUpdate, current: Write
    ) -> SessionRead:
        return service.update_session(book_id, item_id, payload)

    @books.delete(
        "/reading-sessions/{itemId}", status_code=204, operation_id="deleteReadingSession"
    )
    def delete_session(book_id: BookId, item_id: ItemId, current: Write) -> Response:
        service.delete(ReadingSession, book_id, item_id)
        return Response(status_code=204, headers=NO_STORE)

    @books.get("/bookmarks", operation_id="listBookmarks")
    def list_bookmarks(
        book_id: BookId, current: Read, limit: Limit = 20, offset: Offset = 0
    ) -> ReadingPage[BookmarkRead]:
        rows, total = service.list_marks(Bookmark, book_id, limit, offset)
        return ReadingPage[BookmarkRead](
            items=[read_mark(row) for row in rows],
            total=total,
            limit=limit,
            offset=offset,
        )

    @books.post("/bookmarks", operation_id="createBookmark")
    def create_bookmarks(
        book_id: BookId, payload: BookmarkCreate, current: Write
    ) -> ReadingAccepted[BookmarkRead] | ReadingDuplicate:
        row = service.create_mark(Bookmark, book_id, payload)
        return (
            ReadingDuplicate()
            if row is None
            else ReadingAccepted[BookmarkRead](item=read_mark(row))
        )

    @books.patch("/bookmarks/{itemId}", operation_id="updateBookmark")
    def update_bookmarks(
        book_id: BookId, item_id: ItemId, payload: BookmarkUpdate, current: Write
    ) -> BookmarkRead:
        return read_mark(service.update_mark(Bookmark, book_id, item_id, payload))

    @books.delete("/bookmarks/{itemId}", status_code=204, operation_id="deleteBookmark")
    def delete_bookmarks(book_id: BookId, item_id: ItemId, current: Write) -> Response:
        service.delete(Bookmark, book_id, item_id)
        return Response(status_code=204, headers=NO_STORE)

    @books.get("/annotations", operation_id="listAnnotations")
    def list_annotations(
        book_id: BookId, current: Read, limit: Limit = 20, offset: Offset = 0
    ) -> ReadingPage[AnnotationRead]:
        rows, total = service.list_marks(Annotation, book_id, limit, offset)
        return ReadingPage[AnnotationRead](
            items=[read_mark(row) for row in rows],
            total=total,
            limit=limit,
            offset=offset,
        )

    @books.post("/annotations", operation_id="createAnnotation")
    def create_annotations(
        book_id: BookId, payload: AnnotationCreate, current: Write
    ) -> ReadingAccepted[AnnotationRead] | ReadingDuplicate:
        row = service.create_mark(Annotation, book_id, payload)
        return (
            ReadingDuplicate()
            if row is None
            else ReadingAccepted[AnnotationRead](item=read_mark(row))
        )

    @books.patch("/annotations/{itemId}", operation_id="updateAnnotation")
    def update_annotations(
        book_id: BookId, item_id: ItemId, payload: AnnotationUpdate, current: Write
    ) -> AnnotationRead:
        return read_mark(service.update_mark(Annotation, book_id, item_id, payload))

    @books.delete("/annotations/{itemId}", status_code=204, operation_id="deleteAnnotation")
    def delete_annotations(book_id: BookId, item_id: ItemId, current: Write) -> Response:
        service.delete(Annotation, book_id, item_id)
        return Response(status_code=204, headers=NO_STORE)

    @router.get("/reading-positions/{bookFileId}", operation_id="getReadingPosition")
    def get_position(file_id: FileId, current: Read) -> PositionRead | None:
        return service.get_position(file_id)

    conflict_schema = PositionConflictProblem.model_json_schema(
        mode="serialization", ref_template="#/components/schemas/{model}"
    )
    conflict_schema.pop("$defs", None)

    @router.put(
        "/reading-positions/{bookFileId}",
        operation_id="saveReadingPosition",
        response_model=ReadingAccepted[PositionRead] | ReadingDuplicate,
        responses={
            409: {
                "description": "The expected reading position revision did not match.",
                "content": {"application/problem+json": {"schema": conflict_schema}},
            }
        },
    )
    def save_position(
        file_id: FileId,
        payload: PositionWrite,
        request: Request,
        current: Write,
    ) -> ReadingAccepted[PositionRead] | ReadingDuplicate | JSONResponse:
        try:
            return service.save_position(file_id, payload)
        except PositionConflict as exc:
            problem = PositionConflictProblem(
                type="about:blank",
                title="Conflict",
                status=409,
                code="REVISION_CONFLICT",
                detail=str(exc),
                request_id=request.state.request_id,
                current=exc.current,
            )
            return JSONResponse(
                status_code=409,
                content=problem.model_dump(mode="json", by_alias=True),
                media_type="application/problem+json",
                headers=NO_STORE,
            )

    router.include_router(books)
    return router
