import { useTheme } from "@acbooks/ui";
import { ResponsiveBar } from "@nivo/bar";
import { ResponsiveLine } from "@nivo/line";
import type { ReactNode } from "react";
import { ActivityCalendar } from "react-activity-calendar";
import "react-activity-calendar/tooltips.css";
import { useTranslation } from "react-i18next";

import type { ReadingStats } from "./stats-api";
import { useStatsFormat } from "./use-stats-format";

const theme = {
  text: { fill: "var(--foreground)", fontFamily: "var(--font-sans)" },
  axis: { ticks: { text: { fill: "var(--muted-foreground)" } } },
  grid: { line: { stroke: "var(--border)" } },
  tooltip: { container: { background: "var(--popover)", color: "var(--foreground)" } },
};
const colors = ["var(--accent-text)"];
const margin = { top: 12, right: 20, bottom: 36, left: 60 };

function Chart({ title, unit, summary, children }: {
  title: string; unit?: string; summary?: ReactNode; children: ReactNode;
}) {
  return <section className="min-w-0 rounded-lg border border-border bg-background p-3 sm:p-4">
    <h3 className="font-semibold">{title}</h3>
    {unit && <p className="text-xs text-muted-foreground">{unit}</p>}
    {children}
    {summary && <p className="mt-2 text-sm text-muted-foreground">{summary}</p>}
  </section>;
}

