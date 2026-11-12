import {
  Button,
  SegmentedControl,
  Spinner,
  useLanguage,
  type SegmentedControlOption,
} from "@acbooks/ui";
import {
  BookOpen,
  CircleAlert,
  LayoutGrid,
  List,
  Plus,
  RefreshCw,
  Search,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { isAbortError, toApiError, type ApiError } from "../api/client";
import { getCurrentAccount, logout, type Account } from "../auth/auth-api";
import { AppShell } from "../layout/AppShell";
import { BookCard } from "./BookCard";
import { BookDetailDialog } from "./BookDetailDialog";
import { BookFormDialog, type BookFormMode } from "./BookFormDialog";
import { BookList } from "./BookList";
import {
  getBooks,
  type BookDetail,
  type BookListQuery,
  type BookSummary,
} from "./books-api";
import { libraryErrorMessage, libraryRequestId } from "./errors";
import { FilterSelect, type FilterOption } from "./FilterSelect";
import { LibraryAlert } from "./LibraryAlert";
import { LibrarySidebar } from "./LibrarySidebar";
import { LibraryTopbar } from "./LibraryTopbar";
import { Pagination } from "./Pagination";

type TypeFilter = "all" | "digital" | "physical";
type StatusFilter = "all" | "done" | "reading" | "unread";
type SortOrder = "author" | "recent" | "title";
type ViewMode = "grid" | "list";

type DialogState =
  | { mode: "create" }
  | { mode: "detail"; bookId: string }
  | { mode: "edit"; book: BookDetail };

type GuardState =
  | { status: "error" }
  | { status: "loading" }
  | { account: Account; status: "ready" };

const PAGE_SIZE = 12;

export interface LibraryPageProps {
  onLoggedOut?: () => void;
  onSessionExpired?: () => void;
  onUnauthenticated?: () => void;
}

function redirectToLogin(): void {
  window.location.replace("/");
}

function redirectToExpiredLogin(): void {
  window.location.replace("/?expired=1");
}

function redirectHome(): void {
  window.location.assign("/");
}

function option(value: string, label: string): FilterOption {
  return { label, value };
}

interface LibraryContentProps {
  accountName: string;
  onLoggedOut: () => void;
  onSessionExpired: () => void;
}

function LibraryContent({
  accountName,
  onLoggedOut,
  onSessionExpired,
}: LibraryContentProps) {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  const [books, setBooks] = useState<BookSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [offset, setOffset] = useState(0);
  const [query, setQuery] = useState("");
  const [searchTerm, setSearchTerm] = useState("");
  const [type, setType] = useState<TypeFilter>("all");
  const [status, setStatus] = useState<StatusFilter>("all");
  const [sort, setSort] = useState<SortOrder>("recent");
  const [view, setView] = useState<ViewMode>("grid");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [dialog, setDialog] = useState<DialogState | null>(null);
  const [loggingOut, setLoggingOut] = useState(false);
  const [logoutError, setLogoutError] = useState<string | undefined>(
    undefined,
  );
  const searchTimer = useRef<number | undefined>(undefined);
  const composingRef = useRef(false);
  const searchTermRef = useRef("");

  useEffect(
    () => () => {
      if (searchTimer.current !== undefined) {
        window.clearTimeout(searchTimer.current);
      }
    },
    [],
  );

  useEffect(() => {
    const controller = new AbortController();
    const queryParams: BookListQuery = {
      q: searchTerm.trim().length > 0 ? searchTerm.trim() : undefined,
      readingStatus: status === "all" ? undefined : status,
      mediaType: type === "all" ? undefined : type,
      sort: sort === "recent" ? "createdAt" : sort,
      order: sort === "recent" ? "desc" : "asc",
      limit: PAGE_SIZE,
      offset,
    };

    getBooks(queryParams, controller.signal)
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
  }, [offset, onSessionExpired, reloadKey, searchTerm, sort, status, type]);

  const beginRequest = () => {
    setLoading(true);
    setError(null);
  };

  const applySearchTerm = (value: string) => {
    if (value === searchTermRef.current) {
      return;
    }
    searchTermRef.current = value;
    beginRequest();
    setSearchTerm(value);
    setOffset(0);
  };

  const commitSearch = (value: string, immediate = false) => {
    setQuery(value);
    if (composingRef.current) {
      return;
    }
    if (searchTimer.current !== undefined) {
      window.clearTimeout(searchTimer.current);
      searchTimer.current = undefined;
    }
    const run = () => {
      searchTimer.current = undefined;
      applySearchTerm(value);
    };
    if (immediate) {
      run();
    } else {
      searchTimer.current = window.setTimeout(run, 300);
    }
  };

  const changeType = (value: string) => {
    beginRequest();
    setType(value as TypeFilter);
    setOffset(0);
  };

  const changeStatus = (value: string) => {
    beginRequest();
    setStatus(value as StatusFilter);
    setOffset(0);
  };

  const changeSort = (value: string) => {
    beginRequest();
    setSort(value as SortOrder);
    setOffset(0);
  };

  const clearFilters = () => {
    if (searchTimer.current !== undefined) {
      window.clearTimeout(searchTimer.current);
      searchTimer.current = undefined;
    }
    setQuery("");
    searchTermRef.current = "";
    setSearchTerm("");
    setType("all");
    setStatus("all");
    setSort("recent");
    setOffset(0);
    beginRequest();
  };

  const retry = () => {
    beginRequest();
    setReloadKey((current) => current + 1);
  };

  const refreshLibrary = (resetPage: boolean) => {
    beginRequest();
    if (resetPage) {
      setOffset(0);
    }
    setReloadKey((current) => current + 1);
  };

  const handleSaved = (_book: BookDetail, mode: BookFormMode) => {
    setDialog(null);
    refreshLibrary(mode === "create");
  };

  const handleLogout = async () => {
    setLoggingOut(true);
    setLogoutError(undefined);
    try {
      await logout();
      setLoggingOut(false);
      onLoggedOut();
    } catch (cause) {
      const apiError = toApiError(cause);
      setLoggingOut(false);
      if (apiError.status === 401) {
        onSessionExpired();
        return;
      }
      setLogoutError(libraryErrorMessage(t, apiError));
    }
  };

  const typeOptions: readonly FilterOption[] = [
    option("all", t("filters.all")),
    option("physical", t("filters.physical")),
    option("digital", t("filters.digital")),
  ];
  const statusOptions: readonly FilterOption[] = [
    option("all", t("filters.all")),
    option("done", t("filters.done")),
    option("reading", t("filters.reading")),
    option("unread", t("filters.unread")),
  ];
  const sortOptions: readonly FilterOption[] = [
    option("recent", t("filters.recent")),
    option("title", t("filters.title")),
    option("author", t("filters.author")),
  ];
  const viewOptions: readonly SegmentedControlOption<ViewMode>[] = [
    {
      value: "grid",
      label: (
        <>
          <LayoutGrid aria-hidden="true" className="size-4" />
          <span className="sr-only">{t("view.grid")}</span>
        </>
      ),
    },
    {
      value: "list",
      label: (
        <>
          <List aria-hidden="true" className="size-4" />
          <span className="sr-only">{t("view.list")}</span>
        </>
      ),
    },
  ];

  const hasFilters =
    searchTerm.trim().length > 0 || type !== "all" || status !== "all";
  const formattedTotal = new Intl.NumberFormat(resolvedLanguage).format(total);
  const countMessage =
    loading && books.length === 0
      ? t("loading.books")
      : t("page.count", {
          count: total,
          formattedCount: formattedTotal,
        });
  const errorMessage =
    error === null ? undefined : libraryErrorMessage(t, error);
  const errorRequestId = error === null ? undefined : libraryRequestId(error);

  return (
    <AppShell
      header={
        <LibraryTopbar
          accountName={accountName}
          loggingOut={loggingOut}
          logoutError={logoutError}
          onCompositionEnd={(value) => {
            composingRef.current = false;
            commitSearch(value, true);
          }}
          onCompositionStart={() => {
            composingRef.current = true;
          }}
          onLogout={() => {
            void handleLogout();
          }}
          onSearchChange={(value) => {
            commitSearch(value);
          }}
          query={query}
        />
      }
      sidebar={<LibrarySidebar />}
    >
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-[28px] leading-[1.25] font-semibold">
            {t("page.title")}
          </h1>
          <p className="mt-1 flex items-center gap-2 text-sm text-muted-foreground">
            <span>{countMessage}</span>
            {loading && books.length > 0 ? (
              <Spinner label={t("loading.updating")} />
            ) : null}
          </p>
        </div>
        <Button
          onClick={() => {
            setDialog({ mode: "create" });
          }}
          variant="soft"
        >
          <Plus aria-hidden="true" />
          {t("actions.addBook")}
        </Button>
      </div>

      <div className="flex items-center justify-between gap-3 max-md:gap-2">
        <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2 [scrollbar-width:none] max-md:flex-nowrap max-md:overflow-x-auto max-md:pb-1 [&::-webkit-scrollbar]:hidden">
          <FilterSelect
            label={t("filters.type")}
            onChange={changeType}
            options={typeOptions}
            value={type}
          />
          <FilterSelect
            label={t("filters.status")}
            onChange={changeStatus}
            options={statusOptions}
            value={status}
          />
          <FilterSelect
            disabled
            label={t("filters.shelf")}
            onChange={() => {}}
            options={[option("all", t("filters.all"))]}
            value="all"
          />
          <FilterSelect
            label={t("filters.sort")}
            onChange={changeSort}
            options={sortOptions}
            value={sort}
          />
        </div>

        <SegmentedControl
          ariaLabel={t("view.label")}
          onValueChange={setView}
          options={viewOptions}
          value={view}
        />
      </div>

      {errorMessage !== undefined && books.length > 0 ? (
        <div className="flex flex-col gap-2">
          <LibraryAlert requestId={errorRequestId}>
            {errorMessage}
          </LibraryAlert>
          <div>
            <Button onClick={retry} size="sm" variant="outline">
              <RefreshCw aria-hidden="true" />
              {t("errors.retry")}
            </Button>
          </div>
        </div>
      ) : null}

      {loading && books.length === 0 ? (
        <div className="flex flex-1 items-center justify-center gap-3 py-16 text-sm text-muted-foreground">
          <Spinner label={t("loading.books")} />
          <span>{t("loading.books")}</span>
        </div>
      ) : errorMessage !== undefined && books.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 py-16">
          <LibraryAlert requestId={errorRequestId}>
            {errorMessage}
          </LibraryAlert>
          <Button onClick={retry} variant="soft">
            <RefreshCw aria-hidden="true" />
            {t("errors.retry")}
          </Button>
        </div>
      ) : books.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-2 py-16 text-center">
          {hasFilters ? (
            <Search
              aria-hidden="true"
              className="size-8 text-muted-foreground"
              strokeWidth={2}
            />
          ) : (
            <BookOpen
              aria-hidden="true"
              className="size-8 text-muted-foreground"
              strokeWidth={2}
            />
          )}
          <h2 className="text-xl font-semibold">
            {hasFilters ? t("empty.title") : t("empty.libraryTitle")}
          </h2>
          <p className="text-sm text-muted-foreground">
            {hasFilters
              ? t("empty.description")
              : t("empty.libraryDescription")}
          </p>
          {hasFilters ? (
            <Button className="mt-2" onClick={clearFilters} variant="outline">
              {t("empty.clear")}
            </Button>
          ) : (
            <Button
              className="mt-2"
              onClick={() => {
                setDialog({ mode: "create" });
              }}
              variant="soft"
            >
              <Plus aria-hidden="true" />
              {t("empty.addBook")}
            </Button>
          )}
        </div>
      ) : view === "grid" ? (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(152px,1fr))] gap-6 max-md:grid-cols-2 max-md:gap-x-4 max-md:gap-y-6">
          {books.map((book) => (
            <BookCard
              key={book.id}
              book={book}
              onOpen={(bookId) => {
                setDialog({ mode: "detail", bookId });
              }}
            />
          ))}
        </div>
      ) : (
        <BookList
          books={books}
          onOpen={(bookId) => {
            setDialog({ mode: "detail", bookId });
          }}
        />
      )}

      <Pagination
        disabled={loading}
        limit={PAGE_SIZE}
        offset={offset}
        onOffsetChange={(nextOffset) => {
          beginRequest();
          setOffset(nextOffset);
        }}
        total={total}
      />

      {dialog?.mode === "create" ? (
        <BookFormDialog
          mode="create"
          onClose={() => {
            setDialog(null);
          }}
          onSaved={handleSaved}
          onSessionExpired={onSessionExpired}
        />
      ) : null}

      {dialog?.mode === "edit" ? (
        <BookFormDialog
          key={dialog.book.id}
          book={dialog.book}
          mode="edit"
          onClose={() => {
            setDialog(null);
          }}
          onSaved={handleSaved}
          onSessionExpired={onSessionExpired}
        />
      ) : null}

      {dialog?.mode === "detail" ? (
        <BookDetailDialog
          key={dialog.bookId}
          bookId={dialog.bookId}
          onClose={() => {
            setDialog(null);
          }}
          onEdit={(book) => {
            setDialog({ mode: "edit", book });
          }}
          onSessionExpired={onSessionExpired}
        />
      ) : null}
    </AppShell>
  );
}

