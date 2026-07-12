# Round 12：发布前 Compose smoke 可观测性计划

## 背景与问题边界

Round 11 发布机制提交 `7fd4abc` 已推送至 `main`。其准确 GitHub Actions run `29175449348` 中，`openapi`、`frontend`、`backend`、`e2e` 和 `security` 均成功，但 `container-smoke` 在执行 `scripts/compose-smoke.sh` 约 2 分 14 秒后以退出码 1 失败；依赖它的 `release-candidate` 被跳过，tag 与 Release 因而尚未创建。公共未认证页面只能核验失败 step 和退出码，不能取得完整日志，现有普通时间戳阶段输出也没有转化成页面可见的 GitHub annotation，因此当前证据不足以判断具体失败阶段或根因。

本轮只改善失败诊断的可见性，不猜测并修复未经证明的故障，不改变任何业务、接口、镜像、发布资产或发布授权语义。不得降低现有断言，不得延长或缩短超时，不得改变轮询次数、性能阈值、重试条件、失败条件、阶段顺序或容器清理行为，也不得创建或推送 `v0.1.0` tag。Round 11 的发布提交候选资格因准确 `main` CI 未全绿而尚未成立。

## 目标

1. 让 `compose-smoke.sh` 无论因显式 `fail`、直接命令、管道、严格 shell 模式还是信号而非零退出，GitHub Actions 页面都能通过一条脱敏、简短的 error annotation 看见准确的当前阶段、最后安全观测和原始退出码。
2. 保留本地和其他 CI 环境的可读阶段日志与有界容器诊断，同时不假定运行环境一定是 GitHub Actions。
3. 通过独立审核和本地行为测试证明诊断逻辑不会掩盖原始失败、泄漏响应正文或凭据，也不会改变 smoke 的断言与时序语义。
4. 推送经审核的普通提交并等待该准确 `main` run；若仍失败，只依据新公开 annotation 进入下一轮针对性规划—实施—审核，若全绿则以该新提交作为唯一允许创建 `v0.1.0` 的 release SHA。

## 实施范围

### 1. 维护明确的阶段状态

修改 `scripts/compose-smoke.sh`：

- 初始化 `current_stage` 为脚本启动前的安全值，并保留已有 `last_observation`。
- 在每个 Stage 1–10 开始前，先更新 `current_stage`，再打印现有阶段日志。阶段值只包含固定、非敏感的阶段编号与短名称，不拼接命令行、请求、token、email、密码、数据库密码、URL query、响应正文或容器日志。
- 在关键直接命令前按需更新 `last_observation` 为固定的安全动作描述，使 `set -e` 或 `pipefail` 直接退出时仍能定位；成功后可更新为该动作已通过。轮询 helper 现有的 HTTP 状态、attempt 和 token present/absent 描述可以保留，但绝不存储 token 或响应正文。
- 不通过给每条命令增加重试、`|| true` 或包装器来获得诊断；会失败的原命令及其退出语义保持不变。

### 2. 在 EXIT trap 发出安全 annotation

扩展现有 `cleanup`/辅助函数：

- `cleanup` 第一时间保存原始 `$?`；非零时，先发出单行摘要，再执行现有有界 `diagnostics`，最后清理临时文件和 Compose 资源并以原始状态退出。
- GitHub Actions 环境中使用 workflow command 形式 `::error title=Compose smoke failed::...`。消息只包含固定字段 `stage`、`last`、`exit`；对换行、回车、百分号以及 workflow command 具有语义的字符进行可靠转义/净化，避免 annotation 注入或多行日志污染。
- 非 GitHub 环境仍输出普通 `ERROR` 摘要，不输出伪 workflow command。环境判断只读取 GitHub 提供的布尔环境标志，不影响失败状态。
- 摘要输出自身不得因 `set -u`、格式化或写日志失败覆盖原始退出码；INT/TERM 经现有 trap 转成 130/143 后也必须在 annotation 中保留相应原始值。
- 保持诊断为 `docker compose ps`、容器状态和各服务有限尾部日志；本轮不上传额外 artifact、不 `tee` 全量控制台或响应、不扩大日志尾数，不把 diagnostics 内容塞入 annotation。

如果实施中发现现有 cleanup 顺序无法安全满足上述不变量，可以提取小型纯 shell helper；不得新增运行时依赖或修改 workflow 的 token/权限。

### 3. 定向行为测试

优先新增或扩展可独立运行的 shell 测试，对诊断 helper 或测试注入点做隔离验证，至少覆盖：

