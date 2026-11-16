from io import BytesIO
from pathlib import Path

import pytest
from PIL import Image
from test_setup import challenge, make_client
from test_web_auth import CREDENTIALS, ME, auth_paths  # noqa: F401

AVATAR = "/api/v1/auth/me/avatar"


def image_bytes(size: tuple[int, int] = (800, 600)) -> bytes:
    output = BytesIO()
    Image.new("RGBA", size, (30, 120, 200, 160)).save(output, format="PNG")
    return output.getvalue()


def test_avatar_upload_read_delete_and_revision(
    request: pytest.FixtureRequest, tmp_path: Path
) -> None:
    paths: tuple[Path, Path] = request.getfixturevalue("auth_paths")
    avatars = tmp_path / "avatars"
    with make_client(paths, avatars_dir=avatars) as client:
        login = client.post(
            "/api/v1/auth/login", json=CREDENTIALS, headers=challenge(client)
        )
        assert login.status_code == 200
        assert login.json()["account"]["avatarUpdatedAt"] is None
        assert client.get(AVATAR).status_code == 404

        blocked = client.put(AVATAR, files={"file": ("avatar.png", image_bytes(), "image/png")})
        assert blocked.status_code == 403

        uploaded = client.put(
            AVATAR,
            files={"file": ("avatar.png", image_bytes(), "image/png")},
            headers=challenge(client),
        )
        assert uploaded.status_code == 200, uploaded.text
        assert uploaded.headers["cache-control"] == "no-store"
        revision = uploaded.json()["avatarUpdatedAt"]
        assert isinstance(revision, int) and revision > 0
        assert client.get(ME).json()["avatarUpdatedAt"] == revision

        image = client.get(AVATAR)
        assert image.status_code == 200
        assert image.headers["content-type"] == "image/jpeg"
        assert image.headers["cache-control"] == "private, max-age=0, must-revalidate"
        with Image.open(BytesIO(image.content)) as stored:
            assert stored.format == "JPEG"
            assert stored.size == (512, 512)
        assert len(list(avatars.glob("*.jpg"))) == 1

        removed = client.delete(AVATAR, headers=challenge(client))
        assert removed.status_code == 200
        assert removed.json()["avatarUpdatedAt"] is None
        assert client.get(AVATAR).status_code == 404


def test_avatar_rejects_invalid_and_oversized_content(
    request: pytest.FixtureRequest, tmp_path: Path
) -> None:
    paths: tuple[Path, Path] = request.getfixturevalue("auth_paths")
    with make_client(paths, avatars_dir=tmp_path / "avatars") as client:
        client.post("/api/v1/auth/login", json=CREDENTIALS, headers=challenge(client))

        invalid = client.put(
            AVATAR,
            files={"file": ("avatar.txt", b"not an image", "text/plain")},
            headers=challenge(client),
        )
        assert invalid.status_code == 422
        assert invalid.json()["code"] == "INVALID_AVATAR"

        oversized = client.put(
            AVATAR,
            files={"file": ("avatar.png", b"\x00" * (5 * 1024 * 1024 + 1), "image/png")},
            headers=challenge(client),
        )
        assert oversized.status_code == 413
        assert oversized.json()["code"] == "AVATAR_TOO_LARGE"


def test_device_bearer_can_sync_avatar(
    request: pytest.FixtureRequest, tmp_path: Path
) -> None:
    paths: tuple[Path, Path] = request.getfixturevalue("auth_paths")
    with make_client(paths, avatars_dir=tmp_path / "avatars") as client:
        token = client.post(
            "/api/v1/auth/device-login", json={**CREDENTIALS, "deviceName": "Android"}
        ).json()
        bearer = {"Authorization": f"Bearer {token['accessToken']}"}
        uploaded = client.put(
            AVATAR,
            files={"file": ("avatar.png", image_bytes((640, 640)), "image/png")},
            headers=bearer,
        )
        assert uploaded.status_code == 200, uploaded.text
        assert client.get(AVATAR, headers=bearer).status_code == 200
        assert client.get(AVATAR).status_code == 401


