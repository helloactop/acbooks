import argparse
import logging
import signal
import sys
from pathlib import Path
from threading import Event
from types import FrameType
from typing import TextIO

logger = logging.getLogger(__name__)
READY_LOCK = Path("/tmp/acbooks-worker.ready")


def try_lock(lock: TextIO) -> bool:
    if sys.platform != "win32":
        from fcntl import LOCK_EX, LOCK_NB, flock

        try:
            flock(lock, LOCK_EX | LOCK_NB)
        except BlockingIOError:
            return False
        return True
    else:
        raise SystemExit("Run the worker in a Linux container.")


def worker_ready() -> bool:
    try:
        with READY_LOCK.open(encoding="utf-8") as lock:
            return not try_lock(lock)
    except FileNotFoundError:
        return False


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--healthcheck", action="store_true")
    if parser.parse_args().healthcheck:
        raise SystemExit(0 if worker_ready() else 1)

    from sqlalchemy import select

    from app.db.models.jobs import BackgroundJob
    from app.db.session import DATABASE_PATH, create_sqlite_engine

    if not DATABASE_PATH.is_file():
        raise SystemExit("Run the database migration before starting the worker.")

    logging.basicConfig(level=logging.INFO)
    stop = Event()

    def request_stop(_signum: int, _frame: FrameType | None) -> None:
        stop.set()

    signal.signal(signal.SIGTERM, request_stop)
    signal.signal(signal.SIGINT, request_stop)

    with DATABASE_PATH.with_name(".worker.lock").open("a", encoding="utf-8") as lock:
        if not try_lock(lock):
            raise SystemExit("Another worker already holds the database lock.")
        engine = create_sqlite_engine()
        try:
            with engine.connect() as connection:
                connection.execute(select(BackgroundJob.id).limit(0))
            # The probe sees readiness only while this container's worker holds the lock.
            with READY_LOCK.open("a", encoding="utf-8") as ready:
                if not try_lock(ready):
                    raise SystemExit("Another worker holds the readiness lock.")
                logger.info("Worker started.")
                stop.wait()
        finally:
            engine.dispose()
        logger.info("Worker stopped.")


if __name__ == "__main__":
    main()
