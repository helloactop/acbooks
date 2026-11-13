import { expect, test } from "@playwright/test";

import { login, PASSWORD, USERNAME } from "./helpers";

test("rejects invalid credentials and signs in", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "登录 acbooks" })).toBeVisible();
  await page.getByLabel("用户名").fill(USERNAME);
  await page.getByLabel("密码", { exact: true }).fill("wrong-password");
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page.getByText("用户名或密码不正确，请重试。")).toBeVisible();

  // Failed logins start a short backoff; wait it out before retrying.
  await page.waitForTimeout(1_500);
  await page.getByLabel("密码", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page).toHaveURL(/\/home$/);
  await expect(page.getByRole("heading", { level: 1, name: "首页" })).toBeVisible();
});

test("keeps the session after a deep link reload", async ({ page }) => {
  await login(page);
  await page.goto("/library");
  await page.reload();
  await expect(page.getByRole("heading", { level: 1, name: "书库" })).toBeVisible();
  await expect(page).toHaveURL(/\/library$/);
});
