# DEC XML → Canonical YAML 工具使用

`scripts/xml_to_yaml.py` 用于把此前没有 canonical YAML 的 DEC XML **首次迁移/导入**为 canonical YAML。转换完成后，YAML 成为新的 Source of Truth；不要把该工具理解为 XML 与 YAML 双主维护机制。

如果已有 canonical YAML，只是用户/外部系统修改了对应 XML，需要把变化同步回 existing YAML，**不要用本脚本直接覆盖 YAML**；改用 `scripts/sync_xml_to_yaml.py`，详见 `references/xml-yaml-sync.md`。

## 1. 单文件转换

```sh
python3 scripts/xml_to_yaml.py path/to/order.xml \
  -o path/to/order.yaml
```

## 2. 目录批量转换

```sh
python3 scripts/xml_to_yaml.py path/to/dec-xml \
  -o path/to/dec-yaml
```

目录层级会保留，扩展名改为 `.yaml`。

## 3. 只检查 XML 是否可转换

```sh
python3 scripts/xml_to_yaml.py path/to/dec-xml --check
```

## 4. 输出到 stdout

```sh
python3 scripts/xml_to_yaml.py order.xml --stdout
```

`--stdout` 只支持单文件输入。

## 5. Design ID 处理

XML Runtime grammar 默认不携带 canonical YAML 的 `id`。工具提供三种策略：

```sh
# 默认：优先恢复 dec-id 注释，没有则生成确定性 ID
python3 scripts/xml_to_yaml.py order.xml -o order.yaml --id-mode auto

# 只恢复 XML 中已有的 dec-id 注释，不生成新 ID
python3 scripts/xml_to_yaml.py order.xml -o order.yaml --id-mode comments

# 完全不输出 Design ID
python3 scripts/xml_to_yaml.py order.xml -o order.yaml --id-mode none
```

推荐使用默认 `auto`。

如果 XML 是这样生成的：

```sh
python3 scripts/yaml_to_xml.py order.yaml \
  -o order.xml \
  --emit-id-comments
```

则反向转换时会优先恢复：

```xml
<!-- dec-id: RV-SAVE-ORDER -->
```

对应 YAML：

```yaml
id: RV-SAVE-ORDER
```

如果旧 XML 没有注释，则按节点语义生成稳定 ID，例如：

```text
Enum       → ENUM-<name>
EnumValue  → ENUM-VALUE-<enum>-<name>
Data       → DATA-<name>
View       → VIEW-<name>
RuleView   → RV-<name>
Rule       → RULE-<ruleView>-<rule>
API        → API-<system>-<api>
System     → SYS-<system>
Information→ INFO-<system>-<information>
Business   → BUS-<business>
Directory  → DIR-<directory>
Action     → ACT-<directory>-<action>
Produce    → PROD-<directory>-<action>-<ref>
```

如发生重名，工具会追加确定性的序号避免当前转换结果内 ID 冲突。迁移后仍应由设计 Review 确认这些 ID 是否符合项目长期命名策略。

## 6. 支持的 XML 根节点

| XML 根 | 输出 kind |
|---|---|
| `<orm-config>` | `config` |
| `<enum-config>` | `enum` |
| `<orm-data-mapping>` | `data` |
| `<orm--data-mapping>` | `data`（仅导入兼容，重新生成时规范化） |
| `<orm-view-mapping>` | `view` |
| `<orm-rule-mapping>` | `rule` |
| `<api-config>` | `api` |
| `<systems>` | `systems` |
| `<business-config>` | `business` |

旧 `<directory-config>` 已移除，转换器会拒绝；Directory 必须迁移到 P3 `business.directories`。

## 7. 历史 XML → Canonical YAML 规范化

反向转换会把 Runtime/历史表达恢复为 canonical YAML：

```text
XML type="grammer" → YAML type: dsl
XML sql="..."      → YAML cmd: ...
XML kebab-case      → YAML camelCase
XML rel-enum         → YAML relEnum
XML RuleView/Rule dataSource → YAML dataSource
```

例如：

```xml
<rule name="calc" type="grammer" sql="...">
  <customer-process><![CDATA[status : 1;]]></customer-process>
</rule>
```

转换为：

```yaml
name: calc
type: dsl
cmd: ...
process: |
  status : 1;
```

## 8. 推荐迁移流程

```text
Existing DEC XML
      ↓
xml_to_yaml.py
      ↓
Canonical YAML
      ↓
validate_dec_yaml.py
      ↓
人工 / AI Design Review
      ↓
yaml_to_xml.py
      ↓
Generated XML
      ↓
Compiler / Runtime validation
```

命令示例：

```sh
python3 scripts/xml_to_yaml.py legacy-xml -o dec/design
python3 scripts/validate_dec_yaml.py dec/design
python3 scripts/yaml_to_xml.py dec/design --check
python3 scripts/yaml_to_xml.py dec/design -o dec/generated/xml
```

## 9. Round-trip 边界

本 Skill 的测试覆盖：

```text
Generated XML
    → XML-to-YAML
    → YAML-to-XML
    → XML semantic comparison
```

对当前支持的 Config/Enum/Data/View/Rule/API/Systems/Business grammar，示例集要求 round-trip 语义一致。

但需要注意：

1. 普通 Runtime XML 中没有的设计元数据无法凭空恢复，例如原始 `id`、`notes`、`description`；
2. `--emit-id-comments` 可以保留 Design ID，但不会恢复其他未写入 XML 的 metadata；
3. XML 导入成功不代表旧 XML 的业务设计本身正确；
4. XML → YAML 是首次迁移能力，**不是把 XML 提升为新的权威源**；已有 YAML 的外部 XML 变更必须使用 `sync_xml_to_yaml.py` 安全合并；
5. 转换后应以 canonical YAML 为准，后续 XML 只由 `yaml_to_xml.py` 派生。

## 10. Fail-closed

工具会拒绝：

- 不支持的 XML 根节点；
- 未知属性或未知结构；
- canonical YAML 所需必填信息缺失；
- 非法 boolean；
- 已移除的 `<directory-config>`；
- 当前 converter 无法无歧义表达的 XML。

发生失败时不要静默丢字段；应修正规范、补 converter 能力或登记 `DESIGN_GAP`。
