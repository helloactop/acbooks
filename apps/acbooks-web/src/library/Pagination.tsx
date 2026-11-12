import { Button, IconButton, useLanguage } from "@acbooks/ui";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useTranslation } from "react-i18next";

export interface PaginationProps {
  disabled?: boolean;
  limit: number;
  offset: number;
  onOffsetChange: (offset: number) => void;
  total: number;
}

type PageItem = number | "ellipsis";

function pageItems(current: number, total: number): PageItem[] {
  if (total <= 7) {
    return Array.from({ length: total }, (_, index) => index + 1);
  }

  const pages = Array.from(
    new Set(
      [1, total, current - 1, current, current + 1].filter(
        (page) => page >= 1 && page <= total,
      ),
    ),
  ).sort((left, right) => left - right);

  const items: PageItem[] = [];
  let previous = 0;
  for (const page of pages) {
    if (previous > 0 && page - previous > 1) {
      items.push("ellipsis");
    }
    items.push(page);
    previous = page;
  }
  return items;
}

export function Pagination({
  disabled = false,
  limit,
  offset,
  onOffsetChange,
  total,
}: PaginationProps) {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  if (total === 0) {
    return null;
  }

  const pageCount = Math.max(1, Math.ceil(total / limit));
  const currentPage = Math.min(
    pageCount,
    Math.floor(offset / limit) + 1,
  );
  const numberFormat = new Intl.NumberFormat(resolvedLanguage);
  const rangeStart = offset + 1;
  const rangeEnd = Math.min(offset + limit, total);

  return (
    <nav
      aria-label={t("pagination.label")}
      className="mt-auto flex items-center justify-between gap-3 pt-2"
    >
      <span className="text-[13px] text-muted-foreground max-md:hidden">
        {t("pagination.range", {
          start: numberFormat.format(rangeStart),
          end: numberFormat.format(rangeEnd),
          total: numberFormat.format(total),
        })}
      </span>

      <div className="mx-auto flex items-center gap-1">
        <IconButton
          disabled={disabled || currentPage === 1}
          label={t("pagination.previous")}
          onClick={() => {
            onOffsetChange(Math.max(0, offset - limit));
          }}
        >
          <ChevronLeft aria-hidden="true" />
        </IconButton>

        {pageItems(currentPage, pageCount).map((item, index) =>
          item === "ellipsis" ? (
            <span
              key={`ellipsis-${index}`}
              aria-hidden="true"
              className="px-1 text-muted-foreground"
            >
              …
            </span>
          ) : (
            <Button
              key={item}
              aria-current={item === currentPage ? "page" : undefined}
              aria-label={t("pagination.page", { page: item })}
              className="min-w-9 px-2"
              disabled={disabled}
              onClick={() => {
                onOffsetChange((item - 1) * limit);
              }}
              size="md"
              variant={item === currentPage ? "soft" : "ghost"}
            >
              {item}
            </Button>
          ),
        )}

        <IconButton
          disabled={disabled || currentPage === pageCount}
          label={t("pagination.next")}
          onClick={() => {
            onOffsetChange(Math.min((pageCount - 1) * limit, offset + limit));
          }}
        >
          <ChevronRight aria-hidden="true" />
        </IconButton>
      </div>

      <span className="w-[120px] max-md:hidden" />
    </nav>
  );
}
