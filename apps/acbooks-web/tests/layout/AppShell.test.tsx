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
import { useEffect } from "react";
import { MemoryRouter, useLocation, useNavigate } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { appResources } from "../../src/i18n/resources";
import { AppShell } from "../../src/layout/AppShell";
import { PageHeader } from "../../src/layout/PageHeader";

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
    <button
      onClick={() => {
        navigate(-1);
      }}
      type="button"
    >
      history-back
    </button>
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

function mockViewport(kind: "desktop" | "mobile") {
  vi.stubGlobal("matchMedia", (query: string) => ({
    addEventListener: () => undefined,
    addListener: () => undefined,
    dispatchEvent: () => false,
    matches: kind === "desktop" && query.includes("min-width: 768px"),
    media: query,
    onchange: null,
    removeEventListener: () => undefined,
    removeListener: () => undefined,
  }));
}

function renderShell(currentPath: string) {
  const onLogout = vi.fn();
  const expireSession = vi.fn();
  render(
    <I18nProvider defaultLanguage="zh-CN" resources={appResources}>
      <MemoryRouter initialEntries={[currentPath]}>
        <LocationProbe />
        <HistoryControls />
        <AppShell
          accountName="admin"
          expireSession={expireSession}
          loggingOut={false}
          onLogout={onLogout}
        >
          <PageHeader title="书库" />
        </AppShell>
      </MemoryRouter>
    </I18nProvider>,
  );
  return { expireSession, onLogout };
}

function bookSearchCalls(): number {
  return fetchMock.mock.calls.filter(([input]) =>
    String(input).startsWith("/api/v1/books"),
  ).length;
}

interface DeferredSearch {
  q: string;
  signal: AbortSignal | null;
  resolve: (body: unknown) => void;
}

function installDeferredSearch(): DeferredSearch[] {
  const pending: DeferredSearch[] = [];
  fetchMock.mockImplementation(
    (input: RequestInfo | URL, init?: RequestInit) =>
      new Promise<Response>((resolve) => {
        const url = new URL(String(input), "http://localhost");
        pending.push({
          q: url.searchParams.get("q") ?? "",
          signal: init?.signal ?? null,
          resolve: (body) => {
            resolve(jsonResponse(body));
          },
        });
      }),
  );
  return pending;
}

function searchPage(title: string, total = 1) {
  return {
    items: [
      {
        id: "11111111-1111-4111-8111-111111111111",
        title,
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
      },
    ],
    total,
    limit: 5,
    offset: 0,
  };
}

beforeEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
  window.history.replaceState({}, "", "/library");
  currentLocation = "";
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.history.replaceState({}, "", "/");
});

