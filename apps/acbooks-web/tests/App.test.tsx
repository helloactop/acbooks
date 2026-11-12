import { I18nProvider, ThemeProvider } from "@acbooks/ui";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect } from "react";
import { MemoryRouter, useLocation, useNavigate } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import App from "../src/App";
import { appResources } from "../src/i18n/resources";

const SECOND_BOOK_ID = "22222222-2222-4222-8222-222222222222";

const fetchMock = vi.fn();
let currentLocation = "";

function LocationProbe() {
  const location = useLocation();
  useEffect(() => {
    currentLocation = `${location.pathname}${location.search}`;
  }, [location.pathname, location.search]);
  return null;
}

function HistoryControls() {
  const navigate = useNavigate();
  return (
    <>
      <button
        onClick={() => {
          navigate(-1);
        }}
        type="button"
      >
        history-back
      </button>
      <button
        onClick={() => {
          navigate(1);
        }}
        type="button"
      >
        history-forward
      </button>
      <button
        onClick={() => {
          navigate(`/library/books/${SECOND_BOOK_ID}`);
        }}
        type="button"
      >
        open-second-detail
      </button>
    </>
  );
}

beforeEach(() => {
  currentLocation = "";
  window.localStorage.clear();
  window.sessionStorage.clear();
  document.title = "acbooks";
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  document.documentElement.lang = "zh-CN";
  delete document.documentElement.dataset.theme;
  document.title = "acbooks";
  window.history.replaceState({}, "", "/");
});

