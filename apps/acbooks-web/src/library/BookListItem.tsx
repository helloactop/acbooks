import { IconButton } from "@acbooks/ui";
import { Ellipsis } from "lucide-react";
import { useTranslation } from "react-i18next";

import { COVER_FONT, ROW_GRID, TAG_LABEL_KEYS } from "./presentation";
import type { LibraryBook } from "./sample-books";

export interface BookListItemProps {
  book: LibraryBook;
}

function initialOf(title: string): string {
  return Array.from(title).slice(0, 2).join("");
}

export function BookListItem({ book }: BookListItemProps) {
  const { t } = useTranslation("library");

  return (
    <div
      className={`${ROW_GRID} min-h-[72px] border-b border-border px-3 py-1.5`}
      role="row"
    >
      <div aria-hidden="true" role="cell">
        <div
          className="flex h-[60px] w-10 items-center justify-center overflow-hidden rounded text-xs font-bold shadow-cover"
          style={{
            backgroundColor: book.cover.background,
            color: book.cover.foreground,
            fontFamily: COVER_FONT,
          }}
        >
          {initialOf(book.title)}
        </div>
      </div>

      <div className="min-w-0" role="cell">
        <span className="block truncate text-sm font-medium">{book.title}</span>
        <span className="block truncate text-[13px] text-muted-foreground">
          {book.author}
        </span>
      </div>

      <div className="flex flex-wrap gap-1 max-md:hidden" role="cell">
        {book.tags.map((tag) => (
          <span
            key={tag}
            className="rounded bg-surface px-1.5 py-0.5 text-xs leading-4 text-muted-foreground"
          >
            {t(TAG_LABEL_KEYS[tag])}
          </span>
        ))}
      </div>

      <div className="flex items-center gap-2 max-md:hidden" role="cell">
        {book.status === "reading" && book.progress !== undefined ? (
          <>
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
          </>
        ) : null}
        {book.status === "done" ? (
          <span className="rounded bg-accent-soft px-1.5 py-0.5 text-xs leading-4 text-accent-text">
            {t("status.done")}
          </span>
        ) : null}
        {book.status === "unread" ? (
          <span className="text-xs text-muted-foreground">
            {t("status.unread")}
          </span>
        ) : null}
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
