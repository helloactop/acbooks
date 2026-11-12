from pathlib import Path

from app.db.session import DATABASE_PATH, create_sqlite_engine
from app.modules.auth.service import SetupService
from app.modules.auth.setup_code import SETUP_CODE_PATH, provision_setup_code


def main(database_path: Path = DATABASE_PATH, code_path: Path = SETUP_CODE_PATH) -> None:
    engine = create_sqlite_engine(database_path)
    try:
        service = SetupService(engine, code_path)
        if not service.initialized():
            provision_setup_code(code_path)
        # Also handles a setup that commits while the local command is running.
        if service.initialized():
            code_path.unlink(missing_ok=True)
    finally:
        engine.dispose()


if __name__ == "__main__":
    main()
