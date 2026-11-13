import { Button, IconButton, useLanguage } from "@acbooks/ui";
import { Pencil, Trash2 } from "lucide-react";
import { useTranslation } from "react-i18next";

import { FormatBadges } from "./BookMeta";
import type { BookDetail } from "./books-api";
import type { BookCopy } from "./copies-api";
import { formatBytes, formatDate, formatDateTime } from "./presentation";

export interface BookInfoPanelProps {
  detail: BookDetail;
  onAddCopy?: () => void;
  onDeleteCopy?: (copy: BookCopy) => void;
  onEditCopy?: (copy: BookCopy) => void;
}

export function BookInfoPanel({
  detail,
  onAddCopy,
  onDeleteCopy,
  onEditCopy,
}: BookInfoPanelProps) {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  const otherIdentifiers = detail.identifiers.filter(
    (identifier) => identifier.scheme !== "isbn",
  );

  return (
    <div className="flex flex-col gap-8">
      <div className="grid gap-8 @min-[900px]:grid-cols-2">
        <section>
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-base font-semibold">{t("detail.copies")}</h2>
            {onAddCopy !== undefined ? (
              <Button onClick={onAddCopy} size="sm" variant="outline">
                {t("copy.addAction")}
              </Button>
            ) : null}
          </div>
          {detail.copies.length === 0 ? (
            <p className="mt-3 text-sm text-muted-foreground">
              {t("detail.noCopies")}
            </p>
          ) : (
            <ul
              aria-label={t("detail.copies")}
              className="mt-3 divide-y divide-border border-y border-border"
            >
              {detail.copies.map((copy) => (
                <li className="py-3 text-sm" key={copy.id}>
                  <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
                    <span className="font-medium">
                      {copy.locationName ?? t("detail.locationUnset")}
                    </span>
                    <div className="ml-auto flex items-center gap-1">
                      {copy.acquiredAt !== null ? (
                        <span className="text-xs text-muted-foreground">
                          {formatDate(copy.acquiredAt, resolvedLanguage)}
                        </span>
                      ) : null}
                      {onEditCopy !== undefined ? (
                        <IconButton
                          label={t("copy.edit")}
                          onClick={() => {
                            onEditCopy(copy);
                          }}
                        >
                          <Pencil aria-hidden="true" />
                        </IconButton>
                      ) : null}
                      {onDeleteCopy !== undefined ? (
                        <IconButton
                          label={t("copy.delete")}
                          onClick={() => {
                            onDeleteCopy(copy);
                          }}
                        >
                          <Trash2 aria-hidden="true" />
                        </IconButton>
                      ) : null}
                    </div>
                  </div>
                  {copy.note ? (
                    <p className="mt-1 break-words text-[13px] text-muted-foreground">
                      {copy.note}
                    </p>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </section>

        <section>
          <h2 className="text-base font-semibold">{t("detail.files")}</h2>
          {detail.files.length === 0 ? (
            <p className="mt-3 text-sm text-muted-foreground">
              {t("detail.noFiles")}
            </p>
          ) : (
            <ul
              aria-label={t("detail.files")}
              className="mt-3 divide-y divide-border border-y border-border"
            >
              {detail.files.map((file, index) => (
                <li
                  className="flex flex-wrap items-center gap-x-2 gap-y-1 py-3 text-sm"
                  key={file.id}
                >
                  <span className="text-xs text-muted-foreground">
                    {t("detail.fileNumber", { number: index + 1 })}
                  </span>
                  <FormatBadges formats={[file.format]} />
                  <span
                    className={
                      file.fileStatus === "present"
                        ? "text-[13px] text-muted-foreground"
                        : "text-[13px] text-destructive-text"
                    }
                  >
                    {file.fileStatus === "present"
                      ? t("detail.filePresent")
                      : t("detail.fileMissing")}
                  </span>
                  <span className="ml-auto text-xs text-muted-foreground tabular-nums">
                    {formatBytes(file.sizeBytes, resolvedLanguage)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <section className="border-t border-border pt-6">
        <h2 className="text-base font-semibold">{t("detail.supplementary")}</h2>
        <dl className="mt-3 grid gap-x-6 gap-y-2 text-sm @min-[640px]:grid-cols-[128px_minmax(0,1fr)]">
          {otherIdentifiers.map((identifier) => (
            <div className="contents" key={`${identifier.scheme}-${identifier.value}`}>
              <dt className="text-muted-foreground">
                {identifier.scheme.toUpperCase()}
              </dt>
              <dd className="break-all">{identifier.value}</dd>
            </div>
          ))}
          <dt className="text-muted-foreground">{t("detail.createdAt")}</dt>
          <dd>{formatDateTime(detail.createdAt, resolvedLanguage)}</dd>
          <dt className="text-muted-foreground">{t("detail.updatedAt")}</dt>
          <dd>{formatDateTime(detail.updatedAt, resolvedLanguage)}</dd>
        </dl>
      </section>
    </div>
  );
}
