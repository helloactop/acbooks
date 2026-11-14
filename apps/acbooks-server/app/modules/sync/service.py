import json
from hashlib import sha256
from time import time_ns
from uuid import UUID

from pydantic import ValidationError
from sqlalchemy import Engine
from sqlalchemy.orm import Session

from app.core.errors import BusinessError, BusinessErrorCode
from app.db.models.reading import Annotation, Bookmark, ReadingSession
from app.db.session import create_session_factory
from app.modules.books.organization_service import BookOrganizationService
from app.modules.books.service import BooksService
from app.modules.files.service import FilesService
from app.modules.reader.schemas import (
    AnnotationCreate,
    BookmarkCreate,
    PositionWrite,
    SessionCreate,
)
from app.modules.reader.service import ReaderService
from app.modules.sync import repository
from app.modules.sync.projection import records
from app.modules.sync.schemas import (
    CreateBook,
    CreateSession,
    DeleteCatalogItem,
    DeleteReadingRecord,
    MoveFile,
    Mutation,
    MutationReceipt,
    SavePosition,
    SyncBatch,
    SyncBatchResult,
    SyncPage,
    SyncRecord,
    UpdateBook,
    UpdateSession,
    WriteAnnotation,
    WriteBookmark,
)


class InvalidCursor(Exception):
    pass


