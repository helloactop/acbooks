import type {
  components,
  operations,
} from "../../../../packages/acbooks-api-client/schema";

export type Account = components["schemas"]["AccountResponse"];
export type CsrfResponse = components["schemas"]["CsrfResponse"];
export type LoginRequest = components["schemas"]["LoginRequest"];
export type LoginResponse = components["schemas"]["LoginResponse"];
export type SetupRequest = components["schemas"]["SetupRequest"];
export type SetupStatus = components["schemas"]["SetupStatus"];

type ProblemDetails =
  operations["getSetupStatus"]["responses"][500]["content"]["application/problem+json"];

const API_BASE = "/api/v1";

export interface AuthError {
  code: string;
  requestId?: string;
  retryAfter?: number;
  status?: number;
}

export class AuthApiError extends Error implements AuthError {
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
    this.name = "AuthApiError";
    this.code = code;
    this.status = status;
    this.requestId = requestId;
    this.retryAfter = retryAfter;
  }
}

function fallbackCode(status: number): string {
  switch (status) {
    case 401:
      return "UNAUTHORIZED";
    case 403:
      return "FORBIDDEN";
    case 429:
      return "RATE_LIMITED";
    case 500:
      return "INTERNAL_ERROR";
    default:
      return "UNKNOWN";
  }
}

async function problemError(response: Response): Promise<AuthApiError> {
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

  return new AuthApiError(
    problem?.code ?? fallbackCode(response.status),
    response.status,
    problem?.requestId ??
      response.headers.get("X-Request-ID") ??
      undefined,
    retryAfter,
  );
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      cache: "no-store",
      credentials: "same-origin",
      ...init,
    });
  } catch {
    throw new AuthApiError("NETWORK_ERROR");
  }

  if (response.status === 204) {
    return undefined as T;
  }
  if (!response.ok) {
    throw await problemError(response);
  }

  return (await response.json()) as T;
}

export function getSetupStatus(): Promise<SetupStatus> {
  return request<SetupStatus>("/setup/status");
}

export async function getCurrentAccount(): Promise<Account | null> {
  try {
    return await request<Account>("/auth/me");
  } catch (error) {
    if (error instanceof AuthApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}

export async function getCsrfToken(): Promise<string> {
  const response = await request<CsrfResponse>("/auth/csrf");
  return response.csrfToken;
}

async function postWithCsrf<T>(path: string, body: unknown): Promise<T> {
  const csrfToken = await getCsrfToken();
  return request<T>(path, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-CSRF-Token": csrfToken,
    },
    body: JSON.stringify(body),
  });
}

export function initializeSetup(body: SetupRequest): Promise<Account> {
  return postWithCsrf<Account>("/setup", body);
}

export function login(body: LoginRequest): Promise<LoginResponse> {
  return postWithCsrf<LoginResponse>("/auth/login", body);
}

export function toAuthError(error: unknown): AuthError {
  if (error instanceof AuthApiError) {
    return error;
  }
  return { code: "UNKNOWN" };
}
