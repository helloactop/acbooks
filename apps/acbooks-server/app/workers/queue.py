import json
from collections.abc import Callable
from dataclasses import dataclass
from time import time_ns
from typing import Any, cast
from uuid import uuid4

from sqlalchemy import CursorResult, Engine, select, update
from sqlalchemy.orm import Session, sessionmaker

from app.db.models.jobs import BackgroundJob
from app.db.session import create_session_factory

MAX_ATTEMPTS = 3


@dataclass(frozen=True)
class ClaimedJob:
    id: str
    type: str
    input: dict[str, Any]


def _now() -> int:
    return time_ns() // 1_000_000


def _write(engine: Engine) -> sessionmaker[Session]:
    return create_session_factory(engine.execution_options(sqlite_begin_immediate=True))


def enqueue(engine: Engine, job_type: str, input_payload: dict[str, Any]) -> str:
    with _write(engine).begin() as session:
        return add_job(session, job_type, input_payload)


def add_job(session: Session, job_type: str, input_payload: dict[str, Any]) -> str:
    job_id = str(uuid4())
    now = _now()
    payload = {"input": input_payload, "runtime": {"attempt": 0}}
    session.add(
        BackgroundJob(
            id=job_id,
            type=job_type,
            status="queued",
            payload_json=json.dumps(payload),
            created_at=now,
            updated_at=now,
        )
    )
    return job_id


def queued_for(
    engine: Engine,
    job_type: str,
    predicate: Callable[[dict[str, Any]], bool],
) -> str | None:
    with create_session_factory(engine)() as session:
        jobs = session.scalars(
            select(BackgroundJob).where(
                BackgroundJob.type == job_type,
                BackgroundJob.status.in_(("queued", "running")),
            )
        ).all()
    for job in jobs:
        data: Any = json.loads(job.payload_json)
        candidate = data.get("input") if isinstance(data, dict) else None
        if isinstance(candidate, dict) and predicate(candidate):
            return job.id
    return None


def claim_next(engine: Engine) -> ClaimedJob | None:
    with _write(engine).begin() as session:
        row = session.scalars(
            select(BackgroundJob)
            .where(BackgroundJob.status == "queued")
            .order_by(BackgroundJob.created_at, BackgroundJob.id)
            .limit(1)
        ).first()
        if row is None:
            return None
        result = cast(
            CursorResult[Any],
            session.execute(
                update(BackgroundJob)
                .where(
                    BackgroundJob.id == row.id,
                    BackgroundJob.status == "queued",
                )
                .values(status="running", updated_at=_now())
            ),
        )
        if result.rowcount != 1:
            return None
        data: Any = json.loads(row.payload_json)
        candidate = data.get("input") if isinstance(data, dict) else None
        return ClaimedJob(
            id=row.id,
            type=row.type,
            input=candidate if isinstance(candidate, dict) else {},
        )


def finish(
    engine: Engine,
    job_id: str,
    status: str,
    *,
    result: dict[str, Any] | None = None,
    error_code: str | None = None,
) -> None:
    with _write(engine).begin() as session:
        row = session.get(BackgroundJob, job_id)
        if row is None:
            return
        data: Any = json.loads(row.payload_json)
        if not isinstance(data, dict):
            data = {}
        runtime = data.get("runtime")
        if not isinstance(runtime, dict):
            runtime = {}
            data["runtime"] = runtime
        if result is not None:
            runtime["result"] = result
        if error_code is not None:
            runtime["errorCode"] = error_code
        row.payload_json = json.dumps(data)
        row.status = status  # type: ignore[assignment]
        row.updated_at = _now()


def recover(engine: Engine) -> None:
    with _write(engine).begin() as session:
        rows = session.scalars(select(BackgroundJob).where(BackgroundJob.status == "running")).all()
        for row in rows:
            data: Any = json.loads(row.payload_json)
            if not isinstance(data, dict):
                data = {}
            runtime = data.get("runtime")
            if not isinstance(runtime, dict):
                runtime = {}
                data["runtime"] = runtime
            attempt_value = runtime.get("attempt", 0)
            attempt = (attempt_value if isinstance(attempt_value, int) else 0) + 1
            runtime["attempt"] = attempt
            row.payload_json = json.dumps(data)
            row.status = "failed" if attempt > MAX_ATTEMPTS else "queued"
            row.updated_at = _now()
