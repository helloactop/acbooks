import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  I18nProvider,
  LANGUAGE_STORAGE_KEY,
  ThemeProvider,
  type LanguageMode,
} from "@acbooks/ui";

import { PreviewPage } from "./PreviewPage";
import { appResources } from "../i18n/resources";

let browserLanguages: string[];

beforeEach(() => {
  window.localStorage.clear();
  document.documentElement.lang = "";
  browserLanguages = ["zh-Hans-CN"];
  vi.spyOn(window.navigator, "languages", "get").mockImplementation(
    () => browserLanguages,
  );
});

afterEach(() => {
  vi.restoreAllMocks();
});

function renderApp(language: LanguageMode) {
  return render(
    <ThemeProvider>
      <I18nProvider defaultLanguage={language} resources={appResources}>
        <PreviewPage />
      </I18nProvider>
    </ThemeProvider>,
  );
}

describe("PreviewPage i18n", () => {
  it("switches the preview between Chinese and English without losing input", async () => {
    const user = userEvent.setup();
    renderApp("zh-CN");

    expect(
      screen.getByRole("heading", { level: 1, name: "公共组件预览" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "保存" })).toBeInTheDocument();

    const username = screen.getByLabelText("用户名");
    await user.type(username, "reader");

    await user.click(screen.getByRole("radio", { name: "English" }));

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "Component preview",
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeInTheDocument();
    expect(screen.getByLabelText("Username")).toHaveValue("reader");
    expect(screen.getByRole("radio", { name: "English" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
    expect(document.documentElement.lang).toBe("en");
    expect(window.localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe("en");
  });

  it("restores a saved language on reload", () => {
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, "en");

    renderApp("system");

    expect(
      screen.getByRole("heading", { level: 1, name: "Component preview" }),
    ).toBeInTheDocument();
    expect(document.documentElement.lang).toBe("en");
  });

  it("follows system language changes while in system mode", async () => {
    renderApp("system");

    expect(
      screen.getByRole("heading", { level: 1, name: "公共组件预览" }),
    ).toBeInTheDocument();

    browserLanguages = ["en-US"];
    act(() => {
      window.dispatchEvent(new Event("languagechange"));
    });

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "Component preview",
      }),
    ).toBeInTheDocument();
    expect(document.documentElement.lang).toBe("en");
  });

  it("falls back to English for unsupported system languages", () => {
    browserLanguages = ["fr-FR"];

    renderApp("system");

    expect(
      screen.getByRole("heading", { level: 1, name: "Component preview" }),
    ).toBeInTheDocument();
    expect(document.documentElement.lang).toBe("en");
  });
});
