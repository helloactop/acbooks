import { useTranslation } from "react-i18next";
import { showRequestId, type ApiError } from "../api/client";
import { libraryErrorMessage } from "./errors";
import { LibraryAlert } from "./LibraryAlert";

export function OrganizationError({ error }: { error: ApiError }) {
  const { t } = useTranslation("library");
  const message = error.code === "ORGANIZATION_STALE" ? t("organization.stale")
    : error.code === "ORGANIZATION_CONFLICT" ? t("organization.conflict")
    : error.code === "ORGANIZATION_ENTRY_NOT_FOUND" ? t("organization.entryMissing")
    : libraryErrorMessage(t, error);
  return <LibraryAlert requestId={showRequestId(error)}>{message}</LibraryAlert>;
}
