from typing import Literal

from sqlalchemy import CheckConstraint, ForeignKey, Index, Integer, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class StorageRoot(Base):
    __tablename__ = "storage_roots"
    __table_args__ = (CheckConstraint("mode IN ('managed', 'external')", name="mode"),)

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    name: Mapped[str] = mapped_column(Text)
    root_path: Mapped[str] = mapped_column(Text)
    mode: Mapped[str] = mapped_column(Text)


class StorageObject(Base):
    __tablename__ = "storage_objects"
    __table_args__ = (
        UniqueConstraint("root_id", "relative_path"),
        CheckConstraint("size_bytes >= 0", name="size_bytes"),
    )

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    root_id: Mapped[str] = mapped_column(ForeignKey("storage_roots.id", ondelete="RESTRICT"))
    relative_path: Mapped[str] = mapped_column(Text)
    size_bytes: Mapped[int] = mapped_column(Integer)
    sha256: Mapped[str | None] = mapped_column(Text)
    file_status: Mapped[Literal["present", "missing"]] = mapped_column(
        Text, server_default="present"
    )
    mtime_ms: Mapped[int] = mapped_column(Integer)


class BookFile(Base):
    __tablename__ = "book_files"
    __table_args__ = (Index("ix_book_files_book", "book_id"),)

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    book_id: Mapped[str] = mapped_column(ForeignKey("books.id", ondelete="RESTRICT"))
    storage_object_id: Mapped[str] = mapped_column(
        ForeignKey("storage_objects.id", ondelete="RESTRICT"), unique=True
    )
    format: Mapped[str] = mapped_column(Text)
