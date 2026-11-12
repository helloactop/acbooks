import { useLanguage } from "@acbooks/ui";
import { useTranslation } from "react-i18next";

import { BookMeta } from "./BookMeta";
import type { BookSummary } from "./books-api";
import { COVER_FONT, coverFor, joinNames } from "./presentation";

export interface BookCardProps {
  book: BookSummary;
  onOpen: (bookId: string) => void;
}

export function BookCard({ book, onOpen }: BookCardProps) {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  const cover = coverFor(book);
  const titleId = `book-${book.id}-title`;
  const authors =
    joinNames(book.authors, resolvedLanguage) || t("labels.unknownAuthor");

  return (
    <article aria-labelledby={titleId} className="min-w-0">
      <button
        aria-label={t("detail.open", { title: book.title })}
        className="group block w-full rounded-cover text-left focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none"
        onClick={() => {
          onOpen(book.id);
        }}
        type="button"
      >
        <div
          aria-hidden="true"
          className="relative aspect-[2/3] overflow-hidden rounded-cover shadow-cover"
          style={{ backgroundColor: cover.background, color: cover.foreground }}
        >
          <div
            className="absolute inset-2 flex flex-col justify-between rounded border px-3 py-3.5"
            style={{ borderColor: cover.line }}
          >
            <div
              className="overflow-hidden text-[17px] leading-[1.3] font-bold break-all"
              style={{ fontFamily: COVER_FONT }}
            >
              {book.title}
            </div>
            <div className="overflow-hidden text-[11px] text-ellipsis whitespace-nowrap opacity-85">
              {authors}
            </div>
          </div>
        </div>

        <h3
          className="mt-2 line-clamp-2 text-sm leading-[1.4] font-medium group-hover:text-accent-text"
          id={titleId}
        >
          {book.title}
        </h3>
        <p className="truncate text-[13px] text-muted-foreground">{authors}</p>
      </button>

      <div className="mt-1.5">
        <BookMeta book={book} />
      </div>
    </article>
  );
}
