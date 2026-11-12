const DETAIL_RETURN_KEY = "acbooks-detail-return";
const SETTINGS_RETURN_KEY = "acbooks-settings-return";

export function redirectToLogin(): void {
  window.location.replace("/");
}

export function redirectToExpiredLogin(): void {
  window.location.replace("/?expired=1");
}

export function redirectToLibrary(): void {
  window.location.assign("/library?mediaType=all");
}

export function redirectToHome(): void {
  window.location.assign("/home");
}

export function homeHref(): string {
  return "/home";
}

export function libraryHref(query?: string): string {
  const trimmed = query?.trim() ?? "";
  return trimmed.length > 0
    ? `/library?q=${encodeURIComponent(trimmed)}`
    : "/library";
}

export function libraryMediaHref(mediaType: "all" | "physical" | "digital"): string {
  return `/library?mediaType=${mediaType}&reset=1`;
}

export function libraryTagHref(tag: string): string {
  return `/library?tag=${encodeURIComponent(tag)}`;
}

export function bookDetailHref(bookId: string): string {
  return `/library/books/${encodeURIComponent(bookId)}`;
}

export function settingsHref(section?: string): string {
  const trimmed = section?.trim() ?? "";
  return trimmed.length > 0
    ? `/settings?section=${encodeURIComponent(trimmed)}`
    : "/settings";
}

function readStoredPath(key: string): string | null {
  try {
    return window.sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStoredPath(key: string, value: string): void {
  try {
    window.sessionStorage.setItem(key, value);
  } catch {
    return;
  }
}

export function isSafeBusinessPath(path: string | null): path is string {
  if (path === null || path.length === 0) {
    return false;
  }
  try {
    const url = new URL(path, window.location.origin);
    if (url.origin !== window.location.origin) {
      return false;
    }
    return (
      url.pathname === "/home" ||
      url.pathname === "/home/" ||
      url.pathname === "/library" ||
      url.pathname === "/library/" ||
      url.pathname.startsWith("/library/books/")
    );
  } catch {
    return false;
  }
}

export function rememberDetailReturn(path?: string): void {
  const candidate =
    path ?? `${window.location.pathname}${window.location.search}`;
  if (isSafeBusinessPath(candidate) && !candidate.startsWith("/library/books/")) {
    writeStoredPath(DETAIL_RETURN_KEY, candidate);
  }
}

export function detailReturnHref(): string {
  const stored = readStoredPath(DETAIL_RETURN_KEY);
  return isSafeBusinessPath(stored) ? stored : "/library";
}

export function rememberSettingsReturn(path?: string): void {
  const candidate =
    path ?? `${window.location.pathname}${window.location.search}`;
  if (isSafeBusinessPath(candidate)) {
    writeStoredPath(SETTINGS_RETURN_KEY, candidate);
  }
}

export function settingsReturnHref(): string {
  const stored = readStoredPath(SETTINGS_RETURN_KEY);
  return isSafeBusinessPath(stored) ? stored : homeHref();
}

export function isDetailReturnFromHome(): boolean {
  return detailReturnHref().startsWith("/home");
}
