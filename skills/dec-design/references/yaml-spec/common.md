# DEC Canonical YAML：通用约定

适用于所有 DEC YAML。只在开始设计、跨 kind 修改或需要确认全局约束时读取。

## 1. 通用约定

### 1.1 文件类别

一个 YAML 文件只承载一种主类别：

| kind | 顶层字段 | 作用 |
|---|---|---|
| `config` | `dataSourceInfo` / `dataSources` / `connections` / `*Files` | DataSource、Connection、设计文件入口 |
| `data` | `datas` | 数据模型与 DataSource 映射 |
| `view` | `views` | 业务模型及对象关系 |
| `rule` | `ruleViews` | RuleView 与 Rule |
| `api` | `apis` | API 接口契约、参数校验与返回值 |
| `systems` | `systems` | System、Information、ModelAccess |
| `business` | `business` | BusinessScope 与 P3 Directory 编排 |

推荐显式写 `kind`，避免一个文件同时出现多个可识别顶层字段时产生歧义。

```yaml
kind: data
version: dec/v1
```

`kind` 和 `version` 是设计元数据，不写入 Runtime XML。

### 1.2 字段命名

Canonical YAML 使用 camelCase：

```yaml
viewRef: OrderInfo
informationRef: order.ordered
isRoot: true
modelRef: OrderInfo
```

Converter 负责转换成 XML 历史命名：

```xml
view-ref="OrderInfo"
information-ref="order.ordered"
is-root="true"
model-ref="OrderInfo"
```

### 1.3 稳定设计 ID

需要实现、Review、测试、影响分析或 Binding 的节点应有稳定 `id`：

```yaml
id: INFO-ORDER-ORDERED
```

要求：

- 全设计域唯一；
- 业务语义不变时不要因为 `name` 重命名而换 ID；
- 一个旧语义拆成两个新语义时创建两个新 ID；
- 删除语义后 ID 不应复用给其他语义；
- `id` 默认只用于设计追踪，不输出为 XML 属性。

### 1.4 设计元数据

允许节点使用：

```yaml
id: ...
description: ...
notes: ...
```

这些字段不进入 Runtime XML。

### 1.5 多行表达式

Rule DSL、Information expression、ruleData、changeData 使用 block scalar：

```yaml
expression: |
  order.ordered
  or
  order.waitPay
```

不要把复杂表达式压成难读的一行。

### 1.6 大小写与引用

- name 和 ref 区分大小写；
- 引用必须精确解析；
- 禁止 fuzzy match、同名猜测、找不到后静默降级；
- 未知字段必须失败，不得被 Converter 静默丢弃。

---

# 16. AI 编写 YAML 的强制步骤

AI 不允许只根据一个示例猜字段。必须按以下步骤：

1. 确定需要修改的 YAML kind；
2. 读取本规范对应章节；
3. 读取 `templates/<kind>.yaml`；
4. 查看 `examples/` 中同类完整示例；
5. 检索现有项目同类 YAML，优先增量修改；
6. 编写 YAML；
7. 运行 `validate_dec_yaml.py`；
8. 运行 `yaml_to_xml.py --check`；
9. 生成 XML；
10. 若项目有 Compiler/Runtime 校验，运行真实校验；
11. 不通过时修 YAML，不手改生成 XML。

---

# 17. 禁止事项

- 禁止生成旧顶层 `directories:`；
- 禁止生成 XML `directory-config`；
- 禁止使用 `ref-rule`；统一 `ruleRef`→XML `rule-ref`；
- 禁止 Information 同时出现多个 recognizer；
- 禁止 `ruleRef` 引用内部 Rule name；
- 禁止 Dependency 写字段表达式代替 Information；
- 禁止把复合 Information 当作单一 Produce 的直接后置条件；
- 禁止未知字段静默透传；
- 禁止把 Java class/method 作为业务设计本体；
- 禁止把 XML 作为人工维护的第二权威源。
