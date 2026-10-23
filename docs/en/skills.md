# Skill Usage and Maintenance

[Back to documentation home](README.md)

In this repository, a Skill is more like a handoff manual: it tells the Agent which specifications to read, which scripts to use to change the design, how to verify the results, and which work must be left to the Java application. A Skill is not the Java generator, nor a service that executes rules on behalf of the application after deployment.

## 1. What each of the two Skills is responsible for

| | [dec-design](../../skills/dec-design/SKILL.md) | [dec-implementation](../../skills/dec-implementation/SKILL.md) |
| --- | --- | --- |
| Input | Confirmed requirements, business models, processes, or an existing design | Canonical YAML that has passed validation, and the existing project |
| Main work | Modeling, design validation, XML/HTML generation, import and sync | Hooking into the Runtime, implementing adapters, writing tests, maintaining Binding |
| Main output | YAML, XML, HTML, design gaps, and handoff IDs | Project code, tests, implementation bindings, and evidence |
| Not responsible for | Writing business implementation code directly | Changing the business design or hand-writing derived XML on its own |

The two Skills can be used separately. The project does not require an additional general-purpose development Skill to run; this article covers only the DEC-related work boundaries.

## 2. The steps a design change usually goes through

Taking a new pre-payment check in order payment as an example, `dec-design` usually works in the following order:

1. Identify the kinds involved and their direct dependencies, and read the corresponding specifications first instead of loading all the material at once.
2. Read the existing files with the official YAML I/O, keeping the existing Design IDs.
3. Change the design through JSON payload/patch instead of direct text replacement.
4. Validate the whole relevant directory, especially cross-file references and Information/Directory semantics.
5. Regenerate the XML and HTML; if Java code is affected, continue with compilation and run verification.
6. Hand off added or changed Design IDs, Runtime-managed nodes, Actions/adapters that need to be hand-written, test scenarios, and `DESIGN_GAP`.

The entry point for the specifications is [dec-yaml-spec.md](../../skills/dec-design/references/dec-yaml-spec.md), and the templates are in [templates](../../skills/dec-design/templates). XML mapping, sync, and HTML presentation each have their own specification; do not guess the conversion relationships from field names alone.

### Common tools

| Script | What it is good for |
| --- | --- |
| `yaml_io.py read/check/write/merge` | Controlled reading, checking, and updating of YAML |
| `validate_dec_yaml.py` | Checks fields, references, enums, and cross-node semantics |
| `yaml_to_xml.py` | Deterministic XML generation |
| `xml_to_yaml.py` | Builds YAML from XML the first time |
| `sync_xml_to_yaml.py` | Merges external XML changes back into existing YAML |
| `render_dec_graph.py` | Generates the full HTML site |
| `check_manifest.py` | Checks whether the Skill package manifest matches the files |

The scripts are all in [dec-design/scripts](../../skills/dec-design/scripts). For the operational details of XML sync see the [Skill sync specification](../../skills/dec-design/references/xml-yaml-sync.md); for when sync is appropriate and which situations require stopping for human judgment, see the [sync decision guide](xml-to-yaml-sync.md).

For example:

```sh
python3 skills/dec-design/scripts/yaml_io.py read demo/order-payment-yaml/rule-payment.yaml

# The JSON file is prepared by this design task; the path below only illustrates usage.
python3 skills/dec-design/scripts/yaml_io.py write /tmp/rule-new.yaml \
  --from-json /tmp/rule-new.json
python3 skills/dec-design/scripts/yaml_io.py merge /tmp/rule-new.yaml \
  --patch-json /tmp/rule-patch.json
```

Users or external tools editing XML is a supported input scenario, but once back in the repository it should still go through the sync scripts. Do not overwrite canonical YAML with an ad-hoc parser, direct text replacement, or a full reverse conversion.

## 3. What the implementation Skill does after taking over

`dec-implementation` first confirms the design version and scope per the [consumer contract](../../skills/dec-implementation/references/dec-design-consumer-contract.md), then follows the [implementation workflow](../../skills/dec-implementation/references/implementation-workflow.md) to decide what to reuse, modify, or add, and where.

In the order Demo this usually means: generate the project skeleton, fill in `ServiceImpl`, `Workflow`, and model conversions, register DataSources, CustomActions, and Listeners, clarify transaction ownership, write real scenario tests, and then bind every Design ID to generated code, handwritten symbols, or Runtime capabilities.

A business fact that Runtime already executes does not need a duplicated Java decision method. `runtimeManaged: true` only says that Runtime is responsible for executing it; real tests are still required.

| Script | Purpose |
| --- | --- |
| `yaml_io.py` | Reads canonical/Binding, writes Binding in a controlled way |
| `xml_io.py` | Checks and reads derived XML, does not generate XML |
| `validate_binding.py` | Checks coverage, status, files, Java symbols, and evidence |
| `run_binding_verification.py` | Runs tests and generates evidence tied to the current inputs |
| `JavaSymbolIndex.java` | Checks class and method declarations with the Java Compiler AST |

The scripts are in [dec-implementation/scripts](../../skills/dec-implementation/scripts); see the [contract](../../skills/dec-implementation/references/implementation-binding.md) for Binding states, signatures, and evidence formats.

## 4. What configuration cannot change

| Design semantics | Engineering habits that can be adjusted |
| --- | --- |
| YAML is the authoritative source, and Design IDs must stay stable and traceable | How directories and Java packages are organized |
| APIs, enum values, and Information/Directory meanings stay consistent | How Controllers/Services are grouped by resource |
| Do not duplicate Runtime business conditions or the process state machine | Whether Stores use a Factory or another DI approach |
| Transactions, data sources, access control, and failure semantics must be explicit | How implementation classes are concretely organized |
| Completion claims need real symbols, tests, and current evidence | Which development management tools the team uses |

The generator supports a standalone `--generation-config` to adjust API grouping. See [order-payment-generation.properties](../../demo/order-payment-generation.properties) for an example. It changes the file layout, not URLs, business meaning, or method contracts; after adjusting it you still need to migrate handwritten implementations and Binding and refresh the evidence.

## 5. When changing a Skill itself, how to avoid changing only half

First decide whether the change belongs to design syntax, conversion, runtime capability, generation layout, or evidence validation. When adding a field, you usually need to check the specification/schema, I/O, the Validator, XML forward/reverse conversion and the sync projection, HTML, the Java compiler/Runtime, plus the examples and tests — all at the same time.

If you are only adjusting Java file grouping, change the generator or the standalone configuration, not the business YAML; if it is an HTML presentation problem, fix the renderer and regenerate the whole site instead of maintaining a hand-edited page; if you add an external capability, write down the adapter and transaction constraints clearly, and do not write "can be parsed" as "integration completed".

After Skill files change, check the package manifest and run the relevant tests; see the [development and verification manual](development-and-verification.md) for the commands.
