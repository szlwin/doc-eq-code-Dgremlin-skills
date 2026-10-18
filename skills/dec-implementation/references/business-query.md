# 面向业务的查询实现

处理 `find/eq/start/end/with`、业务阶段范围或“曾经支付/订购”查询时读取本页。

首先确认实际仓库和执行入口。用户要求“当前项目”时以当前工作区为目标；旧 DEC 的代码、路径和文章只作为参考，不能据此切换实现仓库。当前工程的框架是 `dec-java-lite/dec-lite-query`，Demo 是 `demo/order-payment-app`。原 `doc-eq-code-Dgremlin/dec-core-model` 的 `DirectoryContainer` 与 `BusinessQuery` 是两条实现链；不得用其中一方的接口或测试宣称另一方已实现。原 Compiler 的 P5/P6 deferred 不因修复旧查询自动变为 implemented。

先明确查询当前事实还是历史经历。`dec-java-lite` 的 `BusinessQuery` 根据当前 View + Information 和 Directory 图查询阶段范围；没有持久化事件历史。流程可达、当前状态或后继节点不能证明某条记录曾走过某一条路径。若需求要求回退/取消后仍可检索历史，明确历史数据设计和持久化边界；不得把当前阶段查询标记为历史查询已实现。

## 框架入口

```java
BusinessQuery query = BusinessQuery.find("OrderInfo")
    .start("ordered").end("success").with("user").page(0, 20);
QueryPlan plan = compiler.compile(query); // QueryCompiler.compile(BusinessQuery)
QueryResult result = runner.run(plan);    // QueryRunner + 真实 QueryExecutor
```

- `find` 接受 View name/Design ID，编译器绑定指定的 Business graph。多流程项目显式传入该流程的设计切片；当前 DirectoryGraphCompiler 仍只接受一个 root，不可宣称支持多根自动选流程。
- `eq` 选择某个 Directory 当前阶段；result 选择设计声明的 case 并集。检查 case 目标、case 本身及 result 的 Information。不根据文章/名称补造未声明的 case。
- `start` 选择起点和正向可达节点；`end` 限定能够到达终点的节点。两端包含，范围取所有连接路径，排除旁支与 Back 边。终点是 result 时包含其 case；终点是单个 case 时不能把兄弟 case 纳入。
- 阶段集合取 Information 并集，禁止翻译成 `status >= ...` 或 Java 中硬编码状态列表。普通阶段重叠时根记录去重，同一 result 中所选 case 同时成立时报歧义。
- `with` 使用 View 中的关系、键及 Data 映射；不按“用户”猜表名或在 Service 手写联表。
- 中文名称可直接是 canonical name，也可通过不可变 `BusinessQueryNames` 显式绑定到 View/Directory name 或 ID、关联 path。别名配置不保存状态值；不得模糊匹配 desc，不允许遮蔽另一 canonical 名称或别名链。
- `eq` 与范围互斥；未知/跨 View 节点、倒向或不可达范围、错误关联路径必须在 I/O 前失败。

旧 `DirectoryQuery.find("PayResult").eq("success")` 仍保留：find 是 Directory、eq 是其 case，from/to 仅为原路径约束。不能把新增 start/end 作为旧 from/to 的拼写别名，两者查询结果不同。

## 执行与验证

复用 QueryCompiler → QueryPlan → SqlTranslator → QueryRunner。查询只读取与识别，不调用 Directory Action/Change/Back；业务谓词仍来自 Information。无法安全下推的 RuleView/表达式走有界运行时校验，缺失 evaluator 必须失败。不要用空 evaluator 或内存固定结果假装数据库已接入。

SQL 使用真实 DataSource/ConnectionRouter 和参数绑定；where 路径使用 canonical View 属性，不是别名。分页先取 distinct 根 ID，再加载完整关系，不能先对 JOIN 行分页截断集合。事务内使用 SessionJdbcQueryExecutor + session QueryRunner 保证连接归属；不要宣称无事务的两次读具有快照一致性。

