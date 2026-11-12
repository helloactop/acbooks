export const THEME_STORAGE_KEY = "acbooks-theme";

export type ThemeMode = "system" | "light" | "dark";
export type ResolvedTheme = "light" | "dark";

export function isThemeMode(value: string | null): value is ThemeMode {
  return value === "system" || value === "light" || value === "dark";
}
