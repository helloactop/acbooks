import sys
from pathlib import Path
from time import time_ns
from uuid import uuid4

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.models.auth import AuthAccount
from app.db.session import create_sqlite_engine
from app.modules.auth.repository import add_account
from app.modules.auth.service import password_hasher


def main() -> None:
    database, username, password = sys.argv[1:4]
    engine = create_sqlite_engine(Path(database))
    try:
        with Session(engine) as session, session.begin():
            if session.scalar(select(AuthAccount.id)) is not None:
                return
            now = time_ns() // 1_000_000
            add_account(
                session,
                str(uuid4()),
                username,
                username.casefold(),
                password_hasher.hash(password),
                now,
            )
    finally:
        engine.dispose()


if __name__ == "__main__":
    main()
