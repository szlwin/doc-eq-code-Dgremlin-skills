# Canonical DEC 输入

`dec/v1` 包含 `config`、`data`、`view`、`rule`、`api`、`enum`、`systems`、`business` 八种 kind。YAML 文件必须有 `kind` 和 `version`，实现节点使用稳定 Design ID。`DecYamlParser` 与 `DecXmlParser` 都生成 `DecProject`；后续 Information、RuleView、Directory、Query 和生成器共用同一编译器与校验。

YAML 是设计源；需要 XML 时可用 `dec-design` 的转换脚本生成带 `dec-id` 注释的派生 XML。XML 前端也可读取无注释的旧文件，但会按名称合成稳定 ID，建议迁移后审核这些 ID。两个前端均按文件路径排序，混合格式目录需要分别指定输入。`inspect` 输出忽略来源路径与展示文字的语义 digest，完整 mix 的编译对等由 `XmlYamlParityTest` 验证。

YAML 只接受单个 mapping 文档，禁止重复 key、自定义 Java 类型标签和 HTML recovery 字段。XML 禁止 DTD、外部实体、未知根、未知语义属性以及旧 `directory-config`；每个输入文件上限 8 MB。两种前端的解析失败均使用 `VALIDATION` 错误码并标明源文件；语法错误包含行列，结构错误标明相关字段或元素。`DESIGN_GAP` 必须在生成前消除。

旧 singleton-root（`api:`、`data:` 等）、Action `ref-rule`、Directory `viewRef`、`role: predecessor`、`anyOne`、`mutualExclusion` 和旧 Change SQL 不属于 `dec/v1`。见 [迁移说明](migration-p8.md)。
