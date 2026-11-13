import logging
from typing import Literal

from fastapi import APIRouter, HTTPException, Response
from pydantic import BaseModel
from sqlalchemy import Engine, text
from sqlalchemy.exc import SQLAlchemyError

from app.api.errors import Problem, problem_openapi
from app.db.version import SCHEMA_HEAD_REVISION

logger = logging.getLogger("uvicorn.error")
request_id_header = {"X-Request-ID": {"schema": {"type": "string"}, "required": True}}


class HealthResponse(BaseModel):
    status: Literal["ok"]


class ReadinessResponse(BaseModel):
    status: Literal["ok"]
    migration: Literal["current"]


def health_router(engine: Engine) -> APIRouter:
    router = APIRouter(tags=["health"])

    @router.get(
        "/health",
        operation_id="getHealth",
        response_model=HealthResponse,
        responses={
            200: {"headers": request_id_header},
            500: {
                "description": "Internal Server Error",
                "headers": request_id_header,
                "content": {
                    "application/problem+json": {
                        "schema": Problem.model_json_schema(mode="serialization"),
                    },
                },
            },
        },
    )
    async def get_health() -> HealthResponse:
        return HealthResponse(status="ok")

    @router.get(
        "/ready",
        operation_id="getReadiness",
        response_model=ReadinessResponse,
        responses={
            200: {"headers": request_id_header},
            503: problem_openapi(503),
        },
    )
    def get_readiness(response: Response) -> ReadinessResponse:
        response.headers["Cache-Control"] = "no-store"
        try:
            with engine.connect() as connection:
                revision = connection.execute(
                    text("SELECT version_num FROM alembic_version")
                ).scalar_one_or_none()
        except SQLAlchemyError as error:
            logger.warning(
                "operation=readiness result=database_unavailable error=%s",
                type(error).__qualname__,
            )
            raise HTTPException(503) from None
        if revision != SCHEMA_HEAD_REVISION:
            logger.warning(
                "operation=readiness result=migration_mismatch current=%s expected=%s",
                revision,
                SCHEMA_HEAD_REVISION,
            )
            raise HTTPException(503)
        return ReadinessResponse(status="ok", migration="current")

    return router
