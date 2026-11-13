import {
  Button,
  SegmentedControl,
  Select,
  Spinner,
  useLanguage,
} from "@acbooks/ui";
import { BookOpen, Search, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigationType } from "react-router-dom";

import { isAbortError, toApiError, type ApiError } from "../api/client";
import { useAppShell } from "../layout/app-shell-context";
import { PageHeader } from "../layout/PageHeader";
import { rememberDetailReturn } from "../layout/routes";
import {
  readScrollPosition,
  rememberReturnScroll,
  takeReturnScroll,
} from "../layout/scroll-restoration";
import { useDocumentTitle } from "../layout/use-document-title";
import { BookCard } from "./BookCard";
import { BookFormDialog } from "./BookFormDialog";
import { BookList } from "./BookList";
import {
  getBooks,
  type BookListQuery,
  type BookSummary,
} from "./books-api";
import { ActiveFilterSummary } from "./ActiveFilterSummary";
import { libraryErrorMessage, libraryRequestId } from "./errors";
import { LibraryAlert } from "./LibraryAlert";
import { LibraryFilterToolbar } from "./LibraryFilterToolbar";
import {
  coverSizePx,
  type CoverSize,
  type SortOrder,
  type StatusFilter,
  type TypeFilter,
  type ViewMode,
} from "./library-view-state";
import { Pagination } from "./Pagination";
import { SmartFilterBuilder } from "./SmartFilterBuilder";
import { useBookFilterSchema } from "./use-book-filter-schema";
import { useLibraryFilters } from "./use-library-filters";
import { useLibraryUrlState } from "./use-library-url-state";
import { useLibraryViewPreferences } from "./use-library-view-preferences";

const PAGE_SIZE = 12;

