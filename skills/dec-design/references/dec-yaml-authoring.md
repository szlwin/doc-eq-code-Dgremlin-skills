# DEC YAML 快速编写指南

本页是入口，不替代 `yaml-spec/*.md`。

## 写之前

1. 确定 kind；
2. 读 `dec-yaml-spec.md` + `yaml-spec/common.md` + 当前 kind；
3. 复制 `templates/<kind>.yaml`；
4. 涉及引用时按需读 `cross-file.md`；
5. 完成后运行目录级 validator。

## 八类 YAML

```yaml
kind: config
kind: enum
kind: data
kind: view
kind: rule
kind: api
kind: systems
kind: business
```

## Canonical 风格

- YAML 字段 camelCase；XML 兼容名由 Converter 处理；
- Rule DSL 用 `type: dsl`，命令用 `cmd`；
- RuleView 必须有 `code`，可有默认 `dataSource`；Rule 可用 `dataSource` 覆盖；
- 公共枚举定义在 `kind: enum`，Column/API 字段通过 `relEnum` 绑定；
- API 支持 required、数值 min/max、长度 minLength/maxLength、regex、内联 enum、公共 relEnum 和 request-level expression；
- Directory 只能位于 `business.directories`。

## 示例

RuleView：

```yaml
kind: rule
ruleViews:
  - id: RV-ORDER-PAY
    name: pay
    code: ORDER_PAY
    desc: 发起支付
    viewRef: OrderInfo
    dataSource: data1
    rules:
      - name: loadPaymentCache
        type: get
        property: payInfo
        dataSource: redis1
```

Enum + API：

```yaml
kind: enum
enums:
  - name: OrderStatus
    values:
      - {value: 1, name: WAIT_PAY}
```

```yaml
kind: api
apis:
  - name: submitOrder
    system: order
    url: /orders/{orderId}
    method: POST
    request:
      params:
        - {name: orderId, in: path, type: long, required: true}
        - {name: status, in: body, type: int, required: true, relEnum: OrderStatus}
      validations:
        - {type: expression, expression: "status = 1 and type = 1"}
```

## 必跑

```sh
python3 scripts/validate_dec_yaml.py path/to/design
python3 scripts/yaml_to_xml.py path/to/design --check
```
