import { expect, test } from "@playwright/test";

test("issues and revokes a read-only application credential in settings", async ({ page }) => {
  await page.goto("/settings");
  await page.getByRole("link", { name: "打开应用凭据设置" }).click();
  await page.getByLabel("凭据名称").fill("Test OPDS");
  await page.getByRole("button", { name: "签发凭据", exact: true }).click();
  const issued = page.getByRole("dialog", { name: "应用凭据" });
  await expect(issued.getByRole("textbox")).toHaveValue(/^[A-Za-z0-9_-]{43}$/);
  await issued.getByRole("button", { name: "完成" }).click();
  await expect(issued).toHaveCount(0);
  const credential = page.getByRole("listitem").filter({ hasText: "Test OPDS" });
  await credential.getByRole("button", { name: "撤销", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "撤销", exact: true }).click();
  await expect(credential).toContainText("已撤销");
});
