# DEC Design & Implementation Toolkit

English (current) · [简体中文](README.zh-CN.md)

## What this project is

DEC captures the data, business conditions, and process flow of a business system in one structured design (the canonical YAML), and lets that single design source drive four things at once:

- **Validation**: static checks on fields, references, and cross-node semantics surface design errors before any code is generated;
- **Generation**: deterministic derivation of XML (for external exchange and runtime loading), an HTML review site, and a Java project skeleton;
- **Execution**: the `dec-java-lite` Runtime compiles and executes rules and flows from the design, so business conditions are no longer re-implemented in every endpoint;
- **Traceability**: the Implementation Binding records which code, tests, and runtime capability own each design node, linked to test evidence, preventing claims from running ahead of the implementation.

The order-payment demo in this repository walks the whole chain: starting from 10 canonical YAML documents, it generates XML/HTML and a Spring Boot/MyBatis project skeleton, then wires handwritten adapters and runs submission, payment, success/error branches, rollback, and business-stage queries against a real MySQL database.

## Core concepts

| Concept | In one sentence |
| --- | --- |
| canonical YAML | The single authoritative design source; XML, HTML, and generated code are all derived from it, never maintained in reverse |
| Information | A business fact such as "this order is payable" that holds or not right now, reusable by flows and queries |
| RuleView | The concrete check, read, or write rules organized around one View |
| Directory | Describes how process stages, branches (case), and rollbacks (Back) advance |
| Design ID | The stable identity of a design node, carried through generated-code comments, Binding, tests, and docs |
| Binding | The mapping from design nodes to implementation symbols and test evidence — the basis for judging "this design is implemented" |

## Repository layout

```text
skills/
  dec-design/                 Design specs, templates, YAML/XML converters, validators, HTML generator
  dec-implementation/         Implementation contracts, Binding and test-evidence verification
dec-java-lite/                Java 21 multi-module framework: parsing, compilation, Runtime, query, generator, CLI
demo/
  order-payment-yaml/         Canonical design source (10 YAML documents)
  order-payment-xml/          Exported XML
  design-html/                Design review site (HTML)
  order-payment-app/          Generated skeleton + handwritten wiring + tests
docs/
  cn/                         Project docs in Chinese (including history records)
  en/                         Project docs in English
```

## Two Skills, one framework, one demo

- [dec-design](skills/dec-design/SKILL.md): generates canonical DEC YAML from requirements and derives XML/HTML; handles initial import and controlled sync.
- [dec-implementation](skills/dec-implementation/SKILL.md): wires the Runtime, implements adapters, writes tests, and maintains Binding evidence against validated YAML.
- [dec-java-lite](dec-java-lite/README.md): Java 21 parsing, compilation, runtime, business-stage query, and code generation for `dec/v1`.
- [Order-payment demo](demo/README.md): independently verifies the full loop of YAML → XML/HTML → Java generation and Binding.

## Quick start

```sh
# 1. Prepare the environment (Python 3 + JDK 21) and install the Skill dependencies
python3 -m pip install -r skills/dec-design/requirements.txt \
  -r skills/dec-implementation/requirements.txt

# 2. Validate the demo design (regeneration and drift comparison are covered in the guide below)
python3 skills/dec-design/scripts/validate_dec_yaml.py demo/order-payment-yaml

# 3. Build the framework and run all tests (demo tests need your own dedicated MySQL)
./dec-java-lite/mvnw -f dec-java-lite/pom.xml install
```

Full environment setup, generation, execution, and evidence checks are in the [development and verification guide](docs/en/development-and-verification.md).

## Current status

- Order-payment demo: 118 Design IDs fully covered, all `VERIFIED`, `--require-ready` passes; the payment platform, MongoDB, and SMS are still local stubs.
- CI ([verify.yaml](.github/workflows/verify.yaml)): both Skill manifests and test suites, canonical/binding validation, XML/HTML drift checks, real-MySQL behavior tests, and Binding evidence generation with archiving.
- For the `dec-java-lite` `v0.1.0` release and its gates, see the [release notes](dec-java-lite/docs/release-p8.md); for the real wiring status, see [integration verification](demo/INTEGRATION.md).

## Further documentation

- [Documentation home](docs/en/README.md): philosophy, overall architecture, Skills, the Java framework, the worked example, and development & verification; [syncing XML changes back to YAML](docs/en/xml-to-yaml-sync.md). Chinese versions live under [docs/cn](docs/cn/README.md).
- Snapshots of past health checks and fixes are in the [history records](docs/cn/history/README.md) (Chinese only).
