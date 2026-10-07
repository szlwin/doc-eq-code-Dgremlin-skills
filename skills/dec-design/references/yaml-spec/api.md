# DEC Canonical YAML：API

仅在创建/修改 `kind: api` 或 RuleView `apiRef` 时读取；使用公共枚举时追加读 `enum.md`。

## 1. API Contract

API 描述传输层契约：URL、名称、System、请求方式、参数、输入校验与返回值。业务状态规则仍由 RuleView / Information / Dependency 表达。

```yaml
kind: api
version: dec/v1
apis:
  - id: API-ORDER-SUBMIT
    name: submitOrder
    desc: 提交订单
    system: order
    url: /api/orders/{orderId}/submit
    method: POST
    request:
      params:
        - name: orderId
          in: path
          type: long
          required: true
          validations:
            - type: min
              value: 1
        - name: status
          in: body
          type: int
          required: true
          relEnum: OrderStatus
      validations:
        - type: expression
          expression: status = 1 and type = 1
          message: 参数组合不合法
    response:
      type: object
      modelRef: OrderInfo
      fields:
        - name: status
          type: int
          required: true
          relEnum: OrderStatus
```

## 2. API 字段

| 字段 | 必填 | 说明 |
|---|---:|---|
| `id` | 推荐 | Design ID |
| `name` | 是 | 所属 System 内唯一 API 名称 |
| `desc` | 推荐 | 业务说明 |
| `system` | 是 | 所属 System |
| `url` | 是 | URL path，path 参数写 `{name}` |
| `method` | 是 | `GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS` |
| `request` | 否 | 请求契约 |
| `response` | 否 | 返回契约 |

`ApiKey = system + "." + name`，RuleView `apiRef` 精确引用 ApiKey。

## 3. Request Param

| 字段 | 必填 | 说明 |
|---|---:|---|
| `name` | 是 | 参数名，同一 API 内唯一 |
| `in` | 是 | `path|query|header|body` |
| `type` | 是 | 逻辑类型 |
| `required` | 否 | 是否必填，默认 false；path 必须 true |
| `desc` | 推荐 | 说明 |
| `default` | 否 | 默认值；path 禁止 default |
| `relEnum` | 否 | 引用公共 Enum name，XML 映射 `rel-enum` |
| `validations` | 否 | 字段级校验 |

URL `{name}` 与 `in: path` 参数必须一一对应。

## 4. 字段级 Validation

支持：

| type | 配置 | 语义 |
|---|---|---|
| `notNull` | 无 | 非 null |
| `notEmpty` | 无 | 非空 |
| `min` / `max` | `value` | 数值范围 |
| `minLength` / `maxLength` | `value` | 长度范围 |
| `pattern` | `value` | 通用模式校验（兼容字段） |
| `regex` | `value` | 正则表达式校验 |
| `enum` | `values` | 内联枚举约束；仅用于没有公共 Enum 的局部值集合 |

数值上下界使用 `min/max`，例如 `min: 1`、`max: 100`；字符串长度使用 `minLength/maxLength`。

正则表达式推荐使用显式 `regex`：

```yaml
validations:
  - type: regex
    value: "^[A-Z0-9_-]{6,32}$"
```

例如长度：

```yaml
validations:
  - type: minLength
    value: 1
  - type: maxLength
    value: 64
```

公共枚举优先使用：

```yaml
relEnum: OrderStatus
```

同一字段不得同时使用 `relEnum` 和内联 `type: enum`，避免双主。

## 5. Request 表达式校验

跨字段条件放在 `request.validations`：

```yaml
request:
  validations:
    - type: expression
      expression: status = 1 and type = 1
      message: status/type 组合不合法
```

`expression` 用于接口参数之间的传输层组合约束；“订单必须可支付”等业务规则仍放 RuleView / Information。

## 6. Response

```yaml
response:
  type: object
  desc: 提交结果
  modelRef: OrderInfo
  fields:
    - name: status
      type: int
      required: true
      desc: 状态
      relEnum: OrderStatus
      validations:
        - type: notNull
```

Response field 支持 `name/type/required/desc/relEnum/validations`。`modelRef` 存在时必须解析到 View。
