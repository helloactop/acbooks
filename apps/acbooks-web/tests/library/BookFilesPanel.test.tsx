import { I18nProvider } from "@acbooks/ui";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiRequestError } from "../../src/api/client";
import { appResources } from "../../src/i18n/resources";
import { BookFilesPanel } from "../../src/library/BookFilesPanel";
import type { BookFile } from "../../src/library/files-api";

const { uploadBookFile, deleteBookFile } = vi.hoisted(() => ({
  uploadBookFile: vi.fn(),
  deleteBookFile: vi.fn(),
}));

vi.mock("../../src/library/files-api", () => ({
  uploadBookFile,
  deleteBookFile,
  bookFileContentUrl: (id: string) => `/api/v1/files/${id}/content`,
}));

const FILES: BookFile[] = [
  { id: "f1", format: "pdf", fileStatus: "present", sizeBytes: 2048 },
  { id: "f2", format: "epub", fileStatus: "missing", sizeBytes: 1048576 },
];

function renderPanel(overrides: Partial<Parameters<typeof BookFilesPanel>[0]> = {}) {
  const onChanged = vi.fn();
  const onSessionExpired = vi.fn();
  const view = render(
    <I18nProvider defaultLanguage="zh-CN" resources={appResources}>
      <BookFilesPanel
        bookId="book-1"
        files={FILES}
        onChanged={onChanged}
        onSessionExpired={onSessionExpired}
        {...overrides}
      />
    </I18nProvider>,
  );
  return { ...view, onChanged, onSessionExpired };
}

beforeEach(() => {
  uploadBookFile.mockReset();
  deleteBookFile.mockReset();
  vi.spyOn(window.navigator, "languages", "get").mockReturnValue(["zh-CN"]);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("BookFilesPanel", () => {
  it("renders files with format, status and download links", () => {
    renderPanel();
    expect(screen.getByText("PDF")).toBeInTheDocument();
    expect(screen.getByText("EPUB")).toBeInTheDocument();
    expect(screen.getByText("可用")).toBeInTheDocument();
    expect(screen.getByText("缺失")).toBeInTheDocument();
    const links = screen.getAllByRole("link", { name: "下载文件" });
    expect(links).toHaveLength(2);
    expect(links[0]).toHaveAttribute("href", "/api/v1/files/f1/content");
    expect(links[0]).toHaveAttribute("download");
  });

  it("deletes a file after confirmation", async () => {
    deleteBookFile.mockResolvedValue(undefined);
    const { onChanged } = renderPanel();
    const user = userEvent.setup();

    const deleteButtons = screen.getAllByRole("button", { name: "删除文件" });
    await user.click(deleteButtons[0] as HTMLElement);
    const dialog = screen.getByRole("dialog");
    expect(
      within(dialog).getByRole("heading", { name: "删除这个电子文件？" }),
    ).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "删除" }));

    await waitFor(() => {
      expect(deleteBookFile).toHaveBeenCalledWith("f1");
    });
    expect(onChanged).toHaveBeenCalled();
  });

  it("shows an error when an upload fails", async () => {
    uploadBookFile.mockRejectedValue(new ApiRequestError("FILE_DUPLICATE", 409));
    const { container, onChanged } = renderPanel();

    const input = container.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;
    const file = new File(["x"], "book.pdf", { type: "application/pdf" });
    fireEvent.change(input, { target: { files: [file] } });

    await waitFor(() => {
      expect(screen.getByText("该文件已存在于本书。")).toBeInTheDocument();
    });
    expect(uploadBookFile).toHaveBeenCalledWith(
      "book-1",
      file,
      expect.any(Function),
    );
    expect(onChanged).toHaveBeenCalled();
  });
});