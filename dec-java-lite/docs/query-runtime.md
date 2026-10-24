# P6 Directory Query and SQL

## Business-oriented stage queries

The runnable application integration is in this repository's
[`demo/order-payment-app`](../../demo/order-payment-app/README.md#业务目录查询):
`OrderQueryService` binds the actual order-payment design, `OrderQueryExamples` shows all four calls,
and `OrderQueryTest` creates facts through the existing Workflow and reads them from MySQL.
It does not use the separate original DEC repository's `DirectoryContainer`.

The business query API implements the `find/eq/start/end/with` examples from
[文档即编码(五)：设计银弹](https://blog.csdn.net/szlwin/article/details/141533962).
It queries **current Information facts** over a design-defined set of stages. It does not record or query event history.
After cancellation, rollback, or overwritten payment data, graph reachability alone cannot prove that a particular order previously paid.
Historical “ever visited” queries require an explicit persisted history design and adapter; they must not silently use this API as evidence of past events.

`BusinessQuery.find(...)` selects a View by canonical name or Design ID. A compiler is scoped to its supplied Business graph;
use the graph/project slice for the intended flow when the application has multiple flows. The existing graph compiler still
requires one root; this API does not change that restriction or silently select a flow by a shared View name.
Optional Chinese presentation aliases map to existing names/IDs, not duplicated state values:

```java
BusinessQueryNames names = new BusinessQueryNames(
    Map.of("订单", "VIEW-ORDER-INFO"),
    Map.of("已订购", "DIR-ORDERED", "支付结果", "DIR-PAY-RESULT", "支付成功", "DIR-SUCCESS"),
    Map.of("OrderInfo", Map.of("用户", "user")));
QueryCompiler compiler = new QueryCompiler(project, graph, informationCompilation, names);

BusinessQuery paidResult = BusinessQuery.find("订单").eq("支付结果");
BusinessQuery orderedOrLater = BusinessQuery.find("订单").start("已订购");
BusinessQuery range = BusinessQuery.find("订单").start("已订购").end("支付成功");
BusinessQuery withUser = BusinessQuery.find("订单").with("用户").start("已订购").end("支付成功");

QueryPlan plan = compiler.compile(withUser.page(0, 20));
QueryRunner runner = new QueryRunner(executor, new SqlTranslator(new MySqlDialect()), informationEngine);
QueryResult result = runner.run(plan);
// result.models(): assembled View data, including user.id, user.name, etc.
```

`executor` is a real `QueryExecutor` (e.g. `JdbcQueryExecutor` with an application `ConnectionRouter`).
`informationEngine` must register the design's RuleView evaluators; SQL pushdown alone does not replace their checks.
Inside a transaction, use `SessionJdbcQueryExecutor` and the session overload of `QueryRunner` for shared candidate/detail reads.
Queries only recognize Information; they never execute Directory Actions, Changes, or Back operations.

For application assembly, `BusinessQueryRuntime(project, names)` compiles the graph, Information and RuleViews once.
`runtime.run(query, executor, translator, adapter)` binds a new RuleView registry and InformationEngine for each call.
Pass null only when recognition rules require no external data operations; unconfigured external operations fail.
The executor owns transaction integration. Under Spring, the demo passes `TransactionAwareDataSourceProxy` connections
to `JdbcQueryExecutor` inside a Spring transaction, so candidate/detail reads share the physical connection and its owner.
Do not use a session-owned JDBC transaction to commit a Spring-owned connection.

Model-expression relations are inferred by the query compiler; arbitrary RuleView read paths are not.
The application must explicitly include the declared View relations needed by those rules, or provide a reading adapter.
The demo includes `payInfo` and `orderDetailList` before classification; it uses the real canonical check/checkPattern rules,
not the simplified predicates of the framework's query fixture.
LEFT JOIN absence is represented as an explicit null for one-to-one relations; Rule expressions may read through that null.
Unknown/unloaded keys still produce UNRESOLVED, preserving failure visibility for missing inputs and spelling errors.

`SqlTranslator(dialect, tableNames)` supports immutable deployment mappings from design table identifiers to physical table identifiers.
It applies only when rendering FROM/JOIN in both candidate and detail SQL; values, columns, predicates and routes are unchanged.
Keys and values are validated SQL identifiers, and explain shows the actual physical SQL.
The demo uses this to read its existing `dec_demo_*` tables. Generated applications now depend on `dec-lite-query`;
application query services are explicit bindings and are not overwritten by code regeneration.

| Selector | Meaning |
| --- | --- |
| `find(View)` | Union of the View's stages in the supplied graph; records matching no stage are excluded. |
| `eq(Directory)` | Exactly that current stage. A result Directory selects the union of its declared case targets, not its predecessor or subsequent stages. |
| `start(A)` | A and all forward-reachable stages for this View. |
| `start(A).end(B)` | Inclusive range: stages reachable from A which can reach B. All connecting forward branches are included; unrelated branches and Back edges are excluded. |
| `end(B)` | B and its forward ancestors for this View. |
| Range ending at a result | Includes its cases as endpoint classifications. A range ending at one case only includes that case. |
| `with(relation)` | LEFT JOIN through declared View relations; supports nested paths and explicit aliases. Missing related data does not remove the root record. |

Result case stages check the target Directory Information, its owning result Information, and the case Information.
The current order fixture declares success/error cases only; it does **not** add paying as a result case merely because the article's diagram has three branches.
Overlapping ordinary stage facts form a union and return each root once; simultaneous matches in the same selected result case group fail as ambiguous.
`eq` cannot be combined with `start/end`. Reverse/unreachable ranges, missing stages, wrong View targets, unknown joins,
duplicate/ambiguous aliases and aliases shadowing a different canonical name/ID fail before database access.
Aliases are immutable and validated at compiler creation, with no fuzzy matching of `desc` text or alias chaining.

`where/whereSensitive/whereIn` use canonical View property paths and bound values; aliases apply to `find`, stage selectors and `with`, not filter paths.
Immutable query instances support `page` and `candidateLimit` with the same bounded execution contract as `DirectoryQuery`.
Explain output records `queryMode=CURRENT_BUSINESS_STAGES`, selected directories, Information predicates, joins and route.
Changing the canonical Information expressions or Directory edges and rebuilding the compiler changes query behavior without caller state-code lists.

CLI explain (canonical names/IDs; presentation aliases are Java configuration):

```sh
java -jar dec-lite-cli/target/dec-lite-cli-0.1.0.jar query \
  --yaml fixtures/mix --view OrderInfo --start ordered --end success --with user
```

`--view` and `--directory` are mutually exclusive. Business mode uses `--start/--end`; legacy Directory mode uses `--from/--to`.
`BusinessQueryTest` covers the four examples, inclusivity, invalid selectors, aliases, graph/expression changes and current-versus-history semantics.
`MySqlBusinessQueryIntegrationTest` verifies the four examples on temporary MySQL tables, optional users, complete collections,
distinct-root pagination, bound filters, changed design predicates and payment facts reset after execution.

## Existing Directory query API

`dec-lite-query` compiles a business `DirectoryQuery` into a SQL-neutral `QueryPlan`. The plan records the root View/Data, projection, joins, predicate tree, selected result cases, typed parameters, order, page, connection route, and Information that needs runtime evaluation. `SqlTranslator` renders the plan using `AnsiSqlDialect` or `MySqlDialect`; SQL identifiers come only from validated metadata and values remain bound parameters.

```java
DirectoryQuery query = DirectoryQuery.find("PayResult")
    .eq("success")                   // result case, never an execution child
    .from("ordered").to("success")
    .with("user").with("orderDetailList")
    .where("id", 10L)
    .page(0, 20)
    .candidateLimit(1000);
QueryPlan plan = new QueryCompiler(project, graph, informationCompilation).compile(query);
String explain = new SqlTranslator(new MySqlDialect()).explain(plan);
```

`find(PayResult)` builds an OR over its compiled `CaseEdge` entries. `.eq("success")` keeps only that case. The ordinary execution predecessor `paying` is not a result case. A query of a non-result Directory uses its own Information; a case target additionally checks the owning case Information. `from` validates the forward path to the queried Directory; `to` must name that Directory or one selected result case.

`InformationPredicateCompiler` pushes only proven conditions. RuleView Information remains runtime-only. Simple model equality can be pushed down; an `and` containing `every(...)` can push its scalar equality while retaining the complete Information check after SQL. Expressions containing `or` or `not` stay runtime-only when a safe equivalent is unavailable. This avoids narrowing a candidate set incorrectly.

`QueryRunner` pages distinct root IDs, loads complete joined rows for those IDs, assembles one-to-one and one-to-many View relations, then evaluates Information and selects the requested page. Model-expression read paths automatically include required View relations. RuleView evaluators may load additional data through their own adapter when their reads are not declared in the View. `candidateLimit` is a hard candidate scan budget (default 1000) for every query mode; the compiler rejects a page whose `offset + size` exceeds that budget, preventing large offsets from silently turning into an unbounded scan. The plan trace records the page end and scan budget, and successful runs add the actual `scanCost` and selected count. Reaching the budget before completing the page still fails explicitly with the scanned and selected counts. Missing RuleView evaluators yield an `UNRESOLVED` query error. `ResultAssembler` deduplicates one-to-many children by Data ID, so joins do not distort root pagination.

`ConnectionRoute` is derived from the root Data table's `dataSource` and canonical `connectionInfo`. Missing or cross-connection routes fail compilation. `JdbcQueryExecutor` uses `PreparedStatement` and a caller supplied `ConnectionRouter`. `QueryExecutor`, `DataCommandExecutor`, `DatasourceCapabilities`, and `ResultAssembler` are the lite data source boundary. There was no legacy `Directory.find()` implementation in `dec-java-lite` to replace; new callers use `DirectoryQuery`.

CLI explain:

```sh
java -jar dec-lite-cli/target/dec-lite-cli-0.1.0.jar query \
  --yaml fixtures/mix --directory PayResult --eq success \
  --with user --where id=10 --size 20
```

The CLI prints selected cases, Information, joins, pushdown/post-filter status, route, SQL, and bound parameter metadata. Sensitive parameters created by `whereSensitive` are redacted in explain output. The SQL example uses the test fixture; the query API itself is independent of that fixture. A real MySQL deployment still supplies a connection router and the system-scoped RuleView evaluators.

`MySqlQueryIntegrationTest` runs when `DEC_MYSQL_JDBC_URL` is set; set `DEC_MYSQL_USER` and `DEC_MYSQL_PASSWORD` as needed. It creates temporary `order_info`, `user_info`, and `order_detail_info` tables in the supplied disposable database, then verifies generated MySQL SQL, JDBC binding, case filtering, joins, distinct root paging, and collection assembly. Without the URL, only this integration test is skipped.
