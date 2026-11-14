import { Button } from "@acbooks/ui";
import { useTranslation } from "react-i18next";

export interface LibraryBatchBarProps {
  allSelected: boolean;
  count: number;
  disabled?: boolean;
  onClear: () => void;
  onDelete: () => void;
  onEdit: () => void;
  onMetadata: () => void;
  onSelectAll: () => void;
}

export function LibraryBatchBar({
  allSelected,
  count,
  disabled = false,
  onClear,
  onDelete,
  onEdit,
  onMetadata,
  onSelectAll,
}: LibraryBatchBarProps) {
  const { t } = useTranslation("library");

  return (
    <div className="sticky top-0 z-20 flex flex-wrap items-center gap-2 rounded-card border border-border bg-background px-3 py-2">
      <span className="text-[13px] font-medium" role="status">
        {t("selection.count", { count })}
      </span>
      <div className="ml-auto flex flex-wrap items-center gap-2">
        <Button disabled={disabled} onClick={onSelectAll} size="sm" variant="ghost">
          {allSelected ? t("selection.clearPage") : t("selection.selectPage")}
        </Button>
        <Button disabled={disabled} onClick={onClear} size="sm" variant="ghost">
          {t("selection.clear")}
        </Button>
        <Button
          disabled={disabled || count === 0}
          onClick={onEdit}
          size="sm"
          variant="outline"
        >
          {t("selection.edit")}
        </Button>
        <Button disabled={disabled || count === 0} onClick={onMetadata} size="sm" variant="outline">
          {t("metadata.batch")}
        </Button>
        <Button
          disabled={disabled || count === 0}
          onClick={onDelete}
          size="sm"
          variant="danger"
        >
          {t("selection.delete")}
        </Button>
      </div>
    </div>
  );
}
