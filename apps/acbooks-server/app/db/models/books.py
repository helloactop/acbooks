from typing import TYPE_CHECKING

from sqlalchemy import ForeignKey, Index, Integer, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base

if TYPE_CHECKING:
    from app.db.models.copies import BookCopy
    from app.db.models.storage import BookFile


class Book(Base):
    __tablename__ = "books"
    __table_args__ = (Index("ix_books_title", "title"),)

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    title: Mapped[str] = mapped_column(Text)
    description: Mapped[str | None] = mapped_column(Text)
    reading_status: Mapped[str] = mapped_column(Text, server_default="unread")
    cover_path: Mapped[str | None] = mapped_column(Text)
    series: Mapped[str | None] = mapped_column(Text)
    metadata_locks: Mapped[int] = mapped_column(Integer, server_default="0")
    cover_updated_at: Mapped[int | None] = mapped_column(Integer)
    created_at: Mapped[int] = mapped_column(Integer)
    updated_at: Mapped[int] = mapped_column(Integer)

    copies: Mapped[list["BookCopy"]] = relationship(passive_deletes="all")
    files: Mapped[list["BookFile"]] = relationship(passive_deletes="all")


class Contributor(Base):
    __tablename__ = "contributors"
    __table_args__ = (Index("ix_contributors_name", "name"),)

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    name: Mapped[str] = mapped_column(Text)


class BookContributor(Base):
    __tablename__ = "book_contributors"

    book_id: Mapped[str] = mapped_column(
        ForeignKey("books.id", ondelete="CASCADE"), primary_key=True
    )
    contributor_id: Mapped[str] = mapped_column(
        ForeignKey("contributors.id", ondelete="CASCADE"), primary_key=True
    )
    role: Mapped[str] = mapped_column(Text, primary_key=True)


Index("ix_books_created_at", Book.created_at.desc(), Book.id.asc())
Index("ix_books_updated_at", Book.updated_at.desc(), Book.id.asc())
Index("ix_books_title_nocase", Book.title.collate("NOCASE"), Book.id.asc())
Index(
    "ix_books_reading_status_created",
    Book.reading_status,
    Book.created_at.desc(),
    Book.id.asc(),
)
Index(
    "ix_book_contributors_book_role",
    BookContributor.book_id,
    BookContributor.role,
    BookContributor.contributor_id,
)


class BookIdentifier(Base):
    __tablename__ = "book_identifiers"
    __table_args__ = (Index("ix_book_identifiers_scheme_value", "scheme", "value"),)

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    book_id: Mapped[str] = mapped_column(ForeignKey("books.id", ondelete="CASCADE"))
    scheme: Mapped[str] = mapped_column(Text)
    value: Mapped[str] = mapped_column(Text)


class Tag(Base):
    __tablename__ = "tags"

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    name: Mapped[str] = mapped_column(Text)


class BookTag(Base):
    __tablename__ = "book_tags"

    book_id: Mapped[str] = mapped_column(
        ForeignKey("books.id", ondelete="CASCADE"), primary_key=True
    )
    tag_id: Mapped[str] = mapped_column(ForeignKey("tags.id", ondelete="CASCADE"), primary_key=True)


class Shelf(Base):
    __tablename__ = "shelves"

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    name: Mapped[str] = mapped_column(Text)


class ShelfBook(Base):
    __tablename__ = "shelf_books"

    shelf_id: Mapped[str] = mapped_column(
        ForeignKey("shelves.id", ondelete="CASCADE"), primary_key=True
    )
    book_id: Mapped[str] = mapped_column(
        ForeignKey("books.id", ondelete="CASCADE"), primary_key=True
    )