export function LibraryPage() {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  const { expireSession, mainRef } = useAppShell();
  const location = useLocation();
  const navigationType = useNavigationType();
  const preferences = useLibraryViewPreferences();
  const {
    clearAll,
    clearSearch: clearSearchUrl,
    externalRevision,
    filters: appliedRules,
    hasViewParam,
    offset,
    searchTerm,
    setFilters,
    setOffset,
    setSort,
    setStatus,
    setType,
    setView,
    sort,
    status,
    type,
    view: urlView,
  } = useLibraryUrlState();
  const view = hasViewParam ? urlView : preferences.defaultView;
  const [books, setBooks] = useState<BookSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const scrollRestored = useRef(false);
  const filterSchema = useBookFilterSchema(true, expireSession);
  const fields = filterSchema.schema?.fields ?? [];
  const {
    appliedFilters,
    changeRules,
    removeCondition,
    resetDraft,
    rules,
    summary,
  } = useLibraryFilters({
    appliedRules,
    externalRevision,
    fields,
    onApply: (next) => {
      setLoading(true);
      setError(null);
      setFilters(next);
    },
  });
  const waitingForSchema =
    appliedRules.conditions.length > 0 &&
    fields.length === 0 &&
    filterSchema.error === null;

  useEffect(() => {
    const handlePageShow = (event: PageTransitionEvent) => {
      if (event.persisted) {
        setLoading(true);
        setError(null);
        setReloadKey((current) => current + 1);
      }
    };
    window.addEventListener("pageshow", handlePageShow);
    return () => {
      window.removeEventListener("pageshow", handlePageShow);
    };
  }, []);

  useEffect(() => {
    if (loading || scrollRestored.current) {
      return;
    }
    scrollRestored.current = true;
    const returnScroll = takeReturnScroll();
    // Browser back/forward restores the entry's own position; waiting for the loaded
    // list avoids clamping it against the loading placeholder.
    const entryScroll =
      navigationType === "POP" ? readScrollPosition(location) : 0;
    const scrollTop = Math.max(returnScroll, entryScroll);
    if (scrollTop <= 0) {
      return;
    }
    const frame = window.requestAnimationFrame(() => {
      mainRef.current?.scrollTo({ top: scrollTop });
    });
    return () => {
      window.cancelAnimationFrame(frame);
    };
  }, [loading, location, mainRef, navigationType]);

  useEffect(() => {
    if (waitingForSchema) {
      return;
    }
    // The request effect owns loading for URL-driven changes (back/forward, search).
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    setError(null);
    const controller = new AbortController();
    let corrected = false;
    const query: BookListQuery = {
      q: searchTerm.length > 0 ? searchTerm : undefined,
      readingStatus: status === "all" ? undefined : status,
      mediaType: type === "all" ? undefined : type,
      filters: appliedFilters.length > 0 ? appliedFilters : undefined,
      sort: sort === "recent" ? "createdAt" : sort,
      order: sort === "recent" ? "desc" : "asc",
      limit: PAGE_SIZE,
      offset: offset,
    };

    getBooks(query, controller.signal)
      .then((page) => {
        if (controller.signal.aborted) {
          return;
        }
        const pageSize = page.limit > 0 ? page.limit : PAGE_SIZE;
        const maxOffset =
          page.total > 0
            ? Math.floor((page.total - 1) / pageSize) * pageSize
            : 0;
        if (offset > maxOffset) {
          corrected = true;
          setTotal(page.total);
          setError(null);
          setOffset(maxOffset);
          return;
        }
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
        if (!controller.signal.aborted && !corrected) {
          setLoading(false);
        }
      });

    return () => controller.abort();
  }, [
    appliedFilters,
    expireSession,
    reloadKey,
    offset,
    searchTerm,
    setOffset,
    sort,
    status,
    type,
    waitingForSchema,
  ]);

  const changeStatus = (value: StatusFilter) => {
    if (value === status) {
      return;
    }
    setLoading(true);
    setError(null);
    setStatus(value);
  };

  const changeType = (value: TypeFilter) => {
    if (value === type) {
      return;
    }
    setLoading(true);
    setError(null);
    setType(value);
  };

  const changeSort = (value: SortOrder) => {
    if (value === sort) {
      return;
    }
    setLoading(true);
    setError(null);
    setSort(value);
  };

  const changeView = (value: ViewMode) => {
    if (value === view) {
      return;
    }
    preferences.saveView(value);
    setView(value);
  };

  const changeCoverSize = (value: CoverSize) => {
    if (value !== preferences.coverSize) {
      preferences.setCoverSize(value);
    }
  };

  const clearSearch = () => {
    if (searchTerm.length === 0) {
      return;
    }
    setLoading(true);
    setError(null);
    clearSearchUrl();
  };

  const clearAllFilters = () => {
    resetDraft();
    const queryChanged =
      appliedFilters.length > 0 ||
      searchTerm.length > 0 ||
      status !== "all" ||
      type !== "all" ||
      offset !== 0;
    if (queryChanged) {
      setLoading(true);
      setError(null);
    }
    clearAll();
  };

  const retry = () => {
    setLoading(true);
    setError(null);
    setReloadKey((current) => current + 1);
  };

  const refreshLibrary = () => {
    setLoading(true);
    setError(null);
    setOffset(0);
    setReloadKey((current) => current + 1);
  };

  const handleCreated = () => {
    setCreateOpen(false);
    refreshLibrary();
  };

  const openBook = () => {
    rememberReturnScroll(mainRef.current?.scrollTop ?? 0);
    rememberDetailReturn();
  };

  const changePage = (nextOffset: number) => {
    if (nextOffset === offset) {
      return;
    }
    setLoading(true);
    setError(null);
    setOffset(nextOffset);
    const main = mainRef.current;
    if (main !== null && typeof main.scrollTo === "function") {
      main.scrollTo({ top: 0 });
    }
  };

  const activeCount = summary.validCount;
  const hasExternalFilters =
    searchTerm.length > 0 ||
    status !== "all" ||
    type !== "all";
  const hasFilters = hasExternalFilters || activeCount > 0;
  useDocumentTitle(t("page.libraryTitle"));
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
  const schemaLoading =
    filterSchema.schema === null && filterSchema.error === null;
  const schemaError =
    filterSchema.error === null ? undefined : t("filters.schemaError");
  const maxConditions = filterSchema.schema?.maxConditions ?? 30;

  return (
    <>
      <PageHeader
        actions={
          <Button
            onClick={() => {
              setCreateOpen(true);
            }}
            variant="soft"
          >
            {t("actions.addBook")}
          </Button>
        }
        description={
          <span className="flex flex-wrap items-center gap-2">
            <span>{countMessage}</span>
            {loading && books.length > 0 ? (
              <Spinner label={t("loading.updating")} />
            ) : null}
            {searchTerm.length > 0 ? (
              <button
                aria-label={t("filters.clearSearch")}
                className="inline-flex min-h-7 max-w-56 min-w-0 items-center gap-1 rounded-control border border-border bg-surface px-1.5 text-[12px] text-foreground transition-colors duration-150 ease-out hover:bg-surface-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none max-md:min-h-11"
                onClick={clearSearch}
                type="button"
              >
                <Search
                  aria-hidden="true"
                  className="size-3.5 shrink-0 text-muted-foreground"
                />
                <span className="min-w-0 truncate">{searchTerm}</span>
                <X
                  aria-hidden="true"
                  className="size-3.5 shrink-0 text-muted-foreground"
                />
              </button>
            ) : null}
          </span>
        }
        title={t("page.libraryTitle")}
      />

      <div className="flex flex-col gap-2 lg:flex-row lg:items-center">
        <SegmentedControl
          ariaLabel={t("filters.type")}
          className="w-full [&>button]:flex-1 md:w-auto md:self-start md:[&>button]:flex-none"
          onValueChange={(value) => {
            changeType(value as TypeFilter);
          }}
          options={[
            { label: t("page.title"), value: "all" },
            { label: t("page.physicalTitle"), value: "physical" },
            { label: t("page.digitalTitle"), value: "digital" },
          ]}
          value={type}
          variant="boxed"
        />

        <div className="flex flex-wrap items-center gap-2 lg:ml-auto lg:flex-nowrap">
          <Select
            ariaLabel={t("filters.status")}
            containerClassName="min-w-0 flex-1 max-md:min-w-[120px] md:w-[100px] md:flex-none"
            onValueChange={(value) => {
              changeStatus(value as StatusFilter);
            }}
            options={[
              { label: t("filters.all"), value: "all" },
              { label: t("status.unread"), value: "unread" },
              { label: t("status.reading"), value: "reading" },
              { label: t("status.done"), value: "done" },
            ]}
            value={status}
          />
          <Select
            ariaLabel={t("filters.sort")}
            containerClassName="min-w-0 flex-1 max-md:min-w-[120px] md:w-[152px] md:flex-none"
            onValueChange={(value) => {
              changeSort(value as SortOrder);
            }}
            options={[
              { label: t("filters.recent"), value: "recent" },
              { label: t("filters.title"), value: "title" },
              { label: t("filters.author"), value: "author" },
            ]}
            value={sort}
          />
          <div className="ml-auto shrink-0 max-md:ml-0 max-md:w-full">
            <LibraryFilterToolbar
              activeCount={activeCount}
              canClear={hasFilters}
              coverSize={preferences.coverSize}
              filtersOpen={filtersOpen}
              onClearAll={clearAllFilters}
              onCoverSizeChange={changeCoverSize}
              onRefresh={refreshLibrary}
              onToggleFilters={() => {
                setFiltersOpen((current) => !current);
              }}
              onViewChange={changeView}
              view={view}
            />
          </div>
        </div>
      </div>

      <div className="flex flex-col gap-2">
        {filtersOpen ? (
          <SmartFilterBuilder
            error={schemaError}
            fields={fields}
            hasExternalFilters={hasExternalFilters}
            loading={schemaLoading}
            maxConditions={maxConditions}
            onChange={changeRules}
            onClearAll={clearAllFilters}
            onRetry={filterSchema.retry}
            rules={rules}
          />
        ) : summary.total > 0 ? (
          <ActiveFilterSummary
            combinator={rules.combinator}
            fields={fields}
            onOpenFilters={() => {
              setFiltersOpen(true);
            }}
            onRemove={removeCondition}
            summary={summary}
          />
        ) : null}
      </div>

      {errorMessage !== undefined && books.length > 0 ? (
        <div className="flex flex-col gap-2">
          <LibraryAlert requestId={errorRequestId}>
            {errorMessage}
          </LibraryAlert>
          <div>
            <Button onClick={retry} size="sm" variant="outline">
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
            <Button
              className="mt-2"
              onClick={clearAllFilters}
              variant="outline"
            >
              {t("empty.clear")}
            </Button>
          ) : (
            <Button
              className="mt-2"
              onClick={() => {
                setCreateOpen(true);
              }}
              variant="soft"
            >
              {t("empty.addBook")}
            </Button>
          )}
        </div>
      ) : view === "grid" ? (
        <div
          className="grid justify-start gap-3 sm:gap-4"
          style={{
            gridTemplateColumns: `repeat(auto-fill, ${coverSizePx(preferences.coverSize)}px)`,
          }}
        >
          {books.map((book) => (
            <BookCard
              book={book}
              coverSize={preferences.coverSize}
              key={book.id}
              onOpen={openBook}
            />
          ))}
        </div>
      ) : (
        <BookList books={books} onOpen={openBook} />
      )}

      <Pagination
        disabled={loading}
        limit={PAGE_SIZE}
        offset={offset}
        onOffsetChange={changePage}
        total={total}
      />

      {createOpen ? (
        <BookFormDialog
          mode="create"
          onClose={() => {
            setCreateOpen(false);
          }}
          onSaved={handleCreated}
          onSessionExpired={expireSession}
        />
      ) : null}
    </>
  );
}
