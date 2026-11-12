import {
  EMPTY_FILTER_RULES,
  filterRulesFromTagAndFormat,
  parseSmartFilterRules,
  type SmartFilterRules,
} from "./filter-model";

export type TypeFilter = "all" | "digital" | "physical";
export type StatusFilter = "all" | "done" | "reading" | "unread";
export type SortOrder = "author" | "recent" | "title";
export type ViewMode = "grid" | "list";

export interface LibraryViewState {
  filters: SmartFilterRules;
  offset: number;
  searchTerm: string;
  sort: SortOrder;
  status: StatusFilter;
  type: TypeFilter;
  view: ViewMode;
}

const VIEW_KEY = "acbooks-library-view";
const SCROLL_KEY = "acbooks-library-scroll";
const DESCRIPTION_PREFIX = "acbooks-description-expanded:";

export const DEFAULT_LIBRARY_VIEW: LibraryViewState = {
  filters: EMPTY_FILTER_RULES,
  offset: 0,
  searchTerm: "",
  sort: "recent",
  status: "all",
  type: "all",
  view: "grid",
};

const TYPES: readonly TypeFilter[] = ["all", "digital", "physical"];
const STATUSES: readonly StatusFilter[] = ["all", "done", "reading", "unread"];
const SORTS: readonly SortOrder[] = ["author", "recent", "title"];
const VIEWS: readonly ViewMode[] = ["grid", "list"];

function isOneOf<T extends string>(
  values: readonly T[],
  value: unknown,
): value is T {
  return typeof value === "string" && values.includes(value as T);
}

function readSession(key: string): string | null {
  try {
    return window.sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeSession(key: string, value: string): void {
  try {
    window.sessionStorage.setItem(key, value);
  } catch {
    return;
  }
}

function removeSession(key: string): void {
  try {
    window.sessionStorage.removeItem(key);
  } catch {
    return;
  }
}

export function readLibraryView(): LibraryViewState | null {
  const raw = readSession(VIEW_KEY);
  if (raw === null) {
    return null;
  }
  try {
    const value = JSON.parse(raw) as Partial<LibraryViewState> & {
      format?: unknown;
      tag?: unknown;
    };
    return {
      filters:
        value.filters !== undefined
          ? parseSmartFilterRules(value.filters)
          : filterRulesFromTagAndFormat(
              typeof value.tag === "string" ? value.tag : "",
              typeof value.format === "string" ? value.format : "",
            ),
      offset:
        typeof value.offset === "number" && Number.isInteger(value.offset)
          ? Math.max(0, value.offset)
          : DEFAULT_LIBRARY_VIEW.offset,
      searchTerm:
        typeof value.searchTerm === "string" ? value.searchTerm : "",
      sort: isOneOf(SORTS, value.sort) ? value.sort : DEFAULT_LIBRARY_VIEW.sort,
      status: isOneOf(STATUSES, value.status)
        ? value.status
        : DEFAULT_LIBRARY_VIEW.status,
      type: isOneOf(TYPES, value.type)
        ? value.type
        : DEFAULT_LIBRARY_VIEW.type,
      view: isOneOf(VIEWS, value.view)
        ? value.view
        : DEFAULT_LIBRARY_VIEW.view,
    };
  } catch {
    return null;
  }
}

export function readInitialLibraryView(): LibraryViewState {
  const saved = readLibraryView() ?? DEFAULT_LIBRARY_VIEW;
  const params = new URLSearchParams(window.location.search);
  const urlQuery = params.get("q");
  const mediaType = params.get("mediaType");
  const urlTag = params.get("tag");
  const urlFormat = params.get("format");
  const urlFilters = params.get("filters");
  const reset = params.get("reset") === "1";
  if (
    urlQuery === null &&
    mediaType === null &&
    urlTag === null &&
    urlFormat === null &&
    urlFilters === null &&
    !reset
  ) {
    return saved;
  }

  const view: LibraryViewState = { ...saved, offset: 0 };
  if (reset) {
    view.filters = EMPTY_FILTER_RULES;
    view.searchTerm = "";
    view.status = "all";
    view.type = "all";
  }
  if (mediaType === "physical" || mediaType === "digital") {
    view.type = mediaType;
  } else if (mediaType !== null) {
    view.type = "all";
  }
  if (urlFilters !== null) {
    view.filters = parseSmartFilterRules(urlFilters);
  } else if (urlTag !== null || urlFormat !== null) {
    view.filters = filterRulesFromTagAndFormat(
      urlTag?.trim() ?? "",
      urlFormat?.trim() ?? "",
    );
  }
  if (urlQuery !== null) {
    view.filters = EMPTY_FILTER_RULES;
    view.searchTerm = urlQuery.trim();
    view.status = "all";
    view.type = "all";
  }
  return view;
}

export function saveLibraryView(view: LibraryViewState): void {
  writeSession(VIEW_KEY, JSON.stringify(view));
}

export function saveLibraryScroll(scrollTop: number): void {
  writeSession(SCROLL_KEY, String(Math.max(0, scrollTop)));
}

export function takeLibraryScroll(): number {
  const raw = readSession(SCROLL_KEY);
  if (raw === null) {
    return 0;
  }
  removeSession(SCROLL_KEY);
  const value = Number(raw);
  return Number.isFinite(value) ? Math.max(0, value) : 0;
}

export function readDescriptionExpanded(bookId: string): boolean {
  return readSession(`${DESCRIPTION_PREFIX}${bookId}`) === "true";
}

export function saveDescriptionExpanded(
  bookId: string,
  expanded: boolean,
): void {
  writeSession(`${DESCRIPTION_PREFIX}${bookId}`, String(expanded));
}

export function clearLibrarySessionState(): void {
  removeSession(VIEW_KEY);
  removeSession(SCROLL_KEY);
  const keys: string[] = [];
  try {
    for (let index = 0; index < window.sessionStorage.length; index += 1) {
      const key = window.sessionStorage.key(index);
      if (key !== null && key.startsWith(DESCRIPTION_PREFIX)) {
        keys.push(key);
      }
    }
  } catch {
    return;
  }
  for (const key of keys) {
    removeSession(key);
  }
}
