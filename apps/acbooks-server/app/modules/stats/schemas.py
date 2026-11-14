from datetime import date, datetime, timedelta
from typing import Literal, Self
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from pydantic import field_validator, model_validator

from app.modules.books.schemas import LaxApiModel
from app.modules.reader.schemas import ReadingPage, SessionRead

ReadingType = Literal["electronic", "manual", "unknown"]


class StatsQuery(LaxApiModel):
    time_zone: str

    @field_validator("time_zone")
    @classmethod
    def known_time_zone(cls, value: str) -> str:
        try:
            ZoneInfo(value)
        except (ValueError, ZoneInfoNotFoundError) as exc:
            raise ValueError("Expected an IANA time zone.") from exc
        return value


class StatsRange(StatsQuery):
    start_date: date
    end_date: date


class StatsSelection(StatsQuery):
    start_date: date | None = None
    end_date: date | None = None

    @model_validator(mode="after")
    def check_range(self) -> Self:
        if self.start_date is None and self.end_date is None:
            return self
        if self.start_date is None or self.end_date is None or self.end_date <= self.start_date:
            raise ValueError("Expected both dates in ascending order.")
        if not 1970 <= self.start_date.year <= self.end_date.year <= 9998:
            raise ValueError("Date range is outside supported years.")
        tomorrow = datetime.now(ZoneInfo(self.time_zone)).date() + timedelta(days=1)
        if (self.end_date - self.start_date).days > 366 and self.end_date > tomorrow:
            raise ValueError("Extended date ranges must end by the end of today.")
        return self


class ReadingDay(LaxApiModel):
    date: date
    active_seconds: float
    session_count: int
    average_gap_seconds: float | None


class ReadingStats(StatsRange):
    active_seconds: float
    session_count: int
    average_gap_seconds: float | None
    days: list[ReadingDay]
    hourly_seconds: list[float]
    type_seconds: dict[ReadingType, float]


class TimelineSession(SessionRead):
    book_title: str
    file_format: str | None


class DailyReadingBook(LaxApiModel):
    id: str
    title: str
    has_cover: bool
    cover_updated_at: int | None
    updated_at: int
    active_seconds: float


class DailyReadingStats(LaxApiModel):
    active_seconds: float
    session_count: int
    books: ReadingPage[DailyReadingBook]
