import logging
from collections import deque
from dataclasses import dataclass
from hashlib import sha256
from hmac import compare_digest
from math import ceil
from pathlib import Path
from re import compile as compile_pattern
from secrets import token_urlsafe
from threading import Lock
from time import monotonic, time_ns
from uuid import UUID, uuid4

from argon2 import PasswordHasher
from argon2.exceptions import VerifyMismatchError
from argon2.profiles import RFC_9106_LOW_MEMORY
from sqlalchemy import Engine
from sqlalchemy.orm import Session

from app.core.errors import BusinessError, BusinessErrorCode
from app.db.models.auth import AuthAccount, AuthToken
from app.db.session import create_session_factory
from app.modules.auth.repository import (
    account_exists,
    active_credential,
    add_account,
    add_session,
    find_account,
    find_active_session,
    find_active_token,
    list_tokens,
    prune_inactive_credentials,
    revoke_all,
    revoke_session,
    revoke_token,
)
from app.modules.auth.schemas import DeviceLoginResponse, IssuedToken, TokenPage, TokenResponse
from app.modules.auth.setup_code import read_setup_code

logger = logging.getLogger("uvicorn.error")
password_hasher = PasswordHasher.from_parameters(RFC_9106_LOW_MEMORY)
# A dummy hash with the same Argon2id parameters makes unknown accounts pay the same cost.
DUMMY_PASSWORD_HASH = (
    "$argon2id$v=19$m=65536,t=3,p=4$VJQkBcJTFeL2yF4f4FINXQ"
    "$9nEYSPpM74Fbtwq7X4d+OR/hlETiZmc1Oj4PYdfewFM"
)
SESSION_TOKEN_PATTERN = compile_pattern(r"[A-Za-z0-9_-]{43}")


class AttemptLimit:
    def __init__(self, max_attempts: int) -> None:
        self.lock = Lock()
        self.max_attempts = max_attempts
        self.attempts: deque[float] = deque()
        self.failures = 0
        self.retry_at = 0.0

    def admit(self) -> float:
        now = monotonic()
        while self.attempts and self.attempts[0] <= now - 60:
            self.attempts.popleft()
        retry_at = self.retry_at
        if len(self.attempts) >= self.max_attempts:
            retry_at = max(retry_at, self.attempts[0] + 60)
        if retry_at > now:
            raise BusinessError(BusinessErrorCode.RATE_LIMITED, retry_after=ceil(retry_at - now))
        if not self.attempts:
            self.reset_backoff()
        self.attempts.append(now)
        return now

    def failed(self, now: float) -> None:
        self.failures = min(self.failures + 1, 6)
        self.retry_at = now + 2 ** (self.failures - 1)

    def reset_backoff(self) -> None:
        self.failures = 0
        self.retry_at = 0.0


class SetupService:
    def __init__(self, engine: Engine, code_path: Path) -> None:
        self.engine = engine
        self.code_path = code_path
        self.limit = AttemptLimit(max_attempts=5)

    def initialized(self) -> bool:
        with Session(self.engine) as session:
            return account_exists(session)

    def _check_code(self, supplied: bytes) -> None:
        with self.limit.lock:
            now = self.limit.admit()
            code = read_setup_code(self.code_path)
            if code is not None and compare_digest(code, supplied):
                return
            if self.initialized():
                raise BusinessError(BusinessErrorCode.SETUP_ALREADY_INITIALIZED)
            self.limit.failed(now)
            raise BusinessError(BusinessErrorCode.SETUP_CODE_INVALID)

    def initialize(self, username: str, password: str, setup_code: str) -> UUID:
        if self.initialized():
            raise BusinessError(BusinessErrorCode.SETUP_ALREADY_INITIALIZED)
        supplied = setup_code.encode("utf-8")
        self._check_code(supplied)
        password_hash = password_hasher.hash(password)
        account_id = uuid4()
        with (
            self.engine.connect().execution_options(sqlite_begin_immediate=True) as connection,
            Session(connection) as session,
            session.begin(),
        ):
            if account_exists(session):
                raise BusinessError(BusinessErrorCode.SETUP_ALREADY_INITIALIZED)
            code = read_setup_code(self.code_path)
            if code is None or not compare_digest(code, supplied):
                raise BusinessError(BusinessErrorCode.SETUP_CODE_INVALID)
            add_account(
                session,
                str(account_id),
                username,
                username.casefold(),
                password_hash,
                time_ns() // 1_000_000,
            )
        try:
            self.code_path.unlink(missing_ok=True)
        except OSError as error:
            logger.warning(
                "operation=setup result=code_cleanup_failed error=%s", type(error).__name__
            )
        return account_id


