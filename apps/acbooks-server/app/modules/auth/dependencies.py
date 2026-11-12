import logging
from collections.abc import Callable
from ipaddress import ip_address
from secrets import token_urlsafe
from typing import Annotated
from urllib.parse import urlsplit

from fastapi import Depends, HTTPException, Request, Response
from fastapi.security import APIKeyCookie, APIKeyHeader

from app.modules.auth.csrf import CHALLENGE_TTL, CsrfScope, CsrfTokens
from app.modules.auth.schemas import CsrfResponse
from app.modules.auth.service import WebAuthService, WebSession

logger = logging.getLogger("uvicorn.error")
CSRF_HEADER_NAME = "X-CSRF-Token"


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


class AuthDependencies:
    def __init__(
        self,
        auth: WebAuthService,
        public_origin: str,
        csrf: CsrfTokens,
    ) -> None:
        self.auth = auth
        self.public_origin = public_origin
        self.csrf = csrf
        self.secure = public_origin.startswith("https://")
        prefix = "__Host-" if self.secure else ""
        self.challenge_cookie = prefix + "acbooks-preauth"
        self.session_cookie = prefix + "acbooks-session"
        self.csrf_header_name = CSRF_HEADER_NAME
        self.cookie_auth = APIKeyCookie(
            name=self.session_cookie, scheme_name="WebSession", auto_error=False
        )
        self.csrf_header = APIKeyHeader(name=self.csrf_header_name, auto_error=False)
        self.require_session = self._session_dependency()
        self.require_write = self._write_dependency()

    def _session_dependency(self) -> Callable[..., WebSession]:
        def require_session(
            request: Request,
            raw: Annotated[str | None, Depends(self.cookie_auth)],
        ) -> WebSession:
            if not secure_transport(request, self.secure):
                raise HTTPException(403)
            current = self.auth.authenticate(raw)
            if current is None:
                raise HTTPException(401)
            return current

        return require_session

    def _write_dependency(self) -> Callable[..., WebSession]:
        def require_write(
            request: Request,
            current: Annotated[WebSession, Depends(self.require_session)],
            token: Annotated[str | None, Depends(self.csrf_header)],
        ) -> WebSession:
            self.check_csrf(
                request, token, current, f"{request.method} {request.url.path}"
            )
            return current

        return require_write

    def check_csrf(
        self,
        request: Request,
        token: str | None,
        current: WebSession | None,
        operation: str,
    ) -> None:
        scope: CsrfScope = "session" if current is not None else "preauth"
        binding = (
            current.id
            if current is not None
            else request.cookies.get(self.challenge_cookie, "")
        )
        if (
            not secure_transport(request, self.secure)
            or not trusted_source(request, self.public_origin)
            or token is None
            or not self.csrf.valid(scope, binding, token)
        ):
            logger.info(
                "request_id=%s operation=%s result=csrf_rejected",
                request.state.request_id,
                operation,
            )
            raise HTTPException(403)

    def issue_csrf(self, request: Request, response: Response) -> CsrfResponse:
        if not secure_transport(request, self.secure):
            raise HTTPException(403)
        response.headers["Cache-Control"] = "no-store"
        current = self.auth.authenticate(request.cookies.get(self.session_cookie))
        if current is not None:
            return CsrfResponse(csrf_token=self.csrf.issue("session", current.id))
        challenge = token_urlsafe(32)
        response.set_cookie(
            self.challenge_cookie,
            challenge,
            max_age=CHALLENGE_TTL,
            httponly=True,
            secure=self.secure,
            samesite="strict",
        )
        return CsrfResponse(csrf_token=self.csrf.issue("preauth", challenge))
