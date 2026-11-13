import { expect, test } from "@playwright/test";

import { addBook, uniqueTitle } from "./helpers";

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
  await page.locator('input[type="file"]').setInputFiles({
    buffer: png,
    mimeType: "image/png",
    name: "cover.png",
  });
  const image = page.locator('img[src*="/cover?v="]');
  await expect(image).toBeVisible();

  await page.getByRole("button", { name: "删除封面" }).click();
  await expect(image).toBeHidden();
});
