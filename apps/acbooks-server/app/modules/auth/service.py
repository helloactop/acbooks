import logging
from collections import deque
from hmac import compare_digest
from math import ceil
from pathlib import Path
from threading import Lock
from time import monotonic, time_ns
from uuid import UUID, uuid4

from argon2 import PasswordHasher
from argon2.profiles import RFC_9106_LOW_MEMORY
from sqlalchemy import Engine
from sqlalchemy.orm import Session

from app.core.errors import BusinessError, BusinessErrorCode
from app.modules.auth.repository import account_exists, add_account
from app.modules.auth.setup_code import read_setup_code

logger = logging.getLogger("uvicorn.error")
password_hasher = PasswordHasher.from_parameters(RFC_9106_LOW_MEMORY)


class SetupService:
    def __init__(self, engine: Engine, code_path: Path) -> None:
        self.engine = engine
        self.code_path = code_path
        self.attempt_lock = Lock()
        self.attempts: deque[float] = deque()
        self.failures = 0
        self.retry_at = 0.0

    def initialized(self) -> bool:
        with Session(self.engine) as session:
            return account_exists(session)

    def _check_code(self, supplied: bytes) -> None:
        with self.attempt_lock:
            now = monotonic()
            while self.attempts and self.attempts[0] <= now - 60:
                self.attempts.popleft()
            retry_at = self.retry_at
            if len(self.attempts) >= 5:
                retry_at = max(retry_at, self.attempts[0] + 60)
            if retry_at > now:
                raise BusinessError(
                    BusinessErrorCode.RATE_LIMITED, retry_after=ceil(retry_at - now)
                )
            if not self.attempts:
                self.failures = 0
            self.attempts.append(now)
            code = read_setup_code(self.code_path)
            if code is not None and compare_digest(code, supplied):
                return
            if self.initialized():
                raise BusinessError(BusinessErrorCode.SETUP_ALREADY_INITIALIZED)
            self.failures = min(self.failures + 1, 6)
            self.retry_at = now + 2 ** (self.failures - 1)
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
