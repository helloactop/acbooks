import logging
from http import HTTPStatus
from ipaddress import ip_address
from typing import Annotated, Any
from urllib.parse import urlsplit

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from fastapi.security import APIKeyHeader

from app.api.errors import Problem
from app.core.errors import BusinessError
from app.modules.auth.csrf import CHALLENGE_TTL, PreAuthCsrf
from app.modules.auth.schemas import AccountResponse, CsrfResponse, SetupRequest, SetupStatus
from app.modules.auth.service import SetupService

logger = logging.getLogger("uvicorn.error")
csrf_header = APIKeyHeader(name="X-CSRF-Token", auto_error=False)
no_store = {"Cache-Control": {"schema": {"type": "string", "const": "no-store"}}}


def problem(status: int) -> dict[str, Any]:
    headers = {"Retry-After": {"schema": {"type": "integer"}}} if status == 429 else {}
    return {
        "description": HTTPStatus(status).phrase,
        "headers": headers,
        "content": {
            "application/problem+json": {"schema": Problem.model_json_schema(mode="serialization")}
        },
    }


def trusted_source(request: Request, public_origin: str) -> bool:
    origins = request.headers.getlist("origin")
    if origins:
        return origins == [public_origin]
    referers = request.headers.getlist("referer")
    if len(referers) != 1:
        return False
    try:
        source = urlsplit(referers[0])
    except ValueError:
        return False
    return f"{source.scheme}://{source.netloc}" == public_origin


def secure_transport(request: Request, https: bool) -> bool:
    if https:
        return request.url.scheme == "https"
    if request.url.scheme != "http" or request.client is None:
        return False
    try:
        return ip_address(request.client.host).is_loopback
    except ValueError:
        return False


def setup_router(service: SetupService, public_origin: str) -> APIRouter:
    router = APIRouter(tags=["auth"])
    csrf = PreAuthCsrf()
    secure = public_origin.startswith("https://")
    cookie_name = "__Host-acbooks-preauth" if secure else "acbooks-preauth"

    @router.get(
        "/setup/status",
        operation_id="getSetupStatus",
        response_model=SetupStatus,
        responses={200: {"headers": no_store}, 500: problem(500)},
    )
    def get_setup_status(response: Response) -> SetupStatus:
        response.headers["Cache-Control"] = "no-store"
        return SetupStatus(initialized=service.initialized())

    @router.get(
        "/auth/csrf",
        operation_id="getCsrfToken",
        response_model=CsrfResponse,
        responses={
            200: {"headers": {**no_store, "Set-Cookie": {"schema": {"type": "string"}}}},
            403: problem(403),
        },
    )
    def get_csrf_token(request: Request, response: Response) -> CsrfResponse:
        if not secure_transport(request, secure):
            raise HTTPException(403)
        challenge, token = csrf.issue()
        response.set_cookie(
            cookie_name,
            challenge,
            max_age=CHALLENGE_TTL,
            httponly=True,
            secure=secure,
            samesite="strict",
        )
        response.headers["Cache-Control"] = "no-store"
        return CsrfResponse(csrf_token=token)

    @router.post(
        "/setup",
        status_code=201,
        operation_id="initializeSetup",
        response_model=AccountResponse,
        responses={
            201: {
                "headers": {
                    **no_store,
                    "Location": {"schema": {"type": "string", "format": "uri-reference"}},
                }
            },
            **{status: problem(status) for status in (403, 409, 422, 429, 500)},
        },
    )
    def initialize(
        body: SetupRequest,
        request: Request,
        response: Response,
        token: Annotated[str | None, Depends(csrf_header)],
    ) -> AccountResponse:
        request_id = request.state.request_id
        if (
            not secure_transport(request, secure)
            or not trusted_source(request, public_origin)
            or token is None
            or not csrf.valid(request.cookies.get(cookie_name, ""), token)
        ):
            logger.info("request_id=%s operation=setup result=csrf_rejected", request_id)
            raise HTTPException(403)
        try:
            account_id = service.initialize(
                body.username, body.password.get_secret_value(), body.setup_code.get_secret_value()
            )
        except BusinessError as error:
            logger.info("request_id=%s operation=setup result=%s", request_id, error.code)
            raise
        logger.info("request_id=%s operation=setup result=created", request_id)
        response.headers["Location"] = "/api/v1/auth/me"
        response.headers["Cache-Control"] = "no-store"
        response.delete_cookie(cookie_name, httponly=True, secure=secure, samesite="strict")
        return AccountResponse(id=account_id, username=body.username)

    return router
