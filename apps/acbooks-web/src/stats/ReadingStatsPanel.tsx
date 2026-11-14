import { Button, SegmentedControl, Spinner } from "@acbooks/ui";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { toApiError } from "../api/client";
import { useAppShell } from "../layout/app-shell-context";
import { ReadingCharts } from "./ReadingCharts";
import { useStatsFormat } from "./use-stats-format";
import { getStats, getTimeline, type ReadingStats, type StatsRange, type TimelinePage } from "./stats-api";

type Period = "week" | "month" | "year";
const DAY = 86_400_000;

function ranges(period: Period, shift: number, timeZone: string): [StatsRange, StatsRange] {
  const today = new Date();
  const year = today.getFullYear();
  const month = today.getMonth();
  const monday = Date.UTC(year, month, today.getDate() - (today.getDay() + 6) % 7);
  const start = period === "week" ? monday + shift * 7 * DAY
    : period === "month" ? Date.UTC(year, month + shift, 1) : Date.UTC(year + shift, 0, 1);
  const end = period === "week" ? start + 7 * DAY
    : period === "month" ? Date.UTC(year, month + shift + 1, 1) : Date.UTC(year + shift + 1, 0, 1);
  const range = (left: number, right: number) => ({
    startDate: new Date(left).toISOString().slice(0, 10),
    endDate: new Date(right).toISOString().slice(0, 10),
    timeZone,
  });
  const startYear = new Date(period === "week" ? end - DAY : start).getUTCFullYear();
  return [range(start, end), range(Date.UTC(startYear, 0, 1), Date.UTC(startYear + 1, 0, 1))];
}

