import type { TFunction } from "i18next";

import type { AuthError } from "./auth-api";

export function authErrorMessage(
  t: TFunction<"auth">,
  error: AuthError,
): string {
  switch (error.code) {
    case "INVALID_CREDENTIALS":
      return t("errors.invalidCredentials");
    case "SETUP_CODE_INVALID":
      return t("errors.setupCodeInvalid");
    case "SETUP_ALREADY_INITIALIZED":
      return t("errors.setupAlreadyInitialized");
    case "RATE_LIMITED":
      return error.retryAfter === undefined
        ? t("errors.rateLimited")
        : t("errors.rateLimitedWithSeconds", { seconds: error.retryAfter });
    case "VALIDATION_ERROR":
      return t("errors.validation");
    case "FORBIDDEN":
      return t("errors.forbidden");
    case "UNAUTHORIZED":
      return t("errors.unauthorized");
    case "NETWORK_ERROR":
      return t("errors.network");
    case "INTERNAL_ERROR":
      return t("errors.server");
    default:
      return t("errors.unknown");
  }
}

export function showRequestId(error: AuthError): string | undefined {
  return error.code === "INTERNAL_ERROR" || error.code === "UNKNOWN"
    ? error.requestId
    : undefined;
}
