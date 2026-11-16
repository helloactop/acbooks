import type { components } from "../../../../packages/acbooks-api-client/schema";

import {
  apiRequest,
  ApiRequestError,
  deleteWithCsrf,
  postWithCsrf,
  putFileWithCsrf,
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

export function accountAvatarUrl(account: Pick<Account, "avatarUpdatedAt">): string | null {
  return account.avatarUpdatedAt == null
    ? null
    : `/api/v1/auth/me/avatar?v=${account.avatarUpdatedAt}`;
}

export function uploadAccountAvatar(file: File): Promise<Account> {
  return putFileWithCsrf<Account>("/auth/me/avatar", file);
}

export function deleteAccountAvatar(): Promise<Account> {
  return deleteWithCsrf<Account>("/auth/me/avatar");
}

export async function prepareAvatarFile(source: File): Promise<File> {
  if (!source.type.startsWith("image/")) {
    throw new Error("INVALID_IMAGE");
  }
  const bitmap = await createImageBitmap(source, { imageOrientation: "from-image" });
  try {
    const side = Math.min(bitmap.width, bitmap.height);
    if (side === 0) {
      throw new Error("INVALID_IMAGE");
    }
    const canvas = document.createElement("canvas");
    canvas.width = 512;
    canvas.height = 512;
    const context = canvas.getContext("2d");
    if (context === null) {
      throw new Error("INVALID_IMAGE");
    }
    context.drawImage(
      bitmap,
      (bitmap.width - side) / 2,
      (bitmap.height - side) / 2,
      side,
      side,
      0,
      0,
      512,
      512,
    );
    const blob = await new Promise<Blob | null>((resolve) => {
      canvas.toBlob(resolve, "image/jpeg", 0.9);
    });
    if (blob === null) {
      throw new Error("INVALID_IMAGE");
    }
    return new File([blob], "avatar.jpg", { type: "image/jpeg" });
  } finally {
    bitmap.close();
  }
}
