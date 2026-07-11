import { test, expect } from "@playwright/test";
test("mobile layout has no horizontal overflow and keeps guest actions reachable", async ({
  page,
}) => {
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: /把想去的地方/ }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: /开始规划/ }).first(),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () =>
        document.documentElement.scrollWidth <=
        document.documentElement.clientWidth,
    ),
  ).toBe(true);
  await page
    .getByRole("link", { name: /开始规划/ })
    .first()
    .focus();
  await expect(
    page.getByRole("link", { name: /开始规划/ }).first(),
  ).toBeFocused();
});
test("mobile authenticated navigation opens and closes", async ({ page }) => {
  await page.goto("/register");
  await page.getByLabel("昵称").fill("移动用户");
  await page
    .getByLabel("邮箱")
    .fill(`mobile-${Date.now()}-${Math.random()}@example.com`);
  await page.getByLabel("密码").fill("secure-pass-123");
  await page.getByRole("button", { name: "注册并开始" }).click();
  await page.getByRole("button", { name: "菜单" }).click();
  await expect(
    page.getByRole("navigation", { name: "主要导航" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "AI 咨询" }).click();
  await expect(
    page.getByRole("heading", { name: "AI 旅游咨询" }),
  ).toBeVisible();
  await expect(
    page.getByRole("navigation", { name: "主要导航" }),
  ).not.toBeVisible();
});
