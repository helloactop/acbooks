import { Button, DropdownMenu, SegmentedControl } from "@acbooks/ui";
import {
  Check,
  Ellipsis,
  LayoutGrid,
  List,
  RefreshCw,
  Scaling,
  SlidersHorizontal,
} from "lucide-react";
import { useTranslation } from "react-i18next";

import type { CoverSize, ViewMode } from "./library-view-state";

export interface LibraryFilterToolbarProps {
  activeCount: number;
  coverSize: CoverSize;
  filtersOpen: boolean;
  onCoverSizeChange: (size: CoverSize) => void;
  onRefresh: () => void;
  onToggleFilters: () => void;
  onViewChange: (view: ViewMode) => void;
  view: ViewMode;
}

export function LibraryFilterToolbar({
  activeCount,
  coverSize,
  filtersOpen,
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
    <div className="flex shrink-0 flex-wrap items-center gap-2 max-md:w-full">
      <Button
        aria-label={filterLabel}
        aria-pressed={filtersOpen}
        className="shrink-0 aria-pressed:bg-surface max-md:flex-1"
        onClick={onToggleFilters}
        variant="outline"
      >
        <SlidersHorizontal aria-hidden="true" />
        {t("filters.open")}
        {activeCount > 0 ? (
          <span className="inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-accent-soft px-1 text-[11px] font-semibold text-accent-text tabular-nums">
            {activeCount}
          </span>
        ) : null}
      </Button>

      <SegmentedControl
        ariaLabel={t("view.label")}
        className="[&>button]:min-w-8 max-md:[&>button]:min-w-11"
        onValueChange={onViewChange}
        options={[
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
        ]}
        value={view}
        variant="boxed"
      />

      {view === "grid" ? (
        <DropdownMenu
          actions={(["small", "medium", "large"] as const).map((size) => ({
            icon: coverSize === size ? Check : undefined,
            label: t(`view.cover.${size}`),
            onSelect: () => onCoverSizeChange(size),
          }))}
          heading={t("view.coverSize")}
          label={t("view.coverSize")}
          triggerVariant="outline"
        >
          <Scaling aria-hidden="true" />
        </DropdownMenu>
      ) : null}

      <DropdownMenu
        actions={[
          {
            icon: RefreshCw,
            label: t("actions.refresh"),
            onSelect: onRefresh,
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
