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
from app.db.session import create_session_factory
from app.modules.auth.repository import (
    account_exists,
    add_account,
    add_session,
    find_account,
    find_active_session,
    prune_inactive_credentials,
    revoke_session,
)
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
class WebSession:
    id: str
    account_id: UUID
    username: str


class WebAuthService:
    def __init__(self, engine: Engine, session_ttl: int) -> None:
        self.read_sessions = create_session_factory(engine)
        self.write_sessions = create_session_factory(
            engine.execution_options(sqlite_begin_immediate=True)
        )
        self.session_ttl = session_ttl
        self.limit = AttemptLimit(max_attempts=10)

    def authenticate(self, token: str | None) -> WebSession | None:
        if token is None or not SESSION_TOKEN_PATTERN.fullmatch(token):
            return None
        with self.read_sessions() as session:
            record = find_active_session(
                session, sha256(token.encode()).hexdigest(), time_ns() // 1_000_000
            )
        if record is None:
            return None
        session_id, account_id, username = record
        return WebSession(session_id, UUID(account_id), username)

    def login(
        self, username: str, password: str, previous: WebSession | None
    ) -> tuple[WebSession, str]:
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
                add_session(
                    session,
                    session_id,
                    current.id,
                    token_hash,
                    now,
                    now + self.session_ttl * 1000,
                )
                prune_inactive_credentials(session, now)
                identity = WebSession(session_id, UUID(current.id), current.username)
            self.limit.reset_backoff()
            return identity, token
        finally:
            self.limit.lock.release()

    def logout(self, current: WebSession) -> bool:
        with self.write_sessions.begin() as session:
            session.connection()
            return revoke_session(session, current.id, time_ns() // 1_000_000)
