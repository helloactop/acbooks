import { IconButton, cn } from "@acbooks/ui";
import { format, parseISO } from "date-fns";
import { enUS, zhCN } from "date-fns/locale";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { createContext, useContext, useEffect, useId, useRef, type ComponentProps } from "react";
import { DayButton } from "react-day-picker";
import { useTranslation } from "react-i18next";

import { CalendarHeatmap } from "./CalendarHeatmap";
import type { ReadingStats } from "./stats-api";
import { useStatsFormat } from "./use-stats-format";
import "./monthly-reading-calendar.css";

const DayData = createContext({ durations: new Map<string, number>(), duration: (value: number) => String(value) });
const variants = ["reading-level-1", "reading-level-2", "reading-level-3", "reading-level-4"];

function ReadingDayButton({ day, modifiers, children, ...props }: ComponentProps<typeof DayButton>) {
  const { durations, duration } = useContext(DayData);
  const seconds = durations.get(format(day.date, "yyyy-MM-dd")) ?? 0;
  const value = seconds > 0 ? duration(seconds) : undefined;
  const ref = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (modifiers.focused) ref.current?.focus();
  }, [modifiers.focused]);
  return <button {...props} ref={ref} aria-label={value ? `${props["aria-label"]} · ${value}` : props["aria-label"]}>
    <span>{children}</span>
    {value && <small className="reading-day-duration">{value}</small>}
  </button>;
}

export function MonthlyReadingCalendar({ month, today, stats, selected, onMonthChange, onOpen }: {
  month: string; today: string; stats?: ReadingStats; selected?: string;
  onMonthChange: (shift: number) => void; onOpen: (day: string) => void;
}) {
  const { t } = useTranslation("stats");
  const titleId = useId();
  const presentation = useStatsFormat();
  const localToday = parseISO(today);
  const durations = new Map<string, number>();
  const datesPerVariant: Date[][] = [[], [], [], []];
  for (const day of stats?.days ?? []) {
    if (day.date > today || day.activeSeconds <= 0) continue;
    durations.set(day.date, day.activeSeconds);
    const level = day.activeSeconds < 900 ? 0 : day.activeSeconds < 1800 ? 1 : day.activeSeconds < 3600 ? 2 : 3;
    datesPerVariant[level]?.push(parseISO(day.date));
  }

  return <section aria-labelledby={titleId} className="rounded-panel border border-border bg-background p-3 sm:p-4">
    <header className="flex flex-wrap items-center justify-between gap-2">
      <h3 id={titleId} className="font-semibold">{t("monthlyCalendar")}</h3>
      <div className="flex items-center gap-1">
        <IconButton label={t("previousMonth")} disabled={month <= "1970-01-01"} onClick={() => onMonthChange(-1)}><ChevronLeft /></IconButton>
        <p className="text-sm font-medium" aria-live="polite">
          {new Intl.DateTimeFormat(presentation.lang, { month: "long", year: "numeric" }).format(parseISO(month))}
        </p>
        <IconButton label={t("nextMonth")} disabled={month.slice(0, 7) >= today.slice(0, 7)} onClick={() => onMonthChange(1)}><ChevronRight /></IconButton>
      </div>
    </header>
    <DayData.Provider value={{ durations, duration: presentation.duration }}>
      <CalendarHeatmap
        className="monthly-reading-heatmap mt-3"
        mode="single"
        month={parseISO(month)}
        today={localToday}
        disabled={{ after: localToday }}
        locale={presentation.lang === "zh-CN" ? zhCN : enUS}
        weekStartsOn={1}
        hideNavigation
        selected={selected ? parseISO(selected) : undefined}
        onDayClick={(date, modifiers) => {
          if (!modifiers.disabled) onOpen(format(date, "yyyy-MM-dd"));
        }}
        components={{ DayButton: ReadingDayButton }}
        classNames={{ month_caption: "sr-only" }}
        variantClassnames={variants}
        datesPerVariant={datesPerVariant}
      />
    </DayData.Provider>
    <div className="mt-2 flex items-center justify-end gap-1.5 text-xs text-muted-foreground">
      <span>{t("less")}</span>
      {variants.map(level => <span key={level} aria-hidden="true" className={cn(level, "reading-level-swatch size-3 rounded-sm")} />)}
      <span>{t("more")}</span>
    </div>
  </section>;
}
