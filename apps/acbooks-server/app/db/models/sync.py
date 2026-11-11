from sqlalchemy import ForeignKey, Integer, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class Device(Base):
    __tablename__ = "devices"

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    name: Mapped[str] = mapped_column(Text)
    last_seen_at: Mapped[int | None] = mapped_column(Integer)


class SyncChange(Base):
    __tablename__ = "sync_changes"
    __table_args__ = {"sqlite_autoincrement": True}

    seq: Mapped[int] = mapped_column(Integer, primary_key=True)
    entity_type: Mapped[str] = mapped_column(Text)
    entity_id: Mapped[str] = mapped_column(Text)
    operation: Mapped[str] = mapped_column(Text)
    changed_at: Mapped[int] = mapped_column(Integer)


class SyncMutation(Base):
    __tablename__ = "sync_mutations"
    __table_args__ = (UniqueConstraint("device_id", "mutation_id"),)

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    device_id: Mapped[str] = mapped_column(ForeignKey("devices.id", ondelete="RESTRICT"))
    mutation_id: Mapped[str] = mapped_column(Text)
    created_at: Mapped[int] = mapped_column(Integer)
