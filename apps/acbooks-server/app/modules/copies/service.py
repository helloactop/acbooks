from uuid import UUID, uuid4

from sqlalchemy import Engine
from sqlalchemy.orm import Session

from app.core.errors import BusinessError, BusinessErrorCode
from app.db.models.copies import BookCopy, Location
from app.db.session import create_session_factory
from app.modules.books.schemas import BookCopy as BookCopyDetail
from app.modules.books.schemas import BookCopyCreate
from app.modules.copies import repository
from app.modules.copies.schemas import (
    BookCopyUpdate,
    LocationCreate,
    LocationNode,
    LocationUpdate,
)


class CopiesService:
    def __init__(self, engine: Engine) -> None:
        self.read_sessions = create_session_factory(engine)
        self.write_sessions = create_session_factory(
            engine.execution_options(sqlite_begin_immediate=True)
        )

    def list_locations(self) -> list[LocationNode]:
        with self.read_sessions() as session:
            counts = repository.location_copy_counts(session)
            return [
                self._location_node(location, counts)
                for location in repository.list_locations(session)
            ]

    def create_location(self, payload: LocationCreate) -> LocationNode:
        with self.write_sessions.begin() as session:
            parent_id = (
                str(payload.parent_id) if payload.parent_id is not None else None
            )
            if (
                parent_id is not None
                and repository.get_location(session, parent_id) is None
            ):
                raise BusinessError(BusinessErrorCode.LOCATION_NOT_FOUND)
            location = Location(
                id=str(uuid4()), parent_id=parent_id, name=payload.name
            )
            repository.add_location(session, location)
            return self._location_node(location, {})

    def update_location(
        self, location_id: UUID, payload: LocationUpdate
    ) -> LocationNode:
        fields = payload.model_fields_set
        key = str(location_id)
        with self.write_sessions.begin() as session:
            location = repository.get_location(session, key)
            if location is None:
                raise BusinessError(BusinessErrorCode.LOCATION_NOT_FOUND)
            if "name" in fields:
                assert payload.name is not None
                location.name = payload.name
            if "parent_id" in fields:
                parent_id = (
                    str(payload.parent_id) if payload.parent_id is not None else None
                )
                if parent_id is not None:
                    if repository.get_location(session, parent_id) is None:
                        raise BusinessError(BusinessErrorCode.LOCATION_NOT_FOUND)
                    if parent_id == key or self._creates_cycle(
                        session, key, parent_id
                    ):
                        raise BusinessError(BusinessErrorCode.LOCATION_CYCLE)
                location.parent_id = parent_id
            session.flush()
            return self._location_node(
                location, repository.location_copy_counts(session)
            )

    def delete_location(self, location_id: UUID) -> None:
        key = str(location_id)
        with self.write_sessions.begin() as session:
            if repository.get_location(session, key) is None:
                raise BusinessError(BusinessErrorCode.LOCATION_NOT_FOUND)
            if repository.has_children(session, key):
                raise BusinessError(BusinessErrorCode.LOCATION_HAS_CHILDREN)
            # book_copies.location_id is ON DELETE SET NULL, so copies survive.
            repository.delete_location(session, key)

    def list_copies(self, book_id: UUID) -> list[BookCopyDetail]:
        key = str(book_id)
        with self.read_sessions() as session:
            if not repository.book_exists(session, key):
                raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)
            return [
                self._copy_detail(copy, location_name)
                for copy, location_name in repository.list_book_copies(session, key)
            ]

    def create_copy(self, book_id: UUID, payload: BookCopyCreate) -> BookCopyDetail:
        key = str(book_id)
        with self.write_sessions.begin() as session:
            if not repository.book_exists(session, key):
                raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)
            copy = self._add_copy(session, key, payload)
            return self._copy_detail(
                copy, self._location_name(session, copy.location_id)
            )

    def update_copy(
        self, book_id: UUID, copy_id: UUID, payload: BookCopyUpdate
    ) -> BookCopyDetail:
        fields = payload.model_fields_set
        with self.write_sessions.begin() as session:
            copy = repository.get_copy(session, str(copy_id))
            if copy is None or copy.book_id != str(book_id):
                raise BusinessError(BusinessErrorCode.COPY_NOT_FOUND)
            if "location_id" in fields:
                location_id = (
                    str(payload.location_id)
                    if payload.location_id is not None
                    else None
                )
                if (
                    location_id is not None
                    and repository.get_location(session, location_id) is None
                ):
                    raise BusinessError(BusinessErrorCode.LOCATION_NOT_FOUND)
                copy.location_id = location_id
            if "acquired_at" in fields:
                copy.acquired_at = payload.acquired_at
            if "note" in fields:
                copy.note = payload.note
            session.flush()
            return self._copy_detail(
                copy, self._location_name(session, copy.location_id)
            )

    def delete_copy(self, book_id: UUID, copy_id: UUID) -> None:
        with self.write_sessions.begin() as session:
            copy = repository.get_copy(session, str(copy_id))
            if copy is None or copy.book_id != str(book_id):
                raise BusinessError(BusinessErrorCode.COPY_NOT_FOUND)
            repository.delete_copy(session, str(copy_id))

    def create_copies(
        self, session: Session, book_id: str, payloads: list[BookCopyCreate]
    ) -> None:
        # BooksService owns the transaction when creating a book with inline copies.
        for payload in payloads:
            self._add_copy(session, book_id, payload)

    def _add_copy(
        self, session: Session, book_id: str, payload: BookCopyCreate
    ) -> BookCopy:
        location_id = (
            str(payload.location_id) if payload.location_id is not None else None
        )
        if (
            location_id is not None
            and repository.get_location(session, location_id) is None
        ):
            raise BusinessError(BusinessErrorCode.LOCATION_NOT_FOUND)
        copy = BookCopy(
            id=str(uuid4()),
            book_id=book_id,
            location_id=location_id,
            acquired_at=payload.acquired_at,
            note=payload.note,
        )
        repository.add_copy(session, copy)
        return copy

    @staticmethod
    def _creates_cycle(
        session: Session, location_id: str, parent_id: str
    ) -> bool:
        edges = repository.location_edges(session)
        current: str | None = parent_id
        seen: set[str] = set()
        while current is not None and current not in seen:
            if current == location_id:
                return True
            seen.add(current)
            current = edges.get(current)
        return False

    @staticmethod
    def _location_name(session: Session, location_id: str | None) -> str | None:
        if location_id is None:
            return None
        location = repository.get_location(session, location_id)
        return location.name if location is not None else None

    @staticmethod
    def _location_node(location: Location, counts: dict[str, int]) -> LocationNode:
        return LocationNode(
            id=UUID(location.id),
            parent_id=(
                UUID(location.parent_id) if location.parent_id is not None else None
            ),
            name=location.name,
            copy_count=counts.get(location.id, 0),
        )

    @staticmethod
    def _copy_detail(copy: BookCopy, location_name: str | None) -> BookCopyDetail:
        return BookCopyDetail(
            id=UUID(copy.id),
            location_id=(
                UUID(copy.location_id) if copy.location_id is not None else None
            ),
            location_name=location_name,
            acquired_at=copy.acquired_at,
            note=copy.note,
        )