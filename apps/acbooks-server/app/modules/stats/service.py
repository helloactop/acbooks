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
    ReadingDay,
    ReadingStats,
    ReadingType,
    StatsRange,
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
        end_hour = min(end, cursor + HOUR - (clock.minute * 60 + clock.second) * 1000)
        if offset != local_time(end_hour, zone).utcoffset():
            # Some zones change by a fraction of an hour. Split at the actual transition.
            low, high = cursor, end_hour
            while high - low > 1000:
                mid = ((low + high) // 2000) * 1000
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
    query: StatsRange, rows: list[ReadingSession], previous: dict[str, int]
) -> ReadingStats:
    zone = ZoneInfo(query.time_zone)
    start, end = bounds(query, zone)
    edges = hour_edges(start, end, zone) if rows else {start, end}
    starts: dict[int, list[tuple[float, int, str, ReadingType]]] = defaultdict(list)
    daily: dict[date, ReadingDay] = {
        day: ReadingDay(date=day, active_seconds=0, session_count=0, average_gap_seconds=None)
        for i in range((query.end_date - query.start_date).days)
        for day in [query.start_date + timedelta(days=i)]
    }
    gap_sums: dict[date, float] = defaultdict(float)
    gap_counts: dict[date, int] = defaultdict(int)
    for row in rows:
        if row.ended_at is None or row.ended_at <= row.started_at:
            continue
        left, right = max(row.started_at, start), min(row.ended_at, end)
        edges.update((left, right))
        density = row.active_seconds * 1000 / (row.ended_at - row.started_at)
        starts[left].append((-density, right, row.id, reading_type(row)))
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
    active: list[tuple[float, int, str, ReadingType]] = []
    ordered = sorted(edges)
    for left, right in zip(ordered, ordered[1:], strict=False):
        for item in starts.get(left, ()):
            heappush(active, item)
        while active and active[0][1] <= left:
            heappop(active)
        if not active:
            continue
        rate, _, _, kind = active[0]
        seconds = -rate * (right - left) / 1000
        clock = local_time(left, zone)
        if clock.date() not in daily:
            continue
        daily[clock.date()].active_seconds += seconds
        hourly[clock.hour] += seconds
        types[kind] += seconds

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

    def overview(self, query: StatsRange, book_id: str | None = None) -> ReadingStats:
        start, end = bounds(query, ZoneInfo(query.time_zone))
        with Session(self.engine) as db:
            self.require_book(db, book_id)
            rows = list(
                db.scalars(
                    repository.matching(start, end, book_id).order_by(
                        ReadingSession.started_at, ReadingSession.id
                    )
                )
            )
            previous = repository.previous_ends(db, start, {row.book_id for row in rows})
        return aggregate(query, rows, previous)

    def timeline(
        self, query: StatsRange, book_id: str | None, limit: int, offset: int
    ) -> ReadingPage[TimelineSession]:
        start, end = bounds(query, ZoneInfo(query.time_zone))
        with Session(self.engine) as db:
            self.require_book(db, book_id)
            items, total = repository.timeline(db, start, end, book_id, limit, offset)
        return ReadingPage[TimelineSession](items=items, total=total, limit=limit, offset=offset)

    @staticmethod
    def require_book(db: Session, book_id: str | None) -> None:
        if book_id is not None and db.get(Book, book_id) is None:
            raise BusinessError(BusinessErrorCode.BOOK_NOT_FOUND)
