# DEC YAML → XML：Enum

`kind: enum`：

```yaml
enums:
  - name: OrderStatus
    desc: 订单状态
    values:
      - value: 1
        name: WAIT_PAY
        desc: 待支付
```

→

```xml
<enum-config>
  <enum name="OrderStatus" desc="订单状态">
    <enum-value value="1" name="WAIT_PAY" desc="待支付"/>
  </enum>
</enum-config>
```

`id` 默认只存在 canonical YAML；使用 `--emit-id-comments` 时以 `dec-id` XML comment 保留。

引用方：

```yaml
relEnum: OrderStatus
```

→ XML：

```xml
rel-enum="OrderStatus"
```
