from pathlib import Path

from starlette.exceptions import HTTPException
from starlette.responses import Response
from starlette.staticfiles import StaticFiles
from starlette.types import Scope


class SPAStaticFiles(StaticFiles):
    """Serve the built Web client, falling back to index.html for client routes."""

    async def get_response(self, path: str, scope: Scope) -> Response:
        normalized = path.replace("\\", "/")
        if normalized == "api" or normalized.startswith("api/"):
            raise HTTPException(404)
        try:
            response = await super().get_response(path, scope)
        except HTTPException as error:
            if error.status_code != 404 or Path(path).suffix:
                raise
            response = await super().get_response("index.html", scope)
        if Path(path).suffix in ("", ".html"):
            response.headers["Cache-Control"] = "no-cache"
        return response
