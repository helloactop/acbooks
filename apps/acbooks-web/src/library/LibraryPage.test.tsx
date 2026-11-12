import { I18nProvider } from "@acbooks/ui";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { appResources } from "../i18n/resources";
import { AuthenticatedLayout } from "../layout/AuthenticatedLayout";
import type { BookDetail, BookPage, BookSummary } from "./books-api";
import { LibraryPage } from "./LibraryPage";

const ACCOUNT = {
  id: "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f",
  username: "admin",
};

function summary(overrides: Partial<BookSummary> = {}): BookSummary {
  return {
    id: "99999999-9999-4999-8999-999999999999",
    title: "示例书",
    authors: ["作者"],
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
    ...overrides,
  };
}

const BOOK1 = summary({
  id: "11111111-1111-4111-8111-111111111111",
  title: "呐喊",
  authors: ["鲁迅"],
  translators: ["译者甲"],
  isbn: "9787020002207",
  readingStatus: "reading",
  tags: ["文学"],
  shelves: ["书房"],
  copyCount: 2,
  fileCount: 2,
  formats: ["epub", "pdf"],
});
const FILTER_SCHEMA = {
  fields: [
    {
      key: "title",
      group: "metadata",
      type: "text",
      operators: ["contains", "equals", "is_empty"],
      allowCustom: false,
      options: [],
    },
    {
      key: "author",
      group: "metadata",
      type: "select",
      operators: ["equals", "not_equals"],
      optionSource: "authors",
      allowCustom: true,
      options: [],
    },
    {
      key: "tag",
      group: "metadata",
      type: "select",
      operators: ["equals", "not_equals"],
      optionSource: "tags",
      allowCustom: true,
      options: [],
    },
    {
      key: "format",
      group: "files",
      type: "select",
      operators: ["equals", "not_equals"],
      optionSource: "formats",
      allowCustom: true,
      options: [],
    },
    {
      key: "readingStatus",
      group: "reading",
      type: "select",
      operators: ["equals", "not_equals"],
      allowCustom: false,
      options: [
        { value: "unread", label: "Unread", count: null },
        { value: "reading", label: "Reading", count: null },
        { value: "done", label: "Read", count: null },
      ],
    },
    {
      key: "copyCount",
      group: "files",
      type: "number",
      operators: ["equals", "greater_than", "between"],
      allowCustom: false,
      options: [],
    },
    {
      key: "createdAt",
      group: "timeline",
      type: "date",
      operators: ["equals", "between"],
      allowCustom: false,
      options: [],
    },
    {
      key: "hasCover",
      group: "timeline",
      type: "boolean",
      operators: ["is_true", "is_false"],
      allowCustom: false,
      options: [],
    },
  ],
  maxConditions: 30,
};

const FILTER_OPTIONS: Record<string, Array<{ value: string; label: string; count: number }>> = {
  authors: [
    { value: "鲁迅", label: "鲁迅", count: 2 },
    { value: "沈从文", label: "沈从文", count: 1 },
  ],
  tags: [
    { value: "文学", label: "文学", count: 2 },
    { value: "历史", label: "历史", count: 1 },
  ],
  formats: [
    { value: "epub", label: "EPUB", count: 2 },
    { value: "pdf", label: "PDF", count: 1 },
  ],
  shelves: [
    { value: "书房", label: "书房", count: 2 },
  ],
  translators: [],
};

const BOOK2 = summary({
  id: "22222222-2222-4222-8222-222222222222",
  title: "边城",
  authors: ["沈从文"],
  readingStatus: "done",
  tags: ["文学"],
  fileCount: 1,
  formats: ["epub"],
});

function detailFor(
  book: BookSummary,
  overrides: Partial<BookDetail> = {},
): BookDetail {
  return {
    ...book,
    description: "简介",
    identifiers: book.isbn === null ? [] : [{ scheme: "isbn", value: book.isbn }],
    copies: Array.from({ length: book.copyCount }, (_, index) => ({
      id: `aaaaaaaa-aaaa-4aaa-8aaa-${String(index + 1).padStart(12, "0")}`,
      locationId: null,
      locationName: null,
      acquiredAt: null,
      note: "副本",
    })),
    files: book.formats.map((format, index) => ({
      id: `bbbbbbbb-bbbb-4bbb-8bbb-${String(index + 1).padStart(12, "0")}`,
      format,
      fileStatus: "present",
      sizeBytes: 2048,
    })),
    ...overrides,
  };
}

