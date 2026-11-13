"""Run the Docker Compose deployment tests against an isolated Docker project."""

import json
import os
import re
import ssl
import subprocess
import sys
from http.cookiejar import CookieJar
from pathlib import Path
from secrets import token_hex, token_urlsafe
from tempfile import TemporaryDirectory
from time import monotonic, sleep
from typing import Any
from urllib.error import HTTPError
from urllib.request import (
    HTTPCookieProcessor,
    HTTPSHandler,
    OpenerDirector,
    ProxyHandler,
    Request,
    build_opener,
)

ROOT = Path(__file__).resolve().parents[4]
API = "/api/v1"
CODE = "/data/acbooks/secrets/setup-code"
PROXY_IMAGE = "caddy:2-alpine"


class Deployment:
    def __init__(self, directory: Path) -> None:
        self.project = f"acbooks-test-{token_hex(6)}"
        self.directory = directory
        self.env = {
            **os.environ,
            "ACBOOKS_DATA_DIR": "",
            "ACBOOKS_PORT": "0",
            "ACBOOKS_BIND_ADDRESS": "127.0.0.1",
            "ACBOOKS_PUBLIC_ORIGIN": "http://127.0.0.1:8000",
            "ACBOOKS_FORWARDED_ALLOW_IPS": "127.0.0.1",
            "COMPOSE_ANSI": "never",
        }
        env_file = directory / "empty.env"
        env_file.touch()
        self.command = [
            "compose",
            "--env-file",
            str(env_file),
            "-p",
            self.project,
            "-f",
            str(ROOT / "deploy" / "compose.yaml"),
            "-f",
            str(
                self.override(
                    "proxy",
                    {
                        "proxy": {
                            "image": PROXY_IMAGE,
                            "command": [
                                "caddy",
                                "reverse-proxy",
                                "--from",
                                "https://localhost",
                                "--to",
                                "api:8000",
                                "--internal-certs",
                                "--disable-redirects",
                            ],
                            "ports": ["127.0.0.1:0:443"],
                        }
                    },
                )
            ),
        ]

    def override(self, name: str, services: dict[str, Any]) -> Path:
        path = self.directory / f"{name}.json"
        path.write_text(json.dumps({"services": services}), encoding="utf-8")
        return path

    def docker(self, *args: str, expected: int = 0) -> str:
        result = subprocess.run(
            ["docker", *args],
            cwd=ROOT,
            env=self.env,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=360,
            check=False,
        )
        if result.returncode != expected:
            raise RuntimeError(
                f"docker {args[0]} returned {result.returncode}, expected {expected}\n"
                f"{result.stdout}\n{result.stderr}"
            )
        return result.stdout if expected == 0 else result.stderr

    def compose(self, *args: str, expected: int = 0) -> str:
        return self.docker(*self.command, *args, expected=expected)

    def container(self, service: str) -> str:
        container_id = self.compose("ps", "-a", "-q", service).strip()
        assert container_id, f"Missing {service} container"
        return container_id

    def inspect(self, service: str, expression: str) -> str:
        return self.docker("inspect", "--format", expression, self.container(service)).strip()


def get_json(
    client: OpenerDirector, request: Request | str, expected: int = 200
) -> tuple[dict[str, Any], str]:
    try:
        response = client.open(request, timeout=10)
    except HTTPError as error:
        response = error
    with response:
        assert response.status == expected, f"HTTP {response.status}, expected {expected}"
        return json.load(response), response.headers.get("Set-Cookie", "")


def get_text(
    client: OpenerDirector, request: Request | str, expected: int = 200
) -> tuple[str, str]:
    try:
        response = client.open(request, timeout=10)
    except HTTPError as error:
        response = error
    with response:
        assert response.status == expected, f"HTTP {response.status}, expected {expected}"
        return response.read().decode("utf-8"), response.headers.get("Content-Type", "")


def proxy_client(deploy: Deployment) -> tuple[OpenerDirector, str]:
    if platform := deploy.env.get("DOCKER_DEFAULT_PLATFORM"):
        deploy.docker("pull", "--platform", platform, PROXY_IMAGE)
    deploy.compose("up", "-d", "proxy")
    proxy_address = deploy.inspect(
        "proxy", "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}"
    )
    assert proxy_address, "Proxy has no Compose network address"
    port = deploy.compose("port", "proxy", "443").strip().rsplit(":", 1)[1]
    origin = f"https://localhost:{port}"
    deploy.env["ACBOOKS_PUBLIC_ORIGIN"] = origin
    deploy.env["ACBOOKS_FORWARDED_ALLOW_IPS"] = proxy_address
    deadline = monotonic() + 30
    while True:
        try:
            ca = deploy.compose(
                "exec", "-T", "proxy", "cat", "/data/caddy/pki/authorities/local/root.crt"
            )
            break
        except RuntimeError:
            if monotonic() >= deadline:
                raise
            sleep(1)
    context = ssl.create_default_context(cadata=ca)
    client = build_opener(
        ProxyHandler({}), HTTPSHandler(context=context), HTTPCookieProcessor(CookieJar())
    )
    return client, origin


