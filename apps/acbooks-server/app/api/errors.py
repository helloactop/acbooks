import logging
from collections.abc import Mapping
from http import HTTPStatus
from traceback import walk_tb
from typing import cast

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
    code = cast(BusinessError, error).code
    status, detail = _BUSINESS_PROBLEMS[code]
    return problem_response(request, status, code.value, detail)


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
