import json
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Path, Query, Response
from sqlalchemy import Engine

from app.api.errors import problem_openapi
from app.core.errors import BusinessError, BusinessErrorCode
from app.db.models.jobs import BackgroundJob
from app.db.models.storage import StorageRoot
from app.db.session import create_session_factory
from app.modules.auth.dependencies import AuthDependencies
from app.modules.auth.service import WebSession
from app.modules.imports.schemas import JobStatus, ScanAccepted
from app.workers import queue

no_store = {"Cache-Control": {"schema": {"type": "string", "const": "no-store"}}}


def imports_router(engine: Engine, dependencies: AuthDependencies) -> APIRouter:
    router = APIRouter(tags=["imports"])
    read_sessions = create_session_factory(engine)

    @router.post(
        "/storage-roots/{rootId}/scan",
        status_code=202,
        operation_id="startStorageScan",
        response_model=ScanAccepted,
        responses={
            202: {"headers": no_store},
            **{
                status: problem_openapi(status)
                for status in (401, 403, 404, 409, 500)
            },
        },
    )
    def start_storage_scan(
        root_id: Annotated[UUID, Path(alias="rootId")],
        response: Response,
        current: Annotated[WebSession, Depends(dependencies.require_write)],
        deep: Annotated[bool, Query()] = False,
    ) -> ScanAccepted:
        with read_sessions() as session:
            if session.get(StorageRoot, str(root_id)) is None:
                raise BusinessError(BusinessErrorCode.STORAGE_ROOT_NOT_FOUND)
        existing = queue.queued_for(
            engine,
            "library_scan",
            lambda data: data.get("rootId") == str(root_id),
        )
        job_id = existing or queue.enqueue(
            engine,
            "library_scan",
            {"rootId": str(root_id), "deep": deep},
        )
        response.headers["Cache-Control"] = "no-store"
        return ScanAccepted(job_id=UUID(job_id))

    @router.get(
        "/jobs/{jobId}",
        operation_id="getJob",
        response_model=JobStatus,
        responses={
            200: {"headers": no_store},
            **{status: problem_openapi(status) for status in (401, 404, 500)},
        },
    )
    def get_job(
        job_id: Annotated[UUID, Path(alias="jobId")],
        response: Response,
        current: Annotated[WebSession, Depends(dependencies.require_session)],
    ) -> JobStatus:
        with read_sessions() as session:
            row = session.get(BackgroundJob, str(job_id))
        if row is None:
            raise BusinessError(BusinessErrorCode.JOB_NOT_FOUND)
        data = json.loads(row.payload_json)
        runtime = data.get("runtime", {}) if isinstance(data, dict) else {}
        result = runtime.get("result") if isinstance(runtime, dict) else None
        error_code = runtime.get("errorCode") if isinstance(runtime, dict) else None
        response.headers["Cache-Control"] = "no-store"
        return JobStatus(
            id=UUID(row.id),
            type=row.type,
            status=row.status,
            created_at=row.created_at,
            updated_at=row.updated_at,
            result=result if isinstance(result, dict) else None,
            error_code=error_code if isinstance(error_code, str) else None,
        )

    return router