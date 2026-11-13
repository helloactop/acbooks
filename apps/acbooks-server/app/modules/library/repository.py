from sqlalchemy import delete, func, select
from sqlalchemy.orm import Session

from app.db.models.storage import StorageObject, StorageRoot


def get_root(session: Session, root_id: str) -> StorageRoot | None:
    return session.get(StorageRoot, root_id)


def list_roots(session: Session) -> list[StorageRoot]:
    return list(
        session.scalars(
            select(StorageRoot).order_by(
                StorageRoot.name.collate("NOCASE"), StorageRoot.id
            )
        ).all()
    )


def root_paths(session: Session) -> list[str]:
    return [str(path) for path in session.scalars(select(StorageRoot.root_path)).all()]


def file_counts(session: Session) -> dict[str, int]:
    return {
        root_id: int(total)
        for root_id, total in session.execute(
            select(StorageObject.root_id, func.count()).group_by(StorageObject.root_id)
        ).all()
    }


def has_objects(session: Session, root_id: str) -> bool:
    return (
        session.scalar(
            select(StorageObject.id).where(StorageObject.root_id == root_id).limit(1)
        )
        is not None
    )


def add_root(session: Session, root: StorageRoot) -> None:
    session.add(root)
    session.flush()


def delete_root(session: Session, root_id: str) -> None:
    session.execute(delete(StorageRoot).where(StorageRoot.id == root_id))