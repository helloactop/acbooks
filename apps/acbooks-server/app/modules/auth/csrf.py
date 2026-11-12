from hmac import compare_digest, digest
from secrets import token_bytes, token_urlsafe
from time import time

CHALLENGE_TTL = 600


class PreAuthCsrf:
    def __init__(self) -> None:
        self.key = token_bytes(32)

    def _sign(self, challenge: str, expires: int) -> str:
        return digest(self.key, f"preauth:{challenge}:{expires}".encode(), "sha256").hex()

    def issue(self) -> tuple[str, str]:
        challenge = token_urlsafe(32)
        expires = int(time()) + CHALLENGE_TTL
        return challenge, f"{expires}.{self._sign(challenge, expires)}"

    def valid(self, challenge: str, token: str) -> bool:
        expires, separator, signature = token.partition(".")
        return (
            len(challenge) == 43
            and challenge.isascii()
            and separator == "."
            and 1 <= len(expires) <= 12
            and expires.isascii()
            and expires.isdecimal()
            and len(signature) == 64
            and signature.isascii()
            and int(expires) > int(time())
            and compare_digest(signature, self._sign(challenge, int(expires)))
        )
