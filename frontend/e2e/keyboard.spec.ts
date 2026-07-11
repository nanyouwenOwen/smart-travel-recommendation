import { test, expect } from "@playwright/test";
test("keyboard completes login, trip creation and message sending", async ({
  page,
}) => {
  const email = `keyboard-${Date.now()}@example.com`,
    password = "secure-pass-123";
  await page.request.post("/api/v1/auth/register", {
    data: { email, password, displayName: "键盘用户" },
  });
  await page.goto("/login");
  await page.getByLabel("邮箱").focus();
  await page.keyboard.type(email);
  await page.keyboard.press("Tab");
  await page.keyboard.type(password);
  await page.keyboard.press("Tab");
  await page.keyboard.press("Enter");
  await expect(page.getByRole("heading", { name: "我的行程" })).toBeVisible();
  await page
    .getByRole("link", { name: /新建规划/ })
    .first()
    .focus();
  await page.keyboard.press("Enter");
  await page.getByLabel("目的地").focus();
  await page.keyboard.type("苏州");
  await page
    .getByLabel("开始日期")
    .fill(new Date(Date.now() + 86_400_000).toISOString().slice(0, 10));
  await page.getByRole("button", { name: "生成我的行程" }).focus();
  await page.keyboard.press("Enter");
  await expect(
    page.getByRole("heading", { name: "苏州", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "AI 咨询" }).focus();
  await page.keyboard.press("Enter");
  await page.getByRole("button", { name: "创建并开始提问" }).focus();
  await page.keyboard.press("Enter");
  await page.getByLabel("输入旅游问题").focus();
  await page.keyboard.type("推荐一条步行路线");
  await page.getByRole("button", { name: "发送" }).focus();
  await page.keyboard.press("Enter");
  await expect(page.getByText(/关于“推荐一条步行路线”/)).toBeVisible({
    timeout: 30_000,
  });
});
