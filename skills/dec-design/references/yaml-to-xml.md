# DEC YAML → XML 工具使用

XML 是 canonical YAML 的派生兼容产物。

## 1. 单文件转换

```sh
python3 scripts/yaml_to_xml.py path/to/order-business.yaml \
  -o path/to/generated/order-business.xml
```

## 2. 目录批量转换

```sh
python3 scripts/yaml_to_xml.py path/to/dec-yaml \
  -o path/to/generated-xml
```

目录层级会保留，扩展名改为 `.xml`。

## 3. 只检查

```sh
python3 scripts/yaml_to_xml.py path/to/dec-yaml --check
```

## 4. 输出到 stdout

```sh
python3 scripts/yaml_to_xml.py order.yaml --stdout
```

## 5. 输出 Design ID 注释

```sh
python3 scripts/yaml_to_xml.py order.yaml \
  -o order.xml \
  --emit-id-comments
```

生成示例：

```xml
<!-- dec-id: DATA-ORDER -->
```

ID 注释仅用于追踪，不改变 Runtime 语义。**如果 XML 将交给用户或外部系统继续修改，并计划将变更同步回 YAML，应使用 `--emit-id-comments`，以便 rename/identity 可以无歧义匹配。**

## 6. Kind → XML 根节点

| kind | YAML 主字段 | XML 根 |
|---|---|---|
| `config` | DataSource/Connection/`*Files` | `<orm-config>` |
| `enum` | `enums` | `<enum-config>` |
| `data` | `datas` | `<orm-data-mapping>` |
| `view` | `views` | `<orm-view-mapping>` |
| `rule` | `ruleViews` | `<orm-rule-mapping>` |
| `systems` | `systems` | `<systems>` |
| `api` | `apis` | `<api-config>` |
| `business` | `business` | `<business-config>` |

P3 Config 特例：

```text
apiFiles      → api-file-info/api-file
enumFiles     → enum-file-info/enum-file
systemFiles   → system-file-info/system-file
businessFiles → business-file-info/business-file
```

不是 `orm-file`。

## 7. Canonical → 历史 Runtime 字段

为了让 YAML 对 AI/人更清晰，同时兼容当前 Runtime：

```text
YAML type: dsl  → XML type="grammer"
YAML cmd        → XML sql="..."
YAML camelCase  → XML kebab-case / 当前历史标签
YAML relEnum    → XML rel-enum
YAML RuleView/Rule dataSource → XML dataSource
```

不要因此在新 YAML 中继续写历史拼写。

## 8. Fail-closed

以下情况直接失败：

- unknown field；
- kind 无法唯一识别；
- 缺必填字段；
- `cmd` 与 legacy `sql` 同时存在且值冲突；
- 旧顶层 `directories:` / `directoryFiles` / `kind: directory`；
- 当前未支持的 legacy declaration shape。

完整字段映射由 `xml-compatibility-spec.md` 索引路由到 `xml-mapping/common.md` 与当前 kind 分片；不要为单一 kind 加载全部 XML 映射。


## 9. 与 XML → YAML 工具配合

历史 XML 首次导入使用 `scripts/xml_to_yaml.py`，详见 `references/xml-to-yaml.md`。

如果 XML 是从现有 YAML 派生后，被用户/外部系统修改，再需要回流到 existing YAML，必须使用 `scripts/sync_xml_to_yaml.py`，详见 `references/xml-yaml-sync.md`。这类 XML 建议始终使用 `--emit-id-comments` 生成。

两种场景都不改变 YAML 的唯一权威地位。
