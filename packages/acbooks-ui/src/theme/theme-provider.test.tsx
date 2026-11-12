import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { Button } from "../components/button";
import { ThemeProvider } from "./theme-provider";
import { THEME_STORAGE_KEY } from "./theme-types";
import { useTheme } from "./use-theme";

function ThemeProbe() {
  const { resolvedTheme, setTheme, theme } = useTheme();
  return (
    <>
      <output>{`${theme}:${resolvedTheme}`}</output>
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

beforeEach(() => {
  window.localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
  vi.stubGlobal(
    "matchMedia",
    vi.fn((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
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
});

describe("ThemeProvider", () => {
  it("applies the resolved theme and persists an explicit choice", async () => {
    render(
      <ThemeProvider>
        <ThemeProbe />
      </ThemeProvider>,
    );

    expect(screen.getByText("system:light")).toBeInTheDocument();
    expect(document.documentElement.dataset.theme).toBe("light");

    await userEvent.click(screen.getByRole("button", { name: "使用深色" }));

    expect(screen.getByText("dark:dark")).toBeInTheDocument();
    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe("dark");
  });
});
