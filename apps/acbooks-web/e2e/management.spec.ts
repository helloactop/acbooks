import { expect, test } from "@playwright/test";

import { addBook, openAddBookDialog, uniqueTitle } from "./helpers";

test("batch edits and deletes selected books", async ({ page }) => {
  const first = uniqueTitle("E2E 批量一");
  const second = uniqueTitle("E2E 批量二");
  await addBook(page, first);
  const secondCard = await addBook(page, second);

  await page.getByRole("button", { name: "选择", exact: true }).click();
  await page.getByRole("checkbox", { name: `选择《${first}》` }).check();
  await page.getByRole("checkbox", { name: `选择《${second}》` }).check();
  await expect(page.getByText("已选 2 本")).toBeVisible();

  await page.getByRole("button", { name: "批量修改" }).click();
  const editDialog = page.getByRole("dialog");
  await editDialog.getByRole("combobox", { name: "阅读状态" }).click();
  await page.getByRole("option", { name: "已读", exact: true }).click();
  await editDialog.getByRole("button", { name: "保存修改" }).click();
  await expect(editDialog).toBeHidden();
  await expect(
    page.getByRole("button", { name: "选择", exact: true }),
  ).toBeVisible();

  await page.getByRole("button", { name: "选择", exact: true }).click();
  await page.getByRole("checkbox", { name: `选择《${first}》` }).check();
  await page.getByRole("checkbox", { name: `选择《${second}》` }).check();
  await page.getByRole("button", { name: "删除", exact: true }).click();
  const deleteDialog = page.getByRole("dialog");
  await expect(
    deleteDialog.getByRole("heading", { name: "删除 2 本书？" }),
  ).toBeVisible();
  await deleteDialog.getByRole("button", { name: "删除", exact: true }).click();
  await expect(deleteDialog).toBeHidden();
  await expect(secondCard).toBeHidden();
});

test("uploads, replaces, and removes a cover", async ({ page }) => {
  const title = uniqueTitle("E2E 封面");
  const card = await addBook(page, title);
  await card.click();
  await expect(page.getByRole("heading", { level: 1, name: title })).toBeVisible();

  const png = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    "base64",
  );
  await page.locator('input[type="file"][accept*="image"]').setInputFiles({
    buffer: png,
    mimeType: "image/png",
    name: "cover.png",
  });
  const image = page.locator('img[src*="/cover?v="]');
  await expect(image).toBeVisible();

  await page.getByRole("button", { name: "封面管理" }).click();
  await page.getByRole("menuitem", { name: "删除封面" }).click();
  await expect(image).toBeHidden();
});


test("reviews duplicate candidates on the library cleanup page", async ({ page }) => {
  const title = uniqueTitle("重复候选");
  await addBook(page, title);
  const addDialog = await openAddBookDialog(page);
  await addDialog.getByLabel("书名").fill(title);
  await addDialog.getByRole("button", { name: "保存", exact: true }).click();
  await expect(addDialog).toBeHidden();
  await page.goto("/organization");
  await expect(page.getByRole("heading", { name: "书库整理" })).toBeVisible();
  const row = page.getByRole("listitem").filter({ hasText: title });
  await expect(row.getByText("书名相同")).toBeVisible();
  await row.getByRole("button", { name: "合并书籍" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("button", { name: "预览合并" }).click();
  await expect(dialog.getByRole("button", { name: "交换书目" })).toBeVisible();
  await dialog.getByRole("button", { name: "取消" }).click();
  await expect(row.getByRole("link", { name: title, exact: true })).toHaveCount(2);
});
