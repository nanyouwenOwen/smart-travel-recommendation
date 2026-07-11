import { test, expect } from "@playwright/test";
async function register(page: any) {
  await page.goto("/register");
  await page.getByLabel("昵称").fill("流测试用户");
  await page
    .getByLabel("邮箱")
    .fill(`stream-${Date.now()}-${Math.random()}@example.com`);
  await page.getByLabel("密码").fill("secure-pass-123");
  await page.getByRole("button", { name: "注册并开始" }).click();
  await page.getByRole("link", { name: "AI 咨询" }).click();
}
test("navigation disconnect replays the active stream without duplicate answer", async ({
  page,
}) => {
  await register(page);
  await page.getByLabel("标题（可选）").fill("重连测试");
  await page.getByRole("button", { name: "创建并开始提问" }).click();
  await page
    .getByLabel("输入旅游问题")
    .fill("请详细说明这段行程应该如何安排交通并提醒我核验实时信息");
  await page.getByRole("button", { name: "发送" }).click();
  await expect(page.getByRole("button", { name: "停止回答" })).toBeVisible();
  await page.getByRole("link", { name: "AI 咨询" }).click();
  await page.getByRole("link", { name: /重连测试/ }).click();
  await expect(page.getByText(/关于“请详细说明/)).toBeVisible({
    timeout: 30_000,
  });
  await expect(page.getByText(/关于“请详细说明/)).toHaveCount(1);
});
test("explicit cancel reaches a persisted terminal state", async ({ page }) => {
  await register(page);
  await page.getByLabel("标题（可选）").fill("取消测试");
  await page.getByRole("button", { name: "创建并开始提问" }).click();
  await page
    .getByLabel("输入旅游问题")
    .fill("请生成一段足够详细的交通与餐饮建议以便测试取消");
  await page.getByRole("button", { name: "发送" }).click();
  await page.getByRole("button", { name: "停止回答" }).click({ force: true });
  await expect(page.getByText("CLIENT_CANCELLED")).toBeVisible({
    timeout: 15_000,
  });
});