回归必须验证：四种链式查询的实际记录集合、端点、排除旁支、result 分支互斥、错误名称/范围、无用户和多明细、分页/去重、参数绑定、设计表达式或流程边修改后行为变化。真实 MySQL 未运行时如实区分计划/内存测试与数据库验证。示例和实现参见工程 `dec-java-lite/docs/query-runtime.md`，生成应用直接调用上述框架入口，不生成平行状态判断或查询 DSL。

## 当前 Demo 与应用装配

复用 `BusinessQueryRuntime(project, names)` 保存编译结果，调用 `run(query, executor, translator, adapter)` 时独立创建识别引擎和 RuleView 绑定。不要用只包含 `InformationCompilation` 的空注册引擎接入含 ruleRef 的真实设计。没有外部读取规则时 adapter 可为 null；需要外部读取时必须提供真实适配器，禁止固定返回 TRUE。

框架自动收集 model expression 的关系读取；任意 RuleView 的读取路径仍须由应用显式 `with` 所需的 View 关联，或由读取适配器加载。当前 Demo 的 `OrderQueryService` 加载 `payInfo`、`orderDetailList` 后运行真实 RuleView；不能把简化测试 fixture 的单状态表达式替换到应用设计中。

Spring 应用通过事务代理调用查询 Service；`TransactionAwareDataSourceProxy` 交给 `JdbcQueryExecutor`，候选/明细/关联共享 Spring 连接。不要在 `SessionJdbcQueryExecutor` 内重新提交 Spring 托管连接。只读事务的隔离级别明确，加入外层事务时以外层隔离级别为准。

部署表名与设计表名不同可用 `SqlTranslator(dialect, tableNames)` 配置不可变映射，保留标识符校验；不做 SQL 字符串 replace，不更改业务条件、字段、关系或数据源路由。当前 Demo 用它映射到 `dec_demo_*`。生成模板包含 `dec-lite-query` 依赖，修改模板后正式重新生成应用，不手改生成 POM。

调用示例集中在 `demo/order-payment-app/.../order/query/OrderQueryExamples.java`，装配在 `.../order/service/OrderQueryService.java`；均为应用自有绑定。`OrderQueryTest` 通过真实 Workflow 建立数据库事实，证明四种查询、可选用户、多明细分页、事务内未提交数据和 Back 后当前事实变化。新增 HTTP 查询入口时先补 canonical API，不手改生成 Controller。维护对应 Design ID 的 Binding 与当次测试证据，保留未实现节点状态。

## 原 DEC 旧 DirectoryContainer

原执行器已提供 eq（equals 别名）、单 start、闭区间、默认 find、include/exclude 阶段集合、with 关联及字段投影。查询图来自根下的完整 subdirectory 声明；普通边 child → parent，旧 handler 表示 parent → case，XML/YAML 必须保留该字段。不能使用丢分支信息的 parentDirectory 单指针规划，也不能按 desc/状态名猜路径。原订单示例只有分散阶段，应用必须补齐实际根/边并加载它，不能把测试中显式补齐的图当成现有生产配置。

旧 change-info 只允许解析可确定的标量字面量赋值并参数化；复杂脚本、作用域 property 或无法映射的字段应拒绝。不得以字符串替换把任意写操作变成读取条件，也不得把这个兼容层称为现代 Information 完整实现。

优先显式 connection；否则取默认连接或主 Data 的唯一映射连接，歧义失败。using 接口借用已打开连接、不负责其事务；自建连接必须 finally 关闭，查询和清理失败都可见。关联依据 View 映射批量加载，先根分页再完整集合；字段投影不能删除内部关联键。默认页大小 500、关联上限 10000；超限不能静默截断。查询不执行 Action/Change。详见原工程 `docs/directory-query-runtime.md` 和 DirectoryQueryRegressionTest / DirectoryQueryMySqlTest。
