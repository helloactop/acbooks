import json
import re
from pathlib import Path
from time import monotonic, sleep, time_ns
from typing import Any, cast
from urllib.parse import urlencode
from urllib.request import HTTPRedirectHandler, Request, build_opener

from sqlalchemy import Engine
from sqlalchemy.orm import Session

from app.core.errors import BusinessError, BusinessErrorCode
from app.db.models.books import Book
from app.db.models.settings import AppSetting
from app.db.session import create_session_factory
from app.modules.books import repository
from app.modules.books.normalization import normalize_isbn
from app.modules.books.service import MAX_COVER_BYTES, publish_cover
from app.modules.metadata.extract import EmbeddedMetadata, _authors, _text, read_metadata
from app.modules.metadata.locks import (
    AUTHOR,
    COVER,
    DESCRIPTION,
    ISBN,
    PLACEHOLDER_TITLE,
    SERIES,
    TRANSLATOR,
)
from app.modules.metadata.schemas import DEFAULT_FIELDS, Candidate, MetadataSettings
from app.workers import queue

AUTO_FILL_KEY = "metadata.auto_fill"


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args: Any, **kwargs: Any) -> None:
        return None


def _download(host: str, path: str, limit: int) -> bytes:
    request = Request(f"https://{host}{path}", headers={"User-Agent": "acbooks"})
    try:
        with build_opener(NoRedirect()).open(request, timeout=5) as response:
            data = cast(bytes, response.read(limit + 1))
    except OSError as error:
        raise BusinessError(BusinessErrorCode.METADATA_UNAVAILABLE) from error
    if len(data) > limit:
        raise BusinessError(BusinessErrorCode.METADATA_UNAVAILABLE)
    return data


def _strings(value: Any) -> list[str]:
    return [item for item in value if isinstance(item, str)] if isinstance(value, list) else []


def _json(path: str) -> dict[str, Any]:
    try:
        payload = json.loads(_download("openlibrary.org", path, 524288))
        if isinstance(payload, dict):
            return payload
    except (ValueError, TypeError) as error:
        raise BusinessError(BusinessErrorCode.METADATA_UNAVAILABLE) from error
    raise BusinessError(BusinessErrorCode.METADATA_UNAVAILABLE)


