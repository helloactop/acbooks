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
  for (const name of ["阅读时长趋势", "阅读时段分布"]) {
    await expect(page.getByRole("img", { name, exact: true })).toBeVisible();
  }
  await expect(page.getByRole("img", { name: "阅读次数", exact: true })).toHaveCount(0);
  await expect(page.getByRole("img", { name: "阅读间隔趋势", exact: true })).toHaveCount(0);
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
  await expect(page.getByText("累计 1小时")).toBeVisible();
  expect(errors).toEqual([]);
});

test("manages reading records across statistics and book detail", async ({ page }) => {
  const title = uniqueTitle("阅读记录");
  const card = await addBook(page, title);
  const href = await card.getAttribute("href");
  if (!href) throw new Error("Book detail link is missing");
  await page.goto("/stats");
  await page.getByRole("button", { name: "添加阅读记录" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("搜索书籍").fill(title);
  await dialog.getByRole("combobox", { name: "书籍" }).click();
  await dialog.getByRole("option", { name: title, exact: true }).click();
  await dialog.getByLabel("开始时间").fill("2021-06-10T08:00");
  await dialog.getByLabel("结束时间").fill("2021-06-10T08:30");
  await dialog.getByRole("button", { name: "保存" }).click();
  await expect(dialog).toBeHidden();

  async function choosePeriod(name: string) {
    await page.getByRole("combobox", { name: "阅读统计周期" }).click();
    await page.getByRole("option", { name, exact: true }).click();
  }
  await choosePeriod("全部时间");
  const record = page.getByRole("listitem").filter({ has: page.getByRole("link", { name: title, exact: true }) });
  await expect(record).toBeVisible();
  await choosePeriod("自定义");
  await page.getByLabel("开始日期").fill("2020-01-01");
  await page.getByLabel("结束日期（含当天）").fill("2021-06-10");
  await page.getByRole("button", { name: "应用", exact: true }).click();
  await expect(record).toBeVisible();
  const calendar = page.getByRole("region", { name: "月度阅读日历" });
  await calendar.getByRole("button", { name: /6月9日/ }).click();
  await expect(dialog.getByText("当天暂无阅读活动。")).toBeVisible();
  await dialog.getByRole("button", { name: "查看当天记录" }).click();
  await expect(record).toHaveCount(0);
  const activeDay = calendar.getByRole("button", { name: /6月10日.*30分钟/ });
  await expect(activeDay.locator("..")).toHaveClass(/reading-level-3/);
  const lightColor = await activeDay.locator("..").evaluate(cell => getComputedStyle(cell).backgroundColor);
  await page.emulateMedia({ colorScheme: "dark" });
  await expect.poll(() => activeDay.locator("..").evaluate(cell => getComputedStyle(cell).backgroundColor))
    .not.toBe(lightColor);
  await activeDay.focus();
  await page.keyboard.press("Enter");
  await expect(dialog.getByRole("link", { name: title, exact: true })).toBeVisible();
  await expect(dialog.getByRole("meter", { name: `《${title}》的阅读时长占比` })).toHaveAttribute("value", "1");
  await dialog.getByRole("button", { name: "查看当天记录" }).click();
  await expect(record).toBeVisible();

  await page.goto(href);
  await page.getByRole("button", { name: "阅读统计", exact: true }).click();
  await choosePeriod("全部时间");
  await expect(page.getByText("累计 30分钟", { exact: true })).toBeVisible();
  const timeline = page.getByRole("region", { name: "阅读时间线" });
  await timeline.getByRole("button", { name: "编辑", exact: true }).click();
  await dialog.getByLabel("有效时长（秒）").fill("600");
  await dialog.getByRole("button", { name: "保存" }).click();
  await expect(page.getByText("累计 10分钟", { exact: true })).toBeVisible();
  await timeline.getByRole("button", { name: "删除", exact: true }).click();
  await dialog.getByRole("button", { name: "确认删除" }).click();
  await expect(timeline.getByText("本周期暂无阅读数据。")).toBeVisible();
  await expect(page.getByRole("img", { name: "阅读时长趋势" })).toHaveCount(0);
});
