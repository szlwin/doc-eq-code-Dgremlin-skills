# 五项体检反馈的确认与收尾

记录日期：2026-10-20（Asia/Shanghai）。本记录接续 [上一轮体检修复](health-check-recovery-2026-10-19.md)。本记录为当日修复与验证快照，不代表当前工作区状态。

## 逐项结果

| 反馈 | 确认与处理 |
| --- | --- |
| 1. API 参数键序 | 纯规范性问题成立。通过正式 YAML I/O 将 Demo 全部 20 个 request.params 的 `id` 统一为首键，参数数组顺序、ID、约束和值均保持不变。 |
| 2. RuleView 数据源缺省 | 缺省原本有效；按本轮明确要求，`RV-SAVE-ORDER` 现在显式声明 `dataSource: data1`。XML 两份副本、HTML、Java 产物已重新生成，README 同步。渲染测试验证显式 RuleView 来源，并另外保留 Business 回落测试。 |
| 3. 并发下单返回 500 | 当前代码没有复现该问题。初次 find 只是快速失败检查，数据库主键负责并发裁决；订单写入异常经 Rule/Directory 失败链转为 409，用户初始化的并发异常也已显式映射 409。补充 HTTP 层确定性并发回归及代码注释，保留现有正确行为。 |
| 4. 同步投影直接解析 XML | 该输入是内部 YAML converter 生成的 XML，原先并非外部 XML 防护绕过；现已统一使用 `parse_safe_xml`，共享 DTD/ENTITY 拒绝和 8 MiB 限制。两 Skill helper 同步，str 保留已解码字符、bytes 按声明解码，补 UTF-16/ISO-8859-1 声明的 Unicode 字符串保真测试。 |
| 5. JDBC 物化重复 | 抽取包内 `JdbcRows.bind/read`。两个 executor 保留各自连接获取、资源释放、事务 enlist/commit/rollback 行为；新增真实 MySQL 测试验证映射一致及连接生命周期不同。 |

## 并发证据

`OrderApiTest.concurrentHttpSubmissionsReturnOneSuccessAndOneConflictWithoutPartialWrites` 使用 barrier，让两个 HTTP 请求均完成“订单不存在”的读取后再继续，避免仅测试顺序重复请求。

覆盖相同新用户、相同已有用户、不同新用户争抢同一订单 ID 三种场景。每个场景均断言 HTTP 状态集合为 `[200, 409]`，数据库只有一笔订单、一条明细和一个用户；不同用户场景同时验证失败请求的新用户写入被回滚。

实际订单写入由 `LocalOrderMapperAdapter` 的 JdbcTemplate 同步执行，MySQL 主键冲突不会推迟到事务提交。异常链为 LocalRuleAdapter → RuleViewInterpreter → ActionRuntime → DirectoryEngine/Runner → OrderWorkflow → Spring MVC。网络或真正的提交故障不应一概标成业务冲突，本轮未扩大 409 的范围。

## 验证

| 检查 | 结果 |
| --- | --- |
| dec-design Python | 90 通过，0 跳过 |
| dec-implementation Python | 30 通过，0 跳过 |
| Java reactor `clean install -Pmysql-it` | 197 通过，0 失败/错误/跳过 |
| Demo `clean test`（真实 MySQL） | 41 通过，0 失败/错误/跳过，含新增 HTTP 并发测试 |
| 合计 | 358 个测试通过 |
| canonical / XML / HTML | Demo 118 IDs、Skill examples 94 IDs 校验通过；两套 HTML 和三套 XML 重生成比对 CLEAN；Java 受管产物由 generator 快照测试检查 |
| manifest / diff | 100 文件 PASSED，`git diff --check` 通过 |
| Binding | 完整覆盖、符号、测试及当前证据核对通过；118/118，`evidenceVerified=True` |
| 独立复审 | 运行时与最终脚本/生成物复审 PASS；编码兼容问题在复审中发现并修正后再次通过 |

数据库验证使用单独的临时 MySQL 8.4 容器。测试完成后再次核对了摘要；这些证据只对应当时的输入，后续变更仍需重新验证。

Binding 保留 114 VERIFIED、4 PLANNED，`releaseReady=False` 仍是预期事实。证据路径为 `demo/order-payment-app/target/dec-binding-evidence.json`。
