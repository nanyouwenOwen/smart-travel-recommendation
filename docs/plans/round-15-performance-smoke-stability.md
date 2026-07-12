# Round 15：Container performance smoke 稳定性与证据计划

## 背景与问题边界

GitHub Actions 的 `container-smoke` 已不止一次在 `scripts/compose-smoke.sh` 的 `stage=7/10 performance thresholds` 失败，而相邻提交又可通过。当前 `tests/performance/smoke.js` 以固定 10 VU 持续 30 秒，每次迭代先请求 `/api/v1/health`、再请求前端 `/`，并用全体请求聚合后的 `http_req_failed rate<0.01` 与 `http_req_duration p(95)<2000` 作为门禁。Stage 6 只在测量前做一次健康请求；健康状态只能证明服务可用，不能证明刚构建、刚启动并完成业务、SSE、备份恢复负载的 JVM、Nginx、文件缓存和共享 runner 已进入稳定测量状态。当前 k6 结果也没有保存结构化 summary，后端与前端样本混在同一聚合指标中，因此历史 annotation 只能证明“阈值失败”，不能区分冷态长尾、某个端点退化、HTTP/传输错误或 runner 资源争用。

本轮解决的是可复现的测量边界和充分证据，不把性能门禁改成概率性重试。以下约束不可改变：

- 正式测量仍为 **10 VU、连续 30 秒**，每个 VU 仍同时覆盖后端健康接口与前端首页；
- 正式测量总体错误率仍须严格 `<1%`，总体响应时间 p95 仍须严格 `<2000ms`；
- 一次正式测量失败即为本次 job 失败，不重跑后取较好结果，不以多数通过、自动重试、`continue-on-error` 或 `|| true` 制造绿色；
- 不通过减少 VU、缩短时长、增加请求间 sleep、排除慢样本、提高阈值或更换更宽松统计口径来稳定 CI；
- 不修改产品业务行为、API 契约、Docker 资源配额或发布资产内容来掩盖测量问题。

## 目标

1. 为冷启动/预热和正式测量建立明确、可审核的边界：只有达到固定且有界的稳定条件后才开始一次 10 VU/30 秒门禁，预热样本绝不进入正式阈值统计。
2. 无论成功或失败，都产生可机读且有界的 k6 summary，能区分后端、前端、HTTP 错误、check 失败以及正式测量总体 p95/错误率。
3. 通过确定性测试证明阈值、负载和失败传播没有被弱化；通过诊断运行判断波动是否来自测量冷态，不能在证据不足时把它宣称为 runner 噪声或真实性能回归。
4. 经独立审核后推送准确提交，要求同一提交的完整 CI 单次通过；随后完成已获授权的 `v0.1.0` Release 恢复、附件核验和一次性 recovery 清理。

## 实施范围

### 1. 先建立可复现基线，不先修改门禁

实施终端先记录当前脚本与失败/成功 run 的准确 SHA、run id、job 结论和可取得的 Stage 7 annotation。不得从相邻绿色 run 推断失败原因。若 GitHub 日志可访问，提取并保存以下非敏感事实到 Round 15 审核或治理记录，而不是复制整份原始日志：

- k6 的 `http_reqs`、`iterations`、`http_req_failed`、`http_req_duration`（至少 avg、med、p90、p95、max）和 `checks`；
- 失败阈值名称、实际值、失败/成功时间点；
- runner 与三个容器在测量前后的有界资源快照；
- 两个端点是否都返回正确内容，以及失败是否为状态码、transport error 或 check 内容不匹配。

如果旧 run 没有这些数据，明确记为“历史证据缺失”，不得补写猜测。允许新增一个人工/本地诊断命令在同一已启动栈上连续执行若干次并保存每次完整结果，用于比较冷态和稳定态；该命令必须明确标注为诊断、不得接入通过判定，也不得选择其中最好一次作为 CI 证据。

### 2. 将预热和正式测量严格分离

修改 `tests/performance/smoke.js` 与必要的调用脚本，使单次 k6 执行具有两个清晰 phase：

1. **预热 phase**：分别触达后端健康接口和前端首页，验证状态码与首页标识；使用固定、有界的请求/持续时间和明确的 `phase=warmup`、`endpoint=backend|frontend` 标签。预热失败、未得到正确内容或未达到计划中固定的连续成功条件时立即失败，不进入正式测量。不得靠无限轮询或逐次延长等待跨过真实故障。
2. **正式 measurement phase**：预热成功后才启动；严格保持 10 个并发 VU、30 秒持续时间以及每次迭代的两个目标请求。正式请求必须统一带 `phase=measurement`，并分别带 `endpoint=backend|frontend` 标签。

