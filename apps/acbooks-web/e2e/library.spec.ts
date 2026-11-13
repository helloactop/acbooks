import { expect, test } from "@playwright/test";

import { addBook, chooseStatus, login, uniqueTitle } from "./helpers";

test("adds a book and filters it by reading status", async ({ page }) => {
  await login(page);
  const title = uniqueTitle("E2E 筛选");
  await addBook(page, title);

  await chooseStatus(page, "阅读中");
  await expect(page).toHaveURL(/readingStatus=reading/);
  await expect(page.getByRole("link", { name: `查看《${title}》详情` })).toBeHidden();

  await chooseStatus(page, "未读");
  await expect(page).toHaveURL(/readingStatus=unread/);
  await expect(page.getByRole("link", { name: `查看《${title}》详情` })).toBeVisible();
});

test("edits a book through the detail dialog", async ({ page }) => {
  await login(page);
  const title = uniqueTitle("E2E 编辑");
  const card = await addBook(page, title);
  await card.click();
  await expect(page).toHaveURL(/\/library\/books\//);
  await expect(page.getByRole("heading", { level: 1, name: title })).toBeVisible();

  await page.getByRole("button", { name: "编辑", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  const updated = `${title} 修订`;
  await dialog.getByLabel("书名").fill(updated);
  await dialog.getByRole("button", { name: "保存" }).click();

  await expect(dialog).toBeHidden();
  await expect(page.getByRole("heading", { level: 1, name: updated })).toBeVisible();
  await page.goto("/library");
  await expect(page.getByRole("link", { name: `查看《${updated}》详情` })).toBeVisible();
});
