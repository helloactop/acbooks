import { Button, Field, Input, Spinner } from "@acbooks/ui";
import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import type { components } from "../../../../packages/acbooks-api-client/schema";
import { apiRequest, deleteWithCsrf, patchWithCsrf, postWithCsrf, toApiError } from "../api/client";
import { useAppShell } from "../layout/app-shell-context";
import { Modal } from "../library/Modal";

type Token = components["schemas"]["TokenResponse"];
type Page = components["schemas"]["TokenPage"];
type Issued = components["schemas"]["IssuedToken"];

export function PasswordSettings() {
  const { t } = useTranslation("security");
  const { expireSession } = useAppShell();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    const currentPassword = fields.get("currentPassword");
    const newPassword = fields.get("newPassword");
    if (newPassword !== fields.get("confirmation")) {
      setError(t("mismatch"));
      return;
    }
    setError("");
    setBusy(true);
    try {
      await patchWithCsrf<void>("/auth/password", { currentPassword, newPassword });
      expireSession();
    } catch (cause) {
      const { code, status } = toApiError(cause);
      if (code === "INVALID_CREDENTIALS") setError(t("incorrectPassword"));
      else if (status === 401) expireSession();
      else setError(t(status === 429 ? "rateLimited" : "failed"));
    } finally {
      setBusy(false);
    }
  };
  return <form className="max-w-md space-y-4" onSubmit={event => void submit(event)}>
    <p className="text-sm text-muted-foreground">{t("passwordDescription")}</p>
    {(["currentPassword", "newPassword", "confirmation"] as const).map(name => <Field
      key={name} htmlFor={name} label={t(name)}
    >
      <Input id={name} name={name} type="password" required maxLength={1024}
        minLength={name === "currentPassword" ? 1 : 12}
        autoComplete={name === "currentPassword" ? "current-password" : "new-password"} />
    </Field>)}
    {error && <p role="alert" className="text-sm text-destructive-text">{error}</p>}
    <Button type="submit" loading={busy}>{t("savePassword")}</Button>
  </form>;
}

export function CredentialSettings({ kind }: { kind: "device" | "application" }) {
  const { t, i18n } = useTranslation("security");
  const { expireSession } = useAppShell();
  const [offset, setOffset] = useState(0);
  const [version, setVersion] = useState(0);
  const [page, setPage] = useState<Page>();
  const [loadedAt, setLoadedAt] = useState(Date.now);
  const [error, setError] = useState(false);
  const [busy, setBusy] = useState(false);
  const [issued, setIssued] = useState<Issued>();
  const [target, setTarget] = useState<Token>();
  useEffect(() => {
    const abort = new AbortController();
    void apiRequest<Page>(`/auth/tokens?kind=${kind}&offset=${offset}`, { signal: abort.signal })
      .then(result => {
        if (!abort.signal.aborted) {
          setLoadedAt(Date.now());
          setPage(result);
        }
      })
      .catch(cause => {
        if (abort.signal.aborted) return;
        if (toApiError(cause).status === 401) expireSession();
        else setError(true);
      });
    return () => abort.abort();
  }, [expireSession, kind, offset, version]);
  const reload = () => { setPage(undefined); setError(false); setVersion(n => n + 1); };
  const mutate = async (action: () => Promise<void>) => {
    setBusy(true);
    setError(false);
    try {
      await action();
      reload();
    } catch (cause) {
      if (toApiError(cause).status === 401) expireSession();
      else setError(true);
    } finally {
      setBusy(false);
    }
  };
  const create = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = event.currentTarget;
    const label = new FormData(form).get("label");
    void mutate(async () => {
      setIssued(await postWithCsrf<Issued>("/auth/tokens", { label, scope: "read" }));
      form.reset();
      setOffset(0);
    });
  };
  const date = new Intl.DateTimeFormat(i18n.resolvedLanguage, { dateStyle: "medium", timeStyle: "short" });
  return <div className="space-y-4">
    <p className="text-sm text-muted-foreground">{t(kind === "device" ? "devicesDescription" : "credentialsDescription")}</p>
    {kind === "application" && <form onSubmit={create} className="flex max-w-md flex-wrap items-end gap-2">
      <Field htmlFor="credential-label" label={t("label")}><Input id="credential-label" name="label" required maxLength={128} /></Field>
      <Button type="submit" loading={busy}>{t("issue")}</Button>
    </form>}
    {error && <div role="alert" className="flex items-center gap-2 text-destructive-text">{t("failed")} <Button variant="outline" onClick={reload}>{t("retry")}</Button></div>}
    {page ? <>
      {page.items.length === 0 && <p className="text-sm text-muted-foreground">{t("empty")}</p>}
      <ul className="divide-y divide-border rounded-lg border border-border bg-background">
        {page.items.map(item => {
          const state = item.revokedAt !== null ? "revoked" : item.expiresAt <= loadedAt ? "expired" : "active";
          return <li key={item.id} className="flex flex-wrap items-center justify-between gap-2 p-3">
            <div className="min-w-0">
              <p className="font-medium break-words">{item.label}</p>
              <p className="text-sm text-muted-foreground">{t(state)} · {t("expires", { date: date.format(item.expiresAt) })}</p>
            </div>
            {state === "active" && <Button variant="outline" onClick={() => setTarget(item)} disabled={busy}>{t("revoke")}</Button>}
          </li>;
        })}
      </ul>
      {page.total > page.limit && <div className="flex gap-2">
        <Button disabled={offset === 0} variant="outline" onClick={() => { setPage(undefined); setOffset(offset - page.limit); }}>{t("previous")}</Button>
        <Button disabled={offset + page.limit >= page.total} variant="outline" onClick={() => { setPage(undefined); setOffset(offset + page.limit); }}>{t("next")}</Button>
      </div>}
    </> : !error && <Spinner />}
    {issued && <Modal labelledBy="issued-credential-title" onClose={() => setIssued(undefined)}>
      <div className="p-4">
      <h2 id="issued-credential-title" className="mb-2 font-semibold">{t("issued")}</h2>
      <p className="mb-3 text-sm">{t("shownOnce")}</p>
      <Input aria-label={t("issued")} readOnly value={issued.token} onFocus={event => event.currentTarget.select()} />
      <div className="mt-3 flex justify-end"><Button onClick={() => setIssued(undefined)}>{t("done")}</Button></div>
      </div>
    </Modal>}
    {target && <Modal labelledBy="revoke-credential-title" size="confirm" onClose={() => { if (!busy) setTarget(undefined); }}>
      <div className="p-4">
      <h2 id="revoke-credential-title" className="font-semibold">{t("confirmRevoke")}</h2>
      <p className="my-3 break-words">{target.label}</p>
      {error && <p role="alert" className="mb-3 text-sm text-destructive-text">{t("failed")}</p>}
      <div className="flex justify-end gap-2">
        <Button disabled={busy} variant="outline" onClick={() => setTarget(undefined)}>{t("cancel")}</Button>
        <Button variant="danger-confirm" loading={busy} onClick={() => void mutate(async () => {
          await deleteWithCsrf<void>(`/auth/tokens/${target.id}`);
          setTarget(undefined);
        })}>{t("revoke")}</Button>
      </div>
      </div>
    </Modal>}
  </div>;
}
