import { Button, Spinner, buttonVariants } from "@acbooks/ui";
import { ArrowLeft } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router-dom";

import { isAbortError, toApiError, type ApiError } from "../api/client";
import { useAppShell } from "../layout/app-shell-context";
import { MobileNavigationTrigger } from "../layout/PageHeader";
import {
  detailReturnHref,
  isDetailReturnFromHome,
} from "../layout/routes";
import { takeReturnScroll } from "../layout/scroll-restoration";
import { useDocumentTitle } from "../layout/use-document-title";
import { BookDetailHeader } from "./BookDetailHeader";
import { BookDetailTabs } from "./BookDetailTabs";
import { BookFormDialog } from "./BookFormDialog";
import {
  deleteBook,
  deleteBookCover,
  getBook,
  updateBook,
  uploadBookCover,
  type BookDetail,
  type ReadingStatus,
} from "./books-api";
import { DeleteBooksDialog } from "./DeleteBooksDialog";
import { libraryErrorMessage, libraryRequestId } from "./errors";
import { LibraryAlert } from "./LibraryAlert";

export interface BookDetailPageProps {
  bookId: string;
}

export function BookDetailPage({ bookId }: BookDetailPageProps) {
  const { t } = useTranslation("library");
  const { expireSession } = useAppShell();
  const navigate = useNavigate();
  const coverInput = useRef<HTMLInputElement>(null);
  const [detail, setDetail] = useState<BookDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [editing, setEditing] = useState(false);
  const [statusDraft, setStatusDraft] = useState<ReadingStatus | null>(null);
  const [statusSaving, setStatusSaving] = useState(false);
  const [actionError, setActionError] = useState<ApiError | null>(null);
  const [coverBusy, setCoverBusy] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState<ApiError | null>(null);
  useDocumentTitle(detail?.title ?? t("detail.title"));
  const backHref = detailReturnHref();
  const backLabel = isDetailReturnFromHome()
    ? t("detail.backHome")
    : t("detail.backToLibrary");

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
          expireSession();
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
  }, [attempt, bookId, expireSession]);

  useEffect(
    () => () => {
      // Keep the library scroll only when leaving through its return link; StrictMode
      // remounts while still on the detail path and must not consume it.
      const stillOnDetail = window.location.pathname.startsWith(
        "/library/books/",
      );
      const leavingToReturn =
        `${window.location.pathname}${window.location.search}` ===
        detailReturnHref();
      if (!stillOnDetail && !leavingToReturn) {
        takeReturnScroll();
      }
    },
    [],
  );

  const retry = () => {
    setLoading(true);
    setError(null);
    setDetail(null);
    setAttempt((current) => current + 1);
  };

  const changeStatus = async (next: ReadingStatus) => {
    if (detail === null || detail.readingStatus === next || statusSaving) {
      return;
    }
    setStatusDraft(next);
    setStatusSaving(true);
    setActionError(null);
    try {
      const saved = await updateBook(detail.id, { readingStatus: next });
      setDetail(saved);
      setStatusDraft(null);
    } catch (cause) {
      const apiError = toApiError(cause);
      setStatusDraft(null);
      if (apiError.status === 401) {
        expireSession();
        return;
      }
      setActionError(apiError);
    } finally {
      setStatusSaving(false);
    }
  };

  const changeCover = async (file: File) => {
    if (detail === null || coverBusy) {
      return;
    }
    setCoverBusy(true);
    setActionError(null);
    try {
      setDetail(await uploadBookCover(detail.id, file));
    } catch (cause) {
      const apiError = toApiError(cause);
      if (apiError.status === 401) {
        expireSession();
        return;
      }
      setActionError(apiError);
    } finally {
      setCoverBusy(false);
      if (coverInput.current !== null) {
        coverInput.current.value = "";
      }
    }
  };

  const removeCover = async () => {
    if (detail === null || coverBusy) {
      return;
    }
    setCoverBusy(true);
    setActionError(null);
    try {
      setDetail(await deleteBookCover(detail.id));
    } catch (cause) {
      const apiError = toApiError(cause);
      if (apiError.status === 401) {
        expireSession();
        return;
      }
      setActionError(apiError);
    } finally {
      setCoverBusy(false);
    }
  };

  const confirmDelete = async () => {
    if (detail === null || deleteBusy) {
      return;
    }
    setDeleteBusy(true);
    setDeleteError(null);
    try {
      await deleteBook(detail.id);
      navigate("/library", { replace: true });
    } catch (cause) {
      const apiError = toApiError(cause);
      setDeleteBusy(false);
      if (apiError.status === 401) {
        expireSession();
        return;
      }
      setDeleteError(apiError);
    }
  };

  const headerDetail =
    detail !== null && statusDraft !== null
      ? { ...detail, readingStatus: statusDraft }
      : detail;

  const errorMessage =
    error === null ? undefined : libraryErrorMessage(t, error);
  const errorRequestId = error === null ? undefined : libraryRequestId(error);
  const actionErrorMessage =
    actionError === null ? undefined : libraryErrorMessage(t, actionError);
  const actionErrorRequestId =
    actionError === null ? undefined : libraryRequestId(actionError);
  const notFound =
    error !== null &&
    (error.code === "BOOK_NOT_FOUND" || error.status === 404);

  return (
    <div className="@container mx-auto flex min-h-0 w-full max-w-[1200px] flex-col gap-4">
      <div className="flex items-center gap-2">
        <MobileNavigationTrigger className="-ml-1 md:hidden" />
        <nav aria-label={t("detail.breadcrumb")}>
          <Link
            className="inline-flex items-center gap-1.5 rounded-control text-[13px] font-medium text-muted-foreground transition-colors duration-150 ease-out hover:text-accent-text focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none"
            to={backHref}
          >
            <ArrowLeft aria-hidden="true" className="size-4" />
            {backLabel}
          </Link>
        </nav>
      </div>

      {loading ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 py-16 text-sm text-muted-foreground">
          <Spinner label={t("detail.loading")} />
          <span>{t("detail.loading")}</span>
          <h1 className="sr-only">{t("detail.title")}</h1>
        </div>
      ) : notFound ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-2 py-16 text-center">
          <h1 className="text-2xl font-semibold">{t("detail.notFoundTitle")}</h1>
          <p className="max-w-[42ch] text-sm text-muted-foreground">
            {t("detail.notFoundDescription")}
          </p>
          <Link
            className={buttonVariants({
              className: "mt-2",
              size: "lg",
              variant: "soft",
            })}
            to={backHref}
          >
            {backLabel}
          </Link>
        </div>
      ) : errorMessage !== undefined ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 py-16">
          <h1 className="sr-only">{t("detail.title")}</h1>
          <LibraryAlert requestId={errorRequestId}>
            {errorMessage}
          </LibraryAlert>
          <Button onClick={retry} variant="soft">
            {t("errors.retry")}
          </Button>
        </div>
      ) : headerDetail !== null ? (
        <>
          <BookDetailHeader
            coverBusy={coverBusy}
            detail={headerDetail}
            onDelete={() => {
              setDeleteOpen(true);
            }}
            onDeleteCover={() => {
              void removeCover();
            }}
            onEdit={() => {
              setEditing(true);
            }}
            onStatusChange={(next) => {
              void changeStatus(next);
            }}
            onUploadCover={() => {
              coverInput.current?.click();
            }}
            statusSaving={statusSaving}
          />
          <input
            accept="image/png,image/jpeg,image/webp"
            className="hidden"
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file !== undefined) {
                void changeCover(file);
              }
            }}
            ref={coverInput}
            type="file"
          />

          {actionErrorMessage !== undefined ? (
            <LibraryAlert requestId={actionErrorRequestId}>
              {actionErrorMessage}
            </LibraryAlert>
          ) : null}

          <BookDetailTabs detail={detail ?? headerDetail} />
        </>
      ) : null}

      {editing && detail !== null ? (
        <BookFormDialog
          book={detail}
          key={detail.id}
          mode="edit"
          onClose={() => {
            setEditing(false);
          }}
          onSaved={(saved) => {
            setEditing(false);
            setDetail(saved);
          }}
          onSessionExpired={expireSession}
        />
      ) : null}

      {deleteOpen && detail !== null ? (
        <DeleteBooksDialog
          busy={deleteBusy}
          count={1}
          error={
            deleteError === null
              ? undefined
              : libraryErrorMessage(t, deleteError)
          }
          onCancel={() => {
            if (!deleteBusy) {
              setDeleteOpen(false);
              setDeleteError(null);
            }
          }}
          onConfirm={() => {
            void confirmDelete();
          }}
        />
      ) : null}
    </div>
  );
}
