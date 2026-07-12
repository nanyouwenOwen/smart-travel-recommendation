# Round 15 性能烟测稳定性独立审核

## 首审结论：FAIL

审核日期：2026-07-12  
审核范围：工作树中 Round 15 实现与 `docs/plans/round-15-performance-smoke-stability.md` 逐项对照。审核端未修改实现代码。

## 已验证且符合计划的部分

- `tests/performance/smoke.js` 的唯一正式 scenario 明确为 `constant-vus`、`vus: 10`、`duration: '30s'`；正式函数每次仍请求 `/api/v1/health` 与 `/`，没有 sleep、自动重跑、`continue-on-error` 或取优逻辑。
- 正式总体 selector 精确为 `http_req_failed{phase:measurement}: rate<0.01` 与 `http_req_duration{phase:measurement}: p(95)<2000`；端点级阈值相同而非更宽松，正式 checks 要求 `rate==1`。
- 五次预热在 `setup()` 内顺序执行，预热请求带 `phase=warmup`，正式 scenario 带 `phase=measurement`；setup 失败会阻止 scenario 启动，且预热样本不匹配正式阈值 selector。
- `handleSummary()` 使用固定容器内输出路径并只保存 k6 metrics，不保存请求/响应正文或环境变量。CI 上传步骤使用 `if: always()`、7 天 retention、run id 唯一名称，未扩大 workflow 权限。
- `scripts/perf.sh` 在 k6 非零时优先返回所捕获的 k6 退出码；k6 为零而 summary 缺失或畸形时返回 70。

## 阻断项

1. **默认 summary 输出目录与真实 k6 非 root 用户不兼容，mock 没有覆盖宿主写权限。** `PERF_RESULTS_DIR` 未设置时用 `mktemp -d` 创建的目录通常为 `0700 root`。虽然脚本预创建了 `0666` 文件，但容器内非 root k6 用户仍不能穿越 `/results` 目录来打开它；当前 mock `docker` 直接以宿主用户写文件，因此测试会错误通过。环境中的 Docker daemon 无权限，无法做真实容器复现，但权限模型已直接由实际调用参数证明。必须给固定挂载目录最小必要的 traverse/write 权限，或只 bind-mount 预创建的可写文件，并增加以不同 UID 写入的测试；不能改成 root 容器。

2. **失败路径缺少测量后的资源快照。** `compose-smoke.sh` 在 `scripts/perf.sh` 非零后立即 `fail`，`Post-measurement bounded container resources` 仅位于成功分支之后。因此最需要诊断的阈值/transport/check/summary 失败只保留测量前快照，违反成功和失败均保留有界资源证据的计划。

3. **结构化 summary 的 fail-closed 校验和固定摘要字段不足。** 当前只校验正式请求 count、总体失败率、总体 p95 和总体 checks rate；没有校验或输出迭代数、后端/前端 tagged 指标、duration 的 avg/med/p90/max，也没有可审计的固定 k6 版本字段。一个只有当前四个 fixture 指标、完全没有两个端点证据和完整 duration 分布的 JSON 会被接受为有效，与计划要求不符。

4. **自动化防回归覆盖未达到计划列出的失败语义。** `scripts/test-perf.sh` 仅覆盖有效、缺失、畸形和退出码 99；未覆盖空文件、正式样本为零、缺端点指标、缺 duration 字段、check/transport/p95/error-rate 阈值失败、预热三类失败不启动 measurement、信号路径、不可写挂载目录、任意工作树覆盖防护、annotation 脱敏，以及失败路径仍采集 post snapshot。静态字符串断言不能证明这些行为。尤其 fixture 本身缺少计划要求的字段却被当作 valid，直接暴露了 fail-open schema。

5. **k6 原始退出语义没有覆盖 summary 处理期间的信号。** 脚本只在 `docker run` 正常返回后捕获 `$?`；没有 INT/TERM trap 来在收到信号时尽力保留/检查 summary 并返回对应 130/143。现有测试也未验证该计划项。

## 独立执行证据

- `bash scripts/test-perf.sh`：PASS（但存在上述覆盖缺口）。
- `bash -n scripts/perf.sh scripts/compose-smoke.sh scripts/test-perf.sh`：PASS。
- 使用仓库现有 Node YAML parser 解析 `.github/workflows/ci.yml`：PASS。
- `git diff --check`：PASS。
- 直接检查实际 k6 options/call：10 VU、30 秒、双端点、总体 `<1%`/`<2000ms` 均未放宽；正式 measurement 只有一个 scenario。
- 测试/生产脚本行数为 54/55，定向断言命令约 13 个；数量比例不低，但关键失败分支覆盖不足，因此比例本身不能替代行为证据。
- 完整 `scripts/check.sh`：实施端报告 PASS；该项目门禁不覆盖上述真实容器权限和诊断失败路径。
- 本机 Docker CLI 存在，但 daemon socket 返回 permission denied，故未声称运行过完整 Compose/k6；此环境限制不改变由文件权限与控制流直接证明的 blocker。

