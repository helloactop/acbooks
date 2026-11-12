import i18n, { type i18n as I18nInstance, type Resource } from "i18next";
import { initReactI18next } from "react-i18next";

import { FALLBACK_LANGUAGE, type SupportedLanguage } from "./language";
import { sharedResources } from "./resources";

export type TranslationResources = Resource;

export interface CreateI18nOptions {
  language?: SupportedLanguage;
  resources?: TranslationResources;
}

function mergeResources(
  base: Resource,
  extra: Resource | undefined,
): Resource {
  const merged: Resource = {};

  for (const [language, namespaces] of Object.entries(base)) {
    merged[language] = { ...namespaces };
  }

  for (const [language, namespaces] of Object.entries(extra ?? {})) {
    merged[language] = { ...(merged[language] ?? {}), ...namespaces };
  }

  return merged;
}

export function createI18n({
  language = FALLBACK_LANGUAGE,
  resources,
}: CreateI18nOptions = {}): I18nInstance {
  const mergedResources = mergeResources(sharedResources, resources);
  const namespaces = Array.from(
    new Set(
      Object.values(mergedResources).flatMap((bundles) =>
        Object.keys(bundles ?? {}),
      ),
    ),
  );

  const instance = i18n.createInstance();

  void instance.use(initReactI18next).init({
    resources: mergedResources,
    lng: language,
    fallbackLng: FALLBACK_LANGUAGE,
    defaultNS: "common",
    ns: namespaces,
    interpolation: { escapeValue: false },
    initAsync: false,
    returnNull: false,
    react: { useSuspense: false },
  });

  return instance;
}
