import { useTheme } from "@acbooks/ui";
import { ResponsiveBar } from "@nivo/bar";
import { ResponsiveLine } from "@nivo/line";
import { useId, type ReactNode } from "react";
import { ActivityCalendar } from "react-activity-calendar";
import "react-activity-calendar/tooltips.css";
import { useTranslation } from "react-i18next";

import type { ReadingStats } from "./stats-api";
import { useStatsFormat } from "./use-stats-format";
import "./reading-charts.css";

const accent = "var(--accent-text)";
const theme = {
  text: { fill: "var(--foreground)", fontFamily: "var(--font-sans)", fontSize: 11 },
  axis: { domain: { line: { strokeWidth: 0 } }, ticks: { text: { fill: "var(--muted-foreground)" } } },
  grid: { line: { stroke: "var(--border)", strokeOpacity: 0.65, strokeDasharray: "3 5" } },
};
const colors = [accent];
const margin = { top: 12, right: 22, bottom: 32, left: 70 };
const axis = { tickSize: 0, tickPadding: 9 };

function ChartTooltip({ label, value }: { label: string; value: string }) {
  return <div className="reading-chart-tooltip">
    <div className="text-muted-foreground">{label}</div>
    <strong className="font-semibold tabular-nums">{value}</strong>
  </div>;
}

function Chart({ title, summary, children }: {
  title: string; summary?: ReactNode; children: ReactNode;
}) {
  return <section className="min-w-0 rounded-panel border border-border bg-background p-4 sm:p-5">
    <h3 className="font-semibold">{title}</h3>
    {children}
    {summary && <p className="mt-2 text-sm text-muted-foreground">{summary}</p>}
  </section>;
}

export function ReadingCharts({ stats, annual, today }: {
  stats: ReadingStats; annual: ReadingStats; today: string;
}) {
  const { t } = useTranslation("stats");
  const format = useStatsFormat();
  const { resolvedTheme } = useTheme();
  const gradient = useId();
  const days = stats.days.filter(row => row.date <= today);
  const buckets = new Map<string, number>();
  const precision = days.length > 366 * 5 ? 4 : days.length > 366 ? 7 : 10;
  for (const row of days) {
    const key = row.date.slice(0, precision);
    buckets.set(key, (buckets.get(key) ?? 0) + row.activeSeconds);
  }
  const points = [...buckets].map(([x, y]) => ({ x, y }));
  const trendDate = (value: string) => {
    if (precision === 10) return format.day(value);
    const config: Intl.DateTimeFormatOptions = { year: "numeric", timeZone: "UTC" };
    if (precision === 7) config.month = "short";
    return new Intl.DateTimeFormat(format.lang, config).format(new Date(value + (precision === 4 ? "-01" : "") + "-01T00:00:00Z"));
  };
  const hourlySeconds = stats.hourlySeconds;
  const ticks = points.filter((_, index) => index % Math.max(1, Math.ceil(points.length / 4)) === 0).map(point => point.x);
  const hour = (i: number) => `${String(i).padStart(2, "0")}:00`;
  const hasTime = stats.activeSeconds > 0;
  const graph = "mt-2 h-52 min-w-0 sm:h-56";
  const empty = <p className="py-6 text-sm text-muted-foreground">{t("empty")}</p>;
  const calendarData = annual.days.filter(row => row.date <= today).map(({ date, activeSeconds }) => ({
    date, count: Math.round(activeSeconds),
    level: activeSeconds === 0 ? 0 : activeSeconds < 900 ? 1 : activeSeconds < 1800 ? 2 : activeSeconds < 3600 ? 3 : 4,
  }));
  const durationTrend = (
    <div className={graph}><ResponsiveLine
      data={[{ id: "duration", data: points }]}
      xScale={{ type: "point" }} yScale={{ type: "linear", min: 0, max: "auto" }}
      axisBottom={{ ...axis, tickValues: ticks, format: value => trendDate(String(value)) }}
      axisLeft={{ ...axis, tickValues: 3, format: format.duration }}
      margin={margin} animate={false} enableGridX={false} gridYValues={3}
      enablePoints={points.length === 1} pointSize={7}
      enableArea areaOpacity={1} curve="monotoneX" lineWidth={2.5}
      defs={[{ id: `${gradient}-area`, type: "linearGradient", colors: [
        { offset: 0, color: accent, opacity: 0.32 }, { offset: 100, color: accent, opacity: 0.02 },
      ] }]}
      fill={[{ match: "*", id: `${gradient}-area` }]}
      tooltip={({ point }) => <ChartTooltip label={trendDate(String(point.data.x))}
        value={typeof point.data.y === "number" ? format.duration(point.data.y) : "—"} />}
      colors={colors} theme={theme} useMesh crosshairType="x" role="img" ariaLabel={t("durationTrend")}
    /></div>
  );
  const peak = hourlySeconds.reduce((best, value, i) => value > (hourlySeconds[best] ?? 0) ? i : best, 0);
  return <div className="grid min-w-0 gap-3 lg:grid-cols-2">
    <Chart title={t("durationTrend")} summary={hasTime && t("totalDuration", { value: format.duration(stats.activeSeconds) })}>
      {hasTime ? durationTrend : empty}
    </Chart>
    <Chart title={t("hours")} summary={hasTime && t("peakHour", { hour: hour(peak), value: format.duration(hourlySeconds[peak] ?? 0) })}>
      {hasTime ? <div className={graph}><ResponsiveBar
        data={hourlySeconds.map((seconds, i) => ({ hour: hour(i), value: seconds }))}
        indexBy="hour" keys={["value"]} margin={margin} animate={false} enableLabel={false}
        colors={colors} theme={theme} axisBottom={{ ...axis, tickValues: [0, 6, 12, 18].map(hour) }}
        axisLeft={{ ...axis, tickValues: 3, format: format.duration }}
        enableGridX={false} gridYValues={3} padding={0.38} borderRadius={16}
        defs={[{ id: `${gradient}-hours`, type: "linearGradient", colors: [
          { offset: 0, color: accent }, { offset: 100, color: accent, opacity: 0.45 },
        ] }]}
        fill={[{ match: "*", id: `${gradient}-hours` }]}
        valueFormat={format.duration} role="img" ariaLabel={t("hours")}
        tooltip={({ indexValue, value }) => <ChartTooltip label={String(indexValue)} value={format.duration(value)} />}
      /></div> : empty}
    </Chart>
    <div className="min-w-0 lg:col-span-2">
      <Chart title={t("annual", { year: annual.startDate.slice(0, 4) })} summary={t("activeDays", { count: annual.days.filter(row => row.activeSeconds > 0).length })}>
        <div className="mt-3 min-w-0 overflow-x-auto" tabIndex={0} role="region" aria-label={t("annual", { year: annual.startDate.slice(0, 4) })}>
          {calendarData.length > 0 && <ActivityCalendar data={calendarData} colorScheme={resolvedTheme}
            theme={{ light: ["var(--surface-active)", "var(--accent-text)"], dark: ["var(--surface-active)", "var(--accent-text)"] }}
            labels={{
              months: Array.from({ length: 12 }, (_, i) => new Intl.DateTimeFormat(format.lang, { month: "short", timeZone: "UTC" }).format(Date.UTC(2026, i, 1))),
              legend: { less: t("less"), more: t("more") },
            }}
            weekStart={1} showTotalCount={false}
            tooltips={{ activity: { text: entry => `${format.day(entry.date)} · ${format.duration(entry.count)}`, withArrow: false } }}
          />}
        </div>
      </Chart>
    </div>
  </div>;
}
