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

## 5. 手动注入 DEC 数据源适配器与执行选择

使用 `DataSourceAdapter(name, DataOperationAdapter)` 注册具名实现，生成的 `config.DataSourceAdapterConfiguration` 收集这些 Spring Bean，构建不可变 `DataSourceAdapterRegistry`。非 Spring 使用 `DataSourceAdapterRegistry.from(List.of(...))`。实现名属于工程配置，不写入 canonical YAML。

每次调用用 `registry.select(Map.of("data1", "tenant-a", "mongo1", "error-store", ...))` 生成独立的 `DataSourceSelection`。左侧是设计中的逻辑 DataSource，右侧是注册的实现名；同一实现可服务多个逻辑源。传给 `DirectoryWorkflowRunner.selectDataSources(registry, selection)` 可在业务读写之前完成校验；返回值作为 DataOperationAdapter 交给 recognizeDirectory / advance / assertDirectoryMatches。也可使用带 registry+selection 的 advance 重载，或低层 RunRequest.withDataSources。

有效逻辑名按 Action → Rule → RuleView → Business 解析；传给 adapter 的 CompiledRule 携带本次有效逻辑名，共享编译结果不变。一次选择覆盖流程所有外部操作（checkData/checkDataPattern/insert/update/delete/get/query），包括未走到的分支与 Back、Information 识别，以及显式 Business/Action 注册。未知实现、重复实现名、遗漏路由、未被设计引用的逻辑名均报错。缺少有效逻辑名的外部 Rule 不得静默选源。纯模型 check/DSL 不执行外部 adapter。

Registry 不存储“当前数据源”，Selection 复制选择表，不使用全局或 ThreadLocal 切换。适配器实例若被复用应线程安全，不保存当前请求模型。Demo `OrderDataSourceConfiguration` 手动注册 local，Workflow 每个方法新增 selection 重载，原 API 使用显式 DEFAULT_DATA_SOURCES；这不会改动公开 HTTP 契约。

这里切换的是 DEC 外部 Rule 的实现，不自动切换应用 DAO/JDBC 数据库，也不自动提供多资源原子提交。事务资源接入仍由原 adapter/WorkflowTransaction 契约负责；不能把未参与事务的远程操作宣称可回滚。Demo 替代实现测试复用已加入 Spring 事务的本地 DAO，验证写后失败回滚。

测试覆盖按逻辑源分派、Rule override、未知/遗漏路由在执行前失败、并发选择隔离、默认选择不被前次请求污染及 adapter 失败回滚。Binding 为真实适配/装配符号与测试补充追踪并重新生成证据。

### 流程默认与 Action 数据源

`business.dataSource` 声明流程默认逻辑数据源；`actions[].dataSource` 显式覆盖当前 Action，包括 `subDirectories[].back.actions[]`。两者引用 Config 的 DataSource name，不填写 Java 类名、Bean 名或适配器实现名称。旧设计可以省略；新流程有外部操作时应明确默认值。

有效路由按 **Action.dataSource → Rule.dataSource → RuleView.dataSource → Business.dataSource** 解析。Action 显式覆盖该 Action 调用的所有外部 Rule；未配置 Action 时保留 Rule/RuleView 多数据源语义。纯模型 check/dsl 不触发外部访问。Information 识别没有 Action 覆盖，使用 Rule → RuleView → Business；一次 Action 的选择不能泄漏给下一次 Action 或其他请求。

Custom Action 没有 Rule 层级，使用 Action → Business。逻辑数据源与注册实现分离：设计确定逻辑名称，执行参数 `Map<逻辑名, 实现名>` 选择注册的 DEC 适配器；不改变 JDBC/DAO 路由。无有效数据源或缺少实现选择的外部操作必须失败；不得静默使用首个适配器。一个 Runner 装载一个 Business。

Java 投影生成 `OrderPaymentFlow.DATA_SOURCE`，ActionNode 保留 `dataSource/businessDataSource`，`resolveDataSource(ruleSource)` 用于导航查看，实际执行由 Runtime 负责。`ActionDefinition` 同样保留声明及流程默认值；不要修改共享编译 Rule。框架在请求绑定阶段验证所需注册，之后才开启事务。

```java
// 注册实现（Spring 中可直接提供 DataSourceAdapter Bean）
var registry = DataSourceAdapterRegistry.from(List.of(
    new DataSourceAdapter("local", localAdapter),
    new DataSourceAdapter("remote", remoteAdapter)));
var selection = runner.selectDataSources(registry, Map.of(
    "data1", "local", "paymentService", "remote", "mongo1", "local"));
// 将 selection 传入本次 advance/run；Action.dataSource 决定逻辑路由。
```

Custom Action 中 `context.dataSource()` 取得 Action/Business 逻辑名称，`context.dataSourceAdapter()` 取得本次选择的适配器。通过适配器执行操作时继续传递当前模型、MutationSet 和 transaction；配置 dataSource 不会自动执行 Custom Action 业务逻辑。监听器可通过 `context.action().dataSource()` / `businessDataSource()` 查看声明；RuleView 可能包含多个 Rule 数据源，不能把单一默认值当作所有 Rule 的实际路由。短信提交后投递仍由独立发送实现负责。

### dec-java-lite 的 Rule 驱动桥接

原框架 `DataConnection.execute(ExecuteParam)` 的接入位置对应 `RuleDataAccess.execute(Request)`。用 `new DataSourceAdapter("implementation-name", new RuleDataAdapter(driver))` 注册。驱动读取 `request.rule().cmd()/customer()`、`request.target()` 和 `request.transaction()`；返回 `Result.read(rows)`、`Result.written(count)`，或含生成键的 Result。输入/返回行是不可变快照，模型合并由桥接层负责。

`checkData` 发送 get，`checkDataPattern` 发送 query，二者都必须执行真实读取。针对集合的 checkData，驱动应逐项匹配全部目标并保留缺失语义，不能只返回命中的行造成误通过。命令翻译与安全参数绑定属于驱动；不要直接拼接 `#property` 或宣称任意 DEC SQL 都可直接执行。桥接层不创建数据库连接、不自动提交、不模拟远程资源可回滚。
