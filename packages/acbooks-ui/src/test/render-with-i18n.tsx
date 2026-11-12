import { render } from "@testing-library/react";
import type { ReactElement } from "react";

import { I18nProvider } from "../i18n/i18n-provider";
import type { LanguageMode } from "../i18n/language";

export function renderWithI18n(
  ui: ReactElement,
  language: LanguageMode = "zh-CN",
) {
  return render(
    <I18nProvider defaultLanguage={language}>{ui}</I18nProvider>,
  );
}
