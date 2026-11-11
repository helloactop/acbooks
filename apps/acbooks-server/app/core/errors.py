from enum import StrEnum


class BusinessErrorCode(StrEnum):
    REVISION_CONFLICT = "REVISION_CONFLICT"
    INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    SETUP_CODE_INVALID = "SETUP_CODE_INVALID"
    SETUP_ALREADY_INITIALIZED = "SETUP_ALREADY_INITIALIZED"
    RATE_LIMITED = "RATE_LIMITED"


class BusinessError(Exception):
    def __init__(self, code: BusinessErrorCode) -> None:
        self.code = code
        super().__init__(code.value)
