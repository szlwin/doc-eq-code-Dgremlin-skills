# P4 Action Runtime

`dec-lite-action` implements the P4 execution boundary:

- `BusinessActionParser` reads canonical `business.directories[].actions`.
- `RuleViewCompiler` binds RuleView files to their owning System through `systems[].ruleFiles`.
- `RuleViewActionInvoker` dispatches by `(system, ruleRef)` and resolves the underlying `RuleViewKey` only when unambiguous.
- `ActionRuntime` runs preconditions, invokes a RuleView or a registered Custom Action, collects `MutationSet`, verifies Produce, and commits model changes only after verification succeeds.
- `ActionPipeline` runs ordered actions fail-fast and verifies the directory Information after a directory's actions finish.

`RuleViewInterpreter` evaluates model-only `check`, `checkPattern` and `dsl` rules. `checkData` and `checkDataPattern` require an explicit adapter read; `insert`, `update`, `delete`, `get` and `query` require adapter execution. Missing adapters return `ERROR` and never silently succeed. `RuleDataAdapter` bridges these operations to a `RuleDataAccess` driver. See [Rule compatibility](rule-compatibility.md) for supported syntax and backend responsibilities.

Custom Actions implement `CustomAction.name()` and `execute(ActionExecutionContext)`. An Action without `ruleRef` uses its `name` as the default registry key. `context.model()` and `context.payload()` supply inputs; `context.produce(ref, value)` publishes outputs for verification before execution returns. Exceptions fail the Action.

Use `CustomActionRegistry.from(providers)` to collect provider instances into a frozen, application-scoped registry. Duplicate or empty keys are rejected; missing definitions fail `DirectoryWorkflowRunner` readiness validation. There is no global registry and a missing custom Action never falls back to a RuleView.

Generated Spring projects include `config.CustomActionConfiguration`, which collects `CustomAction` beans and constructs this registry. Application Workflows inject the registry and pass it to the runner rather than referencing each Action class. In other applications, annotating a provider with `@Component` alone is insufficient: explicitly assemble and supply the registry. Generated `ActionNode.customType()` exposes the effective custom registration key; `isCustom()` distinguishes it from a RuleView Action.

The demo's `payment.action.SmsNotifyAction` reads the order ID and schedules `SmsSender.sendPaymentError` in Spring `afterCommit`. A rollback prevents sending. `LoggingSmsSender` only logs; a real SMS adapter must replace that provider. An after-commit send failure cannot roll back the committed order, and the callback is not durable delivery. Delivery retry/idempotency belongs to the application's provider or declared messaging contract. A deferred send result cannot satisfy a synchronous Produce declaration.

Produce verification requires declared values and, when `informationRef` is present, requires that Information to evaluate to `TRUE`. A failed Produce check leaves the candidate model uncommitted.

The current module does not implement Directory path planning or database adapters. Those are later runtime concerns; P4 only fixes their typed execution boundary.


## 截止时间

ActionRuntime 在依赖检查前、调用前、调用返回后及模型发布前检查 deadline，过期返回 `TIMEOUT`，不会继续下一动作或提交；Directory 和 Runner 也在完成事务前检查，Runner 覆盖应用最终写回回调。达到截止时刻即视为过期。默认使用系统 UTC Clock，测试可注入同一 Clock，不依赖 sleep。外部事务失败标记回滚，Runtime 自管事务回滚已参与的资源。

这是同步执行的截止时间检查，不是强制终止线程。Custom Action 必须把剩余时间传入客户端的连接/读取超时，阻塞且不返回的外部调用无法由检查点抢占；已经提交/发送的外部副作用不因超时自动撤销。提交后短信失败的现有策略与验证见 `generality-scenarios.md`。

## Business-bound Action listeners

Each Business flow can bind one `ActionListener`, keyed by its canonical Business Design ID. The listener receives the before/after events of every executed Action in that flow, including RuleView Actions, Custom Actions, case branches and Back Actions. A flow without a listener keeps its existing behavior; duplicate bindings and unknown Business IDs fail initialization.

```java
@Component
public class OrderPaymentListener implements ActionListener {
    public String businessId() { return OrderPaymentFlow.ID; } // BUS-ORDER-PAYMENT
    public void before(ActionListenerContext context) {
        switch (context.action().id()) {
            case "ACT-SMS-NOTIFY" -> { /* notification preparation */ }
            case "ACT-RECEIVE-PAY-RESULT" -> { /* callback integration logic */ }
            default -> { }
        }
    }
    public void after(ActionListenerContext context, ActionResult result) {
        // This Action succeeded; the transaction has NOT committed yet.
    }
}
```

