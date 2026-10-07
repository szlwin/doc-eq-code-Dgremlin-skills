# dec-design

`dec-design` 是规范驱动的 DEC 设计 Skill：把确认后的需求/业务模型/Flow 编写为 **canonical YAML**，完成字段/引用/业务语义校验，再生成兼容 XML。

## 先读什么

```text
SKILL.md
  ↓
references/source-basis.md
  ↓
references/dec-yaml-spec.md       # 轻量规范索引
  ↓
references/yaml-spec/common.md + 当前 kind 分片
  ↓
templates/<kind>.yaml             # 可直接复制
  ↓
references/validation-rules.md    # 跨文件时按需
  ↓
references/xml-compatibility-spec.md  # 仅生成 XML 时
  ↓
references/xml-mapping/common.md + 当前 kind 分片
```

如需自检，可使用本包 `examples/` 中的可选样例；它们不是 skill 的运行时依赖。HTML 生成会产出 Business Directory、DataSource、Data、View、RuleView、API、System、Enum、Overview、每个 System 的聚合设计页，以及按 System 拆分的 Information dependency HTML。人工阅读以 System-first 为主：System → View → RuleView，并在同一 System 页面进入 Enum、API、Information。所有引用均使用相对链接互相跳转。

## 按需读取示例

只修改 Rule：

```text
source-basis.md（首次任务）
dec-yaml-spec.md（索引）
yaml-spec/common.md
yaml-spec/rule.md
templates/rule.yaml
```

只修改 API / Enum：

```text
dec-yaml-spec.md
yaml-spec/common.md
yaml-spec/api.md
yaml-spec/enum.md（仅 relEnum 时）
templates/api.yaml / enum.yaml
```

只修改 Business 且不新增 Information：

```text
dec-yaml-spec.md
yaml-spec/common.md
yaml-spec/business.md
templates/business.yaml
```

只有在生成 XML 时，再加载 `xml-compatibility-spec.md`、`xml-mapping/common.md` 和相同 kind 的 XML 映射分片。


## YAML / XML 必须通过脚本读写

本 Skill 禁止直接读取或手工编辑 `.yaml/.yml/.xml`。

```sh
# 读取 YAML（输出 JSON）
python3 scripts/yaml_io.py read examples/data-order.yaml

# 写 YAML：输入必须是 JSON payload
python3 scripts/yaml_io.py write /tmp/data-order.yaml --from-json /tmp/data-order.json

# 增量修改 YAML
python3 scripts/yaml_io.py merge /tmp/data-order.yaml --patch-json /tmp/patch.json

# XML 读取/导入
python3 scripts/xml_to_yaml.py examples/generated-xml/data-order.xml --stdout

# XML 写入/重建
python3 scripts/yaml_to_xml.py examples/data-order.yaml -o /tmp/data-order.xml

# 用户/外部系统修改 XML 后，同步回已有 canonical YAML
python3 scripts/sync_xml_to_yaml.py changed/data-order.xml \
  --existing examples/data-order.yaml \
  --check
```

YAML 写入后运行 `validate_dec_yaml.py`；XML 永远从 canonical YAML 重新生成。Skill/Agent 不使用 `cat`、文本编辑器、shell 重定向或临时 parser 直接读写 YAML/XML。

## Quick start

```sh
python3 -m pip install -r requirements.txt

# 1. 校验整套 YAML（推荐）
python3 scripts/validate_dec_yaml.py examples

# 2. 验证是否能转换
python3 scripts/yaml_to_xml.py examples --check

# 3. 生成 XML
python3 scripts/yaml_to_xml.py examples -o /tmp/dec-generated-xml

# 4. 已有 XML 首次迁移回 canonical YAML
python3 scripts/xml_to_yaml.py examples/generated-xml -o /tmp/dec-imported-yaml

# 4.5 用户/外部系统修改 XML 后安全同步回已有 YAML
python3 scripts/sync_xml_to_yaml.py changed/order.xml \
  --existing design/order.yaml \
  --in-place \
  --validate-root design \
  --report reports/order-xml-sync.json

# 5. 生成完整 HTML 设计文档站点（自包含 HTML）
# 会生成 Business + Information + DataSource/Data/View/RuleView/API/System/Enum/Overview
# 并生成 system-<name>.html；View/RuleView/API/Enum/Information 从 System 页作为主导航进入
# 同名旧 HTML 会被覆盖；HTML 禁止手工维护
python3 scripts/render_dec_graph.py examples -o /tmp/order-payment.html
```

## 权威关系

```text
Requirement / BM / Flow
        ↓
Canonical YAML       ← Source of Truth
        ↓
Validator
        ↓
YAML → XML Converter
        ↓
Generated XML        ← Runtime/Compiler compatibility artifact

Existing XML --xml_to_yaml.py--> Canonical YAML   ← 首次迁移/导入

Externally edited XML
        ↓ sync_xml_to_yaml.py
Existing Canonical YAML
        ↓ safe semantic merge
Canonical YAML       ← 再次成为唯一 Source of Truth
```

Skill/Agent 不直接编辑 generated XML；如果 XML 是由用户或外部系统修改，必须通过 `sync_xml_to_yaml.py` 安全同步回已有 YAML。同步不是双主维护，成功后仍以 YAML 为唯一权威源。

## 文档依据

规范已经固化在本包的 references、schemas、templates 和 scripts 中，覆盖设计与实现分离、DataSource、Data/View/Rule、Information、Directory、System ownership、API 和 Enum。独立的数据声明语言不属于本 Skill 的 canonical YAML；只有明确的迁移任务才加载其专用规范。

## 独立使用

`dec-design` 不依赖任何研发流程 Skill。输入可以直接是需求、业务模型、流程说明或已有 DEC XML；输出是 canonical YAML、validator 结果和可选 generated XML。其他编排系统如需组合，只通过外部契约读取这些产物，不改变本 Skill 的运行规则。

### System-scoped HTML output

`Data` and `View` both declare their owning `system`. `render_dec_graph.py` generates all System directories under `system/<system>/`, including `system/common/`, with `index.html`, `data.html`, `views.html`, `ruleviews.html`, `apis.html`, `enums.html`, and `information.html`. The `system/` namespace prevents a System named `directory` or another reserved site directory from colliding with generated documentation paths. The System page is the primary navigation entry.

View detail pages render nested Relation objects hierarchically. Scalar property type is resolved from the referenced Data property; Enum is resolved from Data Column `relEnum`; View property `desc` is rendered as the business description. These values are derived rather than duplicated.

The root Directory page lists Business Directory Maps, while each interactive map is generated under `directory/<business>.html`.
