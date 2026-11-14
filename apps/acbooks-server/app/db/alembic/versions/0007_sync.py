"""Record synchronized changes transactionally across all write paths."""

from alembic import op

revision = "0007_sync"
down_revision = "0006"
branch_labels = None
depends_on = None

ENTITIES = {
    "books": ("book", "id"),
    "book_files": ("book_file", "id"),
    "reading_positions": ("reading_position", "book_file_id"),
    "reading_sessions": ("reading_session", "id"),
    "bookmarks": ("bookmark", "id"),
    "annotations": ("annotation", "id"),
}
RELATIONS = (
    "book_contributors",
    "book_identifiers",
    "book_tags",
    "shelf_books",
    "book_copies",
)
PARENTS = {
    "contributors": ("book_contributors", "contributor_id"),
    "tags": ("book_tags", "tag_id"),
    "shelves": ("shelf_books", "shelf_id"),
}
NOW = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"


def log(entity: str, ids: str, operation: str = "upsert") -> str:
    return f"""
    INSERT INTO sync_changes(entity_type, entity_id, operation, changed_at)
    SELECT '{entity}', ids.entity_id, '{operation}', {NOW}
    FROM ({ids}) AS ids;
    """


def trigger(table: str, action: str, body: str) -> None:
    op.execute(f"""
    CREATE TRIGGER sync_{table}_{action.lower()} AFTER {action} ON {table}
    BEGIN {body} END
    """)


def upgrade() -> None:
    op.execute("ALTER TABLE sync_mutations ADD COLUMN request_hash TEXT")
    op.execute("ALTER TABLE sync_mutations ADD COLUMN receipt_json TEXT")
    # Older Reader entries used the private position row ID.
    op.execute("DELETE FROM sync_changes WHERE entity_type = 'reading_position'")
    op.create_index("ix_sync_changes_entity", "sync_changes", ["entity_type", "entity_id", "seq"])

    for table, (entity, key) in ENTITIES.items():
        for action in ("INSERT", "UPDATE", "DELETE"):
            row = "OLD" if action == "DELETE" else "NEW"
            operation = "delete" if action == "DELETE" else "upsert"
            body = log(entity, f"SELECT {row}.{key} AS entity_id", operation)
            if table == "book_files":
                books = "SELECT OLD.book_id AS entity_id" if action != "INSERT" else ""
                if action != "DELETE":
                    books += (" UNION " if books else "") + "SELECT NEW.book_id AS entity_id"
                body += log("book", books)
            trigger(table, action, body)
        op.execute(log(entity, f"SELECT {key} AS entity_id FROM {table}"))

    for table in RELATIONS:
        for action in ("INSERT", "UPDATE", "DELETE"):
            ids = "SELECT OLD.book_id AS entity_id" if action != "INSERT" else ""
            if action != "DELETE":
                ids += (" UNION " if ids else "") + "SELECT NEW.book_id AS entity_id"
            trigger(table, action, log("book", ids))

    for table, (relation, key) in PARENTS.items():
        trigger(
            table,
            "UPDATE",
            log(
                "book",
                f"SELECT DISTINCT book_id AS entity_id FROM {relation} WHERE {key} = NEW.id",
            ),
        )

    trigger(
        "storage_objects",
        "UPDATE",
        log("book_file", "SELECT id AS entity_id FROM book_files WHERE storage_object_id = NEW.id")
        + log(
            "book", "SELECT book_id AS entity_id FROM book_files WHERE storage_object_id = NEW.id"
        ),
    )
    trigger(
        "locations",
        "UPDATE",
        log(
            "book",
            "SELECT DISTINCT book_id AS entity_id FROM book_copies WHERE location_id = NEW.id",
        ),
    )


def downgrade() -> None:
    for table in (*ENTITIES, *RELATIONS):
        for action in ("insert", "update", "delete"):
            op.execute(f"DROP TRIGGER sync_{table}_{action}")
    for table in (*PARENTS, "storage_objects", "locations"):
        op.execute(f"DROP TRIGGER sync_{table}_update")
    op.drop_index("ix_sync_changes_entity", table_name="sync_changes")
    op.execute("DELETE FROM sync_changes WHERE entity_type = 'reading_position'")
    op.execute("ALTER TABLE sync_mutations DROP COLUMN receipt_json")
    op.execute("ALTER TABLE sync_mutations DROP COLUMN request_hash")
