export function redirectHome(): void {
  window.location.assign("/");
}

export function redirectToLogin(): void {
  window.location.replace("/");
}

export function redirectToExpiredLogin(): void {
  window.location.replace("/?expired=1");
}

export function bookDetailHref(bookId: string): string {
  return `/library/books/${encodeURIComponent(bookId)}`;
}

export function libraryHref(query?: string): string {
  const trimmed = query?.trim() ?? "";
  return trimmed.length > 0
    ? `/library?q=${encodeURIComponent(trimmed)}`
    : "/library";
}
