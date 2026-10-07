# DEC YAML → XML：Data

`kind: data` → `<orm-data-mapping>`。

Property：

```yaml
createDate: {type: date, desc: 创建时间}
```

→ `<property name="createDate" type="date" desc="创建时间"/>`。

Table / Column：

```yaml
tables:
  - name: order_info
    dataSource: data1
    key: o_id
    keyType: increment
    columns:
      o_status:
        ref: orderStatus
        type: int
        relEnum: OrderStatus
```

→

```xml
<table name="order_info" data-source="data1" key="o_id" key-type="increment">
  <column name="o_status" ref-property="orderStatus" type="int" rel-enum="OrderStatus"/>
</table>
```

旧 `<orm--data-mapping>` 仅 XML→YAML 导入兼容；重新生成统一使用 `<orm-data-mapping>`。


`Data.system` → `<data system="...">`，新设计必填。
