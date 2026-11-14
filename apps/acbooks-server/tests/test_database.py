import shutil
import sqlite3
from argparse import Namespace
from collections.abc import Iterator
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import pytest
from alembic import command
from alembic.config import Config
from alembic.operations import Operations
from alembic.script import ScriptDirectory
from sqlalchemy import inspect
from sqlalchemy.engine import Engine
from sqlalchemy.exc import IntegrityError
from sqlalchemy.schema import SchemaItem

from app.db.base import Base
from app.db.models.books import Book
from app.db.models.copies import BookCopy
from app.db.models.storage import BookFile, StorageObject, StorageRoot
from app.db.session import create_session_factory, create_sqlite_engine
from app.db.version import SCHEMA_HEAD_REVISION

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
        "INSERT INTO sync_mutations (id, device_id, mutation_id, created_at)"
        " VALUES ('mutation2', 'device', 'key', 1)",
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
            "INSERT INTO sync_mutations (id, device_id, mutation_id, created_at)"
            " VALUES ('mutation', 'device', 'key', 1)",
            "INSERT INTO auth_accounts VALUES ('account', 1, 'a', 'a', 'hash', 1, 1, 1)",
        ):
            connection.exec_driver_sql(seed)
    with pytest.raises(IntegrityError), database.begin() as connection:
        connection.exec_driver_sql(sql)


def test_reading_constraints_guard_insert_and_update(database: Engine) -> None:
    with database.begin() as connection:
        connection.exec_driver_sql(
            "INSERT INTO books (id, title, reading_status, created_at, updated_at)"
            " VALUES ('book', 'Book', 'unread', 1, 1)"
        )
        connection.exec_driver_sql(
            "INSERT INTO reading_sessions"
            " (id, book_id, started_at, ended_at, active_seconds, source)"
            " VALUES ('read', 'book', 0, 1000, 1, 'manual')"
        )

    for statement in (
        "INSERT INTO books (id, title, reading_status, created_at, updated_at)"
        " VALUES ('other', 'Other', 'paused', 1, 1)",
        "UPDATE books SET reading_status = 'paused' WHERE id = 'book'",
        "INSERT INTO reading_sessions (id, book_id, started_at, ended_at, active_seconds, source)"
        " VALUES ('long', 'book', 0, 1000, 2, 'manual')",
        "UPDATE reading_sessions SET active_seconds = 2 WHERE id = 'read'",
        "UPDATE reading_sessions SET ended_at = 500 WHERE id = 'read'",
    ):
        with pytest.raises(IntegrityError), database.begin() as connection:
            connection.exec_driver_sql(statement)

    with database.begin() as connection:
        connection.exec_driver_sql("UPDATE books SET reading_status = 'done' WHERE id = 'book'")
        connection.exec_driver_sql(
            "UPDATE reading_sessions SET active_seconds = 2, ended_at = 2000 WHERE id = 'read'"
        )
        connection.exec_driver_sql(
            "INSERT INTO reading_sessions (id, book_id, started_at, source)"
            " VALUES ('open', 'book', 0, 'automatic')"
        )


@pytest.mark.parametrize(
    ("seed", "message"),
    [
        (
            "INSERT INTO books (id, title, reading_status, created_at, updated_at)"
            " VALUES ('legacy', 'Legacy', 'paused', 1, 1)",
            "Unsupported reading status in books",
        ),
        (
            "INSERT INTO reading_sessions"
            " (id, book_id, started_at, ended_at, active_seconds, source)"
            " VALUES ('legacy', 'book', 0, 1000, 2, 'manual')",
            "Active time exceeds the reading session duration",
        ),
    ],
)
def test_library_constraint_migration_rejects_invalid_legacy_rows(
    tmp_path: Path, seed: str, message: str
) -> None:
    path = tmp_path / "legacy.db"
    config = migration_config(path)
    command.upgrade(config, "0003")
    engine = create_sqlite_engine(path)
    try:
        with engine.begin() as connection:
            connection.exec_driver_sql(
                "INSERT INTO books (id, title, created_at, updated_at)"
                " VALUES ('book', 'Book', 1, 1)"
            )
            connection.exec_driver_sql(seed)
        with pytest.raises(RuntimeError, match=message):
            command.upgrade(config, "head")
        with engine.connect() as connection:
            assert (
                connection.exec_driver_sql("SELECT version_num FROM alembic_version").scalar_one()
                == "0003"
            )
            assert (
                connection.exec_driver_sql(
                    "SELECT name FROM sqlite_schema WHERE name IN"
                    " ('ix_books_created_at', 'ix_book_copies_book',"
                    " 'validate_books_reading_status_insert')"
                ).all()
                == []
            )
    finally:
        engine.dispose()


