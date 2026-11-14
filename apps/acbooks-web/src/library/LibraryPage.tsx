import {
  Button,
  SegmentedControl,
  Select,
  Spinner,
  useLanguage,
} from "@acbooks/ui";
import { BookOpen, Search, X } from "lucide-react";
import { useEffect, useState, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import { toApiError, type ApiError } from "../api/client";
import { useAppShell } from "../layout/app-shell-context";
import { PageHeader } from "../layout/PageHeader";
import { bookDetailHref, rememberDetailReturn } from "../layout/routes";
import { rememberReturnScroll } from "../layout/scroll-restoration";
import { PageContainer } from "../layout/PageContainer";
import { useDocumentTitle } from "../layout/use-document-title";
import { MetadataDialog } from "./MetadataDialog";
import { BatchEditDialog } from "./BatchEditDialog";
import { BookCard } from "./BookCard";
import { BookFormDialog } from "./BookFormDialog";
import { BookList } from "./BookList";
import { BookMergeDialog } from "./BookMergeDialog";
import type { MergePair } from "./organization-api";
import { batchDeleteBooks } from "./books-api";
import { DeleteBooksDialog } from "./DeleteBooksDialog";
import { ActiveFilterSummary } from "./ActiveFilterSummary";
import { libraryErrorMessage, libraryRequestId } from "./errors";
import { serializeSmartFilterRules } from "./filter-model";
import { LibraryAlert } from "./LibraryAlert";
import { LibraryBatchBar } from "./LibraryBatchBar";
import { LibraryFilterToolbar } from "./LibraryFilterToolbar";
import {
  coverSizePx,
  LIBRARY_PAGE_SIZE,
  type CoverSize,
  type SortOrder,
  type StatusFilter,
  type TypeFilter,
  type ViewMode,
} from "./library-view-state";
import { Pagination } from "./Pagination";
import { SmartFilterBuilder } from "./SmartFilterBuilder";
import { useBookFilterSchema } from "./use-book-filter-schema";
import { useLibraryBooks } from "./use-library-books";
import { useLibraryFilters } from "./use-library-filters";
import { useLibraryScrollRestoration } from "./use-library-scroll-restoration";
import { useLibraryUrlState } from "./use-library-url-state";
import { useLibraryViewPreferences } from "./use-library-view-preferences";

export function LibraryPage() {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  const { expireSession, mainRef } = useAppShell();
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
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [uploadIssues, setUploadIssues] = useState<string[]>([]);
  const navigate = useNavigate();
  const [mergePair, setMergePair] = useState<MergePair>();
  const [selecting, setSelecting] = useState(false);
  const [selectedIds, setSelectedIds] = useState<ReadonlySet<string>>(
    new Set(),
  );
  const [batchMetadataOpen, setBatchMetadataOpen] = useState(false);
  const [batchEditOpen, setBatchEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState<ApiError | null>(null);
  // Snapshot the targets when a batch dialog opens so a background reload
  // cannot change which books the confirmation applies to.
  const [batchTargetIds, setBatchTargetIds] = useState<string[]>([]);
  const filterSchema = useBookFilterSchema(true, expireSession);
  const fields = filterSchema.schema?.fields ?? [];
  const appliedFilters = serializeSmartFilterRules(appliedRules, fields);
  const waitingForSchema =
    appliedRules.conditions.length > 0 &&
    fields.length === 0 &&
    filterSchema.error === null;
  const { beginUpdate, books, error, loading, reload, total } =
    useLibraryBooks({
      appliedFilters,
      expireSession,
      offset,
      pageSize: LIBRARY_PAGE_SIZE,
      searchTerm,
      setOffset,
      sort,
      status,
      type,
      waitingForSchema,
    });
  useLibraryScrollRestoration({ loading, mainRef });
  const { changeRules, removeCondition, resetDraft, rules, summary } =
    useLibraryFilters({
      appliedFilters,
      appliedRules,
      externalRevision,
      fields,
      onApply: (next) => {
        beginUpdate();
        setFilters(next);
      },
    });

  const changeStatus = (value: StatusFilter) => {
    if (value === status) {
      return;
    }
    beginUpdate();
    setStatus(value);
  };

  const changeType = (value: TypeFilter) => {
    if (value === type) {
      return;
    }
    beginUpdate();
    setType(value);
  };

  const changeSort = (value: SortOrder) => {
    if (value === sort) {
      return;
    }
    beginUpdate();
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
    beginUpdate();
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
      beginUpdate();
    }
    clearAll();
  };

  const refreshLibrary = () => {
    setOffset(0);
    reload();
  };

  useEffect(() => {
    // Selection applies to the current query; leaving it is an external reset.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setSelectedIds(new Set());
  }, [appliedFilters, offset, searchTerm, sort, status, type]);

  const leaveSelection = () => {
    setSelecting(false);
    setSelectedIds(new Set());
  };

  const toggleSelection = () => {
    if (selecting) {
      leaveSelection();
    } else {
      setSelecting(true);
    }
  };

  const toggleBookSelection = (bookId: string) => {
    if (loading) {
      return;
    }
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(bookId)) {
        next.delete(bookId);
      } else {
        next.add(bookId);
      }
      return next;
    });
  };

  const allSelected =
    books.length > 0 && books.every((book) => selectedIds.has(book.id));

  const togglePageSelection = () => {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (allSelected) {
        for (const book of books) {
          next.delete(book.id);
        }
      } else {
        for (const book of books) {
          next.add(book.id);
        }
      }
      return next;
    });
  };

  const finishBatch = () => {
    setBatchEditOpen(false);
    setDeleteOpen(false);
    setDeleteError(null);
    setBatchTargetIds([]);
    leaveSelection();
    reload();
  };

  const confirmBatchDelete = async () => {
    if (batchTargetIds.length === 0 || deleteBusy) {
      return;
    }
    setDeleteBusy(true);
    setDeleteError(null);
    try {
      await batchDeleteBooks(batchTargetIds);
      setDeleteBusy(false);
      finishBatch();
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
    beginUpdate();
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
  const statusLabel = status === "all" ? t("filters.all") : t(`status.${status}`);
  const sortLabel = t(sort === "recent" ? "filters.recentShort" : `filters.${sort}`);
  const typeLabel = t(type === "physical" ? "page.physicalTitle" : "page.digitalTitle");
  const filterChips = [
    ...(searchTerm.length > 0
      ? [{ label: searchTerm, removeLabel: t("filters.clearSearch"), onRemove: clearSearch }]
      : []),
    ...(status !== "all"
      ? [{
          label: t("filters.statusCurrent", { value: statusLabel }),
          removeLabel: t("filters.summaryRemove", { condition: statusLabel }),
          onRemove: () => changeStatus("all"),
        }]
      : []),
    ...(type !== "all"
      ? [{
          label: typeLabel,
          removeLabel: t("filters.summaryRemove", { condition: typeLabel }),
          onRemove: () => changeType("all"),
        }]
      : []),
  ];
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
    <PageContainer className="flex-1">
      {mergePair && <BookMergeDialog pair={mergePair} onClose={() => setMergePair(undefined)}
        onMerged={(id) => { openBook(); void navigate(bookDetailHref(id)); }} />}
      <PageHeader
        actions={
          <>
            <Button
              aria-pressed={selecting}
              className="max-[360px]:px-2"
              onClick={toggleSelection}
              variant="outline"
            >
              {selecting ? t("selection.done") : t("selection.start")}
            </Button>
            <Button
              className="max-[360px]:px-2"
              onClick={() => {
                setCreateOpen(true);
              }}
              variant="soft"
            >
              {t("actions.addBook")}
            </Button>
          </>
        }
        description={
          <span className="flex flex-wrap items-center gap-2">
            <span>{countMessage}</span>
            {loading && books.length > 0 ? (
              <Spinner label={t("loading.updating")} />
            ) : null}
          </span>
        }
        title={t("page.libraryTitle")}
      />

      {uploadIssues.length > 0 ? (
        <div
          className="flex items-start gap-2 rounded-card border border-border bg-surface px-3 py-2 text-sm"
          role="status"
        >
          <span className="min-w-0 flex-1 text-muted-foreground">
            {t("form.filesPartial", { names: uploadIssues.join("、") })}
          </span>
          <button
            aria-label={t("form.close")}
            className="shrink-0 rounded-control p-1 text-muted-foreground transition-colors duration-150 ease-out hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
            onClick={() => {
              setUploadIssues([]);
            }}
            type="button"
          >
            <X aria-hidden="true" className="size-4" />
          </button>
        </div>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        <SegmentedControl
          ariaLabel={t("filters.type")}
          className="w-full [&>button]:min-w-0 [&>button]:flex-1 max-md:[&>button]:px-1 md:w-auto md:self-start md:[&>button]:flex-none"
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

        <div className="flex w-full flex-wrap items-center gap-2 md:w-auto">
          <Select
            ariaLabel={t("filters.status")}
            containerClassName="min-w-0 flex-1 basis-[112px] md:w-[140px] md:flex-none"
            onValueChange={(value) => {
              changeStatus(value as StatusFilter);
            }}
            options={[
              { label: t("filters.all"), value: "all" },
              { label: t("status.unread"), value: "unread" },
              { label: t("status.reading"), value: "reading" },
              { label: t("status.done"), value: "done" },
            ]}
            triggerLabel={t("filters.statusCurrent", { value: statusLabel })}
            value={status}
          />
          <Select
            ariaLabel={t("filters.sort")}
            containerClassName="min-w-0 flex-1 basis-[144px] md:w-[168px] md:flex-none"
            onValueChange={(value) => {
              changeSort(value as SortOrder);
            }}
            options={[
              { label: t("filters.recent"), value: "recent" },
              { label: t("filters.title"), value: "title" },
              { label: t("filters.author"), value: "author" },
            ]}
            triggerLabel={t("filters.sortCurrent", { value: sortLabel })}
            value={sort}
          />
          <div className="shrink-0 max-md:w-full">
            <LibraryFilterToolbar
              activeCount={activeCount}
              coverSize={preferences.coverSize}
              filtersOpen={filtersOpen}
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

      {selecting ? (
        <LibraryBatchBar
          allSelected={allSelected}
          count={selectedIds.size}
          disabled={deleteBusy || loading}
          onClear={() => {
            setSelectedIds(new Set());
          }}
          onDelete={() => {
            setBatchTargetIds(Array.from(selectedIds));
            setDeleteOpen(true);
          }}
          onEdit={() => {
            setBatchTargetIds(Array.from(selectedIds));
            setBatchEditOpen(true);
          }}
          onMerge={() => {
            const [targetBookId, sourceBookId] = selectedIds;
            if (targetBookId && sourceBookId) setMergePair({ sourceBookId, targetBookId });
          }}
          onMetadata={() => {
            setBatchTargetIds(Array.from(selectedIds));
            setBatchMetadataOpen(true);
          }}
          onSelectAll={togglePageSelection}
        />
      ) : null}

      {hasExternalFilters || summary.total > 0 ? (
        <ActiveFilterSummary
          chips={filterChips}
          combinator={rules.combinator}
          fields={fields}
          onClearAll={hasFilters ? clearAllFilters : undefined}
          onOpenFilters={() => setFiltersOpen(true)}
          onRemove={removeCondition}
          summary={summary}
        />
      ) : null}

      {filtersOpen ? (
        <SmartFilterBuilder
          error={schemaError}
          fields={fields}
          loading={schemaLoading}
          maxConditions={maxConditions}
          onChange={changeRules}
          onRetry={filterSchema.retry}
          rules={rules}
        />
      ) : null}

      {errorMessage !== undefined && books.length > 0 ? (
        <div className="flex flex-col gap-2">
          <LibraryAlert requestId={errorRequestId}>
            {errorMessage}
          </LibraryAlert>
          <div>
            <Button onClick={reload} size="sm" variant="outline">
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
          <Button onClick={reload} variant="soft">
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
          className={preferences.coverSize === "large"
            ? "grid grid-cols-[repeat(auto-fill,min(100%,var(--cover-width)))] gap-3 sm:gap-4"
            : "grid grid-cols-[repeat(auto-fill,minmax(min(var(--cover-width),calc((100%_-_12px)/2)),1fr))] gap-3 sm:grid-cols-[repeat(auto-fill,var(--cover-width))] sm:gap-4"
          }
          style={{
            "--cover-width": `${coverSizePx(preferences.coverSize)}px`,
          } as CSSProperties}
        >
          {books.map((book) => (
            <BookCard
              book={book}
              coverSize={preferences.coverSize}
              key={book.id}
              onOpen={openBook}
              onSelect={toggleBookSelection}
              selectable={selecting}
              selected={selectedIds.has(book.id)}
              selectionDisabled={loading}
            />
          ))}
        </div>
      ) : (
        <BookList
          books={books}
          onOpen={openBook}
          onSelect={toggleBookSelection}
          selectable={selecting}
          selectedIds={selectedIds}
          selectionDisabled={loading}
        />
      )}

      <Pagination
        disabled={loading}
        limit={LIBRARY_PAGE_SIZE}
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
          onFilesFailed={setUploadIssues}
          onSaved={handleCreated}
          onSessionExpired={expireSession}
        />
      ) : null}

      {batchMetadataOpen ? (
        <MetadataDialog bookIds={batchTargetIds} onApplied={reload}
          onClose={() => { setBatchMetadataOpen(false); reload(); }} />
      ) : null}
      {batchEditOpen ? (
        <BatchEditDialog
          bookIds={batchTargetIds}
          onClose={() => {
            setBatchEditOpen(false);
          }}
          onSaved={finishBatch}
          onSessionExpired={expireSession}
        />
      ) : null}

      {deleteOpen ? (
        <DeleteBooksDialog
          busy={deleteBusy}
          count={batchTargetIds.length}
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
            void confirmBatchDelete();
          }}
        />
      ) : null}
    </PageContainer>
  );
}
