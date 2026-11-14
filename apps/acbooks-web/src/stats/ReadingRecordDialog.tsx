import { Button, Field, Input, Select } from "@acbooks/ui";
import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";

import { toApiError } from "../api/client";
import { useAppShell } from "../layout/app-shell-context";
import { getBooks, type BookSummary } from "../library/books-api";
import { Modal } from "../library/Modal";
import { localInput } from "./reading-dates";
import { createSession, deleteSession, updateSession, type TimelineSession } from "./stats-api";

export type RecordAction = { type: "add" } | { type: "edit" | "delete"; session: TimelineSession };

function uuid() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = ((bytes[6] ?? 0) & 15) | 64;
  bytes[8] = ((bytes[8] ?? 0) & 63) | 128;
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, "0")).join("");
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join("-");
}

let deviceId: string | undefined;
function webDeviceId() {
  if (deviceId) return deviceId;
  const key = "acbooks.reader.deviceId";
  try {
    deviceId = localStorage.getItem(key) || uuid();
    localStorage.setItem(key, deviceId);
  } catch {
    deviceId ??= uuid();
  }
  return deviceId;
}

function BookPicker({ value, onChange, disabled }: {
  value: string; onChange: (value: string) => void; disabled: boolean;
}) {
  const { t } = useTranslation("stats");
  const { expireSession } = useAppShell();
  const [query, setQuery] = useState("");
  const [result, setResult] = useState<{ query: string; items?: BookSummary[] }>();
  useEffect(() => {
    const abort = new AbortController();
    const timer = window.setTimeout(() => {
      void getBooks({ q: query.trim() || undefined, limit: 20 }, abort.signal).then(page => {
        if (!abort.signal.aborted) setResult({ query, items: page.items });
      }).catch(cause => {
        if (abort.signal.aborted) return;
        if (toApiError(cause).status === 401) expireSession();
        else setResult({ query });
      });
    }, 250);
    return () => { window.clearTimeout(timer); abort.abort(); };
  }, [expireSession, query]);
  const items = result?.query === query ? result.items : undefined;
  return <div className="grid gap-2">
    <Field label={t("searchBook")}>
      <Input value={query} maxLength={200} disabled={disabled} onChange={event => {
        setQuery(event.target.value);
        setResult(undefined);
        onChange("");
      }} />
    </Field>
    <Field label={t("book")} required>
      <Select value={value} onValueChange={onChange} disabled={disabled || !items?.length}
        placeholder={t("chooseBook")} options={(items ?? []).map(book => ({
          value: book.id,
          label: book.authors.length ? t("bookWithAuthors", { title: book.title, authors: book.authors.join(", ") }) : book.title,
        }))} />
    </Field>
    {!items && <p role={result ? "alert" : "status"} className="text-sm text-muted-foreground">
      {t(result ? "bookSearchFailed" : "loadingBooks")}
    </p>}
    {items?.length === 0 && <p className="text-sm text-muted-foreground">{t("noBooks")}</p>}
  </div>;
}

export function ReadingRecordDialog({ action, bookId, initialDate, onClose, onSaved }: {
  action: RecordAction; bookId?: string; initialDate?: string; onClose: () => void; onSaved: () => void;
}) {
  const { t } = useTranslation("stats");
  const { expireSession } = useAppShell();
  const titleId = useId();
  const session = action.type === "add" ? undefined : action.session;
  const [defaults] = useState(() => {
    const end = Date.now();
    return {
      start: session ? localInput(session.startedAt) : initialDate ? `${initialDate}T08:00` : localInput(end - 30 * 60_000),
      end: session?.endedAt != null ? localInput(session.endedAt) : initialDate ? `${initialDate}T08:30` : localInput(end),
    };
  });
  const [start, setStart] = useState(defaults.start);
  const [end, setEnd] = useState(session?.endedAt === null ? "" : defaults.end);
  const [seconds, setSeconds] = useState(String(session?.activeSeconds ?? 1800));
  const [target, setTarget] = useState(bookId ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const mutation = useRef<{ payload: string; id: string } | undefined>(undefined);
  const removing = action.type === "delete";
  const title = t(removing ? "deleteRecord" : session ? "editRecord" : "addRecord");

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (saving) return;
    setError("");
    const startedAt = session && start === defaults.start ? session.startedAt : Date.parse(start);
    const endedAt = end === "" ? null
      : session?.endedAt != null && end === defaults.end ? session.endedAt : Date.parse(end);
    const activeSeconds = Number(seconds);
    if (!removing && (!Number.isFinite(startedAt) || startedAt < 0 || seconds === ""
      || !Number.isSafeInteger(activeSeconds) || activeSeconds < 0
      || (endedAt === null ? session?.source !== "automatic"
        : !Number.isFinite(endedAt) || endedAt < startedAt || activeSeconds * 1000 > endedAt - startedAt))) {
      setError(t("invalidRecord"));
      return;
    }
    setSaving(true);
    try {
      if (removing && session) {
        await deleteSession(session);
      } else {
        const times = { startedAt, endedAt, activeSeconds };
        if (session) {
          await updateSession(session, times);
        } else {
          const payload = JSON.stringify([target, times]);
          if (mutation.current?.payload !== payload) mutation.current = { payload, id: uuid() };
          await createSession(target, times, webDeviceId(), mutation.current.id);
        }
      }
      onSaved();
    } catch (cause) {
      if (toApiError(cause).status === 401) expireSession();
      else setError(t("recordFailed"));
    } finally {
      setSaving(false);
    }
  }

  return <Modal labelledBy={titleId} size={removing ? "confirm" : "form"} onClose={() => { if (!saving) onClose(); }}>
    <form className="space-y-4 overflow-y-auto p-5" onSubmit={event => { void submit(event); }}>
      <h2 id={titleId} className="text-lg font-semibold">{title}</h2>
      {session && <p className="break-words text-sm text-muted-foreground">{session.bookTitle}</p>}
      {removing ? <p className="text-sm text-muted-foreground">{t("deleteDescription")}</p> : <>
        {!session && !bookId && <BookPicker value={target} onChange={setTarget} disabled={saving} />}
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("startedAt")} required>
            <Input type="datetime-local" value={start} required disabled={saving} onChange={event => setStart(event.target.value)} />
          </Field>
          <Field label={t("endedAt")} required={session?.source !== "automatic"}>
            <Input type="datetime-local" value={end} required={session?.source !== "automatic"}
              disabled={saving} onChange={event => setEnd(event.target.value)} />
          </Field>
        </div>
        <Field label={t("activeSeconds")} required>
          <Input type="number" value={seconds} min={0} step={1} required disabled={saving}
            onChange={event => setSeconds(event.target.value)} />
        </Field>
      </>}
      {error && <p role="alert" className="text-sm text-destructive-text">{error}</p>}
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="ghost" disabled={saving} onClick={onClose}>{t("cancel")}</Button>
        <Button type="submit" variant={removing ? "danger-confirm" : "default"} loading={saving}
          disabled={!session && !target}>{removing ? t("confirmDelete") : t("save")}</Button>
      </div>
    </form>
  </Modal>;
}
