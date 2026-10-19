# XML 修改后同步回已有 YAML

[返回文档总览](README.md) · [三种产物的关系](yaml-xml-html.md)

**可以同步。** 本仓库已有 `skills/dec-design/scripts/sync_xml_to_yaml.py`，专门处理用户或外部系统修改 XML 后，将变更合并回现有 canonical YAML 的场景。它保留已有 Design ID 和 YAML 独有元数据，遇到歧义或非法设计会拒绝同步或回滚。

这不是实时双向同步服务。每次调用处理**一个 XML 文件与一个对应 YAML 文件**；成功后 YAML 仍是唯一设计源。Agent 日常修改设计应通过 YAML I/O 脚本完成，不能为了方便先手改生成的 XML。

## 1. 先区分两种任务

| 现状 | 使用工具 |
| --- | --- |
| 只有 XML，首次建立 canonical YAML | `xml_to_yaml.py` |
| 已有 YAML，外部修改了 XML，需要合并回去 | `sync_xml_to_yaml.py --existing` |

同步前保留外部修改过的 XML。**不要先对它运行 YAML→XML 生成**，否则修改内容会被旧 YAML 覆盖。建议把它另存到工作目录外，例如 `/tmp/dec-xml-edit/rule-payment.xml`；下文假定该文件已经由用户或外部工具提供。

## 2. 为外部编辑准备带身份信息的 XML

尚未开始外部编辑时，可以单独导出：

```sh
python3 skills/dec-design/scripts/yaml_to_xml.py demo/order-payment-yaml \
  -o /tmp/dec-xml-edit --emit-id-comments
```

将所需文件交给外部工具。保留其 `dec-id` 注释；这些注释能让同步工具识别重命名前后的同一个实体。订单 Demo 的已生成 XML 本身也带这类注释。

如果 XML 已经修改完成，不要为“补注释”覆盖它；先使用下一步预检查。没有注释并不必然阻止普通字段修改，但实体改名会失去可靠的身份匹配依据。

## 3. 预检查：计算合并，不写 YAML

以修改支付规则说明为例：外部工具在 `rule-payment.xml` 中，将 `pay` RuleView 的 `desc="发起支付"` 改为 `desc="发起支付并返回支付信息"`，其他结构保持不变。

```sh
python3 skills/dec-design/scripts/sync_xml_to_yaml.py \
  /tmp/dec-xml-edit/rule-payment.xml \
  --existing demo/order-payment-yaml/rule-payment.yaml \
  --check \
  --report /tmp/dec-rule-payment-preview.json
```

输出是 JSON，主要字段如下：

| 字段 | 含义 |
| --- | --- |
| `status` | 预检查成功为 `PASSED`，存在语义合并冲突为 `CONFLICT` |
| `updated` | 已有可表达字段的变化及新旧值 |
| `added` | 新增字段或实体 |
| `removedFields` | 被删除的 XML 可表达字段 |
| `removedEntities` | 允许删除时记录的实体删除 |
| `conflicts` | 无法安全合并的路径、原因和错误码 |
| `changeCounts` | 各类变化数量 |
| `preservedYamlOnly` | 记录的顶层 YAML-only 字段；嵌套字段也按合并规则保留 |

**`--check` 的成功只是合并预检查成功。** 它检查合并结果能否通过 Converter 的语法/形状检查，但不会调用整套 `validate_dec_yaml.py` 做跨文件验证；即使传了 `--validate-root`，预检查分支也不会执行写入后的验证。不能把这个结果写成“全部设计和运行时测试通过”。

## 4. 写回并校验整个设计目录

预检查报告符合预期后执行：

```sh
python3 skills/dec-design/scripts/sync_xml_to_yaml.py \
  /tmp/dec-xml-edit/rule-payment.xml \
  --existing demo/order-payment-yaml/rule-payment.yaml \
  --in-place \
  --validate-root demo/order-payment-yaml \
  --report /tmp/dec-rule-payment-sync.json
```

脚本先合并并写入目标 YAML，再调用设计验证器。验证成功才最终保留修改；验证失败会恢复该目标文件原来的内容，报告 `ROLLED_BACK`。若目标是原先不存在的输出文件，失败会删除这个新文件。

随后可使用 `git diff -- demo/order-payment-yaml/rule-payment.yaml` 审阅变化，并用正式读取脚本核对语义：

```sh
python3 skills/dec-design/scripts/yaml_io.py read demo/order-payment-yaml/rule-payment.yaml
```

在上述描述修改的例子中，`desc` 更新，`RV-PAY` 等既有 ID 保留。如果 YAML 中存在 XML 无法表达的 `notes` 等元数据，也会保留。输出可能经过规范化排版，不保证原 YAML 文本注释、引号风格或空行原样保留。

## 5. 同步算法究竟保留什么

```text
已有完整 YAML ──正式转换器──> XML ──正式反向转换──> 旧的可表达投影
外部修改 XML ──正式反向转换──────────────────────> 新的可表达投影
                           ↓
             按身份/语义键比较两个投影
                           ↓
          将可表达字段变化合并到已有完整 YAML
                           ↓
              写入、校验，失败恢复目标文件
```

