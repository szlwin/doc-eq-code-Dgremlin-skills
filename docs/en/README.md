# DEC project documentation

This set of documents uses an order-payment demo to explain how a business rule travels from design to a running application. `dec-design` is responsible for writing facts and flows down clearly, `dec-java-lite` for reading and executing them, and `dec-implementation` for recording code and test evidence.

These documents describe the current `doc-eq-code-Dgremlin-skills` repository, not the implementation guide of the neighboring original DEC repository. Unless otherwise noted, commands are run from the repository root.

## Documentation navigation

| Document | Main content |
| --- | --- |
| [Project philosophy](philosophy.md) | Starting from a single order-payment rule, explains how design and implementation divide the work, how changes propagate, and when this approach is worth adopting |
| [Overall architecture](architecture.md) | The path from design to running code, module responsibilities, startup and the request lifecycle, commands and queries |
| [Design model](design-model.md) | The eight YAML categories, Data/View/RuleView, Information, Directory, Action, data sources |
| [YAML, XML and HTML](yaml-xml-html.md) | Responsibilities of the three artifacts, generation paths, reading HTML, the runtime mirror, the initial XML import |
| **[Syncing XML changes back to YAML](xml-to-yaml-sync.md)** | Choosing a sync method, collaboration risks, typical incidents, and the entry point to the authoritative operating procedures |
| [Skill usage and maintenance](skills.md) | Inputs and outputs of the two Skills, spec fragments, scripts, semantic requirements, and engineering conventions |
| [Java framework in depth](java-framework.md) | Compilation and execution, DSL, transactions, listeners, data sources, queries, generators, and extension points |
| [Order-payment example in depth](order-payment-demo.md) | Mapping design to code, the six APIs, success/failure branches, SMS, query examples, and limitations |
| [Development and verification guide](development-and-verification.md) | Environment, generation, running, tests, Binding evidence, change checks, and troubleshooting |

For snapshots of historical checks and fixes, see [History records](../cn/history/README.md).

## If this is your first encounter with this project

- Read the [project philosophy](philosophy.md) first, then follow one complete flow in the [order-payment demo](order-payment-demo.md).
- To understand how the files relate to each other, read the [overall architecture](architecture.md) and the [design model](design-model.md).
- To edit designs, read [Skill usage and maintenance](skills.md) and [YAML, XML and HTML](yaml-xml-html.md).
- To build, run, or verify evidence, go straight to the [development and verification guide](development-and-verification.md).
- When writing back existing XML changes, keep the external original first, then read the [sync guide](xml-to-yaml-sync.md).

## Repository map

```text
skills/
  dec-design/                 Design spec, templates, converters, validators, HTML generator
  dec-implementation/         Implementation contract, Binding, symbol and test evidence validation
dec-java-lite/                Java 21 multi-module framework and CLI
demo/
  order-payment-yaml/         canonical design source
  order-payment-xml/          XML exported for external use
  design-html/               Design documentation site shared by multiple flows
  order-payment-app/          Generated code + handwritten application wiring + tests
  order-payment-implementation-binding.yaml
  order-payment-generation.properties
docs/                        Project documentation for this set
```

The runtime XML is additionally packaged in the demo's `src/main/resources/dec/design/`. It and the exported XML are two copies of the same design. The Java generator maintains the `dec/design-files.txt` resource manifest; the converter maintains the XML content.

## How to judge whether "the design has been implemented"

A completed implementation requires behavior tests that match the current design and source code, plus Binding evidence; even full coverage does not equal release readiness. For the contract between design and implementation, see [project philosophy](philosophy.md#hand-this-contract-to-the-next-person); for how it is carried out, see the [development and verification guide](development-and-verification.md#6-use-binding-to-state-who-implements-what). The order demo's design size, Binding status, and unimplemented items are summarized in the [integration notes](../../demo/INTEGRATION.md).

These documents explain how things are implemented and used; for exact fields and syntax, the [design spec index](../../skills/dec-design/references/dec-yaml-spec.md), the [implementation consumer contract](../../skills/dec-implementation/references/dec-design-consumer-contract.md), and the framework source code remain authoritative. For framework-specific documentation, see the [dec-java-lite documentation entry point](../../dec-java-lite/README.md#文档).
