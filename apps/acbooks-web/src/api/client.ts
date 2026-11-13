import type { operations } from "../../../../packages/acbooks-api-client/schema";

type ProblemDetails =
  operations["getSetupStatus"]["responses"][500]["content"]["application/problem+json"];

const API_BASE = "/api/v1";

export interface ApiError {
  code: string;
  requestId?: string;
  retryAfter?: number;
  status?: number;
}

export class ApiRequestError extends Error implements ApiError {
  readonly code: string;
  readonly requestId?: string;
  readonly retryAfter?: number;
  readonly status?: number;

  constructor(
    code: string,
    status?: number,
    requestId?: string,
    retryAfter?: number,
  ) {
    super(code);
    this.name = "ApiRequestError";
    this.code = code;
    this.status = status;
    this.requestId = requestId;
    this.retryAfter = retryAfter;
  }
}

export function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === "AbortError";
}

function fallbackCode(status: number): string {
  switch (status) {
    case 401:
      return "UNAUTHORIZED";
    case 403:
      return "FORBIDDEN";
    case 404:
      return "NOT_FOUND";
    case 429:
      return "RATE_LIMITED";
    case 500:
      return "INTERNAL_ERROR";
    default:
      return "UNKNOWN";
  }
}

async function problemError(response: Response): Promise<ApiRequestError> {
  let problem: ProblemDetails | null;
  try {
    problem = (await response.json()) as ProblemDetails;
  } catch {
    problem = null;
  }

  const retryHeader = response.headers.get("Retry-After");
  const retryAfter =
    retryHeader !== null && /^\d+$/.test(retryHeader)
      ? Number(retryHeader)
      : undefined;

  return new ApiRequestError(
    problem?.code ?? fallbackCode(response.status),
    response.status,
    problem?.requestId ??
      response.headers.get("X-Request-ID") ??
      undefined,
    retryAfter,
  );
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      cache: "no-store",
      credentials: "same-origin",
      ...init,
    });
  } catch (error) {
    if (isAbortError(error)) {
      throw error;
    }
    throw new ApiRequestError("NETWORK_ERROR");
  }

  if (response.status === 204) {
    return undefined as T;
  }
  if (!response.ok) {
    throw await problemError(response);
  }

  return (await response.json()) as T;
}

export async function getCsrfToken(): Promise<string> {
  const response = await apiRequest<{ csrfToken: string }>("/auth/csrf");
  return response.csrfToken;
}

export async function postWithCsrf<T>(
  path: string,
  body?: unknown,
): Promise<T> {
  const csrfToken = await getCsrfToken();
  const headers: Record<string, string> = { "X-CSRF-Token": csrfToken };
  const init: RequestInit = { method: "POST", headers };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    init.body = JSON.stringify(body);
  }
  return apiRequest<T>(path, init);
}

export async function patchWithCsrf<T>(
  path: string,
  body: unknown,
): Promise<T> {
  const csrfToken = await getCsrfToken();
  return apiRequest<T>(path, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      "X-CSRF-Token": csrfToken,
    },
    body: JSON.stringify(body),
  });
}

export async function deleteWithCsrf<T>(
  path: string,
): Promise<T> {
  const csrfToken = await getCsrfToken();
  return apiRequest<T>(path, {
    method: "DELETE",
    headers: { "X-CSRF-Token": csrfToken },
  });
}

export async function putFileWithCsrf<T>(
  path: string,
  file: File,
): Promise<T> {
  const csrfToken = await getCsrfToken();
  const body = new FormData();
  body.append("file", file, file.name);
  return apiRequest<T>(path, {
    method: "PUT",
    headers: { "X-CSRF-Token": csrfToken },
    body,
  });
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiRequestError) {
    return error;
  }
  return { code: "UNKNOWN" };
}

export function showRequestId(error: ApiError): string | undefined {
  return error.code === "INTERNAL_ERROR" || error.code === "UNKNOWN"
    ? error.requestId
    : undefined;
}
