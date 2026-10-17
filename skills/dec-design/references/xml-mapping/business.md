# DEC YAML → XML：Business / P3 Directory 映射

仅在转换 `kind: business` 时读取。

# 7. Business / P3 Directory

## 7.1 根

```yaml
business:
  name: order-payment
  desc: 订单支付业务流程
  directories: ...
```

→

```xml
<business-config name="order-payment" desc="订单支付业务流程">
  <directory-info>...</directory-info>
</business-config>
```

Business 不拥有 Information；只引用 `system.information`。`business.desc` 直接映射为 `<business-config desc="...">`。

## 7.2 Directory

| YAML | XML |
|---|---|
| `name` | `directory/@name` |
| `type` | `directory/@type` |
| `informationRef` | `directory/@information-ref` |
| `modelRef` | `directory/@model-ref` |
| `isRoot` | `directory/@is-root` |

子元素固定顺序：

```text
subdirectory-info
→ dependency-info
→ action-info
→ change-info
```

## 7.3 SubDirectory / case

```yaml
subDirectories:
  - rel: success
    role: case
    informationRef: payment.success
```

→

```xml
<subdirectory rel="success" role="case" information-ref="payment.success"/>
```

当前 P3 mix 的普通执行路径采用“前置/子目录完成后向当前目录执行”的关系表达。不要根据较早文档示意图反转已生效 P3 关系。

## 7.4 Dependency

```yaml
dependencies:
  - informationRef: order.payable
```

→

```xml
<dependency-info>
  <dependency information-ref="order.payable"/>
</dependency-info>
```

## 7.5 Action

```yaml
- name: startPay
  systemRef: payment
  ruleRef: pay
```

→

```xml
<action name="startPay" system-ref="payment" rule-ref="pay"/>
```

无 `ruleRef`：

```yaml
- name: smsNotify
```

→ `<action name="smsNotify"/>`，表示 Custom Action。

## 7.6 Produce

```yaml
produces:
  - ref: PaymentInfo
    informationRef: payment.paymentInfo
```

→

```xml
<produce-info>
  <produce ref="PaymentInfo" information-ref="payment.paymentInfo"/>
</produce-info>
```

## 7.7 Change

```yaml
change:
  informationRef: order.paying
```

→

```xml
<change-info information-ref="order.paying"/>
```

## 7.8 Back

```yaml
subDirectories:
  - rel: paying
    back:
      name: returnPaying
      actions:
        - name: resetPayResult
          systemRef: payment
          ruleRef: resetPayResult
```

→

```xml
<subdirectory rel="paying">
  <back name="returnPaying">
    <action-info>
      <action name="resetPayResult" system-ref="payment" rule-ref="resetPayResult"/>
    </action-info>
  </back>
</subdirectory>
```

---

### DataSource 映射

`business.dataSource` → `<business-config dataSource="...">`；普通与 Back `actions[].dataSource` → `<action dataSource="...">`。字段可省略，显式提供时必须为非空逻辑名称。YAML/XML 双向转换和受控同步必须保留声明，不能把继承结果展开写回每个 Action；继承优先级见 YAML Business 规范。
