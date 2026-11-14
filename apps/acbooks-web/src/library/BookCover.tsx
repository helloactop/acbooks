import { cn } from "@acbooks/ui";
import { Book } from "lucide-react";
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
  thumbnail?: boolean;
  titleClassName?: string;
}

export function BookCover({
  authorClassName,
  authorLabel,
  book,
  className,
  flat = false,
  thumbnail = false,
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
      {thumbnail ? (
        <div className="absolute inset-0 grid place-items-center">
          <Book className="size-4" />
        </div>
      ) : (
        <div
          className="absolute inset-2 flex flex-col justify-between rounded border px-3 py-3.5"
          style={{ borderColor: cover.line }}
        >
          <div
            className={cn("overflow-hidden font-bold wrap-break-word", titleClassName)}
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
      )}
      {src !== null && !imageFailed ? (
        <div className="absolute inset-0">
          <img
            alt=""
            className="absolute inset-0 h-full w-full object-cover"
            decoding="async"
            loading="lazy"
            onError={() => {
              setFailedSrc(src);
            }}
            src={src}
          />
          <span className="pointer-events-none absolute inset-0 rounded-cover ring-1 ring-foreground/10 ring-inset" />
        </div>
      ) : null}
    </div>
  );
}
