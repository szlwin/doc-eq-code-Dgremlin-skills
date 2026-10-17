# RuleView / Rule / Action / Produce 实现规范

## RuleView / Rule

RuleView 是业务模型 + 有序 Rule。`ruleRef` 引用 RuleView `name`；`code` 用于快速查找/注册索引，不替代 name/Design ID。

DataSource：

```text
effectiveDataSource(rule) = action.dataSource
                          else rule.dataSource
                          else ruleView.dataSource
                          else business.dataSource
```

实现必须保留这个继承/覆盖顺序。一个 RuleView 可以默认 MySQL，某条 Rule 单独走 Redis、MongoDB、第三方系统或微服务。

Canonical Rule types：`check/checkPattern/checkData/checkDataPattern/insert/update/delete/get/query/dsl`。`dsl/cmd` 在 XML 兼容层映射为 `grammer/sql`，实现代码不得要求设计回退历史拼写。

## 原始 Rule 模型的执行契约

`CompiledRule` 必须完整保留 `property/pattern/process/cmd/error/customer`，执行时更换有效 dataSource 也不能丢字段。`customer` 与原 `CustomerInfo` 一致，只允许平铺字符串值。RuleView 按声明顺序执行，任一规则非 TRUE 即停止；错误诊断保留 Rule ID 及配置的 code/message/level。

- `check/checkPattern/dsl` 使用 Runtime 的模型校验和表达式能力；不得按 Rule ID 在 adapter 中复制业务表达式。当前支持范围见框架 `docs/rule-compatibility.md` 和设计 skill 的 `yaml-spec/rule.md`。不支持的历史表达式应在编译阶段失败，并报告 gap。
- `checkData/checkDataPattern` 是外部读操作，必须实现 `DataOperationAdapter.read`，不能委托旧的写操作 lambda 或只检查当前内存模型。读调用不得修改 View 或 produce；查询别名只在每行校验作用域内有效，不能覆盖原模型同名字段。没有结果时 checkData 按 null 校验，checkDataPattern 为 FALSE。
- 通用接入优先 `new RuleDataAdapter(ruleDataAccess)`。`RuleDataAccess.Request` 携带完整 Rule、操作、View、不可变模型/参数与当前 transaction；驱动按 operation/property/cmd 访问外部资源，返回 rows/affectedRows，不用订单专属 Rule ID 分支。
- `get` 返回单对象（多行报错，空结果 FALSE），`query` 写集合；insert 可返回生成键，集合条数必须匹配。写入和 Produce 由桥接层走 Invocation 追踪。Information 中 get/query 仅判定存在，不写模型；需要对查询值判断时使用 checkDataPattern。Information 禁止 insert/update/delete。
- 驱动承担 Data/View 映射、逻辑命令翻译、参数绑定、集合逐项校验与物理资源接入。连接/提交不能隐藏在桥接层；必须加入 WorkflowTransaction 的既有事务。通用接口和内存契约测试不等于已支持所有 JDBC/Redis/远程后端。

验证须覆盖原始 XML 导入后的实际执行、字段保留、读源失败/空结果/多行、别名隔离、复合校验、DSL 临时变量隔离、未知语法编译失败和先写后校验失败回滚。Binding 区分 Runtime 能力、真实驱动和测试证据。

## RuleView Action

有 `systemRef + ruleRef`：执行指定 System 的 RuleView。如果 Runtime 已支持，Binding 标记 runtimeManaged，不创建空壳 Service。

## Custom Action

无 `ruleRef`：必须有真实 implementation + registration；默认注册 key 为 Action `name`，不允许回退同名 RuleView。Design ID 用于追踪，不作为注册 key。

接入步骤：

