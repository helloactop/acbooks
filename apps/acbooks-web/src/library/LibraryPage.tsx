import {
  Button,
  cn,
  Field,
  Input,
  SegmentedControl,
  Select,
  Spinner,
  useLanguage,
  type SegmentedControlOption,
} from "@acbooks/ui";
import {
  ArrowUpDown,
  BookOpen,
  LayoutGrid,
  List,
  Plus,
  RefreshCw,
  Search,
  SlidersHorizontal,
  X,
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
const FILTER_FORMATS = ["epub", "pdf", "mobi", "azw3", "txt", "cbz"] as const;
const TOOLBAR_SEGMENTED =
  "h-10 [&>button]:h-8 max-lg:h-11 max-lg:p-0 max-lg:[&>button]:h-11";
const FILTER_CHIP_CLASS =
  "inline-flex h-9 max-w-full items-center gap-1.5 rounded-full border border-border bg-surface px-3 text-xs text-foreground transition-colors duration-150 ease-out hover:bg-surface-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none max-lg:min-h-11";

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
  const [tag, setTag] = useState(initialView.tag);
  const [tagDraft, setTagDraft] = useState(initialView.tag);
  const [format, setFormat] = useState(initialView.format);
  const [sort, setSort] = useState<SortOrder>(initialView.sort);
  const [view, setView] = useState<ViewMode>(initialView.view);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const scrollRestored = useRef(false);
  const searchTimer = useRef<number | undefined>(undefined);
  const tagTimer = useRef<number | undefined>(undefined);
  const composingRef = useRef(false);
  const tagComposingRef = useRef(false);
  const searchTermRef = useRef(initialView.searchTerm);
  const tagRef = useRef(initialView.tag);

  useEffect(
    () => () => {
      if (searchTimer.current !== undefined) {
        window.clearTimeout(searchTimer.current);
      }
      if (tagTimer.current !== undefined) {
        window.clearTimeout(tagTimer.current);
      }
    },
    [],
  );

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    let changed = false;
    for (const key of ["format", "q", "reset", "tag"]) {
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
      format,
      offset,
      query,
      searchTerm,
      sort,
      status,
      tag,
      type,
      view,
    });
  }, [format, offset, query, searchTerm, sort, status, tag, type, view]);

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
      tag: tag.trim().length > 0 ? tag.trim() : undefined,
      format: format.length > 0 ? format : undefined,
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
  }, [
    expireSession,
    format,
    offset,
    reloadKey,
    searchTerm,
    sort,
    status,
    tag,
    type,
  ]);

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

  const applyTag = (value: string) => {
    const next = value.trim();
    if (next === tagRef.current) {
      return;
    }
    tagRef.current = next;
    beginRequest();
    setTag(next);
    setOffset(0);
  };

  const scheduleTag = (value: string, immediate = false) => {
    setTagDraft(value);
    if (tagComposingRef.current) {
      return;
    }
    if (tagTimer.current !== undefined) {
      window.clearTimeout(tagTimer.current);
      tagTimer.current = undefined;
    }
    const run = () => {
      tagTimer.current = undefined;
      applyTag(value);
    };
    if (immediate || value.trim().length === 0) {
      run();
    } else {
      tagTimer.current = window.setTimeout(run, 300);
    }
  };

  const changeType = (value: TypeFilter) => {
    beginRequest();
    setType(value);
    setOffset(0);
    syncMediaTypeParam(value);
  };

  const changeStatus = (value: StatusFilter) => {
    beginRequest();
    setStatus(value);
    setOffset(0);
  };

  const changeFormat = (value: string) => {
    beginRequest();
    setFormat(value);
    setOffset(0);
  };

  const changeSort = (value: SortOrder) => {
    beginRequest();
    setSort(value);
    setOffset(0);
  };

  const clearTag = () => {
    if (tagTimer.current !== undefined) {
      window.clearTimeout(tagTimer.current);
      tagTimer.current = undefined;
    }
    tagComposingRef.current = false;
    tagRef.current = "";
    setTag("");
    setTagDraft("");
    beginRequest();
    setOffset(0);
  };

  const clearAdvanced = () => {
    if (tagTimer.current !== undefined) {
      window.clearTimeout(tagTimer.current);
      tagTimer.current = undefined;
    }
    tagComposingRef.current = false;
    tagRef.current = "";
    setTag("");
    setTagDraft("");
    setType("all");
    setFormat("");
    beginRequest();
    setOffset(0);
    syncMediaTypeParam("all");
  };

  const clearFilters = () => {
    if (searchTimer.current !== undefined) {
      window.clearTimeout(searchTimer.current);
      searchTimer.current = undefined;
    }
    if (tagTimer.current !== undefined) {
      window.clearTimeout(tagTimer.current);
      tagTimer.current = undefined;
    }
    composingRef.current = false;
    tagComposingRef.current = false;
    tagRef.current = "";
    searchTermRef.current = "";
    setQuery("");
    setSearchTerm("");
    setType("all");
    setStatus("all");
    setTag("");
    setTagDraft("");
    setFormat("");
    setSort("recent");
    setOffset(0);
    setFiltersOpen(false);
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
    saveLibraryView({
      format,
      offset,
      query,
      searchTerm,
      sort,
      status,
      tag,
      type,
      view,
    });
    saveLibraryScroll(mainRef.current?.scrollTop ?? 0);
    rememberDetailReturn();
  };

  const statusOptions: readonly SegmentedControlOption<StatusFilter>[] = [
    { label: t("filters.all"), value: "all" },
    { label: t("filters.unread"), value: "unread" },
    { label: t("filters.reading"), value: "reading" },
    { label: t("filters.done"), value: "done" },
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

  const advancedCount =
    (type === "all" ? 0 : 1) +
    (tag.trim().length > 0 ? 1 : 0) +
    (format.length > 0 ? 1 : 0);
  const hasFilters =
    searchTerm.trim().length > 0 || status !== "all" || advancedCount > 0;
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
  const typeChipLabel =
    type === "all"
      ? ""
      : t("filters.value", {
          label: t("filters.type"),
          value:
            type === "physical"
              ? t("filters.physical")
              : t("filters.digital"),
        });
  const formatChipLabel =
    format.length === 0
      ? ""
      : t("filters.value", {
          label: t("filters.format"),
          value: format.toLocaleUpperCase(),
        });
  const tagChipLabel =
    tag.trim().length === 0
      ? ""
      : t("filters.value", { label: t("filters.tag"), value: tag });

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
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative min-w-0 flex-1 basis-[220px]">
            <Search
              aria-hidden="true"
              className="pointer-events-none absolute top-1/2 left-3 size-[18px] -translate-y-1/2 text-muted-foreground"
              strokeWidth={2}
            />
            <Input
              aria-label={t("header.searchLabel")}
              className="pl-9 max-lg:h-11 [&::-webkit-search-cancel-button]:appearance-none"
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

          <label className="inline-flex items-center gap-2 text-sm text-muted-foreground">
            <ArrowUpDown
              aria-hidden="true"
              className="size-4 shrink-0"
              strokeWidth={2}
            />
            <span>{t("filters.sort")}</span>
            <Select
              aria-label={t("filters.sort")}
              className="h-10 w-auto min-w-[132px] max-lg:h-11"
              onChange={(event) => {
                changeSort(event.target.value as SortOrder);
              }}
              value={sort}
            >
              <option value="recent">{t("filters.recent")}</option>
              <option value="title">{t("filters.title")}</option>
              <option value="author">{t("filters.author")}</option>
            </Select>
          </label>

          <SegmentedControl
            ariaLabel={t("view.label")}
            className={TOOLBAR_SEGMENTED}
            onValueChange={setView}
            options={viewOptions}
            value={view}
          />
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <SegmentedControl
            ariaLabel={t("filters.status")}
            className={cn(TOOLBAR_SEGMENTED, "shrink-0 whitespace-nowrap")}
            onValueChange={changeStatus}
            options={statusOptions}
            value={status}
          />

          <Button
            aria-controls="library-filter-panel"
            aria-expanded={filtersOpen}
            aria-label={
              advancedCount > 0
                ? t("filters.moreActive", { count: advancedCount })
                : t("filters.more")
            }
            aria-pressed={advancedCount > 0}
            className="h-10 max-lg:min-h-11 aria-pressed:border-accent-border aria-pressed:bg-accent-soft aria-pressed:text-accent-text aria-pressed:hover:bg-accent-hover"
            onClick={() => {
              setFiltersOpen((current) => !current);
            }}
            variant="outline"
          >
            <SlidersHorizontal aria-hidden="true" />
            {t("filters.more")}
            {advancedCount > 0 ? (
              <span className="inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-accent-text px-1 text-[11px] font-semibold text-background">
                {advancedCount}
              </span>
            ) : null}
          </Button>

          {hasFilters ? (
            <Button
              className="h-10 max-lg:min-h-11"
              onClick={clearFilters}
              variant="ghost"
            >
              {t("empty.clear")}
            </Button>
          ) : null}
        </div>

        {!filtersOpen && advancedCount > 0 ? (
          <div className="flex flex-wrap gap-2">
            {type !== "all" ? (
              <button
                aria-label={t("filters.remove", { label: typeChipLabel })}
                className={FILTER_CHIP_CLASS}
                onClick={() => {
                  changeType("all");
                }}
                type="button"
              >
                <span className="truncate">{typeChipLabel}</span>
                <X aria-hidden="true" className="size-3.5 shrink-0 text-muted-foreground" />
              </button>
            ) : null}
            {format.length > 0 ? (
              <button
                aria-label={t("filters.remove", { label: formatChipLabel })}
                className={FILTER_CHIP_CLASS}
                onClick={() => {
                  changeFormat("");
                }}
                type="button"
              >
                <span className="truncate">{formatChipLabel}</span>
                <X aria-hidden="true" className="size-3.5 shrink-0 text-muted-foreground" />
              </button>
            ) : null}
            {tag.trim().length > 0 ? (
              <button
                aria-label={t("filters.remove", { label: tagChipLabel })}
                className={FILTER_CHIP_CLASS}
                onClick={() => {
                  clearTag();
                }}
                type="button"
              >
                <span className="truncate">{tagChipLabel}</span>
                <X aria-hidden="true" className="size-3.5 shrink-0 text-muted-foreground" />
              </button>
            ) : null}
          </div>
        ) : null}

        {filtersOpen ? (
          <div
            aria-label={t("filters.panel")}
            className="rounded-card border border-border bg-surface p-4"
            id="library-filter-panel"
            role="group"
          >
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <Field htmlFor="filter-type" label={t("filters.type")}>
                <Select
                  className="max-lg:h-11"
                  id="filter-type"
                  onChange={(event) => {
                    changeType(event.target.value as TypeFilter);
                  }}
                  value={type}
                >
                  <option value="all">{t("filters.all")}</option>
                  <option value="physical">{t("filters.physical")}</option>
                  <option value="digital">{t("filters.digital")}</option>
                </Select>
              </Field>

              <Field htmlFor="filter-format" label={t("filters.format")}>
                <Select
                  className="max-lg:h-11"
                  id="filter-format"
                  onChange={(event) => {
                    changeFormat(event.target.value);
                  }}
                  value={format}
                >
                  <option value="">{t("filters.allFormats")}</option>
                  {FILTER_FORMATS.map((item) => (
                    <option key={item} value={item}>
                      {item.toLocaleUpperCase()}
                    </option>
                  ))}
                </Select>
              </Field>

              <Field
                className="sm:col-span-2 lg:col-span-1"
                htmlFor="filter-tag"
                label={t("filters.tag")}
              >
                <Input
                  autoComplete="off"
                  className="max-lg:h-11"
                  id="filter-tag"
                  onBlur={(event) => {
                    scheduleTag(event.currentTarget.value, true);
                  }}
                  onChange={(event) => {
                    scheduleTag(event.target.value);
                  }}
                  onCompositionEnd={(event) => {
                    tagComposingRef.current = false;
                    scheduleTag(event.currentTarget.value, true);
                  }}
                  onCompositionStart={() => {
                    tagComposingRef.current = true;
                  }}
                  onKeyDown={(event) => {
                    if (
                      event.key === "Enter" &&
                      !event.nativeEvent.isComposing
                    ) {
                      event.preventDefault();
                      scheduleTag(event.currentTarget.value, true);
                    }
                  }}
                  placeholder={t("filters.tagPlaceholder")}
                  value={tagDraft}
                />
              </Field>
            </div>

            {advancedCount > 0 ? (
              <div className="mt-4 flex justify-end">
                <Button
                  className="max-lg:min-h-11"
                  onClick={clearAdvanced}
                  size="sm"
                  variant="ghost"
                >
                  {t("filters.clearAdvanced")}
                </Button>
              </div>
            ) : null}
          </div>
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
