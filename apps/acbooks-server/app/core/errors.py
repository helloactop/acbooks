from enum import StrEnum


class BusinessErrorCode(StrEnum):
    REVISION_CONFLICT = "REVISION_CONFLICT"
    INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    SETUP_CODE_INVALID = "SETUP_CODE_INVALID"
    SETUP_ALREADY_INITIALIZED = "SETUP_ALREADY_INITIALIZED"
    RATE_LIMITED = "RATE_LIMITED"
    BOOK_NOT_FOUND = "BOOK_NOT_FOUND"
    COVER_NOT_FOUND = "COVER_NOT_FOUND"
    INVALID_BOOK_FILTER = "INVALID_BOOK_FILTER"
    INVALID_COVER = "INVALID_COVER"
    BATCH_LIMIT_EXCEEDED = "BATCH_LIMIT_EXCEEDED"


class BusinessError(Exception):
    def __init__(self, code: BusinessErrorCode, *, retry_after: int | None = None) -> None:
        if retry_after is not None and (
            code != BusinessErrorCode.RATE_LIMITED
            or type(retry_after) is not int
            or retry_after < 0
        ):
            raise ValueError("Retry delay must be non-negative integer seconds for RATE_LIMITED.")
        self.code = code
        self.retry_after = retry_after
        super().__init__(code.value)
