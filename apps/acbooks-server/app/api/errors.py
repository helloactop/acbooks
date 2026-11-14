import logging
from collections.abc import Mapping
from http import HTTPStatus
from traceback import walk_tb
from typing import Any, cast

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response
from pydantic import BaseModel, Field
from starlette.exceptions import HTTPException

from app.core.errors import BusinessError, BusinessErrorCode

logger = logging.getLogger("uvicorn.error")

_BUSINESS_PROBLEMS = {
    BusinessErrorCode.REVISION_CONFLICT: (HTTPStatus.CONFLICT, "Reading position has changed."),
    BusinessErrorCode.INVALID_CREDENTIALS: (HTTPStatus.UNAUTHORIZED, "Invalid credentials."),
    BusinessErrorCode.SETUP_CODE_INVALID: (HTTPStatus.FORBIDDEN, "Invalid setup code."),
    BusinessErrorCode.SETUP_ALREADY_INITIALIZED: (
        HTTPStatus.CONFLICT,
        "Setup has already been completed.",
    ),
    BusinessErrorCode.RATE_LIMITED: (HTTPStatus.TOO_MANY_REQUESTS, "Too many requests."),
    BusinessErrorCode.BOOK_NOT_FOUND: (HTTPStatus.NOT_FOUND, "Book not found."),
    BusinessErrorCode.COVER_NOT_FOUND: (HTTPStatus.NOT_FOUND, "Cover not found."),
    BusinessErrorCode.INVALID_BOOK_FILTER: (
        HTTPStatus.UNPROCESSABLE_ENTITY,
        "Invalid book filter.",
    ),
    BusinessErrorCode.INVALID_COVER: (
        HTTPStatus.UNPROCESSABLE_ENTITY,
        "Invalid cover image.",
    ),
    BusinessErrorCode.BATCH_LIMIT_EXCEEDED: (
        HTTPStatus.UNPROCESSABLE_ENTITY,
        "Batch change exceeds the per-book limit.",
    ),
    BusinessErrorCode.COPY_NOT_FOUND: (HTTPStatus.NOT_FOUND, "Book copy not found."),
    BusinessErrorCode.LOCATION_NOT_FOUND: (HTTPStatus.NOT_FOUND, "Location not found."),
    BusinessErrorCode.LOCATION_HAS_CHILDREN: (
        HTTPStatus.CONFLICT,
        "Location still has child locations.",
    ),
    BusinessErrorCode.LOCATION_CYCLE: (
        HTTPStatus.UNPROCESSABLE_ENTITY,
        "Location parent would create a cycle.",
    ),
    BusinessErrorCode.METADATA_STALE: (HTTPStatus.CONFLICT, "Book ISBN changed."),
    BusinessErrorCode.METADATA_UNAVAILABLE: (
        HTTPStatus.BAD_GATEWAY, "Metadata source unavailable.",
    ),
    BusinessErrorCode.IMPORT_FILE_CHANGED: (HTTPStatus.CONFLICT, "File changed. Scan again."),
    BusinessErrorCode.FILE_NOT_FOUND: (HTTPStatus.NOT_FOUND, "File not found."),
    BusinessErrorCode.FILE_FORMAT_UNSUPPORTED: (
        HTTPStatus.UNPROCESSABLE_ENTITY,
        "Unsupported file format.",
    ),
    BusinessErrorCode.FILE_TOO_LARGE: (
        HTTPStatus.REQUEST_ENTITY_TOO_LARGE,
        "File exceeds the maximum upload size.",
    ),
    BusinessErrorCode.FILE_DUPLICATE: (
        HTTPStatus.CONFLICT,
        "This file is already attached to the book.",
    ),
    BusinessErrorCode.FILE_EXTERNAL_PROTECTED: (
        HTTPStatus.CONFLICT,
        "External files cannot be deleted.",
    ),
    BusinessErrorCode.STORAGE_ROOT_NOT_FOUND: (
        HTTPStatus.NOT_FOUND,
        "Storage root not found.",
    ),
    BusinessErrorCode.STORAGE_ROOT_INVALID: (
        HTTPStatus.UNPROCESSABLE_ENTITY,
        "Storage root path is invalid.",
    ),
    BusinessErrorCode.STORAGE_ROOT_OVERLAP: (
        HTTPStatus.CONFLICT,
        "Storage root overlaps another root or protected directory.",
    ),
    BusinessErrorCode.STORAGE_ROOT_IN_USE: (
        HTTPStatus.CONFLICT,
        "Storage root still indexes files.",
    ),
    BusinessErrorCode.JOB_NOT_FOUND: (HTTPStatus.NOT_FOUND, "Job not found."),
}


def problem_openapi(status: int) -> dict[str, Any]:
    headers = {"Retry-After": {"schema": {"type": "integer"}}} if status == 429 else {}
    return {
        "description": HTTPStatus(status).phrase,
        "headers": headers,
        "content": {
            "application/problem+json": {"schema": Problem.model_json_schema(mode="serialization")}
        },
    }


class Problem(BaseModel):
    type: str
    title: str
    status: int
    code: str
    detail: str
    request_id: str = Field(serialization_alias="requestId")


def problem_response(
    request: Request,
    status: HTTPStatus,
    code: str,
    detail: str,
    headers: Mapping[str, str] | None = None,
) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        media_type="application/problem+json",
        headers=headers,
        content=Problem(
            type="about:blank",
            title=status.phrase,
            status=status.value,
            code=code,
            detail=detail,
            request_id=request.state.request_id,
        ).model_dump(by_alias=True),
    )


def internal_error(request: Request, error: Exception) -> JSONResponse:
    # Use code locations only. Exception messages, source lines and locals can contain secrets.
    frames = [f"{frame.f_code.co_qualname}:{line}" for frame, line in walk_tb(error.__traceback__)]
    logger.error(
        "request_id=%s code=INTERNAL_ERROR exception=%s frames=%s",
        request.state.request_id,
        type(error).__qualname__,
        " > ".join(frames[-16:]),
    )
    return problem_response(
        request, HTTPStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error."
    )


async def business_error(request: Request, error: Exception) -> Response:
    exc = cast(BusinessError, error)
    status, detail = _BUSINESS_PROBLEMS[exc.code]
    headers = {"Retry-After": str(exc.retry_after)} if exc.retry_after is not None else None
    return problem_response(request, status, exc.code.value, detail, headers)


async def http_error(request: Request, error: Exception) -> Response:
    exc = cast(HTTPException, error)
    try:
        status = HTTPStatus(exc.status_code)
    except ValueError:
        status = HTTPStatus.INTERNAL_SERVER_ERROR
    if status < 400 or status == HTTPStatus.INTERNAL_SERVER_ERROR:
        return internal_error(request, exc)

    return problem_response(request, status, status.name, status.phrase, exc.headers)


async def validation_error(request: Request, _exc: Exception) -> Response:
    return problem_response(
        request, HTTPStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Invalid request input."
    )


def register_exception_handlers(app: FastAPI) -> None:
    app.add_exception_handler(BusinessError, business_error)
    app.add_exception_handler(HTTPException, http_error)
    app.add_exception_handler(RequestValidationError, validation_error)
