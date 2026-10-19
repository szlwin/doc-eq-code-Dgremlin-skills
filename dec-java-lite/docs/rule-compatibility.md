# Rule model and execution compatibility

This implementation follows the original `dec-core-context/config/model/rule` model and the execution responsibilities in `dec-core-model/execute/rule`. It does not import the legacy global registry or the santr/smarter binaries.

| Original model | dec-java-lite |
|---|---|
| RuleDefineInfo: name/type/property/grammer | CompiledRule: name/type/property/process |
| RuleCheckInfo: pattern | CompiledRule.pattern; RuleChecks / RuleExpression |
| RuleCheckData / RuleCheckDataPattern: sql, pattern | CompiledRule.cmd; adapter read followed by Runtime validation |
| RuleExecuteInfo: sql | CompiledRule.cmd; RuleDataAdapter / RuleDataAccess |
| ErrorInfo: code/message/level | CompiledRule.RuleError, retained in failure diagnostics |
| CustomerInfo: Map<String,String> | Immutable flat string metadata in CompiledRule.customer |
| RuleViewInfo: ordered rules | CompiledRuleView; immutable definitions, request-local binding |

Canonical YAML uses `dsl/process/cmd`; XML uses `grammer/customer-process/sql`. Compatible legacy aliases are normalized; conflicting aliases fail. Duplicate Rule names, missing required fields and unsupported expression syntax fail compilation. DataSource resolution remains Action → Rule → RuleView → Business; recognition has no Action override. Changing the invocation route preserves all Rule metadata.

## Executable rules

- `check`: property checks separated by `;`: NOTNULL, NULL, EQUAL, NOTEQUAL, GREATER, GREATEREQUAL, LESS, LESSEQUAL. Binary operands follow `:`; quote strings. Example: `NOTNULL;NOTEQUAL:2`.
- `checkPattern`: field paths/indexes, field-to-field comparisons, numbers/strings/booleans/null, arithmetic `+ - * / %`, comparison `= == != <> < <= > >=`, parentheses, short-circuit `and/or/not` and `&&/||/!`. `exists(path)`, `every(list, predicate)` and `any(list, predicate)` are supported; collection predicates read item fields.
- `checkData`: calls adapter.read with get semantics, then checks each returned object. No rows means checking null. It never treats an existing View property as evidence of a database row.
- `checkDataPattern`: calls adapter.read with query semantics, evaluates every returned row against a temporary overlay of row aliases and the View, and returns FALSE on an empty result. Aliases shadow colliding View fields only during evaluation. The View, produced values and mutations remain unchanged.
- `dsl`: assignments, invocation-local `#variables`, arithmetic, lazy `if ... then ... else ...`, `every(list, field : expression)` and `error("code","message") : predicate` (fails when false). Writes pass through Invocation.write and its access checks. Variables never become model fields or leak to another invocation.
- `get/query/insert/update/delete`: require a DataSource adapter. The generic bridge merges a single get row, replaces query collections, and merges optional insert-generated fields. get rejects multiple rows and returns FALSE on none. Collection insert results must match target cardinality. Updates/deletes use driver completion; affectedRows alone does not invent a business success condition.

RuleViews stop on the first non-TRUE result. Missing expression values produce UNRESOLVED; adapter errors and invalid operations produce ERROR. Configured error code/message/level are retained. Action/Workflow transaction boundaries roll back participating resources and withhold failed model changes; direct low-level RuleView invocation is not an independent transaction boundary.

### Compiled programs and evaluation scopes

`CompiledRule` retains immutable expression/DSL programs at construction; validation no longer compiles and discards them. DataSource rebinding reuses those programs. Variables, models and mutations remain invocation-local, including concurrent execution and nested `every` updates.

| Construct | Unqualified property lookup |
| --- | --- |
| checkPattern / outer DSL | Current View model |
| checkDataPattern | Temporary shallow overlay of the returned row on the View; a row alias shadows the whole same-named top-level View property |
| every/any predicate, DSL every body | Current collection item only; nested every enters the nested item, without implicit fallback to parent/root fields |
| `#variable` | Invocation-local variables, also accessible inside collection scopes |

