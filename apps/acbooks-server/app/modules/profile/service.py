from io import BytesIO
from pathlib import Path
from threading import Lock
from time import time_ns
from uuid import UUID, uuid4

from PIL import Image, ImageOps, UnidentifiedImageError
from sqlalchemy import Engine
from sqlalchemy.orm import Session

from app.core.errors import BusinessError, BusinessErrorCode
from app.db.models.auth import AuthAccount
from app.db.session import create_session_factory
from app.modules.auth.schemas import AccountResponse
from app.modules.auth.service import AuthIdentity

AVATAR_LOCK = Lock()
MAX_AVATAR_BYTES = 5 * 1024 * 1024
MAX_AVATAR_DIMENSION = 8192
AVATAR_SIZE = 512
AVATAR_QUALITY = 88
AVATAR_FORMATS = {"JPEG", "PNG", "WEBP"}


def normalize_avatar(content: bytes) -> bytes:
    if not content:
        raise BusinessError(BusinessErrorCode.INVALID_AVATAR)
    if len(content) > MAX_AVATAR_BYTES:
        raise BusinessError(BusinessErrorCode.AVATAR_TOO_LARGE)
    try:
        with Image.open(BytesIO(content)) as image:
            if image.format not in AVATAR_FORMATS:
                raise BusinessError(BusinessErrorCode.INVALID_AVATAR)
            width, height = image.size
            if not (16 <= width <= MAX_AVATAR_DIMENSION and 16 <= height <= MAX_AVATAR_DIMENSION):
                raise BusinessError(BusinessErrorCode.INVALID_AVATAR)
            image.load()
            upright = ImageOps.exif_transpose(image)
            if upright.mode in {"RGBA", "LA"} or "transparency" in upright.info:
                rgba = upright.convert("RGBA")
                rgb = Image.new("RGB", rgba.size, (255, 255, 255))
                rgb.paste(rgba, mask=rgba.getchannel("A"))
            else:
                rgb = upright.convert("RGB")
            fitted = ImageOps.fit(
                rgb,
                (AVATAR_SIZE, AVATAR_SIZE),
                method=Image.Resampling.LANCZOS,
            )
            output = BytesIO()
            fitted.save(output, format="JPEG", quality=AVATAR_QUALITY, optimize=True)
            return output.getvalue()
    except BusinessError:
        raise
    except (
        UnidentifiedImageError,
        OSError,
        ValueError,
        SyntaxError,
        Image.DecompressionBombError,
    ):
        raise BusinessError(BusinessErrorCode.INVALID_AVATAR) from None


class ProfileService:
    def __init__(self, engine: Engine, avatars_dir: Path) -> None:
        self.read_sessions = create_session_factory(engine)
        self.write_sessions = create_session_factory(
            engine.execution_options(sqlite_begin_immediate=True)
        )
        self.avatars_dir = avatars_dir

    def account(self, identity: AuthIdentity) -> AccountResponse:
        with self.read_sessions() as session:
            revision = self._revision(session, identity.account_id)
        return AccountResponse(
            id=identity.account_id,
            username=identity.username,
            avatar_updated_at=revision,
        )

    def get_avatar(self, identity: AuthIdentity) -> tuple[Path, str]:
        with self.read_sessions() as session:
            revision = self._revision(session, identity.account_id)
        if revision is None:
            raise BusinessError(BusinessErrorCode.AVATAR_NOT_FOUND)
        target = self._target(identity)
        if not target.is_file():
            raise BusinessError(BusinessErrorCode.AVATAR_NOT_FOUND)
        return target, "image/jpeg"

    def save_avatar(self, identity: AuthIdentity, content: bytes) -> AccountResponse:
        processed = normalize_avatar(content)
        target = self._target(identity)
        with AVATAR_LOCK:
            self.avatars_dir.mkdir(parents=True, exist_ok=True)
            pending = target.with_name(f".{target.name}.{uuid4().hex}.tmp")
            try:
                pending.write_bytes(processed)
                now = time_ns() // 1_000_000
                with self.write_sessions.begin() as session:
                    row = session.get(AuthAccount, str(identity.account_id))
                    if row is None:
                        raise BusinessError(BusinessErrorCode.AUTHENTICATION_REQUIRED)
                    row.avatar_updated_at = now
                    row.updated_at = now
                pending.replace(target)
            finally:
                pending.unlink(missing_ok=True)
        return self.account(identity)

    def delete_avatar(self, identity: AuthIdentity) -> AccountResponse:
        target = self._target(identity)
        with AVATAR_LOCK:
            now = time_ns() // 1_000_000
            with self.write_sessions.begin() as session:
                row = session.get(AuthAccount, str(identity.account_id))
                if row is None:
                    raise BusinessError(BusinessErrorCode.AUTHENTICATION_REQUIRED)
                row.avatar_updated_at = None
                row.updated_at = now
            target.unlink(missing_ok=True)
        return self.account(identity)

    def _target(self, identity: AuthIdentity) -> Path:
        return self.avatars_dir / f"{identity.account_id}.jpg"

    @staticmethod
    def _revision(session: Session, account_id: UUID) -> int | None:
        row = session.get(AuthAccount, str(account_id))
        return row.avatar_updated_at if row is not None else None