它不是简单的 XML→YAML 覆盖。已有 ID 不会被 XML 自动重写，XML 表达不了的字段不会因为“不在 XML 里”就被删除。

但它也**不是带历史共同基线的三方合并**：旧投影来自同步时的现有 YAML，而不是外部编辑开始时的版本。如果外部编辑期间 YAML 也改了同一字段，脚本不能可靠判断哪一边是后续修改；外部 XML 的旧值可能覆盖新的 YAML 值。实际协作时应固定交付版本、查看 Git 差异并审阅 `updated`，不要仅凭 `PASSED` 判断并发编辑没有冲突。

## 6. 重命名和删除

### 重命名

有 `<!-- dec-id: RV-PAY -->` 时，修改 `name` 可以按同一 ID 匹配原实体。没有注释且语义键也发生变化时，工具默认不猜测“这是改名还是删旧建新”，会报告 `AMBIGUOUS_RENAME_OR_REPLACEMENT`。

身份可匹配不代表整个项目引用会自动重构。更改 RuleView 名称后，其他文件中的 `ruleRef` 也要协调更新；否则目录级校验可能失败并触发回滚。

### 删除

删除 XML 可表达的普通字段会进入 `removedFields`。删除整个实体默认冲突，报告 `ENTITY_DELETE_REQUIRES_ALLOW_DELETE`。

确实打算删除该实体，并已处理引用时，先以 `--check --allow-delete` 预览，再写回：

```sh
python3 skills/dec-design/scripts/sync_xml_to_yaml.py \
  /tmp/dec-xml-edit/rule-payment.xml \
  --existing demo/order-payment-yaml/rule-payment.yaml \
  --in-place --allow-delete \
  --validate-root demo/order-payment-yaml \
  --report /tmp/dec-rule-payment-delete.json
```

`--allow-delete` 只解除实体删除保护，不会绕过设计校验或修复悬空引用。

## 7. 多文件修改与临时输出

`--output/-o` 可替代 `--in-place`，二者不能同时使用。若要审阅完整结果，建议先复制整个 canonical 设计目录到隔离工作区，再以副本为 `--existing` 和 `--validate-root` 同步。不要把同 ID 的候选 YAML 另存到原设计目录，导致目录级校验读到重复定义。

当前 CLI 没有跨多个文件的一次性原子事务。一个调用成功后，另一个调用失败，不会回滚前一个文件。涉及跨文件改名或相互依赖的结构变更，应在隔离分支/完整副本中协调变更、校验整体，再交付整个变更集；不得把单文件的回滚保障描述成全项目原子同步。

## 8. 同步成功后的闭环

先保留同步报告和外部原件，再从新的 YAML 重建派生产物：

```sh
python3 skills/dec-design/scripts/validate_dec_yaml.py demo/order-payment-yaml
python3 skills/dec-design/scripts/yaml_to_xml.py demo/order-payment-yaml --check
python3 skills/dec-design/scripts/yaml_to_xml.py demo/order-payment-yaml \
  -o demo/order-payment-xml --emit-id-comments
python3 skills/dec-design/scripts/yaml_to_xml.py demo/order-payment-yaml \
  -o demo/order-payment-app/src/main/resources/dec/design --emit-id-comments
python3 skills/dec-design/scripts/render_dec_graph.py demo/order-payment-yaml -o demo/design-html
```

如果影响 Java 类型、API、Information/Flow 投影或设计索引，再运行 Java 生成器；调整手写适配、Binding 和相关测试。应用使用打包设计，部署时还要重新打包并重建 Runtime。统一流程见 [开发与验证手册](development-and-verification.md)。

## 9. 错误定位

| 现象 | 处理 |
| --- | --- |
| `KIND_MISMATCH` | 核实 XML 与 `--existing` 是否为同类、同一份设计 |
| `AMBIGUOUS_RENAME_OR_REPLACEMENT` | 恢复外部编辑时的身份注释，或人工核对后通过 canonical YAML 正式变更 |
| `ENTITY_DELETE_REQUIRES_ALLOW_DELETE` | 核实是否误删；明确需要删除时使用专门参数 |
| `MERGED_DOCUMENT_INVALID` | 修正 XML 可表达的结构/字段，不要强行覆盖 YAML |
| `ROLLED_BACK` | 阅读报告 `validation.output`，修复引用、重复 ID 或其他设计问题 |
| `ERROR` / 非零退出 | 检查路径、参数及语法；不要继续生成/发布来掩盖失败 |

正常预检查/写入退出码为 0；合并冲突为 3，写入后验证失败并回滚为 4，输入或其他异常为 2。`--report` 可持久保存结构化同步报告；异常路径也应保留标准错误输出。

实现依据：[同步脚本](../skills/dec-design/scripts/sync_xml_to_yaml.py)、[同步测试](../skills/dec-design/tests/test_sync_xml_to_yaml.py)、[Skill 同步契约](../skills/dec-design/references/xml-yaml-sync.md)。
