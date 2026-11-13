import { expect, type Locator, type Page } from "@playwright/test";

export const USERNAME = "admin";
export const PASSWORD = "correct horse battery staple";

export function uniqueTitle(prefix: string): string {
  return `${prefix} ${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

export async function login(page: Page): Promise<void> {
  await page.goto("/");
  await page.getByLabel("用户名").fill(USERNAME);
  await page.getByLabel("密码", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page).toHaveURL(/\/home$/);
}

export async function openAddBookDialog(page: Page): Promise<Locator> {
  await page.goto("/library");
  await page.getByRole("button", { name: "添加书籍" }).first().click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  return dialog;
}

export async function addBook(
  page: Page,
  title: string,
  status?: "未读" | "阅读中" | "已读",
): Promise<Locator> {
  const dialog = await openAddBookDialog(page);
  await dialog.getByLabel("书名").fill(title);
  if (status !== undefined) {
    await dialog.getByRole("combobox", { name: "阅读状态" }).click();
    await page.getByRole("option", { name: status, exact: true }).click();
  }
  await dialog.getByRole("button", { name: "保存" }).click();
  await expect(dialog).toBeHidden();
  const card = page.getByRole("link", { name: `查看《${title}》详情` });
  await expect(card).toBeVisible();
  return card;
}

export async function chooseStatus(page: Page, label: string): Promise<void> {
  await page.getByRole("combobox", { name: "状态" }).click();
  await page.getByRole("option", { name: label, exact: true }).click();
}
