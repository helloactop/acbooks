import { cn } from "@acbooks/ui";

import type { BookSummary } from "./books-api";
import { COVER_FONT, coverFor } from "./presentation";

export interface BookCoverProps {
  authorClassName?: string;
  authorLabel?: string;
  book: Pick<BookSummary, "authors" | "id" | "title">;
  className?: string;
  flat?: boolean;
  titleClassName?: string;
}

export function BookCover({
  authorClassName,
  authorLabel,
  book,
  className,
  flat = false,
  titleClassName,
}: BookCoverProps) {
  const cover = coverFor(book);

  return (
    <div
      aria-hidden="true"
      className={cn(
        "relative aspect-[2/3] overflow-hidden rounded-cover",
        !flat && "shadow-cover",
        className,
      )}
      style={{ backgroundColor: cover.background, color: cover.foreground }}
    >
      <div
        className="absolute inset-2 flex flex-col justify-between rounded border px-3 py-3.5"
        style={{ borderColor: cover.line }}
      >
        <div
          className={cn("overflow-hidden font-bold break-all", titleClassName)}
          style={{ fontFamily: COVER_FONT }}
        >
          {book.title}
        </div>
        {authorLabel !== undefined && authorLabel.length > 0 ? (
          <div
            className={cn(
              "overflow-hidden text-ellipsis whitespace-nowrap opacity-85",
              authorClassName,
            )}
          >
            {authorLabel}
          </div>
        ) : null}
      </div>
    </div>
  );
}
