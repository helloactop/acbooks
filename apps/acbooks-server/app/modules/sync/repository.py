from collections.abc import Sequence
from uuid import uuid4

from sqlalchemy import func, select
from sqlalchemy.orm import Session, aliased

from app.db.models.sync import Device, SyncChange, SyncMutation


def head(session: Session) -> int:
    return session.scalar(select(func.max(SyncChange.seq))) or 0


def latest(session: Session, kind: str, key: str) -> SyncChange | None:
    return session.scalar(
        select(SyncChange)
        .where(SyncChange.entity_type == kind, SyncChange.entity_id == key)
        .order_by(SyncChange.seq.desc())
        .limit(1)
    )


def page(
    session: Session, after: int, through: int, limit: int, snapshot: bool
) -> Sequence[SyncChange]:
    statement = select(SyncChange).where(SyncChange.seq > after, SyncChange.seq <= through)
    newer = aliased(SyncChange)
    statement = statement.where(
        ~select(newer.seq)
        .where(
            newer.entity_id == SyncChange.entity_id,
            newer.entity_type == SyncChange.entity_type,
            newer.seq > SyncChange.seq,
        )
        .exists(),
    )
    if snapshot:
        statement = statement.where(SyncChange.operation == "upsert")
    return session.scalars(statement.order_by(SyncChange.seq).limit(limit)).all()


def register_device(session: Session, device_id: str, now: int) -> None:
    device = session.get(Device, device_id)
    if device is None:
        session.add(Device(id=device_id, name="Sync", last_seen_at=now))
    else:
        device.last_seen_at = now
    session.flush()


def receipt(session: Session, device_id: str, mutation_id: str) -> SyncMutation | None:
    return session.scalar(
        select(SyncMutation).where(
            SyncMutation.device_id == device_id,
            SyncMutation.mutation_id == mutation_id,
        )
    )


def store_receipt(
    session: Session,
    device_id: str,
    mutation_id: str,
    now: int,
    request_hash: str,
    receipt_json: str,
) -> None:
    session.add(
        SyncMutation(
            id=str(uuid4()),
            device_id=device_id,
            mutation_id=mutation_id,
            created_at=now,
            request_hash=request_hash,
            receipt_json=receipt_json,
        )
    )
