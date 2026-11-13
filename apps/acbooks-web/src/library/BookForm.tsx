import {
  Button,
  Field,
  IconButton,
  Input,
  MultiValueInput,
  Select,
  Textarea,
} from "@acbooks/ui";
import { X } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";

import { toApiError, type ApiError } from "../api/client";
import {
  createBook,
  NAME_LIST_LIMIT,
  NAME_MAX_LENGTH,
  updateBook,
  type BookCreate,
  type BookDetail,
  type BookUpdate,
  type ReadingStatus,
} from "./books-api";
import { libraryErrorMessage, libraryRequestId } from "./errors";
import { LibraryAlert } from "./LibraryAlert";

export type BookFormMode = "create" | "edit";

export interface BookFormProps {
  book?: BookDetail;
  mode: BookFormMode;
  onBusyChange: (busy: boolean) => void;
  onCloseRequest: () => void;
  onDirtyChange: (dirty: boolean) => void;
  onSaved: (book: BookDetail, mode: BookFormMode) => void;
  onSessionExpired: () => void;
}

const STATUS_OPTIONS: readonly ReadingStatus[] = [
  "unread",
  "reading",
  "done",
];

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

export function BookForm({
  book,
  mode,
  onBusyChange,
  onCloseRequest,
  onDirtyChange,
  onSaved,
  onSessionExpired,
}: BookFormProps) {
  const { t } = useTranslation("library");
  const [title, setTitle] = useState(book?.title ?? "");
  const [description, setDescription] = useState(book?.description ?? "");
  const [authors, setAuthors] = useState<string[]>(book?.authors ?? []);
  const [translators, setTranslators] = useState<string[]>(
    book?.translators ?? [],
  );
  const [isbn, setIsbn] = useState(book?.isbn ?? "");
  const [readingStatus, setReadingStatus] = useState<ReadingStatus>(
    book?.readingStatus ?? "unread",
  );
  const [tags, setTags] = useState<string[]>(book?.tags ?? []);
  const [shelves, setShelves] = useState<string[]>(book?.shelves ?? []);
  const [titleError, setTitleError] = useState<string | undefined>(undefined);
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);

  const formValues: BookFormValues = {
    title: title.trim(),
    description: description.trim().length > 0 ? description.trim() : null,
    authors,
    translators,
    isbn: isbn.trim().length > 0 ? isbn.trim() : null,
    readingStatus,
    tags,
    shelves,
  };

  const dirty =
    mode === "edit" && book !== undefined
      ? Object.keys(buildUpdate(book, formValues)).length > 0
      : title.trim().length > 0 ||
        description.trim().length > 0 ||
        isbn.trim().length > 0 ||
        authors.length > 0 ||
        translators.length > 0 ||
        tags.length > 0 ||
        shelves.length > 0 ||
        readingStatus !== "unread";

  useEffect(() => {
    onDirtyChange(dirty);
  }, [dirty, onDirtyChange]);

  useEffect(() => {
    onBusyChange(busy);
  }, [busy, onBusyChange]);

  const clearError = () => {
    setError(null);
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (busy) {
      return;
    }
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
      authors,
      translators,
      isbn: isbn.trim().length > 0 ? isbn.trim() : null,
      readingStatus,
      tags,
      shelves,
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

  return (
    <>
      <div className="flex items-center justify-between gap-3 border-b border-border p-4 sm:p-5">
        <h2 className="text-lg font-semibold" id="book-form-title">
          {titleText}
        </h2>
        <IconButton
          className="-mr-2 shrink-0"
          disabled={busy}
          label={t("form.close")}
          onClick={onCloseRequest}
        >
          <X aria-hidden="true" />
        </IconButton>
      </div>

      <form
        aria-busy={busy || undefined}
        className="flex min-h-0 flex-1 flex-col"
        noValidate
        onSubmit={handleSubmit}
      >
        <div className="min-h-0 flex-1 overflow-y-auto p-4 sm:p-5">
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

              <Field
                description={t("form.authorsHint")}
                htmlFor="book-authors"
                label={t("form.authors")}
              >
                <MultiValueInput
                  disabled={busy}
                  id="book-authors"
                  maxLength={NAME_MAX_LENGTH}
                  maxValues={NAME_LIST_LIMIT}
                  onChange={(values) => {
                    setAuthors(values);
                    clearError();
                  }}
                  placeholder={t("form.multiValuePlaceholder")}
                  values={authors}
                />
              </Field>

              <Field
                description={t("form.translatorsHint")}
                htmlFor="book-translators"
                label={t("form.translators")}
              >
                <MultiValueInput
                  disabled={busy}
                  id="book-translators"
                  maxLength={NAME_MAX_LENGTH}
                  maxValues={NAME_LIST_LIMIT}
                  onChange={(values) => {
                    setTranslators(values);
                    clearError();
                  }}
                  placeholder={t("form.multiValuePlaceholder")}
                  values={translators}
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

              <Field htmlFor="book-status" label={t("form.readingStatus")}>
                <Select
                  ariaLabel={t("form.readingStatus")}
                  disabled={busy}
                  id="book-status"
                  onValueChange={(value) => {
                    setReadingStatus(value as ReadingStatus);
                    clearError();
                  }}
                  options={STATUS_OPTIONS.map((status) => ({
                    label: t(`status.${status}`),
                    value: status,
                  }))}
                  value={readingStatus}
                />
              </Field>

              <Field
                description={t("form.tagsHint")}
                htmlFor="book-tags"
                label={t("form.tags")}
              >
                <MultiValueInput
                  disabled={busy}
                  id="book-tags"
                  maxLength={NAME_MAX_LENGTH}
                  maxValues={NAME_LIST_LIMIT}
                  onChange={(values) => {
                    setTags(values);
                    clearError();
                  }}
                  placeholder={t("form.multiValuePlaceholder")}
                  values={tags}
                />
              </Field>

              <Field
                description={t("form.shelvesHint")}
                htmlFor="book-shelves"
                label={t("form.shelves")}
              >
                <MultiValueInput
                  disabled={busy}
                  id="book-shelves"
                  maxLength={NAME_MAX_LENGTH}
                  maxValues={NAME_LIST_LIMIT}
                  onChange={(values) => {
                    setShelves(values);
                    clearError();
                  }}
                  placeholder={t("form.multiValuePlaceholder")}
                  values={shelves}
                />
              </Field>
            </div>

            <Field htmlFor="book-description" label={t("form.description")}>
              <Textarea
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

        <div className="flex flex-wrap justify-end gap-2 border-t border-border p-4 sm:p-5">
          <Button disabled={busy} onClick={onCloseRequest} variant="ghost">
            {t("form.cancel")}
          </Button>
          <Button loading={busy} type="submit">
            {t("form.save")}
          </Button>
        </div>
      </form>
    </>
  );
}
