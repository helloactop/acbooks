import { useLanguage, cn } from "@acbooks/ui";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { BookCover } from "./BookCover";
import type { BookSummary } from "./books-api";
import type { CoverSize } from "./library-view-state";
import { bookDetailHref } from "../layout/routes";
import { joinNames } from "./presentation";

export interface BookCardProps {
  book: BookSummary;
  coverSize?: CoverSize;
  onOpen: (bookId: string) => void;
}

const COVER_TITLE: Record<CoverSize, string> = {
  large: "text-[19px] leading-[1.25]",
  medium: "text-[16px] leading-[1.3]",
  small: "text-[13px] leading-tight",
};

const COVER_AUTHOR: Record<CoverSize, string> = {
  large: "text-[11px]",
  medium: "text-[10px]",
  small: "text-[8px]",
};

export function BookCard({
  book,
  coverSize = "medium",
  onOpen,
}: BookCardProps) {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  const titleId = `book-${book.id}-title`;
  const authors =
    joinNames(book.authors, resolvedLanguage) || t("labels.unknownAuthor");

  return (
    <article aria-labelledby={titleId} className="min-w-0">
      <Link
        aria-label={t("detail.open", { title: book.title })}
        className="group block w-full rounded-cover text-left focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none"
        onClick={() => {
          onOpen(book.id);
        }}
        to={bookDetailHref(book.id)}
      >
        <BookCover
          authorClassName={COVER_AUTHOR[coverSize]}
          authorLabel={authors}
          book={book}
          flat
          titleClassName={cn("font-semibold", COVER_TITLE[coverSize])}
        />

        <h3
          className="mt-1.5 line-clamp-2 min-h-[2.6em] text-[13px] leading-[1.3] font-medium group-hover:text-accent-text"
          id={titleId}
        >
          {book.title}
        </h3>
        <p className="truncate text-xs text-muted-foreground">{authors}</p>
      </Link>
    </article>
  );
}