def test_library_migration_preserves_existing_rows(tmp_path: Path) -> None:
    path = tmp_path / "upgrade.db"
    config = migration_config(path)
    command.upgrade(config, "0003")
    engine = create_sqlite_engine(path)
    try:
        with engine.begin() as connection:
            connection.exec_driver_sql(
                "INSERT INTO books (id, title, reading_status, created_at, updated_at)"
                " VALUES ('book', 'Kept', 'reading', 1, 2)"
            )
            connection.exec_driver_sql(
                "INSERT INTO reading_sessions"
                " (id, book_id, started_at, ended_at, active_seconds, source)"
                " VALUES ('read', 'book', 0, 60000, 60, 'manual')"
            )

        command.upgrade(config, "head")
        with engine.connect() as connection:
            assert tuple(
                connection.exec_driver_sql(
                    "SELECT title, reading_status, created_at FROM books WHERE id = 'book'"
                ).one()
            ) == ("Kept", "reading", 1)
            assert tuple(
                connection.exec_driver_sql(
                    "SELECT active_seconds FROM reading_sessions WHERE id = 'read'"
                ).one()
            ) == (60,)
            assert (
                connection.exec_driver_sql("PRAGMA foreign_key_check").all() == []
            )

        command.downgrade(config, "0003")
        with engine.connect() as connection:
            assert (
                connection.exec_driver_sql("SELECT title FROM books WHERE id = 'book'").scalar_one()
                == "Kept"
            )
            assert (
                connection.exec_driver_sql(
                    "SELECT name FROM sqlite_schema WHERE name = 'ix_books_created_at'"
                ).all()
                == []
            )
    finally:
        engine.dispose()


def test_library_query_plans_use_indexes(database: Engine) -> None:
    with database.begin() as connection:
        connection.exec_driver_sql("INSERT INTO locations (id, name) VALUES ('room', 'Room')")
        connection.exec_driver_sql(
            "INSERT INTO contributors (id, name) VALUES ('author', 'Author')"
        )
        for position in range(100):
            connection.exec_driver_sql(
                "INSERT INTO books (id, title, reading_status, created_at, updated_at)"
                " VALUES (?, ?, ?, ?, ?)",
                (f"book-{position}", f"Title {position}", "reading", position, position),
            )
            connection.exec_driver_sql(
                "INSERT INTO book_contributors (book_id, contributor_id, role)"
                " VALUES (?, 'author', 'author')",
                (f"book-{position}",),
            )
            connection.exec_driver_sql(
                "INSERT INTO book_copies (id, book_id, location_id) VALUES (?, ?, 'room')",
                (f"copy-{position}", f"book-{position}"),
            )

    def plan(statement: str) -> str:
        with database.connect() as connection:
            rows = connection.exec_driver_sql(f"EXPLAIN QUERY PLAN {statement}")
            return " ".join(str(row[-1]) for row in rows)

    queries = {
        "ix_books_created_at": "SELECT id FROM books ORDER BY created_at DESC, id ASC LIMIT 12",
        "ix_books_updated_at": "SELECT id FROM books ORDER BY updated_at DESC, id ASC LIMIT 12",
        "ix_books_title_nocase": (
            "SELECT id FROM books ORDER BY title COLLATE NOCASE ASC, id ASC LIMIT 12"
        ),
        "ix_books_reading_status_created": (
            "SELECT id FROM books WHERE reading_status = 'reading'"
            " ORDER BY created_at DESC, id ASC LIMIT 12"
        ),
        "ix_book_copies_book": "SELECT id FROM book_copies WHERE book_id = 'book-1'",
        "ix_book_copies_location": "SELECT id FROM book_copies WHERE location_id = 'room'",
        "ix_book_contributors_book_role": (
            "SELECT (SELECT name FROM contributors JOIN book_contributors"
            " ON book_contributors.contributor_id = contributors.id"
            " WHERE book_contributors.book_id = books.id"
            " AND book_contributors.role = 'author'"
            " ORDER BY contributors.name COLLATE NOCASE, contributors.id LIMIT 1) FROM books"
        ),
    }
    for index_name, statement in queries.items():
        assert index_name in plan(statement), f"{index_name} is not used by: {statement}"


def test_schema_head_constant_matches_migrations() -> None:
    script = ScriptDirectory.from_config(Config(str(SERVER_ROOT / "alembic.ini")))
    assert script.get_current_head() == SCHEMA_HEAD_REVISION


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