describe("AppShell", () => {
  it("renders the icon rail, top search and account entry", () => {
    mockViewport("desktop");
    renderShell("/library");
    const rail = screen.getByRole("complementary", {
      name: "应用侧边栏",
    });

    expect(within(rail).getByRole("link", { name: "首页" })).toHaveAttribute(
      "href",
      "/home",
    );
    expect(
      within(rail).getByRole("link", { name: "书库" }),
    ).toHaveAttribute("href", "/library");
    expect(
      within(rail).getByRole("link", { name: "书库" }),
    ).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("combobox", { name: "全局搜索" })).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "账户与设置：admin" }),
    ).toHaveAttribute("href", "/settings");
    expect(
      screen.queryByRole("button", { name: "退出登录" }),
    ).not.toBeInTheDocument();
    expect(rail.className).toContain("w-20");
  });

  it("keeps the library group active on detail routes", () => {
    mockViewport("desktop");
    renderShell("/library/books/11111111-1111-4111-8111-111111111111");
    const rail = screen.getByRole("complementary", {
      name: "应用侧边栏",
    });

    expect(
      within(rail).getByRole("link", { name: "书库" }),
    ).toHaveAttribute("aria-current", "page");
  });

  it("opens and closes the mobile navigation drawer", async () => {
    mockViewport("mobile");
    const user = userEvent.setup();
    renderShell("/library");
    const trigger = screen.getByRole("button", { name: "打开导航菜单" });
    await user.click(trigger);
    const dialog = document.getElementById(
      "app-navigation-drawer",
    ) as HTMLDialogElement;

    expect(dialog.open).toBe(true);
    expect(
      within(dialog).getByRole("link", { name: "书库" }),
    ).toHaveAttribute("href", "/library");
    expect(
      within(dialog).getByRole("combobox", { name: "全局搜索" }),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole("link", { name: "账户与设置：admin" }),
    ).toHaveAttribute("href", "/settings");

    await user.click(
      within(dialog).getByRole("button", { name: "关闭导航菜单" }),
    );
    await waitFor(() => {
      expect(trigger).toHaveAttribute("aria-expanded", "false");
    });
  });

  it("opens and closes the drawer with horizontal swipes", async () => {
    mockViewport("mobile");
    renderShell("/library");
    const dialog = document.getElementById(
      "app-navigation-drawer",
    ) as HTMLDialogElement;

    fireEvent.touchStart(document, {
      touches: [{ clientX: 8, clientY: 400 }],
    });
    fireEvent.touchMove(document, {
      touches: [{ clientX: 26, clientY: 300 }],
    });
    expect(dialog.open).toBe(false);

    fireEvent.touchStart(document, {
      touches: [{ clientX: 8, clientY: 400 }],
    });
    fireEvent.touchMove(document, {
      touches: [{ clientX: 90, clientY: 404 }],
    });
    await waitFor(() => {
      expect(dialog.open).toBe(true);
    });

    fireEvent.touchStart(dialog, {
      touches: [{ clientX: 260, clientY: 400 }],
    });
    fireEvent.touchMove(dialog, {
      touches: [{ clientX: 150, clientY: 404 }],
    });
    await waitFor(() => {
      expect(dialog.open).toBe(false);
    });
  });

  it("focuses the top bar search with Ctrl+K on desktop", () => {
    mockViewport("desktop");
    renderShell("/library");

    fireEvent.keyDown(window, { ctrlKey: true, key: "k" });

    expect(screen.getByRole("combobox", { name: "全局搜索" })).toHaveFocus();
  });

  it("opens the drawer and focuses its search with Ctrl+K on mobile", async () => {
    mockViewport("mobile");
    renderShell("/library");

    fireEvent.keyDown(window, { ctrlKey: true, key: "k" });
    const dialog = document.getElementById(
      "app-navigation-drawer",
    ) as HTMLDialogElement;

    await waitFor(() => {
      expect(dialog.open).toBe(true);
      expect(
        within(dialog).getByRole("combobox", { name: "全局搜索" }),
      ).toHaveFocus();
    });
  });

  it("closes the drawer after navigating and keeps it closed on back", async () => {
    mockViewport("mobile");
    const user = userEvent.setup();
    renderShell("/library");
    const trigger = screen.getByRole("button", { name: "打开导航菜单" });
    await user.click(trigger);
    const dialog = document.getElementById(
      "app-navigation-drawer",
    ) as HTMLDialogElement;

    await user.click(within(dialog).getByRole("link", { name: "首页" }));
    await waitFor(() => {
      expect(currentLocation).toBe("/home");
      expect(trigger).toHaveAttribute("aria-expanded", "false");
    });

    await user.click(screen.getByRole("button", { name: "history-back" }));
    await waitFor(() => {
      expect(currentLocation).toBe("/library");
    });
    expect(trigger).toHaveAttribute("aria-expanded", "false");
  });

  it("searches the whole library and offers the full result list", async () => {
    mockViewport("desktop");
    fetchMock.mockResolvedValue(jsonResponse(searchPage("三体", 7)));
    const user = userEvent.setup();
    renderShell("/library");
    const search = screen.getByRole("combobox", { name: "全局搜索" });

    await user.type(search, "三体");
    expect(
      await screen.findByRole("option", { name: /三体/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "查看全部 7 个结果" }),
    ).toHaveAttribute("href", `/library?q=${encodeURIComponent("三体")}`);

    const request = new URL(
      String(fetchMock.mock.calls.at(-1)?.[0]),
      "http://localhost",
    );
    expect(request.searchParams.get("q")).toBe("三体");
    expect(request.searchParams.get("limit")).toBe("5");

    await user.keyboard("{Escape}");
    expect(
      screen.queryByRole("option", { name: /三体/ }),
    ).not.toBeInTheDocument();
  });

  it("deduplicates repeated search, ignores IME Enter and navigates on Enter", async () => {
    mockViewport("desktop");
    fetchMock.mockResolvedValue(jsonResponse(searchPage("三体")));
    const user = userEvent.setup();
    renderShell("/library");
    const search = screen.getByRole("combobox", { name: "全局搜索" });

    await user.type(search, "三体");
    await screen.findByRole("option", { name: /三体/ });
    const requestsBefore = bookSearchCalls();

    fireEvent.compositionStart(search);
    fireEvent.compositionEnd(search, { target: { value: "三体" } });
    expect(bookSearchCalls()).toBe(requestsBefore);

    fireEvent.keyDown(search, { key: "Enter", isComposing: true });
    expect(currentLocation).toBe("/library");

    fireEvent.keyDown(search, { key: "Enter" });
    await waitFor(() => {
      expect(currentLocation).toBe(
        `/library?q=${encodeURIComponent("三体")}`,
      );
    });
  });

  it("keeps an in-flight search when the same keyword is confirmed", async () => {
    mockViewport("desktop");
    const pending = installDeferredSearch();
    const user = userEvent.setup();
    renderShell("/library");
    const search = screen.getByRole("combobox", { name: "全局搜索" });

    await user.type(search, "三体");
    await waitFor(() => {
      expect(pending).toHaveLength(1);
    });
    expect(pending[0]?.q).toBe("三体");

    fireEvent.compositionEnd(search, { target: { value: "三体" } });
    expect(pending[0]?.signal?.aborted).toBe(false);

    await act(async () => {
      pending[0]?.resolve(searchPage("三体"));
    });
    expect(
      await screen.findByRole("option", { name: /三体/ }),
    ).toBeInTheDocument();
  });

  it("recovers when the original keyword is restored during the debounce", async () => {
    mockViewport("desktop");
    const pending = installDeferredSearch();
    const user = userEvent.setup();
    renderShell("/library");
    const search = screen.getByRole("combobox", { name: "全局搜索" });

    await user.type(search, "三体");
    await waitFor(() => {
      expect(pending).toHaveLength(1);
    });
    expect(pending[0]?.q).toBe("三体");

    fireEvent.change(search, { target: { value: "边城" } });
    expect(pending[0]?.signal?.aborted).toBe(true);

    fireEvent.change(search, { target: { value: "三体" } });
    await waitFor(() => {
      expect(pending).toHaveLength(2);
    });
    expect(pending[1]?.q).toBe("三体");
    expect(pending[1]?.signal?.aborted).toBe(false);

    await act(async () => {
      pending[1]?.resolve(searchPage("三体"));
    });
    expect(
      await screen.findByRole("option", { name: /三体/ }),
    ).toBeInTheDocument();
    expect(screen.queryByText("正在搜索")).not.toBeInTheDocument();
  });

  it("hides stale results and discards late responses for old keywords", async () => {
    mockViewport("desktop");
    const pending = installDeferredSearch();
    const user = userEvent.setup();
    renderShell("/library");
    const search = screen.getByRole("combobox", { name: "全局搜索" });

    await user.type(search, "三体");
    await waitFor(() => {
      expect(pending).toHaveLength(1);
    });

    fireEvent.change(search, { target: { value: "边城" } });
    expect(screen.queryByRole("option")).not.toBeInTheDocument();
    expect(pending[0]?.signal?.aborted).toBe(true);

    await waitFor(() => {
      expect(pending).toHaveLength(2);
    });
    expect(pending[1]?.q).toBe("边城");

    await act(async () => {
      pending[0]?.resolve(searchPage("三体"));
    });
    expect(
      screen.queryByRole("option", { name: /三体/ }),
    ).not.toBeInTheDocument();

    await act(async () => {
      pending[1]?.resolve(searchPage("边城"));
    });
    expect(
      await screen.findByRole("option", { name: /边城/ }),
    ).toBeInTheDocument();
  });
});
