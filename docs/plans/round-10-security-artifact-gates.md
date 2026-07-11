# Round 10：安全与候选产物正向证据门禁计划

## 目标与边界

本轮只关闭 Round 09 独立审核确认的两个实现级证据缺口：

1. `security` job 必须对后端实际打包产物及其内含依赖执行不忽略 unfixed 项的 High/Critical 漏洞扫描，任何 High/Critical 均阻断；前端现有 `npm audit` 门禁继续保留。
2. `release-candidate` 必须在上传前验证候选目录的完整性、提交绑定、校验和、SBOM 结构和归档可读性，并在 CI 日志/step summary 输出不含文件内容或秘密的正向摘要，使没有 Actions artifact 下载权限的 reviewer 也能审计同一 run。

本轮不改变业务功能、API 契约或版本号，不创建或推送 tag，不创建 GitHub Release，不部署，不写入凭据，也不修改 `TODO.md`。当前不预设任何漏洞例外；若门禁发现 High/Critical，默认修复依赖并重新扫描，不能临时恢复 `--ignore-unfixed` 或降低等级。

## 当前事实与失败原因

- 当前 `.github/workflows/ci.yml` 的 `security` job 只 checkout 后运行固定 Trivy `0.58.2` 的仓库级 `fs` 扫描，并带有 `--ignore-unfixed`；它既会静默跳过 unfixed High/Critical，也没有先构建 `backend/target`，因此不足以直接证明实际发布 JAR 的依赖安全。
- `backend` job 的 `mvn verify` 覆盖测试、格式和 SpotBugs，不是依赖漏洞扫描；不能替代上述产物扫描。
- 当前 `release-candidate` 先对候选目录执行 `sha256sum *`，随后才写入 `GIT_SHA`，所以 `GIT_SHA` 不在校验清单中；上传前也没有验证必需文件、SHA、SBOM 或归档。
- GitHub Actions artifact 下载在无认证环境返回 401。workflow 声明和 upload job 成功只能证明步骤执行，不能直接证明 ZIP 内部文件满足约束。
- `actions/upload-artifact@v4` 默认不上传隐藏文件。本轮候选文件均应使用明确的非隐藏名称；校验不得依赖隐藏文件或未明确上传的目录项。

## 设计约束与验收映射

