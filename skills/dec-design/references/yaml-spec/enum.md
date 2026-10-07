# DEC Canonical YAML：Enum

仅在定义或修改公共枚举，或 Data Column / API 字段通过 `relEnum` 引用枚举时读取。

## 1. 目标

Enum 是跨 Data、API、实现代码复用的业务枚举事实。枚举值只维护一份；引用方使用 `relEnum`，不要复制另一份 values。

```yaml
kind: enum
version: dec/v1

enums:
  - id: ENUM-ORDER-STATUS
    name: OrderStatus
    desc: 订单状态
    values:
      - id: ENUM-VALUE-ORDER-STATUS-WAIT-PAY
        value: 1
        name: WAIT_PAY
        desc: 待支付
      - id: ENUM-VALUE-ORDER-STATUS-SUCCESS
        value: 3
        name: PAY_SUCCESS
        desc: 支付成功
```

## 2. Enum 字段

| 字段 | 必填 | 说明 |
|---|---:|---|
| `id` | 推荐 | 稳定 Design ID，建议 `ENUM-*` |
| `name` | 是 | 枚举引用名；在设计域内全局唯一 |
| `desc` | 推荐 | 业务说明 |
| `values` | 是 | 至少一个枚举值 |

Enum value：

| 字段 | 必填 | 说明 |
|---|---:|---|
| `id` | 推荐 | 稳定 Design ID |
| `value` | 是 | 对外/持久化的**具体值**；状态类枚举优先使用明确数字（如 `1/2/3/4`），协议本身仍允许字符串业务值 |
| `name` | 是 | 业务代码名，在当前 Enum 内唯一 |
| `desc` | 推荐 | 人类可读说明 |

同一 Enum 内 `value` 与 `name` 均不得重复。枚举不能只写名称而省略具体 `value`。

## 3. 引用

Data Column：

```yaml
columns:
  o_status:
    ref: orderStatus
    relEnum: OrderStatus
```

API request/response field：

```yaml
- name: status
  type: int
  required: true
  relEnum: OrderStatus
```

转换后统一使用 XML `rel-enum="OrderStatus"`。

## 4. Enum 与 Information change

当 Information 的 `changeData` 修改 View property，而该 property 最终映射到带 `relEnum` 的 Data Column 时，写入值必须来自该 Enum。

例如 `OrderInfo.status -> order.orderStatus -> OrderStatus`：

```yaml
# Enum
values:
  - { value: 1, name: WAIT_PAY, desc: 待支付 }
  - { value: 2, name: PAYING, desc: 支付中 }
  - { value: 3, name: PAY_SUCCESS, desc: 支付成功 }
  - { value: 4, name: PAY_ERROR, desc: 支付失败 }

# Information
changeData: |
  status : 3;
```

这里 `status : 3` 必须能精确解析为 `OrderStatus.PAY_SUCCESS`。`validate_dec_yaml.py` 会沿 `View -> Data property -> Column.relEnum -> Enum.value` 校验；嵌套 View property（如 `orderDetailList.status`）同样适用。

## 5. 权威规则

- `relEnum` 存在时，Enum 文件是枚举值 Source of Truth。
- 不要同时在同一 API 字段上再维护 `validations: [{type: enum, values: ...}]`；validator 会拒绝这种双主。
- Information change 写入 enum-bound 字段时不得使用 Enum 中不存在的值。
- 不应为了语言实现方便修改枚举业务值；语言 Enum/常量由实现层映射。
- 删除/变更既有枚举值属于兼容性变更，必须做引用影响分析。
