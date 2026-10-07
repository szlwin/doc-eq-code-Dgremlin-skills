# DEC Canonical YAML：RuleView / Rule

仅在编写或修改 `kind: rule` 时读取。涉及 API 绑定时追加读取 `api.md`；涉及 DataSource 时确认 `config.md`。

## 1. RuleView

```yaml
kind: rule
version: dec/v1

ruleViews:
  - id: RV-SAVE-ORDER
    name: save-Order
    code: SAVE_ORDER
    desc: 保存并提交订单
    viewRef: OrderInfo
    apiRef: order.submitOrder       # optional
    dataSource: data1               # optional default for all child rules
    rules:
      - id: RULE-INSERT-ORDER
        name: insertOrder
        type: insert
        property: OrderInfo
      - id: RULE-LOAD-CACHE
        name: loadCache
        type: get
        property: OrderInfo
        dataSource: redis1          # optional override for this rule only
```

字段：

| 字段 | 必填 | 说明 |
|---|---:|---|
| `id` | 推荐 | 稳定 Design ID |
| `name` | 是 | RuleView name；`ruleRef` 引用此值 |
| `code` | **是** | 快速查找代码；设计域内唯一 |
| `desc` | 推荐 | RuleView 业务说明 |
| `viewRef` | 是 | 绑定 View |
| `apiRef` | 否 | `<system>.<apiName>`，一个 RuleView 最多绑定一个 API |
| `dataSource` | 否 | 当前 RuleView 全部 Rule 的默认 DataSource |
| `rules` | 是 | 有序 Rule 列表，至少一条 |

`ruleRef` 永远引用 RuleView `name`，不是内部 Rule name。`code` 用于快速检索，不替代 `name` 或 Design ID。

### DataSource 继承规则

```text
effectiveDataSource(rule) =
    rule.dataSource if present
    else ruleView.dataSource if present
    else runtime/project default
```

因此 RuleView 可以默认使用 MySQL，而某条 Rule 单独覆盖为 Redis、MongoDB、第三方系统或微服务。DataSource 是统一抽象，不因协议/存储类型创建平行 Rule 模型。

## 2. Rule 类型

| canonical type | 语义 |
|---|---|
| `check` | 单 property 校验 |
| `checkPattern` | 多 property 表达式校验 |
| `checkData` | 读取数据后单值校验 |
| `checkDataPattern` | 读取数据后表达式校验 |
| `insert` | 新增 |
| `update` | 更新 |
| `delete` | 删除 |
| `get` | 读取单个 |
| `query` | 读取多个 |
| `dsl` | 赋值、计算、判断等 DSL |

历史 Runtime XML 的 `grammer` 由 Converter 兼容；新 YAML 使用 `dsl`。

## 3. Rule 字段

| 字段 | 必填 | 说明 |
|---|---:|---|
| `id` | 推荐 | 稳定 ID |
| `name` | 是 | RuleView 内唯一 |
| `type` | 是 | 上表类型 |
| `dataSource` | 否 | 当前 Rule 特定 DataSource；覆盖 RuleView 默认值 |
| `property` | 条件 | 操作/校验目标 |
| `pattern` | 条件 | 校验表达式 |
| `cmd` | 条件 | DataSource 命令；canonical 使用 `cmd` |
| `process` | `dsl` 必填 | DSL 内容 |
| `error` | 否 | 错误定义 |
| `customer` | 否 | 扩展信息 |

`cmd` 输出历史 XML `sql`；不要在 YAML 新增 `sql`。

## 4. 主要类型条件

- `check`：`property + pattern`。
- `checkPattern`：`pattern`。
- `checkData`：`property + pattern`，`cmd` 按 DataSource 能力可选。
- `checkDataPattern`：`property + pattern`，`cmd` 可选。
- `insert/update`：canonical 要求 `property`。
- `delete`：至少 `property` 或 `cmd`。
- `get/query`：要求 `property`，`cmd` 可选。
- `dsl`：要求 `process`。

## 5. Error

```yaml
error:
  code: C001
  message: user error
  level: "1"
```

存在 `error` 时 `code/message` 必填，`level` 可选。
