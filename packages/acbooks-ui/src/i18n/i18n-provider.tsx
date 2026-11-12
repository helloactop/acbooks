import type { Resource } from "i18next";
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { I18nextProvider } from "react-i18next";

import { createI18n } from "./create-i18n";
import {
  LanguageContext,
  type LanguageContextValue,
} from "./i18n-context";
import {
  getBrowserLanguages,
  isLanguageMode,
  LANGUAGE_STORAGE_KEY,
  resolveLanguage,
  type LanguageMode,
  type SupportedLanguage,
} from "./language";

export interface I18nProviderProps {
  children: ReactNode;
  defaultLanguage?: LanguageMode;
  resources?: Resource;
  storageKey?: string;
}

function readStoredLanguage(
  storageKey: string,
  fallback: LanguageMode,
): LanguageMode {
  if (typeof window === "undefined") {
    return fallback;
  }
  try {
    const stored = window.localStorage.getItem(storageKey);
    return isLanguageMode(stored) ? stored : fallback;
  } catch {
    return fallback;
  }
}

function applyDocumentLanguage(language: SupportedLanguage): void {
  if (typeof document === "undefined") {
    return;
  }
  document.documentElement.lang = language;
}

export function I18nProvider({
  children,
  defaultLanguage = "system",
  resources,
  storageKey = LANGUAGE_STORAGE_KEY,
}: I18nProviderProps) {
  const [language, setLanguageState] = useState<LanguageMode>(() =>
    readStoredLanguage(storageKey, defaultLanguage),
  );
  const [browserLanguages, setBrowserLanguages] =
    useState<readonly string[]>(getBrowserLanguages);
  const resolvedLanguage = resolveLanguage(language, browserLanguages);
  const [i18n] = useState(() =>
    createI18n({ language: resolvedLanguage, resources }),
  );

  useEffect(() => {
    void i18n.changeLanguage(resolvedLanguage);
    applyDocumentLanguage(resolvedLanguage);
  }, [i18n, resolvedLanguage]);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    const handleLanguageChange = () => {
      setBrowserLanguages(getBrowserLanguages());
    };
    window.addEventListener("languagechange", handleLanguageChange);
    return () => {
      window.removeEventListener("languagechange", handleLanguageChange);
    };
  }, []);

  const setLanguage = useCallback(
    (nextLanguage: LanguageMode) => {
      setLanguageState(nextLanguage);
      try {
        window.localStorage.setItem(storageKey, nextLanguage);
      } catch {
        // Storage can be unavailable in private or restricted browser contexts.
      }
    },
    [storageKey],
  );

  const value = useMemo<LanguageContextValue>(
    () => ({ language, resolvedLanguage, setLanguage }),
    [language, resolvedLanguage, setLanguage],
  );

  return (
    <I18nextProvider i18n={i18n}>
      <LanguageContext.Provider value={value}>
        {children}
      </LanguageContext.Provider>
    </I18nextProvider>
  );
}
