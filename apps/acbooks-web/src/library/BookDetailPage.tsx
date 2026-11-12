import { Button, Spinner, buttonVariants } from "@acbooks/ui";
import { ArrowLeft, RefreshCw } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { isAbortError, toApiError, type ApiError } from "../api/client";
import type { Account } from "../auth/auth-api";
import { AppShell } from "../layout/AppShell";
import { AccountGuard } from "./account-guard";
import { BookDetailHeader } from "./BookDetailHeader";
import { BookDetailTabs } from "./BookDetailTabs";
import { BookFormDialog } from "./BookFormDialog";
import {
  getBook,
  updateBook,
  type BookDetail,
  type ReadingStatus,
} from "./books-api";
import { libraryErrorMessage, libraryRequestId } from "./errors";
import { LibraryAlert } from "./LibraryAlert";
import { LibrarySidebar } from "./LibrarySidebar";
import { LibraryTopbar } from "./LibraryTopbar";
import {
  libraryHref,
  redirectHome,
  redirectToExpiredLogin,
  redirectToLogin,
} from "./navigation";
import { useLibraryLogout } from "./use-library-logout";

export interface BookDetailPageProps {
  bookId: string;
  onLoggedOut?: () => void;
  onSessionExpired?: () => void;
  onUnauthenticated?: () => void;
}

export function BookDetailPage({
  bookId,
  onLoggedOut = redirectHome,
  onSessionExpired = redirectToExpiredLogin,
  onUnauthenticated = redirectToLogin,
}: BookDetailPageProps) {
  return (
    <AccountGuard onUnauthenticated={onUnauthenticated}>
      {(account: Account) => (
        <BookDetailContent
          accountName={account.username}
          bookId={bookId}
          key={bookId}
          onLoggedOut={onLoggedOut}
          onSessionExpired={onSessionExpired}
        />
      )}
    </AccountGuard>
  );
}

interface BookDetailContentProps {
  accountName: string;
  bookId: string;
  onLoggedOut: () => void;
  onSessionExpired: () => void;
}

function BookDetailContent({
  accountName,
  bookId,
  onLoggedOut,
  onSessionExpired,
}: BookDetailContentProps) {
  const { t } = useTranslation("library");
  const [detail, setDetail] = useState<BookDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [editing, setEditing] = useState(false);
  const [statusDraft, setStatusDraft] = useState<ReadingStatus | null>(null);
  const [statusSaving, setStatusSaving] = useState(false);
  const [actionError, setActionError] = useState<ApiError | null>(null);
  const [query, setQuery] = useState("");
  const composingRef = useRef(false);
  const { handleLogout, loggingOut, logoutError } = useLibraryLogout({
    onLoggedOut,
    onSessionExpired,
  });

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

  const retry = () => {
    setLoading(true);
    setError(null);
    setDetail(null);
    setAttempt((current) => current + 1);
  };

  const changeStatus = async (next: ReadingStatus) => {
    if (
      detail === null ||
      detail.readingStatus === next ||
      statusSaving
    ) {
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
        onSessionExpired();
        return;
      }
      setActionError(apiError);
    } finally {
      setStatusSaving(false);
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
    <AppShell
      header={
        <LibraryTopbar
          accountName={accountName}
          loggingOut={loggingOut}
          logoutError={logoutError}
          onCompositionEnd={(value) => {
            composingRef.current = false;
            setQuery(value);
          }}
          onCompositionStart={() => {
            composingRef.current = true;
          }}
          onLogout={() => {
            void handleLogout();
          }}
          onSearchChange={setQuery}
          onSearchSubmit={() => {
            if (composingRef.current) {
              return;
            }
            window.location.assign(
              query.trim().length > 0 ? libraryHref(query) : "/library?q=",
            );
          }}
          query={query}
        />
      }
      sidebar={<LibrarySidebar />}
    >
      <div className="@container flex min-h-0 flex-col gap-6">
        <nav aria-label={t("detail.breadcrumb")}>
          <a
            className="inline-flex items-center gap-1.5 rounded-control text-[13px] font-medium text-muted-foreground transition-colors duration-150 ease-out hover:text-accent-text focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none"
            href={libraryHref()}
          >
            <ArrowLeft aria-hidden="true" className="size-4" />
            {t("detail.backToLibrary")}
          </a>
        </nav>

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
            <a
              className={buttonVariants({
                className: "mt-2",
                size: "lg",
                variant: "soft",
              })}
              href={libraryHref()}
            >
              {t("detail.backToLibrary")}
            </a>
          </div>
        ) : errorMessage !== undefined ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-16">
            <h1 className="sr-only">{t("detail.title")}</h1>
            <LibraryAlert requestId={errorRequestId}>
              {errorMessage}
            </LibraryAlert>
            <Button onClick={retry} variant="soft">
              <RefreshCw aria-hidden="true" />
              {t("errors.retry")}
            </Button>
          </div>
        ) : headerDetail !== null ? (
          <>
            <BookDetailHeader
              detail={headerDetail}
              onEdit={() => {
                setEditing(true);
              }}
              onStatusChange={(next) => {
                void changeStatus(next);
              }}
              statusSaving={statusSaving}
            />

            {actionErrorMessage !== undefined ? (
              <LibraryAlert requestId={actionErrorRequestId}>
                {actionErrorMessage}
              </LibraryAlert>
            ) : null}

            <BookDetailTabs detail={detail ?? headerDetail} />
          </>
        ) : null}
      </div>

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
          onSessionExpired={onSessionExpired}
        />
      ) : null}
    </AppShell>
  );
}
