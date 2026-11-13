import type { components } from "../../../../packages/acbooks-api-client/schema";

import {
  apiRequest,
  deleteWithCsrf,
  patchWithCsrf,
  postWithCsrf,
} from "../api/client";

export type BookCopy = components["schemas"]["BookCopy"];
export type BookCopyCreate = components["schemas"]["BookCopyCreate"];
export type BookCopyUpdate = components["schemas"]["BookCopyUpdate"];

export function listBookCopies(
  bookId: string,
  signal?: AbortSignal,
): Promise<BookCopy[]> {
  return apiRequest<BookCopy[]>(
    `/books/${encodeURIComponent(bookId)}/copies`,
    { signal },
  );
}

export function createBookCopy(
  bookId: string,
  body: BookCopyCreate,
): Promise<BookCopy> {
  return postWithCsrf<BookCopy>(
    `/books/${encodeURIComponent(bookId)}/copies`,
    body,
  );
}

export function updateBookCopy(
  bookId: string,
  copyId: string,
  body: BookCopyUpdate,
): Promise<BookCopy> {
  return patchWithCsrf<BookCopy>(
    `/books/${encodeURIComponent(bookId)}/copies/${encodeURIComponent(copyId)}`,
    body,
  );
}

export function deleteBookCopy(
  bookId: string,
  copyId: string,
): Promise<void> {
  return deleteWithCsrf<void>(
    `/books/${encodeURIComponent(bookId)}/copies/${encodeURIComponent(copyId)}`,
  );
}