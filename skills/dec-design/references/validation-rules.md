# DEC YAML 校验规则

`validate_dec_yaml.py` 覆盖文件结构、跨文件引用和业务语义三层。

## 通用

- kind 唯一识别，未知字段拒绝；
- 必填字段存在；
- 稳定 Design ID 全局唯一；
- list/map/scalar 类型正确；
- 已加载足够事实时真实错误不得降级为 warning。

## Config / DataSource

- DataSource name 全局唯一；
- Connection name 唯一且至少引用一个 DataSource；
- Data table、RuleView、Rule 的 `dataSource` 在 Config 已加载时必须可解析；
- Rule 的有效 DataSource 按 Rule override → RuleView default → Runtime/project default 计算。

## Enum

- Enum name 全局唯一；
- 每个 Enum 至少一个具体 `value`，例如状态枚举可使用 `1/2/3/4`；
- 同一 Enum 的 `value` 和 `name` 均不得重复；
- `Data.column.relEnum`、API request/response `relEnum` 必须精确引用 Enum；
- 同一 API 字段不得同时配置 `relEnum` 与内联 `type: enum` values；
- 当 Information `changeData` 修改的 View property 最终映射到带 `relEnum` 的 Data Column 时，change 写入值必须存在于该 Enum；不存在、映射到多个 Enum 或 Enum 缺失时必须报错。

## Data / View

- Data name 唯一、properties 非空；
- table `name/dataSource/key/keyType/columns` 完整；
- `keyType ∈ {increment,set}`；column.ref 必须存在于 Data.properties；table.key 必须存在于 columns；
- View `system` 必填并引用已定义 System；该字段定义 View 的 ownership；
- View.targetMain 对应 Data；relation 仅 `one-to-one|one-to-many`；relation data/key/relKey 可解析。

## RuleView / Rule

- RuleView name 唯一；
- `code` 必填且设计域内唯一；
- viewRef 对应 View；apiRef 存在时精确解析 ApiKey；
- Rule name 在 RuleView 内唯一；
- Rule type 合法，并按类型校验 property/pattern/cmd/process；
- RuleView/Rule dataSource 已加载 Config 时必须存在；
- error 存在时 code/message 必填。

## API

- ApiKey `<system>.<name>` 唯一；system/url/method/name 必填；
- method 属于允许 HTTP 方法；
- request parameter name 唯一，`in ∈ {path,query,header,body}`；
- URL `{name}` 与 path 参数一一对应，path 必须 required=true 且不得 default；
- 字段级 validation：`min/max/minLength/maxLength/pattern/regex` 要有 value，`enum` 要有 values；
- request-level `expression` 必须有 expression 文本，例如 `status = 1 and type = 1`；
- required、长度、枚举、表达式校验均可机器验证；
- response.modelRef 存在时引用 View；
- relEnum 存在时引用公共 Enum。

## System / Information

- System name 唯一；Information local name 在 System 内唯一；
- InformationKey `<system>.<name>` 全局唯一；
- `ruleRef/ruleData/expression` 恰好一个；
- 原子 Information 有 viewRef；复合 Information 不允许 viewRef/changeData；
- expression 引用存在且 Information graph 无环；
- RuleView 已加载时 ruleRef 存在且 viewRef 兼容。

## Business

- Directory name 唯一且至少一个 root；informationRef/modelRef 必填；
- Dependency 只引用 Information；其语义是“Directory 依赖 prerequisite Information”；
- `role: case` 的目标 Directory 已由 case `informationRef` 负责分支判定，目标 Directory 不得再声明 `dependencies`，避免 case/depend 双重 gate；
- Action 有 ruleRef 时 systemRef 必填；Custom Action 无 ruleRef，不允许 fallback；
- Produce.ref 必填，informationRef 应指向原子 Information；
- Change/Back/分类关系必须可解析。

缺少相关外部文件导致无法验证时才给 warning，例如只校验 Rule 文件但未加载 Config/DataSource。