| 缺口 | 实现约束 | 可接受的正向证据 |
| --- | --- | --- |
| 后端实际依赖未扫描 | `security` job 设置 Java 21，并以锁定的 `pom.xml` 执行 `mvn --batch-mode -f backend/pom.xml -DskipTests package`；随后用固定 Trivy 版本直接扫描生成的发布 JAR或包含该 JAR 的 `backend/target`，且扫描器为 `vuln`、等级为 `HIGH,CRITICAL`、`--exit-code 1`，不得出现 `--ignore-unfixed` | job 日志显示预期 JAR 非空、Trivy 版本/漏洞数据库元数据、扫描目标与零 High/Critical 摘要；人为注入或检测到任一 High/Critical 时命令非零退出 |
| 源码 secret 扫描与构建缓存误扫混在一起 | 将 secret 扫描与 JAR 漏洞扫描拆成明确步骤；secret 扫描仓库受版本控制的源码范围并排除 `.git`、`backend/target`、`frontend/node_modules`、`frontend/dist` 等生成物/缓存，避免把依赖缓存或构建产物当作源码秘密；漏洞门禁不以仓库级缓存扫描替代 JAR 扫描 | workflow 中两类扫描目标、scanner 与排除项可区分；二者任一失败均阻断 `security` |
| Trivy 数据库可追溯性 | 固定 Trivy CLI 镜像版本；每次 job 使用干净 runner 下载当次数据库，不引入可能长期陈旧的跨 run DB cache；在日志记录 Trivy 版本和数据库更新时间/元数据。若下载发生短暂网络故障，只允许有限、显式重试，不能用 `--skip-db-update` 绕过 | 同一 run 可看到 CLI 版本、DB 元数据与最终 scan conclusion；不存在静默使用旧库的配置 |
| 必需产物可能缺失/为空 | 上传前按固定白名单逐一执行普通文件且非空校验：后端 JAR、前端 tar、`openapi.yaml`、`CHANGELOG.md`、前后端 CycloneDX SBOM、`GIT_SHA`、`SHA256SUMS` | 校验步骤日志列出全部必需文件的名称与字节数，仅输出元数据、不输出内容 |
| Git SHA 未直接绑定 | 在生成校验和之前写入 `release/GIT_SHA`；去除换行后必须严格等于 `git rev-parse HEAD`，且为当前 checkout 的完整 SHA | 日志输出脱敏且非秘密的 expected/actual commit SHA，并明确 `PASS`；不相等则上传前失败 |
| 校验清单可能自包含或不完整 | 以固定排序的明确 payload 白名单生成 `SHA256SUMS`，必须包含 `GIT_SHA` 和所有其他必需 payload，但不得包含 `SHA256SUMS` 自身；验证清单行数、文件名集合无多无少，再在 `release/` 内执行 `sha256sum -c SHA256SUMS` | 日志显示 manifest 条目数、每个文件 `OK` 和总结果；检查明确证明不存在 `SHA256SUMS` 自身条目 |
| SBOM 只因文件存在而被接受 | 使用 runner 可用的 JSON 解析工具（优先 `jq -e`）验证两个 SBOM 都是有效 JSON，顶层 `bomFormat == "CycloneDX"`、具有非空 `specVersion`，并具有符合预期类型的 `components`；不得只检查扩展名 | 摘要仅输出 SBOM 文件名、格式、specVersion 与 component 数量，不输出依赖明细 |
| JAR/tar 可能损坏 | 使用 Java 21 自带 `jar tf` 读取 JAR并确认至少一个条目；使用 `tar -tzf` 读取前端包并确认至少一个条目。可额外确认 JAR 中存在 Spring Boot 启动结构、前端包存在 `index.html`，但不能用过度脆弱的完整列表快照 | 日志输出每个归档的条目数和关键结构检查结果；损坏或空归档立即失败 |
| reviewer 无 artifact 下载权限 | 所有验证在 `actions/upload-artifact` 之前完成；同时写入普通 job 日志与 `$GITHUB_STEP_SUMMARY`。摘要只包含 commit SHA、固定文件名、大小、校验摘要、SBOM 格式/版本/组件数、归档条目数和 PASS/FAIL，不包含文件内容、环境变量或 Token | reviewer 可通过公开/可访问的准确 run job 页面核对验证步骤成功及摘要；artifact digest 仍由 upload action 提供，内部正确性由同一 job 的前置自校验证明 |

## 实施步骤

### 1. 强化 `security` job

1. 为 job 增加 Temurin Java 21 setup（Maven cache 只用于依赖获取，不作为 Trivy 扫描目标）。
2. 在扫描前运行后端 `package`，并明确断言预期的 `backend/target/travel-assistant-api-0.1.0.jar` 是非空普通文件。构建必须来自当前 checkout，不使用其他 job 下载的未知产物。
3. 保持 Trivy 镜像版本固定，先输出版本与漏洞数据库元数据；不配置跨 run DB cache，避免缓存让“当前扫描”难以解释。若实施时 Trivy 的镜像命令无法稳定输出 DB 元数据，应保留版本和扫描日志中的 DB 信息，而不是因此跳过数据库更新。
4. 用 `vuln` scanner 扫描实际 JAR（或最小化的只含实际发布 JAR 的目标目录），设置 `HIGH,CRITICAL --exit-code 1`，删除 `--ignore-unfixed`。不要扫描整个 `backend/target` 中与交付无关的报告或测试缓存，避免误报来源不清。
5. 独立运行 repository secret scan，目标与排除项明确，且生成目录和依赖缓存不参与。扫描范围的调整不能漏掉受版本控制的业务代码、配置、脚本与文档。
6. 保留前端 job 的 `npm audit --audit-level=high`；核对其默认覆盖生产和开发依赖的实际语义，文档不得把它夸大为运行时镜像扫描。

