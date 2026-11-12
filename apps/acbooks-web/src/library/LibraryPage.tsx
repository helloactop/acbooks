import {
  Button,
  Input,
  SegmentedControl,
  Spinner,
  useLanguage,
  type SegmentedControlOption,
} from "@acbooks/ui";
import {
  BookOpen,
  LayoutGrid,
  List,
  Plus,
  RefreshCw,
  Search,
} from "lucide-react";
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
import { libraryErrorMessage, libraryRequestId } from "./errors";
import { FilterSelect, type FilterOption } from "./FilterSelect";
import { LibraryAlert } from "./LibraryAlert";
import {
  readInitialLibraryView,
  saveLibraryScroll,
  saveLibraryView,
  takeLibraryScroll,
  type SortOrder,
  type StatusFilter,
  type TypeFilter,
  type ViewMode,
} from "./library-view-state";
import { Pagination } from "./Pagination";

const PAGE_SIZE = 12;

function option(value: string, label: string): FilterOption {
  return { label, value };
}

function syncMediaTypeParam(value: TypeFilter): void {
  const params = new URLSearchParams(window.location.search);
  if (value === "all") {
    params.delete("mediaType");
  } else {
    params.set("mediaType", value);
  }
  const search = params.toString();
  window.history.replaceState(
    {},
    "",
    `${window.location.pathname}${search.length > 0 ? `?${search}` : ""}${
      window.location.hash
    }`,
  );
  window.dispatchEvent(new Event("acbooks:locationchange"));
}

export function LibraryPage() {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  const { expireSession, mainRef } = useAppShell();
  const [initialView] = useState(readInitialLibraryView);
  const [books, setBooks] = useState<BookSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [offset, setOffset] = useState(initialView.offset);
  const [query, setQuery] = useState(initialView.query);
  const [searchTerm, setSearchTerm] = useState(initialView.searchTerm);
  const [type, setType] = useState<TypeFilter>(initialView.type);
  const [status, setStatus] = useState<StatusFilter>(initialView.status);
  const [sort, setSort] = useState<SortOrder>(initialView.sort);
  const [view, setView] = useState<ViewMode>(initialView.view);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const scrollRestored = useRef(false);
  const searchTimer = useRef<number | undefined>(undefined);
  const composingRef = useRef(false);
  const searchTermRef = useRef(initialView.searchTerm);

  useEffect(
    () => () => {
      if (searchTimer.current !== undefined) {
        window.clearTimeout(searchTimer.current);
      }
    },
    [],
  );

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (!params.has("q") && !params.has("reset")) {
      return;
    }
    params.delete("q");
    params.delete("reset");
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
    saveLibraryView({ offset, query, searchTerm, sort, status, type, view });
  }, [offset, query, searchTerm, sort, status, type, view]);

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
  }, [expireSession, offset, reloadKey, searchTerm, sort, status, type]);

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
    const next = value as TypeFilter;
    beginRequest();
    setType(next);
    setOffset(0);
    syncMediaTypeParam(next);
  };

  const changeStatus = (value: StatusFilter) => {
    beginRequest();
    setStatus(value);
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
    syncMediaTypeParam("all");
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

  const handleCreated = () => {
    setCreateOpen(false);
    refreshLibrary(true);
  };

  const openBook = () => {
    saveLibraryView({ offset, query, searchTerm, sort, status, type, view });
    saveLibraryScroll(mainRef.current?.scrollTop ?? 0);
    rememberDetailReturn();
  };

  const typeOptions: readonly FilterOption[] = [
    option("all", t("filters.all")),
    option("physical", t("filters.physical")),
    option("digital", t("filters.digital")),
  ];
  const statusOptions: readonly SegmentedControlOption<StatusFilter>[] = [
    { label: t("filters.all"), value: "all" },
    { label: t("filters.unread"), value: "unread" },
    { label: t("filters.reading"), value: "reading" },
    { label: t("filters.done"), value: "done" },
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
        title={t("page.title")}
      />

      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative min-w-0 flex-1 basis-[220px]">
            <Search
              aria-hidden="true"
              className="pointer-events-none absolute top-1/2 left-3 size-[18px] -translate-y-1/2 text-muted-foreground"
              strokeWidth={2}
            />
            <Input
              aria-label={t("header.searchLabel")}
              className="pl-9 [&::-webkit-search-cancel-button]:appearance-none"
              onChange={(event) => {
                commitSearch(event.target.value);
              }}
              onCompositionEnd={(event) => {
                composingRef.current = false;
                commitSearch(event.currentTarget.value, true);
              }}
              onCompositionStart={() => {
                composingRef.current = true;
              }}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  commitSearch(query, true);
                }
              }}
              placeholder={t("header.searchPlaceholder")}
              type="search"
              value={query}
            />
          </div>
          <SegmentedControl
            ariaLabel={t("view.label")}
            onValueChange={setView}
            options={viewOptions}
            value={view}
          />
        </div>

        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <SegmentedControl
            ariaLabel={t("filters.status")}
            className="shrink-0 whitespace-nowrap"
            onValueChange={changeStatus}
            options={statusOptions}
            value={status}
          />
          <FilterSelect
            label={t("filters.type")}
            onChange={changeType}
            options={typeOptions}
            value={type}
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
          {hasFilters ? (
            <Button onClick={clearFilters} size="sm" variant="ghost">
              {t("empty.clear")}
            </Button>
          ) : null}
        </div>
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
