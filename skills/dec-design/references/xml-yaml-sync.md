# 外部 XML 变更安全同步到 Canonical YAML

`scripts/sync_xml_to_yaml.py` 用于一种受控场景：**XML 由用户或外部系统修改后，将 XML 中可表达的变化安全同步回现有 canonical YAML。**

这不是 XML/YAML 双主机制。同步成功后，canonical YAML 仍然是唯一 Source of Truth；后续 XML 继续由 `yaml_to_xml.py` 重新生成。

外部 XML 在构建 DOM 前限制为 8 MB，并通过 XML 解析事件拒绝 DTD、ENTITY 和外部实体声明（包括 UTF-16 输入）；`xml_to_yaml.py` 与本同步入口共用该保护。实现 Skill 的 XML 检查/读取入口使用同一份独立打包的保护模块。

本文是同步操作的权威规范；命令从 `skills/dec-design/` 目录执行，示例输入输出路径按实际工程调整。

## 1. 适用场景

适用：

- 用户在外部工具中修改 DEC XML；
- 第三方系统产出或修改 DEC XML；
- 需要保留现有 YAML 中的 Design ID、`notes` 或其他 XML 无法表达的设计元数据；
- 需要把 XML 变更安全合并，而不是直接覆盖现有 YAML。

不适用：

- Skill/Agent 自己为了方便直接编辑 generated XML；
- 希望建立 XML/YAML 双向实时双主；
- XML 中存在当前 DEC grammar 无法表达的未知结构。

## 2. 默认安全流程

```text
Externally edited XML
        ↓
sync_xml_to_yaml.py
        ↓
Existing YAML → XML-representable projection
        ↓
Semantic diff / merge
        ↓
Preserve YAML-only metadata
        ↓
validate_dec_yaml.py
        ↓
Canonical YAML
        ↓
yaml_to_xml.py
        ↓
Regenerated XML
```

## 3. 预检查，不写文件

```sh
python3 scripts/sync_xml_to_yaml.py changed/order.xml \
  --existing design/order.yaml \
  --check
```

输出 JSON 同步报告，包括：

- `updated`
- `added`
- `removedFields`
- `removedEntities`
- `conflicts`
- `changeCounts`
- `preservedYamlOnly`（记录顶层 YAML-only 字段；嵌套元数据也按合并规则保留）

有冲突时返回非 0，并且不写 YAML。`--check` 仅完成语义合并和 Converter 语法/形状检查，不执行目录级验证；即使传入 `--validate-root`，此分支也不会调用 `validate_dec_yaml.py`。

## 4. 同步到原 YAML

```sh
python3 scripts/sync_xml_to_yaml.py changed/order.xml \
  --existing design/order.yaml \
  --in-place \
  --validate-root design
```

也可以显式指定目标：

```sh
python3 scripts/sync_xml_to_yaml.py changed/order.xml \
  --existing design/order.yaml \
  -o design/order.yaml \
  --validate-root design
```

## 5. 输出同步报告

```sh
python3 scripts/sync_xml_to_yaml.py changed/order.xml \
  --existing design/order.yaml \
  --in-place \
  --validate-root design \
  --report reports/order-xml-sync.json
```

报告使用 JSON，避免再引入一份需要维护的 YAML 合同。

## 6. YAML-only metadata 的保护

脚本先把 existing YAML 通过正式 Converter 投影成 XML 再反向恢复，从而识别“XML 能表达的字段”。

例如已有 YAML：

```yaml
id: RV-SAVE-ORDER
name: saveOrder
desc: 保存订单
notes: 该规则必须在支付前完成
```

XML 只能表达：

```xml
<rule-view-info name="saveOrder" desc="保存订单" ...>
```

用户把 XML `desc` 修改为“保存并校验订单”后同步：

```yaml
id: RV-SAVE-ORDER                 # 保留
name: saveOrder
desc: 保存并校验订单              # 从 XML 更新
notes: 该规则必须在支付前完成      # XML 无法表达，保留
```

现有 YAML Design ID 永远不会被 XML 自动重写。语义元数据保留不等于文本原样保留：输出会规范化排版，不保证 YAML 注释、引号风格和空行不变。

同步不是三方合并：旧投影来自同步时的现有 YAML，没有外部编辑开始时的历史共同基线。若两边同时修改同一字段，外部 XML 的旧值可能覆盖新的 YAML 值；需固定交付版本并审阅差异。

## 7. Rename 与 `dec-id` 注释

Design ID 注释的适用边界和快照规则统一见 [`yaml-to-xml.md`](yaml-to-xml.md)；本节只说明同步时如何使用已有注释进行身份匹配。

需要外部编辑的 XML，应按 [`yaml-to-xml.md`](yaml-to-xml.md) 的外部编辑策略生成。

例如：

```xml
<!-- dec-id: RV-SAVE-ORDER -->
<rule-view-info name="saveOrder" ...>
```

此时把 `name` 改为 `saveOrderV2`，脚本可以根据 Design ID 确认这是 rename，而不是“删除旧节点 + 新增另一个节点”。

如果 XML 没有 `dec-id` 注释，且 name 等语义键也变化，脚本会 fail closed：

```text
AMBIGUOUS_RENAME_OR_REPLACEMENT
```

不静默猜测。身份匹配不会自动更新其他文件的引用；跨文件改名需协调修改并通过完整设计校验。

## 8. 删除保护

XML 中删除整个实体默认不会直接删除 YAML 节点，而是产生冲突：

```text
ENTITY_DELETE_REQUIRES_ALLOW_DELETE
```

确认确实需要删除时：

```sh
python3 scripts/sync_xml_to_yaml.py changed/order.xml \
  --existing design/order.yaml \
  --in-place \
  --allow-delete \
  --validate-root design
```

标量属性/可表达字段的删除是明确的，会正常同步；“整个实体删除”需要显式授权。

## 9. 事务与回滚

写入后脚本自动运行：

```text
validate_dec_yaml.py
```

如果验证失败：

```text
write merged YAML
    ↓
validation FAILED
    ↓
rollback original YAML
    ↓
status = ROLLED_BACK
```

此回滚仅覆盖本次目标文件：恢复原文件内容，或删除原先不存在的新输出文件。它不是跨文件事务，不会回滚此前成功的其他调用。目录校验默认检查目标文件父目录，也可通过 `--validate-root` 指定完整设计根目录。

`--output/-o` 与 `--in-place` 不能同时使用。需要审阅候选结果时，使用隔离的完整设计副本，不要把同 ID 的候选 YAML 放入原 canonical 目录。

退出码：0 表示预检查或写回成功，3 表示合并冲突，4 表示写入后校验失败并回滚，2 表示输入或其他异常。异常路径还需保留标准错误输出，不能只依赖 JSON 报告。

## 10. 同步后的强制动作

同步成功后仍必须重新生成 XML：

```sh
python3 scripts/yaml_to_xml.py design --check
python3 scripts/yaml_to_xml.py design -o generated/xml
```

若生成结果还要再次交给外部编辑，按 [`yaml-to-xml.md`](yaml-to-xml.md) 的外部编辑策略生成带身份注释的 XML。

如果项目有 DEC Compiler/Runtime 验证，再继续运行真实 Compiler/Runtime test。

最终 authority 永远回到：

```text
Canonical YAML = Source of Truth
Generated XML  = Derived Artifact
```
