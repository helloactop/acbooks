import type { TFunction } from "i18next";

import { showRequestId, type ApiError } from "../api/client";

export function libraryErrorMessage(
  t: TFunction<"library">,
  error: ApiError,
): string {
  switch (error.code) {
    case "BOOK_NOT_FOUND":
      return t("errors.notFound");
    case "COPY_NOT_FOUND":
      return t("errors.copyNotFound");
    case "LOCATION_NOT_FOUND":
      return t("errors.locationNotFound");
    case "LOCATION_HAS_CHILDREN":
      return t("errors.locationHasChildren");
    case "LOCATION_CYCLE":
      return t("errors.locationCycle");
    case "FILE_NOT_FOUND":
      return t("errors.fileNotFound");
    case "FILE_FORMAT_UNSUPPORTED":
      return t("errors.fileFormat");
    case "FILE_TOO_LARGE":
      return t("errors.fileTooLarge");
    case "FILE_DUPLICATE":
      return t("errors.fileDuplicate");
    case "FILE_EXTERNAL_PROTECTED":
      return t("errors.fileExternal");
    case "STORAGE_ROOT_INVALID":
      return t("errors.storageInvalid");
    case "STORAGE_ROOT_OVERLAP":
      return t("errors.storageOverlap");
    case "STORAGE_ROOT_IN_USE":
      return t("errors.storageInUse");
    case "STORAGE_ROOT_NOT_FOUND":
      return t("errors.storageNotFound");
    case "VALIDATION_ERROR":
      return t("errors.validation");
    case "INVALID_COVER":
      return t("errors.invalidCover");
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
