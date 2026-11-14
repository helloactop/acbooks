from datetime import date
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Path, Query, Response
from fastapi.exceptions import RequestValidationError
from pydantic import ValidationError

from app.api.errors import problem_openapi
from app.modules.auth.dependencies import AuthDependencies
from app.modules.reader.schemas import ReadingPage
from app.modules.stats.schemas import ReadingStats, StatsRange, TimelineSession
from app.modules.stats.service import StatsService


def read_range(
    start_date: Annotated[date, Query(alias="startDate")],
    end_date: Annotated[date, Query(alias="endDate")],
    time_zone: Annotated[str, Query(alias="timeZone", min_length=1, max_length=128)],
) -> StatsRange:
    try:
        return StatsRange(start_date=start_date, end_date=end_date, time_zone=time_zone)
    except ValidationError as error:
        raise RequestValidationError(error.errors()) from error


def stats_router(service: StatsService, dependencies: AuthDependencies) -> APIRouter:
    def private(response: Response) -> None:
        response.headers["Cache-Control"] = "no-store"

    router = APIRouter(
        tags=["stats"],
        dependencies=[Depends(dependencies.require_session), Depends(private)],
        responses={status: problem_openapi(status) for status in (401, 403, 404, 422, 500)},
    )
    Range = Annotated[StatsRange, Depends(read_range)]
    BookId = Annotated[UUID, Path(alias="bookId")]

    @router.get("/stats/overview", operation_id="getReadingStats")
    def overview(query: Range) -> ReadingStats:
        return service.overview(query)

    @router.get("/books/{bookId}/stats", operation_id="getBookReadingStats")
    def book_overview(book_id: BookId, query: Range) -> ReadingStats:
        return service.overview(query, str(book_id))

    @router.get("/stats/timeline", operation_id="listReadingTimeline")
    def timeline(
        query: Range,
        book_id: Annotated[UUID | None, Query(alias="bookId")] = None,
        limit: Annotated[int, Query(ge=1, le=100)] = 20,
        offset: Annotated[int, Query(ge=0, le=1_000_000)] = 0,
    ) -> ReadingPage[TimelineSession]:
        return service.timeline(query, str(book_id) if book_id is not None else None, limit, offset)

    return router