Generated `ActionListenerConfiguration` collects listener beans into an immutable `ActionListenerRegistry`. Inject it into the application Workflow and construct `new DirectoryWorkflowRunner(project, customActions, listeners)`. Old constructors keep their no-listener behavior. Without Spring, use `ActionListenerRegistry.from(List.of(listener))`. Runner validates registered Business IDs against `BusinessActionParser.parseByBusiness(project)`; the parser retains Business ownership on each Action, including Back Actions. Listener dispatch uses that ownership, not Action name, Directory name or file name. Registering two listeners for one Business fails instead of using an ordering policy.

For direct ActionRuntime usage, pass the registry and call `validate(allBusinessIds)` at assembly time; construct ActionDefinition with its Business ID. Legacy standalone ActionDefinition constructors have no Business ownership and do not dispatch flow listeners. Binding isolation does not change the current DirectoryGraph compiler's requirement for one executable root graph per Runner; it does not add multi-flow routing to Runner.

Execution order: dependency checks → flow listener `before` → Action body → optional standalone Change / Produce verification → flow listener `after` → Produce revalidation → publish Action model. Directory Change and final Information checks follow. Action failure or skip does not run `after`. Listener exceptions stop remaining work and propagate through the existing transaction failure path; phase and class appear in the diagnostic. Trace includes `listener-before` and `listener-after` events. Before/after are per Action, not once at flow start/end.

`ActionListenerContext` exposes `businessId()`, `action()` (including Design ID, name and owning Directory), detached `read(path)`, `payload()`, `deadline()` and guarded `write(path, value)`. Writes participate in the Action MutationSet and invalidate dependent Information caches. They remain private until verification succeeds. Invalidating Produce or throwing in after causes the owning transaction to roll back (or mark an external transaction rollback-only). Listener code must not duplicate design-owned predicates, reorder Actions or implement another state machine.

Instances can be shared across requests: keep request data in local variables, never listener fields. Runtime checks deadlines around callbacks; clients must use `deadline()` to bound their I/O. Existing Session `WorkflowCallback` remains a separate observer; flow listeners see the actual Action working model.

`after` means Action success, not commit or external delivery. Demo `business.OrderPaymentListener` receives the whole order-payment flow. Its SMS-specific branch observes that `SmsNotifyAction` registered a Spring after-commit callback. SMS still sends only after commit via `SmsSender`; listeners must not send it twice. Demo tests cover success/error branches, Back Actions, full event order, transactional writes and rollback. Runtime tests cover Business isolation and duplicate rejection.

## Typed flow View in listeners

`OrderInfo order = context.view(OrderInfo.class)` decodes the Action's current working model. `context.viewRef()` exposes the bound design View name. Every call returns a detached snapshot: changing its setters does not mutate the flow; use `context.write(path, value)` for guarded, tracked changes. Before/after see their respective current values, including changes by the Action body.

`DirectoryWorkflowRunner.advance` reuses the supplied ModelCodec, binds the Java class of the input View and checks the graph's Directory.modelRef values. No reflection-based field guessing or extra listener-side bridge is needed. A graph with different modelRefs cannot silently use one typed binding. Missing bindings, type mismatches and incompatible modelRefs fail clearly. Low-level requests use `request.withView(new ActionViewBinding("OrderInfo", OrderInfo.class, codec::fromModel))`; direct ActionRuntime users can pass the same binding in its constructor. Bindings are scoped to the execution and do not cache a mutable View.

## Manually injected DataSource implementations

Register implementations in application code, not in the canonical business design:

```java
@Bean
DataSourceAdapter tenantA() {
    return new DataSourceAdapter("tenant-a", myTenantAdapter);
}
```

Generated `DataSourceAdapterConfiguration` collects all such beans into `DataSourceAdapterRegistry`. Non-Spring applications use `DataSourceAdapterRegistry.from(List.of(new DataSourceAdapter("tenant-a", adapter)))`.

Choose implementations separately for each execution:

```java
var selected = runner.selectDataSources(registry, Map.of(
    "data1", "tenant-a", "paymentService", "payment-provider", "mongo1", "error-store"));
// Use selected as the DataOperationAdapter for recognizeDirectory, advance and final assertion.
```

The advance overload accepting registry + selection and `RunRequest.withDataSources` are also available. The selected map is immutable and does not alter the shared Runner or design. Registry rejects duplicate names; selection rejects missing implementations. Binding validates coverage of all external-operation logical sources in the compiled design (including alternate branches) before transaction binding. Unknown logical source choices fail. Pure model checks do not call adapters. Rule-level dataSource overrides still take precedence over RuleView defaults, and adapters receive an immutable invocation-local CompiledRule containing the effective logical source and all original command/error/customer metadata.

This routes DEC Rule operations only; it does not switch application DAOs/JDBC connections or enlist remote resources automatically. Keep adapters within the established WorkflowTransaction/resource contract. Demo OrderWorkflow exposes selection overloads for Java callers and uses DEFAULT_DATA_SOURCES for its existing HTTP API; OrderDataSourceConfiguration manually registers local. Tests inject a second implementation and verify route isolation and rollback on its write failure.

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
