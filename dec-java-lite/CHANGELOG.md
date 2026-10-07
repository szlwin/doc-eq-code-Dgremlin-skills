# Changelog

## 0.1.0 — P8 release (2026-10-07)

- Add secure DEC XML frontend using the same canonical AST and compilers as YAML; verify full mix and API/Enum semantic parity.
- Reject removed Action, Directory, edge and Change fields; remove unused model slots and runtime adapters.
- Add semantic project digest, immutable published snapshots, frozen registries and atomic runtime replacement.
- Make `fixtures/mix` the single test contract; generate the order-service example from it and check file drift.
- Support nested View projections in generated Java; verify the generated Spring project compiles.
- Add performance, concurrency, security and MySQL release gates, Maven wrapper, source and Javadoc packaging profiles.
- Make generated Service methods fail with a Design ID until application runtime binding is implemented.
