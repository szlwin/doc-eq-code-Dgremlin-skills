# DEC Canonical YAML：RuleView / Rule

仅在编写或修改 `kind: rule` 时读取。涉及 API 绑定时追加读取 `api.md`；涉及 DataSource 时确认 `config.md`。

## 1. RuleView

```yaml
kind: rule
version: dec/v1

ruleViews:
  - id: RV-SAVE-ORDER
    name: saveOrder
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
    action.dataSource if present (执行 Action 时)
    else rule.dataSource if present
    else ruleView.dataSource if present
    else business.dataSource
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

### 表达式作用域

普通 checkPattern/DSL 读取当前 View；checkDataPattern、every/any 谓词及 DSL every 内部都使用当前查询行或集合项的独立顶层映射，不隐式回退根模型。嵌套 every 进入嵌套项。`#变量` 在一次 RuleView 调用内共享，可显式传入 DSL 外层值。字段缺失必须报告未决，不能因作用域不同而静默补值。

独立关键字不能作为属性操作数；可使用明确的限定字段（如 `item.and`）。改写查询别名或集合表达式时，应覆盖同名字段和缺失字段的场景。

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

## 6. 原始模型与 dec-java-lite 能力边界

原始 `RuleDefineInfo` 保存 name/type/property/grammer/errorInfo/customerInfo；`RuleCheckInfo` 保存 pattern，`RuleCheckData/RuleCheckDataPattern` 保存 sql/pattern，`RuleExecuteInfo` 保存 sql。规范转换必须完整保留这些字段：`process ↔ customer-process`、`cmd ↔ sql`、`error ↔ error-info`、`customer ↔ customer-info`。`customer` 对应原始 `Map<String,String>`，只能使用平铺的字符串值；数字须显式写为字符串，禁止嵌套对象/列表被静默字符串化。

选择 dec-java-lite 时，设计校验通过不等于所有历史表达式可执行。Runtime 编译阶段检查下列语法；不支持的表达式报告 capability/design gap，不能在 Service 复制条件绕过：

- `check/checkData`：支持以 `;` 连接的 `NOTNULL/NULL/EQUAL/NOTEQUAL/GREATER/GREATEREQUAL/LESS/LESSEQUAL`，二元校验用 `:` 接常量，字符串加引号，例如 `NOTNULL;NOTEQUAL:2`。
- `checkPattern/checkDataPattern`：支持字段路径、列表下标、字段比较、数字算术、括号、短路布尔运算、`exists/every/any`；`checkDataPattern` 对查询返回的每行在独立行上下文中校验，空结果为 FALSE，不将行字段写回 View。
- `checkData` 必须真实读源后校验返回对象，空结果按 null 校验；不能用当前 View 中已有的非空字段冒充数据库存在。集合目标的逐项读取/匹配由适配器兑现，不能漏查部分项目后报告全部通过。
- `dsl` 支持模型赋值、`#变量`、`if ... then ... else ...`、算术、`every(list, field : expression)` 和 `error(code,message) : predicate`。error 的 code/message 在运行时当前作用域求值，可引用模型字段、集合项字段和 `#变量`；临时变量限一次 RuleView 调用，写入由 Runtime 追踪。
- `get/query/insert/update/delete` 需要真实 DataSource adapter；`cmd` 是传给驱动的设计命令，框架不会把 DEC 逻辑 SQL 自动视作可直接执行的 JDBC SQL。

历史 santr/smarter 的集合过滤、`.sum`、`.compute` 及其他校验器未全部迁移，不能宣称完整兼容；Information/Query 自身的表达式语法没有因此扩大。无驱动、缺路由或未支持的语法必须明确失败。

### 原 DEC MySQL 的操作组合

原 DEC Runtime 接受 dsl/grammer，内部统一为 grammer。MySQL 已提供带 SQL insert 和无 SQL query 转换器，但设计仍需遵守其具体语法与目标约束：

- 带 SQL insert 使用 `insert into <View或关联属性> (<View字段...>) values (...)`，支持单行 VALUES、#参数路径、标量字面量、NULL/DEFAULT。View→Data→物理表列两级映射不能跳过，参数必须绑定；不支持的 SQL 扩展必须报错，不能转成原生 SQL 执行。
- 目标为集合时逐项执行 insert，#参数引用当前元素；单对象可引用完整模型路径。每项主键回填到其 View 字段。
- 无 SQL query 的 Rule.property 必须是直接 one-to-many 属性，由声明的父键和子外键生成等值条件；空结果清空集合，父键缺失失败。不能将已有集合字段当成筛选条件，也不能静默全表读取。
- 低层 BaseData query 以非空属性组合 AND 条件，NullData 表示 IS NULL，至少提供一个条件。需要排序、联表或全表查询时使用显式 SQL。

checkDataPattern 的 property 在原解析器中保留作元数据；SQL 使用完整模型参数，property 不自动限制查询范围。上述能力属于原 DEC MySQL 驱动；dec-java-lite 的 RuleDataAccess 仍由应用提供实际驱动，不能因原项目的 MySQL 回归通过而声称 lite 已自动接入同一数据库能力。
