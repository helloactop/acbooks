import { Button, Input, Select, Spinner, buttonVariants } from "@acbooks/ui";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { isAbortError, toApiError, type ApiError } from "../api/client";
import { PageContainer } from "../layout/PageContainer";
import { PageHeader } from "../layout/PageHeader";
import { bookDetailHref, redirectToExpiredLogin } from "../layout/routes";
import { useDocumentTitle } from "../layout/use-document-title";
import { BookMergeDialog } from "./BookMergeDialog";
import { Modal } from "./Modal";
import { OrganizationError } from "./OrganizationError";
import { Pagination } from "./Pagination";
import {
  deleteEntry, listDuplicates, listEntries, mergeEntries, ORGANIZATION_PAGE_SIZE,
  renameEntry, type DuplicatePage, type MergePair, type OrganizationEntry, type OrganizationKind,
} from "./organization-api";

const sections = ["duplicates", "tag", "shelf", "author", "translator"] as const;
type Section = (typeof sections)[number];
type EntryAction = "rename" | "merge" | "delete";

function report(cause: unknown) {
  const error = toApiError(cause);
  if (error.status === 401) redirectToExpiredLogin();
  return error;
}

function EntryDialog({ kind, entry, action, onClose, onSave }: {
  kind: OrganizationKind;
  entry: OrganizationEntry;
  action: EntryAction;
  onClose: () => void;
  onSave: () => void;
}) {
  const { t } = useTranslation("library");
  const [name, setName] = useState(entry.name);
  const [target, setTarget] = useState("");
  const [targets, setTargets] = useState<{ items: OrganizationEntry[]; total: number }>();
  const [offset, setOffset] = useState(0);
  const [retry, setRetry] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError>();

  useEffect(() => {
    if (action !== "merge") return;
    const controller = new AbortController();
    listEntries(kind, offset, controller.signal).then(setTargets).catch((cause: unknown) => {
      if (!isAbortError(cause)) setError(report(cause));
    });
    return () => controller.abort();
  }, [action, kind, offset, retry]);

  async function submit() {
    if (busy || (action === "merge" && !target) || (action === "rename" && !name.trim())) return;
    setBusy(true);
    setError(undefined);
    try {
      if (action === "rename") await renameEntry(kind, entry.id, name.trim());
      if (action === "merge") await mergeEntries(kind, entry.id, target);
      if (action === "delete") await deleteEntry(kind, entry.id);
      onSave();
    } catch (cause) {
      setError(report(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal labelledBy="entry-action-title" onClose={() => { if (!busy) onClose(); }}>
      <form className="space-y-4 overflow-y-auto p-5" onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}>
        <h2 id="entry-action-title" className="text-lg font-semibold">{t(`organization.${action}`)} · {entry.name}</h2>
        {action === "rename" && <Input aria-label={t("organization.name")} maxLength={200}
          disabled={busy} value={name} onChange={(event) => setName(event.target.value)} />}
        {action === "delete" && <p>{t("organization.deleteHint", { count: entry.bookCount })}</p>}
        {action === "merge" && (
          <>
            <p>{t("organization.entryMergeHint")}</p>
            {!targets && !error && <Spinner />}
            {!targets && error && <Button variant="outline" onClick={() => {
              setError(undefined);
              setRetry((value) => value + 1);
            }}>{t("organization.retry")}</Button>}
            {targets && (
              <>
                <fieldset className="divide-y divide-border" disabled={busy}>
                  <legend className="sr-only">{t("organization.name")}</legend>
                  {targets.items.filter((item) => item.id !== entry.id).map((item) => (
                    <label className="flex min-h-11 items-center gap-2 break-words" key={item.id}>
                      <input type="radio" name="entryTarget" checked={target === item.id}
                        onChange={() => setTarget(item.id)} /> <span className="min-w-0">{item.name}</span>
                    </label>
                  ))}
                </fieldset>
                {targets.total <= 1 && <p>{t("organization.noTargets")}</p>}
                <Pagination limit={ORGANIZATION_PAGE_SIZE} offset={offset} total={targets.total}
                  disabled={busy} onOffsetChange={(value) => { setTargets(undefined); setError(undefined); setTarget(""); setOffset(value); }} />
              </>
            )}
          </>
        )}
        {error && <OrganizationError error={error} />}
        <div className="flex flex-wrap justify-end gap-2">
          <Button disabled={busy} variant="ghost" onClick={onClose}>{t("form.cancel")}</Button>
          <Button type="submit" loading={busy} variant={action === "delete" ? "danger-confirm" : "soft"}
            disabled={(action === "merge" && !target) || (action === "rename" && !name.trim())}>
            {t(`organization.${action}`)}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

type Results = { kind: "duplicates"; page: DuplicatePage } |
  { kind: OrganizationKind; page: { items: OrganizationEntry[]; total: number } };

export function OrganizationPage() {
  const { t } = useTranslation("library");
  const [section, setSection] = useState<Section>("duplicates");
  const [offset, setOffset] = useState(0);
  const [revision, setRevision] = useState(0);
  const [results, setResults] = useState<Results>();
  const [error, setError] = useState<ApiError>();
  const [entryAction, setEntryAction] = useState<{ entry: OrganizationEntry; action: EntryAction }>();
  const [pair, setPair] = useState<MergePair>();
  const [merged, setMerged] = useState("");
  useDocumentTitle(t("organization.title"));

  useEffect(() => {
    const controller = new AbortController();
    const request: Promise<Results> = section === "duplicates"
      ? listDuplicates(offset, controller.signal).then((page) => ({ kind: section, page }))
      : listEntries(section, offset, controller.signal).then((page) => ({ kind: section, page }));
    request.then(setResults).catch((cause: unknown) => {
      if (!isAbortError(cause)) setError(report(cause));
    });
    return () => controller.abort();
  }, [section, offset, revision]);

  function reset(nextOffset: number) {
    setResults(undefined);
    setError(undefined);
    setOffset(nextOffset);
    setRevision((value) => value + 1);
  }

  return (
    <PageContainer>
      <PageHeader title={t("organization.title")} />
      <div className="flex flex-wrap gap-2" aria-label={t("organization.title")} role="group">
        {sections.map((kind) => (
          <Button key={kind} aria-pressed={section === kind} variant={section === kind ? "soft" : "outline"}
            onClick={() => { setSection(kind); setMerged(""); reset(0); }}>
            {t(`organization.sections.${kind}`)}
          </Button>
        ))}
      </div>
      {section === "duplicates" && (
        <p className="text-sm text-muted-foreground">{t("organization.duplicatesHint")}{" "}
          <Link className="text-accent-text underline" to="/library">{t("organization.openLibrary")}</Link>
        </p>
      )}
      {merged && <p role="status">{t("organization.mergeSuccess")}{" "}
        <Link className="text-accent-text underline" to={bookDetailHref(merged)}>{t("organization.openBook")}</Link>
      </p>}
      {error && <><OrganizationError error={error} /><Button className="self-start" variant="outline" onClick={() => reset(offset)}>{t("organization.retry")}</Button></>}
      {!error && !results && <Spinner />}
      {results && results.page.items.length === 0 && <p>{t("organization.noResults")}</p>}
      {results?.kind === "duplicates" && (
        <ul className="divide-y divide-border">
          {results.page.items.map(({ source, target, reasons }) => (
            <li key={`${source.id}/${target.id}`} className="flex flex-wrap items-center gap-3 py-3">
              <div className="grid min-w-0 flex-1 gap-1 break-words sm:grid-cols-2">
                {[source, target].map((book) => <Link key={book.id} className="min-w-0 hover:underline" to={bookDetailHref(book.id)}>
                  {book.title}<span className="block text-sm text-muted-foreground">{book.authors.join(", ")}</span>
                </Link>)}
                <span className="text-xs text-muted-foreground sm:col-span-2">
                  {reasons.map((reason) => t(`organization.reasons.${reason}`)).join(" · ")}
                </span>
              </div>
              <Button variant="outline" onClick={() => { setMerged(""); setPair({ sourceBookId: source.id, targetBookId: target.id }); }}>
                {t("organization.mergeBooks")}
              </Button>
            </li>
          ))}
        </ul>
      )}
      {results && results.kind !== "duplicates" && (
        <ul className="divide-y divide-border">
          {results.page.items.map((entry) => {
            const filters = JSON.stringify({
              combinator: "ALL",
              conditions: [{ field: results.kind, operator: "equals", value: entry.name }],
            });
            return (
              <li className="flex flex-wrap items-center gap-2 py-3" key={entry.id}>
                <div className="min-w-0 flex-1 break-words">
                  <p className="font-medium">{entry.name}</p>
                  <p className="text-sm text-muted-foreground">{t("organization.bookCount", { count: entry.bookCount })}</p>
                </div>
                <Link className={buttonVariants({ variant: "outline", size: "sm" })}
                  to={`/library?filters=${encodeURIComponent(filters)}`}>{t("organization.viewBooks")}</Link>
                <Select ariaLabel={`${entry.name} · ${t("organization.actions")}`} value=""
                  onValueChange={(value) => {
                    if (value === "rename" || value === "merge" || value === "delete") setEntryAction({ entry, action: value });
                  }}
                  options={(["rename", "merge", "delete"] as const).map((value) => ({
                    value, label: t(`organization.${value}`),
                  }))}
                  placeholder={t("organization.actions")} />
              </li>
            );
          })}
        </ul>
      )}
      {results && <Pagination limit={ORGANIZATION_PAGE_SIZE} offset={offset} total={results.page.total} onOffsetChange={reset} />}
      {entryAction && section !== "duplicates" && <EntryDialog kind={section} {...entryAction}
        onClose={() => setEntryAction(undefined)}
        onSave={() => { setEntryAction(undefined); reset(0); }} />}
      {pair && <BookMergeDialog pair={pair} onClose={() => setPair(undefined)}
        onMerged={(id) => { setPair(undefined); setMerged(id); reset(0); }} />}
    </PageContainer>
  );
}
