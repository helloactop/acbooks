import { useTranslation } from "react-i18next";

export function useStatsFormat() {
  const { i18n } = useTranslation("stats");
  const lang = i18n.resolvedLanguage;
  const number = new Intl.NumberFormat(lang, { maximumFractionDigits: 1 });
  const minutes = new Intl.NumberFormat(lang, { style: "unit", unit: "minute", maximumFractionDigits: 1 });
  const date = new Intl.DateTimeFormat(lang, { month: "short", day: "numeric", timeZone: "UTC" });
  return {
    lang,
    number: (n: number) => number.format(n),
    duration: (s: number) => minutes.format(s / 60),
    day: (key: string) => date.format(new Date(`${key}T00:00:00Z`)),
  };
}
