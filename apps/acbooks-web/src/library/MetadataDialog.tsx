import { Button, Input, Select, Spinner } from "@acbooks/ui";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import type { components } from "../../../../packages/acbooks-api-client/schema";
import { postWithCsrf, toApiError } from "../api/client";
import { useAppShell } from "../layout/app-shell-context";
import { getJob } from "../storage/storage-api";
import { searchMetadata, type Candidate, type MetadataSource } from "./metadata-api";
import { Modal } from "./Modal";

type Job = components["schemas"]["JobStatus"];
type BatchItem = { bookId: string; status: string; fields?: string[] };

type Props = {
  onClose: () => void;
  onApplied: () => void;
} & ({ bookIds: string[]; title?: never; isbn?: never; bookId?: never }
  | { bookId: string; title: string; isbn: string | null; bookIds?: never });

export function MetadataDialog(props: Props) {
  const { t } = useTranslation("library");
  const { expireSession } = useAppShell();
  const [query, setQuery] = useState(props.isbn ?? props.title ?? "");
  const [source, setSource] = useState<MetadataSource>("douban");
  const [candidates, setCandidates] = useState<Candidate[]>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(false);
  const [fields, setFields] = useState<string[]>();
  const [jobId, setJobId] = useState<string>();
  const [job, setJob] = useState<Job>();
  const [pollAttempt, setPollAttempt] = useState(0);

  const fail = (cause: unknown) => {
    if (toApiError(cause).status === 401) expireSession();
    else setError(true);
  };

  useEffect(() => {
    if (jobId === undefined) return;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    const poll = () => {
      getJob(jobId, controller.signal).then((value) => {
        if (controller.signal.aborted) return;
        setJob(value);
        if (value.status === "queued" || value.status === "running") timer = setTimeout(poll, 1200);
      }).catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        if (toApiError(cause).status === 401) expireSession();
        else setError(true);
      });
    };
    poll();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [expireSession, jobId, pollAttempt]);

  const perform = async (operation: () => Promise<void>) => {
    if (busy) return;
    setBusy(true);
    setError(false);
    try { await operation(); } catch (cause) { fail(cause); }
    finally { setBusy(false); }
  };

  const done = job?.status === "succeeded";
  const items = done ? job.result?.items as BatchItem[] | undefined : undefined;

  return (
    <Modal labelledBy="metadata-title" onClose={props.onClose} size="form">
      <div className="flex max-h-[80dvh] flex-col gap-4 overflow-y-auto p-4">
        <h2 className="text-lg font-semibold" id="metadata-title">
          {t(props.bookIds ? "metadata.batch" : "metadata.search")}
        </h2>
        <p className="text-sm text-muted-foreground">{t("metadata.policy")}</p>
        {error || job?.status === "failed" ? (
          <p className="text-sm text-destructive-text" role="alert">{t("metadata.error")}</p>
        ) : null}
        {props.bookIds ? (
          <>
            {jobId === undefined ? (
              <Button disabled={busy || props.bookIds.length > 20} onClick={() => void perform(async () => {
                const response = await postWithCsrf<components["schemas"]["MetadataJob"]>(
                  "/metadata/jobs", { bookIds: props.bookIds },
                );
                setJobId(response.jobId);
              })} variant="soft">{t("metadata.start")}</Button>
            ) : null}
            {props.bookIds.length > 20 ? <p>{t("metadata.limit")}</p> : null}
            {error && jobId !== undefined ? (
              <Button onClick={() => { setError(false); setPollAttempt(pollAttempt + 1); }}>
                {t("errors.retry")}
              </Button>
            ) : null}
            {jobId !== undefined && job?.status !== "failed" && !done && !error ? (
              <p role="status">{t("metadata.pending")}</p>
            ) : null}
            {items !== undefined ? (
              <div role="status">
                <p>{t("metadata.finished")}</p>
                <ul>{["filled", "unchanged", "review", "missing", "failed"].map((status) => (
                  <li key={status}>{t(`metadata.status.${status}`)} · {items.filter((item) => item.status === status).length}</li>
                ))}</ul>
              </div>
            ) : null}
          </>
        ) : (
          <>
            <form className="flex flex-wrap gap-2" onSubmit={(event) => {
              event.preventDefault();
              void perform(async () => {
                setFields(undefined);
                setCandidates(undefined);
                setCandidates(await searchMetadata(query, source));
              });
            }}>
              <Select ariaLabel={t("metadata.source")} disabled={busy} value={source}
                onValueChange={(value) => {
                  setSource(value === "openlibrary" ? "openlibrary" : "douban");
                  setCandidates(undefined);
                  setFields(undefined);
                  setError(false);
                }}
                options={[
                  { value: "douban", label: t("metadata.douban") },
                  { value: "openlibrary", label: "Open Library" },
                ]} />
              <Input aria-label={t("metadata.query")} className="min-w-0 flex-1" maxLength={200}
                onChange={(event) => setQuery(event.target.value)} value={query} />
              <Button disabled={busy || query.trim().length === 0} type="submit">{t("metadata.search")}</Button>
            </form>
            {fields !== undefined ? (
              <p role="status">{fields.length > 0
                ? fields.map((field) => t(`metadata.fields.${field}`)).join(" · ")
                : t("metadata.status.unchanged")}</p>
            ) : null}
            {candidates?.length === 0 ? <p>{t("metadata.empty")}</p> : null}
            {candidates?.map((candidate, index) => (
              <div className="flex flex-col items-start gap-1 rounded-card border border-border p-3"
                key={candidate.doubanId ?? candidate.sourceKey ?? index}>
                <h3 className="font-semibold">{candidate.title}</h3>
                {candidate.authors?.length ? <p className="text-sm">{candidate.authors.join(" · ")}</p> : null}
                {candidate.translators?.length ? (
                  <p className="text-sm">{t("metadata.translatedBy", { names: candidate.translators.join(" · ") })}</p>
                ) : null}
                {candidate.publisher || candidate.publicationDate || candidate.rating ? (
                  <p className="text-sm">{[
                    candidate.publisher, candidate.publicationDate,
                    candidate.rating ? t("metadata.rating", { score: candidate.rating }) : null,
                  ].filter(Boolean).join(" · ")}</p>
                ) : null}
                {candidate.isbn ? <p className="text-sm">ISBN {candidate.isbn}</p> : null}
                {candidate.description ? (
                  <p className="line-clamp-3 whitespace-pre-line text-sm text-muted-foreground">{candidate.description}</p>
                ) : null}
                {candidate.tags?.length ? (
                  <p className="text-xs text-muted-foreground">{candidate.tags.join(" · ")}</p>
                ) : null}
                {candidate.doubanId ? (
                  <a className="text-xs text-accent-text underline" target="_blank" rel="noreferrer"
                    href={`https://book.douban.com/subject/${encodeURIComponent(candidate.doubanId)}/`}>
                    {t("metadata.douban")}
                  </a>
                ) : <p className="text-xs text-muted-foreground">Open Library</p>}
                <Button disabled={busy} onClick={() => void perform(async () => {
                  const result = await postWithCsrf<components["schemas"]["AppliedMetadata"]>(
                    `/books/${encodeURIComponent(props.bookId)}/metadata`, candidate,
                  );
                  setFields(result.fields);
                  props.onApplied();
                })} size="sm" variant="outline">{t("metadata.apply")}</Button>
              </div>
            ))}
          </>
        )}
        {busy ? <Spinner /> : null}
        <Button className="self-end" onClick={props.onClose} variant="ghost">{t("metadata.close")}</Button>
      </div>
    </Modal>
  );
}
