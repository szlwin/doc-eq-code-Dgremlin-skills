# P4 Action Runtime

`dec-lite-action` implements the P4 execution boundary:

- `BusinessActionParser` reads canonical `business.directories[].actions`.
- `RuleViewCompiler` binds RuleView files to their owning System through `systems[].ruleFiles`.
- `RuleViewActionInvoker` dispatches by `(system, ruleRef)` and resolves the underlying `RuleViewKey` only when unambiguous.
- `ActionRuntime` runs preconditions, invokes a RuleView or a registered Custom Action, collects `MutationSet`, verifies Produce, and commits model changes only after verification succeeds.
- `ActionPipeline` runs ordered actions fail-fast and verifies the directory Information after a directory's actions finish.

`RuleViewInterpreter` supports canonical `check`, `checkPattern`, `checkData`, `checkDataPattern` and `dsl` rules. `insert`, `update`, `delete`, `get` and `query` require an explicit `DataOperationAdapter`; missing adapters return `ERROR` and never silently succeed.

Custom Actions are registered on an instance of `CustomActionRegistry`. There is no global registry and a missing custom Action never falls back to a RuleView.

Produce verification requires declared values and, when `informationRef` is present, requires that Information to evaluate to `TRUE`. A failed Produce check leaves the candidate model uncommitted.

The current module does not implement Directory path planning or database adapters. Those are later runtime concerns; P4 only fixes their typed execution boundary.
