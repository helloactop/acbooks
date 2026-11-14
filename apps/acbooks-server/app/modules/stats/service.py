from collections import defaultdict
from datetime import UTC, date, datetime, time, timedelta
from heapq import heappop, heappush
from zoneinfo import ZoneInfo

from sqlalchemy import Engine
from sqlalchemy.orm import Session

from app.core.errors import BusinessError, BusinessErrorCode
from app.db.models.books import Book
from app.db.models.reading import ReadingSession
from app.modules.reader.schemas import ReadingPage
from app.modules.stats import repository
from app.modules.stats.schemas import (
    DailyReadingBook,
    DailyReadingStats,
    ReadingDay,
    ReadingStats,
    ReadingType,
    StatsRange,
    StatsSelection,
    TimelineSession,
)

HOUR = 3_600_000
EPOCH = datetime(1970, 1, 1, tzinfo=UTC)


def timestamp(value: datetime) -> int:
    return int(value.timestamp() * 1000)


def local_time(value: int, zone: ZoneInfo) -> datetime:
    return (EPOCH + timedelta(milliseconds=value)).astimezone(zone)


def bounds(query: StatsRange, zone: ZoneInfo) -> tuple[int, int]:
    return (
        timestamp(datetime.combine(query.start_date, time(), zone)),
        timestamp(datetime.combine(query.end_date, time(), zone)),
    )


def hour_edges(start: int, end: int, zone: ZoneInfo) -> set[int]:
    edges = {start, end}
    cursor = start
    while cursor < end:
        clock = local_time(cursor, zone)
        offset = clock.utcoffset()
        end_hour = min(
            end,
            cursor + HOUR - (clock.minute * 60 + clock.second) * 1000 - clock.microsecond // 1000,
        )
        if offset != local_time(end_hour, zone).utcoffset():
            # Some zones change by a fraction of an hour. Split at the actual transition.
            low, high = cursor, end_hour
            while high - low > 1:
                mid = (low + high) // 2
                if local_time(mid, zone).utcoffset() == offset:
                    low = mid
                else:
                    high = mid
            end_hour = high
        edges.add(end_hour)
        cursor = end_hour
    return edges


def reading_type(row: ReadingSession) -> ReadingType:
    if row.book_file_id is not None:
        return "electronic"
    return "manual" if row.source == "manual" else "unknown"


def aggregate(
    query: StatsRange,
    rows: list[ReadingSession],
    previous: dict[str, int],
    book_seconds: dict[str, float] | None = None,
) -> ReadingStats:
    zone = ZoneInfo(query.time_zone)
    start, end = bounds(query, zone)
    edges = {start, end}
    starts: dict[int, list[tuple[float, int, str, ReadingType, str]]] = defaultdict(list)
    daily: dict[date, ReadingDay] = {
        day: ReadingDay(date=day, active_seconds=0, session_count=0, average_gap_seconds=None)
        for i in range((query.end_date - query.start_date).days)
        for day in [query.start_date + timedelta(days=i)]
    }
    gap_sums: dict[date, float] = defaultdict(float)
    gap_counts: dict[date, int] = defaultdict(int)
    covered_until = start
    for row in rows:
        if row.ended_at is None or row.ended_at <= row.started_at:
            continue
        left, right = max(row.started_at, start), min(row.ended_at, end)
        edges.update((left, right))
        if right > covered_until:
            edges.update(hour_edges(max(left, covered_until), right, zone))
            covered_until = right
        density = row.active_seconds * 1000 / (row.ended_at - row.started_at)
        starts[left].append((-density, right, row.id, reading_type(row), row.book_id))
        if row.started_at < start:
            continue
        day = local_time(row.started_at, zone).date()
        if day not in daily:
            continue
        daily[day].session_count += 1
        last = previous.get(row.book_id)
        if last is not None and last < row.started_at:
            gap_sums[day] += (row.started_at - last) / 1000
            gap_counts[day] += 1
        previous[row.book_id] = max(last if last is not None else row.ended_at, row.ended_at)

    hourly = [0.0] * 24
    types: dict[ReadingType, float] = {"electronic": 0, "manual": 0, "unknown": 0}
    active: list[tuple[float, int, str, ReadingType, str]] = []
    ordered = sorted(edges)
    for left, right in zip(ordered, ordered[1:], strict=False):
        for item in starts.get(left, ()):
            heappush(active, item)
        while active and active[0][1] <= left:
            heappop(active)
        if not active:
            continue
        rate, _, _, kind, book_id = active[0]
        seconds = -rate * (right - left) / 1000
        clock = local_time(left, zone)
        if clock.date() not in daily:
            continue
        daily[clock.date()].active_seconds += seconds
        hourly[clock.hour] += seconds
        types[kind] += seconds
        if book_seconds is not None:
            book_seconds[book_id] = book_seconds.get(book_id, 0) + seconds

    for day, count in gap_counts.items():
        daily[day].average_gap_seconds = gap_sums[day] / count
    gaps = sum(gap_counts.values())
    return ReadingStats(
        **query.model_dump(),
        active_seconds=sum(types.values()),
        session_count=sum(day.session_count for day in daily.values()),
        average_gap_seconds=sum(gap_sums.values()) / gaps if gaps else None,
        days=list(daily.values()),
        hourly_seconds=hourly,
        type_seconds=types,
    )


