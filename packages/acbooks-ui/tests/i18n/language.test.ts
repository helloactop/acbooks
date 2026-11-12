import { describe, expect, it } from "vitest";

import {
  getBrowserLanguages,
  isLanguageMode,
  resolveLanguage,
} from "../../src/i18n/language";

describe("resolveLanguage", () => {
  it("honors explicit language choices over browser preferences", () => {
    expect(resolveLanguage("zh-CN", ["en-US"])).toBe("zh-CN");
    expect(resolveLanguage("en", ["zh-CN"])).toBe("en");
  });

  it("matches zh and en variants in browser preference order", () => {
    expect(resolveLanguage("system", ["zh-Hans-CN"])).toBe("zh-CN");
    expect(resolveLanguage("system", ["en-GB"])).toBe("en");
    expect(resolveLanguage("system", ["zh-Hant-TW"])).toBe("zh-CN");
    expect(resolveLanguage("system", ["fr-FR", "zh-CN"])).toBe("zh-CN");
    expect(resolveLanguage("system", ["fr-FR", "en-US"])).toBe("en");
  });

  it("uses the first supported language in preference order", () => {
    expect(resolveLanguage("system", ["en-US", "zh-CN"])).toBe("en");
    expect(resolveLanguage("system", ["zh-CN", "en-US"])).toBe("zh-CN");
  });

  it("falls back to English when no language matches", () => {
    expect(resolveLanguage("system", [])).toBe("en");
    expect(resolveLanguage("system", ["fr-FR", "de-DE"])).toBe("en");
  });

  it("validates persisted language modes", () => {
    expect(isLanguageMode("system")).toBe(true);
    expect(isLanguageMode("zh-CN")).toBe(true);
    expect(isLanguageMode("en")).toBe(true);
    expect(isLanguageMode("fr")).toBe(false);
    expect(isLanguageMode(null)).toBe(false);
  });

  it("falls back to the browser language list when languages is empty", () => {
    expect(getBrowserLanguages().length).toBeGreaterThan(0);
  });
});
