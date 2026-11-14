import { Button, Field, Input, Select, Spinner } from "@acbooks/ui";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { toApiError } from "../api/client";
import { useAppShell } from "../layout/app-shell-context";
import { MonthlyReadingCalendar } from "./MonthlyReadingCalendar";
import { ReadingCharts } from "./ReadingCharts";
import { ReadingDayDialog } from "./ReadingDayDialog";
import { ReadingRecordDialog, type RecordAction } from "./ReadingRecordDialog";
import { ReadingTimeline } from "./ReadingTimeline";
import { DAY, dateLabel, dateValue, localInput } from "./reading-dates";
import { getStats, getTimeline, type ReadingStats, type StatsRange, type StatsSelection, type TimelinePage } from "./stats-api";
import { useStatsFormat } from "./use-stats-format";

const periods = ["week", "month", "year", "all", "custom"] as const;
type Period = typeof periods[number];

function preset(period: Period, shift: number, today: string, timeZone: string): StatsRange {
  const clock = new Date(dateValue(today));
  const year = clock.getUTCFullYear();
  const month = clock.getUTCMonth();
  const monday = clock.getTime() - ((clock.getUTCDay() + 6) % 7) * DAY;
  const start = period === "week" ? monday + shift * 7 * DAY
    : period === "year" ? Date.UTC(year + shift, 0, 1) : Date.UTC(year, month + shift, 1);
  const end = period === "week" ? start + 7 * DAY
    : period === "year" ? Date.UTC(year + shift + 1, 0, 1) : Date.UTC(year, month + shift + 1, 1);
  return { startDate: dateLabel(Math.max(0, start)), endDate: dateLabel(end), timeZone };
}

function selectionKey(range: StatsSelection) {
  return "startDate" in range ? `${range.startDate}/${range.endDate}` : "";
}