class StatsService:
    def __init__(self, engine: Engine) -> None:
        self.engine = engine

    def overview(self, query: StatsSelection, book_id: str | None = None) -> ReadingStats:
        with Session(self.engine) as db:
            self.require_book(db, book_id)
            selected = self.resolve_range(db, query, book_id)
            start, end = bounds(selected, ZoneInfo(query.time_zone))
            rows = list(
                db.scalars(
                    repository.matching(start, end, book_id).order_by(
                        ReadingSession.started_at, ReadingSession.id
                    )
                )
            )
            previous = repository.previous_ends(db, start, {row.book_id for row in rows})
        return aggregate(selected, rows, previous)

    def timeline(
        self,
        query: StatsSelection,
        book_id: str | None,
        limit: int,
        offset: int,
        include_inactive: bool,
    ) -> ReadingPage[TimelineSession]:
        with Session(self.engine) as db:
            self.require_book(db, book_id)
            selected = self.resolve_range(db, query, book_id)
            start, end = bounds(selected, ZoneInfo(query.time_zone))
            items, total = repository.timeline(
                db, start, end, book_id, limit, offset, include_inactive
            )
        return ReadingPage[TimelineSession](items=items, total=total, limit=limit, offset=offset)

    def daily(
        self, query: StatsRange, book_id: str | None, limit: int, offset: int
    ) -> DailyReadingStats:
        start, end = bounds(query, ZoneInfo(query.time_zone))
        book_seconds: dict[str, float] = {}
        with Session(self.engine) as db:
            self.require_book(db, book_id)
            rows = list(
                db.scalars(
                    repository.matching(start, end, book_id).order_by(
                        ReadingSession.started_at, ReadingSession.id
                    )
                )
            )
            stats = aggregate(query, rows, {}, book_seconds)
            for row in rows:
                book_seconds.setdefault(row.book_id, 0)
            ids = sorted(book_seconds, key=lambda id_: (-book_seconds[id_], id_))[
                offset : offset + limit
            ]
            books = repository.books_by_id(db, ids)
            items = [
                DailyReadingBook(
                    id=book.id,
                    title=book.title,
                    has_cover=bool(book.cover_path),
                    cover_updated_at=book.cover_updated_at,
                    updated_at=book.updated_at,
                    active_seconds=book_seconds[book.id],
                )
                for book in (books[id_] for id_ in ids if id_ in books)
            ]
        return DailyReadingStats(
            active_seconds=stats.active_seconds,
            session_count=stats.session_count,
            books=ReadingPage[DailyReadingBook](
                items=items,
                total=len(book_seconds),
                limit=limit,
                offset=offset,
            ),
        )

    @staticmethod
    def resolve_range(db: Session, query: StatsSelection, book_id: str | None) -> StatsRange:
        if query.start_date is not None and query.end_date is not None:
            return StatsRange(
                start_date=query.start_date, end_date=query.end_date, time_zone=query.time_zone
            )
        zone = ZoneInfo(query.time_zone)
        today = datetime.now(zone).date()
        tomorrow = today + timedelta(days=1)
        earliest = repository.earliest_start(
            db, timestamp(datetime.combine(tomorrow, time(), zone)), book_id
        )
        start = today if earliest is None else min(today, local_time(earliest, zone).date())
        return StatsRange(start_date=start, end_date=tomorrow, time_zone=query.time_zone)

    @staticmethod
    def require_book(db: Session, book_id: str | None) -> None:
        if book_id is not None and db.get(Book, book_id) is None:
            raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)
