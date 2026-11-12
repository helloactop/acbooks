from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.models.auth import AuthAccount


def account_exists(session: Session) -> bool:
    return session.scalar(select(AuthAccount.id).where(AuthAccount.singleton_slot == 1)) is not None


def add_account(
    session: Session,
    account_id: str,
    username: str,
    username_normalized: str,
    password_hash: str,
    timestamp: int,
) -> None:
    session.add(
        AuthAccount(
            id=account_id,
            username=username,
            username_normalized=username_normalized,
            password_hash=password_hash,
            password_changed_at=timestamp,
            created_at=timestamp,
            updated_at=timestamp,
        )
    )
