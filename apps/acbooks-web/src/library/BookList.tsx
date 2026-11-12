import { useTranslation } from "react-i18next";

import { BookListItem } from "./BookListItem";
import { ROW_GRID } from "./presentation";
import type { LibraryBook } from "./sample-books";

export interface BookListProps {
  books: readonly LibraryBook[];
}

export function BookList({ books }: BookListProps) {
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
          {t("list.progress")}
        </span>
        <span role="columnheader" />
      </div>

      {books.map((book) => (
        <BookListItem key={book.id} book={book} />
      ))}
    </div>
  );
}