@pytest.mark.parametrize(
    ("table", "insert"),
    [
        (
            "reading_sessions",
            "INSERT INTO reading_sessions (id, book_id, book_file_id, started_at, source)"
            " VALUES (?, ?, ?, 1, 'manual')",
        ),
        (
            "bookmarks",
            "INSERT INTO bookmarks (id, book_id, book_file_id, created_at, updated_at)"
            " VALUES (?, ?, ?, 1, 1)",
        ),
        (
            "annotations",
            "INSERT INTO annotations (id, book_id, book_file_id, created_at, updated_at)"
            " VALUES (?, ?, ?, 1, 1)",
        ),
    ],
)
def test_reading_file_must_belong_to_book(tmp_path: Path, table: str, insert: str) -> None:
    path = tmp_path / "reading.db"
    config = migration_config(path)
    command.upgrade(config, "0002")
    engine = create_sqlite_engine(path)
    try:
        with engine.begin() as connection:
            connection.exec_driver_sql(
                "INSERT INTO books (id, title, created_at, updated_at)"
                " VALUES ('a', 'A', 1, 1), ('b', 'B', 1, 1)"
            )
            connection.exec_driver_sql(
                "INSERT INTO storage_roots VALUES ('root', 'NAS', '/books', 'external')"
            )
            connection.exec_driver_sql(
                "INSERT INTO storage_objects (id, root_id, relative_path, size_bytes, mtime_ms)"
                " VALUES ('oa', 'root', 'a.epub', 1, 1), ('ob', 'root', 'b.epub', 1, 1)"
            )
            connection.exec_driver_sql(
                "INSERT INTO book_files VALUES ('fa', 'a', 'oa', 'epub'), ('fb', 'b', 'ob', 'epub')"
            )
            connection.exec_driver_sql(insert, ("record", "b", "fa"))

        with pytest.raises(RuntimeError, match=f"Mismatched book and file in {table}"):
            command.upgrade(config, "head")
        with engine.begin() as connection:
            assert (
                connection.exec_driver_sql("SELECT version_num FROM alembic_version").scalar_one()
                == "0002"
            )
            table_sql = connection.exec_driver_sql(
                "SELECT sql FROM sqlite_schema WHERE type = 'table' AND name = ?", (table,)
            ).scalar_one()
            foreign_keys = connection.exec_driver_sql(f"PRAGMA foreign_key_list({table})").all()
            read_record = f"SELECT book_id, book_file_id FROM {table} WHERE id = 'record'"
            assert tuple(connection.exec_driver_sql(read_record).one()) == ("b", "fa")
            connection.exec_driver_sql(f"UPDATE {table} SET book_id = 'a' WHERE id = 'record'")

        command.upgrade(config, "head")
        with engine.begin() as connection:
            assert (
                connection.exec_driver_sql(
                    "SELECT sql FROM sqlite_schema WHERE type = 'table' AND name = ?", (table,)
                ).scalar_one()
                == table_sql
            )
            assert connection.exec_driver_sql(f"PRAGMA foreign_key_list({table})").all() == (
                foreign_keys
            )
            assert tuple(connection.exec_driver_sql(read_record).one()) == ("a", "fa")
            connection.exec_driver_sql(insert, ("physical", "b", None))
            for statement, args in (
                (insert, ("wrong", "b", "fa")),
                (f"UPDATE {table} SET book_id = ? WHERE id = 'record'", ("b",)),
                (f"UPDATE {table} SET book_file_id = ? WHERE id = 'record'", ("fb",)),
                ("UPDATE book_files SET book_id = ? WHERE id = 'fa'", ("b",)),
            ):
                with pytest.raises(IntegrityError):
                    connection.exec_driver_sql(statement, args)
            connection.exec_driver_sql("UPDATE book_files SET book_id = 'a' WHERE id = 'fb'")
            connection.exec_driver_sql("DELETE FROM book_files WHERE id = 'fa'")
            assert tuple(connection.exec_driver_sql(read_record).one()) == ("a", None)
            assert connection.exec_driver_sql("PRAGMA foreign_key_check").all() == []

        command.downgrade(config, "0002")
        with engine.begin() as connection:
            connection.exec_driver_sql(insert, ("legacy", "b", "fb"))
    finally:
        engine.dispose()