def _names(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [
        item["name"]
        for item in value
        if isinstance(item, dict) and isinstance(item.get("name"), str)
    ]


def _source_key(value: Any) -> str | None:
    if isinstance(value, str) and re.fullmatch(r"/(books/OL[0-9]+M|works/OL[0-9]+W)", value):
        return value
    return None


def _description(key: str) -> str | None:
    entry = _json(key + ".json")
    value = entry.get("description")
    if isinstance(value, dict):
        value = value.get("value")
    if value is None and key.startswith("/books/"):
        works = entry.get("works")
        if isinstance(works, list) and works and isinstance(works[0], dict):
            work = _source_key(works[0].get("key"))
            if work is not None and work.startswith("/works/"):
                return _description(work)
    return _text(value, 20000, multiline=True)


def _cover_id(value: Any) -> int | None:
    url = value.get("medium") if isinstance(value, dict) else None
    if not isinstance(url, str):
        return None
    match = re.fullmatch(r"https?://covers\.openlibrary\.org/b/id/([0-9]{1,10})-M\.jpg", url)
    if match and 0 < (identifier := int(match[1])) <= 2147483647:
        return identifier
    return None


class MetadataService:
    extract = staticmethod(read_metadata)

    def __init__(self, engine: Engine, covers: Path) -> None:
        self.read_sessions = create_session_factory(engine)
        self.write_sessions = create_session_factory(
            engine.execution_options(sqlite_begin_immediate=True)
        )
        self.covers = covers

    @staticmethod
    def settings_from(session: Session) -> MetadataSettings:
        setting = session.get(AppSetting, AUTO_FILL_KEY)
        if setting is None:
            return MetadataSettings()
        return MetadataSettings.model_validate_json(setting.value_json)

    def settings(self) -> MetadataSettings:
        with self.read_sessions() as session:
            return self.settings_from(session)

    def save_settings(self, settings: MetadataSettings) -> None:
        with self.write_sessions.begin() as session:
            session.merge(AppSetting(key=AUTO_FILL_KEY, value_json=settings.model_dump_json()))

    @classmethod
    def enqueue_auto_fill(cls, session: Session, book_id: str) -> None:
        settings = cls.settings_from(session)
        if settings.auto_fill and settings.fields:
            queue.add_job(session, "metadata_fill", {"bookIds": [book_id], "automatic": True})

    @staticmethod
    def search(query: str) -> list[Candidate]:
        isbn = normalize_isbn(query)
        if isbn:
            key = f"ISBN:{isbn}"
            payload = _json(
                "/api/books?"
                + urlencode(
                    {
                        "bibkeys": key,
                        "jscmd": "data",
                        "format": "json",
                    }
                )
            )
            row = payload.get(key)
            if not isinstance(row, dict) or not (title := _text(row.get("title"), 500)):
                return []
            identifiers = row.get("identifiers")
            if not isinstance(identifiers, dict) or isbn not in (
                _strings(identifiers.get("isbn_10")) + _strings(identifiers.get("isbn_13"))
            ):
                return []
            return [
                Candidate(
                    title=title,
                    isbn=isbn,
                    authors=list(_authors(_names(row.get("authors")))),
                    description=_text(row.get("description"), 20000, multiline=True),
                    source_key=_source_key(row.get("key")),
                    cover_id=_cover_id(row.get("cover")),
                    series=_text(row.get("series"), 200),
                    translators=list(_authors(_names(row.get("translators")))),
                )
            ]
        rows = _json(
            "/search.json?"
            + urlencode(
                {
                    "title": query,
                    "fields": "key,title,author_name,cover_i",
                    "limit": "10",
                }
            )
        ).get("docs")
        if not isinstance(rows, list):
            raise BusinessError(BusinessErrorCode.METADATA_UNAVAILABLE)

        candidates = []
        for row in rows[:10]:
            if not isinstance(row, dict) or not (title := _text(row.get("title"), 500)):
                continue
            cover = row.get("cover_i")
            candidates.append(
                Candidate(
                    title=title,
                    authors=list(_authors(_strings(row.get("author_name")))),
                    source_key=_source_key(row.get("key")),
                    cover_id=cover if type(cover) is int and 0 < cover <= 2147483647 else None,
                )
            )
        return candidates

    @staticmethod
    def fill(
        session: Session,
        book: Book,
        metadata: EmbeddedMetadata,
        cover: Path | None,
        fields: set[str] | None = None,
    ) -> list[str]:
        record = repository.load_records(session, [book])[0]
        locks = book.metadata_locks
        changed = []
        allowed = fields if fields is not None else set(DEFAULT_FIELDS)
        if "title" in allowed and metadata.title and locks & PLACEHOLDER_TITLE:
            book.title = metadata.title
            book.metadata_locks &= ~PLACEHOLDER_TITLE
            changed.append("title")
        if "translators" in allowed and metadata.translators and not record.translators:
            if not locks & TRANSLATOR:
                repository.replace_contributors(
                    session,
                    book.id,
                    "translator",
                    list(metadata.translators),
                )
                changed.append("translators")
        if "series" in allowed and metadata.series and not book.series and not locks & SERIES:
            book.series = metadata.series
            changed.append("series")
        if "authors" in allowed and metadata.authors and not record.authors and not locks & AUTHOR:
            repository.replace_contributors(session, book.id, "author", list(metadata.authors))
            changed.append("authors")
        if (
            "description" in allowed
            and metadata.description
            and not book.description
            and not locks & DESCRIPTION
        ):
            book.description = metadata.description
            changed.append("description")
        if (
            "isbn" in allowed
            and metadata.isbn
            and not any(scheme == "isbn" for scheme, _ in record.identifiers)
        ):
            if not locks & ISBN:
                repository.replace_identifiers(session, book.id, metadata.isbn)
                changed.append("isbn")
        if "cover" in allowed and cover is not None and not book.cover_path and not locks & COVER:
            book.cover_path = cover.name
            book.cover_updated_at = time_ns() // 1_000_000
            changed.append("cover")
        if changed:
            book.updated_at = time_ns() // 1_000_000
        return changed

    def prepare_cover(self, book_id: str, content: bytes | None) -> Path | None:
        if content is None:
            return None
        try:
            return publish_cover(self.covers, book_id, content)
        except (BusinessError, OSError):
            return None

    def apply(
        self,
        book_id: str,
        candidate: Candidate,
        *,
        automatic: bool = False,
        require_isbn: str | None = None,
    ) -> list[str]:
        with self.read_sessions() as session:
            book = repository.get_book(session, book_id)
            if book is None:
                raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)
            settings = self.settings_from(session)
            if automatic and not settings.auto_fill:
                return []
            needs_cover = (
                (require_isbn is None or "cover" in settings.fields)
                and not book.cover_path
                and not book.metadata_locks & COVER
            )
            needs_description = (
                (require_isbn is None or "description" in settings.fields)
                and not book.description
                and not book.metadata_locks & DESCRIPTION
            )
        description = candidate.description
        if description is None and needs_description and candidate.source_key:
            try:
                description = _description(candidate.source_key)
            except BusinessError:
                pass
        cover = None
        if needs_cover and candidate.cover_id is not None:
            try:
                content = _download(
                    "covers.openlibrary.org",
                    f"/b/id/{candidate.cover_id}-M.jpg?default=false",
                    MAX_COVER_BYTES,
                )
            except BusinessError:
                content = None
            cover = self.prepare_cover(book_id, content)
        keep_cover = False
        try:
            with self.write_sessions.begin() as session:
                book = repository.get_book(session, book_id)
                if book is None:
                    raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)
                if require_isbn is not None:
                    record = repository.load_records(session, [book])[0]
                    if ("isbn", require_isbn) not in record.identifiers:
                        raise BusinessError(BusinessErrorCode.METADATA_STALE)
                settings = self.settings_from(session)
                if automatic and not settings.auto_fill:
                    return []
                changed = self.fill(
                    session,
                    book,
                    EmbeddedMetadata(
                        title=candidate.title,
                        authors=tuple(candidate.authors),
                        translators=tuple(candidate.translators),
                        series=candidate.series,
                        description=description,
                        isbn=candidate.isbn,
                    ),
                    cover,
                    set(settings.fields) if require_isbn is not None else None,
                )
            keep_cover = "cover" in changed
            return changed
        finally:
            if cover is not None and not keep_cover:
                cover.unlink(missing_ok=True)

    def batch(self, book_ids: list[str], *, automatic: bool = False) -> dict[str, Any]:
        items: list[dict[str, Any]] = []
        next_request = 0.0
        for book_id in dict.fromkeys(book_ids):
            with self.read_sessions() as session:
                settings = self.settings_from(session)
                if not settings.fields or (automatic and not settings.auto_fill):
                    items.append({"bookId": book_id, "status": "unchanged"})
                    continue
                book = repository.get_book(session, book_id)
                record = repository.load_records(session, [book])[0] if book else None
                isbn = (
                    next((value for key, value in record.identifiers if key == "isbn"), None)
                    if record
                    else None
                )
            if isbn is None:
                items.append({"bookId": book_id, "status": "review" if record else "missing"})
                continue
            try:
                sleep(max(0.0, next_request - monotonic()))
                candidates = self.search(isbn)
                next_request = monotonic() + 1
                if len(candidates) != 1 or candidates[0].isbn != isbn:
                    items.append({"bookId": book_id, "status": "review"})
                    continue
                candidate = candidates[0]
                changed = self.apply(book_id, candidate, automatic=automatic, require_isbn=isbn)
                items.append(
                    {
                        "bookId": book_id,
                        "status": "filled" if changed else "unchanged",
                        "fields": changed,
                    }
                )
            except BusinessError as error:
                next_request = monotonic() + 1
                items.append({"bookId": book_id, "status": "failed", "errorCode": error.code})
        return {"items": items}
