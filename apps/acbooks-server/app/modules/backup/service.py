from __future__ import annotations

import json
import os
import sqlite3
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from hashlib import sha256
from pathlib import Path
from time import time_ns

from app.db.session import DATABASE_PATH
from app.db.version import SCHEMA_HEAD_REVISION


class BackupError(RuntimeError):
    pass


@dataclass(frozen=True)
class BackupManifest:
    created_at_ms: int
    database: str
    schema_revision: str
    sha256: str
    size_bytes: int


def default_backup_path(database: Path = DATABASE_PATH) -> Path:
    stamp = datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ")
    return database.parent.parent / "backups" / f"{database.stem}-{stamp}.db"


def manifest_path(backup: Path) -> Path:
    return backup.with_name(f"{backup.name}.json")


def _connect(path: Path, *, read_only: bool = False) -> sqlite3.Connection:
    if read_only:
        connection = sqlite3.connect(f"{path.resolve().as_uri()}?mode=ro", uri=True)
    else:
        connection = sqlite3.connect(path)
    connection.execute("PRAGMA busy_timeout=5000")
    return connection


def _verify_database(path: Path) -> str:
    try:
        connection = _connect(path, read_only=True)
    except sqlite3.Error as error:
        raise BackupError(f"Cannot open database {path.name}.") from error
    try:
        integrity = connection.execute("PRAGMA integrity_check").fetchone()
        if integrity is None or integrity[0] != "ok":
            raise BackupError(f"SQLite integrity check failed for {path.name}.")
        if connection.execute("PRAGMA foreign_key_check").fetchall():
            raise BackupError(f"SQLite foreign key check failed for {path.name}.")
        row = connection.execute("SELECT version_num FROM alembic_version").fetchone()
        if row is None:
            raise BackupError(f"{path.name} has no Alembic revision.")
        return str(row[0])
    except sqlite3.Error as error:
        raise BackupError(f"Cannot verify database {path.name}.") from error
    finally:
        connection.close()


def _online_backup(source: Path, target: Path) -> None:
    try:
        source_connection = _connect(source)
        target_connection = sqlite3.connect(target)
    except sqlite3.Error as error:
        raise BackupError(f"Cannot open {source.name} for copying.") from error
    try:
        source_connection.backup(target_connection)
    except sqlite3.Error as error:
        raise BackupError(f"Cannot copy {source.name}.") from error
    finally:
        target_connection.close()
        source_connection.close()


def _digest(path: Path) -> tuple[str, int]:
    digest = sha256()
    size = 0
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
            size += len(chunk)
    return digest.hexdigest(), size


def _write_manifest(path: Path, manifest: BackupManifest) -> None:
    temp = path.with_name(f".{path.name}.tmp")
    temp.write_text(
        json.dumps(asdict(manifest), indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    os.replace(temp, path)


def read_manifest(path: Path) -> BackupManifest:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise BackupError(f"Cannot read backup manifest {path.name}.") from error
    if not isinstance(payload, dict):
        raise BackupError(f"Invalid backup manifest {path.name}.")
    try:
        return BackupManifest(
            created_at_ms=int(payload["created_at_ms"]),
            database=str(payload["database"]),
            schema_revision=str(payload["schema_revision"]),
            sha256=str(payload["sha256"]),
            size_bytes=int(payload["size_bytes"]),
        )
    except (KeyError, TypeError, ValueError) as error:
        raise BackupError(f"Invalid backup manifest {path.name}.") from error


def verify_backup(backup: Path, manifest: Path | None = None) -> BackupManifest:
    if not backup.is_file():
        raise BackupError(f"Backup file not found: {backup}.")
    revision = _verify_database(backup)
    digest, size = _digest(backup)
    expected_path = manifest if manifest is not None else manifest_path(backup)
    created_at = time_ns() // 1_000_000
    if expected_path.is_file():
        expected = read_manifest(expected_path)
        if (
            expected.sha256 != digest
            or expected.size_bytes != size
            or expected.schema_revision != revision
        ):
            raise BackupError(f"Backup does not match its manifest: {backup.name}.")
        created_at = expected.created_at_ms
    elif manifest is not None:
        raise BackupError(f"Backup manifest not found: {manifest}.")
    return BackupManifest(
        created_at_ms=created_at,
        database=backup.name,
        schema_revision=revision,
        sha256=digest,
        size_bytes=size,
    )


def backup_database(
    output: Path | None = None, database: Path = DATABASE_PATH
) -> BackupManifest:
    if not database.is_file():
        raise BackupError(f"Database not found: {database}.")
    target = output if output is not None else default_backup_path(database)
    target.parent.mkdir(parents=True, exist_ok=True)
    temp = target.with_name(f".{target.name}.tmp")
    temp.unlink(missing_ok=True)
    try:
        _online_backup(database, temp)
        revision = _verify_database(temp)
        digest, size = _digest(temp)
        manifest = BackupManifest(
            created_at_ms=time_ns() // 1_000_000,
            database=target.name,
            schema_revision=revision,
            sha256=digest,
            size_bytes=size,
        )
        os.replace(temp, target)
    finally:
        temp.unlink(missing_ok=True)
    _write_manifest(manifest_path(target), manifest)
    return manifest


def restore_database(
    backup: Path,
    database: Path = DATABASE_PATH,
    manifest: Path | None = None,
) -> Path | None:
    expected = verify_backup(backup, manifest)
    if expected.schema_revision > SCHEMA_HEAD_REVISION:
        raise BackupError(
            f"Backup schema {expected.schema_revision} is newer than "
            f"{SCHEMA_HEAD_REVISION}; upgrade the server before restoring."
        )

    snapshot: Path | None = None
    mode: int | None = None
    if database.is_file():
        stamp = datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ")
        snapshot = database.with_name(f"{database.name}.pre-restore-{stamp}")
        backup_database(snapshot, database)
        mode = database.stat().st_mode

    database.parent.mkdir(parents=True, exist_ok=True)
    temp = database.with_name(f".{database.name}.restore.tmp")
    temp.unlink(missing_ok=True)
    try:
        _online_backup(backup, temp)
        if _verify_database(temp) != expected.schema_revision:
            raise BackupError("Restored database revision does not match the backup.")
        if mode is not None:
            os.chmod(temp, mode)
        database.with_name(f"{database.name}-wal").unlink(missing_ok=True)
        database.with_name(f"{database.name}-shm").unlink(missing_ok=True)
        os.replace(temp, database)
    finally:
        temp.unlink(missing_ok=True)
    return snapshot
