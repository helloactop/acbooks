from hmac import compare_digest, digest
from secrets import token_bytes
from time import time
from typing import Literal

CHALLENGE_TTL = 600


CsrfScope = Literal["preauth", "session"]


class CsrfTokens:
    def __init__(self) -> None:
        self.key = token_bytes(32)

    def _sign(self, scope: CsrfScope, binding: str, expires: int) -> str:
        return digest(self.key, f"{scope}:{binding}:{expires}".encode(), "sha256").hex()

    def issue(self, scope: CsrfScope, binding: str) -> str:
        expires = int(time()) + CHALLENGE_TTL
        return f"{expires}.{self._sign(scope, binding, expires)}"

    def valid(self, scope: CsrfScope, binding: str, token: str) -> bool:
        expires, separator, signature = token.partition(".")
        return (
            len(binding) == (43 if scope == "preauth" else 36)
            and binding.isascii()
            and separator == "."
            and 1 <= len(expires) <= 12
            and expires.isascii()
            and expires.isdecimal()
            and len(signature) == 64
            and signature.isascii()
            and int(expires) > int(time())
            and compare_digest(signature, self._sign(scope, binding, int(expires)))
        )
