# DEC YAML JSON Schema

这些 Schema 用于辅助编辑器、AI 和 CI 做结构校验；跨文件引用、DataSource、Enum/relEnum、Information DAG、RuleView/View 兼容、Directory/Produce 等语义由 `scripts/validate_dec_yaml.py` 继续检查。

对应关系：

- `config.schema.json`
- `enum.schema.json`
- `data.schema.json`
- `view.schema.json`
- `rule.schema.json`
- `api.schema.json`
- `systems.schema.json`
- `business.schema.json`

YAML 是 JSON 数据模型的超集，因此这些 Draft 2020-12 JSON Schema 可用于 YAML Language Server 等工具。

注意：JSON Schema 负责单文件结构；`relEnum`、RuleView/Rule `dataSource`、RuleView `apiRef`、API expression 等跨文件或条件语义仍以 normative validator 为准。
