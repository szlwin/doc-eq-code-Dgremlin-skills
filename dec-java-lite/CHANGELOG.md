# Changelog

## Unreleased — health-check fixes (2026-10-25)

- Stage generated projects before replacing output, reject paths owned by handwritten files, and restore previous files after a caught commit failure.
- Derive query equality predicates from the Runtime expression AST, preserving literal whitespace, quoted keywords and escaped characters.
- Preserve typed failures and causes through recognition, Actions and workflow results. Add `CONFLICT` and `INFRASTRUCTURE` error codes; keep existing result constructors available.
- Require VERIFIED Binding readiness in CI and refresh the implementation Skill manifest.

## 0.1.0 — P8 release (2026-10-07)

- Add secure DEC XML frontend using the same canonical AST and compilers as YAML; verify full mix and API/Enum semantic parity.
- Reject removed Action, Directory, edge and Change fields; remove unused model slots and runtime adapters.
- Add semantic project digest, immutable published snapshots, frozen registries and atomic runtime replacement.
- Make `fixtures/mix` the single test contract; generate the order-service example from it and check file drift.
- Support nested View projections in generated Java; verify the generated Spring project compiles.
- Add performance, concurrency, security and MySQL release gates, Maven wrapper, source and Javadoc packaging profiles.
- Make generated Service methods fail with a Design ID until application runtime binding is implemented.

## 2026-10-26 质量契约补充

- 增加生成器 dry-run 文件清单及冲突退出码，预览与提交共用所有权检查。
- 增加 Python→XML→Java Runtime/查询语义变体与生成表达式对等回归；修复生成请求表达式数值比较受 Java 包装类型/小数 scale 影响的问题。
- 增加临时生成工程 Spring 启动、费用审批 MySQL 提交/回滚、API 分组迁移及 JAR XML 资源校验。
- 两个 DEC Skill 增加 Agent 级用例和轨迹/产物评分；生产化验收清单明确尚待实现及验证的边界。
