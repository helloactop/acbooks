import os
from pathlib import Path
from uuid import UUID, uuid4

from sqlalchemy import Engine

from app.core.errors import BusinessError, BusinessErrorCode
from app.db.models.storage import StorageRoot
from app.db.session import create_session_factory
from app.modules.library import repository
from app.modules.library.schemas import (
    StorageRootCreate,
    StorageRootNode,
)


def _overlaps(left: Path, right: Path) -> bool:
    return left == right or left in right.parents or right in left.parents


class LibraryService:
    def __init__(self, engine: Engine, protected_paths: list[Path]) -> None:
        self.read_sessions = create_session_factory(engine)
        self.write_sessions = create_session_factory(
            engine.execution_options(sqlite_begin_immediate=True)
        )
        self.protected = [path.resolve() for path in protected_paths]

    def list_roots(self) -> list[StorageRootNode]:
        with self.read_sessions() as session:
            counts = repository.file_counts(session)
            return [
                self._node(root, counts.get(root.id, 0))
                for root in repository.list_roots(session)
            ]

    def create_root(self, payload: StorageRootCreate) -> StorageRootNode:
        raw = Path(payload.root_path).expanduser()
        if not raw.is_absolute():
            raise BusinessError(BusinessErrorCode.STORAGE_ROOT_INVALID)
        try:
            resolved = raw.resolve(strict=True)
        except OSError as error:
            raise BusinessError(BusinessErrorCode.STORAGE_ROOT_INVALID) from error
        if not resolved.is_dir() or not os.access(resolved, os.R_OK):
            raise BusinessError(BusinessErrorCode.STORAGE_ROOT_INVALID)
        for protected in self.protected:
            if _overlaps(resolved, protected):
                raise BusinessError(BusinessErrorCode.STORAGE_ROOT_OVERLAP)
        with self.write_sessions.begin() as session:
            for existing in repository.root_paths(session):
                if _overlaps(resolved, Path(existing)):
                    raise BusinessError(BusinessErrorCode.STORAGE_ROOT_OVERLAP)
            root = StorageRoot(
                id=str(uuid4()),
                name=payload.name,
                root_path=str(resolved),
                mode=payload.mode,
            )
            repository.add_root(session, root)
            return self._node(root, 0)

    def delete_root(self, root_id: UUID) -> None:
        key = str(root_id)
        with self.write_sessions.begin() as session:
            if repository.get_root(session, key) is None:
                raise BusinessError(BusinessErrorCode.STORAGE_ROOT_NOT_FOUND)
            if repository.has_objects(session, key):
                raise BusinessError(BusinessErrorCode.STORAGE_ROOT_IN_USE)
            repository.delete_root(session, key)

    @staticmethod
    def _node(root: StorageRoot, file_count: int) -> StorageRootNode:
        return StorageRootNode(
            id=UUID(root.id),
            name=root.name,
            root_path=root.root_path,
            mode=root.mode,  # type: ignore[arg-type]
            file_count=file_count,
        )