function renderApp() {
  const entry = `${window.location.pathname}${window.location.search}`;
  render(
    <ThemeProvider>
      <I18nProvider defaultLanguage="zh-CN" resources={appResources}>
        <MemoryRouter initialEntries={[entry]}>
          <LocationProbe />
          <HistoryControls />
          <App />
        </MemoryRouter>
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

  it("renders the dashboard at /home", async () => {
    window.history.replaceState({}, "", "/home");
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
      await screen.findByRole("heading", { level: 1, name: "首页" }),
    ).toBeInTheDocument();
    expect(
      await screen.findByText("书库还没有书籍"),
    ).toBeInTheDocument();
  });

  it("shows recently added books on the dashboard", async () => {
    window.history.replaceState({}, "", "/home");
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = String(input);
      const body = url.endsWith("/api/v1/auth/me")
        ? {
            id: "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f",
            username: "admin",
          }
        : {
            items: [
              {
                id: "11111111-1111-4111-8111-111111111111",
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
              },
            ],
            total: 1,
            limit: 12,
            offset: 0,
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
      await screen.findByRole("heading", { level: 1, name: "首页" }),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("article", { name: "呐喊" }),
    ).toBeInTheDocument();
    expect(screen.getByText("最近加入")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "查看全部" })).toHaveAttribute(
      "href",
      "/library?mediaType=all&reset=1",
    );
  });

  it("renders personal settings at /settings", async () => {
    window.history.replaceState({}, "", "/settings");
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      headers: { get: () => null },
      json: async () => ({
        id: "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f",
        username: "admin",
      }),
    });

    renderApp();

    expect(
      await screen.findByRole("heading", { level: 1, name: "设置" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "账户" }),
    ).toBeInTheDocument();
  });

  it("applies theme and language preferences from settings", async () => {
    window.history.replaceState({}, "", "/settings");
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      headers: { get: () => null },
      json: async () => ({
        id: "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f",
        username: "admin",
      }),
    });
    const user = userEvent.setup();
    renderApp();
    await screen.findByRole("heading", { level: 1, name: "设置" });

    await user.selectOptions(screen.getByLabelText("主题"), "dark");
    await waitFor(() => {
      expect(document.documentElement.dataset.theme).toBe("dark");
    });

    await user.selectOptions(screen.getByLabelText("界面语言"), "en");
    await waitFor(() => {
      expect(document.documentElement.lang).toBe("en");
    });
    expect(
      await screen.findByRole("heading", { level: 1, name: "Settings" }),
    ).toBeInTheDocument();
  });

  it("updates the document title from the navigation context", async () => {
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
    const user = userEvent.setup();
    renderApp();

    expect(
      await screen.findByRole("heading", { level: 1, name: "全部书籍" }),
    ).toBeInTheDocument();
    await waitFor(() => {
      expect(document.title).toBe("全部书籍 · acbooks");
    });

    await user.click(
      screen.getAllByRole("link", { name: "实体书" })[0]!,
    );
    await waitFor(() => {
      expect(document.title).toBe("实体书 · acbooks");
    });

    await user.click(screen.getAllByRole("link", { name: "首页" })[0]!);
    await waitFor(() => {
      expect(document.title).toBe("首页 · acbooks");
    });

    await user.click(screen.getByRole("link", { name: "账户与设置：admin" }));
    await waitFor(() => {
      expect(document.title).toBe("设置 · acbooks");
    });
    await user.click(screen.getAllByRole("link", { name: "外观" })[0]!);
    await waitFor(() => {
      expect(document.title).toBe("外观 · 设置 · acbooks");
    });
  });

  it("renders a safe not-found page for unknown routes", () => {
    window.history.replaceState({}, "", "/not-a-real-page");

    renderApp();

    expect(
      screen.getByRole("heading", { level: 1, name: "页面不存在" }),
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

  it("restores search pagination and filters after returning from a book detail", async () => {
    const bookId = "11111111-1111-4111-8111-111111111111";
    const filters = encodeURIComponent(
      JSON.stringify({
        combinator: "ANY",
        conditions: [{ field: "tag", operator: "equals", value: "文学" }],
      }),
    );
    const libraryPath = `/library?q=${encodeURIComponent("三体")}&readingStatus=reading&sort=title&view=list&offset=12&filters=${filters}`;
    window.history.replaceState({}, "", libraryPath);
    const book = {
      id: bookId,
      title: "呐喊",
      authors: ["鲁迅"],
      translators: [],
      isbn: null,
      readingStatus: "reading",
      tags: ["文学"],
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
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      let body: unknown;
      if (url.pathname === "/api/v1/auth/me") {
        body = {
          id: "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f",
          username: "admin",
        };
      } else if (url.pathname === "/api/v1/books/filter-schema") {
        body = {
          fields: [
            {
              key: "tag",
              group: "metadata",
              type: "select",
              operators: ["equals", "not_equals"],
              optionSource: "tags",
              allowCustom: true,
              options: [],
            },
          ],
          maxConditions: 30,
        };
      } else if (url.pathname === "/api/v1/books/filter-options") {
        body = {
          source: url.searchParams.get("source"),
          query: url.searchParams.get("query") ?? "",
          options: [],
          hasMore: false,
          indexReady: true,
        };
      } else if (url.pathname === `/api/v1/books/${bookId}`) {
        body = book;
      } else if (url.pathname === "/api/v1/books") {
        body = {
          items: [book],
          total: 30,
          limit: 12,
          offset: 12,
        };
      } else {
        body = {};
      }
      return {
        ok: true,
        status: 200,
        headers: { get: () => null },
        json: async () => body,
      };
    });
    const user = userEvent.setup();
    renderApp();

    await screen.findByRole("table", { name: "书籍列表" });
    const listRequests = () =>
      fetchMock.mock.calls.flatMap(([input]) => {
        const url = new URL(String(input), "http://localhost");
        return url.pathname === "/api/v1/books" ? [url] : [];
      });
    const initialList = listRequests().at(-1);
    expect(initialList?.searchParams.get("q")).toBe("三体");
    expect(initialList?.searchParams.get("offset")).toBe("12");

    await user.click(
      screen.getByRole("link", { name: "查看《呐喊》详情" }),
    );
    expect(
      await screen.findByRole("heading", { level: 1, name: "呐喊" }),
    ).toBeInTheDocument();

    const main = screen.getByRole("main");
    const beforeReturn = listRequests().length;
    await user.click(
      within(main).getByRole("link", { name: "全部书籍" }),
    );
    await screen.findByRole("table", { name: "书籍列表" });
    await waitFor(() => {
      expect(listRequests().length).toBeGreaterThan(beforeReturn);
      expect(currentLocation).toContain(`q=${encodeURIComponent("三体")}`);
      expect(currentLocation).toContain("readingStatus=reading");
      expect(currentLocation).toContain("sort=title");
      expect(currentLocation).toContain("view=list");
      expect(currentLocation).toContain("offset=12");
      expect(currentLocation).toContain("filters=");
    });
    const restoredList = listRequests().at(-1);
    expect(restoredList?.searchParams.get("q")).toBe("三体");
    expect(restoredList?.searchParams.get("offset")).toBe("12");
  });

  it("paginates search results without losing the query", async () => {
    window.history.replaceState(
      {},
      "",
      `/library?q=${encodeURIComponent("三体")}`,
    );
    const book = {
      id: "11111111-1111-4111-8111-111111111111",
      title: "三体",
      authors: ["刘慈欣"],
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
    };
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      let body: unknown = {};
      if (url.pathname === "/api/v1/auth/me") {
        body = {
          id: "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f",
          username: "admin",
        };
      } else if (url.pathname === "/api/v1/books/filter-schema") {
        body = { fields: [], maxConditions: 30 };
      } else if (url.pathname === "/api/v1/books") {
        body = {
          items: [book],
          total: 30,
          limit: 12,
          offset: Number(url.searchParams.get("offset") ?? "0"),
        };
      }
      return {
        ok: true,
        status: 200,
        headers: { get: () => null },
        json: async () => body,
      };
    });
    const user = userEvent.setup();
    renderApp();

    await screen.findByRole("article", { name: "三体" });
    expect(screen.getByText("1 / 3 页")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "下一页" }));
    await waitFor(() => {
      expect(currentLocation).toContain("offset=12");
      expect(currentLocation).toContain(`q=${encodeURIComponent("三体")}`);
    });
    const request = fetchMock.mock.calls
      .map(([input]) => new URL(String(input), "http://localhost"))
      .filter((url) => url.pathname === "/api/v1/books")
      .at(-1);
    expect(request?.searchParams.get("q")).toBe("三体");
    expect(request?.searchParams.get("offset")).toBe("12");
    expect(await screen.findByText("2 / 3 页")).toBeInTheDocument();
  });

  it("filters the library from a detail tag", async () => {
    const bookId = "11111111-1111-4111-8111-111111111111";
    window.history.replaceState({}, "", `/library/books/${bookId}`);
    const book = {
      id: bookId,
      title: "呐喊",
      authors: ["鲁迅"],
      translators: [],
      isbn: null,
      readingStatus: "unread",
      tags: ["文学"],
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
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      let body: unknown = {};
      if (url.pathname === "/api/v1/auth/me") {
        body = {
          id: "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f",
          username: "admin",
        };
      } else if (url.pathname === `/api/v1/books/${bookId}`) {
        body = book;
      } else if (url.pathname === "/api/v1/books/filter-schema") {
        body = {
          fields: [
            {
              key: "tag",
              group: "metadata",
              type: "select",
              operators: ["equals", "not_equals"],
              optionSource: "tags",
              allowCustom: true,
              options: [],
            },
          ],
          maxConditions: 30,
        };
      } else if (url.pathname === "/api/v1/books/filter-options") {
        body = {
          source: url.searchParams.get("source"),
          query: url.searchParams.get("query") ?? "",
          options: [],
          hasMore: false,
          indexReady: true,
        };
      } else if (url.pathname === "/api/v1/books") {
        body = { items: [book], total: 1, limit: 12, offset: 0 };
      }
      return {
        ok: true,
        status: 200,
        headers: { get: () => null },
        json: async () => body,
      };
    });
    const user = userEvent.setup();
    renderApp();

    expect(
      await screen.findByRole("heading", { level: 1, name: "呐喊" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("link", { name: "文学" }));

    await waitFor(() => {
      expect(currentLocation).toContain("filters=");
    });
    const listRequest = fetchMock.mock.calls
      .map(([input]) => new URL(String(input), "http://localhost"))
      .filter((url) => url.pathname === "/api/v1/books")
      .at(-1);
    expect(listRequest?.searchParams.get("filters")).toContain("文学");
    expect(
      await screen.findByRole("article", { name: "呐喊" }),
    ).toBeInTheDocument();
  });

  it("isolates detail state when switching to another book", async () => {
    const firstId = "11111111-1111-4111-8111-111111111111";
    window.history.replaceState({}, "", `/library/books/${firstId}`);
    const firstBook = {
      id: firstId,
      title: "呐喊",
      authors: ["鲁迅"],
      translators: [],
      isbn: null,
      readingStatus: "reading",
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
    const secondBook = {
      ...firstBook,
      id: SECOND_BOOK_ID,
      title: "边城",
      authors: ["沈从文"],
      readingStatus: "unread",
    };
    let resolveSecond: ((response: Response) => void) | undefined;
    fetchMock.mockImplementation((input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      const respond = (body: unknown) =>
        Promise.resolve({
          ok: true,
          status: 200,
          headers: { get: () => null },
          json: async () => body,
        } as unknown as Response);
      if (url.pathname === "/api/v1/auth/me") {
        return respond({
          id: "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f",
          username: "admin",
        });
      }
      if (url.pathname === `/api/v1/books/${firstId}`) {
        return respond(firstBook);
      }
      if (url.pathname === `/api/v1/books/${SECOND_BOOK_ID}`) {
        return new Promise<Response>((resolve) => {
          resolveSecond = resolve;
        });
      }
      return respond({});
    });
    const user = userEvent.setup();
    renderApp();

    expect(
      await screen.findByRole("heading", { level: 1, name: "呐喊" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "编辑" }));
    expect(
      await screen.findByRole("dialog", { name: "编辑书籍" }),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: "open-second-detail" }),
    );
    expect(
      screen.queryByRole("heading", { level: 1, name: "呐喊" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getAllByText("正在载入书籍详情").length).toBeGreaterThan(0);

    await act(async () => {
      resolveSecond?.({
        ok: true,
        status: 200,
        headers: { get: () => null },
        json: async () => secondBook,
      } as unknown as Response);
    });
    expect(
      await screen.findByRole("heading", { level: 1, name: "边城" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { level: 1, name: "呐喊" }),
    ).not.toBeInTheDocument();
  });
});
