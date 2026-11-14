import { Button, DropdownMenu, SegmentedControl, Spinner } from "@acbooks/ui";
import { Ellipsis, Trash2 } from "lucide-react";
import { useTranslation } from "react-i18next";

import type { BookDetail, ReadingStatus } from "./books-api";

const STATUSES: readonly ReadingStatus[] = ["unread", "reading", "done"];

export interface BookDetailActionsProps {
  coverBusy: boolean;
  detail: BookDetail;
  onDelete: () => void;
  onEdit: () => void;
  onStatusChange: (status: ReadingStatus) => void;
  statusSaving: boolean;
}

export function BookDetailActions({
  coverBusy,
  detail,
  onDelete,
  onEdit,
  onStatusChange,
  statusSaving,
}: BookDetailActionsProps) {
  const { t } = useTranslation("library");
  const busy = statusSaving || coverBusy;
  return (
    <div className="flex w-full flex-col gap-2">
      <Button disabled={busy} onClick={onEdit} size="lg" variant="soft">
        {t("detail.edit")}
      </Button>
      <div className="flex items-center gap-2" aria-busy={statusSaving || undefined}>
        <SegmentedControl
          ariaLabel={t("detail.readingStatus")}
          className="min-w-0 flex-1 [&>button]:min-w-0 [&>button]:flex-1 max-md:[&>button]:px-1"
          disabled={busy}
          onValueChange={onStatusChange}
          options={STATUSES.map((status) => ({
            label: t(`status.${status}`),
            value: status,
          }))}
          value={detail.readingStatus}
          variant="boxed"
        />
        <DropdownMenu
          actions={[{ danger: true, icon: Trash2, label: t("detail.delete"), onSelect: onDelete }]}
          disabled={busy}
          label={t("actions.more")}
          triggerVariant="outline"
        >
          <Ellipsis aria-hidden="true" />
        </DropdownMenu>
      </div>
      {statusSaving ? (
        <div role="status" className="flex items-center gap-2 text-xs text-muted-foreground">
          <Spinner label={t("detail.savingStatus")} />
          {t("detail.savingStatus")}
        </div>
      ) : null}
    </div>
  );
}
