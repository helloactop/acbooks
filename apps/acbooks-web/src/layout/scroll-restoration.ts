const POSITION_PREFIX = "acbooks-scroll:";
const RETURN_KEY = "acbooks-scroll-return";

export interface ScrollLocation {
  key: string;
  pathname: string;
  search: string;
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

function toScrollTop(value: string | null): number {
  if (value === null) {
    return 0;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(0, Math.floor(parsed)) : 0;
}

function positionKey(location: ScrollLocation): string {
  // Memory routers reuse the "default" key, so fall back to the URL for those entries.
  return location.key !== "default"
    ? `${POSITION_PREFIX}${location.key}`
    : `${POSITION_PREFIX}${location.pathname}${location.search}`;
}

export function saveScrollPosition(
  location: ScrollLocation,
  scrollTop: number,
): void {
  writeSession(positionKey(location), String(Math.max(0, Math.floor(scrollTop))));
}

export function readScrollPosition(location: ScrollLocation): number {
  return toScrollTop(readSession(positionKey(location)));
}

export function rememberReturnScroll(scrollTop: number): void {
  writeSession(RETURN_KEY, String(Math.max(0, Math.floor(scrollTop))));
}

export function takeReturnScroll(): number {
  const value = toScrollTop(readSession(RETURN_KEY));
  removeSession(RETURN_KEY);
  return value;
}

export function clearScrollRestoration(): void {
  removeSession(RETURN_KEY);
  const keys: string[] = [];
  try {
    for (let index = 0; index < window.sessionStorage.length; index += 1) {
      const key = window.sessionStorage.key(index);
      if (key !== null && key.startsWith(POSITION_PREFIX)) {
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
