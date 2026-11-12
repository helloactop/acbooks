import type { TFunction } from "i18next";

import { showRequestId, type ApiError } from "../api/client";

export function appErrorMessage(t: TFunction<"app">, error: ApiError): string {
  let message: string;
  switch (error.code) {
    case "FORBIDDEN":
      message = t("errors.forbidden");
      break;
    case "NETWORK_ERROR":
      message = t("errors.network");
      break;
    case "INTERNAL_ERROR":
      message = t("errors.server");
      break;
    default:
      message = t("errors.unknown");
  }
  const requestId = showRequestId(error);
  return requestId === undefined
    ? message
    : `${message} ${t("errors.requestId", { requestId })}`;
}
