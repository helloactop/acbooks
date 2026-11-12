from urllib.parse import urlsplit

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="ACBOOKS_", frozen=True)

    enable_docs: bool = False
    public_origin: str = "http://127.0.0.1:8000"

    @field_validator("public_origin")
    @classmethod
    def validate_public_origin(cls, value: str) -> str:
        origin = urlsplit(value)
        host = origin.hostname
        if (
            not host
            or not host.isascii()
            or any(char.isspace() for char in value)
            or origin.username is not None
            or value != f"{origin.scheme}://{origin.netloc}"
            or (
                origin.scheme != "https"
                and not (origin.scheme == "http" and host in {"localhost", "127.0.0.1", "::1"})
            )
        ):
            raise ValueError("Use an HTTPS origin or a local HTTP origin.")
        authority = f"[{host}]" if ":" in host else host
        if origin.port is not None and origin.port != (443 if origin.scheme == "https" else 80):
            authority += f":{origin.port}"
        return f"{origin.scheme}://{authority}"
