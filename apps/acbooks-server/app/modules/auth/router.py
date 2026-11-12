import logging
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, Response

from app.api.errors import problem_openapi
from app.core.errors import BusinessError
from app.modules.auth.dependencies import AuthDependencies
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
no_store = {"Cache-Control": {"schema": {"type": "string", "const": "no-store"}}}
cookie_headers = {**no_store, "Set-Cookie": {"schema": {"type": "string"}}}


def auth_router(
    setup: SetupService, auth: WebAuthService, dependencies: AuthDependencies
) -> APIRouter:
    router = APIRouter(tags=["auth"])
    secure = dependencies.secure

    @router.get(
        "/setup/status",
        operation_id="getSetupStatus",
        response_model=SetupStatus,
        responses={200: {"headers": no_store}, 500: problem_openapi(500)},
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
            403: problem_openapi(403),
            500: problem_openapi(500),
        },
    )
    def get_csrf_token(request: Request, response: Response) -> CsrfResponse:
        return dependencies.issue_csrf(request, response)

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
            **{
                status: problem_openapi(status)
                for status in (403, 409, 422, 429, 500)
            },
        },
    )
    def initialize(
        body: SetupRequest,
        request: Request,
        response: Response,
        token: Annotated[str | None, Depends(dependencies.csrf_header)],
    ) -> AccountResponse:
        request_id = request.state.request_id
        current = auth.authenticate(request.cookies.get(dependencies.session_cookie))
        dependencies.check_csrf(request, token, current, "setup")
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
        response.delete_cookie(
            dependencies.challenge_cookie,
            httponly=True,
            secure=secure,
            samesite="strict",
        )
        return AccountResponse(id=account_id, username=body.username)

    @router.post(
        "/auth/login",
        operation_id="login",
        response_model=LoginResponse,
        responses={
            200: {"headers": cookie_headers},
            **{
                status: problem_openapi(status)
                for status in (401, 403, 422, 429, 500)
            },
        },
    )
    def login(
        body: LoginRequest,
        request: Request,
        response: Response,
        token: Annotated[str | None, Depends(dependencies.csrf_header)],
    ) -> LoginResponse:
        request_id = request.state.request_id
        previous = auth.authenticate(request.cookies.get(dependencies.session_cookie))
        dependencies.check_csrf(request, token, previous, "login")
        try:
            identity, raw = auth.login(
                body.username, body.password.get_secret_value(), previous
            )
        except BusinessError as error:
            logger.info("request_id=%s operation=login result=%s", request_id, error.code)
            raise
        response.headers["Cache-Control"] = "no-store"
        response.set_cookie(
            dependencies.session_cookie,
            raw,
            max_age=auth.session_ttl,
            httponly=True,
            secure=secure,
            samesite="strict",
        )
        response.delete_cookie(
            dependencies.challenge_cookie,
            httponly=True,
            secure=secure,
            samesite="strict",
        )
        logger.info("request_id=%s operation=login result=authenticated", request_id)
        return LoginResponse(
            account=AccountResponse(id=identity.account_id, username=identity.username)
        )

    @router.get(
        "/auth/me",
        operation_id="getCurrentAccount",
        response_model=AccountResponse,
        responses={
            200: {"headers": no_store},
            **{status: problem_openapi(status) for status in (401, 403, 500)},
        },
    )
    def get_current_account(
        response: Response,
        current: Annotated[WebSession, Depends(dependencies.require_session)],
    ) -> AccountResponse:
        response.headers["Cache-Control"] = "no-store"
        return AccountResponse(id=current.account_id, username=current.username)

    @router.post(
        "/auth/logout",
        operation_id="logout",
        status_code=204,
        response_class=Response,
        responses={
            204: {"headers": cookie_headers},
            **{status: problem_openapi(status) for status in (401, 403, 500)},
        },
        openapi_extra={
            "parameters": [
                {
                    "name": "X-CSRF-Token",
                    "in": "header",
                    "required": True,
                    "schema": {"type": "string"},
                }
            ]
        },
    )
    def logout(
        request: Request,
        current: Annotated[WebSession, Depends(dependencies.require_session)],
    ) -> Response:
        dependencies.check_csrf(
            request,
            request.headers.get(dependencies.csrf_header_name),
            current,
            "logout",
        )
        if not auth.logout(current):
            raise HTTPException(401)
        response = Response(status_code=204, headers={"Cache-Control": "no-store"})
        response.delete_cookie(
            dependencies.session_cookie,
            httponly=True,
            secure=secure,
            samesite="strict",
        )
        logger.info(
            "request_id=%s operation=logout result=revoked",
            request.state.request_id,
        )
        return response

    return router
