# 2026-10-25 检查报告的修复与验证

对应[原检查报告](health-check-2026-10-25.md)的 F1–F6。修复基于 `75412f9` 的工作区，验证于 2026-10-25 至 2026-10-26 完成；未提交、推送或发布。原报告保留为修复前的检查快照。

## 六项处理结果

| 编号 | 处理 | 验证依据 |
| --- | --- | --- |
| F1 | Java 生成器先在临时目录完成渲染，再检查完整目标集合。旧 manifest 外的同名文件、目录和父路径冲突全部列出并拒绝；仅替换/清理受管文件。提交失败时尝试恢复原内容与清单，并移除本次新增目录。 | `GenerationOptionsTest` 复现 Demo API 分组与手写 Service/Controller 撞名，检查所有文件内容不变；`GeneratedProjectWriterTest` 覆盖晚期模板失败、部分写入后的 I/O 失败和恢复。原有重生成及产物一致性测试通过。 |
| F2 | 查询下推从 `ModelExpressionCompiler` 的已解析 AST 提取必要等值条件，直接复用字面量值。AND 可以提取安全子条件；OR、NOT、量词和不支持的比较保留 Runtime 判断，不从其内部误提取条件。 | `InformationPredicateCompilerTest` 覆盖连续空格、制表符、换行、引号内逻辑关键字、反斜杠及转义引号、null 与组合条件；真实 MySQL 查询验证匹配记录不会在候选阶段丢失。 |
| F3 | `RecognitionResult`、`ActionResult`、`SessionError` 和工作流异常保留结构化类型及 cause。Demo Adapter 区分 `CONFLICT` 与 `INFRASTRUCTURE`，HTTP 分别返回 409 和 503；未分类的 Action 异常返回 500。 | `OrderApiTest` 注入支付 DAO 连接失败及部分写入后失败，检查 503、原始 cause、订单/明细状态及支付记录回滚。既有业务规则拒绝、重复提交仍为 409；监听器与替代 Adapter 的异常为 500，回滚且不发送短信。 |
| F4 | 审阅 `rule-action-implementation.md` 后，使用正式脚本重建 `dec-implementation/FILE_MANIFEST.sha256`，只改变该条摘要。 | 两个 Skill 清单均通过，分别为 100 和 33 个文件；implementation 的 32 项 Python 测试通过。 |
| F5 | CI 最终 Binding 检查改为 `--require-ready`；开发与验证手册同步使用就绪门禁。 | 当前 118 个 Design ID 的完整性、符号、测试证据及 VERIFIED 就绪检查全部通过；现有 Python 回归覆盖完整 Binding 中 PLANNED 条目被 ready 门禁拒绝。 |
| F6 | P8 发布文档将九个模块明确限定为 2026-10-07 的历史快照；移除过期的“当前 111 个 ID”等重复数量。当前模块链接到 Reactor POM，当前 Demo 状态链接到 `demo/INTEGRATION.md`。 | 历史验收表保留，当前源码为十个模块；canonical 校验为 10 份 YAML、118 个 Design ID、6 个 API、19 个 Information。 |

另外，发现旧监听器回滚测试使用了不存在的 `remark`、`id` 列，导致测试提前因 SQL 错误退出。本次改为真实的 `o_remark`、`o_id`，断言更新了一行，并检查最终 cause 确实是主动注入的监听器异常，避免测试因错误原因通过。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| 框架 `install -Prelease-artifacts,mysql-it` | 207 项通过，0 失败、0 错误、0 跳过；包括真实 MySQL 及生成器产物一致性回归，sources/Javadoc 产物构建完成 |
| Demo 经 Binding 包装器运行 `clean test` | 44 项通过，0 失败、0 错误、0 跳过 |
| Binding `--require-ready` | 118/118；`coverageComplete`、`releaseReady`、`symbolsVerified`、`evidenceVerified` 全部为 true |
| implementation Python 测试 | 32 项通过，包括清单漂移与 PLANNED/VERIFIED 证据门禁反例 |
| 两个 Skill 文件清单 | 均通过 |
| canonical 校验、XML 内存转换检查 | 通过；`--check` 只证明转换成功，不代表现有 XML 的漂移比对 |
| `git diff --check` | 通过 |

本次使用独立 MySQL 8.4 容器和专用测试库，未连接项目默认业务数据库。测试结束后停止并删除该临时容器。未改动 canonical 设计、派生 XML/HTML 或生成 Java 模板输出；原报告中的 XML/HTML 全量无漂移结果仍是原检查的证据，本次未重复宣称为新执行的比对。

新增支付故障测试已挂到 `ACT-START-PAY` 的 Binding。当前证据由 `run_binding_verification.py` 生成于应用 `target/dec-binding-evidence.json`，绑定本次源码、设计、Binding、生成配置及 JUnit 报告；输入变化后需要重新生成，不能视为永久认证。

## 边界与后续工作

生成器的恢复针对可捕获的提交异常，不保证断电/进程强杀后的自动恢复，也不支持多个生成进程同时写同一目录。文件系统本身持续不可写时，恢复失败会作为 suppressed exception 保留。

本次没有执行远程 GitHub CI，也没有开展 Agent 实际使用 Skill 的模型评测或生成工程 Spring 启动/接线专项测试。这两类建设，以及更广泛的跨实现语义对等用例，保留为后续工作；本次已补齐与字符串下推缺陷直接相关的 Runtime/SQL/MySQL 对等回归。

## 后续复核 R1：Windows manifest 路径比较

[复核报告](health-check-recheck-2026-10-26.md)指出，Windows 的 `Path.toString()` 使用反斜杠，与 manifest 的正斜杠表示不一致。已将 `ownedFiles` 中规范化后的相对路径转换为 `/` 后再比较，保留对 manifest 原文中反斜杠、重复分隔符、`.`、越界和符号链接的拒绝。

新增回归验证嵌套路径首次生成、再次生成、manifest 格式以及手写文件保留，并验证非规范 manifest 仍被拒绝。本次在 macOS / Java 21 上运行生成器全部 30 项测试，0 失败、0 错误、0 跳过，`git diff --check` 通过。

CI 新增 `generator-windows` 作业，使用 `windows-latest`、Java 21 和 Maven Windows wrapper 运行 `GeneratedProjectWriterTest`、`GenerationOptionsTest`，覆盖原生 Windows 路径下的重复生成和所有权保护。该远程作业尚未实际执行，因此这里不声称 Windows 实机验证通过。本次未重跑 MySQL/Demo 或重新生成 Binding 证据；上文全量结果属于 R1 修改前的验证快照，框架输入变更后应由后续完整 CI 重新生成证据。