若新增门禁发现 High/Critical：先记录 CVE、受影响组件、可利用性与升级路径，优先升级/替换依赖。本轮不建立空白 allowlist，也不添加通配忽略。只有未来独立风险评审明确批准的例外，才允许在单独工程安全文档中按 CVE/组件精确列出理由、补偿措施、负责人和到期日，并由 CI 对过期项失败；AI 治理日志只记录决策索引，不承载安全例外正文。

### 2. 增加上传前候选产物自校验

1. 保持现有构建来源，创建干净 `release/` 后复制/生成固定白名单 payload。
2. 先写 `GIT_SHA`，再对所有 payload 逐个验证存在且非空。
3. 对 JAR 和 tar 执行可读性及最小结构校验；对两个 SBOM 执行 JSON/CycloneDX 结构校验。
4. 以显式数组和稳定排序生成 `SHA256SUMS`。禁止使用可能把刚生成的清单再次匹配进去的无约束 glob；清单生成后验证文件名集合和条目数，并确认没有自引用。
5. 在 `release/` 工作目录执行 `sha256sum -c SHA256SUMS`。只有全部 `OK` 后才生成日志/step summary 的安全摘要。
6. 将验证步骤放在 upload action 之前，且 upload `path` 保持为 `release`。如日后确需隐藏文件，必须显式评估 `include-hidden-files`；本轮禁止通过隐藏文件承载任何必需证据。
7. 可以把非业务专用逻辑提取为 `scripts/verify-release-candidate.sh` 以便本地和 CI 共用；若提取，脚本必须启用严格 shell 模式、接受明确的候选目录和期望 SHA、拒绝额外/缺失文件，并由 shell syntax/行为测试覆盖。不得为了复用引入新的运行时依赖。

### 3. 同步证据文档

在新门禁真实通过前，不恢复 Round 09 中撤回的发布清单第二、第五项。实施完成后：

1. 更新 `docs/reports/mvp-verification.md`，准确描述不忽略 unfixed 的 JAR 漏洞扫描、独立 secret scan、候选自校验内容及其边界。
2. 追加 `docs/ai-governance/AI_CHANGE_LOG.md`，记录本轮为何改变门禁、独立审核结果、准确提交与 CI run；如交接材料仍描述旧安全门，更新 `PROJECT_HANDOFF.md`。
3. 只有在本地验证、独立 reviewer `PASS` 且本轮准确提交 CI 全绿后，才可将 `docs/release-checklist.md` 的第二、第五项重新勾选并引用该 run。第一、三、四项需重新确认未被本轮破坏。
4. `TODO.md` 的“发布 MVP”和用户授权项始终保持 `[ ]`；本轮不能宣称正式发布完成。

## 本地验证

实施终端至少执行并保存实际结果：

1. `git diff --check`，以及新增/修改 shell 脚本的 `bash -n`。
2. 在本地生成候选目录，运行与 CI 相同的自校验脚本，确认正常候选通过。
3. 对候选目录分别做负向篡改测试：删除必需文件、置空文件、篡改 `GIT_SHA`、篡改 payload、向清单加入自身或额外文件、破坏一个 SBOM JSON/CycloneDX 标识、损坏 JAR、损坏 tar；每种情况必须非零退出。测试使用临时副本，不修改真实构建产物。
4. 在具备 Docker/网络能力时，按 workflow 等价命令构建后端 JAR并执行固定 Trivy 扫描，确认命令中不存在 `--ignore-unfixed`，目标确为本次构建 JAR，High/Critical 为阻断等级。若本地环境无法下载 Trivy DB，不得用跳过更新代替，必须由 CI 提供最终证据。
5. 运行 `./scripts/check.sh`，确认 OpenAPI、前后端格式、静态检查、测试、覆盖率与构建未回归。
6. 检查 workflow YAML 可解析，必需 job 依赖仍为 `release-candidate.needs` 的六个质量 job，且自校验严格位于 upload 前。
7. 检查本轮 diff 不含 API key、Token、密码、漏洞数据库缓存、构建产物、artifact 内容或其他敏感/大型生成文件。