export function LibraryPage({
  onLoggedOut = redirectHome,
  onSessionExpired = redirectToExpiredLogin,
  onUnauthenticated = redirectToLogin,
}: LibraryPageProps) {
  const { t } = useTranslation("library");
  const [guard, setGuard] = useState<GuardState>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      try {
        const account = await getCurrentAccount();
        if (cancelled) {
          return;
        }
        if (account === null) {
          onUnauthenticated();
          return;
        }
        setGuard({ account, status: "ready" });
      } catch {
        if (!cancelled) {
          setGuard({ status: "error" });
        }
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [attempt, onUnauthenticated]);

  if (guard.status === "loading") {
    return (
      <div className="flex h-dvh items-center justify-center bg-frame">
        <div className="flex items-center gap-3 text-sm text-muted-foreground">
          <Spinner label={t("guard.loading")} />
          <span>{t("guard.loading")}</span>
        </div>
      </div>
    );
  }

  if (guard.status === "error") {
    return (
      <div className="flex h-dvh items-center justify-center bg-frame p-4">
        <div className="w-full max-w-[400px] rounded-panel border border-border bg-background p-6 text-center">
          <CircleAlert
            aria-hidden="true"
            className="mx-auto size-8 text-destructive-text"
            strokeWidth={2}
          />
          <h1 className="mt-3 text-xl font-semibold">
            {t("guard.errorTitle")}
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {t("guard.errorDescription")}
          </p>
          <Button
            className="mt-5 w-full"
            onClick={() => {
              setGuard({ status: "loading" });
              setAttempt((current) => current + 1);
            }}
            size="lg"
            variant="soft"
          >
            <RefreshCw aria-hidden="true" />
            {t("guard.retry")}
          </Button>
        </div>
      </div>
    );
  }

  return (
    <LibraryContent
      accountName={guard.account.username}
      onLoggedOut={onLoggedOut}
      onSessionExpired={onSessionExpired}
    />
  );
}
