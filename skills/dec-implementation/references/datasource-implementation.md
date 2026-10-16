# DataSource 实现规范

DEC DataSource 是统一外部能力边界：RDBMS、Redis、MongoDB、MQ、文件、第三方系统、数据中间件、HTTP/gRPC 微服务都通过同一抽象接入。

## 1. RuleView / Rule 使用方式

RuleView `dataSource` 是默认值；Rule `dataSource` 是局部覆盖。实现必须把逻辑 DataSource name 解析到 Config 中声明的实例，不得根据名称猜类型。

示例：

```text
RuleView default = mysqlOrder
Rule A no override → mysqlOrder
Rule B dataSource = redisOrder → redisOrder
Rule C dataSource = paymentService → HTTP/gRPC adapter
```

## 2. 新 DataSource type

通常实现：

```text
DataSource
DataConnection
ConvertContainer
DataConvertContainer
ExecuteContainer
+ factories / registration
```

- `DataSource`：物理能力代理；
- `DataConnection`：连接/请求/事务能力；
- `ConvertContainer`：DEC command/mapping → 物理命令；
- `DataConvertContainer`：逻辑/物理类型转换；
- `ExecuteContainer`：执行请求。

## 3. Binding / Testing

新增 type 时 Binding 至少覆盖 adapter、factory/registration 与契约/集成测试。第三方系统或微服务要测试超时、错误映射和返回数据转换，但这些技术策略不能偷偷改变业务 Rule。

## 3.1 Store 生命周期与默认装配

面向 DAO/Mapper 的 Store 属于 DataSource 适配边界。默认由 Factory 注入已有 DAO、Mapper、连接或数据库资源并创建普通 Store；项目也可使用 Spring @Bean/@Component 或已有 DI 容器，不能仅凭注解判为不符合设计。Store 提供类型化 DAO 访问、读写和行锁。实际装配入口选择应用、事务或请求作用域，并明确哪些状态由数据库/事务持久化、哪些状态只属于一次请求。默认简化仅转发 DAO 的 UnitOfWork/begin()；项目兼容需求可保留包装，但不能伪造提交语义。

Store 不得使用 `synchronized`、`ReentrantLock` 或方法级锁隐藏并发策略。需要并发控制时，使用事务协调器、数据库约束、DAO/Repository 的冲突检查或经过说明的并发集合；实际装配入口、Store 和真实提交边界都必须有可验证的测试 seam。除非设计明确要求共享作用域，否则不要通过隐式静态变量或未声明单例共享状态。

当实现提供 JDBC 配置时，必须把连接池、驱动、事务管理器和 schema/migration 纳入可运行工程；DAO/Mapper 的写入必须加入同一个事务边界，失败时回滚。不得保留仅为测试服务的内存 fallback 冒充生产 DataSource；需要离线测试时应使用显式 test profile 或测试容器，并保持相同的 DAO 契约。

Directory 当前状态从持久化 View 通过 Runtime 的 Information 识别，或使用有效的 Runtime Session。Store 不得通过状态枚举 switch 或 payment/result 条件组合重新实现 Information。不要为了保存 Directory 名称增加一张与业务状态重复的 shadow table；若状态无法从现有模型推导，应先报告 `DESIGN_GAP` 或补充 canonical 设计。

Custom Action 的外部副作用由用户提供的 Action/adapter 负责，不要为短信、消息等副作用额外创建一张“通知记录表”来代替 Action。需要可靠投递时使用 Outbox、消息系统或项目已有的副作用契约，并由设计或项目架构明确声明。

## 3.2 校验与事务提交的职责

Runner 必须显式选择事务模式，不传空 `ActionTransaction`、不以 `null` 静默关闭事务。Store 是否复用、是否每次创建，与数据库事务生命周期无关。

- 外部托管：Spring `@Transactional` 方法使用 `SpringWorkflowTransactions.externallyManaged()`；`TransactionTemplate` 回调使用 `externallyManaged(() -> status)`。Runtime 检查 active/rollback-only，Spring 桥接还拒绝只读与已完成事务。失败标记 owner rollback-only；Runtime 返回仅表示参与成功，真实提交仍由外层负责。
- Runtime 自管：使用 `WorkflowTransaction.runtimeManaged(route, resourceFactory)`，factory 创建独立物理事务并提供 `TransactionResource`；Rule adapter 和完成回调通过同一 `transaction.resource(route)` 读写。Spring 工程可用 `SpringWorkflowTransactions.runtimeManaged(route, transactionManager)`，禁止已有外层 Spring 事务。所有 DAO 必须使用该 manager 控制的数据源；不能把多个独立数据源当作原子事务。

两种模式都保持 `Actions → 最终写回 → 持久化回读校验 → 完成事务`。Runner 最外层 scope 在完成回调之后才完成，内部 Directory scope 只参与。Spring 桥接在 `dec-lite-spring`，核心 Directory Runtime 不依赖 Spring。

持久化一致性校验由 Runtime 的 `assertDirectoryMatches(model, expectedDirectory, ruleAdapter)` 复用 canonical Information 完成；Store 只提供数据读写和锁定。不把校验命名为 `commit()`，仅包装 DAO 的 UnitOfWork 可按默认约定简化；兼容包装不得使用 `completed` 标记假装事务已提交。拥有事务或暂存变更的工作单元仍可保留生命周期管理。

`CompletionHandler.complete(completed, execution, transaction)` 在提交前执行：

```java
(completed, execution, transaction) -> {
    assembler.persistFinal(store, completed);
    var persisted = assembler.read(store, orderId);
    runtime.assertDirectoryMatches(bridge.toModel(persisted), execution.currentDirectory(), rules);
    return persisted;
}
```

回调内 `execution.success()` 仅代表流程执行成功，不能提前发布已提交事件。外部托管 `run` 的结果为 `EXTERNAL_PENDING`；Runtime 自管只有物理提交成功后才返回 `COMMITTED`。`advance` 同样遵循此时序。迁移旧 Runner 需替换事务参数并增加回调第三个参数；`run` 的流程失败现抛 `DirectoryWorkflowException`，不再返回失败 RunResult。

验证至少覆盖两种模式的最终写回失败、一致性校验失败和提交失败；检查上层吞掉异常后外部事务仍 rollback-only。提交抛错可能发生在数据库已提交之后，框架只能报告失败、结果不确定并尝试回滚清理，不能承诺数据已撤销或盲目重试。短信等外部副作用仍按独立契约处理；afterCommit 不代表可靠投递。

## 4. 结构化数据边界

如果 DataSource 返回 Object/List/JSON，而 canonical grammar 尚未批准对应结构，不在实现层擅自加 YAML 字段或长期塞 JSON 字符串；返回 `DESIGN_GAP`。
