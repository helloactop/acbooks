import os
import stat
import sys
from pathlib import Path
from secrets import token_urlsafe
from tempfile import NamedTemporaryFile

SETUP_CODE_PATH = Path("/data/acbooks/secrets/setup-code")


def private_path(info: os.stat_result, *, directory: bool = False) -> bool:
    expected_type = stat.S_ISDIR if directory else stat.S_ISREG
    if not expected_type(info.st_mode):
        return False
    if sys.platform == "win32":
        return not bool(info.st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT)
    mode = 0o700 if directory else 0o600
    return info.st_uid == os.geteuid() and stat.S_IMODE(info.st_mode) == mode


def read_setup_code(path: Path) -> bytes | None:
    try:
        if not private_path(path.parent.lstat(), directory=True):
            raise PermissionError("Unsafe secrets directory.")
        before = path.lstat()
        if not private_path(before):
            raise PermissionError("Unsafe setup code file.")
        flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0)
        with os.fdopen(os.open(path, flags), "rb") as file:
            opened = os.fstat(file.fileno())
            if not private_path(opened) or not os.path.samestat(before, opened):
                raise PermissionError("Unsafe setup code file.")
            code = file.read(44)
    except FileNotFoundError:
        return None
    if len(code) != 43 or any(
        char not in b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        for char in code
    ):
        raise ValueError("Invalid setup code file.")
    return code


def provision_setup_code(path: Path) -> None:
    path.parent.mkdir(mode=0o700, exist_ok=True)
    if not private_path(path.parent.lstat(), directory=True):
        raise PermissionError("Unsafe secrets directory.")
    if read_setup_code(path) is not None:
        return
    # Publish only a complete file. A competing provisioner cannot replace the winner.
    file = NamedTemporaryFile(dir=path.parent, delete=False)
    temporary = Path(file.name)
    try:
        with file:
            if sys.platform != "win32":
                temporary.chmod(0o600)
            file.write(token_urlsafe(32).encode("ascii"))
            file.flush()
            os.fsync(file.fileno())
        try:
            os.link(temporary, path)
        except FileExistsError:
            if read_setup_code(path) is None:
                raise
    finally:
        temporary.unlink()