function makePage(
  items: BookSummary[],
  total = items.length,
  offset = 0,
): BookPage {
  return { items, total, limit: 12, offset };
}

function makeBooks(count: number): BookSummary[] {
  return Array.from({ length: count }, (_, index) =>
    summary({
      id: `00000000-0000-4000-8000-${String(index + 1).padStart(12, "0")}`,
      title: `Book ${index + 1}`,
      authors: [`Author ${index + 1}`],
    }),
  );
}

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
      requestId: "request-library",
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
    if (
      url.pathname === "/api/v1/books/filter-schema" &&
      method === "GET"
    ) {
      return jsonResponse(FILTER_SCHEMA);
    }
    if (
      url.pathname === "/api/v1/books/filter-options" &&
      method === "GET"
    ) {
      const source = url.searchParams.get("source") ?? "";
      const query = url.searchParams.get("query") ?? "";
      const options = (FILTER_OPTIONS[source] ?? []).filter(
        (option) =>
          query.length === 0 ||
          option.label.toLocaleLowerCase().includes(query.toLocaleLowerCase()),
      );
      return jsonResponse({
        source,
        query,
        options,
        hasMore: false,
        indexReady: true,
      });
    }
    return handler(url, init);
  };
}

function bookRequests(): URL[] {
  return fetchMock.mock.calls.flatMap(([input, init]) => {
    const url = new URL(String(input), "http://localhost");
    const method = methodOf((init ?? {}) as RequestInit);
    return method === "GET" && url.pathname === "/api/v1/books"
      ? [url]
      : [];
  });
}

function latestBookRequest(): URL | undefined {
  return bookRequests().at(-1);
}

function latestFilters(): {
  combinator: string;
  conditions: Array<{ field: string; operator: string; value?: unknown }>;
} | null {
  const raw = latestBookRequest()?.searchParams.get("filters");
  return raw === null || raw === undefined
    ? null
    : (JSON.parse(raw) as {
        combinator: string;
        conditions: Array<{
          field: string;
          operator: string;
          value?: unknown;
        }>;
      });
}

function findCall(method: string, pathname: string) {
  return fetchMock.mock.calls.find(([input, init]) => {
    const requestInit = (init ?? {}) as RequestInit;
    return (
      String(input) === pathname && methodOf(requestInit) === method
    );
  });
}

beforeEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(window.navigator, "languages", "get").mockReturnValue(["zh-CN"]);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.history.replaceState({}, "", "/");
});

function renderLibrary(language: "zh-CN" | "en" = "zh-CN") {
  const onLoggedOut = vi.fn();
  const onSessionExpired = vi.fn();
  const onUnauthenticated = vi.fn();
  render(
    <I18nProvider defaultLanguage={language} resources={appResources}>
      <AuthenticatedLayout
        currentPath="/library"
        onLoggedOut={onLoggedOut}
        onSessionExpired={onSessionExpired}
        onUnauthenticated={onUnauthenticated}
      >
        <LibraryPage />
      </AuthenticatedLayout>
    </I18nProvider>,
  );
  return { onLoggedOut, onSessionExpired, onUnauthenticated };
}

