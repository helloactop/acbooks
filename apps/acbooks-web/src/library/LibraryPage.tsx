import {
  Button,
  SegmentedControl,
  Spinner,
  useLanguage,
  type SegmentedControlOption,
} from "@acbooks/ui";
import {
  CircleAlert,
  LayoutGrid,
  List,
  Plus,
  RefreshCw,
  Search,
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { getCurrentAccount, type Account } from "../auth/auth-api";
import { AppShell } from "../layout/AppShell";
import { BookCard } from "./BookCard";
import { BookList } from "./BookList";
import { FilterSelect, type FilterOption } from "./FilterSelect";
import { LibrarySidebar } from "./LibrarySidebar";
import { LibraryTopbar } from "./LibraryTopbar";
import { SAMPLE_BOOKS } from "./sample-books";

type TypeFilter = "all" | "digital" | "physical";
type StatusFilter = "all" | "done" | "reading" | "unread";
type SortOrder = "author" | "recent" | "title";
type ViewMode = "grid" | "list";

type GuardState =
  | { status: "error" }
  | { status: "loading" }
  | { account: Account; status: "ready" };

export interface LibraryPageProps {
  onUnauthenticated?: () => void;
}

function redirectToLogin(): void {
  window.location.replace("/");
}

function option(value: string, label: string): FilterOption {
  return { label, value };
}

function LibraryContent({ accountName }: { accountName: string }) {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  const [query, setQuery] = useState("");
  const [committedQuery, setCommittedQuery] = useState("");
  const [type, setType] = useState<TypeFilter>("all");
  const [status, setStatus] = useState<StatusFilter>("all");
  const [sort, setSort] = useState<SortOrder>("recent");
  const [view, setView] = useState<ViewMode>("grid");
  const composingRef = useRef(false);

  const collator = useMemo(
    () => new Intl.Collator(resolvedLanguage, { sensitivity: "base" }),
    [resolvedLanguage],
  );
  const numberFormat = useMemo(
    () => new Intl.NumberFormat(resolvedLanguage),
    [resolvedLanguage],
  );

  const visibleBooks = useMemo(() => {
    const normalizedQuery = committedQuery
      .trim()
      .toLocaleLowerCase(resolvedLanguage);
    let books = SAMPLE_BOOKS.filter((book) => {
      if (type === "physical" && !book.tags.includes("physical")) {
        return false;
      }
      if (
        type === "digital" &&
        !book.tags.includes("epub") &&
        !book.tags.includes("pdf")
      ) {
        return false;
      }
      if (status !== "all" && book.status !== status) {
        return false;
      }
      if (normalizedQuery.length > 0) {
        const title = book.title.toLocaleLowerCase(resolvedLanguage);
        const author = book.author.toLocaleLowerCase(resolvedLanguage);
        if (
          !title.includes(normalizedQuery) &&
          !author.includes(normalizedQuery)
        ) {
          return false;
        }
      }
      return true;
    });

    if (sort === "title") {
      books = [...books].sort((left, right) =>
        collator.compare(left.title, right.title),
      );
    } else if (sort === "author") {
      books = [...books].sort((left, right) =>
        collator.compare(left.author, right.author),
      );
    }

    return books;
  }, [collator, committedQuery, resolvedLanguage, sort, status, type]);

  const formattedCount = numberFormat.format(visibleBooks.length);
  const countMessage = t("page.count", {
    count: visibleBooks.length,
    formattedCount,
  });

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

  const clearFilters = () => {
    setQuery("");
    setCommittedQuery("");
    setType("all");
    setStatus("all");
    setSort("recent");
  };

  return (
    <AppShell
      header={
        <LibraryTopbar
          accountName={accountName}
          onCompositionEnd={(value) => {
            composingRef.current = false;
            setCommittedQuery(value);
          }}
          onCompositionStart={() => {
            composingRef.current = true;
          }}
          onSearchChange={(value) => {
            setQuery(value);
            if (!composingRef.current) {
              setCommittedQuery(value);
            }
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
          <p className="mt-1 text-sm text-muted-foreground">{countMessage}</p>
        </div>
        <Button disabled title={t("nav.notAvailable")} variant="soft">
          <Plus aria-hidden="true" />
          {t("actions.addBook")}
        </Button>
      </div>

      <div className="flex items-center justify-between gap-3 max-md:gap-2">
        <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2 max-md:flex-nowrap max-md:overflow-x-auto max-md:pb-1">
          <FilterSelect
            label={t("filters.type")}
            onChange={(value) => {
              setType(value as TypeFilter);
            }}
            options={typeOptions}
            value={type}
          />
          <FilterSelect
            label={t("filters.status")}
            onChange={(value) => {
              setStatus(value as StatusFilter);
            }}
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
            onChange={(value) => {
              setSort(value as SortOrder);
            }}
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

      {visibleBooks.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-2 py-16 text-center">
          <Search
            aria-hidden="true"
            className="size-8 text-muted-foreground"
            strokeWidth={2}
          />
          <h2 className="text-xl font-semibold">{t("empty.title")}</h2>
          <p className="text-sm text-muted-foreground">
            {t("empty.description")}
          </p>
          <Button className="mt-2" onClick={clearFilters} variant="outline">
            {t("empty.clear")}
          </Button>
        </div>
      ) : view === "grid" ? (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(152px,1fr))] gap-6 max-md:grid-cols-2 max-md:gap-x-4 max-md:gap-y-6">
          {visibleBooks.map((book) => (
            <BookCard key={book.id} book={book} />
          ))}
        </div>
      ) : (
        <BookList books={visibleBooks} />
      )}
    </AppShell>
  );
}

export function LibraryPage({
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

  return <LibraryContent accountName={guard.account.username} />;
}
