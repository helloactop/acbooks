import { Button, Spinner } from "@acbooks/ui";
import { ArrowRight, BookOpen } from "lucide-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { isAbortError, toApiError, type ApiError } from "../api/client";
import { BookCard } from "../library/BookCard";
import { BookFormDialog } from "../library/BookFormDialog";
import { getBooks, type BookSummary } from "../library/books-api";
import { LibraryAlert } from "../library/LibraryAlert";
import {
  libraryErrorMessage,
  libraryRequestId,
} from "../library/errors";
import { useAppShell } from "../layout/app-shell-context";
import { PageHeader } from "../layout/PageHeader";
import { libraryMediaHref, rememberDetailReturn } from "../layout/routes";
import { PageContainer } from "../layout/PageContainer";
import { useDocumentTitle } from "../layout/use-document-title";

const RECENT_LIMIT = 12;

export function DashboardPage() {
  const { t } = useTranslation("dashboard");
  const { t: tLibrary } = useTranslation("library");
  useDocumentTitle(t("title"));
  const { expireSession } = useAppShell();
  const [books, setBooks] = useState<BookSummary[]>([]);
  const [total, setTotal] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    getBooks(
      {
        limit: RECENT_LIMIT,
        offset: 0,
        order: "desc",
        sort: "createdAt",
      },
      controller.signal,
    )
      .then((page) => {
        setBooks(page.items);
        setTotal(page.total);
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
  }, [expireSession, reloadKey]);

  const reload = () => {
    setLoading(true);
    setError(null);
    setReloadKey((current) => current + 1);
  };

  const errorMessage =
    error === null ? undefined : libraryErrorMessage(tLibrary, error);
  const requestId = error === null ? undefined : libraryRequestId(error);

  return (
    <PageContainer>
      <PageHeader
        actions={
          <Button
            onClick={() => {
              setCreateOpen(true);
            }}
            variant="soft"
          >
            {t("addBook")}
          </Button>
        }
        description={
          loading
            ? t("loading")
            : total === null
              ? undefined
              : t("description", { count: total })
        }
        title={t("title")}
      />

      {error !== null && books.length > 0 ? (
        <div className="flex flex-col gap-2">
          <LibraryAlert requestId={requestId}>{errorMessage}</LibraryAlert>
          <div>
            <Button onClick={reload} size="sm" variant="outline">
              {t("retry")}
            </Button>
          </div>
        </div>
      ) : null}

      {loading && books.length === 0 ? (
        <div className="flex flex-1 items-center justify-center gap-3 py-16 text-sm text-muted-foreground">
          <Spinner label={t("loading")} />
          <span>{t("loading")}</span>
        </div>
      ) : error !== null && books.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 py-16">
          <LibraryAlert requestId={requestId}>{errorMessage}</LibraryAlert>
          <Button onClick={reload} variant="soft">
            {t("retry")}
          </Button>
        </div>
      ) : books.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-2 py-16 text-center">
          <BookOpen
            aria-hidden="true"
            className="size-8 text-muted-foreground"
            strokeWidth={2}
          />
          <h2 className="text-base font-semibold">{t("emptyTitle")}</h2>
          <p className="text-sm text-muted-foreground">
            {t("emptyDescription")}
          </p>
          <Button
            className="mt-2"
            onClick={() => {
              setCreateOpen(true);
            }}
            variant="soft"
          >
            {t("addBook")}
          </Button>
        </div>
      ) : (
        <section className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center gap-3">
            <h2 className="text-base font-semibold">{t("recentTitle")}</h2>
            <Link
              className="inline-flex items-center gap-1 text-[13px] font-medium text-accent-text hover:underline focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
              to={libraryMediaHref("all")}
            >
              {t("viewAll")}
              <ArrowRight aria-hidden="true" className="size-3.5" />
            </Link>
          </div>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-[repeat(auto-fill,140px)] sm:gap-4">
            {books.map((book) => (
              <BookCard
                book={book}
                key={book.id}
                onOpen={() => {
                  rememberDetailReturn("/home");
                }}
              />
            ))}
          </div>
        </section>
      )}

      {createOpen ? (
        <BookFormDialog
          mode="create"
          onClose={() => {
            setCreateOpen(false);
          }}
          onSaved={() => {
            setCreateOpen(false);
            reload();
          }}
          onSessionExpired={expireSession}
        />
      ) : null}
    </PageContainer>
  );
}
