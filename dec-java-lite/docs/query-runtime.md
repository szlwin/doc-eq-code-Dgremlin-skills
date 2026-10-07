# P6 Directory Query and SQL

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

`QueryRunner` pages distinct root IDs, loads complete joined rows for those IDs, assembles one-to-one and one-to-many View relations, then evaluates Information and selects the requested page. Model-expression read paths automatically include required View relations. RuleView evaluators may load additional data through their own adapter when their reads are not declared in the View. Post-filter queries have a finite `candidateLimit` (default 1000); reaching it before completing the page fails explicitly. Missing RuleView evaluators yield an `UNRESOLVED` query error. `ResultAssembler` deduplicates one-to-many children by Data ID, so joins do not distort root pagination.

`ConnectionRoute` is derived from the root Data table's `dataSource` and canonical `connectionInfo`. Missing or cross-connection routes fail compilation. `JdbcQueryExecutor` uses `PreparedStatement` and a caller supplied `ConnectionRouter`. `QueryExecutor`, `DataCommandExecutor`, `DatasourceCapabilities`, and `ResultAssembler` are the lite data source boundary. There was no legacy `Directory.find()` implementation in `dec-java-lite` to replace; new callers use `DirectoryQuery`.

CLI explain:

```sh
java -jar dec-lite-cli/target/dec-lite-cli-0.1.0.jar query \
  --yaml fixtures/mix --directory PayResult --eq success \
  --with user --where id=10 --size 20
```

The CLI prints selected cases, Information, joins, pushdown/post-filter status, route, SQL, and bound parameter metadata. Sensitive parameters created by `whereSensitive` are redacted in explain output. The SQL example uses the test fixture; the query API itself is independent of that fixture. A real MySQL deployment still supplies a connection router and the system-scoped RuleView evaluators.

`MySqlQueryIntegrationTest` runs when `DEC_MYSQL_JDBC_URL` is set; set `DEC_MYSQL_USER` and `DEC_MYSQL_PASSWORD` as needed. It creates temporary `order_info`, `user_info`, and `order_detail_info` tables in the supplied disposable database, then verifies generated MySQL SQL, JDBC binding, case filtering, joins, distinct root paging, and collection assembly. Without the URL, only this integration test is skipped.
