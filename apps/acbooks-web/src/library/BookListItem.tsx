import { useLanguage } from "@acbooks/ui";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import {
  FormatBadges,
  PhysicalCopiesBadge,
  ReadingStatusBadge,
  TagBadges,
} from "./BookMeta";
import { BookCover } from "./BookCover";
import type { BookSummary } from "./books-api";
import { bookDetailHref } from "../layout/routes";
import { ROW_GRID, joinNames } from "./presentation";

export interface BookListItemProps {
  book: BookSummary;
  onOpen: (bookId: string) => void;
}

export function BookListItem({ book, onOpen }: BookListItemProps) {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  const authors =
    joinNames(book.authors, resolvedLanguage) || t("labels.unknownAuthor");

  return (
    <div
      className={`${ROW_GRID} relative min-h-14 border-b border-border px-2 py-1 transition-colors duration-150 ease-out hover:bg-surface-hover focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2 focus-within:ring-offset-background`}
      role="row"
    >
      <div aria-hidden="true" className="flex justify-center" role="cell">
        <BookCover
          book={book}
          className="w-8"
          flat
          titleClassName="text-[7px] leading-tight"
        />
      </div>

      <div className="min-w-0" role="cell">
        <Link
          aria-label={t("detail.open", { title: book.title })}
          className="block max-w-full truncate text-left text-[13px] font-medium after:absolute after:inset-0 after:content-[''] hover:text-accent-text focus-visible:outline-none"
          onClick={() => {
            onOpen(book.id);
          }}
          to={bookDetailHref(book.id)}
        >
          {book.title}
        </Link>
        <span className="block truncate text-xs text-muted-foreground">
          {authors}
        </span>
        <div className="mt-0.5 flex flex-wrap gap-1">
          <TagBadges tags={book.tags} />
        </div>
        <div className="mt-0.5 flex flex-wrap gap-1 md:hidden">
          <FormatBadges formats={book.formats} />
          <PhysicalCopiesBadge count={book.copyCount} />
          <ReadingStatusBadge status={book.readingStatus} />
        </div>
      </div>

      <div className="flex flex-wrap gap-1 max-md:hidden" role="cell">
        <FormatBadges formats={book.formats} />
        <PhysicalCopiesBadge count={book.copyCount} />
        {book.formats.length === 0 && book.copyCount === 0 ? (
          <span className="text-xs text-muted-foreground">
            {t("labels.none")}
          </span>
        ) : null}
      </div>

      <div className="flex items-center gap-2 max-md:hidden" role="cell">
        <ReadingStatusBadge status={book.readingStatus} />
      </div>
    </div>
  );
}
