from typing import TypeVar
from uuid import uuid4

from sqlalchemy import Select, func, select
from sqlalchemy.orm import Session

from app.db.models.reading import Annotation, Bookmark, ReadingPosition, ReadingSession
from app.db.models.sync import Device, SyncChange, SyncMutation

Record = TypeVar("Record", ReadingSession, Bookmark, Annotation)


def list_records(
    session: Session, statement: Select[Record], limit: int, offset: int
) -> tuple[list[Record], int]:
    total = (
        session.scalar(select(func.count()).select_from(statement.order_by(None).subquery())) or 0
    )
    return list(session.scalars(statement.limit(limit).offset(offset))), total


def get_position(session: Session, file_id: str) -> ReadingPosition | None:
    return session.scalar(select(ReadingPosition).where(ReadingPosition.book_file_id == file_id))


def record_mutation(session: Session, device_id: str, mutation_id: str, now: int) -> bool:
    if (
        session.scalar(
            select(SyncMutation.id).where(
                SyncMutation.device_id == device_id, SyncMutation.mutation_id == mutation_id
            )
        )
        is not None
    ):
        return False
    device = session.get(Device, device_id)
    if device is None:
        session.add(Device(id=device_id, name="Reader", last_seen_at=now))
        session.flush()
    else:
        device.last_seen_at = now
    session.add(
        SyncMutation(
            id=str(uuid4()),
            device_id=device_id,
            mutation_id=mutation_id,
            created_at=now,
        )
    )
    return True


def log_change(
    session: Session, entity_type: str, entity_id: str, operation: str, now: int
) -> None:
    session.add(
        SyncChange(
            entity_type=entity_type,
            entity_id=entity_id,
            operation=operation,
            changed_at=now,
        )
    )
