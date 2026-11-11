"""Validate job and storage object states."""

import sqlalchemy as sa
from alembic import op

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None

_STATUS_FIELDS = (
    ("background_jobs", "status", "'queued', 'running', 'succeeded', 'failed'"),
    ("storage_objects", "file_status", "'present', 'missing'"),
)


def upgrade() -> None:
    connection = op.get_bind()
    for table, column, values in _STATUS_FIELDS:
        invalid = connection.execute(
            sa.text(f"SELECT 1 FROM {table} WHERE {column} NOT IN ({values}) LIMIT 1")
        ).scalar()
        if invalid is not None:
            raise RuntimeError(
                f"Unsupported values in {table}.{column}; correct them before retry."
            )

    # Keep existing foreign keys and rows intact by enforcing the rule without a table rebuild.
    for table, column, values in _STATUS_FIELDS:
        for event, operation in (("insert", "INSERT"), ("update", f"UPDATE OF {column}")):
            op.execute(f"""
                CREATE TRIGGER validate_{table}_{column}_{event}
                BEFORE {operation} ON {table}
                FOR EACH ROW WHEN NEW.{column} NOT IN ({values})
                BEGIN
                    SELECT RAISE(ABORT, 'Invalid {table}.{column}');
                END
                """)


def downgrade() -> None:
    for table, column, _ in _STATUS_FIELDS:
        for event in ("insert", "update"):
            op.execute(f"DROP TRIGGER validate_{table}_{column}_{event}")
