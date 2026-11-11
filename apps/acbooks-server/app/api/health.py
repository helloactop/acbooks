from typing import Literal

from fastapi import APIRouter
from pydantic import BaseModel

from app.api.errors import Problem

router = APIRouter(tags=["health"])
request_id_header = {"X-Request-ID": {"schema": {"type": "string"}, "required": True}}


class HealthResponse(BaseModel):
    status: Literal["ok"]


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
