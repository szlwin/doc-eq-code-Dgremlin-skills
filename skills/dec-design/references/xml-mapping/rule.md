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

未配置 Rule `dataSource` 时由 Runtime 使用 RuleView `dataSource`；XML 不重复写继承值。
