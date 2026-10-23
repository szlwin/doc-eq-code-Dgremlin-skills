# Java framework in depth

[Back to documentation home](README.md) · [module map](architecture.md#3-java-module-map)

`dec-java-lite` is the execution framework behind the order demo. It uses Java 21 and can generate Spring Boot/MyBatis projects; the [centralized configuration](../../dec-java-lite/dec-lite-generator/src/main/resources/generation-versions.properties) is authoritative for specific dependency versions. The framework is responsible for reading designs, compiling rules, running flows, and generating interfaces, while the payment platform, SMS, and database connections are still wired up by the application itself.

## 1. How design files become an executable plan

When the demo is opened, the application does not re-read the YAML on every request. At startup, the design is first parsed into a `DecProject` and compiled into a reusable plan; once a request arrives, only that request's own model and transaction environment are created.

`DecYamlParser` and `DecXmlParser` convert input into a `DecProject`. Both front ends share the same downstream validation and compilation, so YAML and XML do not each carry their own execution semantics. The `dec-id` comments in XML help restore YAML IDs; when comments are absent, corresponding identities may be generated, so do not assume they naturally match the existing Binding.

The parsers reject dangerous or unsupported input: for example, XML DTD/external entities and unknown semantic structures (including the legacy `directory-config` root); duplicate YAML keys or custom Java tags also cannot be treated as a valid design. There is a limit on a single design input, currently 8 MB, and the classpath manifest has a separate limit. See the [input contract](../../dec-java-lite/docs/canonical-input.md) for details.

The compiler converts references, expressions, graphs, and rules into reusable structures. `SemanticDigest` identifies execution semantics; the Binding's design digest instead covers the design file structure it defines. The two differ in purpose and algorithm and must not substitute for each other.

`RuntimeCatalog` replaces the snapshot only after the candidate design compiles and passes the readiness checks; on failure the old version is retained, and existing sessions keep using their own version. The demo builds its Runner directly at startup and does not automatically enable this hot reload process.

## 2. Information execution

Information answers "does the order currently satisfy a given condition". In the payment demo, it determines whether an order is ordered, whether it is payable, and whether the payment result is successful.

The core components are `InformationParser`, `InformationEngine`, `InformationEngineContext`, `RuleViewRegistry`, and `ModelContext`.

- Compile: parse `ruleRef/ruleData/expression`, check references and dependency cycles, and form the dependency graph.
- Evaluate: return TRUE/FALSE/UNRESOLVED/ERROR, preserving explanations and evidence.
- Materialize: execute permitted `changeData`, pass the write permission checks, and re-evaluate.
- Invalidate: model changes produce a MutationSet that invalidates the affected facts and their dependents.

Caches are isolated by model instance and version. When a RuleView does not currently provide a complete static read path, conservative invalidation applies, prioritizing correctness. `RuleViewRegistry` resolves by system, View, and rule key; rules of another System must not be guessed by bare name.

Collection predicates in model expressions let "all order details are paid successfully" be expressed by the design. Amount comparisons use exact numeric semantics; large integers and amounts must not be uniformly converted to double just for convenience.

## 3. RuleView, DSL, and external operations

A RuleView is where the Runtime actually executes rules. It can inspect the current model and can also read or write external data through adapters.

`RuleViewCompiler` compiles immutable rules; at execution time, the binding process injects this run's adapters, model, and scope. The Runner reuses the compiled results and does not re-invoke the legacy `register()` compilation path on every request.

`RuleViewInterpreter` executes rules in order and stops at the first non-TRUE result. check/checkPattern/dsl inside the model are executed by the framework; checkData/checkDataPattern and CRUD/get/query access external data through adapters.

The DSL does not directly execute Java or arbitrary scripts. It currently supports constrained assignment, local variables, arithmetic, conditionals, collection writes, and error conditions. For example, the demo's fallback rule:

```text
payInfo.resultCode : null;
```

Writes go through Invocation/ModelContext permission and change tracking; `#` local variables never become persistent model fields. Arbitrary functions, `.sum`, `.compute`, and the like from the legacy language cannot automatically be assumed available. See [rule compatibility](../../dec-java-lite/docs/rule-compatibility.md) for the full scope of support.

`RuleDataAdapter` can bridge generic rule operations to an application-implemented `RuleDataAccess`. The driver is responsible for parameter binding, physical access, and participating in transactions, while the framework merges read results or backfills collections. The presence of `cmd` does not mean the framework ships interpreters for every SQL/NoSQL command.

## 4. DirectoryWorkflowRunner: run the flow to completion and confirm the result

The success/error branches of order payment come from the compiled Directory, so the Workflow does not need to duplicate a state machine. The Runner advances the flow, and the application writes the result back and re-verifies it through the completion callback.

The Runner reuses the compiled graph, rules, and Information results, uniformly handling stage recognition, dependency checks, Action execution, case classification, Change, Back, and result verification. The application Workflow supplies model conversion, payload, adapters, transaction mode, and the completion callback.

The application's completion callback carries out the final write back, reload, and persistence consistency checks. For example, the demo first calls `persistFinal`, then rebuilds the View from the DAO, and finally runs `assertDirectoryMatches`. It is a mandatory step before the transaction commits and cannot be moved after the commit.

This keeps the Workflow in its application-orchestration role; there is no need to write another copy of success/error state checks inside `receivePaymentResult`. Flow branches come from the compiled canonical Directory.

## 5. Two ways to wire transactions

The demo usually has Spring open the transaction, but the framework also supports entry points without an outer transaction. The key difference between the two modes is: who owns the physical transaction, and who performs the final commit.

| Mode | Who opens/commits the physical transaction | Wiring |
| --- | --- | --- |
| Externally managed | An application framework such as Spring `@Transactional` | `SpringWorkflowTransactions.externallyManaged()` |
| Runtime-managed | The Runner's transaction scope and bridging implementation | `SpringWorkflowTransactions.runtimeManaged(route, transactionManager)` |

The externally managed mode checks that a real transaction is valid and marks it rollback-only on failure. When the Runner exits successfully, `EXTERNAL_PENDING` means it is still waiting for the outer transaction owner to commit; it must not falsely report "already committed" to callers.

The runtime-managed mode suits well-defined entry points without an outer transaction; the current contract is limited to the supported single-route/resource boundary, and selecting multiple adapters is not a substitute for a distributed transaction. A commit exception may also mean an unknown outcome, so you cannot uniformly claim a rollback happened.

Both modes require actions, the final write back, and validation to happen before the physical commit. An empty ActionTransaction, a newly created Store, or a UnitOfWork without a real transaction cannot satisfy the contract. For more sequencing, see [Directory Runtime](../../dec-java-lite/docs/directory-runtime.md) and [Session/transactions](../../dec-java-lite/docs/session-transaction-runtime.md).

## 6. Custom Actions and flow listeners

A design can declare "send a notification after payment fails", but the Action that actually calls the SMS platform must still be provided by the application. Listeners are a good fit for observing the before/after points of each Action in a flow.

Custom actions implement `CustomAction.name()` and `execute(ActionExecutionContext)` and register with `CustomActionRegistry`. The generated Spring configuration collects the beans; non-Spring integration must assemble the registry itself. Duplicate names or missing implementations fail explicitly.

A Business binds at most one `ActionListener`, matched to the design ID through `businessId()`. It receives before/after for every Action actually executed in that flow, including case and Back; within the same listener class, business handling is distinguished by Action ID.

```java
// Listener method body fragment; OrderInfo is bound by this flow's ModelCodec.
OrderInfo current = context.view(OrderInfo.class);
Object value = context.read("id");
// To change the model, use context.write(path, value); do not modify snapshot setters and expect a write back.
```

A typed View is an independent snapshot of the current working model. Listener instances can be shared across requests, so keep request data in local variables only. Listener failures go down the action/transaction failure path; skipped or failed actions do not trigger after. after means the action succeeded; it still does not mean the transaction committed or that the external notification was delivered.

Deadline checks happen at execution checkpoints and never forcibly terminate blocked threads. Real external requests still need client timeouts configured; remote side effects already sent are not automatically undone by a timeout.

## 7. How logical data sources map to actual adapters

The design carries logical names such as `data1`, `paymentService`, and `mongo1`; the application maps them to actual implementations per request.

There are two layers of selection: the design decides the logical source, and the application decides its implementation for the current request.

```text
Design: Action → Rule → RuleView → Business, yielding data1 / paymentService / mongo1
Wiring: Map<logical name, registered implementation name>, yielding the actual DEC Adapter
Execution: the Adapter uses its own JDBC, HTTP, or other client and honors the transaction contract
```

Information evaluation has no Action layer and uses Rule→RuleView→Business. Custom Actions have no Rule layer and use Action→Business; the logical source and the adapter selected for the current request can be obtained from the context.

Register manually in the form `new DataSourceAdapter("tenant-a", adapter)`, and select per request with `Map.of("data1", "tenant-a", ...)`. Missing, unknown, or incomplete routes are rejected at binding time. The rule metadata declaring sources stays immutable, and a selection must not leak into the next request.

This routes DEC external operations only; it does not automatically switch all of the application's DAOs, nor does it join remote services into a JDBC atomic transaction. See [Action Runtime](../../dec-java-lite/docs/action-runtime.md) for details.

## 8. Query by business stage

Order querying does not introduce yet another set of status enums; it follows the Directory and Information in the design to find current facts.

`BusinessQueryRuntime` compiles a design once and binds an independent evaluator to each execution. `BusinessQueryNames` maps explicit aliases to existing design IDs/names and does not maintain a separate set of status values.

| Call | Current contract |
| --- | --- |
| `find(View)` | Union of the stages the View belongs to in the current graph |
| `eq(Directory)` | Exact stage; the result directory is the union of its declared cases |
| `start(A)` | A plus the stages forward-reachable from A |
| `start(A).end(B)` | Endpoints included: stages reachable from A that can also reach B; Back excluded |
| `end(B)` | B plus its forward ancestor stages |
| `with(relation)` | Assembles according to declared View relations; nested paths allowed |

`eq` and ranges are mutually exclusive; unknown names, unreachable/reverse ranges, and ambiguous aliases fail explicitly. Overlapping plain stages are deduplicated by root record, and a result classification group hitting multiple cases at once is reported as ambiguous.

Query planning is completed jointly by SQL candidate filtering and Runtime fact evaluation. Candidate pagination, full relation reads, and final pagination are handled separately to protect one-to-many collection integrity. The default candidate cap is 1000; if the cap is reached and the requested page still cannot be produced, the query fails. Not every RuleView read path needed for evaluation can be derived automatically; the application must explicitly load the relevant relations or provide a read adapter.

SQL uses bound parameters. `SqlTranslator`'s table-name mapping only maps logical tables in FROM/JOIN to deployed tables and cannot replace design predicates. Query connections come from declared routes and the application's ConnectionRouter; Spring integration uses transaction-aware connections, and the query Session never commits the outer transaction on its own.

The semantics above query current facts and keep no event history. A single Runner/compiled graph still has a single-root constraint; multi-flow applications should choose graph slices explicitly. See [Query Runtime](../../dec-java-lite/docs/query-runtime.md) for details.

## 9. What the generator produces and what the application owns

The generator provides a stable project skeleton and design index; transaction orchestration, external services, and handwritten adaptation still belong to the application.

`GeneratorPipeline` is the coordinating entry point; its fine-grained responsibilities include:

| Component | Responsibility |
| --- | --- |
| `GenerationContextBuilder` | Document ownership, packaging, and the generation context |
| `DataModelBuilder` | Data/Enum/View models and validation |
| `ApiModelBuilder` | API contracts and grouping |
| `JavaModelSupport` | Shared support such as Java naming and types |
| `DesignProjectionGenerator` | Read-only projections of Information/Business |
| `DesignIndexWriter` | Design ID and code index |
| `GeneratedProjectWriter` | Template writing, path checks, manifest, and stale artifact cleanup |

The generator maintains Controllers, Service **interfaces**, DTOs, Entities, Views, Enums, DAO/Mapper interfaces, configuration, POMs, the design index, and the resource manifest. The application owns ServiceImpls, Workflows, Assemblers, data access implementations, custom Actions/Listeners, and similar integration work.

`.dec-generated` and `dec-generated-manifest.txt` determine the overwrite scope. Managed paths that are safe under the old manifest can be updated/cleaned; handwritten files must not be added to that manifest. Path escapes, symbolic links, and input conflicts are never silently overwritten. To fix a problem in generated code, change the model or template and regenerate.

`Design ID + YAML#path + XML#path` in the code and `DEC-DESIGN-INDEX.md` aid readability; they do not replace the Binding and tests that verify handwritten behavior. See the [generation guide](../../dec-java-lite/docs/how-to-generate.md) for generation configuration usage.
