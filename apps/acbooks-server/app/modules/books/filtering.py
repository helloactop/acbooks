from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass, replace
from datetime import UTC, date, datetime, timedelta
from typing import Any, Literal
from typing import cast as typing_cast

from sqlalchemy import (
    ColumnElement,
    and_,
    exists,
    false,
    func,
    not_,
    or_,
    select,
    true,
)

from app.db.models.books import (
    Book,
    BookContributor,
    BookIdentifier,
    BookTag,
    Contributor,
    Shelf,
    ShelfBook,
    Tag,
)
from app.db.models.copies import BookCopy
from app.db.models.storage import BookFile
from app.modules.books.normalization import escape_like, normalize_isbn

FilterCombinator = Literal["ALL", "ANY"]
BookFilterFieldType = Literal["text", "select", "number", "date", "boolean"]
BookFilterOptionSource = Literal[
    "authors", "translators", "tags", "shelves", "formats", "locations"
]

TEXT_OPERATORS = (
    "contains",
    "not_contains",
    "equals",
    "not_equals",
    "starts_with",
    "ends_with",
    "is_empty",
    "is_not_empty",
)
SELECT_OPERATORS = ("equals", "not_equals", "is_empty", "is_not_empty")
NUMBER_OPERATORS = (
    "equals",
    "not_equals",
    "greater_than",
    "greater_or_equal",
    "less_than",
    "less_or_equal",
    "between",
    "is_empty",
    "is_not_empty",
)
DATE_OPERATORS = (
    "equals",
    "not_equals",
    "after",
    "on_or_after",
    "before",
    "on_or_before",
    "between",
    "is_empty",
    "is_not_empty",
)
BOOLEAN_OPERATORS = ("is_true", "is_false")

VALUELESS_OPERATORS = frozenset(
    {"is_empty", "is_not_empty", "is_true", "is_false"}
)
MAX_FILTER_CONDITIONS = 30


@dataclass(frozen=True)
class BookFilterFieldDefinition:
    key: str
    group: str
    field_type: BookFilterFieldType
    operators: tuple[str, ...]
    option_source: BookFilterOptionSource | None = None
    allow_custom: bool = False
    unit: str | None = None


@dataclass(frozen=True)
class BookFilterOption:
    value: str
    label: str
    count: int | None = None


@dataclass(frozen=True)
class BookFilterCondition:
    field: str
    operator: str
    value: str | tuple[str, str] | None = None


@dataclass(frozen=True)
class BookFilterExpression:
    combinator: FilterCombinator
    conditions: tuple[BookFilterCondition, ...]


class InvalidBookFilter(ValueError):
    pass


FILTER_FIELDS = (
    BookFilterFieldDefinition(
        "title", "metadata", "text", TEXT_OPERATORS
    ),
    BookFilterFieldDefinition(
        "author", "metadata", "select", SELECT_OPERATORS, "authors", True
    ),
    BookFilterFieldDefinition(
        "translator",
        "metadata",
        "select",
        SELECT_OPERATORS,
        "translators",
        True,
    ),
    BookFilterFieldDefinition(
        "tag", "metadata", "select", SELECT_OPERATORS, "tags", True
    ),
    BookFilterFieldDefinition(
        "shelf", "metadata", "select", SELECT_OPERATORS, "shelves", True
    ),
    BookFilterFieldDefinition(
        "isbn", "metadata", "text", TEXT_OPERATORS
    ),
    BookFilterFieldDefinition(
        "description", "metadata", "text", TEXT_OPERATORS
    ),
    BookFilterFieldDefinition(
        "format", "files", "select", SELECT_OPERATORS, "formats", True
    ),
    BookFilterFieldDefinition(
        "readingStatus", "reading", "select", SELECT_OPERATORS
    ),
    BookFilterFieldDefinition(
        "copyCount", "files", "number", NUMBER_OPERATORS
    ),
    BookFilterFieldDefinition(
        "location", "files", "select", SELECT_OPERATORS, "locations", True
    ),
    BookFilterFieldDefinition(
        "fileCount", "files", "number", NUMBER_OPERATORS
    ),
    BookFilterFieldDefinition(
        "createdAt", "timeline", "date", DATE_OPERATORS
    ),
    BookFilterFieldDefinition(
        "updatedAt", "timeline", "date", DATE_OPERATORS
    ),
    BookFilterFieldDefinition(
        "hasCover", "timeline", "boolean", BOOLEAN_OPERATORS
    ),
)
FILTER_FIELDS_BY_KEY = {field.key: field for field in FILTER_FIELDS}
STATIC_FILTER_OPTIONS: dict[str, tuple[BookFilterOption, ...]] = {
    "readingStatus": (
        BookFilterOption("unread", "Unread"),
        BookFilterOption("reading", "Reading"),
        BookFilterOption("done", "Read"),
    )
}

