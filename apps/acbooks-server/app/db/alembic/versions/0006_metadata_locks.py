"""Track explicit metadata edits, including intentionally empty fields."""

from alembic import op

revision = "0006"
down_revision = "0005"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("ALTER TABLE books ADD COLUMN series TEXT")
    op.execute("ALTER TABLE books ADD COLUMN metadata_locks INTEGER NOT NULL DEFAULT 0")


def downgrade() -> None:
    op.execute("ALTER TABLE books DROP COLUMN metadata_locks")
    op.execute("ALTER TABLE books DROP COLUMN series")