class SyncService:
    def __init__(
        self,
        engine: Engine,
        books: BooksService,
        reader: ReaderService,
        organization: BookOrganizationService,
    ) -> None:
        self.read = create_session_factory(engine)
        self.write = create_session_factory(engine.execution_options(sqlite_begin_immediate=True))
        self.books = books
        self.reader = reader
        self.organization = organization

    def pull(self, after: int, through: int | None, limit: int, snapshot: bool) -> SyncPage:
        with self.read() as session:
            head = repository.head(session)
            upper = head if through is None else through
            if after > upper or upper > head:
                raise InvalidCursor
            changes = repository.page(session, after, upper, limit + 1, snapshot)
            selected = changes[:limit]
            return SyncPage(
                items=records(session, self.books, selected),
                next_cursor=selected[-1].seq if len(changes) > limit else upper,
                through=upper,
                has_more=len(changes) > limit,
            )

    def submit(self, batch: SyncBatch) -> SyncBatchResult:
        device_id = str(batch.device_id)
        receipts = []
        for mutation in batch.mutations:
            now = time_ns() // 1_000_000
            with self.write.begin() as session:
                repository.register_device(session, device_id, now)
                receipts.append(self.submit_one(session, batch.device_id, mutation, now))
        return SyncBatchResult(receipts=receipts)

    def submit_one(
        self,
        session: Session,
        device_id: UUID,
        mutation: Mutation,
        now: int,
    ) -> MutationReceipt:
        mutation_id = str(mutation.mutation_id)
        canonical = json.dumps(mutation.model_dump(mode="json", exclude_unset=True), sort_keys=True)
        digest = sha256(canonical.encode()).hexdigest()
        saved = repository.receipt(session, str(device_id), mutation_id)
        if saved is not None:
            if saved.request_hash is None:
                return MutationReceipt(mutation_id=mutation.mutation_id, status="duplicate")
            if saved.request_hash != digest or saved.receipt_json is None:
                return MutationReceipt(
                    mutation_id=mutation.mutation_id,
                    status="rejected",
                    code="MUTATION_ID_REUSED",
                )
            return MutationReceipt.model_validate_json(saved.receipt_json)

        kind = mutation.action.split(".", 1)[0]
        key = str(mutation.entity_id)
        before = self.current(session, kind, key)
        creates = mutation.action.endswith(".create")
        missing = before is None or before.operation == "delete"
        if (
            mutation.base_revision != (before.revision if before else 0)
            or (creates and before is not None)
            or (missing and not creates and not isinstance(mutation, SavePosition))
        ):
            result = MutationReceipt(
                mutation_id=mutation.mutation_id,
                status="conflict",
                code=BusinessErrorCode.REVISION_CONFLICT,
                current=before,
            )
        else:
            try:
                with session.begin_nested():
                    self.apply(session, device_id, mutation)
                    session.flush()
                result = MutationReceipt(
                    mutation_id=mutation.mutation_id,
                    status="accepted",
                    current=self.current(session, kind, key),
                )
            except BusinessError as error:
                result = MutationReceipt(
                    mutation_id=mutation.mutation_id,
                    status="rejected",
                    code=error.code,
                )
            except ValidationError:
                result = MutationReceipt(
                    mutation_id=mutation.mutation_id,
                    status="rejected",
                    code="VALIDATION_ERROR",
                )
        repository.store_receipt(
            session, str(device_id), mutation_id, now, digest, result.model_dump_json()
        )
        return result

    def current(self, session: Session, kind: str, key: str) -> SyncRecord | None:
        change = repository.latest(session, kind, key)
        return records(session, self.books, [change])[0] if change is not None else None

    def apply(self, session: Session, device_id: UUID, mutation: Mutation) -> None:
        key = mutation.entity_id
        metadata = {"device_id": device_id, "mutation_id": mutation.mutation_id}
        if isinstance(mutation, CreateBook):
            self.books.create_book(mutation.data, transaction=session, entity_id=key)
        elif isinstance(mutation, UpdateBook):
            self.books.update_book(key, mutation.data, transaction=session)
        elif isinstance(mutation, MoveFile):
            self.organization.reassociate(key, mutation.data, transaction=session)
        elif isinstance(mutation, DeleteCatalogItem):
            if mutation.action == "book.delete":
                self.books.delete_records(session, [str(key)])
            elif mutation.action == "reading_position.delete":
                self.reader.delete_position(session, key)
            else:
                FilesService.unlink(session, str(key))
        elif isinstance(mutation, SavePosition):
            current = self.reader.get_position(key, transaction=session)
            payload = PositionWrite.model_validate(
                {
                    **mutation.data.model_dump(),
                    **metadata,
                    "revision": current.revision if current else 0,
                }
            )
            self.reader.save_position(key, payload, transaction=session)
        elif isinstance(mutation, CreateSession):
            session_payload = SessionCreate.model_validate(
                {**mutation.data.model_dump(), **metadata}
            )
            self.reader.create_session(
                mutation.book_id, session_payload, transaction=session, item_id=key
            )
        elif isinstance(mutation, UpdateSession):
            self.reader.update_session(mutation.book_id, key, mutation.data, transaction=session)
        elif isinstance(mutation, WriteBookmark):
            if mutation.action == "bookmark.create":
                payload_mark = BookmarkCreate.model_validate(
                    {
                        **mutation.data.model_dump(exclude_unset=True),
                        **metadata,
                    }
                )
                self.reader.create_mark(
                    Bookmark,
                    mutation.book_id,
                    payload_mark,
                    transaction=session,
                    item_id=key,
                )
            else:
                self.reader.update_mark(
                    Bookmark, mutation.book_id, key, mutation.data, transaction=session
                )
        elif isinstance(mutation, WriteAnnotation):
            if mutation.action == "annotation.create":
                payload_note = AnnotationCreate.model_validate(
                    {
                        **mutation.data.model_dump(exclude_unset=True),
                        **metadata,
                    }
                )
                self.reader.create_mark(
                    Annotation,
                    mutation.book_id,
                    payload_note,
                    transaction=session,
                    item_id=key,
                )
            else:
                self.reader.update_mark(
                    Annotation, mutation.book_id, key, mutation.data, transaction=session
                )
        elif isinstance(mutation, DeleteReadingRecord):
            if mutation.action == "reading_session.delete":
                self.reader.delete(ReadingSession, mutation.book_id, key, transaction=session)
            elif mutation.action == "bookmark.delete":
                self.reader.delete(Bookmark, mutation.book_id, key, transaction=session)
            else:
                self.reader.delete(Annotation, mutation.book_id, key, transaction=session)
