from sqlalchemy import ForeignKey, Index, Integer, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class Location(Base):
    __tablename__ = "locations"

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    parent_id: Mapped[str | None] = mapped_column(ForeignKey("locations.id"))
    name: Mapped[str] = mapped_column(Text)


class BookCopy(Base):
    __tablename__ = "book_copies"

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    book_id: Mapped[str] = mapped_column(ForeignKey("books.id", ondelete="RESTRICT"))
    location_id: Mapped[str | None] = mapped_column(ForeignKey("locations.id", ondelete="SET NULL"))
    acquired_at: Mapped[int | None] = mapped_column(Integer)
    note: Mapped[str | None] = mapped_column(Text)


Index("ix_book_copies_book", BookCopy.book_id)
Index("ix_book_copies_location", BookCopy.location_id)
