import logging
import signal
import sys
from threading import Event
from types import FrameType
from typing import TextIO

from sqlalchemy import select

from app.db.models.jobs import BackgroundJob
from app.db.session import DATABASE_PATH, create_sqlite_engine

logger = logging.getLogger(__name__)


def acquire_worker_lock(lock: TextIO) -> None:
    if sys.platform != "win32":
        from fcntl import LOCK_EX, LOCK_NB, flock

        try:
            flock(lock, LOCK_EX | LOCK_NB)
        except BlockingIOError:
            raise SystemExit("Another worker already holds the database lock.") from None
    else:
        raise SystemExit("Run the worker in a Linux container.")


def main() -> None:
    if not DATABASE_PATH.is_file():
        raise SystemExit("Run the database migration before starting the worker.")

    logging.basicConfig(level=logging.INFO)
    stop = Event()

    def request_stop(_signum: int, _frame: FrameType | None) -> None:
        stop.set()

    signal.signal(signal.SIGTERM, request_stop)
    signal.signal(signal.SIGINT, request_stop)

    with DATABASE_PATH.with_name(".worker.lock").open("a", encoding="utf-8") as lock:
        acquire_worker_lock(lock)
        engine = create_sqlite_engine()
        try:
            with engine.connect() as connection:
                connection.execute(select(BackgroundJob.id).limit(0))
            logger.info("Worker started.")
            stop.wait()
        finally:
            engine.dispose()
        logger.info("Worker stopped.")


if __name__ == "__main__":
    main()
