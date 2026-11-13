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
    COPY_NOT_FOUND = "COPY_NOT_FOUND"
    LOCATION_NOT_FOUND = "LOCATION_NOT_FOUND"
    LOCATION_HAS_CHILDREN = "LOCATION_HAS_CHILDREN"
    LOCATION_CYCLE = "LOCATION_CYCLE"
    FILE_NOT_FOUND = "FILE_NOT_FOUND"
    FILE_FORMAT_UNSUPPORTED = "FILE_FORMAT_UNSUPPORTED"
    FILE_TOO_LARGE = "FILE_TOO_LARGE"
    FILE_DUPLICATE = "FILE_DUPLICATE"
    FILE_EXTERNAL_PROTECTED = "FILE_EXTERNAL_PROTECTED"
    STORAGE_ROOT_NOT_FOUND = "STORAGE_ROOT_NOT_FOUND"
    STORAGE_ROOT_INVALID = "STORAGE_ROOT_INVALID"
    STORAGE_ROOT_OVERLAP = "STORAGE_ROOT_OVERLAP"
    STORAGE_ROOT_IN_USE = "STORAGE_ROOT_IN_USE"
    JOB_NOT_FOUND = "JOB_NOT_FOUND"


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
