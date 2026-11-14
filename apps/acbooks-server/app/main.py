import logging
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from pathlib import Path
from time import perf_counter
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import Response
from starlette.middleware.gzip import DEFAULT_EXCLUDED_CONTENT_TYPES

from app.api.errors import internal_error, register_exception_handlers
from app.api.health import health_router
from app.api.webapp import SPAStaticFiles
from app.core.config import Settings
from app.db.session import DATABASE_PATH, create_sqlite_engine
from app.modules.auth.csrf import CsrfTokens
from app.modules.auth.dependencies import AuthDependencies
from app.modules.auth.router import auth_router
from app.modules.auth.service import SetupService, WebAuthService
from app.modules.auth.setup_code import SETUP_CODE_PATH
from app.modules.books.organization_router import organization_router
from app.modules.books.organization_service import BookOrganizationService
from app.modules.books.router import books_router
from app.modules.books.service import BooksService
from app.modules.copies.router import copies_router
from app.modules.copies.service import CopiesService
from app.modules.files.router import files_router
from app.modules.files.service import FilesService
from app.modules.imports.router import imports_router
from app.modules.library.router import library_router
from app.modules.library.service import LibraryService
from app.modules.metadata.router import metadata_router
from app.modules.metadata.service import MetadataService
from app.modules.reader.router import reader_router
from app.modules.reader.service import ReaderService

logger = logging.getLogger("uvicorn.error")


def create_app(
    settings: Settings | None = None,
    *,
    database_path: Path = DATABASE_PATH,
    setup_code_path: Path = SETUP_CODE_PATH,
    web_dist_path: Path | None = None,
    covers_dir: Path | None = None,
    library_dir: Path | None = None,
) -> FastAPI:
    settings = settings if settings is not None else Settings()
    web_dist = web_dist_path if web_dist_path is not None else settings.web_dist_path
    covers = covers_dir if covers_dir is not None else settings.covers_dir
    library = library_dir if library_dir is not None else settings.library_dir
    if web_dist is not None and not web_dist.is_dir():
        raise RuntimeError(f"Web build directory does not exist: {web_dist}")
    engine = create_sqlite_engine(database_path)

    @asynccontextmanager
    async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
        try:
            yield
        finally:
            engine.dispose()

    app = FastAPI(
        title="acbooks",
        docs_url="/docs" if settings.enable_docs else None,
        redoc_url=None,
        lifespan=lifespan,
    )
    register_exception_handlers(app)
    app.add_middleware(
        GZipMiddleware,
        minimum_size=1024,
        exclude_content_types=(
            *DEFAULT_EXCLUDED_CONTENT_TYPES,
            "application/pdf",
            "application/epub+zip",
            "application/octet-stream",
        ),
    )
    auth = WebAuthService(engine, settings.web_session_ttl_seconds)
    dependencies = AuthDependencies(
        auth, settings.public_origin, CsrfTokens()
    )
    app.include_router(health_router(engine), prefix="/api/v1")
    app.include_router(
        auth_router(
            SetupService(engine, setup_code_path),
            auth,
            dependencies,
        ),
        prefix="/api/v1",
    )
    app.include_router(
        organization_router(BookOrganizationService(engine), dependencies), prefix="/api/v1"
    )
    copies = CopiesService(engine)
    app.include_router(
        books_router(BooksService(engine, covers, copies), dependencies),
        prefix="/api/v1",
    )
    app.include_router(copies_router(copies, dependencies), prefix="/api/v1")
    metadata = MetadataService(engine, covers)
    app.include_router(metadata_router(engine, metadata, dependencies), prefix="/api/v1")
    files = FilesService(engine, library, metadata)
    app.include_router(files_router(files, dependencies), prefix="/api/v1")
    data_root = database_path.parent.parent
    library_service = LibraryService(
        engine,
        [
            database_path.parent,
            covers,
            data_root / "secrets",
            data_root / "backups",
        ],
    )
    app.include_router(
        library_router(library_service, dependencies), prefix="/api/v1"
    )
    app.include_router(imports_router(engine, dependencies, covers), prefix="/api/v1")
    app.include_router(reader_router(ReaderService(engine), dependencies), prefix="/api/v1")
    if web_dist is not None:
        app.mount("/", SPAStaticFiles(directory=web_dist, html=True), name="web")

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
