import type {
  components,
  operations,
} from "../../../../packages/acbooks-api-client/schema";

import {
  apiRequest,
  deleteWithCsrf,
  patchWithCsrf,
  postWithCsrf,
  putFileWithCsrf,
} from "../api/client";
import type {
  BookFilterOptionPage,
  BookFilterOptionSource,
  BookFilterSchema,
} from "./filter-model";

export type BookSummary = components["schemas"]["BookSummary"];
export type BookDetail = components["schemas"]["BookDetail"];
export type BookPage = components["schemas"]["BookPage"];
export type BookCreate = components["schemas"]["BookCreate"];
export type BookUpdate = components["schemas"]["BookUpdate"];
export type BookBatchUpdate = components["schemas"]["BookBatchUpdate"];
export type BatchResult = components["schemas"]["BatchResult"];
export type BookListQuery = NonNullable<
  operations["listBooks"]["parameters"]["query"]
>;
export type ReadingStatus = BookSummary["readingStatus"];

function appendParam(
  params: URLSearchParams,
  key: string,
  value: string | number | null | undefined,
): void {
  if (value !== undefined && value !== null) {
    params.set(key, String(value));
  }
}

export function getBooks(
  query: BookListQuery,
  signal?: AbortSignal,
): Promise<BookPage> {
  const params = new URLSearchParams();
  appendParam(params, "q", query.q);
  appendParam(params, "readingStatus", query.readingStatus);
  appendParam(params, "tag", query.tag);
  appendParam(params, "shelfId", query.shelfId);
  appendParam(params, "mediaType", query.mediaType);
  appendParam(params, "format", query.format);
  appendParam(params, "filters", query.filters);
  appendParam(params, "sort", query.sort);
  appendParam(params, "order", query.order);
  appendParam(params, "limit", query.limit);
  appendParam(params, "offset", query.offset);
  const search = params.toString();

  return apiRequest<BookPage>(`/books${search.length > 0 ? `?${search}` : ""}`, {
    signal,
  });
}

export function getBookFilterSchema(
  signal?: AbortSignal,
): Promise<BookFilterSchema> {
  return apiRequest<BookFilterSchema>("/books/filter-schema", { signal });
}

export function getBookFilterOptions(
  source: BookFilterOptionSource,
  query: string,
  signal?: AbortSignal,
  limit = 20,
): Promise<BookFilterOptionPage> {
  const params = new URLSearchParams({
    source,
    query,
    limit: String(limit),
  });
  return apiRequest<BookFilterOptionPage>(
    `/books/filter-options?${params.toString()}`,
    { signal },
  );
}

export function getBook(
  bookId: string,
  signal?: AbortSignal,
): Promise<BookDetail> {
  return apiRequest<BookDetail>(`/books/${encodeURIComponent(bookId)}`, {
    signal,
  });
}

export function createBook(body: BookCreate): Promise<BookDetail> {
  return postWithCsrf<BookDetail>("/books", body);
}

export function updateBook(
  bookId: string,
  body: BookUpdate,
): Promise<BookDetail> {
  return patchWithCsrf<BookDetail>(
    `/books/${encodeURIComponent(bookId)}`,
    body,
  );
}

export function deleteBook(bookId: string): Promise<void> {
  return deleteWithCsrf<void>(`/books/${encodeURIComponent(bookId)}`);
}

export function batchUpdateBooks(
  body: BookBatchUpdate,
): Promise<BatchResult> {
  return patchWithCsrf<BatchResult>("/books/batch-update", body);
}

export function batchDeleteBooks(bookIds: string[]): Promise<BatchResult> {
  return postWithCsrf<BatchResult>("/books/batch-delete", { bookIds });
}

export function uploadBookCover(
  bookId: string,
  file: File,
): Promise<BookDetail> {
  return putFileWithCsrf<BookDetail>(
    `/books/${encodeURIComponent(bookId)}/cover`,
    file,
  );
}

export function deleteBookCover(bookId: string): Promise<BookDetail> {
  return deleteWithCsrf<BookDetail>(
    `/books/${encodeURIComponent(bookId)}/cover`,
  );
}

export function coverUrl(
  book: Pick<BookSummary, "coverUpdatedAt" | "hasCover" | "id" | "updatedAt">,
): string | null {
  if (!book.hasCover) {
    return null;
  }
  const revision = book.coverUpdatedAt ?? book.updatedAt;
  return `/api/v1/books/${encodeURIComponent(book.id)}/cover?v=${revision}`;
}
