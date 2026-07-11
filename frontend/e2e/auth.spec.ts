import { test, expect } from "@playwright/test";
test("empty states, protected routes, missing resource and logout are explicit", async ({
  page,
}) => {
  await page.goto("/trips");
  await expect(page).toHaveURL(/\/login/);
  const email = `empty-${Date.now()}@example.com`;
  await page.getByRole("link", { name: "立即注册" }).click();
  await page.getByLabel("昵称").fill("空状态用户");
  await page.getByLabel("邮箱").fill(email);
  await page.getByLabel("密码").fill("secure-pass-123");
  await page.getByRole("button", { name: "注册并开始" }).click();
  await expect(page.getByText("还没有行程")).toBeVisible();
  await page.goto("/trips/00000000-0000-0000-0000-000000000000");
  await expect(page.getByRole("alert")).toBeVisible();
  await page.getByRole("button", { name: "退出" }).click();
  await page.goto("/conversations");
  await expect(page).toHaveURL(/\/login/);
});
