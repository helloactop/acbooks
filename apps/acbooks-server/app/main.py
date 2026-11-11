import logging
from collections.abc import Awaitable, Callable
from time import perf_counter
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.responses import Response

from app.api.errors import internal_error, register_exception_handlers
from app.api.health import router as health_router
from app.core.config import Settings

logger = logging.getLogger("uvicorn.error")


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings if settings is not None else Settings()
    app = FastAPI(
        title="acbooks",
        docs_url="/docs" if settings.enable_docs else None,
        redoc_url=None,
    )
    register_exception_handlers(app)
    app.include_router(health_router, prefix="/api/v1")

    @app.middleware("http")
    async def handle_request(
        request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        request_id = uuid4().hex
        request.state.request_id = request_id
        started = perf_counter()
        try:
            response = await call_next(request)
        except Exception as error:
            response = internal_error(request, error)
        response.headers["X-Request-ID"] = request_id
        logger.log(
            logging.ERROR if response.status_code >= 500 else logging.INFO,
            "request_id=%s status=%d duration_ms=%.2f",
            request_id,
            response.status_code,
            (perf_counter() - started) * 1000,
        )
        return response

    return app
