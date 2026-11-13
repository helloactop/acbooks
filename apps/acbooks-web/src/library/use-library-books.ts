import { useEffect, useState } from "react";

import { isAbortError, toApiError, type ApiError } from "../api/client";
import {
  getBooks,
  type BookListQuery,
  type BookSummary,
} from "./books-api";
import type {
  SortOrder,
  StatusFilter,
  TypeFilter,
} from "./library-view-state";

export interface LibraryBooksOptions {
  appliedFilters: string;
  expireSession: () => void;
  offset: number;
  pageSize: number;
  searchTerm: string;
  setOffset: (offset: number) => void;
  sort: SortOrder;
  status: StatusFilter;
  type: TypeFilter;
  waitingForSchema: boolean;
}

export interface LibraryBooksResult {
  beginUpdate: () => void;
  books: BookSummary[];
  error: ApiError | null;
  loading: boolean;
  reload: () => void;
  total: number;
}

export function useLibraryBooks({
  appliedFilters,
  expireSession,
  offset,
  pageSize,
  searchTerm,
  setOffset,
  sort,
  status,
  type,
  waitingForSchema,
}: LibraryBooksOptions): LibraryBooksResult {
  const [books, setBooks] = useState<BookSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

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
      limit: pageSize,
      offset,
    };

    getBooks(query, controller.signal)
      .then((page) => {
        if (controller.signal.aborted) {
          return;
        }
        const responseSize = page.limit > 0 ? page.limit : pageSize;
        const maxOffset =
          page.total > 0
            ? Math.floor((page.total - 1) / responseSize) * responseSize
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
    offset,
    pageSize,
    reloadKey,
    searchTerm,
    setOffset,
    sort,
    status,
    type,
    waitingForSchema,
  ]);

  const beginUpdate = () => {
    setLoading(true);
    setError(null);
  };

  const reload = () => {
    setLoading(true);
    setError(null);
    setReloadKey((current) => current + 1);
  };

  return { beginUpdate, books, error, loading, reload, total };
}
