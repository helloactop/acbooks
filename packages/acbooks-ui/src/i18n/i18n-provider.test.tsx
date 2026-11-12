import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useTranslation } from "react-i18next";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { I18nProvider } from "./i18n-provider";
import { LANGUAGE_STORAGE_KEY } from "./language";
import { useLanguage } from "./use-language";

function Probe() {
  const { language, resolvedLanguage, setLanguage } = useLanguage();
  const { t } = useTranslation();

  return (
    <>
      <output>{`${language}:${resolvedLanguage}`}</output>
      <p>{t("spinner.loading")}</p>
      <button onClick={() => setLanguage("en")} type="button">
        use-en
      </button>
      <button onClick={() => setLanguage("zh-CN")} type="button">
        use-zh
      </button>
      <button onClick={() => setLanguage("system")} type="button">
        use-system
      </button>
    </>
  );
}

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

function renderProvider(defaultLanguage: "system" | "zh-CN" | "en") {
  return render(
    <I18nProvider defaultLanguage={defaultLanguage}>
      <Probe />
    </I18nProvider>,
  );
}

describe("I18nProvider", () => {
  it("resolves the system language and switches immediately", async () => {
    renderProvider("system");

    expect(screen.getByText("system:zh-CN")).toBeInTheDocument();
    expect(screen.getByText("加载中")).toBeInTheDocument();
    expect(document.documentElement.lang).toBe("zh-CN");

    await userEvent.click(screen.getByRole("button", { name: "use-en" }));

    expect(await screen.findByText("en:en")).toBeInTheDocument();
    expect(screen.getByText("Loading")).toBeInTheDocument();
    expect(document.documentElement.lang).toBe("en");
    expect(window.localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe("en");

    await userEvent.click(screen.getByRole("button", { name: "use-zh" }));

    expect(await screen.findByText("zh-CN:zh-CN")).toBeInTheDocument();
    expect(screen.getByText("加载中")).toBeInTheDocument();
    expect(document.documentElement.lang).toBe("zh-CN");
    expect(window.localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe("zh-CN");
  });

  it("restores a persisted explicit choice", () => {
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, "en");

    renderProvider("system");

    expect(screen.getByText("en:en")).toBeInTheDocument();
    expect(screen.getByText("Loading")).toBeInTheDocument();
    expect(document.documentElement.lang).toBe("en");
  });

  it("follows browser language changes while in system mode", async () => {
    renderProvider("system");
    expect(screen.getByText("system:zh-CN")).toBeInTheDocument();

    browserLanguages = ["en-US"];
    act(() => {
      window.dispatchEvent(new Event("languagechange"));
    });

    expect(await screen.findByText("system:en")).toBeInTheDocument();
    expect(screen.getByText("Loading")).toBeInTheDocument();
    expect(document.documentElement.lang).toBe("en");
  });

  it("keeps an explicit choice when the system language changes", async () => {
    renderProvider("system");

    await userEvent.click(screen.getByRole("button", { name: "use-en" }));
    expect(await screen.findByText("en:en")).toBeInTheDocument();

    browserLanguages = ["zh-Hans-CN"];
    act(() => {
      window.dispatchEvent(new Event("languagechange"));
    });

    await waitFor(() => {
      expect(screen.getByText("en:en")).toBeInTheDocument();
    });
    expect(document.documentElement.lang).toBe("en");
  });

  it("falls back to English for unsupported system languages", () => {
    browserLanguages = ["fr-FR", "de-DE"];

    renderProvider("system");

    expect(screen.getByText("system:en")).toBeInTheDocument();
    expect(screen.getByText("Loading")).toBeInTheDocument();
  });
});
