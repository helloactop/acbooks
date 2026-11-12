import { useContext } from "react";

import { LanguageContext, type LanguageContextValue } from "./i18n-context";

export function useLanguage(): LanguageContextValue {
  const context = useContext(LanguageContext);
  if (context === null) {
    throw new Error("useLanguage must be used within an I18nProvider.");
  }
  return context;
}
