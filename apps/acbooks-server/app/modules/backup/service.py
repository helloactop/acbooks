from __future__ import annotations

import json
import os
import sqlite3
import tempfile
from collections.abc import Iterator
from contextlib import contextmanager
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from hashlib import sha256
from importlib import import_module
from pathlib import Path
from time import time_ns
from typing import Any

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
    stamp = datetime.now(UTC).strftime("%Y%m%dT%H%M%S%fZ")
    return database.parent.parent / "backups" / f"{database.stem}-{stamp}.db"


def manifest_path(backup: Path) -> Path:
    return backup.with_name(f"{backup.name}.json")


def _resolve(path: Path) -> Path:
    return path.expanduser().resolve()


def _assert_not_database(path: Path, database: Path, label: str) -> None:
    resolved = _resolve(path)
    database = _resolve(database)
    sidecars = {
        database,
        database.with_name(f"{database.name}-wal"),
        database.with_name(f"{database.name}-shm"),
        database.with_name(f"{database.name}-journal"),
    }
    if resolved in sidecars:
        raise BackupError(f"{label} must not point at the database or its journals.")


def _assert_writable_target(path: Path, database: Path) -> None:
    _assert_not_database(path, database, "Backup path")
    if path.is_dir():
        raise BackupError(f"Backup path is a directory: {path}.")


@contextmanager
def database_lock(database: Path) -> Iterator[None]:
    lock_path = database.with_name(f"{database.name}.backup.lock")
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    handle = lock_path.open("a+b")
    locked = False
    try:
        handle.seek(0, os.SEEK_END)
        if handle.tell() == 0:
            handle.write(b"0")
            handle.flush()
        handle.seek(0)
        try:
            module: Any = import_module("msvcrt" if os.name == "nt" else "fcntl")
            if os.name == "nt":
                module.locking(handle.fileno(), module.LK_NBLCK, 1)
            else:
                module.flock(handle.fileno(), module.LOCK_EX | module.LOCK_NB)
            locked = True
        except OSError as error:
            raise BackupError(
                f"Another backup or restore is running for {database.name}."
            ) from error
        yield
    finally:
        try:
            if locked:
                handle.seek(0)
                module = import_module("msvcrt" if os.name == "nt" else "fcntl")
                if os.name == "nt":
                    module.locking(handle.fileno(), module.LK_UNLCK, 1)
                else:
                    module.flock(handle.fileno(), module.LOCK_UN)
        finally:
            handle.close()


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


def _temporary_sibling(target: Path) -> Path:
    descriptor, name = tempfile.mkstemp(
        prefix=f".{target.name}.", suffix=".tmp", dir=target.parent
    )
    os.close(descriptor)
    return Path(name)


def _digest(path: Path) -> tuple[str, int]:
    digest = sha256()
    size = 0
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
            size += len(chunk)
    return digest.hexdigest(), size


