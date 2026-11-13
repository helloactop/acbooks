import {
  Button,
  Field,
  MultiValueInput,
  Select,
  Spinner,
} from "@acbooks/ui";
import { useState } from "react";
import { useTranslation } from "react-i18next";

import { toApiError, type ApiError } from "../api/client";
import {
  batchUpdateBooks,
  type ReadingStatus,
} from "./books-api";
import { libraryErrorMessage } from "./errors";
import { Modal } from "./Modal";

export interface BatchEditDialogProps {
  bookIds: string[];
  onClose: () => void;
  onSaved: () => void;
  onSessionExpired: () => void;
}

const STATUSES: readonly ReadingStatus[] = ["unread", "reading", "done"];

export function BatchEditDialog({
  bookIds,
  onClose,
  onSaved,
  onSessionExpired,
}: BatchEditDialogProps) {
  const { t } = useTranslation("library");
  const [status, setStatus] = useState("");
  const [addTags, setAddTags] = useState<string[]>([]);
  const [removeTags, setRemoveTags] = useState<string[]>([]);
  const [addShelves, setAddShelves] = useState<string[]>([]);
  const [removeShelves, setRemoveShelves] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const hasChanges =
    status !== "" ||
    addTags.length > 0 ||
    removeTags.length > 0 ||
    addShelves.length > 0 ||
    removeShelves.length > 0;

  const submit = async () => {
    if (!hasChanges || busy) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await batchUpdateBooks({
        bookIds,
        ...(status === "" ? {} : { readingStatus: status as ReadingStatus }),
        addTags,
        removeTags,
        addShelves,
        removeShelves,
      });
      onSaved();
    } catch (cause) {
      const apiError = toApiError(cause);
      if (apiError.status === 401) {
        onSessionExpired();
        return;
      }
      setError(apiError);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal labelledBy="batch-edit-title" onClose={onClose}>
      <div className="flex max-h-[calc(100dvh-2rem)] flex-col">
        <div className="border-b border-border px-5 py-4">
          <h2 className="text-lg font-semibold" id="batch-edit-title">
            {t("batch.editTitle", { count: bookIds.length })}
          </h2>
          <p className="mt-1 text-[13px] text-muted-foreground">
            {t("batch.editDescription")}
          </p>
        </div>

        <div className="flex flex-col gap-4 overflow-y-auto px-5 py-4">
          <Field htmlFor="batch-status" label={t("batch.status")}>
            <Select
              ariaLabel={t("batch.status")}
              id="batch-status"
              onValueChange={setStatus}
              options={[
                { label: t("batch.statusUnchanged"), value: "" },
                ...STATUSES.map((value) => ({
                  label: t(`status.${value}`),
                  value,
                })),
              ]}
              value={status}
            />
          </Field>

          <Field htmlFor="batch-add-tags" label={t("batch.addTags")}>
            <MultiValueInput
              id="batch-add-tags"
              onChange={setAddTags}
              placeholder={t("form.multiValuePlaceholder")}
              values={addTags}
            />
          </Field>
          <Field htmlFor="batch-remove-tags" label={t("batch.removeTags")}>
            <MultiValueInput
              id="batch-remove-tags"
              onChange={setRemoveTags}
              placeholder={t("form.multiValuePlaceholder")}
              values={removeTags}
            />
          </Field>
          <Field htmlFor="batch-add-shelves" label={t("batch.addShelves")}>
            <MultiValueInput
              id="batch-add-shelves"
              onChange={setAddShelves}
              placeholder={t("form.multiValuePlaceholder")}
              values={addShelves}
            />
          </Field>
          <Field htmlFor="batch-remove-shelves" label={t("batch.removeShelves")}>
            <MultiValueInput
              id="batch-remove-shelves"
              onChange={setRemoveShelves}
              placeholder={t("form.multiValuePlaceholder")}
              values={removeShelves}
            />
          </Field>

          {error !== null ? (
            <p className="text-sm text-destructive-text" role="alert">
              {libraryErrorMessage(t, error)}
            </p>
          ) : null}
        </div>

        <div className="flex flex-wrap items-center justify-end gap-2 border-t border-border px-5 py-3">
          {busy ? <Spinner label={t("batch.saving")} /> : null}
          <Button disabled={busy} onClick={onClose} variant="ghost">
            {t("form.cancel")}
          </Button>
          <Button
            disabled={busy || !hasChanges}
            onClick={() => {
              void submit();
            }}
            variant="default"
          >
            {t("batch.save")}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
