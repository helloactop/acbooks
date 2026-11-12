import { IconButton, useLanguage } from "@acbooks/ui";
import { Ellipsis } from "lucide-react";
import { useTranslation } from "react-i18next";

import {
  FormatBadges,
  PhysicalCopiesBadge,
  ReadingStatusBadge,
  TagBadges,
} from "./BookMeta";
import type { BookSummary } from "./books-api";
import { bookDetailHref } from "../layout/routes";
import {
  COVER_FONT,
  ROW_GRID,
  coverFor,
  initialOf,
  joinNames,
} from "./presentation";

export interface BookListItemProps {
  book: BookSummary;
  onOpen: (bookId: string) => void;
}

export function BookListItem({ book, onOpen }: BookListItemProps) {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  const cover = coverFor(book);
  const authors =
    joinNames(book.authors, resolvedLanguage) || t("labels.unknownAuthor");

  return (
    <div
      className={`${ROW_GRID} min-h-[72px] border-b border-border px-3 py-1.5`}
      role="row"
    >
      <div aria-hidden="true" role="cell">
        <div
          className="flex h-[60px] w-10 items-center justify-center overflow-hidden rounded text-xs font-bold shadow-cover"
          style={{
            backgroundColor: cover.background,
            color: cover.foreground,
            fontFamily: COVER_FONT,
          }}
        >
          {initialOf(book.title)}
        </div>
      </div>

      <div className="min-w-0" role="cell">
        <a
          aria-label={t("detail.open", { title: book.title })}
          className="block max-w-full truncate text-left text-sm font-medium hover:text-accent-text focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none"
          href={bookDetailHref(book.id)}
          onClick={() => {
            onOpen(book.id);
          }}
        >
          {book.title}
        </a>
        <span className="block truncate text-[13px] text-muted-foreground">
          {authors}
        </span>
        <div className="mt-1 flex flex-wrap gap-1">
          <TagBadges tags={book.tags} />
        </div>
        <div className="mt-1 flex flex-wrap gap-1 md:hidden">
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

      <div role="cell">
        <IconButton
          disabled
          label={t("actions.more")}
          title={t("nav.notAvailable")}
        >
          <Ellipsis aria-hidden="true" />
        </IconButton>
      </div>
    </div>
  );
}
