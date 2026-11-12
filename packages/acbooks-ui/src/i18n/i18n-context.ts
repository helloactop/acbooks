import { createContext } from "react";

import type { LanguageMode, SupportedLanguage } from "./language";

export interface LanguageContextValue {
  language: LanguageMode;
  resolvedLanguage: SupportedLanguage;
  setLanguage: (language: LanguageMode) => void;
}

export const LanguageContext = createContext<LanguageContextValue | null>(null);
