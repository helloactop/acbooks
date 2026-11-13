from typing import Any

from sqlalchemy import Engine

from app.modules.imports.service import ScanService
from app.workers.queue import ClaimedJob


def run_job(engine: Engine, job: ClaimedJob) -> dict[str, Any]:
    if job.type == "library_scan":
        root_id = job.input.get("rootId")
        if not isinstance(root_id, str):
            raise ValueError("Invalid scan payload.")
        deep = bool(job.input.get("deep", False))
        return ScanService(engine).run(root_id, deep)
    raise ValueError(f"Unknown job type: {job.type}")