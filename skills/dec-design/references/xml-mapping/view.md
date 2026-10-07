# DEC YAML → XML：View 映射

仅在转换 `kind: view` 时读取。

# 4. View

## 4.1 根

```yaml
kind: view
```

→ `<orm-view-mapping>`。

## 4.2 标量 property

```yaml
properties:
  customerName: userName
```

→

```xml
<property name="customerName" ref-property="userName"/>
```

## 4.3 Relation property

```yaml
orderDetailList:
  relation: one-to-many
  data: orderDetail
  key: orderId
  relKey: id
  properties:
    id: id
    productId: productId
```

→

```xml
<property name="orderDetailList"
          relation="one-to-many"
          data="orderDetail"
          key="orderId"
          rel-key="id">
  <property name="id" ref-property="id"/>
  <property name="productId" ref-property="productId"/>
</property>
```

| YAML | XML |
|---|---|
| `name` | `view/@name` |
| `system` | `view/@system` |
| `targetMain` | `view/@target-main` |
| scalar property value / `ref` | `property/@ref-property` |
| `relation` | `property/@relation` |
| `data` | `property/@data` |
| `key` | `property/@key` |
| `relKey` | `property/@rel-key` |
| `relValue` | `property/@rel-value` |

---

## 4.4 System ownership

`View.system` → `view/@system`。它表示 View 的归属 System；其他 System 仍可通过 `viewRefs` 引用该 View。


View Property 的 `desc` → `<property desc="...">`。HTML 中 Property Type/Enum 从对应 Data/Column 解析，不复制成第二份设计事实。
