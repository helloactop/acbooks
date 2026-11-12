import { useLanguage } from "@acbooks/ui";
import { useTranslation } from "react-i18next";

import { BookCover } from "./BookCover";
import { BookMeta } from "./BookMeta";
import type { BookSummary } from "./books-api";
import { bookDetailHref } from "./navigation";
import { joinNames } from "./presentation";

export interface BookCardProps {
  book: BookSummary;
  onOpen: (bookId: string) => void;
}

export function BookCard({ book, onOpen }: BookCardProps) {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  const titleId = `book-${book.id}-title`;
  const authors =
    joinNames(book.authors, resolvedLanguage) || t("labels.unknownAuthor");

  return (
    <article aria-labelledby={titleId} className="min-w-0">
      <a
        aria-label={t("detail.open", { title: book.title })}
        className="group block w-full rounded-cover text-left focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none"
        href={bookDetailHref(book.id)}
        onClick={() => {
          onOpen(book.id);
        }}
      >
        <BookCover
          authorClassName="text-[11px]"
          authorLabel={authors}
          book={book}
          titleClassName="text-[17px] leading-[1.3]"
        />

        <h3
          className="mt-2 line-clamp-2 text-sm leading-[1.4] font-medium group-hover:text-accent-text"
          id={titleId}
        >
          {book.title}
        </h3>
        <p className="truncate text-[13px] text-muted-foreground">{authors}</p>
      </a>

      <div className="mt-1.5">
        <BookMeta book={book} />
      </div>
    </article>
  );
}
