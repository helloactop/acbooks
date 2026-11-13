import { cn } from "@acbooks/ui";
import { useState } from "react";

import { coverUrl, type BookSummary } from "./books-api";
import { COVER_FONT, coverFor } from "./presentation";

export interface BookCoverProps {
  authorClassName?: string;
  authorLabel?: string;
  book: Pick<
    BookSummary,
    | "authors"
    | "coverUpdatedAt"
    | "hasCover"
    | "id"
    | "title"
    | "updatedAt"
  >;
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
  const src = coverUrl(book);
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const imageFailed = src !== null && failedSrc === src;

  return (
    <div
      aria-hidden="true"
      className={cn(
        "relative aspect-[2/3] overflow-hidden rounded-cover bg-surface",
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
      {src !== null && !imageFailed ? (
        <img
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
          onError={() => {
            setFailedSrc(src);
          }}
          src={src}
        />
      ) : null}
    </div>
  );
}
