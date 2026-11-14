from typing import Any

from sqlalchemy import Engine

from app.core.config import Settings
from app.modules.imports.service import ScanService
from app.modules.metadata.service import MetadataService
from app.workers.queue import ClaimedJob


def run_job(engine: Engine, job: ClaimedJob) -> dict[str, Any]:
    if job.type == "library_scan":
        root_id = job.input.get("rootId")
        if not isinstance(root_id, str):
            raise ValueError("Invalid scan payload.")
        deep = bool(job.input.get("deep", False))
        return ScanService(engine).run(root_id, deep)
    if job.type == "metadata_fill":
        service = MetadataService(engine, Settings().covers_dir)
        if job.input.get("automatic") and not service.settings().auto_fill:
            return {"items": []}
        book_ids = job.input.get("bookIds")
        if not isinstance(book_ids, list) or not all(isinstance(key, str) for key in book_ids):
            raise ValueError("Invalid metadata payload.")
        return service.batch(book_ids, automatic=bool(job.input.get("automatic")))
    raise ValueError(f"Unknown job type: {job.type}")