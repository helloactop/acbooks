from typing import Annotated, Self
from unicodedata import category, normalize
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, SecretStr, field_validator, model_validator


class LoginRequest(BaseModel):
    model_config = ConfigDict(strict=True, extra="forbid")

    username: Annotated[str, Field(min_length=1, max_length=128)]
    password: Annotated[SecretStr, Field(min_length=1, max_length=1024)]

    @field_validator("username")
    @classmethod
    def validate_username(cls, value: str) -> str:
        username = normalize("NFKC", value).strip()
        if not 1 <= len(username) <= 128 or any(
            category(char).startswith("C") for char in username
        ):
            raise ValueError("Invalid username.")
        return username

    @field_validator("password")
    @classmethod
    def validate_password_encoding(cls, value: SecretStr) -> SecretStr:
        value.get_secret_value().encode("utf-8")
        return value


class SetupRequest(LoginRequest):
    password: Annotated[SecretStr, Field(min_length=12, max_length=1024)]
    confirm_password: Annotated[
        SecretStr, Field(alias="confirmPassword", min_length=12, max_length=1024)
    ]
    setup_code: Annotated[SecretStr, Field(alias="setupCode", min_length=1, max_length=128)]

    @model_validator(mode="after")
    def validate_credentials(self) -> Self:
        password = self.password.get_secret_value()
        self.setup_code.get_secret_value().encode("utf-8")
        if not password.strip() or password != self.confirm_password.get_secret_value():
            raise ValueError("Invalid password confirmation.")
        return self


class SetupStatus(BaseModel):
    initialized: bool


class AccountResponse(BaseModel):
    id: UUID
    username: str


class CsrfResponse(BaseModel):
    csrf_token: str = Field(serialization_alias="csrfToken")


class LoginResponse(BaseModel):
    account: AccountResponse