@pytest.mark.parametrize("table", ["background_jobs", "storage_objects"])
def test_batch_migration_must_preserve_status_triggers(tmp_path: Path, table: str) -> None:
    path = tmp_path / "batch.db"
    config = migration_config(path)
    scripts = tmp_path / "alembic"
    shutil.copytree(
        SERVER_ROOT / "app/db/alembic", scripts, ignore=shutil.ignore_patterns("__pycache__")
    )
    config.set_main_option("script_location", str(scripts))
    # Exercise an independent batch migration on the pre-sync schema.
    (scripts / "versions/0007_sync.py").unlink()
    command.upgrade(config, "0003")
    migration = scripts / "versions/0006_test_batch.py"
    migration.write_text(
        f"""
import sqlalchemy as sa
from alembic import op

revision = "0007"
down_revision = "0006"
branch_labels = None
depends_on = None

def upgrade():
    with op.batch_alter_table({table!r}, recreate="always") as batch:
        batch.add_column(sa.Column("batch_test", sa.Text()))
""",
        encoding="utf-8",
    )

    engine = create_sqlite_engine(path)
    try:
        seed: tuple[str, ...]
        invalid: tuple[str, ...]
        with engine.begin() as connection:
            if table == "background_jobs":
                column, valid = "status", "queued"
                insert = (
                    "INSERT INTO background_jobs (id, type, status, payload_json, created_at,"
                    " updated_at) VALUES (?, 'scan', ?, '{}', 1, 1)"
                )
                seed = ("existing", valid)
                invalid = ("new", "invalid")
            else:
                column, valid = "file_status", "present"
                connection.exec_driver_sql(
                    "INSERT INTO storage_roots VALUES ('root', 'NAS', '/books', 'external')"
                )
                insert = (
                    "INSERT INTO storage_objects (id, root_id, relative_path, size_bytes,"
                    " file_status, mtime_ms) VALUES (?, 'root', ?, 1, ?, 1)"
                )
                seed = ("existing", "a.epub", valid)
                invalid = ("new", "b.epub", "invalid")
            connection.exec_driver_sql(insert, seed)
        with pytest.raises(
            RuntimeError, match=f"Required SQLite integrity triggers missing: validate_{table}"
        ):
            command.upgrade(config, "head")

        assert "batch_test" not in {column["name"] for column in inspect(engine).get_columns(table)}
        select_state = f"SELECT {column} FROM {table} WHERE id = 'existing'"
        with engine.connect() as connection:
            assert (
                connection.exec_driver_sql("SELECT version_num FROM alembic_version").scalar_one()
                == "0003"
            )
            assert connection.exec_driver_sql(select_state).scalar_one() == valid
            definitions = (
                connection.exec_driver_sql(
                    "SELECT sql FROM sqlite_schema WHERE type = 'trigger' AND tbl_name = ?",
                    (table,),
                )
                .scalars()
                .all()
            )
            assert len(definitions) == 2
        with migration.open("a", encoding="utf-8") as script:
            for definition in definitions:
                script.write(f"    op.execute({definition!r})\n")
        command.upgrade(config, "head")
        with engine.begin() as connection:
            assert (
                connection.exec_driver_sql("SELECT version_num FROM alembic_version").scalar_one()
                == "0007"
            )
            with pytest.raises(IntegrityError, match=f"Invalid {table}"):
                connection.exec_driver_sql(insert, invalid)
            with pytest.raises(IntegrityError, match=f"Invalid {table}"):
                connection.exec_driver_sql(
                    f"UPDATE {table} SET {column} = 'invalid' WHERE id = 'existing'"
                )
            assert connection.exec_driver_sql(select_state).scalar_one() == valid
            assert connection.exec_driver_sql("PRAGMA foreign_key_check").all() == []

        with migration.open("a", encoding="utf-8") as script:
            script.write(f"""

def downgrade():
    with op.batch_alter_table({table!r}, recreate="always") as batch:
        batch.drop_column("batch_test")
""")
        with pytest.raises(
            RuntimeError, match=f"Required SQLite integrity triggers missing: validate_{table}"
        ):
            command.downgrade(config, "0003")
        assert "batch_test" in {field["name"] for field in inspect(engine).get_columns(table)}
        with engine.connect() as connection:
            assert (
                connection.exec_driver_sql("SELECT version_num FROM alembic_version").scalar_one()
                == "0007"
            )
            assert connection.exec_driver_sql(select_state).scalar_one() == valid

        with migration.open("a", encoding="utf-8") as script:
            for definition in definitions:
                script.write(f"    op.execute({definition!r})\n")
        command.downgrade(config, "0003")
        assert "batch_test" not in {field["name"] for field in inspect(engine).get_columns(table)}
        with engine.begin() as connection:
            assert (
                connection.exec_driver_sql("SELECT version_num FROM alembic_version").scalar_one()
                == "0003"
            )
            with pytest.raises(IntegrityError, match=f"Invalid {table}"):
                connection.exec_driver_sql(insert, invalid)
            with pytest.raises(IntegrityError, match=f"Invalid {table}"):
                connection.exec_driver_sql(
                    f"UPDATE {table} SET {column} = 'invalid' WHERE id = 'existing'"
                )
            assert connection.exec_driver_sql(select_state).scalar_one() == valid
            assert connection.exec_driver_sql("PRAGMA foreign_key_check").all() == []
    finally:
        engine.dispose()
