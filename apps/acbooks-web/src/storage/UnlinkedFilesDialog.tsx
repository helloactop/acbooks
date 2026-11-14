import { Button, Spinner } from "@acbooks/ui";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { toApiError } from "../api/client";
import { useAppShell } from "../layout/app-shell-context";
import { bookDetailHref } from "../layout/routes";
import { Modal } from "../library/Modal";
import {
  listUnlinkedFiles,
  previewUnlinkedFile,
  resolveUnlinkedFile,
  type PendingFilePreview,
  type ResolvedFile,
} from "./storage-api";

export function UnlinkedFilesDialog({ rootId, onClose }: {
  rootId: string;
  onClose: () => void;
}) {
  const { t } = useTranslation("storage");
  const { expireSession } = useAppShell();
  const [page, setPage] = useState<Awaited<ReturnType<typeof listUnlinkedFiles>>>();
  const [offset, setOffset] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const [selected, setSelected] = useState<string>();
  const [preview, setPreview] = useState<PendingFilePreview>();
  const [resolved, setResolved] = useState<ResolvedFile>();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();

  useEffect(() => {
    const controller = new AbortController();
    const fail = (cause: unknown) => {
      if (controller.signal.aborted) return;
      const problem = toApiError(cause);
      if (problem.status === 401) expireSession();
      else setError(problem.code);
    };
    if (selected === undefined) {
      listUnlinkedFiles(rootId, offset, controller.signal)
        .then((result) => {
          if (controller.signal.aborted) return;
          if (result.items.length === 0 && offset > 0) {
            setOffset(Math.max(0, offset - result.limit));
          } else {
            setPage(result);
          }
        })
        .catch(fail);
    } else {
      previewUnlinkedFile(selected, controller.signal)
        .then((result) => {
          if (!controller.signal.aborted) setPreview(result);
        })
        .catch(fail);
    }
    return () => controller.abort();
  }, [attempt, expireSession, offset, rootId, selected]);

  const showList = () => {
    setSelected(undefined);
    setPreview(undefined);
    setResolved(undefined);
    setPage(undefined);
    setError(undefined);
  };

  const resolve = async (bookId: string | null) => {
    if (preview === undefined || saving) return;
    setSaving(true);
    setError(undefined);
    try {
      setResolved(await resolveUnlinkedFile(preview.id, bookId));
    } catch (cause) {
      const problem = toApiError(cause);
      if (problem.status === 401) expireSession();
      else setError(problem.code);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal labelledBy="unlinked-files-title" onClose={() => { if (!saving) onClose(); }} size="form">
      <div className="flex max-h-[80dvh] flex-col gap-4 overflow-y-auto p-4">
        <h2 className="text-lg font-semibold" id="unlinked-files-title">{t("imports.title")}</h2>
        {error !== undefined ? (
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-sm text-destructive-text" role="alert">
              {t(error === "IMPORT_FILE_CHANGED" ? "imports.changed" : "imports.error")}
            </p>
            <Button onClick={() => {
              setError(undefined);
              setAttempt((value) => value + 1);
            }} size="sm" variant="outline">{t("retry")}</Button>
          </div>
        ) : null}
        {resolved !== undefined ? (
          <p role="status">
            {t("imports.done")}{" "}
            <Link className="text-accent-text underline" to={bookDetailHref(resolved.bookId)}>
              {t("imports.openBook")}
            </Link>
          </p>
        ) : preview !== undefined ? (
          <>
            <div className="min-w-0 space-y-1">
              <h3 className="font-semibold wrap-anywhere">{preview.title}</h3>
              <p className="text-xs wrap-anywhere text-muted-foreground">{preview.relativePath}</p>
              {preview.authors.length > 0 ? <p className="text-sm wrap-anywhere">{preview.authors.join(" · ")}</p> : null}
              {preview.isbn !== null ? <p className="text-sm">ISBN {preview.isbn}</p> : null}
              {preview.description !== null ? (
                <p className="text-sm wrap-anywhere whitespace-pre-line">{preview.description}</p>
              ) : null}
            </div>
            <div className="space-y-2">
              <h3 className="text-sm font-semibold">{t("imports.candidates")}</h3>
              {preview.candidates.length === 0 ? <p className="text-sm">{t("imports.noCandidates")}</p> : null}
              <ul className="space-y-2">
                {preview.candidates.map((candidate) => (
                  <li className="flex flex-wrap items-center justify-between gap-2 rounded-card border border-border p-3" key={candidate.bookId}>
                    <div className="min-w-0">
                      <Link className="text-sm wrap-anywhere text-accent-text hover:underline" to={bookDetailHref(candidate.bookId)}>
                        {candidate.title}
                      </Link>
                      <p className="text-xs text-muted-foreground">{candidate.reasons.map((reason) => t(`imports.reason.${reason}`)).join(" · ")}</p>
                    </div>
                    <Button disabled={saving || error === "IMPORT_FILE_CHANGED"} onClick={() => void resolve(candidate.bookId)} size="sm" variant="outline">
                      {t("imports.attach")}
                    </Button>
                  </li>
                ))}
              </ul>
            </div>
            <Button className="self-start" disabled={saving || error === "IMPORT_FILE_CHANGED"} onClick={() => void resolve(null)} variant="soft">
              {t("imports.create")}
            </Button>
          </>
        ) : selected === undefined && page !== undefined ? (
          <>
            {page.items.length === 0 ? <p className="text-sm text-muted-foreground">{t("imports.empty")}</p> : null}
            <ul className="space-y-2">
              {page.items.map((file) => (
                <li key={file.id}>
                  <button className="w-full rounded-card border border-border p-3 text-left text-sm wrap-anywhere hover:bg-surface-hover focus-visible:ring-2 focus-visible:ring-ring" onClick={() => { setError(undefined); setSelected(file.id); }} type="button">
                    {file.relativePath}
                  </button>
                </li>
              ))}
            </ul>
            <div className="flex flex-wrap gap-2">
              {offset > 0 ? (
                <Button onClick={() => { setPage(undefined); setOffset(Math.max(0, offset - page.limit)); }} variant="outline">{t("imports.previous")}</Button>
              ) : null}
              {offset + page.limit < page.total ? (
                <Button onClick={() => { setPage(undefined); setOffset(offset + page.limit); }} variant="outline">{t("imports.next")}</Button>
              ) : null}
            </div>
          </>
        ) : error === undefined ? <Spinner label={t("loading")} /> : null}
        <div className="flex flex-wrap justify-end gap-2">
          {selected !== undefined ? <Button disabled={saving} onClick={showList} variant="ghost">{t("imports.back")}</Button> : null}
          <Button disabled={saving} onClick={onClose} variant="outline">{t("imports.close")}</Button>
        </div>
      </div>
    </Modal>
  );
}
