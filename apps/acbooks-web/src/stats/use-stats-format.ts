import { useTranslation } from "react-i18next";

export function useStatsFormat() {
  const { i18n } = useTranslation("stats");
  const lang = i18n.resolvedLanguage;
  const number = new Intl.NumberFormat(lang, { maximumFractionDigits: 1 });
  const units = { minute: 60, hour: 3600, day: 86400 } as const;
  const durationFormat = (unit: keyof typeof units) =>
    new Intl.NumberFormat(lang, { style: "unit", unit, maximumFractionDigits: 1 });
  const durations = { minute: durationFormat("minute"), hour: durationFormat("hour"), day: durationFormat("day") };
  const date = new Intl.DateTimeFormat(lang, { month: "short", day: "numeric", timeZone: "UTC" });
  return {
    lang,
    number: (n: number) => number.format(n),
    duration: (seconds: number) => {
      const unit = seconds >= units.day ? "day" : seconds >= units.hour ? "hour" : "minute";
      return durations[unit].format(seconds / units[unit]);
    },
    day: (key: string) => date.format(new Date(`${key}T00:00:00Z`)),
  };
}
