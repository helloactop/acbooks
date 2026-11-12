import { I18nProvider } from "@acbooks/ui";
import {
  act,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { appResources } from "../../src/i18n/resources";
import { AuthenticatedLayout } from "../../src/layout/AuthenticatedLayout";
import type { BookDetail } from "../../src/library/books-api";
import { BookDetailPage } from "../../src/library/BookDetailPage";

const ACCOUNT = {
  id: "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f",
  username: "admin",
};

const BOOK_ID = "11111111-1111-4111-8111-111111111111";

const BOOK: BookDetail = {
  id: BOOK_ID,
  title: "一个足够长的书名用来验证详情页标题换行",
  authors: ["作者甲", "作者乙"],
  translators: ["译者甲"],
  isbn: "9787020002207",
  readingStatus: "reading",
  tags: ["文学", "经典"],
  shelves: ["书房"],
  copyCount: 2,
  fileCount: 2,
  formats: ["epub", "pdf"],
  createdAt: 1_700_000_000_000,
  updatedAt: 1_700_000_100_000,
  description: "第一段简介。\n\n第二段简介。",
  identifiers: [{ scheme: "isbn", value: "9787020002207" }],
  copies: [
    {
      id: "aaaaaaaa-aaaa-4aaa-8aaa-000000000001",
      locationId: null,
      locationName: "书房书架",
      acquiredAt: 1_690_000_000_000,
      note: "签名本",
    },
    {
      id: "aaaaaaaa-aaaa-4aaa-8aaa-000000000002",
      locationId: null,
      locationName: null,
      acquiredAt: null,
      note: null,
    },
  ],
  files: [
    {
      id: "bbbbbbbb-bbbb-4bbb-8bbb-000000000001",
      format: "epub",
      fileStatus: "present",
      sizeBytes: 2048,
    },
    {
      id: "bbbbbbbb-bbbb-4bbb-8bbb-000000000002",
      format: "pdf",
      fileStatus: "missing",
      sizeBytes: 1_048_576,
    },
  ],
};

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => null },
    json: async () => body,
  } as unknown as Response;
}

function problemResponse(status: number, code: string): Response {
  return jsonResponse(
    {
      type: "about:blank",
      title: "",
      status,
      code,
      detail: "",
      requestId: "request-detail",
    },
    status,
  );
}

type ApiHandler = (
  url: URL,
  init: RequestInit,
) => Response | Promise<Response>;

const fetchMock = vi.fn();

function methodOf(init: RequestInit): string {
  return init.method ?? "GET";
}

function installApi(handler: ApiHandler) {
  fetchMock.mockImplementation(
    async (input: RequestInfo | URL, init?: RequestInit) =>
      handler(new URL(String(input), "http://localhost"), init ?? {}),
  );
}

function baseHandler(handler: ApiHandler): ApiHandler {
  return (url, init) => {
    const method = methodOf(init);
    if (url.pathname === "/api/v1/auth/me" && method === "GET") {
      return jsonResponse(ACCOUNT);
    }
    if (url.pathname === "/api/v1/auth/csrf" && method === "GET") {
      return jsonResponse({ csrfToken: "csrf-token" });
    }
    return handler(url, init);
  };
}

function findCall(method: string, pathname: string) {
  return fetchMock.mock.calls.find(([input, init]) => {
    const requestInit = (init ?? {}) as RequestInit;
    return String(input) === pathname && methodOf(requestInit) === method;
  });
}

function renderDetail(
  language: "zh-CN" | "en" = "zh-CN",
  bookId = BOOK_ID,
) {
  const onLoggedOut = vi.fn();
  const onSessionExpired = vi.fn();
  const onUnauthenticated = vi.fn();
  render(
    <I18nProvider defaultLanguage={language} resources={appResources}>
      <MemoryRouter initialEntries={[`/library/books/${bookId}`]}>
        <Routes>
          <Route
            element={
              <AuthenticatedLayout
                onLoggedOut={onLoggedOut}
                onSessionExpired={onSessionExpired}
                onUnauthenticated={onUnauthenticated}
              />
            }
          >
            <Route
              element={<BookDetailPage bookId={bookId} />}
              path="/library/books/:bookId"
            />
          </Route>
        </Routes>
      </MemoryRouter>
    </I18nProvider>,
  );
  return { onLoggedOut, onSessionExpired, onUnauthenticated };
}

const ORIGINAL_SCROLL_HEIGHT = Object.getOwnPropertyDescriptor(
  Element.prototype,
  "scrollHeight",
);

