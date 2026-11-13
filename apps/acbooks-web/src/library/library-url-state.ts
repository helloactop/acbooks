import {
  filterRulesFromTagAndFormat,
  parseSmartFilterRules,
  serializeSmartFilterRules,
} from "./filter-model";
import {
  DEFAULT_LIBRARY_VIEW,
  LIBRARY_PAGE_SIZE,
  type LibraryViewState,
  type SortOrder,
  type StatusFilter,
  type TypeFilter,
  type ViewMode,
} from "./library-view-state";

const SORTS: readonly SortOrder[] = ["author", "recent", "title"];
const STATUSES: readonly StatusFilter[] = ["all", "done", "reading", "unread"];
const TYPES: readonly TypeFilter[] = ["all", "digital", "physical"];
const VIEWS: readonly ViewMode[] = ["grid", "list"];

function isOneOf<T extends string>(
  values: readonly T[],
  value: string | null,
): value is T {
  return value !== null && values.includes(value as T);
}

const MAX_ALIGNED_OFFSET =
  Math.floor(1_000_000 / LIBRARY_PAGE_SIZE) * LIBRARY_PAGE_SIZE;

export function readLibraryUrlState(search: string): LibraryViewState {
  const params = new URLSearchParams(search);
  const initial: LibraryViewState = {
    ...DEFAULT_LIBRARY_VIEW,
    filters: { combinator: "ALL", conditions: [] },
  };
  if (params.get("reset") === "1") {
    return applyNonListParams(initial, params);
  }

  const mediaType = params.get("mediaType");
  const status = params.get("readingStatus");
  const sort = params.get("sort");
  const view = params.get("view");
  const offsetValue = Number(params.get("offset"));
  const urlFilters = params.get("filters");

  const state: LibraryViewState = {
    filters:
      urlFilters !== null
        ? parseSmartFilterRules(urlFilters)
        : filterRulesFromTagAndFormat(
            params.get("tag") ?? "",
            params.get("format") ?? "",
          ),
    offset:
      Number.isInteger(offsetValue) && offsetValue >= 0
        ? Math.min(
            Math.floor(offsetValue / LIBRARY_PAGE_SIZE) * LIBRARY_PAGE_SIZE,
            MAX_ALIGNED_OFFSET,
          )
        : initial.offset,
    searchTerm: initial.searchTerm,
    sort: isOneOf(SORTS, sort) ? sort : initial.sort,
    status: isOneOf(STATUSES, status) ? status : initial.status,
    type: isOneOf(TYPES, mediaType) ? mediaType : initial.type,
    view: isOneOf(VIEWS, view) ? view : initial.view,
  };

  return applyNonListParams(state, params);
}

function applyNonListParams(
  state: LibraryViewState,
  params: URLSearchParams,
): LibraryViewState {
  const mediaType = params.get("mediaType");
  let next: LibraryViewState = {
    ...state,
    type: isOneOf(TYPES, mediaType) ? mediaType : state.type,
  };
  const query = params.get("q");
  if (query !== null) {
    next = {
      ...next,
      searchTerm: query.trim(),
    };
  }
  return next;
}

export function libraryUrlSearch(
  state: LibraryViewState,
  includeView = false,
): string {
  const params = new URLSearchParams();
  if (state.searchTerm.length > 0) {
    params.set("q", state.searchTerm);
  }
  if (state.type !== "all") {
    params.set("mediaType", state.type);
  }
  if (state.status !== "all") {
    params.set("readingStatus", state.status);
  }
  const filters = serializeSmartFilterRules(state.filters);
  if (filters.length > 0) {
    params.set("filters", filters);
  }
  if (state.sort !== "recent") {
    params.set("sort", state.sort);
  }
  if (includeView || state.view !== "grid") {
    params.set("view", state.view);
  }
  if (state.offset > 0) {
    params.set("offset", String(state.offset));
  }
  return params.toString();
}
