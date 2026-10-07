# DEC Canonical YAML：View

仅在编写或修改 `kind: view` 时读取。涉及 relation 时同时确认被引用 Data。

# 4. View（业务模型）

对应 `docs/design.md` 与 `docs/architecture.md`。

## 4.1 完整结构

```yaml
kind: view

views:
  - id: VIEW-ORDER-INFO
    name: OrderInfo
    system: order
    targetMain: order
    properties:
      id: id
      userId: userId
      totalAmount: totalAmount

      user:
        relation: one-to-one
        data: user
        key: id
        relKey: userId
        properties:
          id: id
          userName: name

      orderDetailList:
        relation: one-to-many
        data: orderDetail
        key: orderId
        relKey: id
        properties:
          id: id
          orderId: orderId
          productId: productId
```

## 4.2 View 字段

| 字段 | 必填 | 说明 |
|---|---:|---|
| `id` | 推荐 | 稳定 ID |
| `name` | 是 | 业务模型名称 |
| `system` | 是 | View 的归属 System；用于 System → View → RuleView 导航和 ownership |
| `targetMain` | 是 | 主 Data |
| `properties` | 是 | 业务属性，至少一个；Property 可用 `desc` 写业务说明 |
| `class` / `className` | 兼容 | 历史扩展字段，不推荐在新设计中依赖实现类 |

## 4.3 标量 Property

简写：

```yaml
properties:
  customerName: userName
```

含义：业务属性 `customerName` 映射到当前 Data 的 property `userName`。

完整写法：

```yaml
properties:
  customerName:
    ref: userName
    desc: 客户名称
```

## 4.4 Relation Property

当 property 对应一个子业务对象时：

| 字段 | 必填 | 说明 |
|---|---:|---|
| `relation` | 是 | `one-to-one` / `one-to-many` |
| `data` | 是 | 子对象对应 Data |
| `key` | 是 | 子 Data 的关联 key |
| `relKey` | 是 | 上层业务对象关联 property |
| `properties` | 是 | 子对象业务属性 |
| `desc` | 否 | 子对象/Relation 的业务说明 |
| `relValue` | 否 | 兼容现有扩展映射 |

关系语义来自 `docs/design.md`：同样两个 Data 在不同 View 中可以拥有不同业务关系。

## 4.5 约束

- `system` 必须引用已定义 System；
- `targetMain` 必须引用已定义 Data；
- 标量 property 的 ref 必须存在于当前 target Data；
- Property `desc` 是 View 业务语义说明；HTML 中的 Type 必须从被引用 Data property 解析，不能另写一套不一致的类型；
- 如果被引用 Data property 对应 Column 使用 `relEnum`，View HTML 必须链接到该 Enum；
- relation `data` 必须引用已定义 Data；
- relation `key` 必须存在于 relation Data；
- `relKey` 必须存在于父业务对象/父 Data 可解析属性；
- relation property 必须有嵌套 `properties`；
- 同一 View property name 不得重复；
- 不要在 View 中写 Controller/DTO/PO 类名来表达业务语义。

---
