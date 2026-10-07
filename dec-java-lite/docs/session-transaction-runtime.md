# P7 Session、事务与运行时边界

## 入口与归属

`ExecutionSession` 是一次执行的显式拥有者，持有独立的模型副本、Information Engine 缓存、Action Runtime、当前 Directory、输入、截止时间、错误、Trace 和 `TransactionCoordinator`。构造时从原模型复制状态；成功提交后仅发布到 Session 模型，调用者的原始 `ModelContext` 不会被就地修改。Session 不能跨线程共享，调用结束后必须 `close()`。

Directory 的前进和 Back 各开启一个 Scope；同一路径的 Action、Produce 消费、Change、分类和后置验证在同一 Scope 内执行。RuleView 通过 `ActionExecutionContext.transaction()` 接入该 Coordinator。独立 Action 通过 `ExecutionSession.execute()` 使用相同的提交与模型发布规则。Query 的 `SessionJdbcQueryExecutor` 在已开启的 Scope 内为候选查询、详情查询和数据命令复用同一路由的 JDBC 连接；`QueryRunner` 将失败登记到 Session，保留查询 Trace。

## 事务政策与失败

- `REQUIRED`：内层 Scope 加入外层；仅最外层提交。内层回滚把外层标记为 rollback-only。一个 Scope 只允许一条物理路由，路由标识包括 DataSource 和连接名。
- `NONE`：没有托管 JDBC 事务；试图登记托管事务资源会明确失败。`REQUIRES_NEW` 尚无隔离资源契约，因此不提供。
- 跨物理路由的原子提交被拒绝并记录 `REJECTED`；不宣称 XA。外部副作用必须显式登记补偿。普通回滚按反序尝试补偿，缺失或失败会使状态为 `FAILED`。提交失败时真实提交结果可能未知，记录 `COMMIT_FAILED`、`ROLLBACK_ATTEMPTED` 和 `EXTERNAL_IN_DOUBT`，不声称已经成功回滚。
- Action、Produce 消费、Directory 后置验证和分类失败时，Scope 回滚数据库资源并丢弃工作模型；Session 模型维持原值。失败回调在回滚后调用，其再次失败被另记为错误，不覆盖原错误。错误包含稳定 code、实体 Key、来源、cause 和含事务结果的 Trace 快照。
- Back 是一条新的业务执行路径，有自己的 Action、Change、验证和事务；它不是回滚已经提交的数据库事务。

## 旧路径盘点与处置

以下是上游 `doc-eq-code-Dgremlin` 的历史实现盘点，不构成 `dec-java-lite` 的依赖：

| 旧路径 | 现状与处置 |
| --- | --- |
| `dec-core-model` 的 `SimpleSession`、`AbstractSession`、`SessionExecuter` | 绑定旧 `BaseData`、`DataConnection` 与 SQL 执行。历史代码保留在上游；新运行时不调用，改用显式 `ExecutionSession` 与 Session JDBC 执行器。 |
| `SessionFactory` | 使用 `ThreadLocal<SimpleSession>`。新运行时不用隐式全局或线程局部 Session。 |
| `TransactionContainer`、`MultipleTranContainer`、`TranGroup` 与各 `TranLevelGroup` | 历史规则事务分组、连接列表和传播模式；不迁移其实现。新 Coordinator 仅支持有明确资源契约的 `REQUIRED` 和 `NONE`。 |
| `dec-expand-declaration` 的 Context、事务及 policy | 已确认整体退役，不建立 adapter，不复制实现，不形成第二套运行时。Produce 消费与回调是基于当前 `mix` 和统一 Action 契约设计的新 SPI。 |

与旧 `ActionTransaction` 对接只用于当前轻量运行时测试和迁移入口，不能与托管 JDBC 资源在同一 Scope 混用。后续正式适配器应直接使用 Session 持有的资源。

## 验证

`ExecutionSessionTest` 覆盖嵌套加入、连接复用、跨路由拒绝、补偿、提交结果不明、System 写权限和并发隔离。`DirectoryEngineTest` 覆盖整路径提交与失败回滚、Back、回调与 Produce 消费失败。`QueryCompilerTest` 覆盖 Session 查询错误与 Trace。设置 `DEC_MYSQL_JDBC_URL`、`DEC_MYSQL_USER`、`DEC_MYSQL_PASSWORD` 后，`MySqlQueryIntegrationTest` 验证真实 MySQL 连接复用及 order/payment 两次写入的整体回滚。
