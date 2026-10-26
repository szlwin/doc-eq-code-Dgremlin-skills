# 质量增强记录（2026-10-26）

本次按五项建议补充评测、语义对等回归、生成器契约、生成工程接线与生产验收清单。保留已有健康检查修复，不使用 common-develop。以下验证在本机 macOS / Java 21 执行；远程 CI 与 Windows 尚未执行。

## 1. Agent 级 Skill 评测

- [dec-design](../../../skills/dec-design/evals/README.md)：中文费用需求、增量修改、XML 歧义回流、独立安装，共 4 个用例。
- [dec-implementation](../../../skills/dec-implementation/evals/README.md)：DESIGN_GAP、Runtime/驱动能力缺口、PLANNED/VERIFIED 证据边界，共 3 个用例。
- 评分器检查真实文件、正式脚本调用及关联结果；再次执行设计/Binding 校验和 XML 重生成比较。评分器自身另有 8 项单测，不能当作 Agent 评测结果。
- `evals/` 按 skill-up 约定不安装到 Agent，故不加入可安装 Skill 的源码 manifest；评分器测试也放在 `evals/`，避免独立安装包依赖被排除的评测文件。CI 单独运行它们。后续核对补充了独立的 `evals/EVAL_MANIFEST.sha256` 和 CI 校验，保持安装包与评测源码各自完整。

实际安装并使用 skill-up **0.14.0**，引擎为已有登录状态的 Codex CLI **0.163.0-alpha.10.2**，观察到模型为 **gpt-6-astra**，没有设置模型覆盖。两个配置均通过 `skill-up validate`。

| 用例 | 模型运行与评分结果 |
|---|---|
| chinese-design | 直接 PASS |
| incremental | 直接 PASS |
| standalone | 直接 PASS |
| xml-ambiguity | 首次 CLI ERROR；重跑直接 PASS |
| design-gap | 直接 PASS |
| evidence-boundary | 首次 CLI ERROR；重跑直接 PASS |
| runtime-gap | 两次真实模型产物已取得，但评分器先因过严的范围约束、后因解析属性名 `id` 的错误误判；修正评分器后，重评第二次原始轨迹与产物 PASS。最新模型重跑为用量限制 ERROR |

评分器允许补充其他已知节点的 BLOCKED/PLANNED Binding；仍要求目标节点存在、全部状态未就绪、没有虚构节点或测试报告。修复递归读取 Design ID 时把 `columns.id` 当成标识的错误，并补了回归。重评通过使用保存的 Codex command_execution 事件与退出码重建对应工具轨迹，没有伪造命令或模型回答。

因此本次结论是 **6 个用例直接通过，1 个既有模型产物重评通过**，不能写成“7 个用例全新运行一次全部通过”。最近一次重跑的原始错误为 `You’ve hit your usage limit. Try again later.`。所有失败/错误与修正说明保留在[可移植结果摘要](quality-agent-evals-2026-10-26.json)；本机完整报告位于 `/tmp/dec-agent-evals/`，重评结果为该目录下 `rescore-runtime-gap/grading.json`。

没有进行 without_skill 对照或稳定性重复抽样，不能据此量化 AI 收益或证明复杂 Java 实现任务的稳定完成率。本机执行继承宿主 CLI 配置，独立安装验证不等于完整隔离全局元数据。

## 2. 跨实现语义对等

[语义矩阵](../../../dec-java-lite/tools/semantic-cases.json)包含 11 个变体，每个有正例和反例，覆盖连续空格、关键字字符串、整数/负数/小数、null 与字符串 null、枚举值类型、every/any/空集合、OR 和部分 AND 下推。

`SemanticVariantsParityTest` 通过正式 Python YAML I/O、validator、converter 生成变体，再比较 Java YAML/XML 解析摘要、Information Runtime 结果和 SQL 下推覆盖。真实 MySQL 回归补充数值、null 和关系集合的正反例；字符串 SQL 漏查修复继续覆盖。

`ExpressionCompilerTest` 实际编译 Java 表达式与 Runtime 对照，发现并复现：API 数值表达式原先用 `Objects.equals`，使 Long(1) 与整数字面量 1、BigDecimal 的不同 scale 等情况与 Runtime 不一致。修复为共享 `DecValueEquality` 数值比较，数字字面量用 BigDecimal 保留精度，并通过正式生成器重建订单示例 DTO。测试限定在 API/Information 语言的共享子集，没有宣称两种语法全集等价。

