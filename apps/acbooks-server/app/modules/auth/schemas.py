from typing import Annotated, Literal, Self
from unicodedata import category, normalize
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, SecretStr, field_validator, model_validator

from app.modules.books.schemas import LaxApiModel


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


def clean_label(value: str) -> str:
    label = value.strip()
    if not label or any(category(char).startswith("C") for char in label):
        raise ValueError("Invalid credential label.")
    return label


class DeviceLoginRequest(LoginRequest):
    device_name: Annotated[str, Field(alias="deviceName", min_length=1, max_length=128)] = "Device"

    _clean_name = field_validator("device_name")(clean_label)


class PasswordChange(BaseModel):
    model_config = ConfigDict(strict=True, extra="forbid")
    current_password: Annotated[
        SecretStr, Field(alias="currentPassword", min_length=1, max_length=1024)
    ]
    new_password: Annotated[SecretStr, Field(alias="newPassword", min_length=12, max_length=1024)]

    @model_validator(mode="after")
    def validate_passwords(self) -> Self:
        LoginRequest.validate_password_encoding(self.current_password)
        password = LoginRequest.validate_password_encoding(self.new_password).get_secret_value()
        if not password.strip():
            raise ValueError("Password cannot be blank.")
        return self


class TokenCreate(BaseModel):
    model_config = ConfigDict(strict=True, extra="forbid")
    label: Annotated[str, Field(min_length=1, max_length=128)]
    scope: Literal["read"]

    _clean_label = field_validator("label")(clean_label)


class TokenResponse(LaxApiModel):
    id: UUID
    kind: Literal["device", "application"]
    scope: Literal["full", "read"]
    label: str
    created_at: int
    expires_at: int
    revoked_at: int | None


class DeviceLoginResponse(LaxApiModel):
    access_token: str
    token_type: Literal["Bearer"] = "Bearer"
    token_id: UUID
    expires_at: int


class IssuedToken(LaxApiModel):
    id: UUID
    token: str
    expires_at: int


class TokenPage(LaxApiModel):
    items: list[TokenResponse]
    total: int
    limit: int
    offset: int
