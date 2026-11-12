import re
from unicodedata import category, normalize

_ISBN_10 = re.compile(r"\d{9}[\dX]")
_ISBN_13 = re.compile(r"\d{13}")
_NAME_MAX = 200
_NAME_LIMIT = 20


def normalize_isbn(value: str) -> str | None:
    normalized = re.sub(r"[\s-]", "", normalize("NFKC", value)).upper()
    if _ISBN_10.fullmatch(normalized) or _ISBN_13.fullmatch(normalized):
        return normalized
    return None


def clean_text(
    value: str,
    field: str,
    *,
    allow_newlines: bool = False,
    max_length: int,
) -> str:
    text = value.strip()
    if not text:
        raise ValueError(f"{field} must not be empty.")
    if len(text) > max_length:
        raise ValueError(f"{field} is too long.")

    allowed_controls = {"\n", "\r", "\t"} if allow_newlines else set()
    if any(
        category(char).startswith("C") and char not in allowed_controls
        for char in text
    ):
        raise ValueError(f"{field} contains invalid control characters.")
    return text


def normalize_name_list(values: list[str], field: str) -> list[str]:
    if len(values) > _NAME_LIMIT:
        raise ValueError(f"{field} accepts at most {_NAME_LIMIT} values.")

    normalized: list[str] = []
    seen: set[str] = set()
    for value in values:
        name = clean_text(value, field, max_length=_NAME_MAX)
        key = name.casefold()
        if key in seen:
            continue
        seen.add(key)
        normalized.append(name)
    return normalized


def escape_like(value: str) -> str:
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