READING_STATUS_VALUES = frozenset({"unread", "reading", "done"})


def _required_value(
    field: BookFilterFieldDefinition, operator: str, raw_value: object
) -> str | tuple[str, str] | None:
    if operator in VALUELESS_OPERATORS:
        return None
    if operator == "between":
        if (
            not isinstance(raw_value, list)
            or len(raw_value) != 2
            or any(not str(item).strip() for item in raw_value)
        ):
            raise InvalidBookFilter(
                f"{field.key} requires both range values."
            )
        start, end = str(raw_value[0]).strip(), str(raw_value[1]).strip()
        _validate_scalar(field, start)
        _validate_scalar(field, end)
        if field.field_type == "number":
            if float(start) > float(end):
                raise InvalidBookFilter(
                    f"{field.key} range start must not exceed end."
                )
        elif field.field_type == "date":
            if date.fromisoformat(start) > date.fromisoformat(end):
                raise InvalidBookFilter(
                    f"{field.key} range start must not exceed end."
                )
        return (start, end)

    value = str(raw_value if raw_value is not None else "").strip()
    if not value:
        raise InvalidBookFilter(f"{field.key} requires a value.")
    if len(value) > 500:
        raise InvalidBookFilter(f"{field.key} value is too long.")
    _validate_scalar(field, value)
    return value


def _validate_scalar(field: BookFilterFieldDefinition, value: str) -> None:
    if field.field_type == "number":
        try:
            number = float(value)
        except ValueError as error:
            raise InvalidBookFilter(
                f"{field.key} requires a number."
            ) from error
        if number != number or number in {float("inf"), float("-inf")}:
            raise InvalidBookFilter(f"{field.key} requires a number.")
    elif field.field_type == "date":
        try:
            date.fromisoformat(value)
        except ValueError as error:
            raise InvalidBookFilter(
                f"{field.key} requires an ISO date."
            ) from error
    elif field.key == "readingStatus" and value not in READING_STATUS_VALUES:
        raise InvalidBookFilter("Unsupported reading status.")


def parse_filter_expression(value: object) -> BookFilterExpression:
    if value in (None, ""):
        return BookFilterExpression(combinator="ALL", conditions=())
    if not isinstance(value, dict):
        raise InvalidBookFilter("Filter rules must be an object.")
    raw_combinator = str(value.get("combinator") or "ALL").upper()
    if raw_combinator not in {"ALL", "ANY"}:
        raise InvalidBookFilter("Unsupported filter combinator.")
    raw_conditions = value.get("conditions") or []
    if not isinstance(raw_conditions, list):
        raise InvalidBookFilter("Filter conditions must be a list.")
    if len(raw_conditions) > MAX_FILTER_CONDITIONS:
        raise InvalidBookFilter(
            f"At most {MAX_FILTER_CONDITIONS} filter conditions are supported."
        )

    conditions: list[BookFilterCondition] = []
    for raw in raw_conditions:
        if not isinstance(raw, dict):
            raise InvalidBookFilter("Invalid filter condition.")
        field_key = str(raw.get("field") or "").strip()
        field = FILTER_FIELDS_BY_KEY.get(field_key)
        if field is None:
            raise InvalidBookFilter(f"Unsupported filter field: {field_key}")
        operator = str(raw.get("operator") or "").strip()
        if operator not in field.operators:
            raise InvalidBookFilter(
                f"Unsupported operator for {field_key}: {operator}"
            )
        conditions.append(
            BookFilterCondition(
                field=field_key,
                operator=operator,
                value=_required_value(field, operator, raw.get("value")),
            )
        )

    return BookFilterExpression(
        combinator=typing_cast(FilterCombinator, raw_combinator),
        conditions=tuple(conditions),
    )


def _column(expression: object) -> ColumnElement[object]:
    return typing_cast(ColumnElement[object], expression)


def _normalized_text(expression: object) -> ColumnElement[str]:
    return typing_cast(
        ColumnElement[str],
        func.acbooks_casefold(func.trim(func.coalesce(_column(expression), ""))),
    )


def _literal_pattern(value: str) -> str:
    return escape_like(value.casefold())


def _text(
    expression: object,
    condition: BookFilterCondition,
    *,
    empty_values: tuple[str, ...] = (),
) -> ColumnElement[bool]:
    normalized = _normalized_text(expression)
    empty_predicate = normalized.in_(
        ("", *(value.casefold() for value in empty_values))
    )
    if condition.operator == "is_empty":
        return empty_predicate
    if condition.operator == "is_not_empty":
        return not_(empty_predicate)

    literal = str(condition.value or "").casefold()
    pattern = escape_like(literal)
    if condition.operator in {"contains", "not_contains"}:
        predicate = normalized.like(f"%{pattern}%", escape="\\")
        return not_(predicate) if condition.operator == "not_contains" else predicate
    if condition.operator == "starts_with":
        return normalized.like(f"{pattern}%", escape="\\")
    if condition.operator == "ends_with":
        return normalized.like(f"%{pattern}", escape="\\")
    equals_predicate = normalized == literal
    return (
        not_(equals_predicate)
        if condition.operator == "not_equals"
        else equals_predicate
    )


