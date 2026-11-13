import { Button, DropdownMenu } from "@acbooks/ui";
import {
  Check,
  ChevronDown,
  Ellipsis,
  LayoutGrid,
  List,
  RefreshCw,
} from "lucide-react";
import { useTranslation } from "react-i18next";

import type { CoverSize, ViewMode } from "./library-view-state";

export interface LibraryFilterToolbarProps {
  activeCount: number;
  canClear: boolean;
  coverSize: CoverSize;
  filtersOpen: boolean;
  onClearAll: () => void;
  onCoverSizeChange: (size: CoverSize) => void;
  onRefresh: () => void;
  onToggleFilters: () => void;
  onViewChange: (view: ViewMode) => void;
  view: ViewMode;
}

export function LibraryFilterToolbar({
  activeCount,
  canClear,
  coverSize,
  filtersOpen,
  onClearAll,
  onCoverSizeChange,
  onRefresh,
  onToggleFilters,
  onViewChange,
  view,
}: LibraryFilterToolbarProps) {
  const { t } = useTranslation("library");
  const filterLabel =
    activeCount > 0
      ? t("filters.moreActive", { count: activeCount })
      : t("filters.open");

  return (
    <div className="flex shrink-0 items-center gap-2 max-md:w-full">
      <Button
        aria-label={filterLabel}
        aria-pressed={filtersOpen}
        className="shrink-0 aria-pressed:bg-surface max-md:flex-1"
        onClick={onToggleFilters}
        variant="outline"
      >
        {t("filters.open")}
        {activeCount > 0 ? (
          <span className="inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-accent-soft px-1 text-[10px] font-semibold text-accent-text tabular-nums">
            {activeCount}
          </span>
        ) : null}
      </Button>

      <DropdownMenu
        actions={[
          {
            icon: view === "grid" ? Check : undefined,
            label: t("view.grid"),
            onSelect: () => {
              onViewChange("grid");
            },
          },
          {
            icon: view === "list" ? Check : undefined,
            label: t("view.list"),
            onSelect: () => {
              onViewChange("list");
            },
          },
          {
            icon: coverSize === "small" ? Check : undefined,
            label: t("view.coverSmall"),
            onSelect: () => {
              onViewChange("grid");
              onCoverSizeChange("small");
            },
          },
          {
            icon: coverSize === "medium" ? Check : undefined,
            label: t("view.coverMedium"),
            onSelect: () => {
              onViewChange("grid");
              onCoverSizeChange("medium");
            },
          },
          {
            icon: coverSize === "large" ? Check : undefined,
            label: t("view.coverLarge"),
            onSelect: () => {
              onViewChange("grid");
              onCoverSizeChange("large");
            },
          },
        ]}
        align="end"
        label={t("view.label")}
        side="bottom"
        trigger={
          <>
            {view === "grid" ? (
              <LayoutGrid aria-hidden="true" className="size-4 md:hidden" />
            ) : (
              <List aria-hidden="true" className="size-4 md:hidden" />
            )}
            <span className="hidden md:inline">{t("view.label")}</span>
            <ChevronDown
              aria-hidden="true"
              className="hidden size-3.5 md:inline"
            />
          </>
        }
        triggerClassName="inline-flex h-11 shrink-0 items-center justify-center gap-1 rounded-control border border-control-border px-2.5 text-[13px] font-medium text-foreground transition-colors duration-150 ease-out hover:bg-surface-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none md:h-8"
      />

      <DropdownMenu
        actions={[
          {
            icon: RefreshCw,
            label: t("actions.refresh"),
            onSelect: onRefresh,
          },
          {
            disabled: !canClear,
            label: t("filters.clearAll"),
            onSelect: onClearAll,
          },
        ]}
        className="shrink-0"
        label={t("actions.more")}
        side="bottom"
        triggerVariant="outline"
      >
        <Ellipsis aria-hidden="true" />
      </DropdownMenu>
    </div>
  );
}
