"""Index exact file hash matches."""

from alembic import op

revision = "0008_file_hash"
down_revision = "0007_sync"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_index("ix_storage_objects_sha256", "storage_objects", ["sha256"])


def downgrade() -> None:
    op.drop_index("ix_storage_objects_sha256", table_name="storage_objects")
