# P3 Information Engine

`dec-lite-information` 独立消费 canonical `kind: systems` 文档中的 `systems[].information`，不把 Information 伪装成生成的 Controller 或 Service。

## 编译

```java
DecProject project = new DecYamlParser().parse(input);
InformationCompilation compilation = new InformationParser().parse(project);
RuleViewRegistry ruleViews = new RuleViewRegistry()
    .register("payment", "OrderInfo", "isPaySuccess", invocation -> /* invoke the system-owned RuleView */
        RecognitionResult.of(RecognitionResult.Status.TRUE, invocation.context().version(), "payment.isPaySuccess"));
InformationEngine engine = new InformationEngine(new InformationEngineContext(compilation, ruleViews));
```

Information 有三种互斥形式：

- `ruleRef`：RuleView 原子 Information；通过 `RuleViewRegistry` 以 `(system, viewRef, ruleRef)` 精确接入 system-scoped RuleView evaluator。裸 RuleView 名称不会参与查找。
- `ruleData`：模型表达式原子 Information；支持比较、`and/or/not`、`exists`、`every` 和 `any`。
- `expression`：只允许 `system.name` 形式的 InformationKey、`and/or/not` 和括号。

编译阶段会检查引用存在性、表达式语言边界、`viewRef`/`changeData` 组合、依赖循环，并生成稳定 `graphDigest` 和拓扑顺序。

## 识别、物化与失效

```java
RecognitionResult result = engine.evaluate(new InformationKey("order", "payable"), context);
MutationSet mutations = engine.materialize(new InformationKey("order", "ordered"), context);
Set<InformationKey> invalidated = engine.invalidate(mutations);
InformationExplanation explanation = engine.explain(new InformationKey("order", "payable"));
```

识别状态为 `TRUE`、`FALSE`、`UNRESOLVED`、`ERROR`。缺失模型路径和未注册 RuleView evaluator 返回 `UNRESOLVED`；求值或配置错误返回 `ERROR`，不会静默转换成 `FALSE`。`and/or` 使用短路求值，并保持 `UNRESOLVED` 与 `ERROR` 的传播语义。结果带有稳定的 evidence、依赖和 `TraceEvent` 列表。只有声明 `changeData` 的 `ruleData` 原子 Information 可以物化；物化经过 `ModelContext.WriteGuard`，写入后重新识别，未成立时不会提交。

`InformationEngineContext` 固定一次编译结果和 RuleView 注册表。缓存按 `ModelContext` 实例隔离，并以模型版本校验；MutationSet 会使模型表达式、相关 RuleView 以及 reverse DAG 上的复合 Information 失效。当前 RuleView evaluator 未声明静态模型读路径，因此对任意非空 MutationSet 使用保守失效，避免错误复用结果。

P3 仍不负责 XML RuleView 本身的执行实现、Business Action、Produce 或 DataSource Adapter；RuleView 调用边界已经固定，具体 evaluator 可由后续 runtime 接入。
