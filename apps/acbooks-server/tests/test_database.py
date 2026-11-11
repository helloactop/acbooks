import sqlite3
from argparse import Namespace
from collections.abc import Iterator
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import pytest
from alembic import command
from alembic.config import Config
from alembic.operations import Operations
from sqlalchemy import inspect
from sqlalchemy.engine import Engine
from sqlalchemy.exc import IntegrityError
from sqlalchemy.schema import SchemaItem

from app.db.base import Base
from app.db.models.books import Book
from app.db.models.copies import BookCopy
from app.db.models.storage import BookFile, StorageObject, StorageRoot
from app.db.session import create_session_factory, create_sqlite_engine

SERVER_ROOT = Path(__file__).resolve().parents[1]


def migration_config(path: Path) -> Config:
    return Config(
        str(SERVER_ROOT / "alembic.ini"),
        cmd_opts=Namespace(x=[f"db_path={path}"]),
    )


@pytest.fixture
def database(tmp_path: Path) -> Iterator[Engine]:
    path = tmp_path / "acbooks.db"
    command.upgrade(migration_config(path), "head")
    engine = create_sqlite_engine(path)
    try:
        yield engine
    finally:
        engine.dispose()


def test_initial_migration_and_connection_settings(tmp_path: Path) -> None:
    path = tmp_path / "acbooks.db"
    config = migration_config(path)
    command.upgrade(config, "head")
    command.check(config)
    engine = create_sqlite_engine(path)
    try:
        assert set(inspect(engine).get_table_names()) == {*Base.metadata.tables, "alembic_version"}
        with engine.connect() as first, engine.connect() as second:
            for connection in (first, second):
                for pragma, expected in (
                    ("foreign_keys", 1),
                    ("busy_timeout", 5000),
                    ("journal_mode", "wal"),
                    ("synchronous", 2),
                ):
                    assert connection.exec_driver_sql(f"PRAGMA {pragma}").scalar_one() == expected
                assert connection.exec_driver_sql("PRAGMA foreign_key_check").all() == []
        with engine.begin() as connection:
            connection.exec_driver_sql("INSERT INTO locations (id, name) VALUES ('room', 'Room')")
            connection.exec_driver_sql("INSERT INTO locations VALUES ('shelf', 'room', 'Shelf')")
        command.downgrade(config, "base")
        assert inspect(engine).get_table_names() == ["alembic_version"]
        command.upgrade(config, "head")
        command.check(config)
    finally:
        engine.dispose()