## 独立审核、修正与复审

由未参与实现的 reviewer 创建 `docs/reviews/round-10-security-artifact-gates-review.md`，只审核和测试，不编辑实现文件。审核至少覆盖：

1. 对照本计划、Round 09 `FAIL`、实际 diff 和 workflow，确认两项缺口被直接关闭，而不是通过改写声明、降级门槛或新增宽泛忽略项规避。
2. 独立确认 security job 先用 Java 21/Maven 构建当前 checkout 的实际 JAR，再由固定 Trivy 对该 JAR执行无 `--ignore-unfixed` 的 High/Critical 阻断扫描；secret scan 目标和排除项不会把受版本控制源码排除。
3. 独立运行候选校验的正向与关键负向用例，核对必需文件白名单、`GIT_SHA`、无自包含且完整的 `SHA256SUMS`、`sha256sum -c`、两个 CycloneDX JSON 和两个归档。
4. 确认 CI 摘要只输出非敏感元数据，足以让无 artifact 下载权限者核验，但没有打印 SBOM 内容、归档内容、环境变量或秘密。
5. 运行比例相称的工程门，给出明确 `PASS` 或 `FAIL` 和阻断项。若为 `FAIL`，由实施终端修复，再由同一 reviewer 复审；审核者不得直接修改实现。

## 提交、CI 与清单勾选条件

1. 只有独立审核 `PASS` 后，实施终端才可提交并推送普通代码；不得创建 tag、Release 或部署。
2. 等待该准确提交的 GitHub Actions。七个 job 必须全部成功，尤其 `security` 日志必须证明当前 JAR 的无忽略 High/Critical 扫描通过，`release-candidate` 日志必须证明上传前完整自校验通过。
3. reviewer 独立核对准确 commit SHA、run ID、七 job conclusions、security 数据库/目标摘要、候选校验摘要及 upload artifact digest。CI 失败时回到修正—同一 reviewer 复审循环，不能使用历史绿色 run 替代。
4. 只有上述证据成立，才可在后续证据提交中勾选发布清单第二项“依赖审计无未处置 High/Critical”和第五项“候选产物绑定同一 Git SHA”，并记录准确 SHA/run。该证据提交本身也需轻量独立复核和 CI 全绿。
5. `TODO.md` 的“发布 MVP”只有在用户明确授权后实际创建并核验 annotated `v0.1.0` tag、GitHub Release 和附件，且发布后清单全部满足，才可勾选；本轮不得操作或勾选。

## 完成定义

本轮只有同时满足以下条件才完成：

1. security job 对当前 checkout 构建的实际后端 JAR执行固定版本 Trivy 扫描，不忽略 unfixed，任一 High/Critical 阻断；源码 secret scan 与前端 audit 均保留并通过。
2. release-candidate 在上传前证明固定必需文件均非空、`GIT_SHA` 等于当前完整 SHA、`SHA256SUMS` 完整且不自含并通过复验、双 SBOM 是有效 CycloneDX JSON、JAR/tar 可读。
3. 同一 run 的日志和 step summary 给出足够且脱敏的正向证据，reviewer 无需下载 artifact 即可审核；upload action 仍产生可追溯 digest。
4. 本地门和负向校验通过，独立审核最终 `PASS`，准确提交的 GitHub Actions 七 job 全绿，证据提交也经过复核和 CI。
5. 发布清单第二、第五项只有在证据成立后才勾选；没有秘密、tag、GitHub Release 或部署，`TODO.md` 的“发布 MVP”保持未完成并等待用户明确授权。
