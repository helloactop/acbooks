from collections.abc import Mapping
from http import HTTPStatus
from typing import cast

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response
from pydantic import BaseModel, Field
from starlette.exceptions import HTTPException


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


async def http_error(request: Request, error: Exception) -> Response:
    exc = cast(HTTPException, error)
    try:
        status = HTTPStatus(exc.status_code)
    except ValueError:
        status = HTTPStatus.INTERNAL_SERVER_ERROR
    if status < 400 or status == HTTPStatus.INTERNAL_SERVER_ERROR:
        return problem_response(
            request, HTTPStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error."
        )

    return problem_response(request, status, status.name, status.phrase, exc.headers)


async def validation_error(request: Request, _exc: Exception) -> Response:
    return problem_response(
        request, HTTPStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Invalid request input."
    )


def register_exception_handlers(app: FastAPI) -> None:
    app.add_exception_handler(HTTPException, http_error)
    app.add_exception_handler(RequestValidationError, validation_error)
