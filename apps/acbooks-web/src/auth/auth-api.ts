import type { components } from "../../../../packages/acbooks-api-client/schema";

import {
  apiRequest,
  ApiRequestError,
  postWithCsrf,
  toApiError,
  type ApiError,
} from "../api/client";

export type Account = components["schemas"]["AccountResponse"];
export type CsrfResponse = components["schemas"]["CsrfResponse"];
export type LoginRequest = components["schemas"]["LoginRequest"];
export type LoginResponse = components["schemas"]["LoginResponse"];
export type SetupRequest = components["schemas"]["SetupRequest"];
export type SetupStatus = components["schemas"]["SetupStatus"];

export type AuthError = ApiError;
export { ApiRequestError as AuthApiError, toApiError as toAuthError };

export function getSetupStatus(): Promise<SetupStatus> {
  return apiRequest<SetupStatus>("/setup/status");
}

export async function getCurrentAccount(): Promise<Account | null> {
  try {
    return await apiRequest<Account>("/auth/me");
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === 401) {
      return null;
    }
    throw error;
  }
}

export function initializeSetup(body: SetupRequest): Promise<Account> {
  return postWithCsrf<Account>("/setup", body);
}

export function login(body: LoginRequest): Promise<LoginResponse> {
  return postWithCsrf<LoginResponse>("/auth/login", body);
}

export function logout(): Promise<void> {
  return postWithCsrf<void>("/auth/logout");
}
