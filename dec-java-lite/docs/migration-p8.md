# P8 迁移说明

## 输入配置

1. 把旧 singleton-root YAML 改为 `kind` + `version: dec/v1` + 对应集合（`datas`、`views`、`apis` 等），给实现节点分配稳定 Design ID。
2. Action 使用 `ruleRef` 和 `systemRef`；删除 `ref-rule`/`refRule`/`rule-ref`。Directory 用 `informationRef`，删除旧 `viewRef`；执行边省略 `role`，case 边使用 `role: case`；删除 `predecessor`、`anyOne`、`mutualExclusion`。Change 只引用可物化 Information，不写 SQL 文本。
3. 既有 XML 可导入为 canonical YAML，再由设计源生成 XML；带 `dec-id` 注释能保持原 Design ID。无注释 XML 会合成 ID，必须人工核对。XML 与 YAML 独立目录传给 `--xml` / `--yaml`，不可混为一个设计源。
4. 先运行 `inspect`，再运行 `information`、`action`、`directory`、`query` 和生成器。用 [`fixtures/mix`](../fixtures/mix) 对照各类文档和 Back/case 用法。

## API 与运行时

| 旧入口或约定 | P8 处置 | 新入口 |
| --- | --- | --- |
| `DecApi`、`DecData`、`DecView` 空模型槽位 | 删除 | `DecProject` + `DecDocument` + 稳定 ID |
| `InformationEngine.registerRuleEvaluator(InformationKey, …)` | 删除 | 按 System/View/Rule 三元 key 注册到 `RuleViewRegistry` |
| `ActionPipeline.executeActionsOnly` | 删除未使用路径 | `DirectoryEngine` 在 Session Scope 内逐 Action 执行 |
| 隐式或线程局部 Session | 不进入新运行时 | 显式 `RuntimeSnapshot.openSession()` / `ExecutionSession` |
| 旧 `ActionTransaction` | 仅保留迁移边界，不得与 Session JDBC 资源混用 | `TransactionCoordinator` + `TransactionResource` |
| declaration 第二套 Context/Service/事务/配置 | 整体退役，不迁移、不提供 Adapter | 单一 canonical AST 与运行时 |

`ExecutionSession` 复制传入的 `ModelContext`；提交结果在 `session.model()` 中，原始对象不会被修改。`REQUIRES_NEW` 和多数据源 XA 没有实现契约。外部副作用需显式登记补偿；提交结果不明时会记录 `FAILED` 与 `ROLLBACK_ATTEMPTED`，不可视为已回滚。

## 升级步骤

构建 `./mvnw clean verify`；为当前环境提供 RuleView 数据操作和 Custom Action 注册；用 `RuntimeCatalog` 加载并验证完整设计；按请求创建 Session；使用 `DirectoryEngine`/`QueryRunner` 执行。旧实例保持运行直到其 Session 结束，然后再停用。发布前执行 `mysql-it`、性能及生成漂移门禁。
