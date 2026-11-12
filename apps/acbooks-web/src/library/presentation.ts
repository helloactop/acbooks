import type { BookSummary } from "./books-api";

export const COVER_FONT =
  "'Songti SC', 'Noto Serif SC', 'Source Han Serif SC', serif";

export const ROW_GRID =
  "grid grid-cols-[48px_minmax(0,2fr)_minmax(0,1fr)_180px_36px] items-center gap-4 max-md:grid-cols-[48px_minmax(0,1fr)_36px]";

interface CoverStyle {
  background: string;
  foreground: string;
  line: string;
}

const DARK_LINE = "rgba(23, 32, 51, 0.22)";
const LIGHT_LINE = "rgba(255, 255, 255, 0.28)";

const COVER_PALETTE: readonly CoverStyle[] = [
  { background: "#d9c8b4", foreground: "#3a2c1e", line: DARK_LINE },
  { background: "#bcd6c9", foreground: "#1f3a2e", line: DARK_LINE },
  { background: "#e9d49a", foreground: "#4a3a0c", line: DARK_LINE },
  { background: "#1f2a44", foreground: "#e8edf5", line: LIGHT_LINE },
  { background: "#f0b9a3", foreground: "#4a2214", line: DARK_LINE },
  { background: "#c9d6f2", foreground: "#1b2e5c", line: DARK_LINE },
  { background: "#a9d3df", foreground: "#12414f", line: DARK_LINE },
  { background: "#2f3b3a", foreground: "#f1e8d0", line: LIGHT_LINE },
  { background: "#e4a85b", foreground: "#3d2406", line: DARK_LINE },
  { background: "#c6c2bb", foreground: "#2b2b28", line: DARK_LINE },
  { background: "#7d9bd4", foreground: "#0f1f45", line: DARK_LINE },
  { background: "#e7e9ee", foreground: "#1b2230", line: DARK_LINE },
];

export function coverFor(book: Pick<BookSummary, "id">): CoverStyle {
  let hash = 0;
  for (const char of book.id) {
    hash = (hash * 31 + (char.codePointAt(0) ?? 0)) >>> 0;
  }
  return COVER_PALETTE[hash % COVER_PALETTE.length] ?? COVER_PALETTE[0]!;
}

export function initialOf(title: string): string {
  return Array.from(title).slice(0, 2).join("");
}

export function joinNames(names: readonly string[], locale: string): string {
  if (names.length === 0) {
    return "";
  }
  try {
    return new Intl.ListFormat(locale, {
      style: "short",
      type: "conjunction",
    }).format(names);
  } catch {
    return names.join(", ");
  }
}

export function formatBytes(bytes: number, locale: string): string {
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${new Intl.NumberFormat(locale, {
    maximumFractionDigits: 1,
  }).format(value)} ${units[unit] ?? "B"}`;
}

export function formatDate(timestamp: number, locale: string): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: "medium" }).format(
    new Date(timestamp),
  );
}

export function formatDateTime(timestamp: number, locale: string): string {
  return new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "UTC",
  }).format(new Date(timestamp));
}
