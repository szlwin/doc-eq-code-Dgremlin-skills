# P5 Directory Runtime

The `dec-lite-directory` module compiles canonical `business.directories` into a typed graph. Ordinary execution edges point from child to parent; result case edges point from the result Directory to each case; Back edges are separate and must correspond to an adjacent ordinary execution edge.

`DirectoryGraphCompiler` checks one root, unique names, known references, cycles, reachability, single execution parent, result case ownership, and forbidden `predecessor` roles. `compileReady` additionally checks that every Custom Action has a registered implementation. `PathPlanner` returns a unique explainable path. `BackPlanner` follows a case back to its result owner, then traverses explicit Back edges one level at a time.

`DirectoryEngine.executeTo(target, context)` uses one working model and one caller supplied transaction for the entire path. Each Directory checks dependencies, runs actions in YAML order, applies its declared Information Change, checks its own Information, and classifies result cases. Exactly one case must be `TRUE`; `UNRESOLVED`, `ERROR`, zero matches, and multiple matches fail. The working model is published only after the transaction commits. On failure the transaction rolls back and the caller's model and current Directory stay unchanged.

`DirectoryEngine.backTo(target, context)` runs each Back edge's ordered actions and restores the target Directory Information before committing. It does not jump over intermediate directories.

The trace records the path, dependency checks, action results and evidence, Produce verification, Change, Information checks, case classification, Back, transaction completion, and failures. The module does not implement SQL queries or database transaction management; callers supply an `ActionTransaction` and external `DataOperationAdapter`. The adapter receives the same transaction through `RuleViewRegistry.Invocation.transaction()`.

Inspect a canonical project with:

```sh
dec-lite directory --yaml path/to/canonical-dec --target success
```