export function ReadingCharts({ stats, annual }: { stats: ReadingStats; annual: ReadingStats }) {
  const { t } = useTranslation("stats");
  const format = useStatsFormat();
  const { resolvedTheme } = useTheme();
  const { days, hourlySeconds, typeSeconds } = stats;
  const ticks = days.filter((_, index) => index % Math.ceil(days.length / 5) === 0).map(row => row.date);
  const hour = (i: number) => `${String(i).padStart(2, "0")}:00`;
  const hasTime = stats.activeSeconds > 0;
  const graph = "h-52 min-w-0";
  const empty = <p className="py-6 text-sm text-muted-foreground">{t("empty")}</p>;
  const calendarData = annual.days.map(({ date, activeSeconds }) => ({
    date, count: Math.round(activeSeconds),
    level: activeSeconds === 0 ? 0 : activeSeconds < 900 ? 1 : activeSeconds < 1800 ? 2 : activeSeconds < 3600 ? 3 : 4,
  }));
  const line = (key: "activeSeconds" | "averageGapSeconds", title: string) => (
    <div className={graph}><ResponsiveLine
      data={[{ id: title, data: days.map(row => ({ x: row.date, y: row[key] === null ? null : row[key] / 60 })) }]}
      xScale={{ type: "point" }} yScale={{ type: "linear", min: 0, max: "auto" }}
      axisBottom={{ tickValues: ticks, format: value => format.day(String(value)) }}
      axisLeft={{ tickValues: 4, format: format.number }}
      xFormat={value => format.day(String(value))}
      yFormat={value => value === null ? "—" : format.duration(value * 60)} margin={margin} animate={false}
      enablePoints={key === "averageGapSeconds" || days.length <= 31}
      colors={colors} theme={theme} useMesh role="img" ariaLabel={title}
    /></div>
  );
  const peak = hourlySeconds.reduce((best, value, i) => value > (hourlySeconds[best] ?? 0) ? i : best, 0);
  return <div className="grid min-w-0 gap-3 lg:grid-cols-2">
    <Chart title={t("durationTrend")} unit={t("minutes")} summary={hasTime && t("totalDuration", { value: format.duration(stats.activeSeconds) })}>
      {hasTime ? line("activeSeconds", t("durationTrend")) : empty}
    </Chart>
    <Chart title={t("gapTrend")} unit={t("minutes")} summary={stats.averageGapSeconds !== null && t("averageGap", { value: format.duration(stats.averageGapSeconds) })}>
      {stats.averageGapSeconds !== null ? line("averageGapSeconds", t("gapTrend")) : <p className="py-6 text-sm text-muted-foreground">{t("noGap")}</p>}
    </Chart>
    <Chart title={t("counts")} unit={t("sessions")} summary={stats.sessionCount > 0 && t("totalCount", { value: format.number(stats.sessionCount) })}>
      {stats.sessionCount > 0 ? <div className={graph}><ResponsiveBar
        data={days.map(row => ({ date: row.date, value: row.sessionCount }))}
        indexBy="date" keys={["value"]} margin={margin} animate={false} enableLabel={false}
        colors={colors} theme={theme} axisBottom={{ tickValues: ticks, format: value => format.day(String(value)) }}
        axisLeft={{ tickValues: Math.min(4, Math.max(...days.map(row => row.sessionCount))), format: format.number }}
        valueFormat={format.number} role="img" ariaLabel={t("counts")}
        tooltip={({ indexValue, value }) => <div className="rounded border border-border bg-background p-2">{format.day(String(indexValue))} · {t("totalCount", { value: format.number(value) })}</div>}
      /></div> : empty}
    </Chart>
    <Chart title={t("hours")} unit={t("minutes")} summary={hasTime && t("peakHour", { hour: hour(peak), value: format.duration(hourlySeconds[peak] ?? 0) })}>
      {hasTime ? <div className={graph}><ResponsiveBar
        data={hourlySeconds.map((seconds, i) => ({ hour: hour(i), value: seconds / 60 }))}
        indexBy="hour" keys={["value"]} margin={margin} animate={false} enableLabel={false}
        colors={colors} theme={theme} axisBottom={{ tickValues: [0, 6, 12, 18].map(hour) }}
        axisLeft={{ tickValues: 4, format: format.number }}
        valueFormat={value => format.duration(value * 60)} role="img" ariaLabel={t("hours")}
        tooltip={({ indexValue, value }) => <div className="rounded border border-border bg-background p-2">{indexValue} · {format.duration(value * 60)}</div>}
      /></div> : empty}
    </Chart>
    <Chart title={t("types")}>
      {hasTime ? <ul className="mt-3 space-y-2">
        {(["electronic", "manual", "unknown"] as const).map(kind => {
          const proportion = (typeSeconds[kind] ?? 0) / stats.activeSeconds;
          return <li key={kind}>
            <div className="flex justify-between gap-2 text-sm">
              <span>{t(kind)}</span><span>{new Intl.NumberFormat(format.lang, { style: "percent", maximumFractionDigits: 1 }).format(proportion)}</span>
            </div>
            <div className="mt-1 h-2 overflow-hidden rounded bg-surface-active" aria-hidden="true">
              <div className="h-full bg-accent-text" style={{ width: `${proportion * 100}%` }} />
            </div>
          </li>;
        })}
      </ul> : empty}
    </Chart>
    <div className="min-w-0 lg:col-span-2">
      <Chart title={t("annual", { year: annual.startDate.slice(0, 4) })} summary={t("activeDays", { count: annual.days.filter(row => row.activeSeconds > 0).length })}>
        <div className="mt-3 min-w-0 overflow-x-auto" tabIndex={0} role="region" aria-label={t("annual", { year: annual.startDate.slice(0, 4) })}>
          <ActivityCalendar data={calendarData} colorScheme={resolvedTheme}
            theme={{ light: ["var(--surface-active)", "var(--accent-text)"], dark: ["var(--surface-active)", "var(--accent-text)"] }}
            labels={{
              months: Array.from({ length: 12 }, (_, i) => new Intl.DateTimeFormat(format.lang, { month: "short", timeZone: "UTC" }).format(Date.UTC(2026, i, 1))),
              legend: { less: t("less"), more: t("more") },
            }}
            weekStart={1} showTotalCount={false}
            tooltips={{ activity: { text: entry => `${format.day(entry.date)} · ${format.duration(entry.count)}` } }}
          />
        </div>
      </Chart>
    </div>
  </div>;
}
