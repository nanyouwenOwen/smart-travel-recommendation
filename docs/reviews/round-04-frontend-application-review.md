# Round 04：完整前端应用审核

## 结论

**PASS**。经过五轮“独立审核 → 主终端修正 → 复审”，TODO 第 5 阶段可以完成，无剩余阻断。

## 主要审核修正

1. 行程调整从仅观察 `GENERATING` 改为按 `tripId` 保存 baseline、pending、error 和绝对 deadline；A/B 可并行调整，导航返回可恢复轮询，失败/超时保留旧版本并明确提示。
2. 未知网络结果复用相同 Idempotency-Key；表单内容改变或收到确定业务结果才轮换，并有组件测试。
3. SSE 使用共享认证刷新、建流超时、`X-Stream-Id`、29 秒可见性感知且有硬截止的重放；取消/离开页面可中断等待，不会在取消后重新连接。
4. 流事件集中校验 streamId、messageId、SSE id 与 delta sequence；后端修正 delta payload sequence，使契约与持久事件一致。
5. API 增加统一超时、单飞 refresh、请求追踪和刷新失败清理；行程/会话通过请求世代防止旧路由响应覆盖。
6. 完整表单边界、服务端字段错误映射、移动菜单、焦点可恢复确认对话框和克制 live region 均补齐。
7. Playwright 扩展到真实 API 的认证、行程/版本、断线重放、显式取消、键盘主路径、空/404 和移动交互；CI 同时执行桌面与移动项目。
8. E2E 脚本增加 MySQL 归属检测、就绪失败、trap 清理；覆盖率设置为提交门禁并忽略生成产物。

## 最终证据

- `npm run type-check`：PASS。
- `npm run test:coverage`：15 个文件、37 项测试全部通过；lines 91.81%、functions 91.17%、branches 78.82%，门禁 PASS。
- `npm run build`：PASS。
- Playwright 真栈：7/7 PASS；浏览器经 Vite 代理访问真实 Spring Boot，覆盖行程/版本/咨询重放与取消/键盘/移动/错误路径。
- 后端 `mvn verify`：38/38 PASS。
- OpenAPI minimal lint：valid，0 error；33 个既有非阻断 warning。
- `npm audit --audit-level=high`：0 vulnerability。

本地环境因 Docker socket 权限不能启动 MySQL 容器，浏览器行为验收使用同一 Spring Boot 应用的 H2 test profile；GitHub Actions E2E job 已配置 MySQL 8.4 service、Flyway、Stub AI、Spring Boot 和 Vite 真栈，推送后执行该门禁。
