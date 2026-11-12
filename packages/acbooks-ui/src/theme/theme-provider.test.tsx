import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { Button } from "../components/button";
import { ThemeProvider } from "./theme-provider";
import { THEME_BROWSER_COLORS, THEME_STORAGE_KEY } from "./theme-types";
import { useTheme } from "./use-theme";

function ThemeProbe() {
  const { resolvedTheme, setTheme, theme } = useTheme();
  return (
    <>
      <output>{`${theme}:${resolvedTheme}`}</output>
      <Button
        onClick={() => {
          setTheme("light");
        }}
      >
        使用浅色
      </Button>
      <Button
        onClick={() => {
          setTheme("dark");
        }}
      >
        使用深色
      </Button>
    </>
  );
}

let mediaMatches = false;
const mediaListeners = new Set<(event: MediaQueryListEvent) => void>();

function themeColorMeta(): HTMLMetaElement {
  const existing = document.head.querySelector<HTMLMetaElement>(
    'meta[name="theme-color"]',
  );
  if (existing !== null) {
    return existing;
  }
  const meta = document.createElement("meta");
  meta.name = "theme-color";
  meta.content = THEME_BROWSER_COLORS.light;
  document.head.append(meta);
  return meta;
}

beforeEach(() => {
  mediaMatches = false;
  mediaListeners.clear();
  window.localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
  document.head
    .querySelectorAll('meta[name="theme-color"]')
    .forEach((element) => {
      element.remove();
    });
  themeColorMeta();
  vi.stubGlobal(
    "matchMedia",
    vi.fn((query: string) => ({
      matches: mediaMatches,
      media: query,
      onchange: null,
      addEventListener: (
        _type: string,
        listener: (event: MediaQueryListEvent) => void,
      ) => {
        mediaListeners.add(listener);
      },
      removeEventListener: (
        _type: string,
        listener: (event: MediaQueryListEvent) => void,
      ) => {
        mediaListeners.delete(listener);
      },
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
  document.documentElement.removeAttribute("data-theme");
  document.documentElement.style.colorScheme = "";
  document.head
    .querySelectorAll('meta[name="theme-color"]')
    .forEach((element) => {
      element.remove();
    });
});

describe("ThemeProvider", () => {
  it("applies the resolved theme, browser color and persisted choice", async () => {
    render(
      <ThemeProvider>
        <ThemeProbe />
      </ThemeProvider>,
    );

    const meta = themeColorMeta();
    expect(screen.getByText("system:light")).toBeInTheDocument();
    expect(document.documentElement.dataset.theme).toBe("light");
    expect(meta.content).toBe(THEME_BROWSER_COLORS.light);

    await userEvent.click(screen.getByRole("button", { name: "使用深色" }));
    expect(screen.getByText("dark:dark")).toBeInTheDocument();
    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(meta.content).toBe(THEME_BROWSER_COLORS.dark);
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe("dark");

    await userEvent.click(screen.getByRole("button", { name: "使用浅色" }));
    expect(screen.getByText("light:light")).toBeInTheDocument();
    expect(document.documentElement.dataset.theme).toBe("light");
    expect(meta.content).toBe(THEME_BROWSER_COLORS.light);
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe("light");
  });

  it("follows system theme changes while in system mode", () => {
    render(
      <ThemeProvider>
        <ThemeProbe />
      </ThemeProvider>,
    );

    const meta = themeColorMeta();
    expect(document.documentElement.dataset.theme).toBe("light");

    act(() => {
      mediaMatches = true;
      mediaListeners.forEach((listener) => {
        listener({ matches: true } as MediaQueryListEvent);
      });
    });

    expect(screen.getByText("system:dark")).toBeInTheDocument();
    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(meta.content).toBe(THEME_BROWSER_COLORS.dark);
  });
});
