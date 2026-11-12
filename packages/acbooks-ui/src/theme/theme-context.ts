import { createContext } from "react";

import type { ResolvedTheme, ThemeMode } from "./theme-types";

export interface ThemeContextValue {
  theme: ThemeMode;
  resolvedTheme: ResolvedTheme;
  setTheme: (theme: ThemeMode) => void;
}

export const ThemeContext = createContext<ThemeContextValue | null>(null);