阈值必须显式绑定正式 measurement 样本，仍执行总体 `http_req_failed rate<0.01` 和 `http_req_duration p(95)<2000`；其语义与现门禁相同，只排除计划明示的预热样本。不得让预热的快速样本稀释正式 p95，也不得让预热失败被排除后继续测量。保留两个业务 check，并让正式 check 失败能够造成非零结果（例如对 measurement checks 设置 100% 成功门槛），避免 HTTP 200 但错误页面被当作性能成功。端点级 tagged 指标用于诊断；如增加端点级阈值，只能等于或严于总体约束，不能替代或弱化总体阈值。

优先在同一固定 k6 `0.57.0` 进程内用受支持的 scenario/tag 机制表达阶段边界，避免 shell 两次启动带来的额外环境差异。若 k6 生命周期或 executor 无法可靠保证预热结束后才开始测量，则允许用两个明确子命令实现，但正式命令只能执行一次，预热的成功退出必须是它的前置条件，且两者 summary 和标签不得混淆。实施记录要说明选择依据。

### 3. 保存、校验并呈现结构化 summary

扩展 `scripts/perf.sh`，为每次执行创建受控输出目录，并通过只读脚本输入和可写挂载从固定 k6 容器输出 JSON summary。要求：

- summary 即使阈值失败也应尽力保留；k6 原始退出码必须在读取/打印/清理 summary 后原样返回；summary 缺失、空文件、无效 JSON、缺少正式 measurement 指标或样本数为零应使门禁失败；
- summary 至少能审计版本、阶段/端点标签、请求数、迭代数、check、失败率以及 duration 的 avg/med/p90/p95/max；不得包含 token、请求/响应正文、用户输入或凭据；
- 控制台只打印固定字段的有界摘要，失败 annotation 应指出失败阈值及 summary artifact 名称，而不是把整份 JSON 注入 workflow command；
- 输出路径支持调用方显式传入，默认本地临时路径不污染工作树；`compose-smoke.sh` 在 CI 中使用稳定 artifact 路径，cleanup 不应在 workflow 上传前删掉它。

修改 `.github/workflows/ci.yml`，在 `container-smoke` job 使用 `if: always()` 上传本次性能 summary（可连同现有有界 smoke 诊断，但不得上传 secrets、数据库 dump 或无界容器日志），设置短期 retention 和本次 run 唯一 artifact 名称。artifact 上传失败不能把原先的真实性能退出码变成成功；成功 run 也上传 summary，以便比较而不是只观察失败样本。

在 Stage 6/7 边界记录 `docker stats --no-stream` 或等价的有界 CPU/内存快照及容器健康状态，分别标记为测量前/后。资源采集属于诊断：采集失败不得替代或覆盖主门禁结果，输出需有界且不包含环境变量。不得据单次高 CPU 就断言根因。

### 4. 对门禁语义做自动化防回归测试

新增或扩展可独立运行的定向测试，至少覆盖：

1. 静态/行为断言证明 measurement 精确为 10 VU、30 秒，且每迭代仍请求后端和前端；
2. measurement 总体阈值精确保持 `rate<0.01` 与 `p(95)<2000`，并只过滤 `phase=measurement`；预热指标不能进入正式统计；
3. 预热后端失败、前端状态错误或首页标识错误时不启动 measurement；
4. measurement 的 HTTP 错误、内容 check 失败、p95 超标和错误率超标分别非零退出，且不存在自动第二次正式测量；
5. 成功/失败均生成有效 summary；缺失、空白、畸形、无 measurement 样本的 summary 均失败；
6. k6 返回特定非零码时，summary 解析、打印或清理的二次问题不覆盖原码；反之，k6 成功但 summary 校验失败时返回明确非零；
7. 输出路径、Docker mount 和文件权限不允许覆盖仓库任意文件，控制台/annotation 不泄漏环境变量或响应正文；
8. CI artifact 使用 `if: always()`，普通失败仍可取证，且 workflow 权限不增加。

测试优先通过 mock `docker`/fixture summary 验证 shell 控制流，并用固定 k6 镜像执行脚本语法/低成本 fixture 验证标签与 summary schema。测试 fixture 不得通过复制生产阈值常量后只做自我验证；reviewer 必须直接检查实际 k6 options 和真实调用参数。

### 5. 文档与治理同步

实施终端在 `docs/ai-governance/AI_CHANGE_LOG.md` 记录本轮触发证据、预热边界、未放宽阈值的决定、测试和准确 CI 结果；按需要同步 `docs/reports/security-performance.md` 与 `docs/ai-governance/PROJECT_HANDOFF.md`，明确：

