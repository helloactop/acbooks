"""Enforce that reading records and their files belong to the same book."""

from alembic import op

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None

_READING_TABLES = ("reading_sessions", "bookmarks", "annotations")


def upgrade() -> None:
    connection = op.get_bind()
    for table in _READING_TABLES:
        mismatch = connection.exec_driver_sql(f"""
            SELECT 1 FROM {table} AS reading
            WHERE reading.book_file_id IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM book_files
                WHERE id = reading.book_file_id AND book_id = reading.book_id
            ) LIMIT 1
            """).scalar()
        if mismatch is not None:
            raise RuntimeError(f"Mismatched book and file in {table}; correct them before retry.")

        for event, operation in (
            ("insert", "INSERT"),
            ("update", "UPDATE OF book_id, book_file_id"),
        ):
            op.execute(f"""
                CREATE TRIGGER validate_{table}_file_book_{event}
                BEFORE {operation} ON {table}
                FOR EACH ROW WHEN NEW.book_file_id IS NOT NULL AND NOT EXISTS (
                    SELECT 1 FROM book_files
                    WHERE id = NEW.book_file_id AND book_id = NEW.book_id
                )
                BEGIN
                    SELECT RAISE(ABORT, 'Book file must belong to the same book');
                END
                """)

    references = " OR ".join(
        f"EXISTS (SELECT 1 FROM {table} WHERE book_file_id = OLD.id)" for table in _READING_TABLES
    )
    op.execute(f"""
        CREATE TRIGGER validate_book_files_reading_book_update
        BEFORE UPDATE OF book_id ON book_files
        FOR EACH ROW WHEN NEW.book_id <> OLD.book_id AND ({references})
        BEGIN
            SELECT RAISE(ABORT, 'Book file is referenced by reading records');
        END
        """)


def downgrade() -> None:
    op.execute("DROP TRIGGER validate_book_files_reading_book_update")
    for table in _READING_TABLES:
        for event in ("insert", "update"):
            op.execute(f"DROP TRIGGER validate_{table}_file_book_{event}")
