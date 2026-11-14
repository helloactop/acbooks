from datetime import date
from typing import Literal, Self
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from pydantic import field_validator, model_validator

from app.modules.books.schemas import LaxApiModel
from app.modules.reader.schemas import SessionRead

ReadingType = Literal["electronic", "manual", "unknown"]


class StatsRange(LaxApiModel):
    start_date: date
    end_date: date
    time_zone: str

    @field_validator("time_zone")
    @classmethod
    def known_time_zone(cls, value: str) -> str:
        try:
            ZoneInfo(value)
        except (ValueError, ZoneInfoNotFoundError) as exc:
            raise ValueError("Expected an IANA time zone.") from exc
        return value

    @model_validator(mode="after")
    def check_range(self) -> Self:
        if not 1 <= (self.end_date - self.start_date).days <= 366:
            raise ValueError("Expected a range of 1 to 366 days.")
        if not 1970 <= self.start_date.year <= self.end_date.year <= 9998:
            raise ValueError("Date range is outside supported years.")
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
