"""Add library query indexes and reading data constraints."""

import sqlalchemy as sa
from alembic import op

revision = "0004"
down_revision = "0003"
branch_labels = None
depends_on = None

_READING_STATUSES = "'unread', 'reading', 'done'"

_INDEXES = (
    # Mixed directions mirror the stable list ORDER BY so SQLite can skip the sort.
    "CREATE INDEX ix_books_created_at ON books (created_at DESC, id ASC)",
    "CREATE INDEX ix_books_updated_at ON books (updated_at DESC, id ASC)",
    "CREATE INDEX ix_books_title_nocase ON books (title COLLATE NOCASE ASC, id ASC)",
    "CREATE INDEX ix_books_reading_status_created"
    " ON books (reading_status, created_at DESC, id ASC)",
    "CREATE INDEX ix_book_copies_book ON book_copies (book_id)",
    "CREATE INDEX ix_book_copies_location ON book_copies (location_id)",
    "CREATE INDEX ix_book_contributors_book_role"
    " ON book_contributors (book_id, role, contributor_id)",
)


def upgrade() -> None:
    connection = op.get_bind()
    invalid_status = connection.execute(
        sa.text(f"SELECT 1 FROM books WHERE reading_status NOT IN ({_READING_STATUSES}) LIMIT 1")
    ).scalar()
    if invalid_status is not None:
        raise RuntimeError("Unsupported reading status in books; correct them before retry.")

    invalid_duration = connection.execute(
        sa.text(
            "SELECT 1 FROM reading_sessions WHERE ended_at IS NOT NULL"
            " AND active_seconds * 1000 > ended_at - started_at LIMIT 1"
        )
    ).scalar()
    if invalid_duration is not None:
        raise RuntimeError(
            "Active time exceeds the reading session duration; correct them before retry."
        )

    for statement in _INDEXES:
        op.execute(statement)

    # Triggers keep existing rows and foreign keys intact without a table rebuild.
    for event, operation in (("insert", "INSERT"), ("update", "UPDATE OF reading_status")):
        op.execute(f"""
            CREATE TRIGGER validate_books_reading_status_{event}
            BEFORE {operation} ON books
            FOR EACH ROW WHEN NEW.reading_status NOT IN ({_READING_STATUSES})
            BEGIN
                SELECT RAISE(ABORT, 'Invalid books.reading_status');
            END
            """)

    for event, operation in (
        ("insert", "INSERT"),
        ("update", "UPDATE OF started_at, ended_at, active_seconds"),
    ):
        op.execute(f"""
            CREATE TRIGGER validate_reading_sessions_active_duration_{event}
            BEFORE {operation} ON reading_sessions
            FOR EACH ROW WHEN NEW.ended_at IS NOT NULL
                AND NEW.active_seconds * 1000 > NEW.ended_at - NEW.started_at
            BEGIN
                SELECT RAISE(ABORT, 'Active time exceeds reading session duration');
            END
            """)


def downgrade() -> None:
    for event in ("insert", "update"):
        op.execute(f"DROP TRIGGER validate_reading_sessions_active_duration_{event}")
        op.execute(f"DROP TRIGGER validate_books_reading_status_{event}")
    for name in (
        "ix_book_contributors_book_role",
        "ix_book_copies_location",
        "ix_book_copies_book",
        "ix_books_reading_status_created",
        "ix_books_title_nocase",
        "ix_books_updated_at",
        "ix_books_created_at",
    ):
        op.execute(f"DROP INDEX {name}")
