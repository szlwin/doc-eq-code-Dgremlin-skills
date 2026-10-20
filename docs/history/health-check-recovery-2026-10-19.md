# 全面体检修复与中断恢复记录

本记录保存 2026-10-19 的修复与验证快照，不代表当前工作区状态。API 键序、RuleView 数据源及并发、XML、JDBC 的后续核对见 [五项反馈收尾记录](health-check-followup-2026-10-20.md)。

核对日期：2026-10-19（Asia/Shanghai）。恢复基线：`52ab57b` 加上此前中断留下的工作区改动。保留已有修改，核对后补齐遗漏、回归测试及生成物。

## 结论

原报告的 12 项 P2 已逐项核实：真实问题已修复，作用域差异已明确文档化，两项非缺陷已澄清。框架、Skill 脚本、设计及生成物分别经过独立审查，最终无阻断发现。最终运行 354 个测试，全部通过，无跳过；真实数据库验证使用独立的 MySQL 8.4 临时容器。

这不代表所有历史 P3 技术债都已消除，也不代表 Demo 已完成所有业务节点。Binding 仍保留原有 4 个 `PLANNED`。

## 原报告逐项核对

| 原报告 | 最终处置与证据 |
| --- | --- |
| 1. `recordPyaError` | 三套设计统一为 `recordPayError`，`PyaError` 统一为 `PayError`；手写适配、Binding、测试及 XML/HTML/Java 投影同步，稳定 Design ID 保留。 |
| 2. YAML 一致性 | Demo、mix、Skill examples 统一 `saveOrder`，同步引用、规范示例和测试。`rule-compatibility` 保留历史名称作为兼容输入。API `id` 首尾差异是映射键顺序，不是参数顺序，无需改变协议；Demo `RV-SAVE-ORDER` 省略 dataSource 是继承 Business 默认 data1 的有效设计，保留。 |
| 3. 数字词法异常 | 无效数字、整数越界与非有限数归类为 `ActionExecutionException`；另补模型路径下标越界检查。见 `RuleProgramTest`。 |
| 4. 关键字静默错读 | 裸关键字不能作为属性操作数；`item.and` 等限定字段保持可用。见 `RuleProgramTest`。 |
| 5. 热路径重复编译 | `CompiledRule` 保存不可变 Expression/DSL/Checks 编译结果，切换数据源复用程序；并发请求变量隔离。见 `RuleProgramTest`、`RuleChecksTest`。 |
| 6. 谓词作用域 | 明确保留既有语义：checkDataPattern 行覆盖模型、量词/DSL 集合项隔离；文档说明并回归验证，避免静默改变既有设计行为。 |
| 7. XML 防护 | XML 导入、同步、实现侧 read/check 共享编码感知的 DTD/ENTITY 拒绝与 8 MiB 限制；UTF-8/UTF-16、外部实体、注释/CDATA 均有测试。 |
| 8. relEnum 重复逻辑 | 统一使用 `dec_common.resolve_view_property_enums`，校验器与 HTML 渲染器共享解析结果。 |
| 9. 单体脚本 | validator 拆为 local/references/information/business 校验模块；renderer 拆为 catalog/common/details/graphs/pages，CSS/JS 独立为源资源，输出仍为自包含 HTML。 |
| 10. API 契约重复 | 两个 Skill 入口及 validation-rules 改为引用规范，保留可独立使用的实现消费契约。 |
| 11. evidence 保证范围 | 文档明确是当前输入与报告的防漂移核对，依赖可信测试命令和 CI，不证明报告来源或发布安全。 |
| 12. 事务与并发 | Spring 外部绑定追踪 suspend/resume/completion，拒绝跨物理事务使用；并发用户初始化冲突映射 409。H2 事务测试及 MySQL 新/已有用户并发提交测试通过。 |

## 本次收尾额外修复

