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

XML 属性本身以文本传输，但 `enum-value/@value` 的语义仍是 Java `Integer`：必须是无小数的十进制整数，且落在 `-2147483648..2147483647` 范围内。转换器必须拒绝带引号的 YAML 数字、浮点数、布尔值、空值和越界值；不能把枚举值当作枚举常量名或任意字符串。

`id` 默认只存在 canonical YAML；Design ID 注释的生成策略统一见 [`yaml-to-xml.md`](../yaml-to-xml.md)。

引用方：

```yaml
relEnum: OrderStatus
```

→ XML：

```xml
rel-enum="OrderStatus"
```
