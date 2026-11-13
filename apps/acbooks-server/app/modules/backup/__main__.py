import argparse
import json
import sys
from collections.abc import Sequence
from dataclasses import asdict
from pathlib import Path
from time import time_ns

from app.db.session import DATABASE_PATH
from app.modules.backup.service import (
    BackupError,
    backup_database,
    default_backup_path,
    prune_backups,
    restore_database,
    verify_backup,
)


def _database_argument(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--database", type=Path, default=DATABASE_PATH)


def _retain_days(value: str) -> int:
    days = int(value)
    if days < 1:
        raise argparse.ArgumentTypeError("retain days must be at least 1")
    return days


def main(argv: Sequence[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="python -m app.modules.backup")
    commands = parser.add_subparsers(dest="command", required=True)

    backup = commands.add_parser("backup", help="create a consistent database backup")
    backup.add_argument("--output", type=Path, default=None)
    backup.add_argument("--overwrite", action="store_true")
    backup.add_argument("--retain-days", type=_retain_days, default=None)
    _database_argument(backup)

    verify = commands.add_parser("verify", help="check a backup and its manifest")
    verify.add_argument("backup", type=Path)
    verify.add_argument("--manifest", type=Path, default=None)

    restore = commands.add_parser("restore", help="replace the database with a backup")
    restore.add_argument("backup", type=Path)
    restore.add_argument("--manifest", type=Path, default=None)
    restore.add_argument(
        "--offline",
        action="store_true",
        help="confirm API and Worker are stopped",
    )
    restore.add_argument(
        "--covers",
        type=Path,
        default=None,
        help="covers directory for the file consistency check",
    )
    restore.add_argument(
        "--allow-missing-files",
        action="store_true",
        help="restore even if referenced ebook/cover files are missing",
    )
    _database_argument(restore)

    args = parser.parse_args(argv)
    try:
        if args.command == "backup":
            manifest = backup_database(
                args.output, args.database, overwrite=args.overwrite
            )
            result: dict[str, object] = asdict(manifest)
            if args.retain_days is not None:
                target = (
                    args.output
                    if args.output is not None
                    else default_backup_path(Path(args.database))
                )
                cutoff = time_ns() // 1_000_000 - args.retain_days * 86_400_000
                result["pruned"] = [
                    str(path) for path in prune_backups(target, cutoff)
                ]
        elif args.command == "verify":
            result = asdict(verify_backup(args.backup, args.manifest))
        else:
            snapshot = restore_database(
                args.backup,
                args.database,
                args.manifest,
                offline=args.offline,
                covers_dir=args.covers,
                allow_missing_files=args.allow_missing_files,
            )
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
