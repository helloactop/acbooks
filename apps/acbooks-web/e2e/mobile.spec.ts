import { expect, test } from "@playwright/test";

import { addBook, login, uniqueTitle } from "./helpers";

test("uses the drawer and adds a book on mobile", async ({ page }) => {
  await login(page);
  const menu = page.getByRole("button", { name: "打开导航菜单" });
  await menu.click();
  const drawer = page.getByRole("dialog", { name: "应用侧边栏" });
  await expect(drawer).toBeVisible();

  await drawer.getByRole("link", { name: "图书库" }).click();
  await expect(page).toHaveURL(/\/library$/);
  await expect(drawer).toBeHidden();

  const title = uniqueTitle("E2E 移动");
  await addBook(page, title);

  await menu.click();
  await expect(drawer).toBeVisible();
  await drawer.getByRole("button", { name: "关闭导航菜单" }).click();
  await expect(drawer).toBeHidden();
  await expect(page.getByRole("link", { name: `查看《${title}》详情` })).toBeVisible();
});