def test_interrupted_migration_rolls_back_and_can_be_retried(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    path = tmp_path / "interrupted.db"
    config = migration_config(path)
    create_table = Operations.create_table

    def interrupt(
        operations: Operations,
        name: str,
        *columns: SchemaItem,
        if_not_exists: bool | None = None,
        **options: object,
    ) -> None:
        create_table(operations, name, *columns, if_not_exists=if_not_exists, **options)
        raise RuntimeError("simulated migration failure")

    with monkeypatch.context() as patch:
        patch.setattr(Operations, "create_table", interrupt)
        with pytest.raises(RuntimeError, match="simulated migration failure"):
            command.upgrade(config, "head")

    engine = create_sqlite_engine(path)
    try:
        assert inspect(engine).get_table_names() == []
        command.upgrade(config, "head")
        command.check(config)
    finally:
        engine.dispose()


def test_book_orm_supports_copies_and_multiple_files(database: Engine) -> None:
    sessions = create_session_factory(database)
    with sessions.begin() as session:
        session.add(StorageRoot(id="root", name="NAS", root_path="/books", mode="external"))
        session.flush()
        session.add_all(
            StorageObject(
                id=path,
                root_id="root",
                relative_path=path,
                size_bytes=20,
                mtime_ms=1,
                sha256="same-hash",
            )
            for path in ("a.epub", "a.pdf")
        )
        session.flush()
        session.add(
            Book(
                id="book",
                title="Same title",
                created_at=1,
                updated_at=1,
                copies=[BookCopy(id="copy")],
                files=[
                    BookFile(id="epub", storage_object_id="a.epub", format="epub"),
                    BookFile(id="pdf", storage_object_id="a.pdf", format="pdf"),
                ],
            )
        )
        session.add(Book(id="other", title="Same title", created_at=1, updated_at=1))
    with sessions() as session:
        book = session.get(Book, "book")
        assert book is not None
        assert book.reading_status == "unread"
        assert [copy.id for copy in book.copies] == ["copy"]
        assert {file.id for file in book.files} == {"epub", "pdf"}
        other = session.get(Book, "other")
        assert other is not None and other.files == []

    with database.begin() as connection:
        connection.exec_driver_sql(
            "INSERT INTO reading_sessions (id, book_id, book_file_id, started_at, source)"
            " VALUES ('read', 'book', 'epub', 1, 'automatic')"
        )
        connection.exec_driver_sql(
            "INSERT INTO annotations (id, book_id, book_file_id, note, created_at, updated_at)"
            " VALUES ('note', 'book', 'epub', 'Note', 1, 1)"
        )
        connection.exec_driver_sql(
            "UPDATE storage_objects SET file_status = 'missing' WHERE id = 'a.epub'"
        )
        assert connection.exec_driver_sql("SELECT COUNT(*) FROM books").scalar_one() == 2
        assert connection.exec_driver_sql("SELECT COUNT(*) FROM book_files").scalar_one() == 2
        assert connection.exec_driver_sql("SELECT book_id FROM reading_sessions").scalar_one() == (
            "book"
        )
        assert connection.exec_driver_sql("SELECT book_id FROM annotations").scalar_one() == "book"


@pytest.mark.parametrize(
    "sql",
    [
        "INSERT INTO book_copies (id, book_id) VALUES ('copy', 'missing')",
        "INSERT INTO storage_roots VALUES ('bad-root', 'NAS', '/books', 'invalid')",
        "INSERT INTO storage_objects (id, root_id, relative_path, size_bytes, mtime_ms)"
        " VALUES ('object2', 'root', 'book.epub', 1, 1)",
        "INSERT INTO book_files VALUES ('file2', 'book', 'object', 'epub')",
        "INSERT INTO reading_sessions (id, book_id, started_at, ended_at, active_seconds, source)"
        " VALUES ('bad', 'book', 10, 9, 0, 'manual')",
        "INSERT INTO sync_mutations VALUES ('mutation2', 'device', 'key', 1)",
        "INSERT INTO auth_accounts VALUES ('account2', 2, 'b', 'b', 'hash', 1, 1, 1)",
        "INSERT INTO auth_sessions (id, account_id, session_token_hash, created_at, expires_at)"
        " VALUES ('session', 'account', 'digest', 10, 10)",
        "INSERT INTO auth_tokens (id, account_id, token_hash, kind, scope, label,"
        " created_at, expires_at) VALUES ('token', 'account', 'digest', 'application',"
        " 'full', 'OPDS', 1, 10)",
        "DELETE FROM books WHERE id = 'book'",
    ],
)
def test_database_constraints(database: Engine, sql: str) -> None:
    with database.begin() as connection:
        for seed in (
            "INSERT INTO books (id, title, created_at, updated_at) VALUES ('book', 'Book', 1, 1)",
            "INSERT INTO storage_roots VALUES ('root', 'NAS', '/books', 'external')",
            "INSERT INTO storage_objects (id, root_id, relative_path, size_bytes, mtime_ms)"
            " VALUES ('object', 'root', 'book.epub', 1, 1)",
            "INSERT INTO book_files VALUES ('file', 'book', 'object', 'epub')",
            "INSERT INTO devices (id, name) VALUES ('device', 'Reader')",
            "INSERT INTO sync_mutations VALUES ('mutation', 'device', 'key', 1)",
            "INSERT INTO auth_accounts VALUES ('account', 1, 'a', 'a', 'hash', 1, 1, 1)",
        ):
            connection.exec_driver_sql(seed)
    with pytest.raises(IntegrityError), database.begin() as connection:
        connection.exec_driver_sql(sql)


def test_only_one_admin_can_be_created_concurrently(database: Engine) -> None:
    def create_admin(name: str) -> bool:
        try:
            with database.connect().execution_options(sqlite_begin_immediate=True) as connection:
                with connection.begin():
                    connection.exec_driver_sql(
                        "INSERT INTO auth_accounts VALUES (?, 1, ?, ?, 'hash', 1, 1, 1)",
                        (name, name, name),
                    )
        except IntegrityError:
            return False
        return True

    with ThreadPoolExecutor(max_workers=2) as pool:
        assert sorted(pool.map(create_admin, ["first", "second"])) == [False, True]


@pytest.mark.parametrize(
    ("table", "column", "allowed", "insert"),
    [
        (
            "background_jobs",
            "status",
            ("queued", "running", "succeeded", "failed"),
            "INSERT INTO background_jobs VALUES (?, 'scan', ?, '{}', 1, 1)",
        ),
        (
            "storage_objects",
            "file_status",
            ("present", "missing"),
            "INSERT INTO storage_objects"
            " (id, root_id, relative_path, size_bytes, file_status, mtime_ms)"
            " VALUES (?, 'root', 'second.epub', 1, ?, 1)",
        ),
    ],
)
def test_status_validation_on_existing_database(
    tmp_path: Path, table: str, column: str, allowed: tuple[str, ...], insert: str
) -> None:
    path = tmp_path / "existing.db"
    config = migration_config(path)
    command.upgrade(config, "0001")
    engine = create_sqlite_engine(path)
    try:
        with engine.begin() as connection:
            connection.exec_driver_sql(
                "INSERT INTO books (id, title, created_at, updated_at)"
                " VALUES ('book', 'Book', 1, 1)"
            )
            connection.exec_driver_sql(
                "INSERT INTO storage_roots VALUES ('root', 'NAS', '/books', 'external')"
            )
            connection.exec_driver_sql(
                "INSERT INTO storage_objects (id, root_id, relative_path, size_bytes, mtime_ms)"
                " VALUES ('object', 'root', 'book.epub', 1, 1)"
            )
            connection.exec_driver_sql(
                "INSERT INTO book_files VALUES ('file', 'book', 'object', 'epub')"
            )
            connection.exec_driver_sql(insert, ("legacy", "invalid"))
        with pytest.raises(RuntimeError, match=rf"Unsupported values in {table}\.{column}"):
            command.upgrade(config, "head")
        with engine.begin() as connection:
            assert (
                connection.exec_driver_sql("SELECT version_num FROM alembic_version").scalar_one()
                == "0001"
            )
            select_status = f"SELECT {column} FROM {table} WHERE id = 'legacy'"
            update_status = f"UPDATE {table} SET {column} = ? WHERE id = 'legacy'"
            assert connection.exec_driver_sql(select_status).scalar_one() == "invalid"
            connection.exec_driver_sql(update_status, (allowed[0],))

        command.upgrade(config, "head")
        with engine.begin() as connection:
            assert connection.exec_driver_sql(select_status).scalar_one() == allowed[0]
            file = connection.exec_driver_sql(
                "SELECT book_id, storage_object_id FROM book_files WHERE id = 'file'"
            ).one()
            assert tuple(file) == ("book", "object")
            assert connection.exec_driver_sql("PRAGMA foreign_key_check").all() == []
            for status in allowed:
                connection.exec_driver_sql(update_status, (status,))
            with pytest.raises(IntegrityError, match=f"Invalid {table}.{column}"):
                connection.exec_driver_sql(update_status, ("invalid",))
            assert connection.exec_driver_sql(select_status).scalar_one() == allowed[-1]
            connection.exec_driver_sql(f"DELETE FROM {table} WHERE id = 'legacy'")
            with pytest.raises(IntegrityError, match=f"Invalid {table}.{column}"):
                connection.exec_driver_sql(insert, ("new", "invalid"))
            connection.exec_driver_sql(insert, ("legacy", allowed[0]))

        command.downgrade(config, "0001")
        with engine.begin() as connection:
            connection.exec_driver_sql(update_status, ("invalid",))
            assert connection.exec_driver_sql(select_status).scalar_one() == "invalid"
    finally:
        engine.dispose()


def test_transactional_ddl_and_online_backup(database: Engine, tmp_path: Path) -> None:
    with pytest.raises(RuntimeError), database.begin() as connection:
        connection.exec_driver_sql("CREATE TABLE interrupted (id INTEGER)")
        raise RuntimeError("interrupted")
    assert "interrupted" not in inspect(database).get_table_names()

    backup_path = tmp_path / "backup.db"
    with database.connect() as connection:
        with connection.begin():
            connection.exec_driver_sql(
                "INSERT INTO books (id, title, created_at, updated_at) VALUES ('b', 'Book', 1, 1)"
            )
        target = sqlite3.connect(backup_path)
        try:
            source = connection.connection.driver_connection
            assert isinstance(source, sqlite3.Connection)
            source.backup(target)
        finally:
            target.close()
    restored = create_sqlite_engine(backup_path)
    try:
        with restored.connect() as connection:
            assert connection.exec_driver_sql("SELECT title FROM books").scalar_one() == "Book"
            assert connection.exec_driver_sql("PRAGMA integrity_check").scalar_one() == "ok"
    finally:
        restored.dispose()