## 复审要求

实施端修复全部阻断项后，同一审核端至少重跑定向测试、Shell/YAML/diff 门禁，并审查新增 fixture 是否真实覆盖：不同 UID 可写、成功与失败 post snapshot、完整 summary schema/端点字段、零样本、各种阈值/check/transport 失败、信号退出码与脱敏。环境可用时再跑一次固定 `grafana/k6:0.57.0` 或完整 Compose smoke；远端准确提交的单次 CI 仍是最终真实容器证据。

## 第一次复审：FAIL（仅剩测试证据阻断）

实施端修正后复审结果：

- B1 已修：输出目录显式调整为 `0755`，固定 summary 文件预建为 `0666`；权限模型检查得到 `dir_before=700 dir_after=755 file=666`，非 root 容器具有目录 traverse 与文件写权限。
- B2 已修：`performance_status` 在 `set +e` 区间捕获，post-measurement `docker stats` 在状态判断前执行，且 best-effort stats 不覆盖性能状态。
- B3 已修：summary 现在强制校验 schema/k6 版本、正式 requests/iterations、总体 failed/checks、duration 的 avg/med/p90/p95/max，以及 backend/frontend 各自 failed/check/p95；零正式样本 fail-closed。
- B5 已修：docker 作为后台子进程运行，INT/TERM 转发后仍进入 summary 后处理，并分别保留 130/143；实际定向 TERM 测试确认退出 143 且 summary 非空。
- 负载与阈值复核不变：唯一 measurement 仍为 10 VU/30 秒、每迭代双端点，总体及端点 `rate<0.01`、`p(95)<2000`，checks 为 100%，不存在自动第二次 measurement。

仍有一个阻断：**B4 自动化防回归覆盖仍不满足计划明确的“至少覆盖”清单。** 当前新增的行为测试覆盖 missing、malformed、zero request、原始 99、130/143 与运行中 TERM，但以下只靠静态字符串或完全没有测试：

1. 后端预热失败、前端状态错误、前端首页标识错误分别阻止 measurement；
2. measurement 的 HTTP/transport error、内容 check 失败、p95 超标、错误率超标分别导致非零，且不启动第二次 measurement；
3. 完整 schema 中任一端点或 avg/med/p90/max/iterations 缺失均 fail-closed（当前只有代码审读，没有负 fixture）；
4. 不同 UID 对实际挂载文件的可写性，以及 `PERF_RESULTS_DIR` 不覆盖工作树任意文件；
5. performance 失败时仍执行 post snapshot、post stats 失败不覆盖原状态、annotation/控制台不泄漏环境变量或响应正文。

`setup()`、阈值和 jq 条件本身经直接审读方向正确，但计划要求自动化证明这些失败语义，不能用字符串存在性替代。实施端可用 mock/fixture 或低成本 k6 fixture 分层验证，不要求增加第二次正式 30 秒测量。

本次复审重新执行：

- `bash scripts/test-perf.sh`：PASS。
- `bash -n scripts/perf.sh scripts/compose-smoke.sh scripts/test-perf.sh`：PASS。
- Node YAML parser 解析 workflow：PASS。
- `git diff --check`：PASS。
- Docker daemon 仍不可访问，未运行真实 k6/Compose；不得以本地结果替代准确远端 CI。

## 第二次复审：FAIL（失败类别测试为无效覆盖）

第二次修正已补充以下有效证据：缺 endpoint、iterations、distribution 以及 zero/missing/malformed summary 均返回 70；工作树根路径返回 64；目录/文件权限及 other-write 位有断言；TERM 测试保留 summary；secret 值不出现在输出；Compose 的 perf 调用、post snapshot、失败判断顺序有静态断言。上述项目通过复审。

但声称新增的八类 warmup/measurement 失败测试没有实际驱动这些类别，因而 B4 仍未关闭：