def verify_migration_gate(deploy: Deployment) -> None:
    override = deploy.override("bad-migration", {"migrate": {"user": "10002:10002"}})
    deploy.compose("-f", str(override), "up", "-d", "--no-build", expected=1)
    assert deploy.inspect("migrate", "{{.State.Status}} {{.State.ExitCode}}") == "exited 1"
    for service in ("api", "worker"):
        assert deploy.inspect(service, "{{.State.Status}}") == "created"
    print("Failed migration blocks API and Worker startup", flush=True)


def verify_fresh_install(deploy: Deployment, client: OpenerDirector, origin: str) -> str:
    deploy.compose("up", "-d", "--build", "--wait", "--wait-timeout", "90")
    assert deploy.inspect("migrate", "{{.State.Status}} {{.State.ExitCode}}") == "exited 0"
    for service in ("api", "worker"):
        assert deploy.inspect(service, "{{.State.Health.Status}}") == "healthy"
    assert (
        len({deploy.inspect(service, "{{.Image}}") for service in ("migrate", "api", "worker")})
        == 1
    )
    deploy.compose("exec", "-T", "api", "python", "-m", "alembic", "current", "--check-heads")
    deploy.compose(
        "exec",
        "-T",
        "api",
        "python",
        "-c",
        """
import os
import sqlite3
import stat
from contextlib import closing
from app.db.session import DATABASE_PATH
from app.modules.auth.setup_code import SETUP_CODE_PATH

assert os.getuid() == os.getgid() == 10001
assert stat.S_IMODE(SETUP_CODE_PATH.parent.stat().st_mode) == 0o700
assert stat.S_IMODE(SETUP_CODE_PATH.stat().st_mode) == 0o600
with closing(sqlite3.connect(DATABASE_PATH)) as db:
    assert db.execute("PRAGMA journal_mode").fetchone() == ("wal",)
""",
    )
    assert get_json(client, origin + API + "/health")[0] == {"status": "ok"}
    assert get_json(client, origin + API + "/ready")[0] == {"status": "ok", "migration": "current"}
    assert get_json(client, origin + API + "/setup/status")[0] == {"initialized": False}
    index, content_type = get_text(client, origin + "/")
    assert "text/html" in content_type
    assert 'id="root"' in index
    deep_link, _ = get_text(client, origin + "/library")
    assert 'id="root"' in deep_link
    get_text(client, origin + API + "/missing", expected=404)
    backup = deploy.compose(
        "exec",
        "-T",
        "api",
        "python",
        "-m",
        "app.modules.backup",
        "backup",
        "--output",
        "/data/acbooks/backups/smoke.db",
    )
    assert '"sha256"' in backup
    deploy.compose(
        "exec",
        "-T",
        "api",
        "python",
        "-m",
        "app.modules.backup",
        "verify",
        "/data/acbooks/backups/smoke.db",
    )
    api_address = deploy.compose("port", "api", "8000").strip()
    direct = Request(f"http://{api_address}{API}/auth/csrf", headers={"X-Forwarded-Proto": "https"})
    get_json(build_opener(ProxyHandler({})), direct, expected=403)

    code = deploy.compose("exec", "-T", "api", "cat", CODE).strip()
    assert re.fullmatch(r"[A-Za-z0-9_-]{43}", code), "Unexpected setup code format"
    password = token_urlsafe(32)
    payload = {
        "username": "Compose Admin",
        "password": password,
        "confirmPassword": password,
        "setupCode": code,
    }
    result = submit_setup(client, origin, payload, expected=201)
    assert result["username"] == payload["username"]
    assert get_json(client, origin + API + "/setup/status")[0] == {"initialized": True}
    deploy.compose("exec", "-T", "api", "test", "!", "-e", CODE)
    assert (
        submit_setup(client, origin, payload, expected=409)["code"] == "SETUP_ALREADY_INITIALIZED"
    )
    login_token, _ = get_json(client, origin + API + "/auth/csrf")
    logged_in, session_cookie = get_json(
        client,
        Request(
            origin + API + "/auth/login",
            json.dumps({"username": payload["username"], "password": password}).encode(),
            {
                "Content-Type": "application/json",
                "Origin": origin,
                "X-CSRF-Token": login_token["csrfToken"],
            },
        ),
    )
    assert logged_in == {"account": result}
    assert session_cookie.startswith("__Host-acbooks-session=")
    assert "Secure" in session_cookie
    assert get_json(client, origin + API + "/auth/me")[0] == result
    account = result["id"]
    assert isinstance(account, str)
    print("Fresh migration and administrator setup via verified HTTPS passed", flush=True)
    return account


