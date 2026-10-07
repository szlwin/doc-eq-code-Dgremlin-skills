## 2026-10-04 - Script-only YAML/XML I/O

- Added `scripts/yaml_io.py` for canonical DEC YAML and implementation-binding YAML read/write.
- Added `scripts/xml_io.py` for read-only inspection/check of derived XML.
- Direct/ad-hoc YAML/XML access is now forbidden by `SKILL.md`.

## 2026-10-04 — API regex/min/max conformance

- Require implementation to preserve API numeric `min/max`, length, explicit `regex`, enum/relEnum and request-level expression constraints from canonical DEC YAML.
- Treat regex as a transport contract fact; implementation must not maintain an independent regex/pattern copy.

## 2026-10-04 — Fully standalone DEC implementation + DataSource/Enum/API contracts

- Remove all external lifecycle-framework coupling; `dec-implementation` can run directly from canonical DEC YAML, Design IDs, project code and tests.
- Implement RuleView default `dataSource` + Rule override semantics without duplicating business rules per storage technology.
- Add Enum/`relEnum` implementation rules so API, persistence columns, serializers and code constants share one DEC Enum fact.
- Strengthen API implementation rules for required, length, enum/relEnum and request-level expression validation.
- Treat RuleView `code/desc` as design metadata that must remain searchable and must not drift during implementation.

## 2026-09-22 — API implementation contract

- Add `api-implementation.md` for endpoint, transport validation, response mapping and RuleView.apiRef implementation rules.
- Treat API Design IDs as first-class implementation/binding nodes.
- Add API drift to conformance review.

## 2026-09-22 — Consume modular DEC specs

- Update dec-design references to use the lightweight spec index plus only the current kind's YAML/XML spec chunks.
- Avoid requiring full DEC design grammar in implementation context.

# Changelog

## 2026-10-05 — Independent input contract and complete binding gate

- Add a generic input contract that rejects HTML recovery artifacts and legacy YAML as implementation specifications.
- Add `--require-complete` to fail binding validation when a canonical Design ID has no disposition.
- Replace sample-project module names in the illustrative binding with neutral service names.

## 0.2.0

- Add an explicit contract for consuming the normative `dec-design` YAML specification rather than guessing semantics from field names.
- Add node-by-node Runtime-vs-Code implementation guidance for DataSource/Data/View/RuleView/System/Information/Business/Directory/Action/Produce/Change/Back.
- Add DataSource SPI implementation guidance based on project architecture/docs.
- Add RuleView/Custom Action/Produce implementation contracts and anti-fallback rules.
- Add Design-ID-first Implementation Plan guidance.
- Fix Binding Design-ID discovery so ordinary Data/View properties such as `id: int` or `id: id` are not mistaken for traceability IDs.
- Expand Binding validator regression tests.

## 0.1.1

- Explicitly remove legacy standalone Directory from implementation scope; only P3 BusinessScope Directory remains supported.

## 0.1.0

- Initial `dec-implementation` skill.
- DEC YAML authority and design-gap boundaries.
- REUSE → COMPATIBLE_EXTEND → MODIFY → CREATE implementation workflow.
- Implementation Binding schema and validator.
- standalone orchestration-neutral conformance review rules.