- 循环设置了 `MOCK_FAILURE="$failure"`，但整个 mock docker、`scripts/perf.sh` 和 `tests/performance/smoke.js` 都没有读取 `MOCK_FAILURE`；`rg` 的唯一命中就是调用这一行。
- 八次执行实际完全相同：mock 先写一份成功 summary，再无条件以外部指定的 `MOCK_K6_STATUS=91` 退出。它只重复证明“任意 k6 非零码被保留”和“wrapper 只调用一次 docker”，不能分别证明 warmup backend、warmup frontend status/content、measurement transport/HTTP/check/p95/error-rate 的语义。
- 尤其预热失败“不启动 measurement”无法由 docker 调用次数证明：setup 与 measurement 都发生在同一个 k6/docker 进程内，单次 docker 调用在预热正确或错误时都成立。
- p95/error-rate/check 的实际 threshold 由代码审读确认配置正确，但测试没有生成对应 metric/response 失败或执行 k6 来观察阈值非零；未达到计划明确要求的自动化防回归证据。

因此第二次复审结论仍为 **FAIL**。修复方式可以是：用可执行的 k6 fixture + 本地受控 HTTP server 逐类触发响应/内容/时延/transport，或将请求与 phase 判定抽成可由 JS 测试 runner 执行的纯逻辑并另行用真实 k6 低成本 fixture 验证 threshold wiring。不得仅给通用非零 mock 加未使用的类别名称。

本次重新执行结果：

- `bash scripts/test-perf.sh`：PASS，但包含上述伪覆盖。
- `bash -n`：PASS。
- Workflow YAML 解析：PASS。
- `git diff --check`：PASS。
- `rg MOCK_FAILURE`：仅命中测试调用行，直接证明类别变量没有控制任何行为。

## 第三次复审：PASS

实施端删除无效 `MOCK_FAILURE` 循环，新增 `scripts/perf-fixture-server.mjs` 和 `scripts/test-perf-k6.sh`。审核端确认测试直接以固定官方 k6 v0.57.0 执行生产 `tests/performance/smoke.js`，fixture 场景确实改变 HTTP 状态、首页内容、连接或响应时间，不再以外部固定退出码冒充类别行为。

### 真实 k6 失败矩阵证据

审核端独立下载并执行官方 k6 `v0.57.0`（commit `50afd82c18`，linux/arm64），七个场景全部 PASS：

- `warmup-backend-status`、`warmup-frontend-status`、`warmup-frontend-content`：k6 非零；backend/frontend 计数均不超过一次；summary 的 `http_reqs{phase:measurement}` 为零，证明 setup 失败没有启动正式 scenario。
- `measurement-transport`、`measurement-http`、`measurement-content`、`measurement-p95`：五轮预热后才注入故障；k6 非零；backend/frontend 均超过五次且 summary 含正式 measurement 样本，分别证明 transport/HTTP 错误率、内容 check 与 p95 门禁生效。
- 每个场景只启动一次 k6 进程并直接运行生产脚本；没有第二次 measurement、重试取优或替代阈值。

### 最终核验

- 正式 scenario 仍精确为 10 VU、30 秒，每迭代访问 backend/frontend；总体 `rate<0.01`、`p(95)<2000`，端点阈值相同，checks `rate==1`，均未放宽。
- setup 固定最多五次 request pair，任一状态或内容 check 失败即 `fail`；warmup 标签不匹配 measurement selector，既不稀释指标也不能被忽略后继续执行。
- `scripts/perf.sh` 对路径、目录/文件权限、完整 schema、零样本、缺字段、原始非零码和 INT/TERM 均 fail-closed；固定摘要不输出请求正文、环境或 secret。
- `compose-smoke.sh` 在性能调用前后都采集固定三容器的有界 stats；post stats 位于性能状态判断前且 best-effort 采集不覆盖原状态。
- Workflow artifact 使用 `if: always()`、run-id 唯一名称、7 天 retention，未增加权限；summary 缺失仅警告不会将先前失败改成成功。
- 输出目录限制为仓库固定 `perf-results` 或 `/tmp` 子目录，固定文件名，拒绝以工作树根等路径覆盖任意项目文件。

重新执行结果：

- `bash scripts/test-perf.sh`：PASS。
- `K6_BIN=<official-v0.57.0> bash scripts/test-perf-k6.sh`：7/7 PASS。
- `bash -n scripts/perf.sh scripts/compose-smoke.sh scripts/test-perf.sh scripts/test-perf-k6.sh`：PASS。
- `node --check scripts/perf-fixture-server.mjs`：PASS。
- Workflow YAML 解析：PASS。
- `git diff --check`：PASS。
- 实施端完整 `scripts/check.sh`：PASS；审核端本轮没有重复耗时的全量产品测试，以上定向测试覆盖本次实际 diff。

最终结论：**PASS**。Round 15 实现可提交推送；本结论只证明当前实现与本地门禁，不替代准确提交的远端七 job 单次 CI、summary artifact 和后续 Release 验证。
