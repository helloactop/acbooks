export const LANGUAGE_STORAGE_KEY = "acbooks-language";

export const SUPPORTED_LANGUAGES = ["zh-CN", "en"] as const;
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

export const FALLBACK_LANGUAGE: SupportedLanguage = "en";

export type LanguageMode = "system" | SupportedLanguage;

export function isLanguageMode(
  value: string | null | undefined,
): value is LanguageMode {
  return value === "system" || value === "zh-CN" || value === "en";
}

export function getBrowserLanguages(): readonly string[] {
  if (typeof navigator === "undefined") {
    return [];
  }
  if (navigator.languages.length > 0) {
    return navigator.languages;
  }
  return navigator.language ? [navigator.language] : [];
}

export function resolveLanguage(
  mode: LanguageMode,
  languages: readonly string[] = getBrowserLanguages(),
): SupportedLanguage {
  if (mode !== "system") {
    return mode;
  }

  for (const language of languages) {
    const tag = language.toLowerCase();
    if (tag === "zh" || tag.startsWith("zh-")) {
      return "zh-CN";
    }
    if (tag === "en" || tag.startsWith("en-")) {
      return "en";
    }
  }

  return FALLBACK_LANGUAGE;
}
