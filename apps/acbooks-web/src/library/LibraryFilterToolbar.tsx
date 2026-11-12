import {
  Button,
  DropdownMenu,
  IconButton,
  Input,
  SegmentedControl,
  Select,
  cn,
  type SegmentedControlOption,
} from "@acbooks/ui";
import {
  ArrowUpDown,
  Check,
  LayoutGrid,
  List,
  Search,
  SlidersHorizontal,
  X,
} from "lucide-react";
import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";

import { useMediaQuery } from "../layout/use-media-query";
import type { SortOrder, StatusFilter, ViewMode } from "./library-view-state";

export interface LibraryFilterToolbarProps {
  activeCount: number;
  filtersOpen: boolean;
  hasSearch: boolean;
  onClearSearch: () => void;
  onSearchChange: (value: string) => void;
  onSortChange: (value: SortOrder) => void;
  onStatusChange: (value: StatusFilter) => void;
  onToggleFilters: () => void;
  onViewChange: (value: ViewMode) => void;
  searchValue: string;
  sort: SortOrder;
  status: StatusFilter;
  view: ViewMode;
}

const FILTER_CONTROL =
  "h-10 max-lg:min-h-11 aria-pressed:border-accent-border aria-pressed:bg-accent-soft aria-pressed:text-accent-text aria-pressed:hover:bg-accent-hover";
const VIEW_SEGMENTED =
  "h-10 [&>button]:h-8 max-lg:h-11 max-lg:p-0 max-lg:[&>button]:h-11";

