from sqlalchemy import CheckConstraint, Float, ForeignKey, Index, Integer, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class ReadingSession(Base):
    __tablename__ = "reading_sessions"
    __table_args__ = (
        Index("ix_sessions_book_time", "book_id", "started_at"),
        Index("ix_sessions_time", "started_at"),
        CheckConstraint("active_seconds >= 0", name="active_seconds"),
        CheckConstraint("ended_at IS NULL OR ended_at >= started_at", name="ended_at"),
        CheckConstraint("source IN ('automatic', 'manual')", name="source"),
    )

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    book_id: Mapped[str] = mapped_column(ForeignKey("books.id", ondelete="RESTRICT"))
    book_file_id: Mapped[str | None] = mapped_column(
        ForeignKey("book_files.id", ondelete="SET NULL")
    )
    device_id: Mapped[str | None] = mapped_column(ForeignKey("devices.id", ondelete="SET NULL"))
    started_at: Mapped[int] = mapped_column(Integer)
    ended_at: Mapped[int | None] = mapped_column(Integer)
    active_seconds: Mapped[int] = mapped_column(Integer, server_default="0")
    source: Mapped[str] = mapped_column(Text)


class ReadingPosition(Base):
    __tablename__ = "reading_positions"
    __table_args__ = (
        CheckConstraint("percent BETWEEN 0 AND 100", name="percent"),
        CheckConstraint("revision >= 0", name="revision"),
    )

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    book_file_id: Mapped[str] = mapped_column(
        ForeignKey("book_files.id", ondelete="RESTRICT"), unique=True
    )
    locator_json: Mapped[str] = mapped_column(Text)
    percent: Mapped[float | None] = mapped_column(Float)
    revision: Mapped[int] = mapped_column(Integer)
    updated_at: Mapped[int] = mapped_column(Integer)


class Bookmark(Base):
    __tablename__ = "bookmarks"

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    book_id: Mapped[str] = mapped_column(ForeignKey("books.id", ondelete="RESTRICT"))
    book_file_id: Mapped[str | None] = mapped_column(
        ForeignKey("book_files.id", ondelete="SET NULL")
    )
    locator_json: Mapped[str | None] = mapped_column(Text)
    label: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[int] = mapped_column(Integer)
    updated_at: Mapped[int] = mapped_column(Integer)


class Annotation(Base):
    __tablename__ = "annotations"

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    book_id: Mapped[str] = mapped_column(ForeignKey("books.id", ondelete="RESTRICT"))
    book_file_id: Mapped[str | None] = mapped_column(
        ForeignKey("book_files.id", ondelete="SET NULL")
    )
    locator_json: Mapped[str | None] = mapped_column(Text)
    quote: Mapped[str | None] = mapped_column(Text)
    note: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[int] = mapped_column(Integer)
    updated_at: Mapped[int] = mapped_column(Integer)
