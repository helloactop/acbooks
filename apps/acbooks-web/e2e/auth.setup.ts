import { mkdir } from "node:fs/promises";

import { test as setup } from "@playwright/test";

import { login } from "./helpers";

setup("authenticate", async ({ page }) => {
  await login(page);
  await mkdir("e2e/.auth", { recursive: true });
  await page.context().storageState({ path: "e2e/.auth/user.json" });
});
