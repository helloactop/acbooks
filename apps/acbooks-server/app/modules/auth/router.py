import logging
from http import HTTPStatus
from ipaddress import ip_address
from secrets import token_urlsafe
from typing import Annotated, Any
from urllib.parse import urlsplit

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from fastapi.security import APIKeyCookie, APIKeyHeader

from app.api.errors import Problem
from app.core.errors import BusinessError
from app.modules.auth.csrf import CHALLENGE_TTL, CsrfScope, CsrfTokens
from app.modules.auth.schemas import (
    AccountResponse,
    CsrfResponse,
    LoginRequest,
    LoginResponse,
    SetupRequest,
    SetupStatus,
)
from app.modules.auth.service import SetupService, WebAuthService, WebSession

logger = logging.getLogger("uvicorn.error")
CSRF_HEADER_NAME = "X-CSRF-Token"
csrf_header = APIKeyHeader(name=CSRF_HEADER_NAME, auto_error=False)
no_store = {"Cache-Control": {"schema": {"type": "string", "const": "no-store"}}}
cookie_headers = {**no_store, "Set-Cookie": {"schema": {"type": "string"}}}


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


def auth_router(setup: SetupService, auth: WebAuthService, public_origin: str) -> APIRouter:
    router = APIRouter(tags=["auth"])
    csrf = CsrfTokens()
    secure = public_origin.startswith("https://")
    prefix = "__Host-" if secure else ""
    challenge_cookie = prefix + "acbooks-preauth"
    session_cookie = prefix + "acbooks-session"
    cookie_auth = APIKeyCookie(name=session_cookie, scheme_name="WebSession", auto_error=False)

    def require_session(
        request: Request, raw: Annotated[str | None, Depends(cookie_auth)]
    ) -> WebSession:
        if not secure_transport(request, secure):
            raise HTTPException(403)
        current = auth.authenticate(raw)
        if current is None:
            raise HTTPException(401)
        return current

    def require_csrf(
        request: Request, token: str | None, current: WebSession | None, operation: str
    ) -> None:
        scope: CsrfScope = "session" if current is not None else "preauth"
        binding = current.id if current is not None else request.cookies.get(challenge_cookie, "")
        if (
            not secure_transport(request, secure)
            or not trusted_source(request, public_origin)
            or token is None
            or not csrf.valid(scope, binding, token)
        ):
            logger.info(
                "request_id=%s operation=%s result=csrf_rejected",
                request.state.request_id,
                operation,
            )
            raise HTTPException(403)

    @router.get(
        "/setup/status",
        operation_id="getSetupStatus",
        response_model=SetupStatus,
        responses={200: {"headers": no_store}, 500: problem(500)},
    )
    def get_setup_status(response: Response) -> SetupStatus:
        response.headers["Cache-Control"] = "no-store"
        return SetupStatus(initialized=setup.initialized())

    @router.get(
        "/auth/csrf",
        operation_id="getCsrfToken",
        response_model=CsrfResponse,
        responses={
            200: {"headers": cookie_headers},
            403: problem(403),
            500: problem(500),
        },
    )
    def get_csrf_token(request: Request, response: Response) -> CsrfResponse:
        if not secure_transport(request, secure):
            raise HTTPException(403)
        response.headers["Cache-Control"] = "no-store"
        current = auth.authenticate(request.cookies.get(session_cookie))
        if current is not None:
            return CsrfResponse(csrf_token=csrf.issue("session", current.id))
        challenge = token_urlsafe(32)
        response.set_cookie(
            challenge_cookie,
            challenge,
            max_age=CHALLENGE_TTL,
            httponly=True,
            secure=secure,
            samesite="strict",
        )
        return CsrfResponse(csrf_token=csrf.issue("preauth", challenge))

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
        current = auth.authenticate(request.cookies.get(session_cookie))
        require_csrf(request, token, current, "setup")
        try:
            account_id = setup.initialize(
                body.username, body.password.get_secret_value(), body.setup_code.get_secret_value()
            )
        except BusinessError as error:
            logger.info("request_id=%s operation=setup result=%s", request_id, error.code)
            raise
        logger.info("request_id=%s operation=setup result=created", request_id)
        response.headers["Location"] = "/api/v1/auth/me"
        response.headers["Cache-Control"] = "no-store"
        response.delete_cookie(challenge_cookie, httponly=True, secure=secure, samesite="strict")
        return AccountResponse(id=account_id, username=body.username)

    @router.post(
        "/auth/login",
        operation_id="login",
        response_model=LoginResponse,
        responses={
            200: {"headers": cookie_headers},
            **{status: problem(status) for status in (401, 403, 422, 429, 500)},
        },
    )
    def login(
        body: LoginRequest,
        request: Request,
        response: Response,
        token: Annotated[str | None, Depends(csrf_header)],
    ) -> LoginResponse:
        request_id = request.state.request_id
        previous = auth.authenticate(request.cookies.get(session_cookie))
        require_csrf(request, token, previous, "login")
        try:
            identity, raw = auth.login(body.username, body.password.get_secret_value(), previous)
        except BusinessError as error:
            logger.info("request_id=%s operation=login result=%s", request_id, error.code)
            raise
        response.headers["Cache-Control"] = "no-store"
        response.set_cookie(
            session_cookie,
            raw,
            max_age=auth.session_ttl,
            httponly=True,
            secure=secure,
            samesite="strict",
        )
        response.delete_cookie(challenge_cookie, httponly=True, secure=secure, samesite="strict")
        logger.info("request_id=%s operation=login result=authenticated", request_id)
        return LoginResponse(
            account=AccountResponse(id=identity.account_id, username=identity.username)
        )

    @router.get(
        "/auth/me",
        operation_id="getCurrentAccount",
        response_model=AccountResponse,
        responses={200: {"headers": no_store}, **{s: problem(s) for s in (401, 403, 500)}},
    )
    def get_current_account(
        response: Response, current: Annotated[WebSession, Depends(require_session)]
    ) -> AccountResponse:
        response.headers["Cache-Control"] = "no-store"
        return AccountResponse(id=current.account_id, username=current.username)

    @router.post(
        "/auth/logout",
        operation_id="logout",
        status_code=204,
        response_class=Response,
        responses={204: {"headers": cookie_headers}, **{s: problem(s) for s in (401, 403, 500)}},
        openapi_extra={
            "parameters": [
                {
                    "name": CSRF_HEADER_NAME,
                    "in": "header",
                    "required": True,
                    "schema": {"type": "string"},
                }
            ]
        },
    )
    def logout(
        request: Request,
        current: Annotated[WebSession, Depends(require_session)],
    ) -> Response:
        require_csrf(request, request.headers.get(CSRF_HEADER_NAME), current, "logout")
        if not auth.logout(current):
            raise HTTPException(401)
        response = Response(status_code=204, headers={"Cache-Control": "no-store"})
        response.delete_cookie(session_cookie, httponly=True, secure=secure, samesite="strict")
        logger.info("request_id=%s operation=logout result=revoked", request.state.request_id)
        return response

    return router
