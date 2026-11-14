import { expect, test } from "@playwright/test";

import { addBook, uniqueTitle } from "./helpers";

const PDF = Buffer.from("%PDF-1.4\n% e2e\n%%EOF\n", "latin1");
const FILE_INPUT = 'input[type="file"][accept*="epub"]';

test("uploads, downloads and deletes an ebook file", async ({ page }) => {
  const title = uniqueTitle("E2E 文件");
  const card = await addBook(page, title);
  await card.click();
  await expect(
    page.getByRole("heading", { level: 1, name: title }),
  ).toBeVisible();
  await expect(page.getByText("暂无电子文件")).toBeVisible();

  await page.locator(FILE_INPUT).setInputFiles({
    buffer: PDF,
    mimeType: "application/pdf",
    name: "book.pdf",
  });
  await expect(page.getByRole("list", { name: "电子文件" }).getByText("PDF", { exact: true })).toBeVisible();
  await expect(page.getByText("可用")).toBeVisible();

  const [download] = await Promise.all([
    page.waitForEvent("download"),
    page.getByRole("link", { name: "下载文件" }).click(),
  ]);
  expect(download.suggestedFilename()).toMatch(/\.pdf$/);

  await page.getByRole("button", { name: "删除文件" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("button", { name: "删除", exact: true }).click();
  await expect(page.getByText("暂无电子文件")).toBeVisible();
});

test("attaches a file while adding a book", async ({ page }) => {
  const title = uniqueTitle("E2E 添加带文件");
  await page.goto("/library");
  await page.getByRole("button", { name: "添加书籍" }).first().click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("书名").fill(title);
  await dialog.locator(FILE_INPUT).setInputFiles({
    buffer: PDF,
    mimeType: "application/pdf",
    name: "book.pdf",
  });
  await dialog.getByRole("button", { name: "保存" }).click();
  await expect(dialog).toBeHidden();

  const card = page.getByRole("link", { name: `查看《${title}》详情` });
  await expect(card).toBeVisible();
  await card.click();
  await expect(page.getByRole("list", { name: "电子文件" }).getByText("PDF", { exact: true })).toBeVisible();
});