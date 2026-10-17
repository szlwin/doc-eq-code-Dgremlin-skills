# DEC YAML → XML：RuleView / Rule

## RuleView

```yaml
- name: save-Order
  code: SAVE_ORDER
  desc: 保存订单
  viewRef: OrderInfo
  apiRef: order.submitOrder
  dataSource: data1
```

→

```xml
<rule-view-info
    name="save-Order"
    code="SAVE_ORDER"
    desc="保存订单"
    view-ref="OrderInfo"
    api-ref="order.submitOrder"
    dataSource="data1">
```

`apiRef`、`dataSource` 可选；`code` 为 canonical 必填。按用户定义，RuleView/Rule 的 XML DataSource 属性名使用 `dataSource`。

## Rule

| YAML | XML |
|---|---|
| `name` | `@name` |
| `type: dsl` | `@type="grammer"` |
| 其他 `type` | `@type` 原值 |
| `dataSource` | `@dataSource`，覆盖 RuleView 默认值 |
| `property` | `@property` |
| `pattern` | `@pattern` |
| `cmd` | `@sql` |
| `error` | `error-info` |
| `process` | `customer-process` CDATA |
| `customer` | `customer-info` 的字符串属性 |

XML 仅写声明值，不重复写继承值；运行优先级为 Action → Rule → RuleView → Business。Information 识别没有 Action 覆盖。

导入 `grammer/sql` 为 canonical `dsl/cmd`。兼容输入同时出现 `cmd/sql` 或 `process/grammer` 时，值冲突必须失败。`customer` 只允许平铺字符串属性，不能丢失嵌套结构后仍声称无损转换。