## 3. 生成器所有权

新增 [所有权契约](../../../dec-java-lite/docs/generator-ownership.md)及 `generate --dry-run`：列出 CREATE/UPDATE/DELETE/UNCHANGED/PRESERVE/CONFLICT；不修改目标目录；冲突返回非零退出码。预览与提交共用检查，正式生成仍重新检查。

原有冲突拒绝、暂存渲染、可捕获失败恢复和 Windows 路径规范化保留。契约明确受管文件内的手工修改可被覆盖，也明确没有断电/进程崩溃或并发写入的原子性保证。API 分组迁移需要调整手写接口实现、清理旧 Bean、clean 构建、更新 Binding，不能只检查生成命令成功。

## 4. 生成工程启动和接线

[验证脚本](../../../dec-java-lite/tools/verify_generated_projects.py)在临时目录依次生成：原始费用工程、添加测试 API 的默认分组工程、迁移至 Approvals 分组的工程。显式提供测试 Mapper/CustomAction 后启动 Spring，通过生成 Controller/Service、Runtime 和真实 MySQL 执行审批成功与失败回滚；检查旧类和旧 Bean 不存在，并核对可执行 JAR 内 XML 与资源清单。

本次使用新建的独立 MySQL 8.4 容器，未使用既有检查容器。共 **7 项测试通过**，报告位于 `dec-java-lite/target/generated-project-smoke/`；CI 已加入该脚本和报告归档。此处 MySQL Mapper 是明确的测试适配器，不能据此声称生成器自动提供业务实现或生产适配器。

## 5. 生产验收边界

新增[生产验收清单](../production-acceptance.md)，覆盖 paymentAttemptId/callbackId、幂等与迟到事件、可靠投递、真实适配器契约、数据库迁移、并发负载、审计及发布一致性。各项列明决策与证据要求，均作为未来验收门槛，不把 Demo 已知限制包装为已实现能力。

## 最终验证

| 检查 | 本次结果 |
|---|---|
| Java reactor（含真实 MySQL、release artifacts） | 213 项通过，0 失败/错误/跳过 |
| 临时生成工程 | 7 项通过，Spring 接线、MySQL 提交/回滚、分组迁移及 JAR 资源一致 |
| 订单 Demo | 44 项通过；重新生成当前输入对应的 Binding 证据 |
| Binding 就绪门禁 | 118/118，coverageComplete/releaseReady/symbolsVerified/evidenceVerified 均为 true |
| Python Skill 脚本 | dec-design 90 项、dec-implementation 32 项；交互测试使用本机 Node 补齐 |
| 评分器单测 | 两个 Skill 各 4 项通过 |
| Skill manifest | dec-design 100 文件、dec-implementation 33 文件，通过 |
| Agent 模型评测 | 按上表区分直接 PASS、产物重评与 ERROR；不计算虚假的全量通过率 |

Windows 回归已在此前修复中加入 CI，本次没有 Windows 实机执行结果。未提交或推送代码。

## 核验反馈收尾

对“评测尚未执行”的说法复核后，确认上述 11 次尝试均有原始报告，仓库摘要保存的 SHA-256 全部与本机原件一致；runtime-gap 的重评记录也单独保留。问题在于导航不明显，而非缺少实际执行。中英文文档导航及历史目录已增加报告入口。本次没有重新运行模型，也不改变先前的通过/错误状态。

`evals/` 排除在安装包 manifest 之外是独立安装兼容性要求，不能简单加入 SOURCE_ROOTS。新增的独立评测清单及 CI 检查保护用例、fixtures 和评分器源码；新增/修改/删除检查、输出排除及剔除 evals 后的安装包校验均有回归。生产验收清单已补齐英文正文、中英文导航和语言互链。

本轮收尾验证：独立评测 manifest 分别覆盖 18/15 个文件；两套安装包 manifest 继续通过。评测目录共 14 项单测通过（包含新增完整性和独立安装回归），两套 skill-up 配置校验通过；中英文验收表均为 9 项，新增/修改 Markdown 的 132 个本地链接无失效。评测依赖输入更新后重新执行 Demo 的 44 项测试并刷新证据，`--require-ready` 的 118/118 及四个状态均通过；临时测试容器已清理。本轮没有重新执行 Java reactor 或模型评测，先前结果仍作为历史快照保留。
