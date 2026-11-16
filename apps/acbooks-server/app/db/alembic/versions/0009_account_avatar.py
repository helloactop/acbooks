"""Add profile avatar revision to accounts."""

import sqlalchemy as sa
from alembic import op

revision = "0009_account_avatar"
down_revision = "0008_file_hash"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("auth_accounts", sa.Column("avatar_updated_at", sa.Integer(), nullable=True))


def downgrade() -> None:
    op.drop_column("auth_accounts", "avatar_updated_at")