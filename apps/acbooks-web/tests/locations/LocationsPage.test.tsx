import { I18nProvider } from "@acbooks/ui";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { appResources } from "../../src/i18n/resources";
import { AuthenticatedLayout } from "../../src/layout/AuthenticatedLayout";
import { LocationsPage } from "../../src/locations/LocationsPage";

const ACCOUNT = {
  id: "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f",
  username: "admin",
};

const ROOM = {
  id: "aaaaaaaa-aaaa-4aaa-8aaa-000000000001",
  parentId: null,
  name: "书房",
  copyCount: 0,
};

const SHELF = {
  id: "aaaaaaaa-aaaa-4aaa-8aaa-000000000002",
  parentId: ROOM.id,
  name: "书架A",
  copyCount: 2,
};

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => null },
    json: async () => body,
  } as unknown as Response;
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

function renderLocations() {
  render(
    <I18nProvider defaultLanguage="zh-CN" resources={appResources}>
      <MemoryRouter initialEntries={["/locations"]}>
        <Routes>
          <Route
            element={
              <AuthenticatedLayout
                onLoggedOut={vi.fn()}
                onSessionExpired={vi.fn()}
                onUnauthenticated={vi.fn()}
              />
            }
          >
            <Route element={<LocationsPage />} path="/locations" />
            <Route element={<div>书库占位</div>} path="/library" />
          </Route>
        </Routes>
      </MemoryRouter>
    </I18nProvider>,
  );
}

beforeEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(window.navigator, "languages", "get").mockReturnValue(["zh-CN"]);
  window.history.replaceState({}, "", "/locations");
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.history.replaceState({}, "", "/");
});

describe("LocationsPage", () => {
  it("renders the hierarchy, hides delete for parents and links to filtered books", async () => {
    installApi(
      baseHandler((url, init) => {
        if (url.pathname === "/api/v1/locations" && methodOf(init) === "GET") {
          return jsonResponse([ROOM, SHELF]);
        }
        throw new Error(`Unexpected ${methodOf(init)} ${url.pathname}`);
      }),
    );
    renderLocations();

    const roomRow = (await screen.findByText("书房")).closest("li");
    const shelfRow = screen.getByText("书架A").closest("li");
    expect(roomRow).not.toBeNull();
    expect(shelfRow).not.toBeNull();

    expect(
      within(roomRow as HTMLElement).queryByRole("button", {
        name: "删除位置",
      }),
    ).not.toBeInTheDocument();
    expect(
      within(shelfRow as HTMLElement).getByRole("button", {
        name: "删除位置",
      }),
    ).toBeInTheDocument();
    expect(
      within(shelfRow as HTMLElement).getByText("2 册"),
    ).toBeInTheDocument();

    const link = within(shelfRow as HTMLElement).getByRole("link", {
      name: "查看书籍",
    });
    const href = link.getAttribute("href") ?? "";
    expect(href).toContain("filters=");
    expect(decodeURIComponent(href)).toContain(SHELF.id);
    expect(href.startsWith("/library")).toBe(true);
  });

  it("creates and deletes a location", async () => {
    let locations: (typeof ROOM)[] = [];
    installApi(
      baseHandler((url, init) => {
        const method = methodOf(init);
        if (url.pathname === "/api/v1/locations" && method === "GET") {
          return jsonResponse(locations);
        }
        if (url.pathname === "/api/v1/locations" && method === "POST") {
          const created = {
            id: "cccccccc-cccc-4ccc-8ccc-000000000001",
            parentId: null,
            name: "新位置",
            copyCount: 0,
          };
          locations = [created];
          return jsonResponse(created, 201);
        }
        if (
          url.pathname ===
            "/api/v1/locations/cccccccc-cccc-4ccc-8ccc-000000000001" &&
          method === "DELETE"
        ) {
          locations = [];
          return jsonResponse(null, 204);
        }
        throw new Error(`Unexpected ${method} ${url.pathname}`);
      }),
    );
    const user = userEvent.setup();
    renderLocations();
    await screen.findByText("还没有存放位置，先新增一个房间或书架。");

    await user.click(screen.getByRole("button", { name: "新增位置" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("位置名称"), "新位置");
    await user.click(within(dialog).getByRole("button", { name: "保存" }));

    await screen.findByText("新位置");
    const postCall = findCall("POST", "/api/v1/locations");
    expect(postCall).toBeDefined();
    expect(JSON.parse(String((postCall?.[1] as RequestInit).body))).toEqual({
      name: "新位置",
      parentId: null,
    });

    await user.click(screen.getByRole("button", { name: "删除位置" }));
    const confirm = await screen.findByRole("dialog");
    await user.click(within(confirm).getByRole("button", { name: "删除" }));

    await waitFor(() => {
      expect(
        screen.getByText("还没有存放位置，先新增一个房间或书架。"),
      ).toBeInTheDocument();
    });
    expect(
      findCall(
        "DELETE",
        "/api/v1/locations/cccccccc-cccc-4ccc-8ccc-000000000001",
      ),
    ).toBeDefined();
  });
});