def _write_manifest(path: Path, manifest: BackupManifest) -> None:
    temp = _temporary_sibling(path)
    try:
        temp.write_text(
            json.dumps(asdict(manifest), indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        os.replace(temp, path)
    finally:
        temp.unlink(missing_ok=True)


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
    backup = _resolve(backup)
    if not backup.is_file():
        raise BackupError(f"Backup file not found: {backup}.")
    revision = _verify_database(backup)
    digest, size = _digest(backup)
    expected_path = _resolve(manifest) if manifest is not None else manifest_path(backup)
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


def _create_backup(database: Path, target: Path) -> BackupManifest:
    target.parent.mkdir(parents=True, exist_ok=True)
    temp = _temporary_sibling(target)
    try:
        _online_backup(database, temp)
        revision = _verify_database(temp)
        digest, size = _digest(temp)
        os.replace(temp, target)
    finally:
        temp.unlink(missing_ok=True)
    manifest = BackupManifest(
        created_at_ms=time_ns() // 1_000_000,
        database=target.name,
        schema_revision=revision,
        sha256=digest,
        size_bytes=size,
    )
    _write_manifest(manifest_path(target), manifest)
    return manifest


def backup_database(
    output: Path | None = None,
    database: Path = DATABASE_PATH,
    *,
    overwrite: bool = False,
) -> BackupManifest:
    database = _resolve(database)
    if not database.is_file():
        raise BackupError(f"Database not found: {database}.")
    target = _resolve(output) if output is not None else default_backup_path(database)
    _assert_writable_target(target, database)
    if target.exists() and not overwrite:
        raise BackupError(
            f"Backup already exists: {target.name}. Use a new name or --overwrite."
        )
    with database_lock(database):
        return _create_backup(database, target)


def prune_backups(keep: Path, older_than_ms: int) -> list[Path]:
    directory = keep.parent
    removed: list[Path] = []
    if not directory.is_dir():
        return removed
    keep = _resolve(keep)
    for candidate in sorted(directory.glob("*.db")):
        if _resolve(candidate) == keep:
            continue
        sidecar = manifest_path(candidate)
        if not sidecar.is_file():
            continue
        try:
            manifest = read_manifest(sidecar)
        except BackupError:
            continue
        if manifest.created_at_ms >= older_than_ms:
            continue
        candidate.unlink(missing_ok=True)
        sidecar.unlink(missing_ok=True)
        removed.append(candidate)
    return removed


def _prepare_offline(database: Path) -> None:
    if not database.is_file():
        return
    try:
        connection = sqlite3.connect(database)
    except sqlite3.Error as error:
        raise BackupError(f"Cannot open database {database.name}.") from error
    try:
        connection.execute("PRAGMA busy_timeout=0")
        checkpoint = connection.execute("PRAGMA wal_checkpoint(TRUNCATE)").fetchone()
        if checkpoint is not None and int(checkpoint[0]) != 0:
            raise BackupError(
                "Database checkpoint is busy; stop API and Worker before restoring."
            )
        connection.execute("BEGIN EXCLUSIVE")
        connection.rollback()
    except BackupError:
        raise
    except sqlite3.Error as error:
        raise BackupError(
            "Database is in use; stop API and Worker before restoring."
        ) from error
    finally:
        connection.close()
    # Remove journals only after a successful checkpoint and exclusive probe,
    # so committed WAL data is never discarded.
    for suffix in ("-wal", "-shm"):
        try:
            database.with_name(f"{database.name}{suffix}").unlink(missing_ok=True)
        except OSError as error:
            raise BackupError(
                "Database files are still open; stop API and Worker before restoring."
            ) from error


def restore_database(
    backup: Path,
    database: Path = DATABASE_PATH,
    manifest: Path | None = None,
    *,
    offline: bool = False,
) -> Path | None:
    if not offline:
        raise BackupError(
            "Restore requires --offline after stopping API and Worker."
        )
    database = _resolve(database)
    backup = _resolve(backup)
    _assert_not_database(backup, database, "Backup path")
    if manifest is not None:
        _assert_not_database(manifest, database, "Manifest path")
    expected = verify_backup(backup, manifest)
    if expected.schema_revision > SCHEMA_HEAD_REVISION:
        raise BackupError(
            f"Backup schema {expected.schema_revision} is newer than "
            f"{SCHEMA_HEAD_REVISION}; upgrade the server before restoring."
        )

    with database_lock(database):
        _prepare_offline(database)
        snapshot: Path | None = None
        mode: int | None = None
        if database.is_file():
            stamp = datetime.now(UTC).strftime("%Y%m%dT%H%M%S%fZ")
            snapshot = database.with_name(f"{database.name}.pre-restore-{stamp}")
            _create_backup(database, snapshot)
            mode = database.stat().st_mode

        database.parent.mkdir(parents=True, exist_ok=True)
        temp = _temporary_sibling(database)
        try:
            _online_backup(backup, temp)
            if _verify_database(temp) != expected.schema_revision:
                raise BackupError("Restored database revision does not match the backup.")
            if mode is not None:
                os.chmod(temp, mode)
            try:
                os.replace(temp, database)
            except OSError as error:
                raise BackupError(
                    "Cannot replace the database; stop API and Worker before restoring."
                ) from error
        finally:
            temp.unlink(missing_ok=True)
    return snapshot
