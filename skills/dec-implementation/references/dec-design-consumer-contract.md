# DEC 设计消费契约

`dec-implementation` 不重新定义 DEC DSL。它消费已批准 canonical YAML，并对每个 Design ID 判断由 Runtime 直接执行还是需要项目代码。

## 1. 设计事实来源

实现前读取项目中的 canonical YAML，以及与当前 kind 对应的 DEC YAML 规范。generated XML 只用于 Runtime/Compiler 排查，不能覆盖 YAML 事实。

如果项目同时安装 `dec-design`，可以直接读取其规范分片；如果没有，也可以使用项目随版本保存的同一规范。`dec-implementation` 不依赖任何外部研发流程 Skill。

## 2. 节点责任

| DEC 节点 | 默认责任 | 代码场景 |
|---|---|---|
| Config / DataSource | Runtime 配置 | 新 DataSource type/adapter |
| Enum | Runtime/共享枚举 | 语言 Enum、serializer、DB/API converter |
| Data | Runtime model/mapping | 新 adapter 或 Runtime 缺口 |
| View | Runtime relation | 无法表达时 DESIGN_GAP |
| API | 外部契约 | endpoint/DTO/required/min/max/length/regex/enum/expression |
| RuleView / Rule | 优先 Runtime | 自定义能力或 Runtime 缺口 |
| System | ownership boundary | 通常不生成同名方法 |
| Information | 优先 Runtime evaluator | 能力实现，不复制业务条件 |
| Business / Directory | 优先 Runtime orchestration | Runtime 缺口时明确承载点 |
| Custom Action | 项目代码 | 必须实现并注册 |

## 3. DataSource

RuleView `dataSource` 是默认值，Rule `dataSource` 是覆盖值：

```text
effectiveDataSource = rule.dataSource
                   else ruleView.dataSource
                   else runtime/project default
```

实现必须把逻辑 name 解析到 Config DataSource，支持 MySQL、Redis、MongoDB、第三方系统、HTTP/gRPC 微服务等统一抽象。

## 4. Enum / relEnum

- `kind: enum` 是公共枚举事实；
- Data Column、API request/response 的 `relEnum` 必须使用同一个 Enum；
- 语言 Enum/常量、数据库转换、API serializer 不得改变 `value/name`；
- 同字段使用 relEnum 后不再复制 inline enum values。

## 5. API

- ApiKey `<system>.<apiName>`；RuleView.apiRef 可选；
- URL/method/parameter in/type/required/default/min/max/length/pattern/regex/enum/relEnum/request expression/response 不得漂移；
- transport validation 先于 RuleView；
- request expression 是参数组合约束，不替代业务 Rule；
- endpoint 只做 transport mapping + DEC 调用，不复制业务规则。

## 6. RuleView / Information / Business

- RuleView `code` 是查找键，name 仍是 ruleRef 引用键；
- `ruleRef` 指向 RuleView name，不是内部 Rule name；
- Information `ruleRef/ruleData/expression` 三选一；composite 不复制 Java if；
- Dependency 只引用 Information；Action RuleView/Custom Action 不 fallback；
- Produce 成立后再 Change；Back 按明确关系补偿。

## 7. 实现漂移

以下必须修正或返回 DESIGN_GAP：代码增加未声明业务条件、DataSource 选择与 YAML 不一致、枚举值漂移、API validation 漂移、平行业务 Rule、改变 Action 顺序、绕开 ownership/ModelAccess、直接编辑 generated XML。
