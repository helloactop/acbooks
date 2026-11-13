import { I18nProvider } from "@acbooks/ui";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { appResources } from "../../src/i18n/resources";
import { AuthenticatedLayout } from "../../src/layout/AuthenticatedLayout";
import { StoragePage } from "../../src/storage/StoragePage";

const { listStorageRoots, createStorageRoot, deleteStorageRoot, startStorageScan, getJob } =
  vi.hoisted(() => ({
    listStorageRoots: vi.fn(),
    createStorageRoot: vi.fn(),
    deleteStorageRoot: vi.fn(),
    startStorageScan: vi.fn(),
    getJob: vi.fn(),
  }));

vi.mock("../../src/storage/storage-api", () => ({
  listStorageRoots,
  createStorageRoot,
  deleteStorageRoot,
  startStorageScan,
  getJob,
}));

const ACCOUNT = { id: "0f0f0f0f-0f0f-4f0f-8f0f-0f0f0f0f0f0f", username: "admin" };
const ROOT = {
  id: "aaaaaaaa-aaaa-4aaa-8aaa-000000000001",
  name: "NAS 书库",
  rootPath: "/mnt/books",
  mode: "external" as const,
  fileCount: 3,
};

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => null },
    json: async () => body,
  } as unknown as Response;
}

const fetchMock = vi.fn();

function renderPage() {
  fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
    const url = new URL(String(input), "http://localhost");
    if (url.pathname === "/api/v1/auth/me") {
      return jsonResponse(ACCOUNT);
    }
    if (url.pathname === "/api/v1/auth/csrf") {
      return jsonResponse({ csrfToken: "token" });
    }
    throw new Error(`Unexpected ${url.pathname}`);
  });
  vi.stubGlobal("fetch", fetchMock);
  render(
    <I18nProvider defaultLanguage="zh-CN" resources={appResources}>
      <MemoryRouter initialEntries={["/storage"]}>
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
            <Route element={<StoragePage />} path="/storage" />
          </Route>
        </Routes>
      </MemoryRouter>
    </I18nProvider>,
  );
}

beforeEach(() => {
  listStorageRoots.mockReset();
  createStorageRoot.mockReset();
  deleteStorageRoot.mockReset();
  startStorageScan.mockReset();
  getJob.mockReset();
  vi.spyOn(window.navigator, "languages", "get").mockReturnValue(["zh-CN"]);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("StoragePage", () => {
  it("lists storage roots with mode and file count", async () => {
    listStorageRoots.mockResolvedValue([ROOT]);
    renderPage();
    expect(await screen.findByText("NAS 书库")).toBeInTheDocument();
    expect(screen.getByText("/mnt/books")).toBeInTheDocument();
    expect(screen.getByText("外部")).toBeInTheDocument();
    expect(screen.getByText("3 个文件")).toBeInTheDocument();
  });

  it("starts a scan and shows the result", async () => {
    listStorageRoots.mockResolvedValue([ROOT]);
    startStorageScan.mockResolvedValue({ jobId: "job-1" });
    getJob.mockResolvedValue({
      id: "job-1",
      type: "library_scan",
      status: "succeeded",
      createdAt: 0,
      updatedAt: 0,
      result: {
        scanned: 2,
        created: 2,
        updated: 0,
        unchanged: 0,
        missing: 0,
        skipped: 0,
        errors: 0,
      },
      errorCode: null,
    });
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("NAS 书库");

    await user.click(screen.getByRole("button", { name: "增量扫描" }));
    await waitFor(
      () => {
        expect(screen.getByText("扫描完成")).toBeInTheDocument();
      },
      { timeout: 4000 },
    );
    expect(startStorageScan).toHaveBeenCalledWith(ROOT.id, false);
    expect(screen.getByText(/新增 2/)).toBeInTheDocument();
  });

  it("registers a storage root", async () => {
    listStorageRoots.mockResolvedValue([]);
    createStorageRoot.mockResolvedValue(ROOT);
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("尚未注册存储根。");

    await user.click(screen.getByRole("button", { name: "注册存储根" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("名称"), "NAS 书库");
    await user.type(within(dialog).getByLabelText("服务器路径"), "/mnt/books");
    await user.click(within(dialog).getByRole("button", { name: "保存" }));

    await waitFor(() => {
      expect(createStorageRoot).toHaveBeenCalledWith({
        name: "NAS 书库",
        rootPath: "/mnt/books",
        mode: "external",
      });
    });
  });
});