def submit_setup(
    client: OpenerDirector, origin: str, payload: dict[str, str], expected: int
) -> dict[str, Any]:
    challenge, cookie = get_json(client, origin + API + "/auth/csrf")
    assert cookie.startswith("__Host-acbooks-preauth=")
    assert all(attribute in cookie for attribute in ("HttpOnly", "Secure", "SameSite=strict"))
    request = Request(
        origin + API + "/setup",
        json.dumps(payload).encode(),
        {
            "Content-Type": "application/json",
            "Origin": origin,
            "X-CSRF-Token": challenge["csrfToken"],
        },
    )
    return get_json(client, request, expected=expected)[0]


def verify_restart(deploy: Deployment, client: OpenerDirector, origin: str, account: str) -> None:
    # A one-off container uses the same data volume and a separate filesystem.
    result = deploy.compose("run", "--rm", "--no-deps", "worker", expected=1)
    assert "Another worker already holds the database lock." in result
    deploy.compose("stop", "worker")
    assert "Worker stopped." in deploy.compose("logs", "worker")
    previous_api = deploy.container("api")
    deploy.compose("rm", "--stop", "--force", "api", "worker", "migrate")
    verify_migration_gate(deploy)
    deploy.compose("up", "-d", "--no-build", "--wait", "--wait-timeout", "90")
    assert deploy.container("api") != previous_api
    assert get_json(client, origin + API + "/setup/status")[0] == {"initialized": True}
    assert get_json(client, origin + API + "/auth/me")[0]["id"] == account
    logout_token, _ = get_json(client, origin + API + "/auth/csrf")
    logout = Request(
        origin + API + "/auth/logout",
        method="POST",
        headers={"Origin": origin, "X-CSRF-Token": logout_token["csrfToken"]},
    )
    with client.open(logout, timeout=10) as response:
        assert response.status == 204 and response.read() == b""
    get_json(client, origin + API + "/auth/me", expected=401)
    deploy.compose("exec", "-T", "api", "test", "!", "-e", CODE)
    deploy.compose(
        "exec",
        "-T",
        "api",
        "python",
        "-c",
        """
import sqlite3
import sys
from contextlib import closing

from app.db.session import DATABASE_PATH

with closing(sqlite3.connect(DATABASE_PATH)) as db:
    assert db.execute("SELECT id FROM auth_accounts").fetchall() == [(sys.argv[1],)]
""",
        account,
    )
    print("Worker exclusion, graceful shutdown and administrator persistence passed", flush=True)


def verify_worker_readiness(deploy: Deployment) -> None:
    override = deploy.override(
        "unready-worker",
        {
            "migrate": {"command": ["true"]},
            "worker": {"command": ["sleep", "120"]},
        },
    )
    deploy.compose(
        "-f",
        str(override),
        "up",
        "-d",
        "--no-build",
        "--wait",
        "--wait-timeout",
        "20",
        "worker",
        expected=1,
    )
    assert deploy.inspect("worker", "{{.State.Status}}") == "running"
    assert deploy.inspect("worker", "{{.State.Health.Status}}") in {"starting", "unhealthy"}
    print("Compose wait rejects a running Worker that has not initialized", flush=True)


def main() -> None:
    with TemporaryDirectory(prefix="acbooks-compose-") as directory:
        deploy = Deployment(Path(directory))
        try:
            deploy.compose("config", "--quiet")
            client, origin = proxy_client(deploy)
            account = verify_fresh_install(deploy, client, origin)
            verify_restart(deploy, client, origin, account)
            verify_worker_readiness(deploy)
        except Exception:
            print(deploy.compose("ps", "-a"), file=sys.stderr)
            print(deploy.compose("logs", "--no-color", "--tail", "80"), file=sys.stderr)
            raise
        finally:
            deploy.compose("down", "--volumes", "--remove-orphans")


if __name__ == "__main__":
    main()
