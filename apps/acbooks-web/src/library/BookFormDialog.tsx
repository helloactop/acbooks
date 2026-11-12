import { Button, Field, Input } from "@acbooks/ui";
import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";

import { toApiError, type ApiError } from "../api/client";
import {
  createBook,
  updateBook,
  type BookCreate,
  type BookDetail,
  type BookUpdate,
  type ReadingStatus,
} from "./books-api";
import { libraryErrorMessage, libraryRequestId } from "./errors";
import { LibraryAlert } from "./LibraryAlert";
import { Modal } from "./Modal";

export type BookFormMode = "create" | "edit";

export interface BookFormDialogProps {
  book?: BookDetail;
  mode: BookFormMode;
  onClose: () => void;
  onSaved: (book: BookDetail, mode: BookFormMode) => void;
  onSessionExpired: () => void;
}

const STATUS_OPTIONS: readonly ReadingStatus[] = [
  "unread",
  "reading",
  "done",
];

function splitNames(value: string): string[] {
  return value
    .split(/[,，]/)
    .map((part) => part.trim())
    .filter((part) => part.length > 0);
}

function sameNames(left: readonly string[], right: readonly string[]): boolean {
  return (
    left.length === right.length &&
    left.every((value, index) => value === right[index])
  );
}

interface BookFormValues {
  authors: string[];
  description: string | null;
  isbn: string | null;
  readingStatus: ReadingStatus;
  shelves: string[];
  tags: string[];
  title: string;
  translators: string[];
}

function buildUpdate(book: BookDetail, values: BookFormValues): BookUpdate {
  const update: BookUpdate = {};
  if (values.title !== book.title) {
    update.title = values.title;
  }
  if ((values.description ?? "") !== (book.description ?? "")) {
    update.description = values.description;
  }
  if (!sameNames(values.authors, book.authors)) {
    update.authors = values.authors;
  }
  if (!sameNames(values.translators, book.translators)) {
    update.translators = values.translators;
  }
  if ((values.isbn ?? "") !== (book.isbn ?? "")) {
    update.isbn = values.isbn;
  }
  if (values.readingStatus !== book.readingStatus) {
    update.readingStatus = values.readingStatus;
  }
  if (!sameNames(values.tags, book.tags)) {
    update.tags = values.tags;
  }
  if (!sameNames(values.shelves, book.shelves)) {
    update.shelves = values.shelves;
  }
  return update;
}

