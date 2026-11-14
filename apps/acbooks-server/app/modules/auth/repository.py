from sqlalchemy import delete, func, or_, select, update
from sqlalchemy.orm import Session

from app.db.models.auth import AuthAccount, AuthSession, AuthToken


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


def prune_inactive_credentials(session: Session, now: int) -> None:
    session.execute(
        delete(AuthSession).where(
            or_(AuthSession.revoked_at.is_not(None), AuthSession.expires_at <= now)
        )
    )
    session.execute(
        delete(AuthToken).where(or_(AuthToken.revoked_at.is_not(None), AuthToken.expires_at <= now))
    )


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


def find_active_token(
    session: Session, token_hash: str, now: int, *, kind: str, scope: str
) -> tuple[str, str, str] | None:
    row = session.execute(
        select(AuthToken.id, AuthAccount.id, AuthAccount.username)
        .join(AuthAccount, AuthAccount.id == AuthToken.account_id)
        .where(
            AuthToken.token_hash == token_hash,
            AuthToken.kind == kind,
            AuthToken.scope == scope,
            AuthToken.revoked_at.is_(None),
            AuthToken.expires_at > now,
        )
    ).one_or_none()
    return (row[0], row[1], row[2]) if row is not None else None


def active_credential(session: Session, key: str, account_id: str, bearer: bool, now: int) -> bool:
    current: AuthToken | AuthSession | None = (
        session.get(AuthToken, key) if bearer else session.get(AuthSession, key)
    )
    return (
        current is not None
        and current.account_id == account_id
        and current.revoked_at is None
        and current.expires_at > now
        and (
            not isinstance(current, AuthToken)
            or (current.kind == "device" and current.scope == "full")
        )
    )


def revoke_token(session: Session, key: str, account_id: str, now: int) -> bool:
    row = session.get(AuthToken, key)
    if row is None or row.account_id != account_id:
        return False
    if row.revoked_at is None:
        row.revoked_at = now
    return True


def revoke_all(session: Session, account_id: str, now: int) -> None:
    for model in (AuthSession, AuthToken):
        session.execute(
            update(model)
            .where(model.account_id == account_id, model.revoked_at.is_(None))
            .values(revoked_at=now)
        )


def list_tokens(
    session: Session, account_id: str, kind: str | None, limit: int, offset: int
) -> tuple[list[AuthToken], int]:
    query = select(AuthToken).where(AuthToken.account_id == account_id)
    if kind is not None:
        query = query.where(AuthToken.kind == kind)
    total = session.scalar(select(func.count()).select_from(query.subquery())) or 0
    rows = session.scalars(
        query.order_by(AuthToken.created_at.desc(), AuthToken.id).offset(offset).limit(limit)
    )
    return list(rows), total
