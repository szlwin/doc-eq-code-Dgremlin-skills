# P5 Directory Runtime

The `dec-lite-directory` module compiles canonical `business.directories` into a typed graph. Ordinary execution edges point from child to parent; result case edges point from the result Directory to each case; Back edges are separate and must correspond to an adjacent ordinary execution edge.

`DirectoryGraphCompiler` checks one root, unique names, known references, cycles, reachability, single execution parent, result case ownership, and forbidden `predecessor` roles. `compileReady` additionally checks that every Custom Action has a registered implementation. `PathPlanner` returns a unique explainable path. `BackPlanner` follows a case back to its result owner, then traverses explicit Back edges one level at a time.

`DirectoryEngine.executeTo(target, context)` uses one working model and one caller supplied transaction for the entire path. Each Directory checks dependencies, runs actions in YAML order, applies its declared Information Change, checks its own Information, and classifies result cases. Exactly one case must be `TRUE`; `UNRESOLVED`, `ERROR`, zero matches, and multiple matches fail. The working model is published to the Session after its scope completes; a joined outer scope may still roll back. On failure the transaction rolls back and the caller's model and current Directory stay unchanged.

`DirectoryEngine.backTo(target, context)` runs each Back edge's ordered actions and restores the target Directory Information before committing. It does not jump over intermediate directories.

The trace records the path, dependency checks, action results and evidence, Produce verification, Change, Information checks, case classification, Back, transaction completion, and failures. The module does not implement SQL queries or database transaction management; callers supply an `ActionTransaction` and external `DataOperationAdapter`. The adapter receives the same transaction through `RuleViewRegistry.Invocation.transaction()`.

`DirectoryWorkflowRunner.run/advance` require an explicit `WorkflowTransaction`. The Runner owns an outer Session scope; Directory execution joins it. `CompletionHandler.complete(completed, execution, transaction)` runs while that scope is active, before its physical commit. The callback may persist, reload and validate the application value; its execution result is provisional. The transaction argument exposes the same resource as Rule adapters through `transaction.resource(route)`; participants cannot commit the owning scope.

Two modes are supported:

- **External:** `WorkflowTransaction.externallyManaged(currentTransaction)` checks that the caller's transaction is active and not rollback-only before Actions and at completion. Runtime never physically commits/closes it. Failure marks the owner rollback-only even if application code catches the exception. `run` reports `EXTERNAL_PENDING`; the caller may still roll back or fail at commit after Runner returns.
- **Runtime managed:** `WorkflowTransaction.runtimeManaged(route, resourceFactory)` opens and enlists one physical `TransactionResource`. The factory must create a new transaction, and every adapter/completion write must use its handle. Final writes and validation finish before Runtime commits once and closes the resource. Failure triggers rollback. Mixing another managed route or an external participant with managed resources is rejected. `run` reports `COMMITTED` only after commit returns successfully.

`dec-lite-spring` provides `SpringWorkflowTransactions.externallyManaged()` for an enclosing `@Transactional` method, `externallyManaged(() -> status)` for a `TransactionTemplate` callback, and `runtimeManaged(route, transactionManager)` for Runtime-owned Spring transactions. The external bridge rejects missing, read-only, completed and rollback-only transactions. The Runtime-managed bridge rejects an ambient Spring transaction; Spring DAOs must use the DataSource controlled by that transaction manager. Other managers/DataSources are not made atomic automatically. Plain JDBC adapters may instead supply a `TransactionResource` containing their connection through the framework-neutral factory.

```java
// Inside a Spring @Transactional method:
WorkflowTransaction transaction = SpringWorkflowTransactions.externallyManaged();
// Or outside any ambient transaction:
WorkflowTransaction owned = SpringWorkflowTransactions.runtimeManaged("data1", transactionManager);
// Pass one of these to advance(...), and use a three-argument completion callback:
// (completed, execution, scope) -> { persist; reload; validate; return persisted; }
```

Commit exceptions are not proof of rollback: Runtime reports failure with an unknown outcome and attempts rollback/cleanup. If the database committed before the error, data can remain committed. Do not automatically retry non-idempotent operations. External commit failures are observed at the owner's boundary. Notifications remain separate: the Demo sends after the physical Spring commit; this does not provide reliable delivery or undo a committed transaction when a notification fails.

Migration: replace raw/empty `ActionTransaction` arguments on Runner `advance` and `RunRequest` with an explicit mode, add the third completion callback argument, and rebuild dependent applications. `run` now throws `DirectoryWorkflowException` on Directory failure instead of returning an unsuccessful `RunResult`; callback/transaction failures propagate. Low-level `DirectoryEngine` / `ExecutionSession` legacy APIs remain available but do not implicitly cover application code run after their scope closes.

`DirectoryWorkflowRunner.recognizeDirectory(model, adapter)` recovers a persisted stable Directory by evaluating each non-result node's canonical Information. It runs no Actions or Change. Exactly one node must be TRUE and every other node FALSE; unresolved, erroneous, overlapping or unmatched states fail closed. This opt-in API requires mutually exclusive stable predicates. Result nodes are transient classification points. Determine entity existence using the repository before recognition; a missing match is not a new entity. After persisting, reload the View and call `assertDirectoryMatches(model, expected, adapter)` inside the same physical transaction.

For command APIs that allow only one step, call `requireSingleStep(current, target, back)` before `advance`: creation targets the root; forward commands require one execution edge; Back requires one explicit Back edge, allowing case ownership hops. Repeated targets and skipped steps fail before Actions. Existing `advance`/`executeTo` multi-step behavior is unchanged. Do not duplicate predecessor lists in application code.

Inspect a canonical project with:

```sh
dec-lite directory --yaml path/to/canonical-dec --target success
```


## 编译与请求隔离

`DirectoryWorkflowRunner` 在构造时编译一次 RuleView，复用同一个不可变 Map 进行 Directory 就绪检查、执行和持久化状态识别。`RuleViewCompiler.bind(compiled, adapter)` 创建新的请求 Registry/Interpreter，适配器不会被保存在 Runner 中。模型、MutationSet、InformationEngine、Session、事务及 payload 仍属于请求。兼容入口 `register(project, adapter)` 保留，但它每次编译，应用应复用 Runner 或先 compile 再 bind。

缓存作用域为 Runner/设计版本，无静态全局缓存；更新设计必须构建新 Runner。这里复用的是 canonical RuleView 编译结果，解释器内部表达式求值方式未改变，不意味着所有 DSL 和表达式已经预编译。

回归覆盖共享 Runner 并发识别/执行时不再读取设计文档，以及独立绑定的 adapter、模型、payload、MutationSet 不串用。可选接线微测量（先运行 reactor install，从仓库根目录执行）：

```sh
java -cp dec-java-lite/dec-lite-action/target/test-classes:dec-java-lite/dec-lite-cli/target/dec-lite-cli-0.1.0.jar com.dec.lite.action.RuleViewBindingBenchmark demo/order-payment-yaml
```

2026-10-14，本机 Java 21.0.12、14 个 RuleView，预热 2,000 次，7 组交替顺序、每组 10,000 次，中位数：compile+bind 4.765 μs，复用后 bind 0.365 μs。此测量只包含规则接线，非 JMH，也不包含 SQL、表达式执行或 HTTP 请求；不设 CI 时间阈值，不据此声称端到端性能提升。