describe("LibraryPage", () => {
  it("renders API books and keeps tags, formats and copies distinct", async () => {
    installApi(
      baseHandler((url, init) => {
        if (url.pathname === "/api/v1/books" && methodOf(init) === "GET") {
          return jsonResponse(makePage([BOOK1, BOOK2]));
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    renderLibrary();

    expect(
      await screen.findByRole("heading", { level: 1, name: "全部书籍" }),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("article", { name: "呐喊" }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("article")).toHaveLength(2);
    expect(
      screen.getByText("共 2 本，实体书与电子书统一管理"),
    ).toBeInTheDocument();
    expect(screen.getAllByText("文学").length).toBeGreaterThan(0);
    expect(screen.getByText("PDF")).toBeInTheDocument();
    expect(screen.getAllByText("EPUB")).toHaveLength(2);
    expect(screen.getByText("实体 ×2")).toBeInTheDocument();
    expect(screen.getAllByText("阅读中").length).toBeGreaterThan(0);
    expect(screen.getAllByText("已读").length).toBeGreaterThan(0);
    expect(screen.queryAllByRole("progressbar")).toHaveLength(0);
    expect(
      screen.getByRole("complementary", { name: "应用侧边栏" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "添加书籍" }),
    ).toBeEnabled();
    expect(
      screen.getByRole("link", { name: "账户与设置：admin" }),
    ).toBeInTheDocument();
  });

  it("sends search, smart filters, sorting and pagination to the server", async () => {
    installApi(
      baseHandler((url, init) => {
        if (url.pathname === "/api/v1/books" && methodOf(init) === "GET") {
          const offset = Number(url.searchParams.get("offset") ?? "0");
          return jsonResponse(makePage(makeBooks(12), 30, offset));
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    renderLibrary();
    await screen.findByRole("article", { name: "Book 1" });

    expect(latestBookRequest()?.searchParams.get("limit")).toBe("12");
    expect(latestBookRequest()?.searchParams.get("offset")).toBe("0");

    await user.click(screen.getByRole("button", { name: "下一页" }));
    await waitFor(() => {
      expect(latestBookRequest()?.searchParams.get("offset")).toBe("12");
    });

    const search = screen.getByRole("searchbox", { name: "搜索书库" });
    await user.type(search, "三体");
    await waitFor(() => {
      expect(latestBookRequest()?.searchParams.get("q")).toBe("三体");
    });
    expect(latestBookRequest()?.searchParams.get("offset")).toBe("0");

    await user.click(screen.getByRole("radio", { name: "已读" }));
    await waitFor(() => {
      expect(
        latestBookRequest()?.searchParams.get("readingStatus"),
      ).toBe("done");
    });

    const beforeDuplicate = bookRequests().length;
    await user.click(screen.getByRole("radio", { name: "已读" }));
    await user.click(screen.getByRole("radio", { name: "网格视图" }));
    expect(bookRequests()).toHaveLength(beforeDuplicate);

    await user.click(screen.getByRole("button", { name: "更多筛选" }));
    await user.click(
      await screen.findByRole("button", { name: /添加第一个筛选条件/ }),
    );

    await user.selectOptions(
      screen.getByRole("combobox", { name: "第 1 条筛选字段" }),
      "tag",
    );
    const tagValue = screen.getByRole("combobox", {
      name: "标签筛选值",
    });
    await user.type(tagValue, "文学");
    await waitFor(() => {
      expect(latestFilters()).toEqual({
        combinator: "ALL",
        conditions: [
          { field: "tag", operator: "equals", value: "文学" },
        ],
      });
    });
    expect(
      screen.getByRole("button", { name: /更多筛选，已启用 1 个条件/ }),
    ).toHaveTextContent("1");

    await user.click(screen.getByRole("button", { name: "添加条件" }));
    await user.selectOptions(
      screen.getByRole("combobox", { name: "第 2 条筛选字段" }),
      "format",
    );
    await user.type(
      screen.getByRole("combobox", { name: "文件格式筛选值" }),
      "epub",
    );
    await waitFor(() => {
      expect(latestFilters()?.conditions).toHaveLength(2);
      expect(latestBookRequest()?.searchParams.get("offset")).toBe("0");
    });
    expect(
      screen.getByRole("button", { name: /更多筛选，已启用 2 个条件/ }),
    ).toHaveTextContent("2");

    await user.selectOptions(
      screen.getByRole("combobox", { name: "条件组合方式" }),
      "ANY",
    );
    await waitFor(() => {
      expect(latestFilters()?.combinator).toBe("ANY");
    });

    await user.selectOptions(
      screen.getByRole("combobox", { name: "排序" }),
      "title",
    );
    await waitFor(() => {
      const request = latestBookRequest();
      expect(request?.searchParams.get("sort")).toBe("title");
      expect(request?.searchParams.get("order")).toBe("asc");
      expect(latestFilters()?.conditions).toHaveLength(2);
    });

    await user.click(screen.getByRole("button", { name: "清空组合条件" }));
    await waitFor(() => {
      const request = latestBookRequest();
      expect(request?.searchParams.get("filters")).toBeNull();
      expect(request?.searchParams.get("sort")).toBe("title");
      expect(request?.searchParams.get("offset")).toBe("0");
    });
  });

  it("restores smart filters, sorting and pagination from the session", async () => {
    window.sessionStorage.setItem(
      "acbooks-library-view",
      JSON.stringify({
        filters: {
          combinator: "ANY",
          conditions: [
            {
              field: "tag",
              id: "saved-tag",
              operator: "equals",
              value: "文学",
            },
          ],
        },
        offset: 12,
        searchTerm: "",
        sort: "title",
        status: "all",
        type: "all",
        view: "list",
      }),
    );
    installApi(
      baseHandler((url, init) => {
        if (url.pathname === "/api/v1/books" && methodOf(init) === "GET") {
          return jsonResponse(makePage(makeBooks(12), 30, 12));
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    renderLibrary();
    await screen.findByRole("table", { name: "书籍列表" });

    const request = latestBookRequest();
    expect(request?.searchParams.get("offset")).toBe("12");
    expect(request?.searchParams.get("sort")).toBe("title");
    expect(request?.searchParams.get("order")).toBe("asc");
    expect(latestFilters()).toEqual({
      combinator: "ANY",
      conditions: [{ field: "tag", operator: "equals", value: "文学" }],
    });
    expect(
      screen.getByRole("button", { name: /更多筛选，已启用 1 个条件/ }),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: /更多筛选，已启用 1 个条件/ }),
    );
    expect(
      await screen.findByRole("combobox", { name: "标签筛选值" }),
    ).toHaveValue("文学");
    expect(
      screen.getByRole("combobox", { name: "条件组合方式" }),
    ).toHaveValue("ANY");

    await user.click(screen.getByRole("button", { name: "清除筛选" }));
    await waitFor(() => {
      const cleared = latestBookRequest();
      expect(cleared?.searchParams.get("filters")).toBeNull();
      expect(cleared?.searchParams.get("offset")).toBe("0");
      expect(cleared?.searchParams.get("sort")).toBe("title");
      expect(cleared?.searchParams.get("order")).toBe("asc");
    });
  });

  it("resets transient filters for sidebar media views", async () => {
    window.sessionStorage.setItem(
      "acbooks-library-view",
      JSON.stringify({
        format: "pdf",
        offset: 0,
        query: "旧查询",
        searchTerm: "旧查询",
        sort: "recent",
        status: "done",
        tag: "旧标签",
        type: "digital",
        view: "grid",
      }),
    );
    window.history.replaceState(
      {},
      "",
      "/library?mediaType=physical&reset=1",
    );
    installApi(
      baseHandler((url, init) => {
        if (url.pathname === "/api/v1/books" && methodOf(init) === "GET") {
          return jsonResponse(makePage([BOOK1], 1));
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    renderLibrary();
    await screen.findByRole("article", { name: "呐喊" });

    const request = latestBookRequest();
    expect(request?.searchParams.get("mediaType")).toBe("physical");
    expect(request?.searchParams.get("readingStatus")).toBeNull();
    expect(request?.searchParams.get("q")).toBeNull();
    expect(request?.searchParams.get("tag")).toBeNull();
    expect(request?.searchParams.get("format")).toBeNull();
    expect(
      screen.getByRole("heading", { level: 1, name: "实体书" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("searchbox", { name: "搜索书库" }),
    ).toHaveValue("");
    expect(screen.getByRole("radio", { name: "全部" })).toBeChecked();
  });

  it("shows a loading state before the first page arrives", async () => {
    let resolveBooks: ((response: Response) => void) | undefined;
    const pending = new Promise<Response>((resolve) => {
      resolveBooks = resolve;
    });
    installApi(
      baseHandler((url, init) => {
        if (url.pathname === "/api/v1/books" && methodOf(init) === "GET") {
          return pending;
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    renderLibrary();

    await screen.findByRole("heading", { level: 1, name: "全部书籍" });
    expect(
      screen.getAllByText("正在载入书库").length,
    ).toBeGreaterThan(0);

    await act(async () => {
      resolveBooks?.(jsonResponse(makePage([BOOK1])));
    });
    expect(
      await screen.findByRole("article", { name: "呐喊" }),
    ).toBeInTheDocument();
  });

  it("distinguishes an empty library from an empty search result", async () => {
    installApi(
      baseHandler((url, init) => {
        if (url.pathname === "/api/v1/books" && methodOf(init) === "GET") {
          return jsonResponse(makePage([]));
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    renderLibrary();

    expect(
      await screen.findByText("书库还没有书籍"),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("没有找到匹配的书籍"),
    ).not.toBeInTheDocument();
    expect(
      screen.getAllByRole("button", { name: "添加书籍" }).length,
    ).toBeGreaterThan(0);
  });

  it("shows a no-results state and clears filters", async () => {
    installApi(
      baseHandler((url, init) => {
        if (url.pathname === "/api/v1/books" && methodOf(init) === "GET") {
          return jsonResponse(
            url.searchParams.get("q") === "不存在"
              ? makePage([])
              : makePage([BOOK1]),
          );
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    renderLibrary();
    await screen.findByRole("article", { name: "呐喊" });

    const search = screen.getByRole("searchbox", { name: "搜索书库" });
    await user.type(search, "不存在");
    expect(
      await screen.findByText("没有找到匹配的书籍"),
    ).toBeInTheDocument();

    await user.click(
      screen.getAllByRole("button", { name: "清除筛选" })[0]!,
    );
    expect(
      await screen.findByRole("article", { name: "呐喊" }),
    ).toBeInTheDocument();
    expect(search).toHaveValue("");
  });

  it("retries after a list request fails", async () => {
    let failed = false;
    installApi(
      baseHandler((url, init) => {
        if (url.pathname === "/api/v1/books" && methodOf(init) === "GET") {
          if (!failed) {
            failed = true;
            return problemResponse(500, "INTERNAL_ERROR");
          }
          return jsonResponse(makePage([BOOK1]));
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    renderLibrary();

    expect(
      await screen.findByText("服务器暂时无法完成请求，请稍后重试。"),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "重试" }));

    expect(
      await screen.findByRole("article", { name: "呐喊" }),
    ).toBeInTheDocument();
  });

  it("redirects when the session expires during a library request", async () => {
    installApi(
      baseHandler((url, init) => {
        if (url.pathname === "/api/v1/books" && methodOf(init) === "GET") {
          return problemResponse(401, "UNAUTHORIZED");
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const { onSessionExpired } = renderLibrary();

    await waitFor(() => {
      expect(onSessionExpired).toHaveBeenCalledOnce();
    });
  });

  it("adds a book and refreshes the library", async () => {
    let created: BookSummary | null = null;
    installApi(
      baseHandler((url, init) => {
        const method = methodOf(init);
        if (url.pathname === "/api/v1/books" && method === "GET") {
          return jsonResponse(
            created === null ? makePage([]) : makePage([created], 1),
          );
        }
        if (url.pathname === "/api/v1/books" && method === "POST") {
          const body = JSON.parse(String(init.body)) as {
            authors: string[];
            description: string | null;
            isbn: string | null;
            readingStatus: BookSummary["readingStatus"];
            shelves: string[];
            tags: string[];
            title: string;
            translators: string[];
          };
          created = summary({
            id: "44444444-4444-4444-8444-444444444444",
            title: body.title,
            authors: body.authors,
            translators: body.translators,
            isbn: body.isbn,
            readingStatus: body.readingStatus,
            tags: body.tags,
            shelves: body.shelves,
          });
          return jsonResponse(
            detailFor(created, { description: body.description }),
            201,
          );
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    renderLibrary();
    await screen.findByText("书库还没有书籍");

    await user.click(
      screen.getAllByRole("button", { name: "添加书籍" })[0]!,
    );
    const dialog = await screen.findByRole("dialog", {
      name: "添加书籍",
    });
    await user.type(within(dialog).getByLabelText("书名"), "新书");
    await user.type(
      within(dialog).getByLabelText("作者"),
      "作者甲, 作者乙",
    );
    await user.type(
      within(dialog).getByLabelText("标签"),
      "文学, 经典",
    );
    await user.type(
      within(dialog).getByLabelText("书架"),
      "书房, 客厅",
    );
    await user.type(
      within(dialog).getByLabelText("ISBN"),
      "978-0-306-40615-7",
    );
    await user.selectOptions(
      within(dialog).getByLabelText("阅读状态"),
      "reading",
    );
    await user.click(within(dialog).getByRole("button", { name: "保存" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(
      await screen.findByRole("article", { name: "新书" }),
    ).toBeInTheDocument();

    const post = findCall("POST", "/api/v1/books");
    expect(post).toBeDefined();
    const init = (post?.[1] ?? {}) as RequestInit;
    expect(
      (init.headers as Record<string, string>)["X-CSRF-Token"],
    ).toBe("csrf-token");
    expect(JSON.parse(String(init.body))).toEqual({
      authors: ["作者甲", "作者乙"],
      description: null,
      isbn: "978-0-306-40615-7",
      readingStatus: "reading",
      shelves: ["书房", "客厅"],
      tags: ["文学", "经典"],
      title: "新书",
      translators: [],
    });
  });

  it("links books to the detail route and keeps the library view", async () => {
    installApi(
      baseHandler((url, init) => {
        if (url.pathname === "/api/v1/books" && methodOf(init) === "GET") {
          return jsonResponse(makePage([BOOK1], 1));
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    renderLibrary();
    await screen.findByRole("article", { name: "呐喊" });

    expect(
      screen.getByRole("link", { name: "查看《呐喊》详情" }),
    ).toHaveAttribute("href", `/library/books/${BOOK1.id}`);

    await user.click(screen.getByRole("radio", { name: "阅读中" }));
    await waitFor(() => {
      const saved = JSON.parse(
        window.sessionStorage.getItem("acbooks-library-view") ?? "{}",
      ) as { status?: string };
      expect(saved.status).toBe("reading");
    });
  });

  it("logs out with CSRF", async () => {
    installApi(
      baseHandler((url, init) => {
        const method = methodOf(init);
        if (url.pathname === "/api/v1/books" && method === "GET") {
          return jsonResponse(makePage([BOOK1], 1));
        }
        if (url.pathname === "/api/v1/auth/logout" && method === "POST") {
          return jsonResponse(undefined, 204);
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    const { onLoggedOut } = renderLibrary();
    await screen.findByRole("article", { name: "呐喊" });

    await user.click(screen.getByRole("button", { name: "退出登录" }));

    await waitFor(() => {
      expect(onLoggedOut).toHaveBeenCalledOnce();
    });
    const logoutCall = findCall("POST", "/api/v1/auth/logout");
    expect(logoutCall).toBeDefined();
    const init = (logoutCall?.[1] ?? {}) as RequestInit;
    expect(
      (init.headers as Record<string, string>)["X-CSRF-Token"],
    ).toBe("csrf-token");
  });

  it("does not search during IME composition", async () => {
    installApi(
      baseHandler((url, init) => {
        if (url.pathname === "/api/v1/books" && methodOf(init) === "GET") {
          return jsonResponse(makePage([BOOK1], 1));
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    renderLibrary();
    await screen.findByRole("article", { name: "呐喊" });
    const search = screen.getByRole("searchbox", { name: "搜索书库" });

    fireEvent.compositionStart(search);
    fireEvent.change(search, { target: { value: "三体" } });
    await new Promise((resolve) => setTimeout(resolve, 350));
    expect(
      bookRequests().some((url) => url.searchParams.get("q") === "三体"),
    ).toBe(false);

    fireEvent.compositionEnd(search, { target: { value: "三体" } });
    await waitFor(() => {
      expect(
        bookRequests().some((url) => url.searchParams.get("q") === "三体"),
      ).toBe(true);
    });
  });

  it("renders the API-backed library in English", async () => {
    installApi(
      baseHandler((url, init) => {
        if (url.pathname === "/api/v1/books" && methodOf(init) === "GET") {
          return jsonResponse(makePage([BOOK1], 1));
        }
        throw new Error(`Unexpected request ${url.pathname}`);
      }),
    );
    renderLibrary("en");

    expect(
      await screen.findByRole("heading", { level: 1, name: "All books" }),
    ).toBeInTheDocument();
    await screen.findByRole("article", { name: "呐喊" });
    expect(
      screen.getByText(
        /physical and digital editions managed together/,
      ),
    ).toHaveTextContent("1 book ·");
    expect(screen.getByText("Physical ×2")).toBeInTheDocument();
    expect(screen.getAllByText("Reading").length).toBeGreaterThan(0);
    expect(
      screen.getByRole("complementary", { name: "Application sidebar" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", {
        name: "Account and settings for admin",
      }),
    ).toBeInTheDocument();
  });
});
