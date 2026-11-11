from sqlalchemy import CheckConstraint, ForeignKey, Integer, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class AuthAccount(Base):
    __tablename__ = "auth_accounts"
    __table_args__ = (
        CheckConstraint("singleton_slot = 1", name="singleton_slot"),
        CheckConstraint("length(trim(username)) > 0", name="username"),
        CheckConstraint("length(password_hash) > 0", name="password_hash"),
    )

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    singleton_slot: Mapped[int] = mapped_column(Integer, unique=True, server_default="1")
    username: Mapped[str] = mapped_column(Text)
    username_normalized: Mapped[str] = mapped_column(Text, unique=True)
    password_hash: Mapped[str] = mapped_column(Text)
    password_changed_at: Mapped[int] = mapped_column(Integer)
    created_at: Mapped[int] = mapped_column(Integer)
    updated_at: Mapped[int] = mapped_column(Integer)


class AuthSession(Base):
    __tablename__ = "auth_sessions"
    __table_args__ = (CheckConstraint("expires_at > created_at", name="expires_at"),)

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    account_id: Mapped[str] = mapped_column(ForeignKey("auth_accounts.id", ondelete="RESTRICT"))
    session_token_hash: Mapped[str] = mapped_column(Text, unique=True)
    created_at: Mapped[int] = mapped_column(Integer)
    expires_at: Mapped[int] = mapped_column(Integer)
    revoked_at: Mapped[int | None] = mapped_column(Integer)
    last_seen_at: Mapped[int | None] = mapped_column(Integer)


class AuthToken(Base):
    __tablename__ = "auth_tokens"
    __table_args__ = (
        CheckConstraint("expires_at > created_at", name="expires_at"),
        CheckConstraint("kind IN ('device', 'application')", name="kind"),
        CheckConstraint("scope IN ('full', 'read')", name="scope"),
        CheckConstraint("kind <> 'application' OR scope = 'read'", name="application_read_only"),
    )

    id: Mapped[str] = mapped_column(Text, primary_key=True)
    account_id: Mapped[str] = mapped_column(ForeignKey("auth_accounts.id", ondelete="RESTRICT"))
    token_hash: Mapped[str] = mapped_column(Text, unique=True)
    kind: Mapped[str] = mapped_column(Text)
    scope: Mapped[str] = mapped_column(Text)
    label: Mapped[str] = mapped_column(Text)
    created_at: Mapped[int] = mapped_column(Integer)
    expires_at: Mapped[int] = mapped_column(Integer)
    revoked_at: Mapped[int | None] = mapped_column(Integer)
    last_used_at: Mapped[int | None] = mapped_column(Integer)
