export const THEME_STORAGE_KEY = "acbooks-theme";

export type ThemeMode = "system" | "light" | "dark";
export type ResolvedTheme = "light" | "dark";

export const THEME_BROWSER_COLORS: Record<ResolvedTheme, string> = {
  light: "#EDF1F6",
  dark: "#10151E",
};

export function isThemeMode(value: string | null): value is ThemeMode {
  return value === "system" || value === "light" || value === "dark";
}
