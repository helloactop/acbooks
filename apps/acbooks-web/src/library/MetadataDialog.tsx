import { Button, Input, Spinner } from "@acbooks/ui";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import type { components } from "../../../../packages/acbooks-api-client/schema";
import { apiRequest, postWithCsrf, toApiError } from "../api/client";
import { useAppShell } from "../layout/app-shell-context";
import { getJob } from "../storage/storage-api";
import { Modal } from "./Modal";

type Candidate = components["schemas"]["Candidate"];
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
                setCandidates(await apiRequest<Candidate[]>(`/metadata/candidates?q=${encodeURIComponent(query.trim())}`));
              });
            }}>
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
              <div className="rounded-card border border-border p-3" key={index}>
                <h3 className="font-semibold">{candidate.title}</h3>
                <p className="text-sm">{candidate.authors?.join(" · ")}</p>
                {candidate.isbn ? <p className="text-sm">ISBN {candidate.isbn}</p> : null}
                <p className="text-xs text-muted-foreground">Open Library</p>
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
