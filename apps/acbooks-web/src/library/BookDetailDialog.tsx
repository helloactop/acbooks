import { Button, Spinner, useLanguage } from "@acbooks/ui";
import { Pencil, RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { isAbortError, toApiError, type ApiError } from "../api/client";
import {
  FormatBadges,
  ReadingStatusBadge,
  TagBadges,
} from "./BookMeta";
import { getBook, type BookDetail } from "./books-api";
import { libraryErrorMessage, libraryRequestId } from "./errors";
import { LibraryAlert } from "./LibraryAlert";
import { Modal } from "./Modal";
import {
  COVER_FONT,
  coverFor,
  formatBytes,
  formatDate,
  formatDateTime,
  joinNames,
} from "./presentation";

export interface BookDetailDialogProps {
  bookId: string;
  onClose: () => void;
  onEdit: (book: BookDetail) => void;
  onSessionExpired: () => void;
}

export function BookDetailDialog({
  bookId,
  onClose,
  onEdit,
  onSessionExpired,
}: BookDetailDialogProps) {
  const { t } = useTranslation("library");
  const [detail, setDetail] = useState<BookDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    getBook(bookId, controller.signal)
      .then((book) => {
        setDetail(book);
        setError(null);
      })
      .catch((cause) => {
        if (isAbortError(cause)) {
          return;
        }
        const apiError = toApiError(cause);
        if (apiError.status === 401) {
          onSessionExpired();
          return;
        }
        setError(apiError);
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [attempt, bookId, onSessionExpired]);

  const errorMessage =
    error === null ? undefined : libraryErrorMessage(t, error);
  const errorRequestId = error === null ? undefined : libraryRequestId(error);

  return (
    <Modal labelledBy="book-detail-title" onClose={onClose}>
      <div className="flex items-center border-b border-border p-4">
        <h2 className="text-lg font-semibold" id="book-detail-title">
          {t("detail.title")}
        </h2>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto p-4">
        {loading ? (
          <div className="flex items-center justify-center gap-3 py-12 text-sm text-muted-foreground">
            <Spinner label={t("detail.loading")} />
            <span>{t("detail.loading")}</span>
          </div>
        ) : errorMessage !== undefined ? (
          <div className="flex flex-col gap-3">
            <LibraryAlert requestId={errorRequestId}>
              {errorMessage}
            </LibraryAlert>
            <Button
              onClick={() => {
                setLoading(true);
                setError(null);
                setAttempt((current) => current + 1);
              }}
              variant="soft"
            >
              <RefreshCw aria-hidden="true" />
              {t("errors.retry")}
            </Button>
          </div>
        ) : detail !== null ? (
          <BookDetailContent detail={detail} />
        ) : null}
      </div>

      {detail !== null && errorMessage === undefined ? (
        <div className="flex justify-end gap-2 border-t border-border p-4">
          <Button onClick={onClose} variant="ghost">
            {t("detail.close")}
          </Button>
          <Button onClick={() => onEdit(detail)}>
            <Pencil aria-hidden="true" />
            {t("detail.edit")}
          </Button>
        </div>
      ) : null}
    </Modal>
  );
}

function BookDetailContent({ detail }: { detail: BookDetail }) {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  const cover = coverFor(detail);
  const authors =
    joinNames(detail.authors, resolvedLanguage) || t("labels.unknownAuthor");
  const translators = joinNames(detail.translators, resolvedLanguage);

  return (
    <div className="flex flex-col gap-5">
      <div className="flex gap-4">
        <div
          aria-hidden="true"
          className="flex h-[132px] w-[88px] shrink-0 flex-col justify-between overflow-hidden rounded-cover border p-2.5 shadow-cover"
          style={{
            backgroundColor: cover.background,
            borderColor: cover.line,
            color: cover.foreground,
            fontFamily: COVER_FONT,
          }}
        >
          <span className="line-clamp-4 text-sm leading-tight font-bold break-all">
            {detail.title}
          </span>
          <span className="truncate text-[10px] opacity-85">{authors}</span>
        </div>
        <div className="min-w-0">
          <h3 className="text-xl font-semibold">{detail.title}</h3>
          <p className="mt-1 text-sm text-muted-foreground">{authors}</p>
          <div className="mt-2">
            <ReadingStatusBadge status={detail.readingStatus} />
          </div>
        </div>
      </div>

      <dl className="grid gap-x-4 gap-y-2 text-sm sm:grid-cols-[112px_minmax(0,1fr)]">
        <dt className="text-muted-foreground">{t("detail.authors")}</dt>
        <dd>{authors}</dd>
        <dt className="text-muted-foreground">{t("detail.translators")}</dt>
        <dd>{translators || t("labels.none")}</dd>
        <dt className="text-muted-foreground">{t("detail.isbn")}</dt>
        <dd className="tabular-nums">{detail.isbn ?? t("labels.none")}</dd>
        <dt className="text-muted-foreground">{t("detail.createdAt")}</dt>
        <dd>{formatDateTime(detail.createdAt, resolvedLanguage)}</dd>
        <dt className="text-muted-foreground">{t("detail.updatedAt")}</dt>
        <dd>{formatDateTime(detail.updatedAt, resolvedLanguage)}</dd>
      </dl>

      <section>
        <h4 className="text-sm font-semibold">{t("detail.tags")}</h4>
        <div className="mt-2 flex flex-wrap gap-1">
          {detail.tags.length > 0 ? (
            <TagBadges tags={detail.tags} />
          ) : (
            <span className="text-sm text-muted-foreground">
              {t("labels.none")}
            </span>
          )}
        </div>
      </section>

      <section>
        <h4 className="text-sm font-semibold">{t("detail.shelves")}</h4>
        <div className="mt-2 flex flex-wrap gap-1">
          {detail.shelves.length > 0 ? (
            detail.shelves.map((shelf) => (
              <span
                key={shelf}
                className="rounded border border-border bg-background px-1.5 py-0.5 text-xs leading-4"
              >
                {shelf}
              </span>
            ))
          ) : (
            <span className="text-sm text-muted-foreground">
              {t("labels.none")}
            </span>
          )}
        </div>
      </section>

      <section>
        <h4 className="text-sm font-semibold">{t("detail.description")}</h4>
        <p className="mt-1 text-sm leading-6 whitespace-pre-wrap">
          {detail.description ?? t("detail.noDescription")}
        </p>
      </section>

      <section>
        <h4 className="text-sm font-semibold">{t("detail.copies")}</h4>
        {detail.copies.length === 0 ? (
          <p className="mt-1 text-sm text-muted-foreground">
            {t("detail.noCopies")}
          </p>
        ) : (
          <ul className="mt-2 flex flex-col gap-2">
            {detail.copies.map((copy) => (
              <li
                key={copy.id}
                className="rounded-control border border-border p-3 text-sm"
              >
                <div className="flex items-center justify-between gap-3">
                  <span className="font-medium">
                    {copy.locationName ?? t("detail.unknownLocation")}
                  </span>
                  <span className="text-xs text-muted-foreground">
                    {copy.acquiredAt === null
                      ? t("labels.none")
                      : formatDate(copy.acquiredAt, resolvedLanguage)}
                  </span>
                </div>
                {copy.note ? (
                  <p className="mt-1 text-muted-foreground">{copy.note}</p>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h4 className="text-sm font-semibold">{t("detail.files")}</h4>
        {detail.files.length === 0 ? (
          <p className="mt-1 text-sm text-muted-foreground">
            {t("detail.noFiles")}
          </p>
        ) : (
          <ul className="mt-2 flex flex-col gap-2">
            {detail.files.map((file) => (
              <li
                key={file.id}
                className="flex flex-wrap items-center gap-2 rounded-control border border-border p-3 text-sm"
              >
                <FormatBadges formats={[file.format]} />
                <span className="text-muted-foreground">
                  {file.fileStatus === "present"
                    ? t("detail.filePresent")
                    : t("detail.fileMissing")}
                </span>
                <span className="ml-auto text-xs text-muted-foreground tabular-nums">
                  {formatBytes(file.sizeBytes, resolvedLanguage)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
