import { I18nProvider } from "@acbooks/ui";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiRequestError } from "../../src/api/client";
import { appResources } from "../../src/i18n/resources";
import { BookForm } from "../../src/library/BookForm";

const { createBook, updateBook, uploadBookFile } = vi.hoisted(() => ({
  createBook: vi.fn(),
  updateBook: vi.fn(),
  uploadBookFile: vi.fn(),
}));

vi.mock("../../src/library/books-api", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("../../src/library/books-api")>();
  return { ...actual, createBook, updateBook };
});

vi.mock("../../src/library/files-api", () => ({ uploadBookFile }));

const CREATED = { id: "book-created" };

function renderForm() {
  const onSaved = vi.fn();
  const onFilesFailed = vi.fn();
  const view = render(
    <I18nProvider defaultLanguage="zh-CN" resources={appResources}>
      <BookForm
        mode="create"
        onBusyChange={vi.fn()}
        onCloseRequest={vi.fn()}
        onDirtyChange={vi.fn()}
        onFilesFailed={onFilesFailed}
        onSaved={onSaved}
        onSessionExpired={vi.fn()}
      />
    </I18nProvider>,
  );
  return { ...view, onSaved, onFilesFailed };
}

beforeEach(() => {
  createBook.mockReset();
  updateBook.mockReset();
  uploadBookFile.mockReset();
  vi.spyOn(window.navigator, "languages", "get").mockReturnValue(["zh-CN"]);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("BookForm files", () => {
  it("creates the book then reports files that fail to upload", async () => {
    createBook.mockResolvedValue(CREATED);
    uploadBookFile.mockRejectedValue(new ApiRequestError("FILE_TOO_LARGE", 413));
    const { container, onSaved, onFilesFailed } = renderForm();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText("书名"), "带文件的书");
    const input = container.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;
    const file = new File(["x"], "book.pdf", { type: "application/pdf" });
    fireEvent.change(input, { target: { files: [file] } });

    await user.click(screen.getByRole("button", { name: "保存" }));

    await waitFor(() => {
      expect(onFilesFailed).toHaveBeenCalledWith(["book.pdf"]);
    });
    expect(createBook).toHaveBeenCalledTimes(1);
    expect(uploadBookFile).toHaveBeenCalledWith("book-created", file);
    expect(onSaved).toHaveBeenCalledWith(CREATED, "create");
  });
});