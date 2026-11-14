import { Button, Input, Spinner } from "@acbooks/ui";
import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";

import { isAbortError, toApiError, type ApiError } from "../api/client";
import { redirectToExpiredLogin } from "../layout/routes";
import { getBooks, type BookPage } from "./books-api";
import { Modal } from "./Modal";
import { Pagination } from "./Pagination";
import { OrganizationError } from "./OrganizationError";
import { ORGANIZATION_PAGE_SIZE, reassociateFile } from "./organization-api";

export function BookTargetDialog({ bookId, fileId, onClose, onChanged }: {
  bookId: string;
  fileId: string;
  onClose: () => void;
  onChanged: () => void;
}) {
  const { t } = useTranslation("library");
  const [query, setQuery] = useState("");
  const [submitted, setSubmitted] = useState<{ term: string; offset: number }>();
  const [results, setResults] = useState<BookPage>();
  const books = results?.items.filter((book) => book.id !== bookId);
  const [target, setTarget] = useState("");
  const [error, setError] = useState<ApiError>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!submitted) return;
    const controller = new AbortController();
    getBooks({ q: submitted.term, limit: ORGANIZATION_PAGE_SIZE, offset: submitted.offset }, controller.signal)
      .then(setResults)
      .catch((cause: unknown) => {
        if (isAbortError(cause)) return;
        const failure = toApiError(cause);
        if (failure.status === 401) redirectToExpiredLogin();
        setError(failure);
      });
    return () => controller.abort();
  }, [bookId, submitted]);

  function search(event: FormEvent) {
    event.preventDefault();
    const term = query.trim();
    if (!term) return;
    setResults(undefined);
    setTarget("");
    setError(undefined);
    setSubmitted({ term, offset: 0 });
  }

  async function confirm() {
    if (saving || !target) return;
    setSaving(true);
    setError(undefined);
    try {
      await reassociateFile(fileId, { sourceBookId: bookId, targetBookId: target });
      onChanged();
    } catch (cause) {
      const failure = toApiError(cause);
      if (failure.status === 401) redirectToExpiredLogin();
      setError(failure);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal labelledBy="reassociate-title" onClose={() => { if (!saving) onClose(); }}>
      <div className="space-y-4 overflow-y-auto p-5">
        <h2 id="reassociate-title" className="text-lg font-semibold">{t("organization.reassociate")}</h2>
        <p className="text-sm text-muted-foreground">{t("organization.reassociateHint")}</p>
        <form className="flex items-center gap-2" onSubmit={search}>
          <Input aria-label={t("organization.searchBook")} maxLength={200} value={query}
            placeholder={t("organization.searchBook")} disabled={saving}
            onChange={(event) => setQuery(event.target.value)} />
          <Button type="submit" disabled={!query.trim() || saving} variant="outline">{t("organization.search")}</Button>
        </form>
        {error && <OrganizationError error={error} />}
        {submitted && !books && !error && <Spinner />}
        {books?.length === 0 && <p>{t("organization.noResults")}</p>}
        {books && books.length > 0 && (
          <fieldset className="divide-y divide-border" disabled={saving}>
            <legend className="text-sm text-muted-foreground">{t("organization.target")}</legend>
            {books.map((book) => (
              <label key={book.id} className="flex min-h-11 items-center gap-2 py-2">
                <input type="radio" name="targetBook" value={book.id} checked={target === book.id}
                  onChange={() => setTarget(book.id)} />
                <span className="min-w-0 break-words">{book.title} <small>{book.authors.join(", ")}</small></span>
              </label>
            ))}
          </fieldset>
        )}
        {results && submitted && (
          <Pagination limit={ORGANIZATION_PAGE_SIZE} offset={submitted.offset} total={results.total}
            disabled={saving} onOffsetChange={(offset) => {
              setResults(undefined);
              setError(undefined);
              setTarget("");
              setSubmitted({ term: submitted.term, offset });
            }} />
        )}
        <div className="flex justify-end gap-2">
          <Button disabled={saving} variant="ghost" onClick={onClose}>{t("form.cancel")}</Button>
          <Button disabled={saving || !target} variant="soft" onClick={() => void confirm()}>{t("organization.reassociate")}</Button>
        </div>
      </div>
    </Modal>
  );
}