function mockDescriptionMeasure(scrollHeight: number) {
  Object.defineProperty(Element.prototype, "scrollHeight", {
    configurable: true,
    get(this: Element) {
      return this.hasAttribute("data-description-measure") ? scrollHeight : 0;
    },
  });
  const original = window.getComputedStyle.bind(window);
  vi.spyOn(window, "getComputedStyle").mockImplementation((element) => {
    const style = original(element);
    return new Proxy(style, {
      get(target, property) {
        if (property === "lineHeight") {
          return "24px";
        }
        const value = Reflect.get(target, property) as unknown;
        return typeof value === "function" ? value.bind(target) : value;
      },
    });
  });
}

beforeEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(window.navigator, "languages", "get").mockReturnValue(["zh-CN"]);
  window.history.replaceState({}, "", `/library/books/${BOOK_ID}`);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  if (ORIGINAL_SCROLL_HEIGHT !== undefined) {
    Object.defineProperty(
      Element.prototype,
      "scrollHeight",
      ORIGINAL_SCROLL_HEIGHT,
    );
  }
  window.history.replaceState({}, "", "/");
});

describe("BookDetailPage", () => {
  it("renders the real book, metadata and information tab", async () => {
    installApi(
      baseHandler((url, init) => {
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          methodOf(init) === "GET"
        ) {
          return jsonResponse(BOOK);
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    renderDetail();

    expect(
      await screen.findByRole("heading", { level: 1, name: BOOK.title }),
    ).toBeInTheDocument();
    expect(screen.getByText("译者：译者甲")).toBeInTheDocument();
    expect(
      screen.getByRole("link", {
        name: "按关键词搜索 作者甲（不是精确作者筛选）",
      }),
    ).toHaveAttribute("href", `/library?q=${encodeURIComponent("作者甲")}`);
    expect(screen.getByText("9787020002207")).toBeInTheDocument();
    expect(screen.getAllByText("文学").length).toBeGreaterThan(0);
    expect(
      screen.getByRole("link", { name: "文学" }),
    ).toHaveAttribute("href", `/library?tag=${encodeURIComponent("文学")}`);
    expect(screen.getByText("经典")).toBeInTheDocument();
    expect(screen.getByText("书房")).toBeInTheDocument();
    expect(
      within(screen.getByRole("navigation", { name: "面包屑" })).getByRole(
        "link",
        { name: "全部书籍" },
      ),
    ).toHaveAttribute("href", "/library");

    expect(
      screen.getByRole("tab", { name: "信息" }),
    ).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("实体副本")).toBeInTheDocument();
    expect(screen.getByText("书房书架")).toBeInTheDocument();
    expect(screen.getByText("未设置")).toBeInTheDocument();
    expect(screen.getByText("签名本")).toBeInTheDocument();
    expect(screen.getByText("电子文件")).toBeInTheDocument();
    expect(screen.getByText("文件 1")).toBeInTheDocument();
    expect(screen.getByText("文件 2")).toBeInTheDocument();
    expect(screen.getByText("可用")).toBeInTheDocument();
    expect(screen.getByText("缺失")).toBeInTheDocument();
    expect(screen.getByText("2 KB")).toBeInTheDocument();
    expect(screen.getByText("1 MB")).toBeInTheDocument();
    expect(screen.queryAllByRole("progressbar")).toHaveLength(0);
    expect(
      screen.getByRole("button", { name: "编辑" }),
    ).toBeInTheDocument();
  });

  it("shows explicit unavailable panels for reading and notes", async () => {
    installApi(
      baseHandler((url, init) => {
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          methodOf(init) === "GET"
        ) {
          return jsonResponse(BOOK);
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole("heading", { level: 1, name: BOOK.title });

    await user.click(screen.getByRole("tab", { name: "阅读" }));
    expect(
      screen.getByRole("heading", { name: "阅读功能尚未开放" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("实体副本")).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "笔记" }));
    expect(
      screen.getByRole("heading", { name: "笔记功能尚未开放" }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "信息" }));
    expect(screen.getByText("实体副本")).toBeInTheDocument();
  });

  it("expands a long description and remembers it per book", async () => {
    mockDescriptionMeasure(200);
    installApi(
      baseHandler((url, init) => {
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          methodOf(init) === "GET"
        ) {
          return jsonResponse(BOOK);
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole("heading", { level: 1, name: BOOK.title });

    const expand = await screen.findByRole("button", { name: "展开简介" });
    expect(expand).toHaveAttribute("aria-expanded", "false");
    await user.click(expand);

    const collapse = await screen.findByRole("button", { name: "收起简介" });
    expect(collapse).toHaveAttribute("aria-expanded", "true");
    expect(
      window.sessionStorage.getItem(`acbooks-description-expanded:${BOOK_ID}`),
    ).toBe("true");

    await user.click(collapse);
    expect(
      await screen.findByRole("button", { name: "展开简介" }),
    ).toBeInTheDocument();
    expect(
      window.sessionStorage.getItem(`acbooks-description-expanded:${BOOK_ID}`),
    ).toBe("false");
  });

  it("does not show an expand control for short or missing descriptions", async () => {
    mockDescriptionMeasure(48);
    let current: BookDetail = { ...BOOK, description: "很短" };
    installApi(
      baseHandler((url, init) => {
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          methodOf(init) === "GET"
        ) {
          return jsonResponse(current);
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const { unmount } = render(
      <I18nProvider defaultLanguage="zh-CN" resources={appResources}>
        <MemoryRouter initialEntries={[`/library/books/${BOOK_ID}`]}>
          <Routes>
            <Route element={<AuthenticatedLayout />}>
              <Route
                element={<BookDetailPage bookId={BOOK_ID} />}
                path="/library/books/:bookId"
              />
            </Route>
          </Routes>
        </MemoryRouter>
      </I18nProvider>,
    );
    await screen.findByRole("heading", { level: 1, name: BOOK.title });
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 40));
    });
    expect(
      screen.queryByRole("button", { name: "展开简介" }),
    ).not.toBeInTheDocument();

    unmount();
    window.sessionStorage.clear();
    current = { ...BOOK, description: null };
    renderDetail();
    expect(
      await screen.findByRole("heading", { level: 1, name: BOOK.title }),
    ).toBeInTheDocument();
    expect(await screen.findByText("暂无简介")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "展开简介" }),
    ).not.toBeInTheDocument();
  });

  it("updates the reading status with only the changed field", async () => {
    let current = { ...BOOK };
    installApi(
      baseHandler((url, init) => {
        const method = methodOf(init);
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          method === "GET"
        ) {
          return jsonResponse(current);
        }
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          method === "PATCH"
        ) {
          const body = JSON.parse(String(init.body)) as Partial<BookDetail>;
          current = { ...current, ...body, updatedAt: BOOK.updatedAt + 1 };
          return jsonResponse(current);
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole("heading", { level: 1, name: BOOK.title });

    const select = screen.getByRole("combobox", { name: "阅读状态" });
    expect(select).toHaveValue("reading");
    await user.selectOptions(select, "done");

    await waitFor(() => {
      expect(select).toHaveValue("done");
    });
    const patch = findCall("PATCH", `/api/v1/books/${BOOK_ID}`);
    expect(patch).toBeDefined();
    const init = (patch?.[1] ?? {}) as RequestInit;
    expect(
      (init.headers as Record<string, string>)["X-CSRF-Token"],
    ).toBe("csrf-token");
    expect(JSON.parse(String(init.body))).toEqual({ readingStatus: "done" });
  });

  it("keeps the original reading status when the update fails", async () => {
    installApi(
      baseHandler((url, init) => {
        const method = methodOf(init);
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          method === "GET"
        ) {
          return jsonResponse(BOOK);
        }
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          method === "PATCH"
        ) {
          return problemResponse(500, "INTERNAL_ERROR");
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole("heading", { level: 1, name: BOOK.title });

    const select = screen.getByRole("combobox", { name: "阅读状态" });
    await user.selectOptions(select, "done");

    expect(
      await screen.findByText("服务器暂时无法完成请求，请稍后重试。"),
    ).toBeInTheDocument();
    expect(select).toHaveValue("reading");
  });

  it("edits authors through token chips instead of comma text", async () => {
    installApi(
      baseHandler((url, init) => {
        const method = methodOf(init);
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          method === "GET"
        ) {
          return jsonResponse(BOOK);
        }
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          method === "PATCH"
        ) {
          return jsonResponse(BOOK);
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole("heading", { level: 1, name: BOOK.title });

    await user.click(screen.getByRole("button", { name: "编辑" }));
    const dialog = await screen.findByRole("dialog", {
      name: "编辑书籍",
    });
    expect(within(dialog).getByText("作者甲")).toBeInTheDocument();
    expect(within(dialog).getByText("作者乙")).toBeInTheDocument();

    await user.click(
      within(dialog).getByRole("button", { name: "移除 作者乙" }),
    );
    await user.type(
      within(dialog).getByLabelText("作者"),
      "作者丙{Enter}",
    );
    await user.click(within(dialog).getByRole("button", { name: "保存" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    const patch = findCall("PATCH", `/api/v1/books/${BOOK_ID}`);
    const init = (patch?.[1] ?? {}) as RequestInit;
    expect(JSON.parse(String(init.body))).toEqual({
      authors: ["作者甲", "作者丙"],
    });
  });

  it("edits the book with a partial patch and refreshes the detail", async () => {
    let current = { ...BOOK };
    installApi(
      baseHandler((url, init) => {
        const method = methodOf(init);
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          method === "GET"
        ) {
          return jsonResponse(current);
        }
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          method === "PATCH"
        ) {
          const body = JSON.parse(String(init.body)) as Partial<BookDetail>;
          current = { ...current, ...body, updatedAt: BOOK.updatedAt + 1 };
          return jsonResponse(current);
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole("heading", { level: 1, name: BOOK.title });

    await user.click(screen.getByRole("button", { name: "编辑" }));
    const dialog = await screen.findByRole("dialog", {
      name: "编辑书籍",
    });
    const title = within(dialog).getByLabelText("书名");
    expect(title).toHaveValue(BOOK.title);
    await user.clear(title);
    await user.type(title, "修订后的书名");
    await user.click(within(dialog).getByRole("button", { name: "保存" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(
      await screen.findByRole("heading", { level: 1, name: "修订后的书名" }),
    ).toBeInTheDocument();

    const patch = findCall("PATCH", `/api/v1/books/${BOOK_ID}`);
    expect(patch).toBeDefined();
    const init = (patch?.[1] ?? {}) as RequestInit;
    expect(
      (init.headers as Record<string, string>)["X-CSRF-Token"],
    ).toBe("csrf-token");
    expect(JSON.parse(String(init.body))).toEqual({ title: "修订后的书名" });
  });

  it("does not send an empty patch when the form is unchanged", async () => {
    installApi(
      baseHandler((url, init) => {
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          methodOf(init) === "GET"
        ) {
          return jsonResponse(BOOK);
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole("heading", { level: 1, name: BOOK.title });

    await user.click(screen.getByRole("button", { name: "编辑" }));
    const dialog = await screen.findByRole("dialog", {
      name: "编辑书籍",
    });
    await user.click(within(dialog).getByRole("button", { name: "保存" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(findCall("PATCH", `/api/v1/books/${BOOK_ID}`)).toBeUndefined();
    expect(
      screen.getByRole("heading", { level: 1, name: BOOK.title }),
    ).toBeInTheDocument();
  });

  it("retries after a detail request fails", async () => {
    let failed = false;
    installApi(
      baseHandler((url, init) => {
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          methodOf(init) === "GET"
        ) {
          if (!failed) {
            failed = true;
            return problemResponse(500, "INTERNAL_ERROR");
          }
          return jsonResponse(BOOK);
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    renderDetail();

    expect(
      await screen.findByText("服务器暂时无法完成请求，请稍后重试。"),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(
      await screen.findByRole("heading", { level: 1, name: BOOK.title }),
    ).toBeInTheDocument();
  });

  it("shows a not found state for a missing book", async () => {
    installApi(
      baseHandler((url, init) => {
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          methodOf(init) === "GET"
        ) {
          return problemResponse(404, "BOOK_NOT_FOUND");
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    renderDetail();

    expect(
      await screen.findByRole("heading", { level: 1, name: "书籍不存在" }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "全部书籍" }).length).toBeGreaterThan(
      0,
    );
  });

  it("redirects when the session expires", async () => {
    installApi(
      baseHandler((url, init) => {
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          methodOf(init) === "GET"
        ) {
          return problemResponse(401, "UNAUTHORIZED");
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const { onSessionExpired } = renderDetail();

    await waitFor(() => {
      expect(onSessionExpired).toHaveBeenCalledOnce();
    });
  });

  it("supports keyboard tab navigation and closing the action menu", async () => {
    installApi(
      baseHandler((url, init) => {
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          methodOf(init) === "GET"
        ) {
          return jsonResponse(BOOK);
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole("heading", { level: 1, name: BOOK.title });

    const infoTab = screen.getByRole("tab", { name: "信息" });
    infoTab.focus();
    await user.keyboard("{ArrowRight}");
    const readingTab = screen.getByRole("tab", { name: "阅读" });
    expect(readingTab).toHaveAttribute("aria-selected", "true");
    expect(readingTab).toHaveFocus();

    const more = screen.getByRole("button", { name: "更多操作" });
    await user.click(more);
    expect(screen.getByRole("menu")).toBeInTheDocument();
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(more).toHaveFocus();
  });

  it("renders the detail page in English", async () => {
    installApi(
      baseHandler((url, init) => {
        if (
          url.pathname === `/api/v1/books/${BOOK_ID}` &&
          methodOf(init) === "GET"
        ) {
          return jsonResponse(BOOK);
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    renderDetail("en");

    expect(
      await screen.findByRole("heading", { level: 1, name: BOOK.title }),
    ).toBeInTheDocument();
    expect(screen.getByText("Translated by 译者甲")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Information" })).toBeInTheDocument();
    expect(screen.getByText("Physical copies")).toBeInTheDocument();
    expect(screen.getByText("Electronic files")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Edit" })).toBeInTheDocument();
  });
});
