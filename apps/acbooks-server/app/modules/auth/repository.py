from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.models.auth import AuthAccount, AuthSession


def account_exists(session: Session) -> bool:
    return session.scalar(select(AuthAccount.id).where(AuthAccount.singleton_slot == 1)) is not None


def find_account(session: Session, username_normalized: str) -> AuthAccount | None:
    return session.scalar(
        select(AuthAccount).where(AuthAccount.username_normalized == username_normalized)
    )


def find_active_session(session: Session, token_hash: str, now: int) -> tuple[str, str, str] | None:
    result = session.execute(
        select(AuthSession.id, AuthAccount.id, AuthAccount.username)
        .join(AuthAccount, AuthAccount.id == AuthSession.account_id)
        .where(
            AuthSession.session_token_hash == token_hash,
            AuthSession.revoked_at.is_(None),
            AuthSession.expires_at > now,
        )
    ).one_or_none()
    return (result[0], result[1], result[2]) if result is not None else None


def add_session(
    session: Session, session_id: str, account_id: str, token_hash: str, now: int, expires_at: int
) -> None:
    session.add(
        AuthSession(
            id=session_id,
            account_id=account_id,
            session_token_hash=token_hash,
            created_at=now,
            expires_at=expires_at,
        )
    )


def revoke_session(session: Session, session_id: str, now: int) -> bool:
    current = session.get(AuthSession, session_id)
    if current is None or current.revoked_at is not None or current.expires_at <= now:
        return False
    current.revoked_at = now
    return True


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
