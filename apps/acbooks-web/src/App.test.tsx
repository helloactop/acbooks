import { I18nProvider, ThemeProvider } from "@acbooks/ui";
import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import App from "./App";
import { appResources } from "./i18n/resources";

const fetchMock = vi.fn();

beforeEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.history.replaceState({}, "", "/");
});

function renderApp() {
  render(
    <ThemeProvider>
      <I18nProvider defaultLanguage="zh-CN" resources={appResources}>
        <App />
      </I18nProvider>
    </ThemeProvider>,
  );
}

describe("App routes", () => {
  it("renders the auth page at the root", async () => {
    window.history.replaceState({}, "", "/");
    fetchMock.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: { get: () => null },
      json: async () => ({ initialized: false }),
    });

    renderApp();

    expect(
      await screen.findByRole("heading", { level: 1, name: "初始化 acbooks" }),
    ).toBeInTheDocument();
  });

  it("keeps the component preview available at /preview", () => {
    window.history.replaceState({}, "", "/preview");

    renderApp();

    expect(
      screen.getByRole("heading", { level: 1, name: "公共组件预览" }),
    ).toBeInTheDocument();
  });

  it("renders the library at /library", async () => {
    window.history.replaceState({}, "", "/library");
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = String(input);
      const body = url.endsWith("/api/v1/auth/me")
        ? {
            id: "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f",
            username: "admin",
          }
        : { items: [], total: 0, limit: 12, offset: 0 };
      return {
        ok: true,
        status: 200,
        headers: { get: () => null },
        json: async () => body,
      };
    });

    renderApp();

    expect(
      await screen.findByRole("heading", { level: 1, name: "全部书籍" }),
    ).toBeInTheDocument();
    expect(
      await screen.findByText("书库还没有书籍"),
    ).toBeInTheDocument();
  });

  it("renders a book detail at /library/books/:bookId", async () => {
    const bookId = "11111111-1111-4111-8111-111111111111";
    window.history.replaceState({}, "", `/library/books/${bookId}`);
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = String(input);
      const body = url.endsWith("/api/v1/auth/me")
        ? {
            id: "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f",
            username: "admin",
          }
        : {
            id: bookId,
            title: "呐喊",
            authors: ["鲁迅"],
            translators: [],
            isbn: null,
            readingStatus: "unread",
            tags: [],
            shelves: [],
            copyCount: 0,
            fileCount: 0,
            formats: [],
            createdAt: 1_700_000_000_000,
            updatedAt: 1_700_000_000_000,
            description: null,
            identifiers: [],
            copies: [],
            files: [],
          };
      return {
        ok: true,
        status: 200,
        headers: { get: () => null },
        json: async () => body,
      };
    });

    renderApp();

    expect(
      await screen.findByRole("heading", { level: 1, name: "呐喊" }),
    ).toBeInTheDocument();
    expect(
      screen.getAllByRole("link", { name: "全部书籍" }).length,
    ).toBeGreaterThan(0);
  });
});
