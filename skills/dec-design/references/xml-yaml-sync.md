# 外部 XML 变更安全同步到 Canonical YAML

`scripts/sync_xml_to_yaml.py` 用于一种受控场景：**XML 由用户或外部系统修改后，将 XML 中可表达的变化安全同步回现有 canonical YAML。**

这不是 XML/YAML 双主机制。同步成功后，canonical YAML 仍然是唯一 Source of Truth；后续 XML 继续由 `yaml_to_xml.py` 重新生成。

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

有冲突时返回非 0，并且不写 YAML。

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

现有 YAML Design ID 永远不会被 XML 自动重写。

## 7. Rename 与 `dec-id` 注释

强烈建议所有“可能被外部人工修改”的 XML 使用：

```sh
python3 scripts/yaml_to_xml.py design \
  -o generated/xml \
  --emit-id-comments
```

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

不静默猜测。

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

不会留下半同步 YAML。

## 10. 同步后的强制动作

同步成功后仍必须重新生成 XML：

```sh
python3 scripts/yaml_to_xml.py design --check
python3 scripts/yaml_to_xml.py design -o generated/xml --emit-id-comments
```

如果项目有 DEC Compiler/Runtime 验证，再继续运行真实 Compiler/Runtime test。

最终 authority 永远回到：

```text
Canonical YAML = Source of Truth
Generated XML  = Derived Artifact
```