export default function ReadingStatsPanel({ bookId, revision }: { bookId?: string; revision?: string }) {
  const { t } = useTranslation("stats");
  const format = useStatsFormat();
  const { expireSession } = useAppShell();
  const [period, setPeriod] = useState<Period>("month");
  const [shift, setShift] = useState(0);
  const [offset, setOffset] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const [overview, setOverview] = useState<{ key: string; stats: ReadingStats; annual: ReadingStats }>();
  const [timeline, setTimeline] = useState<{ key: string; page: TimelinePage }>();
  const [failure, setFailure] = useState<{ overview?: string; timeline?: string }>({});
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  const [range, year] = ranges(period, shift, timeZone);
  const { startDate, endDate } = range;
  const yearStart = year.startDate;
  const yearEnd = year.endDate;
  const key = JSON.stringify([bookId, startDate, endDate, timeZone, attempt, revision]);
  const timelineKey = JSON.stringify([key, offset]);

  useEffect(() => {
    const abort = new AbortController();
    const aggregate = getStats({ startDate, endDate, timeZone }, abort.signal, bookId);
    const annual = startDate === yearStart && endDate === yearEnd ? aggregate
      : getStats({ startDate: yearStart, endDate: yearEnd, timeZone }, abort.signal, bookId);
    void Promise.all([aggregate, annual]).then(([stats, yearly]) => {
      if (!abort.signal.aborted) setOverview({ key, stats, annual: yearly });
    }).catch(cause => {
      if (abort.signal.aborted) return;
      if (toApiError(cause).status === 401) expireSession();
      else setFailure(current => ({ ...current, overview: key }));
    });
    return () => abort.abort();
  }, [bookId, endDate, expireSession, key, startDate, timeZone, yearEnd, yearStart]);

  useEffect(() => {
    const abort = new AbortController();
    void getTimeline({ startDate, endDate, timeZone }, offset, abort.signal, bookId).then(page => {
      if (!abort.signal.aborted) setTimeline({ key: timelineKey, page });
    }).catch(cause => {
      if (abort.signal.aborted) return;
      if (toApiError(cause).status === 401) expireSession();
      else setFailure(current => ({ ...current, timeline: timelineKey }));
    });
    return () => abort.abort();
  }, [bookId, endDate, expireSession, offset, startDate, timelineKey, timeZone]);

  const data = overview?.key === key ? overview : undefined;
  const page = timeline?.key === timelineKey ? timeline.page : undefined;
  const date = new Intl.DateTimeFormat(format.lang, { dateStyle: "medium", timeZone: "UTC" });
  const timestamp = new Intl.DateTimeFormat(format.lang, { dateStyle: "medium", timeStyle: "short", timeZone });
  const toUtc = (label: string) => Date.parse(`${label}T00:00:00Z`);
  const loading = <div role="status" className="flex items-center gap-2 py-3"><Spinner />{t("loading")}</div>;
  const error = <div role="alert" className="flex items-center gap-2 py-3">{t("failed")} <Button variant="outline" onClick={() => setAttempt(n => n + 1)}>{t("retry")}</Button></div>;
  return <div className="min-w-0 space-y-4">
    <div className="flex flex-wrap items-center gap-2">
      <SegmentedControl
        ariaLabel={t("period")} value={period} variant="boxed"
        options={ (["week", "month", "year"] as const).map(value => ({
          value, label: t(value),
        }))}
        onValueChange={value => { setPeriod(value); setShift(0); setOffset(0); }}
      />
      <Button variant="outline" disabled={range.startDate < "1971-01-01"} onClick={() => { setShift(n => n - 1); setOffset(0); }}>{t("previousPeriod")}</Button>
      <Button variant="outline" disabled={shift >= 0} onClick={() => { setShift(n => n + 1); setOffset(0); }}>{t("nextPeriod")}</Button>
      <p className="text-sm text-muted-foreground">{date.formatRange(toUtc(startDate), toUtc(endDate) - DAY)} · {timeZone}</p>
    </div>
    {data ? <>
      <dl className="grid gap-2 sm:grid-cols-3">
        {([
          [t("duration"), format.duration(data.stats.activeSeconds)],
          [t("counts"), format.number(data.stats.sessionCount)],
          [t("averageGapTitle"), data.stats.averageGapSeconds === null ? "—" : format.duration(data.stats.averageGapSeconds)],
        ]).map(([label, value]) => <div key={label} className="rounded-lg border border-border bg-background p-3">
          <dt className="text-sm text-muted-foreground">{label}</dt><dd className="mt-1 text-xl font-semibold">{value}</dd>
        </div>)}
      </dl>
      <p className="text-sm text-muted-foreground">{t("estimate")}</p>
      <ReadingCharts stats={data.stats} annual={data.annual} />
    </> : failure.overview === key ? error : loading}
    <section className="rounded-lg border border-border bg-background p-3 sm:p-4">
      <h3 className="font-semibold">{t("timeline")}</h3>
      {page ? page.items.length === 0 ? <p className="mt-2 text-sm text-muted-foreground">{t("empty")}</p> : <>
        <ul className="mt-2 divide-y divide-border">
          {page.items.map(item => <li key={item.id} className="flex flex-wrap items-baseline justify-between gap-x-3 py-2 text-sm">
            <div className="min-w-0">
              {bookId === undefined && <Link className="block break-words text-accent-text hover:underline" to={`/library/books/${item.bookId}`}>{item.bookTitle}</Link>}
              <time>{timestamp.format(item.startedAt)}</time>
              {item.endedAt !== null && <> – <time>{timestamp.format(item.endedAt)}</time></>}
              <p className="text-muted-foreground">
                {t(item.source === "manual" ? "manualSource" : "automaticSource")}
                {item.fileFormat && <> · {item.fileFormat.toUpperCase()}</>}
              </p>
            </div>
            <span className="text-muted-foreground">{format.duration(item.activeSeconds)}</span>
          </li>)}
        </ul>
        {page.total > page.limit && <div className="mt-2 flex flex-wrap items-center gap-2">
          <Button variant="outline" disabled={offset === 0} onClick={() => setOffset(n => n - page.limit)}>{t("previousPage")}</Button>
          <span className="text-sm text-muted-foreground">{format.number(offset + 1)}–{format.number(Math.min(offset + page.limit, page.total))} / {format.number(page.total)}</span>
          <Button variant="outline" disabled={offset + page.limit >= page.total} onClick={() => setOffset(n => n + page.limit)}>{t("nextPage")}</Button>
        </div>}
      </> : failure.timeline === timelineKey ? error : loading}
    </section>
  </div>;
}