The row overlay preserves legacy query-alias semantics; collection isolation prevents accidental root-field matches when an item omits a field. These are explicit scopes, not interchangeable evaluation contexts. Use a distinct alias or capture an outer DSL value in `#variable`; do not depend on implicit parent lookup. Overlays never modify the input View. A missing field is UNRESOLVED rather than a guessed fallback.

Standalone grammar words (`and/or/not/if/then/else`, literals and built-in function names) cannot be property operands. Qualified names such as `item.and` are unambiguous. Invalid numbers and out-of-range indexes fail with `ActionExecutionException` at compilation, rather than leaking numeric parsing exceptions.

Information recognition may read external data. Generic get/query then report existence without writing the View; use checkDataPattern to validate returned values. Mutating data rules are prohibited, and DSL writes remain subject to the Information write guard.

## Driver integration

```java
RuleDataAccess driver = request -> {
    // Application integration: translate request.rule().cmd() or property mappings,
    // bind model/payload parameters, and join request.transaction().
    return repository.executeRule(request);
};
var providers = DataSourceAdapterRegistry.from(List.of(
    new DataSourceAdapter("primary", new RuleDataAdapter(driver))));
var selected = runner.selectDataSources(providers, Map.of("data1", "primary"));
// Pass selected to recognizeDirectory / advance / final assertion for this execution.
```

`repository` above is the application's real provider, not a supplied SQL interpreter. Request includes operation, RuleViewKey, full CompiledRule, immutable model/payload snapshots, target() and TransactionScope. Return `Result.read(rows)`, `Result.written(count)` or a Result with generated rows. The driver owns physical access, logical-to-physical mapping, parameter binding and resource enlistment. For collection checkData targets it must verify every target, preserving missing-item semantics; returning only matched rows is insufficient. The bridge does not open connections, commit transactions or make remote writes atomic.

Existing write-only DataOperationAdapter lambdas remain valid for CRUD; their default read method fails explicitly. Implement read or adopt RuleDataAdapter before using checkData/checkDataPattern. Both types now participate in route preflight.

## Compatibility limits and verification

The original test-rule/orm-rule.xml's 14 active rules are imported as fixtures and executed from both YAML and generated XML by RuleCompatibilityTest. Tests also cover update, metadata, generated keys, aliases, missing reads/routes, immutable driver boundaries, rollback and unsupported syntax. The in-memory test driver implements the fixture's commands; it does not demonstrate general SQL translation or production database compatibility.

The legacy express.txt collection filtering, `.sum`, `.compute`, arbitrary functions and additional PropertyCheck validators are not implemented. They fail compilation rather than being approximated in application code. Information and Query predicate languages are unchanged. A real backend driver and its integration tests remain required for physical SQL/ORM/remote access; retaining cmd is not equivalent to executing every legacy command.


## Original Runtime comparison

The sibling original Runtime now accepts dsl as an alias for grammer in its legacy XML/YAML parsers. Its checkData handles an empty get result as null, and checkDataPattern validates independent row overlays without deleting or overwriting model aliases. Its checkDataPattern.property is preserved metadata; SQL still receives the full model parameters.

The original MySQL driver now implements logical single-row INSERT VALUES with View/Data/physical mappings and bound parameters, and model-only collection query through the declared relation key. It supports per-item generated keys, explicit keys, NULL/DEFAULT and bounded BaseData query criteria; unsupported SQL extensions and missing query keys fail explicitly. Four tests on an isolated MySQL 8.4 instance cover both new modes, result/key mapping, order isolation and transaction rollback.

This does not install a MySQL driver in dec-java-lite: RuleDataAccess still needs a real application provider. The original integration results cannot be attributed to lite's in-memory driver or to every historical SQL dialect/CRUD path.
