import type { TFunction } from "i18next";

import { showRequestId, type ApiError } from "../api/client";

export function libraryErrorMessage(
  t: TFunction<"library">,
  error: ApiError,
): string {
  switch (error.code) {
    case "BOOK_NOT_FOUND":
      return t("errors.notFound");
    case "VALIDATION_ERROR":
      return t("errors.validation");
    case "FORBIDDEN":
      return t("errors.forbidden");
    case "RATE_LIMITED":
      return t("errors.rateLimited");
    case "NETWORK_ERROR":
      return t("errors.network");
    case "INTERNAL_ERROR":
      return t("errors.server");
    default:
      return t("errors.unknown");
  }
}

export function libraryRequestId(error: ApiError): string | undefined {
  return showRequestId(error);
}
