from pathlib import Path

from alembic import context

from app.db.base import Base
from app.db.session import DATABASE_PATH, create_sqlite_engine


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
    finally:
        engine.dispose()


run_migrations()
