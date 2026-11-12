import sqlite3
from pathlib import Path

from sqlalchemy import URL, Connection, Engine, create_engine, event
from sqlalchemy.orm import Session, sessionmaker

from app.db import models as models

DATABASE_PATH = Path("/data/acbooks/database/acbooks.db")


def create_sqlite_engine(database_path: Path = DATABASE_PATH) -> Engine:
    engine = create_engine(
        URL.create("sqlite+pysqlite", database=str(database_path)), hide_parameters=True
    )

    @event.listens_for(engine, "connect")
    def configure_connection(dbapi_connection: sqlite3.Connection, _record: object) -> None:
        # SQLAlchemy starts transactions explicitly, including for SQLite DDL.
        dbapi_connection.isolation_level = None
        cursor = dbapi_connection.cursor()
        try:
            cursor.execute("PRAGMA busy_timeout=5000")
            cursor.execute("PRAGMA foreign_keys=ON")
            cursor.execute("PRAGMA synchronous=FULL")
            cursor.execute("PRAGMA journal_mode")
            if cursor.fetchone()[0] != "wal":
                cursor.execute("PRAGMA journal_mode=WAL")
                if cursor.fetchone()[0] != "wal":
                    raise RuntimeError("SQLite could not enable WAL.")
        finally:
            cursor.close()

    @event.listens_for(engine, "begin")
    def begin_transaction(connection: Connection) -> None:
        if connection.get_execution_options().get("sqlite_begin_immediate"):
            connection.exec_driver_sql("BEGIN IMMEDIATE")
        else:
            connection.exec_driver_sql("BEGIN")

    return engine


def create_session_factory(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(bind=engine)
