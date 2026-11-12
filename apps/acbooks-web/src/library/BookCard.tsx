import { useTranslation } from "react-i18next";

import { COVER_FONT, TAG_LABEL_KEYS } from "./presentation";
import type { LibraryBook } from "./sample-books";

export interface BookCardProps {
  book: LibraryBook;
}

export function BookCard({ book }: BookCardProps) {
  const { t } = useTranslation("library");
  const titleId = `book-${book.id}-title`;

  return (
    <article aria-labelledby={titleId} className="min-w-0">
      <div
        aria-hidden="true"
        className="relative aspect-[2/3] overflow-hidden rounded-cover shadow-cover"
        style={{
          backgroundColor: book.cover.background,
          color: book.cover.foreground,
        }}
      >
        <div
          className="absolute inset-2 flex flex-col justify-between rounded border px-3 py-3.5"
          style={{ borderColor: book.cover.line }}
        >
          <div
            className="overflow-hidden text-[17px] leading-[1.3] font-bold break-all"
            style={{ fontFamily: COVER_FONT }}
          >
            {book.title}
          </div>
          <div className="overflow-hidden text-[11px] text-ellipsis whitespace-nowrap opacity-85">
            {book.author}
          </div>
        </div>
      </div>

      <h3
        className="mt-2 line-clamp-2 text-sm leading-[1.4] font-medium"
        id={titleId}
      >
        {book.title}
      </h3>
      <p className="truncate text-[13px] text-muted-foreground">
        {book.author}
      </p>

      <div className="mt-1.5 flex flex-wrap items-center gap-1">
        {book.tags.map((tag) => (
          <span
            key={tag}
            className="rounded bg-surface px-1.5 py-0.5 text-xs leading-4 text-muted-foreground"
          >
            {t(TAG_LABEL_KEYS[tag])}
          </span>
        ))}
        {book.status === "done" ? (
          <span className="rounded bg-accent-soft px-1.5 py-0.5 text-xs leading-4 text-accent-text">
            {t("status.done")}
          </span>
        ) : null}
      </div>

      {book.status === "reading" && book.progress !== undefined ? (
        <div className="mt-2 flex items-center gap-2">
          <div
            aria-label={t("progress.label")}
            aria-valuemax={100}
            aria-valuemin={0}
            aria-valuenow={book.progress}
            className="h-1 flex-1 overflow-hidden rounded-full bg-surface-active"
            role="progressbar"
          >
            <div
              className="h-full rounded-full bg-accent-text"
              style={{ width: `${book.progress}%` }}
            />
          </div>
          <span className="text-xs text-muted-foreground tabular-nums">
            {book.progress}%
          </span>
        </div>
      ) : null}
    </article>
  );
}