export function BookFormDialog({
  book,
  mode,
  onClose,
  onSaved,
  onSessionExpired,
}: BookFormDialogProps) {
  const { t } = useTranslation("library");
  const [title, setTitle] = useState(book?.title ?? "");
  const [description, setDescription] = useState(book?.description ?? "");
  const [authors, setAuthors] = useState((book?.authors ?? []).join(", "));
  const [translators, setTranslators] = useState(
    (book?.translators ?? []).join(", "),
  );
  const [isbn, setIsbn] = useState(book?.isbn ?? "");
  const [readingStatus, setReadingStatus] = useState<ReadingStatus>(
    book?.readingStatus ?? "unread",
  );
  const [tags, setTags] = useState((book?.tags ?? []).join(", "));
  const [shelves, setShelves] = useState((book?.shelves ?? []).join(", "));
  const [titleError, setTitleError] = useState<string | undefined>(undefined);
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);

  const clearError = () => {
    setError(null);
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedTitle = title.trim();
    if (trimmedTitle.length === 0) {
      setTitleError(t("form.titleRequired"));
      return;
    }
    setTitleError(undefined);
    setBusy(true);
    setError(null);

    const values: BookFormValues = {
      title: trimmedTitle,
      description:
        description.trim().length > 0 ? description.trim() : null,
      authors: splitNames(authors),
      translators: splitNames(translators),
      isbn: isbn.trim().length > 0 ? isbn.trim() : null,
      readingStatus,
      tags: splitNames(tags),
      shelves: splitNames(shelves),
    };

    try {
      let saved: BookDetail;
      if (mode === "edit" && book !== undefined) {
        const update = buildUpdate(book, values);
        if (Object.keys(update).length === 0) {
          setBusy(false);
          onSaved(book, mode);
          return;
        }
        saved = await updateBook(book.id, update);
      } else {
        const body: BookCreate = values;
        saved = await createBook(body);
      }
      setBusy(false);
      onSaved(saved, mode);
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
  const errorRequestId = error === null ? undefined : libraryRequestId(error);
  const titleText =
    mode === "edit" ? t("form.editTitle") : t("form.addTitle");
  const handleClose = () => {
    if (!busy) {
      onClose();
    }
  };

  return (
    <Modal labelledBy="book-form-title" onClose={handleClose}>
      <div className="flex items-center border-b border-border p-4">
        <h2 className="text-lg font-semibold" id="book-form-title">
          {titleText}
        </h2>
      </div>

      <form
        aria-busy={busy || undefined}
        className="flex min-h-0 flex-1 flex-col"
        noValidate
        onSubmit={handleSubmit}
      >
        <div className="min-h-0 flex-1 overflow-y-auto p-4">
          <div className="flex flex-col gap-4">
            {errorMessage !== undefined ? (
              <LibraryAlert requestId={errorRequestId}>
                {errorMessage}
              </LibraryAlert>
            ) : null}

            <div className="grid gap-4 sm:grid-cols-2">
              <Field
                className="sm:col-span-2"
                error={titleError}
                htmlFor="book-title"
                label={t("form.title")}
              >
                <Input
                  autoComplete="off"
                  disabled={busy}
                  id="book-title"
                  maxLength={500}
                  onChange={(event) => {
                    setTitle(event.target.value);
                    setTitleError(undefined);
                    clearError();
                  }}
                  placeholder={t("form.titlePlaceholder")}
                  required
                  value={title}
                />
              </Field>

              <Field htmlFor="book-isbn" label={t("form.isbn")}>
                <Input
                  autoComplete="off"
                  disabled={busy}
                  id="book-isbn"
                  maxLength={32}
                  onChange={(event) => {
                    setIsbn(event.target.value);
                    clearError();
                  }}
                  placeholder={t("form.isbnPlaceholder")}
                  spellCheck={false}
                  value={isbn}
                />
              </Field>

              <Field
                description={t("form.authorsHint")}
                htmlFor="book-authors"
                label={t("form.authors")}
              >
                <Input
                  autoComplete="off"
                  disabled={busy}
                  id="book-authors"
                  onChange={(event) => {
                    setAuthors(event.target.value);
                    clearError();
                  }}
                  value={authors}
                />
              </Field>

              <Field
                description={t("form.translatorsHint")}
                htmlFor="book-translators"
                label={t("form.translators")}
              >
                <Input
                  autoComplete="off"
                  disabled={busy}
                  id="book-translators"
                  onChange={(event) => {
                    setTranslators(event.target.value);
                    clearError();
                  }}
                  value={translators}
                />
              </Field>

              <Field
                description={t("form.tagsHint")}
                htmlFor="book-tags"
                label={t("form.tags")}
              >
                <Input
                  autoComplete="off"
                  disabled={busy}
                  id="book-tags"
                  onChange={(event) => {
                    setTags(event.target.value);
                    clearError();
                  }}
                  value={tags}
                />
              </Field>

              <Field
                description={t("form.shelvesHint")}
                htmlFor="book-shelves"
                label={t("form.shelves")}
              >
                <Input
                  autoComplete="off"
                  disabled={busy}
                  id="book-shelves"
                  onChange={(event) => {
                    setShelves(event.target.value);
                    clearError();
                  }}
                  value={shelves}
                />
              </Field>

              <Field
                htmlFor="book-status"
                label={t("form.readingStatus")}
              >
                <select
                  className="h-10 w-full rounded-control border border-control-border bg-background px-3 text-sm text-foreground transition-colors duration-150 ease-out focus-visible:border-accent-text focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none disabled:cursor-not-allowed disabled:border-disabled-border disabled:bg-disabled-surface disabled:text-disabled-foreground"
                  disabled={busy}
                  id="book-status"
                  onChange={(event) => {
                    setReadingStatus(event.target.value as ReadingStatus);
                    clearError();
                  }}
                  value={readingStatus}
                >
                  {STATUS_OPTIONS.map((status) => (
                    <option key={status} value={status}>
                      {t(`status.${status}`)}
                    </option>
                  ))}
                </select>
              </Field>
            </div>

            <Field
              htmlFor="book-description"
              label={t("form.description")}
            >
              <textarea
                className="min-h-24 w-full resize-y rounded-control border border-control-border bg-background p-3 text-sm text-foreground transition-colors duration-150 ease-out placeholder:text-muted-foreground focus-visible:border-accent-text focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none disabled:cursor-not-allowed disabled:border-disabled-border disabled:bg-disabled-surface disabled:text-disabled-foreground"
                disabled={busy}
                id="book-description"
                maxLength={20000}
                onChange={(event) => {
                  setDescription(event.target.value);
                  clearError();
                }}
                placeholder={t("form.descriptionPlaceholder")}
                value={description}
              />
            </Field>
          </div>
        </div>

        <div className="flex justify-end gap-2 border-t border-border p-4">
          <Button disabled={busy} onClick={handleClose} variant="ghost">
            {t("form.cancel")}
          </Button>
          <Button loading={busy} type="submit">
            {t("form.save")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
