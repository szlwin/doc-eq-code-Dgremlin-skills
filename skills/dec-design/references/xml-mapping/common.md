# DEC YAML → XML：通用映射规则

所有 XML 生成任务的通用规则；仅在需要生成或审查 XML 时读取。

## 1. 通用规则

- XML declaration：`<?xml version="1.0" encoding="UTF-8"?>`；
- YAML camelCase 映射到现有 XML 的 kebab-case / 历史标签；
- YAML `id/description/notes/version/kind` 默认不写成 Runtime 属性；
- `desc` 是目标设计语言字段，不等同于 metadata `description`；在 API、RuleView、Business 等支持位置会生成 XML `desc` 属性；
- `--emit-id-comments` 可输出 `<!-- dec-id: ... -->`；
- `expression/ruleData` 作为 XML attribute；`changeData/process` 使用 CDATA；
- unknown field 必须失败；
- `ref-rule` 不生成；目标属性统一 `rule-ref`。

---

# 8. 设计元数据

以下字段默认不进入 Runtime XML：

```text
kind
version
id
description
notes
metadata
```

它们是设计/AI/Review/traceability 层信息。

---

# 9. Converter Fail-closed 条件

Converter 必须拒绝：

- 一个文件无法确定唯一 kind；
- unknown semantic field；
- 必填字段缺失；
- `cmd` 与 legacy `sql` 值冲突；
- 旧顶层 `directories:`；
- `kind: directory`；
- Config 中旧 `directoryFiles`；
- 当前明确不支持的 legacy declaration shape；
- 结构类型不正确。

Converter 成功只证明“可以无歧义映射 XML”，**不等于 Compiler/Runtime 语义验证成功**。