def _select_scalar(
    expression: object, condition: BookFilterCondition
) -> ColumnElement[bool]:
    if condition.operator == "is_empty":
        return false()
    if condition.operator == "is_not_empty":
        return true()
    predicate = (
        func.acbooks_casefold(_column(expression))
        == str(condition.value or "").casefold()
    )
    return not_(predicate) if condition.operator == "not_equals" else predicate


def _positive_condition(
    condition: BookFilterCondition,
) -> tuple[BookFilterCondition, bool]:
    negative = condition.operator in {"not_contains", "not_equals", "is_empty"}
    operator = {
        "not_contains": "contains",
        "not_equals": "equals",
        "is_empty": "is_not_empty",
    }.get(condition.operator, condition.operator)
    return replace(condition, operator=operator), negative


def _relation_text(
    statement: Any,
    expression: object,
    condition: BookFilterCondition,
) -> ColumnElement[bool]:
    positive, negative = _positive_condition(condition)
    predicate = exists(statement.where(_text(expression, positive)))
    return not_(predicate) if negative else predicate


def _contributor_condition(
    role: str, condition: BookFilterCondition
) -> ColumnElement[bool]:
    statement = (
        select(1)
        .select_from(BookContributor)
        .join(Contributor, Contributor.id == BookContributor.contributor_id)
        .where(
            BookContributor.book_id == Book.id,
            BookContributor.role == role,
        )
    )
    return _relation_text(statement, Contributor.name, condition)


def _tag_condition(condition: BookFilterCondition) -> ColumnElement[bool]:
    statement = (
        select(1)
        .select_from(BookTag)
        .join(Tag, Tag.id == BookTag.tag_id)
        .where(BookTag.book_id == Book.id)
    )
    return _relation_text(statement, Tag.name, condition)


def _shelf_condition(condition: BookFilterCondition) -> ColumnElement[bool]:
    statement = (
        select(1)
        .select_from(ShelfBook)
        .join(Shelf, Shelf.id == ShelfBook.shelf_id)
        .where(ShelfBook.book_id == Book.id)
    )
    return _relation_text(statement, Shelf.name, condition)


def _format_condition(condition: BookFilterCondition) -> ColumnElement[bool]:
    statement = select(1).where(BookFile.book_id == Book.id)
    return _relation_text(statement, BookFile.format, condition)


def _isbn_condition(condition: BookFilterCondition) -> ColumnElement[bool]:
    statement = select(1).where(
        BookIdentifier.book_id == Book.id,
        BookIdentifier.scheme == "isbn",
    )
    if condition.operator == "is_empty":
        return not_(exists(statement))
    if condition.operator == "is_not_empty":
        return exists(statement)

    positive, negative = _positive_condition(condition)
    value = positive.value
    if (
        positive.operator in {"equals", "contains", "starts_with", "ends_with"}
        and isinstance(value, str)
    ):
        normalized = normalize_isbn(value)
        if normalized is not None:
            positive = replace(positive, value=normalized)
    predicate = exists(statement.where(_text(BookIdentifier.value, positive)))
    return not_(predicate) if negative else predicate


def _number(
    expression: object, condition: BookFilterCondition
) -> ColumnElement[bool]:
    column = typing_cast(ColumnElement[float], _column(expression))
    if condition.operator == "is_empty":
        return column == 0
    if condition.operator == "is_not_empty":
        return column > 0
    if condition.operator == "between":
        assert isinstance(condition.value, tuple)
        return column.between(float(condition.value[0]), float(condition.value[1]))
    value = float(str(condition.value))
    return {
        "equals": column == value,
        "not_equals": column != value,
        "greater_than": column > value,
        "greater_or_equal": column >= value,
        "less_than": column < value,
        "less_or_equal": column <= value,
    }[condition.operator]


def _utc_midnight(value: str) -> datetime:
    return datetime.combine(date.fromisoformat(value), datetime.min.time(), tzinfo=UTC)


