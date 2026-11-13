import { expect, test, type Page } from "@playwright/test";

import { addBook, openAddBookDialog, uniqueTitle } from "./helpers";

async function addLocation(page: Page, name: string): Promise<void> {
  await page.getByRole("button", { name: "新增位置" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("位置名称").fill(name);
  await dialog.getByRole("button", { name: "保存" }).click();
  await expect(dialog).toBeHidden();
}

test("creates locations, adds an inline copy and filters by location", async ({
  page,
}) => {
  const room = uniqueTitle("房间");
  const shelf = uniqueTitle("层");
  const title = uniqueTitle("E2E 实体筛选");

  await page.goto("/locations");
  await addLocation(page, room);

  const locationList = page.getByRole("list", { name: "存放位置" });
  const roomRow = locationList
    .getByRole("listitem")
    .filter({ hasText: room });
  await roomRow.getByRole("button", { name: "添加子位置" }).click();
  const childDialog = page.getByRole("dialog");
  await childDialog.getByLabel("位置名称").fill(shelf);
  await childDialog.getByRole("button", { name: "保存" }).click();
  await expect(childDialog).toBeHidden();
  await expect(
    locationList.getByRole("listitem").filter({ hasText: shelf }),
  ).toBeVisible();

  const dialog = await openAddBookDialog(page);
  await dialog.getByLabel("书名").fill(title);
  await dialog.getByRole("button", { name: "添加副本" }).click();
  const locationSelect = dialog.getByRole("combobox", { name: "存放位置" });
  await expect(locationSelect).toBeEnabled();
  await locationSelect.click();
  await page
    .getByRole("option", { name: `${room} / ${shelf}` })
    .click();
  await dialog.getByLabel("备注").fill("精装");
  await dialog.getByRole("button", { name: "保存" }).click();
  await expect(dialog).toBeHidden();

  const card = page.getByRole("link", { name: `查看《${title}》详情` });
  await expect(card).toBeVisible();
  await card.click();
  await expect(
    page.getByRole("heading", { level: 1, name: title }),
  ).toBeVisible();
  await expect(page.getByText(shelf, { exact: true })).toBeVisible();
  await expect(page.getByText("精装")).toBeVisible();

  await page.goto("/locations");
  const roomItem = page
    .getByRole("list", { name: "存放位置" })
    .getByRole("listitem")
    .filter({ hasText: room });
  await roomItem.getByRole("link", { name: "查看书籍" }).click();
  await expect(page).toHaveURL(/\/library\?.*filters=/);
  await expect(
    page.getByRole("link", { name: `查看《${title}》详情` }),
  ).toBeVisible();
});

test("adds, edits, and deletes a physical copy from the detail page", async ({
  page,
}) => {
  const title = uniqueTitle("E2E 副本管理");
  const card = await addBook(page, title);
  await card.click();
  await expect(
    page.getByRole("heading", { level: 1, name: title }),
  ).toBeVisible();

  await page.getByRole("button", { name: "添加副本" }).click();
  let dialog = page.getByRole("dialog");
  await dialog.getByLabel("备注").fill("初版");
  await dialog.getByRole("button", { name: "保存" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByText("初版")).toBeVisible();

  await page.getByRole("button", { name: "编辑副本" }).click();
  dialog = page.getByRole("dialog");
  await dialog.getByLabel("备注").fill("修订");
  await dialog.getByRole("button", { name: "保存" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByText("修订")).toBeVisible();

  await page.getByRole("button", { name: "删除副本" }).click();
  const confirm = page.getByRole("dialog");
  await expect(
    confirm.getByRole("heading", { name: "删除这个实体副本？" }),
  ).toBeVisible();
  await confirm.getByRole("button", { name: "删除", exact: true }).click();
  await expect(confirm).toBeHidden();
  await expect(page.getByText("暂无实体副本")).toBeVisible();
});