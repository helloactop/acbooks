import {
  EMPTY_FILTER_RULES,
  type SmartFilterRules,
} from "./filter-model";
import { clearScrollRestoration } from "../layout/scroll-restoration";

export type TypeFilter = "all" | "digital" | "physical";
export type StatusFilter = "all" | "done" | "reading" | "unread";
export type SortOrder = "author" | "recent" | "title";
export type ViewMode = "grid" | "list";
export type CoverSize = "large" | "medium" | "small";

export interface LibraryViewState {
  filters: SmartFilterRules;
  offset: number;
  searchTerm: string;
  sort: SortOrder;
  status: StatusFilter;
  type: TypeFilter;
  view: ViewMode;
}

const LEGACY_VIEW_KEY = "acbooks-library-view";
const VIEW_MODE_KEY = "acbooks-library-view-mode";
const COVER_SIZE_KEY = "acbooks-library-cover-size";
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

function readLocal(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeLocal(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    return;
  }
}

export function readViewMode(): ViewMode {
  return readLocal(VIEW_MODE_KEY) === "list" ? "list" : "grid";
}

export function saveViewMode(view: ViewMode): void {
  writeLocal(VIEW_MODE_KEY, view);
}

export function readCoverSize(): CoverSize {
  const value = readLocal(COVER_SIZE_KEY);
  return value === "small" || value === "large" ? value : "medium";
}

export function saveCoverSize(size: CoverSize): void {
  writeLocal(COVER_SIZE_KEY, size);
}

export function coverSizePx(size: CoverSize): number {
  return size === "small" ? 110 : size === "large" ? 180 : 140;
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
  // Older builds kept the whole library view here; it is URL-backed now.
  removeSession(LEGACY_VIEW_KEY);
  clearScrollRestoration();
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
