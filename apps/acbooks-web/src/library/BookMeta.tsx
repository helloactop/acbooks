import { cn } from "@acbooks/ui";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { libraryTagHref } from "../layout/routes";
import type { BookSummary, ReadingStatus } from "./books-api";

const CHIP = "rounded px-1.5 py-0.5 text-xs leading-4";
const TAG_CLASS = cn(CHIP, "bg-surface text-muted-foreground");
const FORMAT_CLASS = cn(
  CHIP,
  "border border-border bg-background text-foreground",
);
const PHYSICAL_CLASS = cn(CHIP, "bg-surface-active text-foreground");
const SHELF_CLASS = cn(
  CHIP,
  "border border-border bg-background text-foreground",
);

const STATUS_CLASSES: Record<ReadingStatus, string> = {
  done: cn(CHIP, "bg-accent-soft text-accent-text"),
  reading: cn(CHIP, "border border-border bg-background text-foreground"),
  unread: cn(CHIP, "text-muted-foreground"),
};

const STATUS_KEYS: Record<ReadingStatus, string> = {
  done: "status.done",
  reading: "status.reading",
  unread: "status.unread",
};

function unique(values: readonly string[]): string[] {
  return Array.from(new Set(values));
}

export function TagBadges({
  limit,
  tags,
}: {
  limit?: number;
  tags: readonly string[];
}) {
  const { t } = useTranslation("library");
  const labels = unique(tags);
  const visible = limit === undefined ? labels : labels.slice(0, limit);
  const hiddenCount = labels.length - visible.length;
  if (labels.length === 0) {
    return null;
  }
  return (
    <>
      {visible.map((tag) => (
        <span key={tag} className={TAG_CLASS}>
          {tag}
        </span>
      ))}
      {hiddenCount > 0 ? (
        <span
          aria-label={t("labels.moreTags", { count: hiddenCount })}
          className={TAG_CLASS}
        >
          +{hiddenCount}
        </span>
      ) : null}
    </>
  );
}

export function TagLinks({ tags }: { tags: readonly string[] }) {
  const labels = unique(tags);
  if (labels.length === 0) {
    return null;
  }
  return (
    <>
      {labels.map((tag) => (
        <Link
          className={cn(
            TAG_CLASS,
            "transition-colors duration-150 ease-out hover:bg-surface-hover hover:text-accent-text focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none",
          )}
          key={tag}
          to={libraryTagHref(tag)}
        >
          {tag}
        </Link>
      ))}
    </>
  );
}

export function FormatBadges({ formats }: { formats: readonly string[] }) {
  const labels = unique(formats.map((format) => format.toUpperCase()));
  if (labels.length === 0) {
    return null;
  }
  return (
    <>
      {labels.map((format) => (
        <span key={format} className={FORMAT_CLASS}>
          {format}
        </span>
      ))}
    </>
  );
}

export function PhysicalCopiesBadge({ count }: { count: number }) {
  const { t } = useTranslation("library");
  if (count <= 0) {
    return null;
  }
  return (
    <span className={PHYSICAL_CLASS}>
      {t("labels.physicalCopies", { count })}
    </span>
  );
}

export function ShelfBadges({ shelves }: { shelves: readonly string[] }) {
  const labels = unique(shelves);
  if (labels.length === 0) {
    return null;
  }
  return (
    <>
      {labels.map((shelf) => (
        <span key={shelf} className={SHELF_CLASS}>
          {shelf}
        </span>
      ))}
    </>
  );
}

export function ReadingStatusBadge({ status }: { status: ReadingStatus }) {
  const { t } = useTranslation("library");
  return <span className={STATUS_CLASSES[status]}>{t(STATUS_KEYS[status])}</span>;
}

export function BookMeta({
  book,
}: {
  book: Pick<
    BookSummary,
    "copyCount" | "formats" | "readingStatus" | "tags"
  >;
}) {
  return (
    <div className="flex flex-wrap items-center gap-1">
      <TagBadges tags={book.tags} />
      <FormatBadges formats={book.formats} />
      <PhysicalCopiesBadge count={book.copyCount} />
      <ReadingStatusBadge status={book.readingStatus} />
    </div>
  );
}
