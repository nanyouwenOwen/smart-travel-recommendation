# AI 治理与交接文档

本目录只保存 AI 协作流程、审计证据、人工操作清单和后续模型交接材料；不保存业务接口、部署规范或源代码说明。业务/技术事实仍以 `README.md`、`docs/openapi.yaml`、`docs/api-contract.md`、`docs/data-model.md`、`docs/deployment.md` 等为准。

阅读顺序：

1. [`WORKFLOW.md`](WORKFLOW.md)：今后所有修改必须遵循的规划—实施—独立审核循环。
2. [`HUMAN_ACTIONS.md`](HUMAN_ACTIONS.md)：DeepSeek/Xiaomi MiMo、Codespaces 和正式发布中必须由项目负责人完成的操作。
3. [`PROJECT_HANDOFF.md`](PROJECT_HANDOFF.md)：供后续大模型或工程师审查项目的完整入口、证据和风险。
4. [`AI_CHANGE_LOG.md`](AI_CHANGE_LOG.md)：从本轮开始追加的 AI 变更索引；历史细节见 `../ai-usage-log.md`。

禁止在本目录写入 API key、Token、密码、用户数据、完整私密提示词或生产日志。
