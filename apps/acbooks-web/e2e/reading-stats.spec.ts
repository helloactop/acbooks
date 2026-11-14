import { expect, test } from "@playwright/test";

import { addBook, uniqueTitle } from "./helpers";

test.use({ timezoneId: "Asia/Shanghai" });

test("renders reading charts from real sessions in the library and book detail", async ({ page }) => {
  const title = uniqueTitle("统计");
  const card = await addBook(page, title);
  const href = await card.getAttribute("href");
  expect(href).toMatch(/^\/library\/books\//);
  const id = href?.split("/").at(-1);
  const day = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit",
  }).format(new Date());
  const start = Date.parse(`${day}T08:00:00+08:00`);
  const csrf = await page.request.get("/api/v1/auth/csrf");
  const { csrfToken } = await csrf.json() as { csrfToken: string };
  for (const offset of [0, 3_600_000]) {
    const response = await page.request.post(`/api/v1/books/${id}/reading-sessions`, {
      headers: { "X-CSRF-Token": csrfToken, Origin: new URL(page.url()).origin },
      data: {
        deviceId: crypto.randomUUID(), mutationId: crypto.randomUUID(),
        startedAt: start + offset, endedAt: start + offset + 1_800_000, activeSeconds: 1800,
      },
    });
    expect(response.ok(), await response.text()).toBeTruthy();
  }
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.goto("/stats");
  await expect(page.getByRole("heading", { name: "阅读统计", exact: true })).toBeVisible();
  for (const name of ["阅读时长趋势", "阅读间隔趋势", "阅读次数", "阅读时段分布"]) {
    await expect(page.getByRole("img", { name, exact: true })).toBeVisible();
  }
  const calendar = page.getByRole("region", { name: /年阅读热力图/ });
  await expect(calendar.locator("svg").first()).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBe(390);
  await page.setViewportSize({ width: 1280, height: 720 });
  const link = page.getByRole("link", { name: title, exact: true }).first();
  await expect(link).toBeVisible();
  await link.click();
  await page.getByRole("button", { name: "阅读统计", exact: true }).click();
  await expect(page.getByRole("img", { name: "阅读时长趋势", exact: true })).toBeVisible();
  await expect(page.getByText("累计 60分钟")).toBeVisible();
  expect(errors).toEqual([]);
});