- 这是共享 GitHub runner 上的小型演示烟测门禁，不是生产 SLA、容量结论或 Xiaomi MiMo 上游性能测试；
- 预热样本被隔离，正式 10 VU/30 秒门禁及 `<1%`、`<2000ms` 不变；
- 真实原因只有在 summary/资源/端点证据支持时才可陈述；否则使用“波动被隔离/证据仍不足”等审慎表述；
- `TODO.md` 的“发布 MVP”在 GitHub Release 和全部附件真实核验前继续为未完成。

不得把性能 artifact 或运行日志放进 `docs/ai-governance/`，不得提交生成的临时 summary；业务性能说明仍放普通 `docs/reports/`，AI 决策与交接证据只放治理目录。

## 独立审核与修正循环

由未参与实施的 reviewer 创建 `docs/reviews/round-15-performance-smoke-stability-review.md`，只审核和测试，不编辑实现。审核必须包括：

1. 对照本计划和实际 diff，逐项核验 10 VU、30 秒、两端点、总体 p95 `<2000ms`、错误率 `<1%` 均未放宽，并确认没有重试取优、条件跳过、sleep 降载或历史绿色替代当前结果；
2. 验证预热与 measurement 的执行顺序、标签和阈值 selector，证明预热样本既不能稀释正式指标，也不能在失败时被忽略；
3. 审核 summary 在成功、阈值失败、transport failure、信号和解析失败时的落盘与退出码优先级，验证 annotation/artifact 脱敏、有界、可关联准确 run；
4. 独立运行新增定向测试、`bash -n`、`git diff --check`、k6 fixture/脚本检查和比例相称的项目门禁；环境允许时运行一次完整 Compose smoke，记录所有结果而不是只保留成功样本；
5. 给出明确 `PASS`/`FAIL` 和具体 blocker。实施终端修正 blocker，同一 reviewer 重跑受影响门禁并追加复审；reviewer 不修改实现。

审核 PASS 只证明实现可推送，不替代远端准确提交的单次完整 CI 结果。

## 推送、准确 CI 与 Release 收尾

1. 独立 reviewer 最终 PASS 后，提交并推送普通 `main` 变更，记录完整 SHA；不得修改或移动已存在的 annotated tag `v0.1.0`，其目标必须继续为 `52864b1aa72f56081abfc0bd146415d2a5f1ccb8`。
2. 等待该准确 SHA 的 GitHub Actions。`openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security`、`release-candidate` 必须在这一次 run 全部成功；performance 正式门禁只执行一次。核验并下载本次 summary，确认正式样本存在、阈值实际通过、两端点 checks 全部正确。
3. 若 Stage 7 仍失败，保留失败 summary 和资源快照，以端点/指标证据进入实施修正—同一 reviewer 复审；不得 rerun job、推空/纯文档提交碰运气或接受相邻绿色。若失败证明是产品性能回归，则修产品并重新走完整循环；若证据证明稳定条件仍不足，则只调整有界预热判定，不改正式负载与阈值。
4. 准确 run 全绿后，允许既有一次性 `release-recovery-v0-1-0` 按其固定 tag/run/artifact 身份约束执行。必须通过 GitHub API 和公开下载独立核验 Release 为非 draft、非 prerelease，标题/正文/tag/target 正确，且固定 8 个附件名称、非零大小及校验全部正确。
5. Release 核验成功后立即删除一次性 recovery job，更新发布清单、MVP 验证、交接/人工操作记录和 `TODO.md`；再由同一 reviewer 审核删除与证据，推送收尾提交，并等待收尾提交的普通 CI 全绿。收尾提交不得触发第二次发布写入。

## 完成定义

Round 15 以及 MVP 发布收尾只有同时满足以下条件才完成：

1. 预热与一次正式 10 VU/30 秒 measurement 有可证明的边界；正式总体 `rate<0.01`、`p(95)<2000` 和两个端点内容正确性均是强制门禁，没有自动重试或样本稀释；
2. 成功/失败均得到有效、脱敏、可关联准确 run 的结构化 summary 和有界资源证据，失败时保留真实 k6 退出语义；
3. 定向测试及项目门禁通过，独立 reviewer 对实际 diff 与复审结果最终给出 PASS；
4. 准确实现提交已推送，其同一次 GitHub Actions 的七个质量/候选 job 单次全部成功，summary 证明正式指标确实通过；
5. annotated tag 仍指向固定发布 SHA，GitHub Release 已发布且 8 个候选附件经远端下载复验；一次性 recovery 已删除，收尾提交和准确 CI 全绿；
6. `TODO.md` 仅在上述发布事实成立后勾选“发布 MVP”，工作树干净且 `HEAD == origin/main`，项目与 AI 治理文档保持分区并记录最终证据。
