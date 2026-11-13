import { Button } from "@acbooks/ui";
import { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";

import { BookForm, type BookFormMode } from "./BookForm";
import type { BookDetail } from "./books-api";
import { Modal } from "./Modal";

export type { BookFormMode };

export interface BookFormDialogProps {
  book?: BookDetail;
  mode: BookFormMode;
  onClose: () => void;
  onSaved: (book: BookDetail, mode: BookFormMode) => void;
  onFilesFailed?: (names: string[]) => void;
  onSessionExpired: () => void;
}

export function BookFormDialog({
  book,
  mode,
  onClose,
  onFilesFailed,
  onSaved,
  onSessionExpired,
}: BookFormDialogProps) {
  const { t } = useTranslation("library");
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const requestClose = useCallback(() => {
    if (busy) {
      return;
    }
    if (dirty) {
      setConfirmOpen(true);
      return;
    }
    onClose();
  }, [busy, dirty, onClose]);

  return (
    <>
      <Modal labelledBy="book-form-title" onClose={requestClose}>
        <BookForm
          book={book}
          mode={mode}
          onBusyChange={setBusy}
          onCloseRequest={requestClose}
          onDirtyChange={setDirty}
          onFilesFailed={onFilesFailed}
          onSaved={onSaved}
          onSessionExpired={onSessionExpired}
        />
      </Modal>

      {confirmOpen ? (
        <Modal
          describedBy="book-form-discard-description"
          labelledBy="book-form-discard-title"
          onClose={() => {
            setConfirmOpen(false);
          }}
          size="confirm"
        >
          <div className="p-5">
            <h2 className="text-lg font-semibold" id="book-form-discard-title">
              {t("form.discardTitle")}
            </h2>
            <p
              className="mt-2 text-sm text-muted-foreground"
              id="book-form-discard-description"
            >
              {t("form.discardDescription")}
            </p>
            <div className="mt-6 flex flex-wrap justify-end gap-2">
              <Button
                onClick={() => {
                  setConfirmOpen(false);
                }}
                variant="ghost"
              >
                {t("form.keepEditing")}
              </Button>
              <Button
                onClick={() => {
                  setConfirmOpen(false);
                  onClose();
                }}
                variant="danger-confirm"
              >
                {t("form.discard")}
              </Button>
            </div>
          </div>
        </Modal>
      ) : null}
    </>
  );
}