def _date(
    expression: object, condition: BookFilterCondition
) -> ColumnElement[bool]:
    column = _column(expression)
    if condition.operator == "is_empty":
        return column.is_(None)
    if condition.operator == "is_not_empty":
        return column.is_not(None)

    if condition.operator == "between":
        assert isinstance(condition.value, tuple)
        start_day, end_day = condition.value
        start = int(_utc_midnight(start_day).timestamp() * 1000)
        end = int(
            (_utc_midnight(end_day) + timedelta(days=1)).timestamp() * 1000
        )
        return and_(column >= start, column < end)

    start = int(_utc_midnight(str(condition.value)).timestamp() * 1000)
    end = int(
        (_utc_midnight(str(condition.value)) + timedelta(days=1)).timestamp()
        * 1000
    )
    if condition.operator == "equals":
        return and_(column >= start, column < end)
    if condition.operator == "not_equals":
        return not_(and_(column >= start, column < end))
    if condition.operator == "after":
        return column >= end
    if condition.operator == "on_or_after":
        return column >= start
    if condition.operator == "before":
        return column < start
    return column < end


def _has_cover(condition: BookFilterCondition) -> ColumnElement[bool]:
    predicate = and_(
        Book.cover_path.is_not(None),
        func.trim(Book.cover_path) != "",
    )
    return predicate if condition.operator == "is_true" else not_(predicate)


def _copy_count() -> ColumnElement[int]:
    return typing_cast(
        ColumnElement[int],
        select(func.count())
        .select_from(BookCopy)
        .where(BookCopy.book_id == Book.id)
        .correlate(Book)
        .scalar_subquery(),
    )


def _file_count() -> ColumnElement[int]:
    return typing_cast(
        ColumnElement[int],
        select(func.count())
        .select_from(BookFile)
        .where(BookFile.book_id == Book.id)
        .correlate(Book)
        .scalar_subquery(),
    )


def _location_condition(
    condition: BookFilterCondition,
    location_subtrees: Mapping[str, frozenset[str]] | None,
) -> ColumnElement[bool]:
    located = exists(
        select(1).where(
            BookCopy.book_id == Book.id,
            BookCopy.location_id.is_not(None),
        )
    )
    if condition.operator == "is_empty":
        return not_(located)
    if condition.operator == "is_not_empty":
        return located
    value = str(condition.value or "")
    ids = (location_subtrees or {}).get(value, frozenset({value}))
    predicate = exists(
        select(1).where(
            BookCopy.book_id == Book.id,
            BookCopy.location_id.in_(sorted(ids)),
        )
    )
    return not_(predicate) if condition.operator == "not_equals" else predicate


def _condition(
    condition: BookFilterCondition,
    location_subtrees: Mapping[str, frozenset[str]] | None = None,
) -> ColumnElement[bool]:
    if condition.field == "title":
        return _text(Book.title, condition)
    if condition.field == "description":
        return _text(Book.description, condition)
    if condition.field == "isbn":
        return _isbn_condition(condition)
    if condition.field == "author":
        return _contributor_condition("author", condition)
    if condition.field == "translator":
        return _contributor_condition("translator", condition)
    if condition.field == "tag":
        return _tag_condition(condition)
    if condition.field == "shelf":
        return _shelf_condition(condition)
    if condition.field == "format":
        return _format_condition(condition)
    if condition.field == "readingStatus":
        return _select_scalar(Book.reading_status, condition)
    if condition.field == "copyCount":
        return _number(_copy_count(), condition)
    if condition.field == "location":
        return _location_condition(condition, location_subtrees)
    if condition.field == "fileCount":
        return _number(_file_count(), condition)
    if condition.field == "createdAt":
        return _date(Book.created_at, condition)
    if condition.field == "updatedAt":
        return _date(Book.updated_at, condition)
    if condition.field == "hasCover":
        return _has_cover(condition)
    raise InvalidBookFilter(f"Unsupported filter field: {condition.field}")


def compile_filter_expression(
    expression: BookFilterExpression,
    location_subtrees: Mapping[str, frozenset[str]] | None = None,
) -> ColumnElement[bool] | None:
    if not expression.conditions:
        return None
    predicates = [
        _condition(condition, location_subtrees)
        for condition in expression.conditions
    ]
    return and_(*predicates) if expression.combinator == "ALL" else or_(*predicates)


__all__ = [
    "BOOLEAN_OPERATORS",
    "BookFilterCondition",
    "BookFilterExpression",
    "BookFilterFieldDefinition",
    "BookFilterFieldType",
    "BookFilterOption",
    "BookFilterOptionSource",
    "DATE_OPERATORS",
    "FILTER_FIELDS",
    "FILTER_FIELDS_BY_KEY",
    "FilterCombinator",
    "InvalidBookFilter",
    "MAX_FILTER_CONDITIONS",
    "NUMBER_OPERATORS",
    "SELECT_OPERATORS",
    "STATIC_FILTER_OPTIONS",
    "TEXT_OPERATORS",
    "compile_filter_expression",
    "parse_filter_expression",
]