1. 在 canonical `business.directories[].actions`（或 Back 的 actions）声明稳定 `id` 和 `name`；顺序就是执行顺序。设计修改经正式 YAML 脚本完成，再重新生成 XML 和 Java 投影。
2. 在拥有该外部能力的 `<system>.action` 包实现 `CustomAction`。`name()` 返回注册名；`execute(ActionExecutionContext)` 读取 `context.model()`/`context.payload()`，真实执行或按项目副作用契约安排执行。失败抛异常，不吞错或返回假成功。
3. Spring 工程把实现声明为 Bean，统一装配 `CustomActionRegistry`。本仓库生成的 `config.CustomActionConfiguration` 收集全部 `CustomAction` Bean，调用 `CustomActionRegistry.from(providers)` 创建并冻结注册表；Workflow 只注入注册表，不逐个依赖具体 Action。非生成工程的 `@Component` 本身不会自动注册，必须显式装配注册表。
4. 把同一个注册表交给 `DirectoryWorkflowRunner` 的构造和执行入口。初始化时 `compileReady` 校验设计所需 Action；缺失注册、重复 key 或定义校验失败应明确报错，不运行占位实现。
5. Directory 执行到节点时由 Runtime 调用，不让 Controller 直接调用 Action。若声明 `produces`，在 `execute` 返回前调用 `context.produce(ref, value)`；`ref` 与设计一致，后置 Information 由 Runtime 验证。异步发送结果不能伪装成同步 Produce。

非 Spring 环境也可显式组装实例：

```java
CustomActionRegistry registry = CustomActionRegistry.from(List.of(smsNotifyAction));
DirectoryWorkflowRunner runner = new DirectoryWorkflowRunner(project, registry);
```

生成的 Business Flow 中 `ActionNode.customType()` 显示实际注册 key，`isCustom()` 区分自定义 Action 与 RuleView Action；投影只用于导航，不能替代注册和执行。Binding 应分别定位 Action 实现、注册入口和真实验证用例。

### 短信等外部副作用

采用独立的 provider 接口（如 `SmsSender`）接入短信平台，让 Action 保留流程适配职责。Demo 的 `SmsNotifyAction` 在 Spring 事务 `afterCommit` 中调用 `SmsSender.sendPaymentError(orderId)`；提交前只注册回调，回滚时不发送。`LoggingSmsSender` 仅打印日志，不表示已接入真实短信服务。

事务归属与投递保证必须说明：`afterCommit` 发送失败时数据库已提交，不能宣称短信失败会回滚订单；它也不保证进程崩溃后的可靠投递。需要重试、幂等或可靠投递时使用项目已约定的 provider/Outbox 机制，不擅自新增通知表代替 Action。

至少验证注册解析、缺失/重复注册，以及该实现选择的副作用时序。短信例子应验证提交前未发送、提交后发送一次、外层事务回滚不发送；真实 provider 未接入时在 Binding 保留这一限制。

## 一个业务流程绑定一个监听器

使用 `com.dec.lite.action.ActionListener` 为一个 Business 统一接收其全部 Action 的前后事件。`businessId()` 返回 canonical Business Design ID（例如 `BUS-ORDER-PAYMENT`）；同一流程最多注册一个监听器，重复绑定报错。未配置监听器的流程保持原有行为，不强制生成空实现。

