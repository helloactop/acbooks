from pathlib import Path

from alembic import context
from alembic.script import ScriptDirectory
from sqlalchemy.engine import Connection

from app.db.base import Base
from app.db.session import DATABASE_PATH, create_sqlite_engine


def verify_integrity_triggers(connection: Connection) -> None:
    script = ScriptDirectory.from_config(context.config)
    heads = context.get_context().get_current_heads()
    applied = {revision.revision for revision in script.iterate_revisions(heads, "base")}
    required: set[tuple[str, str]] = set()
    if "0002" in applied:
        required.update(
            (f"validate_{table}_{column}_{event}", table)
            for table, column in (
                ("background_jobs", "status"),
                ("storage_objects", "file_status"),
            )
            for event in ("insert", "update")
        )
    if "0003" in applied:
        required.add(("validate_book_files_reading_book_update", "book_files"))
        required.update(
            (f"validate_{table}_file_book_{event}", table)
            for table in ("reading_sessions", "bookmarks", "annotations")
            for event in ("insert", "update")
        )
    if "0004" in applied:
        required.update(
            (f"validate_books_reading_status_{event}", "books") for event in ("insert", "update")
        )
        required.update(
            (f"validate_reading_sessions_active_duration_{event}", "reading_sessions")
            for event in ("insert", "update")
        )
    if required:
        actual = {
            (name, table)
            for name, table in connection.exec_driver_sql(
                "SELECT name, tbl_name FROM sqlite_schema WHERE type = 'trigger'"
            )
        }
        missing = required.difference(actual)
        if missing:
            names = ", ".join(sorted(name for name, _ in missing))
            raise RuntimeError(f"Required SQLite integrity triggers missing: {names}")


def run_migrations() -> None:
    if context.is_offline_mode():
        raise RuntimeError("Run migrations online so SQLite can enforce its connection settings.")

    options = context.get_x_argument(as_dictionary=True)
    path = Path(options["db_path"]) if "db_path" in options else DATABASE_PATH
    engine = create_sqlite_engine(path)
    try:
        with engine.connect().execution_options(sqlite_begin_immediate=True) as connection:
            with connection.begin():
                context.configure(
                    connection=connection,
                    target_metadata=Base.metadata,
                    render_as_batch=True,
                    transactional_ddl=True,
                )
                context.run_migrations()
                verify_integrity_triggers(connection)
    finally:
        engine.dispose()


run_migrations()
