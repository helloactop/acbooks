import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { EMPTY_FILTER_RULES, type SmartFilterRules } from "./filter-model";
import { libraryUrlSearch, readLibraryUrlState } from "./library-url-state";
import type {
  LibraryViewState,
  SortOrder,
  StatusFilter,
  TypeFilter,
  ViewMode,
} from "./library-view-state";

export interface LibraryUrlState {
  clearAll: () => void;
  clearSearch: () => void;
  externalRevision: number;
  filters: SmartFilterRules;
  hasViewParam: boolean;
  offset: number;
  searchTerm: string;
  setFilters: (filters: SmartFilterRules) => void;
  setOffset: (offset: number) => void;
  setSort: (sort: SortOrder) => void;
  setStatus: (status: StatusFilter) => void;
  setType: (type: TypeFilter) => void;
  setView: (view: ViewMode) => void;
  sort: SortOrder;
  status: StatusFilter;
  type: TypeFilter;
  view: ViewMode;
}

const MAX_PENDING_WRITES = 8;

export function useLibraryUrlState(): LibraryUrlState {
  const location = useLocation();
  const navigate = useNavigate();
  const parsed = useMemo(
    () => readLibraryUrlState(location.search),
    [location.search],
  );
  const hasViewParam = useMemo(
    () => new URLSearchParams(location.search).has("view"),
    [location.search],
  );
  const searchRef = useRef(location.search);
  const lastSearchRef = useRef(location.search);
  const pendingWrites = useRef<string[]>([]);
  const [externalRevision, setExternalRevision] = useState(0);

  useEffect(() => {
    searchRef.current = location.search;
  }, [location.search]);

  const queueWrite = useCallback((search: string) => {
    pendingWrites.current.push(search);
    if (pendingWrites.current.length > MAX_PENDING_WRITES) {
      pendingWrites.current.shift();
    }
  }, []);

  const write = useCallback(
    (next: LibraryViewState) => {
      const search = libraryUrlSearch(next);
      const target = search.length > 0 ? `?${search}` : "";
      if (target === searchRef.current) {
        return;
      }
      searchRef.current = target;
      queueWrite(target);
      navigate({ pathname: "/library", search: target }, { replace: true });
    },
    [navigate, queueWrite],
  );

  const update = useCallback(
    (patch: (current: LibraryViewState) => LibraryViewState) => {
      // Read the latest URL so rapid successive actions merge instead of
      // overwriting each other with stale render values.
      write(patch(readLibraryUrlState(searchRef.current)));
    },
    [write],
  );

  useEffect(() => {
    const current = location.search;
    if (current === lastSearchRef.current) {
      return;
    }
    lastSearchRef.current = current;
    const pendingIndex = pendingWrites.current.indexOf(current);
    if (pendingIndex >= 0) {
      pendingWrites.current = pendingWrites.current.slice(pendingIndex + 1);
      return;
    }
    pendingWrites.current = [];
    setExternalRevision((value) => value + 1);
  }, [location.search]);

  useEffect(() => {
    const search = libraryUrlSearch(parsed);
    const canonical = search.length > 0 ? `?${search}` : "";
    if (canonical === location.search) {
      return;
    }
    searchRef.current = canonical;
    queueWrite(canonical);
    navigate({ pathname: "/library", search: canonical }, { replace: true });
  }, [location.search, navigate, parsed, queueWrite]);

  const setType = useCallback(
    (type: TypeFilter) => {
      update((current) =>
        current.type === type ? current : { ...current, type, offset: 0 },
      );
    },
    [update],
  );
  const setStatus = useCallback(
    (status: StatusFilter) => {
      update((current) =>
        current.status === status
          ? current
          : { ...current, status, offset: 0 },
      );
    },
    [update],
  );
  const setSort = useCallback(
    (sort: SortOrder) => {
      update((current) =>
        current.sort === sort ? current : { ...current, sort, offset: 0 },
      );
    },
    [update],
  );
  const setOffset = useCallback(
    (offset: number) => {
      update((current) =>
        current.offset === offset ? current : { ...current, offset },
      );
    },
    [update],
  );
  const setView = useCallback(
    (view: ViewMode) => {
      update((current) =>
        current.view === view ? current : { ...current, view },
      );
    },
    [update],
  );
  const setFilters = useCallback(
    (filters: SmartFilterRules) => {
      update((current) => ({ ...current, filters, offset: 0 }));
    },
    [update],
  );
  const clearSearch = useCallback(() => {
    update((current) =>
      current.searchTerm.length === 0
        ? current
        : { ...current, searchTerm: "", offset: 0 },
    );
  }, [update]);
  const clearAll = useCallback(() => {
    update((current) => ({
      ...current,
      filters: EMPTY_FILTER_RULES,
      offset: 0,
      searchTerm: "",
      status: "all",
      type: "all",
    }));
  }, [update]);

  return {
    clearAll,
    clearSearch,
    externalRevision,
    filters: parsed.filters,
    hasViewParam,
    offset: parsed.offset,
    searchTerm: parsed.searchTerm,
    setFilters,
    setOffset,
    setSort,
    setStatus,
    setType,
    setView,
    sort: parsed.sort,
    status: parsed.status,
    type: parsed.type,
    view: parsed.view,
  };
}
