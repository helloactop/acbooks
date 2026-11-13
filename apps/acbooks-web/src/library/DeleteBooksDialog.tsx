import { Button, Spinner } from "@acbooks/ui";
import { useTranslation } from "react-i18next";

import { Modal } from "./Modal";

export interface DeleteBooksDialogProps {
  busy: boolean;
  count: number;
  error?: string;
  onCancel: () => void;
  onConfirm: () => void;
}

export function DeleteBooksDialog({
  busy,
  count,
  error,
  onCancel,
  onConfirm,
}: DeleteBooksDialogProps) {
  const { t } = useTranslation("library");

  return (
    <Modal
      describedBy="delete-books-description"
      labelledBy="delete-books-title"
      onClose={busy ? () => undefined : onCancel}
      size="confirm"
    >
      <div className="p-5">
        <h2 className="text-lg font-semibold" id="delete-books-title">
          {t("batch.deleteTitle", { count })}
        </h2>
        <p
          className="mt-2 text-sm text-muted-foreground"
          id="delete-books-description"
        >
          {t("batch.deleteDescription")}
        </p>
        {error !== undefined ? (
          <p className="mt-3 text-sm text-destructive-text" role="alert">
            {error}
          </p>
        ) : null}
        <div className="mt-6 flex flex-wrap items-center justify-end gap-2">
          {busy ? <Spinner label={t("batch.deleting")} /> : null}
          <Button disabled={busy} onClick={onCancel} variant="ghost">
            {t("form.cancel")}
          </Button>
          <Button
            disabled={busy}
            onClick={onConfirm}
            variant="danger-confirm"
          >
            {t("batch.deleteConfirm")}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