- `before(ActionListenerContext)` 在每个 Action 的依赖检查通过后、主体执行前调用；`after(ActionListenerContext, ActionResult)` 仅在主体及 Produce 成功后调用。它们是逐 Action 事件，不是流程开始/结束事件；后置仍在事务内、Directory Change 和最终提交之前。失败/跳过不调用 after。
- 在同一个监听器内按 `context.action().id()` 分派不同 Action 的处理逻辑，不为每个 Action 建监听类。`context.businessId()` 表示所属流程，`action()` 还包含 name、ownerDirectory；普通、case、Back Action 由解析器保留 Business 归属，不能通过同名 Action 或 Directory 猜测流程。
- 使用 `context.view(OrderInfo.class)` 获取当前 Action 的类型化 View；`advance` 自动复用传入的 ModelCodec，并从 Directory.modelRef 验证流程 View 绑定。每次读取返回新快照，setter 不自动写回；读到的是当前 Action 模型而非初始 API 参数。低层 `run` 显式使用 `RunRequest.withView(new ActionViewBinding(viewRef, ViewClass.class, decoder))`；缺失绑定、Java 类型不匹配或设计 modelRef 冲突必须失败。
- `context.read(path)` 可继续读取字段，`payload()` 读取参数；修改使用 `context.write(path, value)`，保留权限检查、MutationSet 和缓存失效。Runtime 在后置写入后再次校验 Produce。不能复制设计状态机或改变 Action 顺序。
- `config.ActionListenerConfiguration` 由生成器提供，收集全部监听器 Bean。Workflow 注入注册表并传给 `new DirectoryWorkflowRunner(project, customActions, listeners)`。Runner 初始化校验 Business ID；未知 ID 或重复绑定失败。仅有 `@Component` 而未接入 Runner 不会执行。
- 非 Spring 用 `ActionListenerRegistry.from(List.of(listener))`。直接使用 ActionRuntime 时校验完整 Business ID 集合，ActionDefinition 必须携带所属 Business ID；旧的无 Business 归属的独立 Action 不触发流程监听。该绑定能力不扩展 DirectoryGraph 的多流程路由能力；现有 Runner 仍要求一个可执行根图。
- 监听器异常或超时使当前 Action/流程失败，按事务模式回滚或标记 rollback-only；不会撤销已发生的外部不可逆副作用。监听器实例可能被多请求共享，不在字段保存请求数据；客户端 I/O 使用 `context.deadline()` 自行限制超时。
- after 不代表事务提交或短信送达。短信继续使用既有提交回调，不在监听器重复发送。

Demo 使用 `business.OrderPaymentListener`，接收订单支付流程所有事件，在内部单独处理 `ACT-SMS-NOTIFY`。测试验证一流程一监听器、不同流程隔离、成功/失败分支及 Back Action、工作模型、异常回滚、Produce 复核、超时和并发。Binding 将监听实现和测试挂到 Business，并为有关 Action 保留可追踪链接，重新采集证据。

## Produce

顺序：Action 成功 → ref 数据产生 → 写入模型/上下文 → 识别 informationRef → 重算 composite Information → 后续 Change/Directory。

## Change

Change 是 Information materialization，不是任意 `setStatus()`。Runtime 无法物化时返回 capability/design gap，不能把状态变更隐藏到 Service。

Action 与 Back Action 的 `dataSource` 显式覆盖当前 Action 的外部 Rule；未声明时依次沿用 Rule、RuleView、Business。Custom Action 可读取 `ActionExecutionContext.dataSource()` 与 `dataSourceAdapter()`。监听器通过 ActionDefinition 查看配置，不应绕开 Runtime 另造全局路由。

## 原 DEC Runtime 的兼容修复边界

评估原 `doc-eq-code-Dgremlin` 时，必须把类型注册、解析、规则执行、数据源转换和真实数据库验证分开。旧 XML/YAML 的 dsl 规范化为 grammer 并进入原引擎；dec-java-lite 使用独立受限解释器，不能因原引擎支持某语法就标记 lite 已支持。

原 MySQL 的逻辑 SQL insert 通过 SqlInsertCommand 共用解析，依次映射 View→Data→物理表列；单行 VALUES 支持参数路径、标量字面量和 NULL/DEFAULT，参数必须绑定。集合按项执行并回填每项主键。未知字段、缺参、目标不一致或不支持的方言明确失败，不回退原生 SQL。

无 SQL query 的 Rule 通过直接 one-to-many 关联的父键/子外键建立条件并回填集合；缺失父键在 I/O 前失败。SingleQueryConvert 提供 BaseData 条件查询，非空字段 AND 等值、NullData 为 IS NULL，空条件拒绝。生成器/adapter 不得复制业务过滤条件或擅自全表查询。验证须包括实际 SQL 参数、生成键/显式键、空结果、不同父对象隔离，以及后续项失败时 ModelContainer 回滚先前写入。

checkData 的每个空读必须保留为 null 后校验；集合不能过滤缺失项目。checkDataPattern 的查询仍使用完整模型参数，property 元数据不自动限定 SQL 范围；对每行使用独立别名上下文，成功、失败和异常均不得污染原模型。回归至少覆盖这些分支及 get/query 回填、update/delete 父类执行路径。真实 MySQL 未执行时必须在结论中区分内存连接契约验证与集成验证。