export default function ReadingStatsPanel({ bookId, revision }: { bookId?: string; revision?: string }) {
  const { t } = useTranslation("stats");
  const format = useStatsFormat();
  const { expireSession } = useAppShell();
  const [today] = useState(() => localInput(Date.now()).slice(0, 10));
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  const [period, setPeriod] = useState<Period>("month");
  const [shift, setShift] = useState(0);
  const [monthShift, setMonthShift] = useState(0);
  const [day, setDay] = useState<string>();
  const [dayDetails, setDayDetails] = useState<string>();
  const [custom, setCustom] = useState({ start: `${today.slice(0, 7)}-01`, end: today });
  const [applied, setApplied] = useState(custom);
  const [action, setAction] = useState<RecordAction>();
  const [offset, setOffset] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const [overview, setOverview] = useState<{ key: string; stats: ReadingStats; annual: ReadingStats; monthly: ReadingStats }>();
  const [timeline, setTimeline] = useState<{ key: string; page: TimelinePage }>();
  const [failure, setFailure] = useState<{ overview?: string; timeline?: string }>({});

  const selection = period === "all" ? { timeZone } : period === "custom"
    ? { startDate: applied.start, endDate: dateLabel(dateValue(applied.end) + DAY), timeZone }
    : preset(period, shift, today, timeZone);
  const monthly = preset("month", monthShift, today, timeZone);
  const year = preset("year", 0, monthly.startDate, timeZone);
  const timelineRange = day ? { startDate: day, endDate: dateLabel(dateValue(day) + DAY), timeZone } : selection;
  const overviewKey = JSON.stringify([bookId, selectionKey(selection), selectionKey(year), selectionKey(monthly), timeZone, attempt, revision]);
  const timelineKey = JSON.stringify([bookId, selectionKey(timelineRange), timeZone, attempt, revision, offset]);

  const start = "startDate" in selection ? selection.startDate : undefined;
  const end = "endDate" in selection ? selection.endDate : undefined;
  const { startDate: monthStart, endDate: monthEnd } = monthly;
  const { startDate: yearStart, endDate: yearEnd } = year;
  const timelineStart = "startDate" in timelineRange ? timelineRange.startDate : undefined;
  const timelineEnd = "endDate" in timelineRange ? timelineRange.endDate : undefined;

  useEffect(() => {
    const abort = new AbortController();
    const queries = new Map<string, Promise<ReadingStats>>();
    function request(selection: StatsSelection) {
      const key = selectionKey(selection);
      const existing = queries.get(key);
      if (existing) return existing;
      const pending = getStats(selection, abort.signal, bookId);
      queries.set(key, pending);
      return pending;
    }

    void Promise.all([
      request(start && end ? { startDate: start, endDate: end, timeZone } : { timeZone }),
      request({ startDate: yearStart, endDate: yearEnd, timeZone }),
      request({ startDate: monthStart, endDate: monthEnd, timeZone }),
    ]).then(([stats, annual, monthly]) => {
      if (!abort.signal.aborted) setOverview({ key: overviewKey, stats, annual, monthly });
    }).catch(cause => {
      if (abort.signal.aborted) return;
      if (toApiError(cause).status === 401) expireSession();
      else setFailure(current => ({ ...current, overview: overviewKey }));
    });
    return () => abort.abort();
  }, [bookId, end, expireSession, monthEnd, monthStart, overviewKey, start, timeZone, yearEnd, yearStart]);

  useEffect(() => {
    const abort = new AbortController();
    const selection = timelineStart && timelineEnd
      ? { startDate: timelineStart, endDate: timelineEnd, timeZone } : { timeZone };
    void getTimeline(selection, offset, abort.signal, bookId).then(page => {
      if (!abort.signal.aborted) setTimeline({ key: timelineKey, page });
    }).catch(cause => {
      if (abort.signal.aborted) return;
      if (toApiError(cause).status === 401) expireSession();
      else setFailure(current => ({ ...current, timeline: timelineKey }));
    });
    return () => abort.abort();
  }, [bookId, expireSession, offset, timelineEnd, timelineKey, timelineStart, timeZone]);

  function resetTimeline() {
    setDay(undefined);
    setOffset(0);
  }

  function changeShift(nextShift: number) {
    setShift(nextShift);
    const next = preset(period, nextShift, today, timeZone);
    const anchor = period === "week" ? dateLabel(dateValue(next.endDate) - DAY) : next.startDate;
    setCalendarMonth(anchor > today ? today : anchor);
    resetTimeline();
  }

  function setCalendarMonth(label: string) {
    const month = (value: string) => Number(value.slice(0, 4)) * 12 + Number(value.slice(5, 7));
    setMonthShift(month(label) - month(today));
  }

  const data = overview?.key === overviewKey ? overview : undefined;
  const page = timeline?.key === timelineKey ? timeline.page : undefined;
  const date = new Intl.DateTimeFormat(format.lang, { dateStyle: "medium", timeZone: "UTC" });
  const retry = () => setAttempt(n => n + 1);
  const movable = period !== "all" && period !== "custom";
  const tomorrow = dateLabel(dateValue(today) + DAY);
  const labelStart = start ?? data?.stats.startDate;
  const labelEnd = end ?? data?.stats.endDate;

  return <div className="min-w-0 space-y-4">
    <div className="flex flex-wrap items-end gap-2">
      <Field label={t("period")}>
        <Select value={period} options={periods.map(value => ({ value, label: t(value) }))}
          onValueChange={value => {
            const next = periods.find(option => option === value);
            if (!next) return;
            setPeriod(next);
            setShift(0);
            setCalendarMonth(next === "custom" ? applied.end : today);
            resetTimeline();
          }} />
      </Field>
      {movable && <>
        <Button variant="outline" disabled={start === "1970-01-01"} onClick={() => changeShift(shift - 1)}>{t("previousPeriod")}</Button>
        <Button variant="outline" disabled={shift >= 0} onClick={() => changeShift(shift + 1)}>{t("nextPeriod")}</Button>
      </>}
      <p className="basis-full text-sm text-muted-foreground">
        {labelStart && labelEnd && <>{date.formatRange(dateValue(labelStart), dateValue(labelEnd) - DAY)} · </>}{timeZone}
      </p>
    </div>
    {period === "custom" && <form className="flex flex-wrap items-end gap-2" onSubmit={event => {
      event.preventDefault();
      if (!custom.start || !custom.end || custom.start > custom.end || custom.end > today) return;
      setApplied(custom);
      setCalendarMonth(custom.end);
      resetTimeline();
    }}>
      <Field label={t("fromDate")} required>
        <Input type="date" min="1970-01-01" max={custom.end || today} required value={custom.start}
          onChange={event => setCustom({ ...custom, start: event.target.value })} />
      </Field>
      <Field label={t("toDate")} required>
        <Input type="date" min={custom.start || "1970-01-01"} max={today} required value={custom.end}
          onChange={event => setCustom({ ...custom, end: event.target.value })} />
      </Field>
      <Button type="submit">{t("apply")}</Button>
    </form>}
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
      <ReadingCharts stats={data.stats} annual={data.annual} today={today} />
    </> : failure.overview === overviewKey ? <div role="alert" className="flex flex-wrap items-center gap-2 py-3">
      {t("failed")} <Button variant="outline" onClick={retry}>{t("retry")}</Button>
    </div> : <div role="status" className="flex items-center gap-2 py-3"><Spinner />{t("loading")}</div>}
    <MonthlyReadingCalendar month={monthStart} today={today} stats={data?.monthly} selected={day}
      onMonthChange={delta => { setMonthShift(n => n + delta); resetTimeline(); }}
      onOpen={setDayDetails} />
    {dayDetails && <ReadingDayDialog key={dayDetails} day={dayDetails} bookId={bookId} timeZone={timeZone}
      onClose={() => setDayDetails(undefined)}
      onFilter={() => {
        setDay(dayDetails);
        setOffset(0);
        setDayDetails(undefined);
      }} />}
    {day && <div className="flex flex-wrap items-center gap-2">
      <p className="text-sm text-muted-foreground">{t("timelineDay", { date: date.format(dateValue(day)) })}</p>
      <Button variant="outline" onClick={resetTimeline}>{t("clearDay")}</Button>
    </div>}
    <ReadingTimeline bookId={bookId} timeZone={timeZone} page={page} failed={failure.timeline === timelineKey}
      onRetry={retry} onOffset={setOffset} onAction={setAction} />
    {action && <ReadingRecordDialog action={action} bookId={bookId}
      initialDate={day ?? (end && end < tomorrow ? dateLabel(dateValue(end) - DAY) : undefined)}
      onClose={() => setAction(undefined)}
      onSaved={() => { setAction(undefined); setOffset(0); retry(); }} />}
  </div>;
}
