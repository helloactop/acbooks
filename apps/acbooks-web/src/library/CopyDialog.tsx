import { Button } from "@acbooks/ui";
import { useState } from "react";
import { useTranslation } from "react-i18next";

import { toApiError, type ApiError } from "../api/client";
import {
  createBookCopy,
  updateBookCopy,
  type BookCopy,
} from "./copies-api";
import { CopyFormFields } from "./CopyFormFields";
import { copyDraftToBody, emptyCopyDraft, type CopyDraft } from "./copy-draft";
import { libraryErrorMessage } from "./errors";
import { Modal } from "./Modal";

function toDraft(copy: BookCopy): CopyDraft {
  return {
    acquiredAt:
      copy.acquiredAt === null
        ? ""
        : new Date(copy.acquiredAt).toISOString().slice(0, 10),
    locationId: copy.locationId,
    note: copy.note ?? "",
  };
}

export interface CopyDialogProps {
  bookId: string;
  copy?: BookCopy;
  onClose: () => void;
  onSaved: () => void;
  onSessionExpired: () => void;
}

export function CopyDialog({
  bookId,
  copy,
  onClose,
  onSaved,
  onSessionExpired,
}: CopyDialogProps) {
  const { t } = useTranslation("library");
  const editing = copy !== undefined;
  const [draft, setDraft] = useState<CopyDraft>(
    copy === undefined ? emptyCopyDraft() : toDraft(copy),
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const submit = async () => {
    if (busy) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const body = copyDraftToBody(draft);
      if (editing) {
        await updateBookCopy(bookId, copy.id, body);
      } else {
        await createBookCopy(bookId, body);
      }
      setBusy(false);
      onSaved();
    } catch (cause) {
      const apiError = toApiError(cause);
      if (apiError.status === 401) {
        onSessionExpired();
        return;
      }
      setError(apiError);
      setBusy(false);
    }
  };

  const errorMessage =
    error === null ? undefined : libraryErrorMessage(t, error);

  return (
    <Modal
      labelledBy="copy-form-title"
      onClose={busy ? () => undefined : onClose}
    >
      <div className="flex items-center justify-between gap-3 border-b border-border p-4 sm:p-5">
        <h2 className="text-lg font-semibold" id="copy-form-title">
          {editing ? t("copy.editTitle") : t("copy.addTitle")}
        </h2>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto p-4 sm:p-5">
        <div className="flex flex-col gap-4">
          {errorMessage !== undefined ? (
            <p className="text-sm text-destructive-text" role="alert">
              {errorMessage}
            </p>
          ) : null}
          <CopyFormFields
            disabled={busy}
            draft={draft}
            idPrefix="copy-form"
            onChange={setDraft}
          />
        </div>
      </div>
      <div className="flex flex-wrap justify-end gap-2 border-t border-border p-4 sm:p-5">
        <Button disabled={busy} onClick={onClose} variant="ghost">
          {t("form.cancel")}
        </Button>
        <Button
          loading={busy}
          onClick={() => {
            void submit();
          }}
        >
          {t("form.save")}
        </Button>
      </div>
    </Modal>
  );
}