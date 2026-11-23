"""Store the publisher and publication date on each book."""

import sqlalchemy as sa
from alembic import op

revision = "0010_book_publication"
down_revision = "0009_account_avatar"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("books", sa.Column("publisher", sa.Text(), nullable=True))
    op.add_column("books", sa.Column("publication_date", sa.Text(), nullable=True))


def downgrade() -> None:
    op.drop_column("books", "publication_date")
    op.drop_column("books", "publisher")
