import { Button, Spinner } from "@acbooks/ui";
import { parseISO } from "date-fns";
import { useEffect, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { toApiError } from "../api/client";
import { useAppShell } from "../layout/app-shell-context";
import { BookCover } from "../library/BookCover";
import { Modal } from "../library/Modal";
import { Pagination } from "../library/Pagination";
import { getDailyStats, type DailyReadingStats } from "./stats-api";
import { useStatsFormat } from "./use-stats-format";

export function ReadingDayDialog({ day, bookId, timeZone, onClose, onFilter }: {
  day: string; bookId?: string; timeZone: string; onClose: () => void; onFilter: () => void;
}) {
  const { t } = useTranslation("stats");
  const { expireSession } = useAppShell();
  const format = useStatsFormat();
  const titleId = useId();
  const [request, setRequest] = useState({ offset: 0 });
  const [result, setResult] = useState<{ request: typeof request; data?: DailyReadingStats }>();
  useEffect(() => {
    const abort = new AbortController();
    void getDailyStats(day, timeZone, request.offset, abort.signal, bookId).then(data => {
      if (!abort.signal.aborted) setResult({ request, data });
    }).catch(cause => {
      if (abort.signal.aborted) return;
      if (toApiError(cause).status === 401) expireSession();
      else setResult({ request });
    });
    return () => abort.abort();
  }, [bookId, day, expireSession, request, timeZone]);
  const current = result?.request === request ? result : undefined;
  const data = current?.data;
  const date = new Intl.DateTimeFormat(format.lang, { dateStyle: "full" }).format(parseISO(day));

  return <Modal labelledBy={titleId} onClose={onClose}>
    <div className="space-y-4 overflow-y-auto p-5">
      <h2 id={titleId} className="text-lg font-semibold">{t("dayDetails", { date })}</h2>
      {data ? <>
        <dl className="grid grid-cols-2 gap-2">
          <div className="rounded-panel bg-surface p-3">
            <dt className="text-sm text-muted-foreground">{t("duration")}</dt>
            <dd className="text-xl font-semibold tabular-nums">{format.duration(data.activeSeconds)}</dd>
          </div>
          <div className="rounded-panel bg-surface p-3">
            <dt className="text-sm text-muted-foreground">{t("counts")}</dt>
            <dd className="text-xl font-semibold tabular-nums">{format.number(data.sessionCount)}</dd>
          </div>
        </dl>
        {data.books.total === 0 ? <p className="text-sm text-muted-foreground">{t("emptyDay")}</p> :
          <ul className="divide-y divide-border">
            {data.books.items.map(book => {
              const share = data.activeSeconds > 0 ? Math.min(1, book.activeSeconds / data.activeSeconds) : 0;
              const percent = new Intl.NumberFormat(format.lang, { style: "percent", maximumFractionDigits: 1 }).format(share);
              return <li className="flex items-center gap-3 py-3" key={book.id}>
                <BookCover book={{ ...book, authors: [] }} className="w-10 shrink-0" thumbnail />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-baseline justify-between gap-x-2">
                    {bookId ? <span className="break-all">{book.title}</span> :
                      <Link className="break-all text-accent-text hover:underline" to={`/library/books/${book.id}`} onClick={onClose}>{book.title}</Link>}
                    <span className="text-sm tabular-nums text-muted-foreground">{format.duration(book.activeSeconds)}</span>
                  </div>
                  <div className="mt-1 flex items-center gap-2">
                    <meter className="reading-share min-w-0 flex-1" value={share} max={1} aria-label={t("dailyShare", { title: book.title })} />
                    <span className="text-xs tabular-nums text-muted-foreground">{percent}</span>
                  </div>
                </div>
              </li>;
            })}
          </ul>}
        {data.books.total > data.books.limit && <Pagination {...data.books} onOffsetChange={offset => setRequest({ offset })} />}
      </> : current ? <div role="alert" className="flex flex-wrap items-center gap-2">
        {t("failed")} <Button variant="outline" onClick={() => setRequest({ ...request })}>{t("retry")}</Button>
      </div> : <div role="status" className="flex items-center gap-2"><Spinner />{t("loading")}</div>}
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="ghost" onClick={onClose}>{t("close")}</Button>
        <Button onClick={onFilter}>{t("showDayRecords")}</Button>
      </div>
    </div>
  </Modal>;
}
