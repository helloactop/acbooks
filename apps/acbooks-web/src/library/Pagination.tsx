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
  const rangeStart = Math.min(offset + 1, total);
  const rangeEnd = Math.min(offset + limit, total);
  const requestPage = (nextOffset: number) => {
    if (nextOffset !== offset) {
      onOffsetChange(nextOffset);
    }
  };

  return (
    <nav
      aria-label={t("pagination.label")}
      className="mt-auto flex items-center justify-between gap-2 pt-1"
    >
      <span className="text-xs text-muted-foreground max-md:hidden">
        {t("pagination.range", {
          start: numberFormat.format(rangeStart),
          end: numberFormat.format(rangeEnd),
          total: numberFormat.format(total),
        })}
      </span>

      <div className="mx-auto flex min-w-0 items-center gap-1">
        <IconButton
          disabled={disabled || currentPage === 1}
          className="shrink-0 max-md:size-[44px]"
          label={t("pagination.previous")}
          onClick={() => {
            requestPage(Math.max(0, offset - limit));
          }}
        >
          <ChevronLeft aria-hidden="true" />
        </IconButton>

        <span className="min-w-0 text-center text-[13px] tabular-nums text-muted-foreground md:hidden">
          {t("pagination.pageOf", {
            page: currentPage,
            total: pageCount,
          })}
        </span>

        <div className="hidden items-center gap-1 md:flex">
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
                  requestPage((item - 1) * limit);
                }}
                size="md"
                variant={item === currentPage ? "outline" : "ghost"}
              >
                {item}
              </Button>
            ),
          )}
        </div>

        <IconButton
          disabled={disabled || currentPage === pageCount}
          className="shrink-0 max-md:size-[44px]"
          label={t("pagination.next")}
          onClick={() => {
            requestPage(
              Math.min((pageCount - 1) * limit, offset + limit),
            );
          }}
        >
          <ChevronRight aria-hidden="true" />
        </IconButton>
      </div>

      <span className="w-[120px] max-md:hidden" />
    </nav>
  );
}