- 恢复拆分时遗漏的 HTML CLI shebang，直接执行 `--help` 已验证。
- 补齐示例 `saveOrder` 清理和 HTML 锚点断言；mix XML 使用 `--emit-id-comments` 重生成，保留身份对等。
- JUnit 报告旧字节匹配可被 UTF-16 绕过，已复用 `read_safe_xml_bytes`；增加四种编码及大小限制回归测试。
- 新增 runner 越界测试，先复现默认 target 符号链接和固定 pending 文件符号链接可写工程外的问题，再修复：输出路径统一 resolve 后检查边界，pending 文件使用独占创建的随机临时文件。报告目录和文件越界拒绝也已覆盖。
- 示例构建说明从 `package` 改为 `install`，避免干净 Maven 仓库缺依赖；修正 System HTML 路径文档。
- 刷新 dec-design manifest，包含拆分模块、CSS/JS、安全 helper 和测试，共 100 个源文件。

## 验证结果

| 验证 | 最终结果 |
| --- | --- |
| dec-design Python 测试 | 88 通过，0 跳过；提供 Node 以执行 JS 检查 |
| dec-implementation Python 测试 | 30 通过，含 XML 与 runner 新增安全回归 |
| Java reactor `clean install -Pmysql-it` | 196 通过，0 失败/错误/跳过，含 3 个真实 MySQL 集成测试 |
| 订单 Demo `clean test` | 40 通过，0 失败/错误/跳过，含 29 个 Workflow 与 5 个 Query 测试 |
| 两个生成示例 `clean test` | BUILD SUCCESS；工程没有独立测试源码，此项仅证明生成代码可编译 |
| canonical 校验 | Demo 118 IDs、Skill examples 94 IDs、mix 93 IDs、expense-approval 13 IDs 通过；rule-compatibility 18 IDs 通过（独立规则 fixture 的 View 引用跳过有预期警告） |
| Binding 完整覆盖/测试声明/符号/本次证据 | 118/118，114 VERIFIED、4 PLANNED；`symbolsVerified=True`、`evidenceVerified=True` |
| XML 漂移 | Demo 导出、应用副本、mix、Skill examples、expense-approval 均 CLEAN |
| HTML 漂移与静态检查 | 两套站点共 76 页重生成一致、0 断链，76 个内嵌 JS 语法检查通过 |
| 浏览器抽查 | 流程图加载、缩放、点击 recordPayError 节点后的详情正常，正确显示 mongo1 与 PayError |
| manifest / 空白错误 | 100 文件 PASSED；`git diff --check` 通过 |

最终测试命令和重生成方法见 [开发与验证手册](../development-and-verification.md)。本次使用 JDK 21 和仓库 Maven Wrapper。Node 使用本机已有运行时，不因 PATH 缺失跳过测试。

Binding 证据位于 `demo/order-payment-app/target/dec-binding-evidence.json`，报告位于同目录下 `surefire-reports/`。额外输入包括 `dec-java-lite`、`skills/dec-implementation` 和 generation properties；后续修改这些输入需要重新执行包装器。证据与报告属于构建产物，不提交 Git。

## P3 与既有限制

已补：嵌套 every 与并发隔离、RuleChecks、ExpressionCompiler 的直接测试；RuleDataAdapter 空属性诊断；异常类名；manifest 与路径边界测试；显式 HTML 生成标记；生成依赖版本集中配置。默认数据库口令已移除，表名映射已有 YAML 指针说明，`grammer` 已标为历史兼容关键字。Git 当前未跟踪 `target/` 或 `.DS_Store`，忽略规则完整，无需删除用户本地文件。

保留的后续优化：QueryRunner 大 offset 仍需按候选扫描，复杂度与 offset/limit 有关且受候选上限约束；改为游标分页涉及公开查询契约，本次不改变。generator 的各 Builder 仍主要由生成快照与生成工程编译覆盖，并非每个 Builder 都新增独立单元测试。

Demo 的 `RV-ORDER-IS-WAIT-PAY`、`RULE-ORDER-IS-WAIT-PAY`、`INFO-ORDER-WAIT-PAY`、`INFO-PAYMENT-COMPLETED` 保持 PLANNED，`releaseReady=False` 是预期事实。真实支付/MongoDB/短信仍使用本地替身，本次 MySQL 通过不能代表这些外部系统已经集成。
