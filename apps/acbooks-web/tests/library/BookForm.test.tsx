import { I18nProvider } from "@acbooks/ui";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiRequestError } from "../../src/api/client";
import { appResources } from "../../src/i18n/resources";
import { BookForm } from "../../src/library/BookForm";
import type { BookDetail } from "../../src/library/books-api";

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
const ISBN = "9787536692930";
const CANDIDATE = {
  title: "三体",
  authors: ["刘慈欣"],
  translators: ["译者甲"],
  series: "地球往事",
  description: "科幻小说简介",
  isbn: ISBN,
  tags: ["科幻", "小说"],
};

function renderForm(book?: BookDetail) {
  const onSaved = vi.fn();
  const onFilesFailed = vi.fn();
  const view = render(
    <I18nProvider defaultLanguage="zh-CN" resources={appResources}>
      <BookForm
        book={book}
        mode={book ? "edit" : "create"}
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

describe("BookForm ISBN fill", () => {
  it("fills details and tags from ISBN alone and waits for Save", async () => {
    const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue(Response.json([CANDIDATE]));
    createBook.mockResolvedValue(CREATED);
    renderForm();
    const user = userEvent.setup();
    const fill = screen.getByRole("button", { name: "填充" });
    expect(fill.nextElementSibling).toBe(screen.getByRole("button", { name: "取消" }));
    expect(fill).toBeDisabled();

    await user.type(screen.getByLabelText("ISBN"), "7-5366-9293-5");
    await user.click(fill);

    await waitFor(() => expect(screen.getByLabelText("书名")).toHaveValue("三体"));
    expect(screen.getByLabelText("ISBN")).toHaveValue("7536692935");
    expect(screen.getByLabelText("简介")).toHaveValue(CANDIDATE.description);
    expect(screen.getByText("科幻")).toBeVisible();
    expect(fetch).toHaveBeenCalledWith("/api/v1/metadata/candidates?q=7536692935&source=douban", expect.anything());
    expect(createBook).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(createBook).toHaveBeenCalledWith(expect.objectContaining({
      authors: CANDIDATE.authors,
      translators: CANDIDATE.translators,
      series: CANDIDATE.series,
      tags: CANDIDATE.tags,
    }));
  });

  it("tries Open Library after a provider error and merges tags into an edited book", async () => {
    const fetch = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(Response.json({ code: "METADATA_UNAVAILABLE" }, { status: 502 }))
      .mockResolvedValueOnce(Response.json([{ ...CANDIDATE, isbn: "9780306406157", tags: ["错误版本"] }, CANDIDATE]));
    const book: BookDetail = {
      ...CANDIDATE, id: "existing", title: "手动书名", authors: ["手动作者"],
      tags: ["自定义", "科幻"], shelves: ["书房"], readingStatus: "reading",
      identifiers: [], copies: [], files: [], formats: [], copyCount: 0, fileCount: 0,
      hasCover: false, coverUpdatedAt: null, createdAt: 1, updatedAt: 1,
    };
    updateBook.mockResolvedValue(book);
    renderForm(book);
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "填充" }));
    expect(await screen.findByText("小说")).toBeVisible();
    expect(screen.getByLabelText("书名")).toHaveValue("手动书名");
    expect(screen.getByText("手动作者")).toBeVisible();
    expect(screen.queryByText("错误版本")).not.toBeInTheDocument();
    expect(fetch).toHaveBeenLastCalledWith(`/api/v1/metadata/candidates?q=${ISBN}&source=openlibrary`, expect.anything());
    expect(updateBook).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(updateBook).toHaveBeenCalledWith(book.id, { tags: ["自定义", "科幻", "小说"] });
  });

  it("reports invalid and unmatched ISBNs without changing the draft", async () => {
    const fetch = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(Response.json([CANDIDATE]))
      .mockResolvedValueOnce(Response.json([]));
    renderForm();
    const user = userEvent.setup();
    const isbn = screen.getByLabelText("ISBN");
    const fill = screen.getByRole("button", { name: "填充" });

    await user.type(screen.getByLabelText("书名"), "保留书名");
    await user.type(isbn, "123");
    await user.click(fill);
    expect(screen.getByText("请输入 ISBN-10 或 ISBN-13。")).toBeVisible();
    expect(fetch).not.toHaveBeenCalled();

    await user.clear(isbn);
    await user.type(isbn, "9780306406157");
    await user.click(fill);
    expect(await screen.findByText("没有找到与此 ISBN 匹配的书籍。")).toBeVisible();
    expect(screen.getByLabelText("书名")).toHaveValue("保留书名");
    expect(screen.queryByText("科幻")).not.toBeInTheDocument();
  });
});
