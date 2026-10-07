# DEC Canonical YAML：System / Information / ModelAccess

仅在编写或修改 `kind: systems`，或 Business 需要新增/调整 Information 时读取。

# 6. System

当前 P3 使用 System 作为 Data、View、RuleView、Information 的能力归属边界。

```yaml
kind: systems

systems:
  - id: SYS-ORDER
    name: order
    dataRefs:
      - order
      - orderDetail
    viewRefs:
      - OrderInfo
    ruleFiles:
      - classpath:mix/rule/order-rule.xml
    information: ...
    modelAccess: ...
```

## 6.1 System 字段

| 字段 | 必填 | 说明 |
|---|---:|---|
| `id` | 推荐 | 稳定 ID |
| `name` | 是 | System 名称，全局唯一 |
| `dataRefs` | 否 | 本 System 拥有/声明的数据引用 |
| `viewRefs` | 否 | 本 System 可拥有的 View |
| `ruleFiles` | 否 | RuleView 文件 |
| `information` | 否 | 本 System 拥有的 Information |
| `modelAccess` | 否 | 对共享业务模型的精确读写授权 |

BusinessScope 不拥有 Information；跨 System 信息引用使用 `<system>.<information>` 完整 key。

---

# 7. Information

语义来自 `docs/information-tree.md`，落盘归属采用当前 P3 System ownership。

## 7.1 三种互斥形式

每个 Information 必须且只能使用一种 recognizer：

```text
ruleRef | ruleData | expression
```

### RuleView 型原子 Information

```yaml
- id: INFO-USER-ACTIVATED
  name: activated
  viewRef: UserInfo
  ruleRef: isActivated
```

要求：

- `name`
- `viewRef`
- `ruleRef`

禁止同时使用 `ruleData` / `expression`。

### 数据表达式型原子 Information

```yaml
- id: INFO-ORDER-ORDERED
  name: ordered
  viewRef: OrderInfo
  ruleData: |
    status = 1
    and
    every(orderDetailList, status = 1)
  changeData: |
    status : 1;
    every(orderDetailList, status : 1);
```

要求：`name` + `viewRef` + `ruleData`。

`changeData` 可选，表示如何物化该原子 Information。

### 复合 Information

```yaml
- id: INFO-ORDER-PAYABLE
  name: payable
  expression: |
    order.ordered
    or
    order.waitPay
```

要求：`name` + `expression`。

复合 Information 禁止 `viewRef`、`ruleRef`、`ruleData`、`changeData`。

## 7.2 Expression 约束

- expression 只能组合 InformationKey；
- 跨 System 必须使用完整 key，例如 `payment.success`；
- 禁止直接写 `status = 3` 之类底层字段判断；
- 依赖图不得存在直接或间接循环；
- 引用的 Information 必须存在。

## 7.3 RuleView 引用约束

`ruleRef` 指向 `ruleViews[].name`，不是内部 Rule name。

如果相关 Rule YAML 同时加载，应校验：

```text
Information.viewRef == RuleView.viewRef
```

并且 RuleView 应属于该 System 的能力范围。

## 7.4 识别与物化分离

Information recognizer 只回答“是否成立”。

`changeData`、Action、Produce 等回答“如何产生/物化”。不要把二者混成一个字段。

---

# 8. ModelAccess

当前 P3 用于控制 System 对共享 View/model 的精确路径访问。

```yaml
modelAccess:
  - modelRef: OrderInfo
    read:
      - path: "*"
        ref:
          view: OrderInfo
          property: order
    write:
      - path: status
        ref:
          view: OrderInfo
          property: order
```

字段：

| 字段 | 必填 | 说明 |
|---|---:|---|
| `modelRef` | 是 | 共享业务模型 |
| `read` | 否 | READ path 列表 |
| `write` | 否 | WRITE path 列表 |
| `path` | 是 | 精确授权路径，`*` 表示当前约定的读范围 |
| `ref.view` | 是 | 绑定 View |
| `ref.property` | 是 | 精确 target/property selector |

不允许 fuzzy match 或匹配失败后降级。

---
