import { Button, Spinner, useLanguage } from "@acbooks/ui";
import { BookOpen, Plus, RefreshCw, Search } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { isAbortError, toApiError, type ApiError } from "../api/client";
import { useAppShell } from "../layout/app-shell-context";
import { PageHeader } from "../layout/PageHeader";
import { rememberDetailReturn } from "../layout/routes";
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
import {
  serializeSmartFilterRules,
  summarizeSmartFilterRules,
  type SmartFilterRules,
} from "./filter-model";
import { LibraryAlert } from "./LibraryAlert";
import { LibraryFilterToolbar } from "./LibraryFilterToolbar";
import {
  readInitialLibraryView,
  saveLibraryScroll,
  saveLibraryView,
  takeLibraryScroll,
  type SortOrder,
  type StatusFilter,
  type ViewMode,
} from "./library-view-state";
import { Pagination } from "./Pagination";
import { SmartFilterBuilder } from "./SmartFilterBuilder";
import { useBookFilterSchema } from "./use-book-filter-schema";

const PAGE_SIZE = 12;

export function LibraryPage() {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  const { expireSession, mainRef } = useAppShell();
  const [initialView] = useState(readInitialLibraryView);
  const [books, setBooks] = useState<BookSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [offset, setOffset] = useState(initialView.offset);
  const [searchTerm, setSearchTerm] = useState(initialView.searchTerm);
  const [type] = useState(initialView.type);
  const [status, setStatus] = useState<StatusFilter>(initialView.status);
  const [rules, setRules] = useState<SmartFilterRules>(initialView.filters);
  const [appliedRules, setAppliedRules] = useState<SmartFilterRules>(
    initialView.filters,
  );
  const [sort, setSort] = useState<SortOrder>(initialView.sort);
  const [view, setView] = useState<ViewMode>(initialView.view);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const scrollRestored = useRef(false);
  const filterTimer = useRef<number | undefined>(undefined);
  const filterSchema = useBookFilterSchema(true, expireSession);
  const fields = filterSchema.schema?.fields ?? [];
  const summary = summarizeSmartFilterRules(rules, fields);
  const appliedFilters = serializeSmartFilterRules(appliedRules, fields);
  const waitingForSchema =
    appliedRules.conditions.length > 0 &&
    fields.length === 0 &&
    filterSchema.error === null;

  const beginRequest = () => {
    setLoading(true);
    setError(null);
  };

  useEffect(
    () => () => {
      if (filterTimer.current !== undefined) {
        window.clearTimeout(filterTimer.current);
      }
    },
    [],
  );

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    let changed = false;
    for (const key of ["filters", "format", "q", "reset", "tag"]) {
      if (params.has(key)) {
        params.delete(key);
        changed = true;
      }
    }
    if (!changed) {
      return;
    }
    const search = params.toString();
    window.history.replaceState(
      {},
      "",
      `${window.location.pathname}${search.length > 0 ? `?${search}` : ""}${
        window.location.hash
      }`,
    );
  }, []);

  useEffect(() => {
    saveLibraryView({
      filters: rules,
      offset,
      searchTerm,
      sort,
      status,
      type,
      view,
    });
  }, [offset, rules, searchTerm, sort, status, type, view]);

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
    const scrollTop = takeLibraryScroll();
    if (scrollTop <= 0) {
      return;
    }
    const frame = window.requestAnimationFrame(() => {
      mainRef.current?.scrollTo({ top: scrollTop });
    });
    return () => {
      window.cancelAnimationFrame(frame);
    };
  }, [loading, mainRef]);

  useEffect(() => {
    if (waitingForSchema) {
      return;
    }
    const controller = new AbortController();
    const query: BookListQuery = {
      q: searchTerm.length > 0 ? searchTerm : undefined,
      readingStatus: status === "all" ? undefined : status,
      mediaType: type === "all" ? undefined : type,
      filters: appliedFilters.length > 0 ? appliedFilters : undefined,
      sort: sort === "recent" ? "createdAt" : sort,
      order: sort === "recent" ? "desc" : "asc",
      limit: PAGE_SIZE,
      offset,
    };

    getBooks(query, controller.signal)
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
  }, [
    appliedFilters,
    expireSession,
    offset,
    reloadKey,
    searchTerm,
    sort,
    status,
    type,
    waitingForSchema,
  ]);

  const commitAppliedRules = (next: SmartFilterRules) => {
    setAppliedRules(next);
    setOffset(0);
    beginRequest();
  };

  const changeRules = (
    next: SmartFilterRules,
    immediate = false,
  ) => {
    setRules(next);
    if (filterTimer.current !== undefined) {
      window.clearTimeout(filterTimer.current);
      filterTimer.current = undefined;
    }
    const serialized = serializeSmartFilterRules(next, fields);
    if (serialized === appliedFilters) {
      return;
    }
    if (immediate || serialized.length === 0) {
      commitAppliedRules(next);
      return;
    }
    filterTimer.current = window.setTimeout(() => {
      filterTimer.current = undefined;
      commitAppliedRules(next);
    }, 300);
  };

  const changeSearch = (value: string) => {
    if (value === searchTerm) {
      return;
    }
    beginRequest();
    setSearchTerm(value);
    setOffset(0);
  };

  const changeStatus = (value: StatusFilter) => {
    if (value === status) {
      return;
    }
    beginRequest();
    setStatus(value);
    setOffset(0);
  };

  const changeSort = (value: SortOrder) => {
    if (value === sort) {
      return;
    }
    beginRequest();
    setSort(value);
    setOffset(0);
  };

  const changeView = (value: ViewMode) => {
    if (value === view) {
      return;
    }
    setView(value);
  };

  const clearSearch = () => {
    if (searchTerm.length === 0) {
      return;
    }
    beginRequest();
    setSearchTerm("");
    setOffset(0);
  };

  const clearAllFilters = () => {
    if (filterTimer.current !== undefined) {
      window.clearTimeout(filterTimer.current);
      filterTimer.current = undefined;
    }
    setRules({ combinator: "ALL", conditions: [] });
    setAppliedRules({ combinator: "ALL", conditions: [] });
    setSearchTerm("");
    setStatus("all");
    setOffset(0);
    beginRequest();
  };

  const removeCondition = (id: string) => {
    changeRules(
      {
        ...rules,
        conditions: rules.conditions.filter(
          (condition) => condition.id !== id,
        ),
      },
      true,
    );
  };

  const retry = () => {
    beginRequest();
    setReloadKey((current) => current + 1);
  };

  const refreshLibrary = () => {
    beginRequest();
    setOffset(0);
    setReloadKey((current) => current + 1);
  };

  const handleCreated = () => {
    setCreateOpen(false);
    refreshLibrary();
  };

  const openBook = () => {
    saveLibraryView({
      filters: rules,
      offset,
      searchTerm,
      sort,
      status,
      type,
      view,
    });
    saveLibraryScroll(mainRef.current?.scrollTop ?? 0);
    rememberDetailReturn();
  };

  const activeCount = summary.validCount;
  const hasExternalFilters =
    searchTerm.length > 0 || status !== "all";
  const hasFilters = hasExternalFilters || activeCount > 0;
  const pageTitle =
    type === "physical"
      ? t("page.physicalTitle")
      : type === "digital"
        ? t("page.digitalTitle")
        : t("page.title");
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
            <Plus aria-hidden="true" />
            {t("actions.addBook")}
          </Button>
        }
        description={
          <span className="flex items-center gap-2">
            <span>{countMessage}</span>
            {loading && books.length > 0 ? (
              <Spinner label={t("loading.updating")} />
            ) : null}
          </span>
        }
        title={pageTitle}
      />

      <div className="flex flex-col gap-3">
        <LibraryFilterToolbar
          activeCount={activeCount}
          filtersOpen={filtersOpen}
          hasSearch={searchTerm.length > 0}
          onClearSearch={clearSearch}
          onSearchChange={changeSearch}
          onSortChange={changeSort}
          onStatusChange={changeStatus}
          onToggleFilters={() => {
            setFiltersOpen((current) => !current);
          }}
          onViewChange={changeView}
          searchValue={searchTerm}
          sort={sort}
          status={status}
          view={view}
        />

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
              <Plus aria-hidden="true" />
              {t("empty.addBook")}
            </Button>
          )}
        </div>
      ) : view === "grid" ? (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(152px,1fr))] gap-6 max-md:grid-cols-2 max-md:gap-x-4 max-md:gap-y-6">
          {books.map((book) => (
            <BookCard key={book.id} book={book} onOpen={openBook} />
          ))}
        </div>
      ) : (
        <BookList books={books} onOpen={openBook} />
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
