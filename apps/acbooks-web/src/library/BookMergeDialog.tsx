import { Button, Spinner, useLanguage } from "@acbooks/ui";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { toApiError, type ApiError } from "../api/client";
import { bookDetailHref, redirectToExpiredLogin } from "../layout/routes";
import { Modal } from "./Modal";
import { mergeBooks, previewMerge, type MergePair, type MergePreview } from "./organization-api";
import { OrganizationError } from "./OrganizationError";

type Field = MergePreview["conflicts"][number]["field"];

export function BookMergeDialog({ pair, onClose, onMerged }: {
  pair: MergePair;
  onClose: () => void;
  onMerged: (bookId: string) => void;
}) {
  const { t } = useTranslation("library");
  const { resolvedLanguage } = useLanguage();
  const [direction, setDirection] = useState(pair);
  const [preview, setPreview] = useState<MergePreview>();
  const [selected, setSelected] = useState<Set<Field>>(new Set());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError>();

  async function load(next: MergePair) {
    setBusy(true);
    setError(undefined);
    setPreview(undefined);
    setDirection(next);
    setSelected(new Set());
    try {
      setPreview(await previewMerge(next));
    } catch (cause) {
      const failure = toApiError(cause);
      if (failure.status === 401) redirectToExpiredLogin();
      setError(failure);
    } finally {
      setBusy(false);
    }
  }

  async function confirm() {
    if (!preview) return;
    setBusy(true);
    setError(undefined);
    try {
      await mergeBooks({ ...direction, revision: preview.revision, useSource: [...selected] });
      onMerged(direction.targetBookId);
    } catch (cause) {
      const failure = toApiError(cause);
      if (failure.status === 401) redirectToExpiredLogin();
      if (failure.status === 409) setPreview(undefined);
      setError(failure);
    } finally {
      setBusy(false);
    }
  }

  function format(field: Field, value: string | string[] | null) {
    if (Array.isArray(value)) return new Intl.ListFormat(resolvedLanguage).format(value) || t("organization.emptyValue");
    if (value === null) return t("organization.emptyValue");
    if (field === "cover") return t("organization.hasCover");
    if (field === "reading_status" && (value === "unread" || value === "reading" || value === "done"))
      return t(`status.${value}`);
    return value;
  }

  return (
    <Modal labelledBy="merge-books-title" onClose={() => { if (!busy) onClose(); }}>
      <div className="space-y-4 overflow-y-auto p-5">
        <h2 id="merge-books-title" className="text-lg font-semibold">{t("organization.mergeBooks")}</h2>
        {error && <OrganizationError error={error} />}
        {preview ? (
          <>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="min-w-0 break-words">
                <p>{t("organization.source")} <Link className="underline" to={bookDetailHref(preview.source.id)}>{preview.source.title}</Link></p>
                <p>{t("organization.target")} <Link className="underline" to={bookDetailHref(preview.target.id)}>{preview.target.title}</Link></p>
              </div>
              <Button disabled={busy} variant="outline" onClick={() => {
                void load({ sourceBookId: direction.targetBookId, targetBookId: direction.sourceBookId });
              }}>{t("organization.swap")}</Button>
            </div>
            <p className="text-sm text-muted-foreground">{t("organization.impact", {
              files: preview.fileCount, copies: preview.copyCount, reading: preview.readingCount,
            })}</p>
            <p className="text-sm text-muted-foreground">{t("organization.mergeWarning")}</p>
            {preview.conflicts.map(({ field, source, target }) => (
              <fieldset className="min-w-0 rounded-control border border-border p-2" key={field} disabled={busy}>
                <legend className="px-1 text-sm font-medium">{t(`organization.fields.${field}`)}</legend>
                <div className="grid gap-2 sm:grid-cols-2">
                  {([false, true] as const).map((useSource) => (
                    <label className="flex min-w-0 items-start gap-2 text-sm" key={String(useSource)}>
                      <input className="mt-1" type="radio" name={field} checked={selected.has(field) === useSource}
                        onChange={() => setSelected((current) => {
                          const next = new Set(current);
                          if (useSource) next.add(field);
                          else next.delete(field);
                          return next;
                        })} />
                      <span className="min-w-0 break-words">
                        <span className="block text-xs text-muted-foreground">{t(useSource ? "organization.source" : "organization.target")}</span>
                        {field === "cover" && (useSource ? source : target) ? <img className="h-20 rounded-control" alt={t("organization.hasCover")}
                          src={`/api/v1/books/${useSource ? preview.source.id : preview.target.id}/cover`} /> :
                          format(field, useSource ? source : target)}
                      </span>
                    </label>
                  ))}
                </div>
              </fieldset>
            ))}
          </>
        ) : <p className="text-sm text-muted-foreground">{t("organization.previewHint")}</p>}
        <div className="flex flex-wrap items-center justify-end gap-2">
          {busy && <Spinner />}
          <Button disabled={busy} variant="ghost" onClick={onClose}>{t("form.cancel")}</Button>
          {preview ? <Button disabled={busy} variant="danger-confirm" onClick={() => void confirm()}>{t("organization.mergeBooks")}</Button> :
            <Button disabled={busy} variant="soft" onClick={() => void load(direction)}>{t("organization.preview")}</Button>}
        </div>
      </div>
    </Modal>
  );
}
