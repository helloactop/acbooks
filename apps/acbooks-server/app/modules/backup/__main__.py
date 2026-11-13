import argparse
import json
import sys
from collections.abc import Sequence
from dataclasses import asdict
from pathlib import Path

from app.db.session import DATABASE_PATH
from app.modules.backup.service import (
    BackupError,
    backup_database,
    restore_database,
    verify_backup,
)


def _database_argument(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--database", type=Path, default=DATABASE_PATH)


def main(argv: Sequence[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="python -m app.modules.backup")
    commands = parser.add_subparsers(dest="command", required=True)

    backup = commands.add_parser("backup", help="create a consistent database backup")
    backup.add_argument("--output", type=Path, default=None)
    _database_argument(backup)

    verify = commands.add_parser("verify", help="check a backup and its manifest")
    verify.add_argument("backup", type=Path)
    verify.add_argument("--manifest", type=Path, default=None)

    restore = commands.add_parser("restore", help="replace the database with a backup")
    restore.add_argument("backup", type=Path)
    restore.add_argument("--manifest", type=Path, default=None)
    _database_argument(restore)

    args = parser.parse_args(argv)
    try:
        if args.command == "backup":
            result: dict[str, object] = asdict(backup_database(args.output, args.database))
        elif args.command == "verify":
            result = asdict(verify_backup(args.backup, args.manifest))
        else:
            snapshot = restore_database(args.backup, args.database, args.manifest)
            result = {
                "database": str(args.database),
                "manifest": asdict(verify_backup(args.database)),
                "pre_restore": str(snapshot) if snapshot is not None else None,
            }
    except BackupError as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(2) from None
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
