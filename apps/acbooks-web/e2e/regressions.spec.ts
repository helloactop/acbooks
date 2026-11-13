import { expect, test } from "@playwright/test";

import {
  addBook,
  chooseStatus,
  openAddBookDialog,
  uniqueTitle,
} from "./helpers";

test("selects a reading status inside the add dialog", async ({ page }) => {
  const dialog = await openAddBookDialog(page);
  const title = uniqueTitle("E2E 弹窗选择");
  await dialog.getByLabel("书名").fill(title);
  const status = dialog.getByRole("combobox", { name: "阅读状态" });
  await status.click();
  await page.getByRole("option", { name: "已读", exact: true }).click();
  await expect(status).toHaveText(/已读/);
  await dialog.getByRole("button", { name: "保存" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole("link", { name: `查看《${title}》详情` })).toBeVisible();
});

test("closes the dialog Select before the dialog on Escape", async ({ page }) => {
  const dialog = await openAddBookDialog(page);
  await dialog.getByRole("combobox", { name: "阅读状态" }).click();
  const option = page.getByRole("option", { name: "未读", exact: true });
  await expect(option).toBeVisible();

  await page.keyboard.press("Escape");
  await expect(option).toBeHidden();
  await expect(dialog).toBeVisible();

  await page.keyboard.press("Escape");
  await expect(dialog).toBeHidden();
});

test("applies an advanced title filter", async ({ page }) => {
  const title = uniqueTitle("E2E 高级筛选");
  await addBook(page, title);
  const other = await addBook(page, uniqueTitle("E2E 其他"));

  await page.getByRole("button", { name: "筛选", exact: true }).click();
  await page.getByRole("button", { name: "添加第一个筛选条件" }).click();
  await page.getByLabel("书名筛选值").fill(title);

  await expect(page).toHaveURL(/filters=/);
  await expect(page.getByRole("link", { name: `查看《${title}》详情` })).toBeVisible();
  await expect(other).toBeHidden();
});

test("recovers global search after typing A, B and A again", async ({ page }) => {
  const title = uniqueTitle("E2E 搜索");
  await addBook(page, title);
  const search = page.getByRole("combobox", { name: "全局搜索" });
  const result = page
    .getByRole("listbox", { name: "搜索结果" })
    .getByRole("option", { name: new RegExp(title) });

  await search.fill(title);
  await expect(result).toBeVisible();
  await search.fill("no-such-book-zzz");
  await expect(page.getByText("没有找到匹配的书籍")).toBeVisible();
  await search.fill(title);
  await expect(result).toBeVisible();

  await search.press("Enter");
  await expect(page).toHaveURL(/\/library\?q=/);
  await expect(page.getByRole("link", { name: `查看《${title}》详情` })).toBeVisible();
});

test("restores the filtered list after browser back", async ({ page }) => {
  const title = uniqueTitle("E2E 返回");
  const card = await addBook(page, title);
  await chooseStatus(page, "未读");
  await expect(page).toHaveURL(/readingStatus=unread/);

  await card.click();
  await expect(page).toHaveURL(/\/library\/books\//);
  await page.goBack();

  await expect(page).toHaveURL(/readingStatus=unread/);
  await expect(page.getByRole("link", { name: `查看《${title}》详情` })).toBeVisible();
});
