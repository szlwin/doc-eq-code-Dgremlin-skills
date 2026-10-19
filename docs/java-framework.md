# Java 框架详解

[返回文档总览](README.md) · [模块地图](architecture.md#3-java-模块地图)

`dec-java-lite` 使用 Java 21，生成 Spring Boot 3.2.4/MyBatis 工程。它提供设计前端、共享编译链、业务 Runtime 和生成器；实际支付、短信、数据库映射由应用适配器接入。

## 1. 解析、编译与快照

`DecYamlParser` 和 `DecXmlParser` 将输入转换为 `DecProject`。两种前端共享下游校验和编译，避免 YAML 与 XML 各有一套执行含义。XML 的 `dec-id` 注释有助于恢复 YAML ID；无注释时可能生成对应身份，不应假设天然与现有 Binding 一致。

解析器拒绝危险或不支持的输入：例如 XML DTD/外部实体、未知语义结构、旧 `directory-config`；YAML 重复键或自定义 Java tag 也不能被当作合法设计。单份设计输入有限制，当前为 8 MB，classpath 清单另有限制。详见 [输入契约](../dec-java-lite/docs/canonical-input.md)。

编译器将引用、表达式、图和规则转换为可复用结构。`SemanticDigest` 用于标识执行语义；Binding 的设计摘要则覆盖其定义的设计文件结构，两者用途和算法不同，不应互相替代。

`RuntimeCatalog` 在候选设计编译和就绪检查通过后才替换快照；失败保留旧版本，已有会话继续使用自己的版本。Demo 直接在启动构建 Runner，并未自动启用这套热更新流程。

## 2. Information 执行

核心组件是 `InformationParser`、`InformationEngine`、`InformationEngineContext`、`RuleViewRegistry` 与 `ModelContext`。

- 编译：解析 `ruleRef/ruleData/expression`，检查引用和依赖环，形成依赖图。
- 识别：返回 TRUE/FALSE/UNRESOLVED/ERROR，保留解释与证据。
- 物化：执行允许的 `changeData`，通过写权限检查并重新识别。
- 失效：模型变更产生 MutationSet，失效受影响的事实及其依赖者。

缓存按模型实例和版本隔离。当前 RuleView 未提供完整静态读路径时采用保守失效，优先保证正确性。`RuleViewRegistry` 按系统、View、规则键解析，不应裸名称猜测其他 System 的规则。

模型表达式中的集合谓词使“所有订单明细均支付成功”可以由设计表达。金额比较使用精确数值语义，不能为了方便把大整数和金额一律转成 double。

## 3. RuleView、DSL 与外部操作

`RuleViewCompiler` 编译不可变规则，执行时通过绑定过程注入本次适配器、模型与作用域。Runner 复用编译结果，不在每个请求重新调用旧式 `register()` 编译路径。

`RuleViewInterpreter` 按顺序执行规则，遇到非 TRUE 结果停止。模型内的 check/checkPattern/dsl 由框架执行；checkData/checkDataPattern 和 CRUD/get/query 通过适配器访问外部数据。

DSL 不是直接执行 Java 或任意脚本。当前支持受约束的赋值、局部变量、算术、条件、集合写入和错误条件。例如 Demo 的回退规则：

```text
payInfo.resultCode : null;
```

写入经过 Invocation/ModelContext 的权限与变更追踪；`#` 局部变量不会成为持久模型字段。旧语言中的任意函数、`.sum`、`.compute` 等不能自动视为可用。完整支持范围见 [规则兼容性](../dec-java-lite/docs/rule-compatibility.md)。

`RuleDataAdapter` 可将通用规则操作桥接到应用实现的 `RuleDataAccess`。驱动负责参数绑定、物理访问和参与事务，框架处理读取结果的合并或集合回填。保留了 `cmd` 并不代表框架内置了所有 SQL/NoSQL 命令解释器。

## 4. DirectoryWorkflowRunner 与完成回调

Runner 复用图、规则和 Information 编译结果，统一完成阶段识别、依赖检查、Action 执行、case 分类、Change、Back 和结果验证。应用 Workflow 传入模型转换、payload、适配器、事务模式和完成回调。

应用完成回调承担最终写回、重新加载和持久化一致性检查。例如 Demo 先 `persistFinal`，再从 DAO 重建 View，最后 `assertDirectoryMatches`。它属于事务提交前的必要步骤，不能移到提交之后。

这使 Workflow 保持应用编排职责，不需要在 `receivePaymentResult` 内再次写一份 success/error 状态判断。流程分支来自编译后的 canonical Directory。

## 5. 两种明确的事务模式

| 模式 | 谁开启/提交物理事务 | 接入方式 |
| --- | --- | --- |
| 外部托管 | 应用框架，如 Spring `@Transactional` | `SpringWorkflowTransactions.externallyManaged()` |
| Runtime 自管 | Runner 的事务作用域与桥接实现 | `SpringWorkflowTransactions.runtimeManaged(route, transactionManager)` |

外部托管模式检查真实事务有效性，失败时标记 rollback-only。Runner 成功退出时的 `EXTERNAL_PENDING` 表示仍等待外层事务所有者提交，不能对外误报“已经提交”。

自管模式适合没有外层事务的明确入口，当前契约限制在支持的单路由/资源边界；不能以选择多个适配器代替分布式事务。提交异常还可能是结果未知，不能一律声称已回滚。

两种模式都要求动作、最终写回和校验先于物理提交。空的 ActionTransaction、Store 新建或无实际事务的 UnitOfWork 都不能满足契约。更多时序见 [Directory Runtime](../dec-java-lite/docs/directory-runtime.md) 和 [Session/事务](../dec-java-lite/docs/session-transaction-runtime.md)。

## 6. 自定义 Action 与流程监听器

自定义动作实现 `CustomAction.name()` 和 `execute(ActionExecutionContext)`，注册到 `CustomActionRegistry`。Spring 生成配置收集 Bean；非 Spring 接入需要自己组装注册表。名称重复、缺失实现都明确失败。

一个 Business 最多绑定一个 `ActionListener`，通过 `businessId()` 对应设计 ID。它接收该流程每个实际执行 Action 的 before/after，包括 case 和 Back；同一个监听类中按 Action ID 区分业务处理。

```java
// 监听方法体片段；OrderInfo 由该流程的 ModelCodec 绑定。
OrderInfo current = context.view(OrderInfo.class);
Object value = context.read("id");
// 需要改变模型时使用 context.write(path, value)，不要修改快照 setter 期待回写。
```

类型化 View 是当前工作模型的独立快照。监听器实例可跨请求共享，因此请求数据只放局部变量。监听器失败会走动作/事务失败路径；跳过或失败的动作不触发 after。after 表示动作成功，仍不等于事务提交或外部通知送达。

截止时间检查发生在执行检查点，不会强制终止阻塞线程。真正的外部请求还需配置客户端超时；已经发送的远程副作用不会因超时自动撤销。

## 7. 数据源声明与按次选择

有两层选择：设计决定逻辑来源，应用决定其本次实现。

```text
设计：Action → Rule → RuleView → Business，得到 data1 / paymentService / mongo1
接入：Map<逻辑名称, 已注册实现名称>，得到实际 DEC Adapter
执行：Adapter 使用自己的 JDBC、HTTP 或其他客户端，并遵守事务契约
```

Information 识别没有 Action 覆盖，使用 Rule→RuleView→Business。Custom Action 没有 Rule 层，使用 Action→Business；可从上下文获取逻辑源和本次选择的适配器。

手动注册形如 `new DataSourceAdapter("tenant-a", adapter)`，在本次请求选择 `Map.of("data1", "tenant-a", ...)`。缺失、未知或不完整路由在绑定时被拒绝。声明来源的规则元数据保持不可变，选择不能泄漏到下一请求。

这只路由 DEC 外部操作，不自动切换应用所有 DAO，也不会把远程服务加入 JDBC 原子事务。详见 [Action Runtime](../dec-java-lite/docs/action-runtime.md)。

## 8. 面向业务阶段的查询

`BusinessQueryRuntime` 对设计编译一次，每次执行绑定独立的求值器。`BusinessQueryNames` 将显式别名映射到现有设计 ID/名称，不维护另一套状态值。

| 调用 | 当前契约 |
| --- | --- |
| `find(View)` | 当前图中该 View 所属阶段的并集 |
| `eq(Directory)` | 精确阶段；结果目录为其声明 case 的并集 |
| `start(A)` | A 及从 A 正向可达的阶段 |
| `start(A).end(B)` | 包含端点，取 A 可达且能到达 B 的阶段；排除 Back |
| `end(B)` | B 及其正向祖先阶段 |
| `with(relation)` | 按声明的 View 关系装配，可包含嵌套路径 |

`eq` 与范围互斥；未知名称、不可达/逆向范围及歧义别名明确失败。普通阶段重叠按根记录去重，同一结果分类组同时命中多个 case 会报歧义。

查询计划由 SQL 候选过滤和 Runtime 事实识别共同完成。候选分页、完整关系读取和最终分页分开处理，保护一对多集合完整性。默认候选上限为 1000；到达上限仍不能完成请求页时失败。需要识别的任意 RuleView 读路径并非全部可自动推导，应用须显式加载相应关系或提供读取适配器。

SQL 使用绑定参数。`SqlTranslator` 的表名映射只映射 FROM/JOIN 的逻辑表到部署表，不能替换设计谓词。查询连接来自声明路由和应用 ConnectionRouter；Spring 接入使用事务感知连接，不由查询 Session 擅自提交外层事务。

上述语义是当前事实查询，不保存事件历史。单 Runner/编译图仍有单根约束，多流程应用应明确选择图切片。详见 [Query Runtime](../dec-java-lite/docs/query-runtime.md)。

## 9. 生成器及文件所有权

`GeneratorPipeline` 是协调入口，细分职责包括：

| 组件 | 职责 |
| --- | --- |
| `GenerationContextBuilder` | 文档归属、分包及生成上下文 |
| `DataModelBuilder` | Data/Enum/View 模型与校验 |
| `ApiModelBuilder` | API 契约和分组 |
| `JavaModelSupport` | Java 命名、类型等公共支持 |
| `DesignProjectionGenerator` | Information/Business 的只读投影 |
| `DesignIndexWriter` | Design ID 与代码索引 |
| `GeneratedProjectWriter` | 模板写入、路径检查、清单与旧产物清理 |

生成器维护 Controller、Service **接口**、DTO、Entity、View、Enum、DAO/Mapper 接口、配置、POM、设计索引和资源清单。应用负责 ServiceImpl、Workflow、Assembler、数据访问实现、自定义 Action/Listener 等接入。

`.dec-generated` 和 `dec-generated-manifest.txt` 确定覆盖范围。旧清单中安全的受管路径可以被更新/清理；手写文件不应加入该清单。路径越界、符号链接和输入冲突不会静默覆盖。生成代码中需要修复的问题，应修改模型或模板后重生成。

代码中的 `Design ID + YAML#路径 + XML#路径` 与 `DEC-DESIGN-INDEX.md` 便于阅读；它们不代替验证手写行为的 Binding 和测试。生成配置用法见 [生成说明](../dec-java-lite/docs/how-to-generate.md)。
