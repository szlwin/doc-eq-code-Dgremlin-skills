# Enum / relEnum 实现规范

## 1. 单一事实源

`kind: enum` 的 `Enum.name + values(value/name/desc)` 是公共枚举事实。Data Column、API parameter/response field 使用 `relEnum` 引用它。

实现不得形成：

```text
DEC Enum
API 自己一套 allowed values
数据库 converter 又一套值
Java/TS Enum 再一套值
```

应形成：

```text
DEC Enum
  ├─ API validator/serializer
  ├─ Column converter
  └─ Language Enum/constant
```

## 2. 代码映射

如果 Runtime 已能直接消费 Enum XML/YAML，Binding 使用 `runtimeManaged`。否则可以生成/维护语言 Enum，但必须：

- 名称映射可技术化，业务 `value/name` 不变；
- 序列化/反序列化使用 `value`；
- 数据库存储/外部接口值与 YAML 相同；
- 未知值处理策略显式测试，不得静默映射到其他值。

## 3. relEnum

`relEnum: OrderStatus` 出现在：

- Data Column：持久化/外部数据转换必须接受该 Enum 值集合；
- API request：输入 validator 与 parser 必须使用该 Enum；
- API response：serializer/documentation 必须输出该 Enum 契约。

同字段存在 `relEnum` 时，不再维护一份独立 inline enum values。

## 4. 兼容性

修改/删除枚举值前检查：

- 已持久化历史数据；
- API 客户端兼容；
- Rule/Information 表达式；
- Column converter；
- 测试 fixture；
- 多语言 SDK/DTO。

枚举值变更不是普通重命名；`name` 可做实现别名，但 `value` 改变通常属于外部/数据兼容变更。
