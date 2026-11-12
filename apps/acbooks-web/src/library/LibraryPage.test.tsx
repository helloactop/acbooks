import { I18nProvider } from "@acbooks/ui";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { appResources } from "../i18n/resources";
import { LibraryPage } from "./LibraryPage";

const ACCOUNT = { id: "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f", username: "admin" };

const fetchMock = vi.fn();

function mockResponse(
  body: unknown,
  status = 200,
  headers: Record<string, string> = {},
): Response {
  const normalized = Object.fromEntries(
    Object.entries(headers).map(([name, value]) => [name.toLowerCase(), value]),
  );

  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get: (name: string) => normalized[name.toLowerCase()] ?? null,
    },
    json: async () => body,
  } as unknown as Response;
}

function problemResponse(status: number, code: string): Response {
  return mockResponse(
    {
      type: "about:blank",
      title: "",
      status,
      code,
      detail: "",
      requestId: "request-library",
    },
    status,
    { "Content-Type": "application/problem+json" },
  );
}

beforeEach(() => {
  window.localStorage.clear();
  fetchMock.mockReset();
  fetchMock.mockResolvedValue(mockResponse(ACCOUNT));
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(window.navigator, "languages", "get").mockReturnValue(["zh-CN"]);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.history.replaceState({}, "", "/");
});

function renderLibrary(language: "zh-CN" | "en" = "zh-CN") {
  const onUnauthenticated = vi.fn();
  render(
    <I18nProvider defaultLanguage={language} resources={appResources}>
      <LibraryPage onUnauthenticated={onUnauthenticated} />
    </I18nProvider>,
  );
  return { onUnauthenticated };
}

describe("LibraryPage", () => {
  it("renders the library shell with sample books and disabled future entries", async () => {
    const { onUnauthenticated } = renderLibrary();

    expect(
      await screen.findByRole("heading", { level: 1, name: "全部书籍" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("共 12 本，实体书与电子书统一管理"),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("article")).toHaveLength(12);
    expect(screen.getByText("当前账号：admin")).toBeInTheDocument();
    expect(onUnauthenticated).not.toHaveBeenCalled();

    expect(screen.getByRole("button", { name: "添加书籍" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "首页" })).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "打开导航菜单" }),
    ).toBeDisabled();
    expect(screen.getByRole("combobox", { name: "书架" })).toBeDisabled();
    expect(screen.getByRole("link", { name: "全部书籍" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  it("filters the sample books locally and clears an empty result", async () => {
    const user = userEvent.setup();
    renderLibrary();
    await screen.findByRole("heading", { level: 1, name: "全部书籍" });

    const search = screen.getByRole("searchbox", { name: "搜索书库" });
    await user.type(search, "三体");

    expect(screen.getAllByRole("article")).toHaveLength(1);
    expect(screen.getByRole("article", { name: "三体" })).toBeInTheDocument();
    expect(
      screen.getByText("共 1 本，实体书与电子书统一管理"),
    ).toBeInTheDocument();

    await user.clear(search);
    await user.type(search, "不存在的书");
    expect(screen.getByText("没有找到匹配的书籍")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "清除筛选" }));
    expect(search).toHaveValue("");
    expect(screen.getAllByRole("article")).toHaveLength(12);
  });

  it("does not filter while an IME composition is active", async () => {
    renderLibrary();
    await screen.findByRole("heading", { level: 1, name: "全部书籍" });

    const search = screen.getByRole("searchbox", { name: "搜索书库" });
    fireEvent.compositionStart(search);
    fireEvent.change(search, { target: { value: "三体" } });
    expect(screen.getAllByRole("article")).toHaveLength(12);

    fireEvent.compositionEnd(search, { target: { value: "三体" } });
    expect(screen.getAllByRole("article")).toHaveLength(1);
  });

  it("combines status and type filters", async () => {
    const user = userEvent.setup();
    renderLibrary();
    await screen.findByRole("heading", { level: 1, name: "全部书籍" });

    await user.selectOptions(
      screen.getByRole("combobox", { name: "状态" }),
      "done",
    );
    expect(screen.getAllByRole("article")).toHaveLength(3);

    await user.selectOptions(
      screen.getByRole("combobox", { name: "类型" }),
      "physical",
    );
    expect(screen.getAllByRole("article")).toHaveLength(2);
    expect(
      screen.getByText("共 2 本，实体书与电子书统一管理"),
    ).toBeInTheDocument();
  });

  it("switches to the list view with disabled row actions", async () => {
    const user = userEvent.setup();
    renderLibrary();
    await screen.findByRole("heading", { level: 1, name: "全部书籍" });

    await user.click(screen.getByRole("radio", { name: "列表视图" }));

    expect(
      screen.getByRole("table", { name: "书籍列表" }),
    ).toBeInTheDocument();
    expect(screen.queryAllByRole("article")).toHaveLength(0);
    expect(screen.getByText("书名与作者")).toBeInTheDocument();
    const moreActions = screen.getAllByRole("button", { name: "更多操作" });
    expect(moreActions).toHaveLength(12);
    expect(moreActions[0]).toBeDisabled();
  });

  it("renders the library in English", async () => {
    renderLibrary("en");

    expect(
      await screen.findByRole("heading", { level: 1, name: "All books" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "12 books · physical and digital editions managed together",
      ),
    ).toBeInTheDocument();
    expect(screen.getAllByText("Physical").length).toBeGreaterThan(0);
    expect(
      screen.getByRole("combobox", { name: "Status" }),
    ).toBeInTheDocument();
  });

  it("redirects unauthenticated visitors", async () => {
    fetchMock.mockResolvedValueOnce(problemResponse(401, "UNAUTHORIZED"));
    const { onUnauthenticated } = renderLibrary();

    await waitFor(() => {
      expect(onUnauthenticated).toHaveBeenCalledOnce();
    });
  });

  it("retries after the session check fails", async () => {
    fetchMock
      .mockRejectedValueOnce(new TypeError("offline"))
      .mockResolvedValueOnce(mockResponse(ACCOUNT));
    renderLibrary();

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "无法载入书库",
      }),
    ).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "重试" }));

    expect(
      await screen.findByRole("heading", { level: 1, name: "全部书籍" }),
    ).toBeInTheDocument();
  });
});
