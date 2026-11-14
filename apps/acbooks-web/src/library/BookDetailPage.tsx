import { Button, Spinner, buttonVariants } from "@acbooks/ui";
import { ArrowLeft } from "lucide-react";
import { lazy, Suspense, useEffect, useRef, useState } from "react";
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
import { PageContainer } from "../layout/PageContainer";
import { useDocumentTitle } from "../layout/use-document-title";
import { BookDetailHeader } from "./BookDetailHeader";
import { BookInfoPanel } from "./BookInfoPanel";
import { MetadataDialog } from "./MetadataDialog";
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
import { deleteBookCopy, type BookCopy } from "./copies-api";
import { CopyDialog } from "./CopyDialog";
import { DeleteBooksDialog } from "./DeleteBooksDialog";
import { libraryErrorMessage, libraryRequestId } from "./errors";
import { LibraryAlert } from "./LibraryAlert";
import { Modal } from "./Modal";

const ReadingStatsPanel = lazy(() => import("../stats/ReadingStatsPanel"));

export interface BookDetailPageProps {
  bookId: string;
}

export function BookDetailPage({ bookId }: BookDetailPageProps) {
  const { t } = useTranslation("library");
  const { t: tStats } = useTranslation("stats");
  const [statsOpen, setStatsOpen] = useState(false);
  const { expireSession } = useAppShell();
  const navigate = useNavigate();
  const coverInput = useRef<HTMLInputElement>(null);
  const activeBookId = useRef(bookId);
  const [detail, setDetail] = useState<BookDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [metadataOpen, setMetadataOpen] = useState(false);
  const [editing, setEditing] = useState(false);
  const [statusDraft, setStatusDraft] = useState<{
    bookId: string;
    status: ReadingStatus;
  } | null>(null);
  const [statusSaving, setStatusSaving] = useState(false);
  const [actionError, setActionError] = useState<ApiError | null>(null);
  const [coverBusy, setCoverBusy] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState<ApiError | null>(null);
  const [copyDialog, setCopyDialog] = useState<{ copy?: BookCopy } | null>(null);
  const [copyDeleteTarget, setCopyDeleteTarget] = useState<BookCopy | null>(
    null,
  );
  const [copyDeleteBusy, setCopyDeleteBusy] = useState(false);
  const [copyDeleteError, setCopyDeleteError] = useState<ApiError | null>(null);
  useDocumentTitle(detail?.title ?? t("detail.title"));
  const backHref = detailReturnHref();
  const backLabel = isDetailReturnFromHome()
    ? t("detail.backHome")
    : t("detail.backToLibrary");

  useEffect(() => {
    activeBookId.current = bookId;
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

  // Ignore save responses for a book the user has already navigated away from.
  const applyDetail = (saved: BookDetail) => {
    if (saved.id === activeBookId.current) {
      setDetail(saved);
    }
  };

  const refreshDetail = () => {
    getBook(bookId)
      .then((book) => {
        applyDetail(book);
      })
      .catch((cause) => {
        const apiError = toApiError(cause);
        if (apiError.status === 401) {
          expireSession();
          return;
        }
        setActionError(apiError);
      });
  };

  const confirmDeleteCopy = async () => {
    if (detail === null || copyDeleteTarget === null || copyDeleteBusy) {
      return;
    }
    setCopyDeleteBusy(true);
    setCopyDeleteError(null);
    try {
      await deleteBookCopy(detail.id, copyDeleteTarget.id);
      setCopyDeleteBusy(false);
      setCopyDeleteTarget(null);
      refreshDetail();
    } catch (cause) {
      const apiError = toApiError(cause);
      setCopyDeleteBusy(false);
      if (apiError.status === 401) {
        expireSession();
        return;
      }
      setCopyDeleteError(apiError);
    }
  };

  const changeStatus = async (next: ReadingStatus) => {
    if (
      detail === null ||
      detail.readingStatus === next ||
      statusSaving ||
      coverBusy
    ) {
      return;
    }
    setStatusDraft({ bookId: detail.id, status: next });
    setStatusSaving(true);
    setActionError(null);
    try {
      const saved = await updateBook(detail.id, { readingStatus: next });
      applyDetail(saved);
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
    if (detail === null || coverBusy || statusSaving) {
      return;
    }
    setCoverBusy(true);
    setActionError(null);
    try {
      applyDetail(await uploadBookCover(detail.id, file));
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
    if (detail === null || coverBusy || statusSaving) {
      return;
    }
    setCoverBusy(true);
    setActionError(null);
    try {
      applyDetail(await deleteBookCover(detail.id));
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
      navigate(backHref, { replace: true });
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
    detail !== null &&
    statusDraft !== null &&
    statusDraft.bookId === detail.id
      ? { ...detail, readingStatus: statusDraft.status }
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
    <PageContainer className="@container min-h-0">
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
            onMetadata={() => setMetadataOpen(true)}
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

          <div className="border-t border-border pt-4">
            <BookInfoPanel
              detail={detail ?? headerDetail}
              onAddCopy={() => {
                setCopyDialog({});
              }}
              onDeleteCopy={(copy) => {
                setCopyDeleteError(null);
                setCopyDeleteTarget(copy);
              }}
              onEditCopy={(copy) => {
                setCopyDialog({ copy });
              }}
              onFilesChanged={refreshDetail}
              onSessionExpired={expireSession}
            />
          </div>
          {detail !== null && (
            <section className="border-t border-border pt-4">
              <Button
                aria-expanded={statsOpen}
                onClick={() => setStatsOpen(open => !open)}
                variant="outline"
              >
                {tStats("title")}
              </Button>
              {statsOpen && (
                <div className="mt-4">
                  <Suspense fallback={<Spinner />}>
                    <ReadingStatsPanel
                      bookId={bookId}
                      revision={JSON.stringify([detail.updatedAt, detail.files.map(file => file.id)])}
                    />
                  </Suspense>
                </div>
              )}
            </section>
          )}
        </>
      ) : null}

      {metadataOpen && detail !== null ? (
        <MetadataDialog bookId={detail.id} isbn={detail.isbn} title={detail.title}
          onApplied={refreshDetail} onClose={() => setMetadataOpen(false)} />
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
            applyDetail(saved);
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

      {copyDialog !== null && detail !== null ? (
        <CopyDialog
          bookId={detail.id}
          copy={copyDialog.copy}
          onClose={() => {
            setCopyDialog(null);
          }}
          onSaved={() => {
            setCopyDialog(null);
            refreshDetail();
          }}
          onSessionExpired={expireSession}
        />
      ) : null}

      {copyDeleteTarget !== null ? (
        <Modal
          describedBy="delete-copy-description"
          labelledBy="delete-copy-title"
          onClose={
            copyDeleteBusy
              ? () => undefined
              : () => {
                  setCopyDeleteTarget(null);
                }
          }
          size="confirm"
        >
          <div className="p-5">
            <h2 className="text-lg font-semibold" id="delete-copy-title">
              {t("copy.deleteTitle")}
            </h2>
            <p
              className="mt-2 text-sm text-muted-foreground"
              id="delete-copy-description"
            >
              {t("copy.deleteDescription")}
            </p>
            {copyDeleteError !== null ? (
              <p className="mt-3 text-sm text-destructive-text" role="alert">
                {libraryErrorMessage(t, copyDeleteError)}
              </p>
            ) : null}
            <div className="mt-6 flex flex-wrap justify-end gap-2">
              <Button
                disabled={copyDeleteBusy}
                onClick={() => {
                  setCopyDeleteTarget(null);
                }}
                variant="ghost"
              >
                {t("form.cancel")}
              </Button>
              <Button
                disabled={copyDeleteBusy}
                onClick={() => {
                  void confirmDeleteCopy();
                }}
                variant="danger-confirm"
              >
                {t("copy.deleteConfirm")}
              </Button>
            </div>
          </div>
        </Modal>
      ) : null}
    </PageContainer>
  );
}
