import { I18nProvider } from "@acbooks/ui";
import {
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { appResources } from "../i18n/resources";
import { AppShell } from "./AppShell";
import { PageHeader } from "./PageHeader";

const fetchMock = vi.fn();

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => null },
    json: async () => body,
  } as unknown as Response;
}

function renderShell(currentPath: string, title = "全部书籍") {
  const onLogout = vi.fn();
  const expireSession = vi.fn();
  render(
    <I18nProvider defaultLanguage="zh-CN" resources={appResources}>
      <AppShell
        accountName="admin"
        currentPath={currentPath}
        expireSession={expireSession}
        loggingOut={false}
        onLogout={onLogout}
      >
        <PageHeader title={title} />
      </AppShell>
    </I18nProvider>,
  );
  return { expireSession, onLogout };
}

beforeEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
  window.history.replaceState({}, "", "/library");
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.history.replaceState({}, "", "/");
});

describe("AppShell", () => {
  it("renders shared navigation, search and the account entry", () => {
    renderShell("/library");
    const sidebar = screen.getByRole("complementary", {
      name: "应用侧边栏",
    });

    expect(within(sidebar).getByRole("link", { name: "首页" })).toHaveAttribute(
      "href",
      "/home",
    );
    expect(
      within(sidebar).getByRole("link", { name: "全部书籍" }),
    ).toHaveAttribute("href", "/library?mediaType=all&reset=1");
    expect(
      within(sidebar).getByRole("link", { name: "全部书籍" }),
    ).toHaveAttribute("aria-current", "page");
    expect(
      within(sidebar).getByRole("link", { name: "实体书" }),
    ).toHaveAttribute("href", "/library?mediaType=physical&reset=1");
    expect(
      within(sidebar).getByRole("button", { name: "书架" }),
    ).toBeDisabled();
    expect(
      within(sidebar).getByRole("combobox", { name: "全局搜索" }),
    ).toBeInTheDocument();
    expect(
      within(sidebar).getByRole("link", { name: "账户与设置：admin" }),
    ).toHaveAttribute("href", "/settings");
    expect(
      within(sidebar).getByRole("button", { name: "退出登录" }),
    ).toBeInTheDocument();
  });

  it("marks the physical-books route as the current view", () => {
    renderShell("/library?mediaType=physical&reset=1", "实体书");
    const sidebar = screen.getByRole("complementary", {
      name: "应用侧边栏",
    });

    expect(
      within(sidebar).getByRole("link", { name: "实体书" }),
    ).toHaveAttribute("aria-current", "page");
    expect(
      within(sidebar).getByRole("link", { name: "全部书籍" }),
    ).not.toHaveAttribute("aria-current");
  });

  it("opens and closes the mobile navigation drawer", async () => {
    const user = userEvent.setup();
    renderShell("/library");
    const trigger = screen.getByRole("button", { name: "打开导航菜单" });
    expect(trigger).toHaveAttribute("aria-expanded", "false");

    await user.click(trigger);
    expect(trigger).toHaveAttribute("aria-expanded", "true");
    const dialog = document.getElementById(
      "app-navigation-drawer",
    ) as HTMLDialogElement | null;
    expect(dialog).not.toBeNull();
    expect(dialog?.open).toBe(true);
    expect(
      within(dialog as HTMLElement).getByRole("button", {
        name: "关闭导航菜单",
      }),
    ).toBeInTheDocument();

    await user.click(
      within(dialog as HTMLElement).getByRole("button", {
        name: "关闭导航菜单",
      }),
    );
    await waitFor(() => {
      expect(trigger).toHaveAttribute("aria-expanded", "false");
    });
  });

  it("searches the whole library and offers the full result list", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        items: [
          {
            id: "11111111-1111-4111-8111-111111111111",
            title: "三体",
            authors: ["刘慈欣"],
            translators: [],
            isbn: null,
            readingStatus: "reading",
            tags: [],
            shelves: [],
            copyCount: 1,
            fileCount: 1,
            formats: ["epub"],
            createdAt: 1_700_000_000_000,
            updatedAt: 1_700_000_000_000,
          },
        ],
        total: 7,
        limit: 5,
        offset: 0,
      }),
    );
    const user = userEvent.setup();
    renderShell("/library");
    const sidebar = screen.getByRole("complementary", {
      name: "应用侧边栏",
    });
    const search = within(sidebar).getByRole("combobox", {
      name: "全局搜索",
    });

    await user.type(search, "三体");
    const option = await within(sidebar).findByRole("option", {
      name: /三体/,
    });
    expect(option).toBeInTheDocument();
    expect(
      within(sidebar).getByRole("link", { name: "查看全部 7 个结果" }),
    ).toHaveAttribute("href", `/library?q=${encodeURIComponent("三体")}`);

    const request = new URL(
      String(fetchMock.mock.calls.at(-1)?.[0]),
      "http://localhost",
    );
    expect(request.searchParams.get("q")).toBe("三体");
    expect(request.searchParams.get("limit")).toBe("5");

    await user.keyboard("{Escape}");
    expect(
      within(sidebar).queryByRole("option", { name: /三体/ }),
    ).not.toBeInTheDocument();
  });

  it("switches the sidebar to settings categories", () => {
    renderShell("/settings", "设置");
    const sidebar = screen.getByRole("complementary", {
      name: "应用侧边栏",
    });

    expect(
      within(sidebar).queryByRole("combobox"),
    ).not.toBeInTheDocument();
    expect(
      within(sidebar).getByRole("link", { name: "返回" }),
    ).toHaveAttribute("href", "/home");
    expect(
      within(sidebar).getByRole("link", { name: "账户" }),
    ).toHaveAttribute("href", "/settings?section=account");
    expect(
      within(sidebar).getByRole("link", { name: "外观" }),
    ).toHaveAttribute("href", "/settings?section=appearance");
    expect(
      within(sidebar).getByRole("link", { name: "语言" }),
    ).toHaveAttribute("href", "/settings?section=language");
  });
});
