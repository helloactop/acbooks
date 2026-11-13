import type { components } from "../../../../packages/acbooks-api-client/schema";

import {
  ApiRequestError,
  apiRequest,
  deleteWithCsrf,
  getCsrfToken,
} from "../api/client";

export type BookFile = components["schemas"]["BookFile"];

export function listBookFiles(
  bookId: string,
  signal?: AbortSignal,
): Promise<BookFile[]> {
  return apiRequest<BookFile[]>(
    `/books/${encodeURIComponent(bookId)}/files`,
    { signal },
  );
}

export function bookFileContentUrl(fileId: string): string {
  return `/api/v1/files/${encodeURIComponent(fileId)}/content`;
}

export function deleteBookFile(fileId: string): Promise<void> {
  return deleteWithCsrf<void>(`/files/${encodeURIComponent(fileId)}`);
}

function parseProblem(
  text: string,
): { code?: string; requestId?: string } | null {
  try {
    return JSON.parse(text) as { code?: string; requestId?: string };
  } catch {
    return null;
  }
}

function problemFromXhr(request: XMLHttpRequest): ApiRequestError {
  const problem = parseProblem(request.responseText);
  return new ApiRequestError(
    problem?.code ?? "UNKNOWN",
    request.status,
    problem?.requestId,
  );
}

export function uploadBookFile(
  bookId: string,
  file: File,
  onProgress?: (loaded: number, total: number) => void,
): Promise<BookFile> {
  return getCsrfToken().then(
    (token) =>
      new Promise<BookFile>((resolve, reject) => {
        const request = new XMLHttpRequest();
        request.open("POST", "/api/v1/uploads");
        request.withCredentials = true;
        request.setRequestHeader("X-CSRF-Token", token);
        request.upload.onprogress = (event) => {
          if (event.lengthComputable) {
            onProgress?.(event.loaded, event.total);
          }
        };
        request.onload = () => {
          if (request.status >= 200 && request.status < 300) {
            try {
              resolve(JSON.parse(request.responseText) as BookFile);
            } catch {
              reject(new ApiRequestError("UNKNOWN", request.status));
            }
            return;
          }
          reject(problemFromXhr(request));
        };
        request.onerror = () => reject(new ApiRequestError("NETWORK_ERROR"));
        request.onabort = () => reject(new ApiRequestError("NETWORK_ERROR"));
        const body = new FormData();
        body.append("bookId", bookId);
        body.append("file", file, file.name);
        request.send(body);
      }),
  );
}