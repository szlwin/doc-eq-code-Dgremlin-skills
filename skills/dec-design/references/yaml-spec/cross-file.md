# DEC Canonical YAML：跨文件引用

跨两个及以上 kind 设计、Review 或目录级校验时读取。

```text
Config.dataSources[].name
  ↑ Data.tables[].dataSource
  ↑ RuleView.dataSource
  ↑ Rule.dataSource (override)

Enum.name
  ↑ Data.columns[].relEnum
  ↑ API.request.params[].relEnum
  ↑ API.response.fields[].relEnum

Data.name
  ↑ View.targetMain / relation.data

View.name
  ↑ RuleView.viewRef
  ↑ System.viewRefs
  ↑ API.response.modelRef

API.system + API.name = ApiKey
  ↑ RuleView.apiRef

RuleView.name
  ↑ System.Information.ruleRef
  ↑ Business.Action.ruleRef (+ systemRef)

System.InformationKey = System.name + "." + Information.name
  ↑ Information.expression
  ↑ Directory / Dependency / SubDirectory / Produce / Change
```

规则：

- Rule 的有效 DataSource = Rule override → RuleView default → Runtime/project default。
- RuleView `code` 在设计域内唯一，用于快速查找；`name` 仍是 ruleRef 引用键。
- `relEnum` 必须精确解析 Enum name；绑定公共 Enum 后不得再维护同字段内联 enum values。
- API `system`、RuleView `apiRef`、response `modelRef` 必须精确解析，禁止模糊匹配。
