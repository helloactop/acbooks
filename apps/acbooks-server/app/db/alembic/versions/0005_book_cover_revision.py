"""Add the cover revision used for cache busting."""

from alembic import op

revision = "0005"
down_revision = "0004"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("ALTER TABLE books ADD COLUMN cover_updated_at INTEGER")


def downgrade() -> None:
    op.execute("ALTER TABLE books DROP COLUMN cover_updated_at")