1. 显式失败产生准确 `stage/last/exit=1` annotation；
2. 未经 `fail` 包装的直接命令失败同样产生 annotation；
3. 管道失败保留非零退出码；
4. INT/TERM 保留 130/143；
5. 普通非 GitHub 环境只有普通错误摘要；
6. `last_observation` 含 `%`、换行、回车、`::` 等内容时只形成一条安全 annotation，不能注入第二条 workflow command；
7. annotation/diagnostics 或容器清理自身失败时，最终进程仍返回最初失败码；
8. 成功路径不产生 error annotation。

测试不得运行真实发布，不得依赖 GitHub Token，也不得用弱化主脚本断言的方式制造可测性。若为测试增加受控环境开关，该开关必须默认关闭、只影响测试 harness，且 reviewer 要确认生产 CI 不会误启用。

## 文档与审计同步

实施终端在 `docs/ai-governance/AI_CHANGE_LOG.md` 追加本轮触发、保守诊断决策、验证结果和外部 CI 状态。若提交/CI 证据变化需要同步交接文档，应明确区分：

- `7fd4abc` / run `29175449348` 是失败触发证据，不是可发布 SHA；
- 本轮诊断提交在准确 `main` CI 全绿前只是候选；
- `TODO.md` 的“发布 MVP”继续为 `[ ]`；
- tag、Release、附件、真实模型和 Codespaces 状态不得提前宣称完成。

本轮不改产品文档、API 契约或 Release notes，除非审核发现其中存在与当前外部事实直接冲突的陈述。

## 独立审核与修正循环

由未参与实施的 reviewer 只创建 `docs/reviews/round-12-release-ci-smoke-diagnostics-review.md`，不得修改实现。审核至少包括：

1. 对照本计划、实际 diff、Round 8/11 审核与 `compose-smoke.sh`，确认变更仅增加诊断，不改变十阶段顺序、等待次数、超时、性能阈值、重试和断言。
2. 审核 EXIT trap 的退出码保存、信号行为、递归 trap 防护、diagnostics/cleanup 失败隔离和成功路径静默性。
3. 审核 workflow command 转义及字段来源，确认 annotation 不含 secret、认证头、用户输入正文、API 响应、数据库凭据或无界容器日志，且恶意/异常观察值不能注入 annotation。
4. 独立运行新增行为测试、`bash -n scripts/*.sh`、`git diff --check` 和比例相称的 `scripts/check.sh`；若环境允许，再运行真实 Compose smoke，但本地 Docker 权限不足不能用旧 run 替代准确推送后 CI。
5. 给出明确 `PASS` 或 `FAIL`。任何阻断由实施终端修正，再由同一 reviewer 重跑受影响检查并追加复审；reviewer 不直接修实现。

## 提交、CI 与失败分支

1. reviewer 最终 `PASS` 后，提交并推送普通 `main` 变更；推送前保持 `TODO.md` 未完成且不得创建 tag。
2. 记录完整提交 SHA，等待与该 SHA 准确对应的 Actions run。必须核验 `openapi`、`frontend`、`backend`、`e2e`、`container-smoke`、`security`、`release-candidate` 全部成功；普通 main run 的 `release` job 不应执行。
3. 若 `container-smoke` 再次失败，读取公开 error annotation：
   - annotation 足以定位时，以准确阶段和最后观测为证据启动下一轮独立规划，禁止通过放宽门禁或重跑直到偶然成功来绕过；
   - annotation 缺失、被截断或不安全时，本轮诊断实现本身未达标，回到实施—同一 reviewer 复审；
   - 其他 job 失败时同样按实际证据规划修正，不能把局部成功称为发布就绪。
4. 只有上述准确 run 七 job 全绿，该诊断提交才能取代 `7fd4abc` 成为 release SHA。之后仍须按 Round 11 计划重新核对工作树、`origin/main`、远端 tag/Release 冲突，再创建 annotated tag；本轮计划本身不执行 tag。

## 完成定义

Round 12 只有同时满足以下条件才完成：

1. 当前阶段、最后安全观测和原始退出码能在所有非零路径形成一条可公开查看的脱敏 GitHub error annotation，本地失败也有等价普通摘要。
2. 行为测试证明直接命令、管道、信号、转义、cleanup 二次失败及成功路径语义；原有 smoke 的十阶段断言、时序、阈值和清理语义未被弱化。
3. 独立 reviewer 对实际实现和测试最终给出 `PASS`，本地语法、diff 和项目门禁通过。
4. 普通提交已推送，准确 `main` GitHub Actions run 的七个质量/候选 job 全部成功；若失败，已经依据 annotation 回到新一轮而不是继续发布。
5. 全绿提交被明确记录为后续唯一 release SHA，远端仍无本轮擅自创建的 tag/Release，`TODO.md` 的“发布 MVP”仍保持未勾选，等待 Round 11 正式发布和发布后证据闭环。
