import json
import sqlite3
from pathlib import Path

import pytest
from alembic import command
from test_database import migration_config

from app.db.version import SCHEMA_HEAD_REVISION
from app.modules.backup.service import (
    BackupError,
    backup_database,
    database_lock,
    manifest_path,
    prune_backups,
    restore_database,
    verify_backup,
)


def _insert(path: Path, prefix: str, titles: tuple[str, ...]) -> None:
    connection = sqlite3.connect(path)
    try:
        connection.executemany(
            "INSERT INTO books (id, title, created_at, updated_at) VALUES (?, ?, 1, 1)",
            [(f"{prefix}-{index}", title) for index, title in enumerate(titles)],
        )
        connection.commit()
    finally:
        connection.close()


def create_database(path: Path, titles: tuple[str, ...]) -> None:
    command.upgrade(migration_config(path), "head")
    _insert(path, "book", titles)


def add_titles(path: Path, titles: tuple[str, ...]) -> None:
    _insert(path, "extra", titles)


def read_titles(path: Path) -> list[str]:
    connection = sqlite3.connect(path)
    try:
        return [row[0] for row in connection.execute("SELECT title FROM books ORDER BY id")]
    finally:
        connection.close()


def test_backup_writes_manifest_and_verifies(tmp_path: Path) -> None:
    database = tmp_path / "acbooks.db"
    create_database(database, ("Book A",))
    output = tmp_path / "backups" / "snapshot.db"

    manifest = backup_database(output, database)

    assert output.is_file()
    assert manifest_path(output).is_file()
    assert manifest.database == "snapshot.db"
    assert manifest.schema_revision == SCHEMA_HEAD_REVISION
    assert manifest.size_bytes == output.stat().st_size
    assert verify_backup(output) == manifest
    assert read_titles(output) == ["Book A"]


def test_restore_replaces_database_and_keeps_pre_restore_snapshot(tmp_path: Path) -> None:
    database = tmp_path / "acbooks.db"
    create_database(database, ("Original",))
    output = tmp_path / "snapshot.db"
    backup_database(output, database)
    add_titles(database, ("Later",))
    assert read_titles(database) == ["Original", "Later"]

    snapshot = restore_database(output, database, offline=True)

    assert snapshot is not None and snapshot.is_file()
    assert snapshot.name.startswith("acbooks.db.pre-restore-")
    assert read_titles(database) == ["Original"]
    assert read_titles(snapshot) == ["Original", "Later"]
    assert not database.with_name("acbooks.db-wal").exists()


def test_restore_rejects_newer_schema(tmp_path: Path) -> None:
    database = tmp_path / "acbooks.db"
    create_database(database, ("Book",))
    connection = sqlite3.connect(database)
    try:
        connection.execute("UPDATE alembic_version SET version_num = '9999'")
        connection.commit()
    finally:
        connection.close()
    output = tmp_path / "newer.db"
    backup_database(output, database)

    target = tmp_path / "restore-target.db"
    with pytest.raises(BackupError, match="newer than"):
        restore_database(output, target, offline=True)
    assert not target.exists()


def test_verify_rejects_tampered_backup_and_manifest(tmp_path: Path) -> None:
    database = tmp_path / "acbooks.db"
    create_database(database, ("Book",))
    output = tmp_path / "snapshot.db"
    backup_database(output, database)

    manifest = manifest_path(output)
    payload = json.loads(manifest.read_text(encoding="utf-8"))
    payload["sha256"] = "0" * 64
    manifest.write_text(json.dumps(payload), encoding="utf-8")
    with pytest.raises(BackupError, match="manifest"):
        verify_backup(output)

    backup_database(output, database, overwrite=True)
    output.write_bytes(output.read_bytes() + b"tampered")
    with pytest.raises(BackupError, match="manifest"):
        verify_backup(output)


def test_verify_rejects_non_database_file(tmp_path: Path) -> None:
    backup = tmp_path / "broken.db"
    backup.write_bytes(b"not a sqlite database")

    with pytest.raises(BackupError, match="Cannot verify|integrity"):
        verify_backup(backup)


def test_backup_refuses_overwrite_and_database_paths(tmp_path: Path) -> None:
    database = tmp_path / "acbooks.db"
    create_database(database, ("Book",))
    output = tmp_path / "snapshot.db"

    backup_database(output, database)
    with pytest.raises(BackupError, match="already exists"):
        backup_database(output, database)
    backup_database(output, database, overwrite=True)

    for unsafe in (
        database,
        database.with_name("acbooks.db-wal"),
        database.with_name("acbooks.db-shm"),
    ):
        with pytest.raises(BackupError, match="must not point"):
            backup_database(unsafe, database)
    with pytest.raises(BackupError, match="must not point"):
        restore_database(output, output, offline=True)


def test_backup_lock_blocks_concurrent_operations(tmp_path: Path) -> None:
    database = tmp_path / "acbooks.db"
    create_database(database, ("Book",))

    with database_lock(database):
        with pytest.raises(BackupError, match="Another backup or restore"):
            backup_database(tmp_path / "snapshot.db", database)


def test_restore_requires_offline_and_an_idle_database(tmp_path: Path) -> None:
    database = tmp_path / "acbooks.db"
    create_database(database, ("Original",))
    output = tmp_path / "snapshot.db"
    backup_database(output, database)
    add_titles(database, ("Later",))

    with pytest.raises(BackupError, match="offline"):
        restore_database(output, database)

    busy = sqlite3.connect(database)
    try:
        busy.execute("BEGIN IMMEDIATE")
        busy.execute(
            "INSERT INTO books (id, title, created_at, updated_at)"
            " VALUES ('pending', 'Pending', 1, 1)"
        )
        with pytest.raises(BackupError, match="busy|in use"):
            restore_database(output, database, offline=True)
    finally:
        busy.rollback()
        busy.close()

    restore_database(output, database, offline=True)
    assert read_titles(database) == ["Original"]


def test_prune_backups_removes_only_old_managed_files(tmp_path: Path) -> None:
    database = tmp_path / "acbooks.db"
    create_database(database, ("Book",))
    backups = []
    for index in range(3):
        path = tmp_path / f"backup-{index}.db"
        backup_database(path, database)
        backups.append(path)
    for path, created_at in ((backups[0], 1), (backups[1], 1), (backups[2], 2)):
        manifest = manifest_path(path)
        payload = json.loads(manifest.read_text(encoding="utf-8"))
        payload["created_at_ms"] = created_at
        manifest.write_text(json.dumps(payload), encoding="utf-8")

    unmanaged = tmp_path / "manual.db"
    unmanaged.write_bytes(b"keep me")

    removed = prune_backups(backups[2], 2)

    assert {path.name for path in removed} == {"backup-0.db", "backup-1.db"}
    assert backups[2].is_file()
    assert manifest_path(backups[2]).is_file()
    assert unmanaged.is_file()
    assert not list(tmp_path.glob(".*.tmp"))