export function LibraryFilterToolbar({
  activeCount,
  filtersOpen,
  hasSearch,
  onClearSearch,
  onSearchChange,
  onSortChange,
  onStatusChange,
  onToggleFilters,
  onViewChange,
  searchValue,
  sort,
  status,
  view,
}: LibraryFilterToolbarProps) {
  const { t } = useTranslation("library");
  const compact = useMediaQuery("(max-width: 1023px)");
  const inputRef = useRef<HTMLInputElement>(null);
  const timer = useRef<number | undefined>(undefined);
  const composing = useRef(false);
  const lastCommitted = useRef(searchValue);

  useEffect(() => {
    if (
      inputRef.current !== null &&
      inputRef.current.value !== searchValue
    ) {
      inputRef.current.value = searchValue;
    }
    if (searchValue.length === 0) {
      if (timer.current !== undefined) {
        window.clearTimeout(timer.current);
        timer.current = undefined;
      }
      composing.current = false;
      lastCommitted.current = "";
    }
  }, [searchValue]);

  useEffect(
    () => () => {
      if (timer.current !== undefined) {
        window.clearTimeout(timer.current);
      }
    },
    [],
  );

  const commitSearch = (value: string) => {
    if (timer.current !== undefined) {
      window.clearTimeout(timer.current);
      timer.current = undefined;
    }
    const next = value.trim();
    if (next === lastCommitted.current) {
      return;
    }
    lastCommitted.current = next;
    onSearchChange(next);
  };

  const scheduleSearch = (value: string, immediate = false) => {
    if (composing.current) {
      return;
    }
    if (timer.current !== undefined) {
      window.clearTimeout(timer.current);
      timer.current = undefined;
    }
    if (immediate) {
      commitSearch(value);
      return;
    }
    timer.current = window.setTimeout(() => {
      timer.current = undefined;
      commitSearch(value);
    }, 300);
  };

  const statusOptions: readonly SegmentedControlOption<StatusFilter>[] = [
    { label: t("filters.all"), value: "all" },
    { label: t("filters.unread"), value: "unread" },
    { label: t("filters.reading"), value: "reading" },
    { label: t("filters.done"), value: "done" },
  ];
  const viewOptions: readonly SegmentedControlOption<ViewMode>[] = [
    {
      label: (
        <>
          <LayoutGrid aria-hidden="true" />
          <span className="sr-only">{t("view.grid")}</span>
        </>
      ),
      value: "grid",
    },
    {
      label: (
        <>
          <List aria-hidden="true" />
          <span className="sr-only">{t("view.list")}</span>
        </>
      ),
      value: "list",
    },
  ];
  const moreLabel =
    activeCount > 0
      ? t("filters.moreActive", { count: activeCount })
      : t("filters.more");
  const sortOptions: readonly { label: string; value: SortOrder }[] = [
    { label: t("filters.recent"), value: "recent" },
    { label: t("filters.title"), value: "title" },
    { label: t("filters.author"), value: "author" },
  ];
  const sortLabel =
    sortOptions.find((option) => option.value === sort)?.label ?? sort;

  return (
    <div className="flex flex-wrap items-center gap-2">
      <div className="relative min-w-[160px] flex-1 basis-[190px] max-sm:basis-full">
        <Search
          aria-hidden="true"
          className="pointer-events-none absolute top-1/2 left-3 size-[18px] -translate-y-1/2 text-muted-foreground"
          strokeWidth={2}
        />
        <Input
          aria-label={t("header.searchLabel")}
          className={cn(
            "pl-9 max-lg:h-11 [&::-webkit-search-cancel-button]:appearance-none",
            hasSearch && "pr-11",
          )}
          defaultValue={searchValue}
          onChange={(event) => {
            scheduleSearch(event.target.value);
          }}
          onCompositionEnd={(event) => {
            composing.current = false;
            scheduleSearch(event.currentTarget.value, true);
          }}
          onCompositionStart={() => {
            composing.current = true;
          }}
          onKeyDown={(event) => {
            if (
              event.key === "Enter" &&
              !event.nativeEvent.isComposing
            ) {
              event.preventDefault();
              scheduleSearch(event.currentTarget.value, true);
            }
          }}
          placeholder={t("header.searchPlaceholder")}
          ref={inputRef}
          type="search"
        />
        {hasSearch ? (
          <IconButton
            className="absolute top-1/2 right-1 size-8 -translate-y-1/2 max-md:size-10"
            label={t("filters.clearSearch")}
            onClick={onClearSearch}
            variant="ghost"
          >
            <X aria-hidden="true" />
          </IconButton>
        ) : null}
      </div>

      <div className="flex w-full flex-wrap items-center gap-2 sm:contents">
        {compact ? (
          <Select
            aria-label={t("filters.status")}
            className="w-[104px] max-lg:h-11"
            containerClassName="shrink-0"
            onChange={(event) => {
              onStatusChange(event.target.value as StatusFilter);
            }}
            value={status}
          >
            <option value="all">{t("filters.all")}</option>
            <option value="unread">{t("filters.unread")}</option>
            <option value="reading">{t("filters.reading")}</option>
            <option value="done">{t("filters.done")}</option>
          </Select>
        ) : (
          <SegmentedControl
            ariaLabel={t("filters.status")}
            className="shrink-0 whitespace-nowrap"
            onValueChange={onStatusChange}
            options={statusOptions}
            value={status}
            variant="boxed"
          />
        )}

        <Button
          aria-controls="library-smart-filter-panel"
          aria-expanded={filtersOpen}
          aria-label={moreLabel}
          aria-pressed={filtersOpen || activeCount > 0}
          className={cn(FILTER_CONTROL, "shrink-0 px-3")}
          onClick={onToggleFilters}
          variant="outline"
        >
          <SlidersHorizontal aria-hidden="true" className="lg:hidden" />
          <span className="max-lg:sr-only">{t("filters.more")}</span>
          {activeCount > 0 ? (
            <span className="inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-accent-text px-1 text-[11px] font-semibold text-background tabular-nums">
              {activeCount}
            </span>
          ) : null}
        </Button>

        {compact ? (
          <DropdownMenu
            actions={sortOptions.map((option) => ({
              icon: sort === option.value ? Check : undefined,
              label: option.label,
              onSelect: () => {
                onSortChange(option.value);
              },
            }))}
            className="shrink-0"
            label={t("filters.sortCurrent", { value: sortLabel })}
            side="bottom"
            triggerClassName="size-10 max-lg:size-11"
            triggerVariant="outline"
          >
            <ArrowUpDown aria-hidden="true" />
          </DropdownMenu>
        ) : (
          <label className="inline-flex shrink-0 items-center gap-1.5">
            <ArrowUpDown
              aria-hidden="true"
              className="size-4 shrink-0 text-muted-foreground"
              strokeWidth={2}
            />
            <Select
              aria-label={t("filters.sort")}
              className="h-10 w-[168px]"
              onChange={(event) => {
                onSortChange(event.target.value as SortOrder);
              }}
              value={sort}
            >
              {sortOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          </label>
        )}

        <SegmentedControl
          ariaLabel={t("view.label")}
          className={cn(VIEW_SEGMENTED, "shrink-0 max-sm:ml-auto")}
          onValueChange={onViewChange}
          options={viewOptions}
          value={view}
        />
      </div>
    </div>
  );
}
