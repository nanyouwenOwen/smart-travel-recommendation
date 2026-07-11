import { test, expect } from "@playwright/test";

test("register, plan, version and AI consultation work against the real API", async ({
  page,
}) => {
  const email = `e2e-${Date.now()}@example.com`;
  await page.goto("/register");
  await page.getByLabel("昵称").fill("端到端用户");
  await page.getByLabel("邮箱").fill(email);
  await page.getByLabel("密码").fill("secure-pass-123");
  await page.getByRole("button", { name: "注册并开始" }).click();
  await expect(page.getByRole("heading", { name: "我的行程" })).toBeVisible();

  await page.reload();
  await expect(page.getByRole("heading", { name: "我的行程" })).toBeVisible();
  await page
    .getByRole("link", { name: /新建规划/ })
    .first()
    .click();
  await page.getByLabel("目的地").fill("京都");
  await page.getByRole("button", { name: "搜索地点" }).click();
  await page.getByRole("button", { name: /京都.*示例地区/ }).click();
  await page
    .getByLabel("开始日期")
    .fill(new Date(Date.now() + 86_400_000).toISOString().slice(0, 10));
  await page.getByLabel("旅行偏好（逗号分隔）").fill("历史，美食");
  await page.getByRole("button", { name: "生成我的行程" }).click();
  await expect(
    page.getByRole("heading", { name: /京都/, level: 1 }),
  ).toBeVisible();
  await expect(page.getByText("逐日安排")).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText(/AI 估算 · 非实时/)).toBeVisible();
  await expect(page.getByRole("heading", { name: "目的地地图" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "行程天气" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "附近景点" })).toBeVisible();
  await expect(page.getByText("演示数据（非实时）").first()).toBeVisible();

  await page.getByLabel("用自然语言调整").fill("第二天增加一个安静的室内活动");
  await page.getByRole("button", { name: "生成新版本" }).click();
  await expect(page.getByRole("button", { name: /v2/ })).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("button", { name: /v1/ }).click();
  await page.getByRole("button", { name: "恢复此版本" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "确认" }).click();
  await expect(page.getByText("· v1")).toBeVisible();

  await page.getByRole("link", { name: "AI 咨询" }).click();
  await page.getByLabel("标题（可选）").fill("京都咨询");
  await page.getByLabel("关联行程（可选）").selectOption({ index: 1 });
  await page.getByRole("button", { name: "创建并开始提问" }).click();
  await page.getByLabel("输入旅游问题").fill("行程天气如何，需要带雨具吗？");
  await page.getByRole("button", { name: "发送" }).click();
  await expect(page.getByText("数据来源")).toBeVisible({ timeout: 30_000 });
  await page.reload();
  await expect(
    page.getByText("行程天气如何，需要带雨具吗？", { exact: true }),
  ).toBeVisible();
  await expect(page.getByText("演示数据（非实时）").last()).toBeVisible();
});
