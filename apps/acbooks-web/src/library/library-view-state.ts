export type TypeFilter = "all" | "digital" | "physical";
export type StatusFilter = "all" | "done" | "reading" | "unread";
export type SortOrder = "author" | "recent" | "title";
export type ViewMode = "grid" | "list";

export interface LibraryViewState {
  offset: number;
  query: string;
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
  offset: 0,
  query: "",
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
    // Storage can be unavailable in private or restricted browsing modes.
  }
}

export function readLibraryView(): LibraryViewState | null {
  const raw = readSession(VIEW_KEY);
  if (raw === null) {
    return null;
  }
  try {
    const value = JSON.parse(raw) as Partial<LibraryViewState>;
    return {
      offset:
        typeof value.offset === "number" && Number.isInteger(value.offset)
          ? Math.max(0, value.offset)
          : DEFAULT_LIBRARY_VIEW.offset,
      query: typeof value.query === "string" ? value.query : "",
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
  const urlQuery = new URLSearchParams(window.location.search).get("q");
  if (urlQuery === null) {
    return saved;
  }
  return {
    ...saved,
    offset: 0,
    query: urlQuery,
    searchTerm: urlQuery.trim(),
  };
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
  try {
    window.sessionStorage.removeItem(SCROLL_KEY);
  } catch {
    // Ignore storage restrictions.
  }
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
  try {
    window.sessionStorage.removeItem(VIEW_KEY);
    window.sessionStorage.removeItem(SCROLL_KEY);
    const keys: string[] = [];
    for (let index = 0; index < window.sessionStorage.length; index += 1) {
      const key = window.sessionStorage.key(index);
      if (key !== null && key.startsWith(DESCRIPTION_PREFIX)) {
        keys.push(key);
      }
    }
    for (const key of keys) {
      window.sessionStorage.removeItem(key);
    }
  } catch {
    // Ignore storage restrictions.
  }
}
