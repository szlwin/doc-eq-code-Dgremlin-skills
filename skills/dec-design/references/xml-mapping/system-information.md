# DEC YAML → XML：System / Information / ModelAccess 映射

仅在转换 `kind: systems` 时读取。

# 6. System / Information / ModelAccess

## 6.1 System

```yaml
systems:
  - name: order
    dataRefs: [order, orderDetail]
    viewRefs: [OrderInfo]
    ruleFiles:
      - classpath:mix/rule/order-rule.xml
```

→

```xml
<systems>
  <system name="order">
    <data-info>
      <data-ref name="order"/>
      <data-ref name="orderDetail"/>
    </data-info>
    <view-info>
      <view-ref name="OrderInfo"/>
    </view-info>
    <rule-file-info>
      <rule-file path="classpath:mix/rule/order-rule.xml"/>
    </rule-file-info>
    ...
  </system>
</systems>
```

## 6.2 Information

RuleView 型：

```yaml
- name: activated
  viewRef: UserInfo
  ruleRef: isActivated
```

→

```xml
<information name="activated" view-ref="UserInfo" rule-ref="isActivated"/>
```

ruleData 型：

```yaml
- name: paying
  viewRef: OrderInfo
  ruleData: |
    status = 2
  changeData: |
    status : 2;
```

→

```xml
<information name="paying" view-ref="OrderInfo" rule-data="status = 2">
  <change-data><![CDATA[status : 2;]]></change-data>
</information>
```

composite：

```yaml
- name: payable
  expression: |
    order.ordered
    or
    order.waitPay
```

→ `information/@expression`。

注意：`docs/information-tree.md` 较早布局使用 `model-ref` 表示识别上下文；当前 P3 System-owned XML 使用 `view-ref`，canonical YAML 按 P3 写 `viewRef`。

## 6.3 ModelAccess

```yaml
modelAccess:
  - modelRef: OrderInfo
    read:
      - path: "*"
        ref:
          view: OrderInfo
          property: order
```

→

```xml
<model-access-info>
  <model-access model-ref="OrderInfo">
    <read path="*">
      <ref view="OrderInfo" property="order"/>
    </read>
  </model-access>
</model-access-info>
```

---
