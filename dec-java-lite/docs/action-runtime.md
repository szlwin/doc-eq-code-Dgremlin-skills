# P4 Action Runtime

`dec-lite-action` implements the P4 execution boundary:

- `BusinessActionParser` reads canonical `business.directories[].actions`.
- `RuleViewCompiler` binds RuleView files to their owning System through `systems[].ruleFiles`.
- `RuleViewActionInvoker` dispatches by `(system, ruleRef)` and resolves the underlying `RuleViewKey` only when unambiguous.
- `ActionRuntime` runs preconditions, invokes a RuleView or a registered Custom Action, collects `MutationSet`, verifies Produce, and commits model changes only after verification succeeds.
- `ActionPipeline` runs ordered actions fail-fast and verifies the directory Information after a directory's actions finish.

`RuleViewInterpreter` supports canonical `check`, `checkPattern`, `checkData`, `checkDataPattern` and `dsl` rules. `insert`, `update`, `delete`, `get` and `query` require an explicit `DataOperationAdapter`; missing adapters return `ERROR` and never silently succeed.

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
