import { useTranslation } from "react-i18next";

import { BookListItem } from "./BookListItem";
import type { BookSummary } from "./books-api";
import { ROW_GRID } from "./presentation";

export interface BookListProps {
  books: readonly BookSummary[];
  onOpen: (bookId: string) => void;
  onSelect?: (bookId: string) => void;
  selectable?: boolean;
  selectedIds?: ReadonlySet<string>;
  selectionDisabled?: boolean;
}

export function BookList({
  books,
  onOpen,
  onSelect,
  selectable = false,
  selectedIds,
  selectionDisabled = false,
}: BookListProps) {
  const { t } = useTranslation("library");

  return (
    <div
      aria-label={t("list.tableLabel")}
      className="flex flex-col"
      role="table"
    >
      <div
        className={`${ROW_GRID} border-b border-border px-3 pb-2 text-xs text-muted-foreground`}
        role="row"
      >
        <span role="columnheader" />
        <span role="columnheader">{t("list.titleAuthor")}</span>
        <span className="max-md:hidden" role="columnheader">
          {t("list.format")}
        </span>
        <span className="max-md:hidden" role="columnheader">
          {t("list.status")}
        </span>
      </div>

      {books.map((book) => (
        <BookListItem
          book={book}
          key={book.id}
          onOpen={onOpen}
          onSelect={onSelect}
          selectable={selectable}
          selected={selectedIds?.has(book.id) ?? false}
          selectionDisabled={selectionDisabled}
        />
      ))}
    </div>
  );
}