@dataclass(frozen=True)
class AuthIdentity:
    id: str
    account_id: UUID
    username: str
    bearer: bool = False
    expires_at: int | None = None
    scope: str = "full"


def now_ms() -> int:
    return time_ns() // 1_000_000


class WebAuthService:
    def __init__(
        self, engine: Engine, session_ttl: int, device_ttl: int, application_ttl: int
    ) -> None:
        self.read_sessions = create_session_factory(engine)
        self.write_sessions = create_session_factory(
            engine.execution_options(sqlite_begin_immediate=True)
        )
        self.session_ttl = session_ttl
        self.device_ttl = device_ttl
        self.application_ttl = application_ttl
        self.limit = AttemptLimit(max_attempts=10)

    def authenticate(self, token: str | None) -> AuthIdentity | None:
        if token is None or not SESSION_TOKEN_PATTERN.fullmatch(token):
            return None
        with self.read_sessions() as session:
            record = find_active_session(
                session, sha256(token.encode()).hexdigest(), time_ns() // 1_000_000
            )
        if record is None:
            return None
        session_id, account_id, username = record
        return AuthIdentity(session_id, UUID(account_id), username)

    def login(
        self,
        username: str,
        password: str,
        previous: AuthIdentity | None,
        *,
        device_label: str | None = None,
    ) -> tuple[AuthIdentity, str]:
        # Bound concurrent Argon2 memory use and reject excess requests without waiting.
        if not self.limit.lock.acquire(blocking=False):
            raise BusinessError(BusinessErrorCode.RATE_LIMITED, retry_after=1)
        try:
            self.limit.admit()
            with self.read_sessions() as session:
                account = find_account(session, username.casefold())
            password_hash = account.password_hash if account is not None else DUMMY_PASSWORD_HASH
            try:
                password_hasher.verify(password_hash, password)
            except VerifyMismatchError:
                self.limit.failed(monotonic())
                raise BusinessError(BusinessErrorCode.INVALID_CREDENTIALS) from None
            if account is None:
                self.limit.failed(monotonic())
                raise BusinessError(BusinessErrorCode.INVALID_CREDENTIALS)

            token = token_urlsafe(32)
            token_hash = sha256(token.encode()).hexdigest()
            session_id = str(uuid4())
            with self.write_sessions.begin() as session:
                # A concurrent password change must invalidate the earlier verification.
                current = find_account(session, account.username_normalized)
                if current is None or (
                    current.id,
                    current.password_hash,
                    current.password_changed_at,
                ) != (account.id, password_hash, account.password_changed_at):
                    self.limit.failed(monotonic())
                    raise BusinessError(BusinessErrorCode.INVALID_CREDENTIALS)
                now = time_ns() // 1_000_000
                if previous is not None:
                    revoke_session(session, previous.id, now)
                ttl = self.session_ttl if device_label is None else self.device_ttl
                expires = now + ttl * 1000
                if device_label is None:
                    add_session(session, session_id, current.id, token_hash, now, expires)
                else:
                    session.add(
                        AuthToken(
                            id=session_id,
                            account_id=current.id,
                            token_hash=token_hash,
                            kind="device",
                            scope="full",
                            label=device_label,
                            created_at=now,
                            expires_at=expires,
                        )
                    )
                prune_inactive_credentials(session, now)
                identity = AuthIdentity(
                    session_id,
                    UUID(current.id),
                    current.username,
                    bearer=device_label is not None,
                    expires_at=expires,
                )
            self.limit.reset_backoff()
            return identity, token
        finally:
            self.limit.lock.release()

    def logout(self, current: AuthIdentity) -> bool:
        with self.write_sessions.begin() as session:
            session.connection()
            now = now_ms()
            if current.bearer:
                self._require_active(session, current, now)
                return revoke_token(session, current.id, str(current.account_id), now)
            return revoke_session(session, current.id, now)

    def authenticate_device(self, token: str | None) -> AuthIdentity | None:
        return self._authenticate_token(token, kind="device", scope="full")

    def authenticate_application(self, token: str | None) -> AuthIdentity | None:
        return self._authenticate_token(token, kind="application", scope="read")

    def _authenticate_token(
        self, token: str | None, *, kind: str, scope: str
    ) -> AuthIdentity | None:
        if token is None or not SESSION_TOKEN_PATTERN.fullmatch(token):
            return None
        with self.read_sessions() as session:
            record = find_active_token(
                session, sha256(token.encode()).hexdigest(), now_ms(), kind=kind, scope=scope
            )
        if record is None:
            return None
        key, account, username = record
        return AuthIdentity(key, UUID(account), username, bearer=True, scope=scope)

    def device_login(self, username: str, password: str, label: str) -> DeviceLoginResponse:
        identity, raw = self.login(username, password, None, device_label=label)
        assert identity.expires_at is not None
        return DeviceLoginResponse(
            access_token=raw, token_id=UUID(identity.id), expires_at=identity.expires_at
        )

    def _require_active(self, session: Session, current: AuthIdentity, now: int) -> None:
        if not active_credential(session, current.id, str(current.account_id), current.bearer, now):
            raise BusinessError(BusinessErrorCode.AUTHENTICATION_REQUIRED)

    def change_password(self, current: AuthIdentity, old: str, new: str) -> None:
        if not self.limit.lock.acquire(blocking=False):
            raise BusinessError(BusinessErrorCode.RATE_LIMITED, retry_after=1)
        try:
            self.limit.admit()
            account_id = str(current.account_id)
            with self.read_sessions() as session:
                self._require_active(session, current, now_ms())
                account = session.get(AuthAccount, account_id)
                assert account is not None
                previous_hash = account.password_hash
            try:
                password_hasher.verify(previous_hash, old)
            except VerifyMismatchError:
                self.limit.failed(monotonic())
                raise BusinessError(BusinessErrorCode.INVALID_CREDENTIALS) from None
            password_hash = password_hasher.hash(new)
            with self.write_sessions.begin() as session:
                now = now_ms()
                self._require_active(session, current, now)
                account = session.get(AuthAccount, account_id)
                assert account is not None
                if account.password_hash != previous_hash:
                    raise BusinessError(BusinessErrorCode.AUTHENTICATION_REQUIRED)
                account.password_hash = password_hash
                account.password_changed_at = now
                account.updated_at = now
                revoke_all(session, account_id, now)
            self.limit.reset_backoff()
        finally:
            self.limit.lock.release()

    def tokens(self, current: AuthIdentity, kind: str | None, limit: int, offset: int) -> TokenPage:
        with self.read_sessions() as session:
            self._require_active(session, current, now_ms())
            rows, total = list_tokens(session, str(current.account_id), kind, limit, offset)
            items = [TokenResponse.model_validate(row, from_attributes=True) for row in rows]
        return TokenPage(items=items, total=total, limit=limit, offset=offset)

    def issue_application_token(self, current: AuthIdentity, label: str) -> IssuedToken:
        raw = token_urlsafe(32)
        key = uuid4()
        with self.write_sessions.begin() as session:
            now = now_ms()
            self._require_active(session, current, now)
            expires = now + self.application_ttl * 1000
            session.add(
                AuthToken(
                    id=str(key),
                    account_id=str(current.account_id),
                    token_hash=sha256(raw.encode()).hexdigest(),
                    kind="application",
                    scope="read",
                    label=label,
                    created_at=now,
                    expires_at=expires,
                )
            )
        return IssuedToken(id=key, token=raw, expires_at=expires)

    def revoke(self, current: AuthIdentity, token_id: UUID) -> None:
        with self.write_sessions.begin() as session:
            now = now_ms()
            self._require_active(session, current, now)
            if not revoke_token(session, str(token_id), str(current.account_id), now):
                raise BusinessError(BusinessErrorCode.TOKEN_NOT_FOUND)
