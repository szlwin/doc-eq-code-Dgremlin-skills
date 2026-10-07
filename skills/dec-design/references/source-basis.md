# DEC 规范来源与优先级

本 Skill 可脱离原始源码仓库运行。`references/`、`schemas/`、`templates/` 和脚本组成自包含的执行契约；原始文档只用于维护这些资源时校准语义，不是用户执行任务时的必需输入。

## 语义基线

规范覆盖以下主题：

- DEC 是表达业务与架构事实的设计语言，设计事实与实现细节分离；
- DataSource/Connection 的统一外部能力边界，以及 Data、View、RuleView、Container 的职责；
- Data 的属性和外部映射、View 的关系、RuleView/Rule 的执行语义；
- Information 的识别、组合、物化和 RuleView 引用；
- Business Directory 的 Dependency、Action、Produce、Change、Back 及编译期引用校验；
- API、Enum、System ownership、ModelAccess 等本包扩展。

## 优先级

发生冲突时按以下顺序处理：

1. 用户当前明确的设计决策和输入约束；
2. 本包当前 `references/`、JSON Schema、模板和脚本实现；
3. 同版本 Runtime/Compiler 的兼容要求；
4. 原始 DEC 文档和历史示例。

原始文档中的独立数据声明语言不属于本 Skill 的 canonical DEC YAML。只有任务明确要求迁移它时才读取对应规范，并保持其根结构、解析器和生命周期独立。


## 已固化的扩展

本包在基础 DEC 语义上固化了以下可选扩展：

- 新增独立 `kind: api`，描述 API `name/desc/system/url/method/request/response`；
- API parameter/response field 支持 `required`、长度、pattern、inline enum，以及公共枚举 `relEnum`；
- API request 支持跨参数 `expression` 校验，例如 `status = 1 and type = 1`；
- 新增独立 `kind: enum`，作为 API 与 Data Column 的公共枚举事实源；
- Data `<column>` 通过 canonical `relEnum` 映射 XML `rel-enum`；
- RuleView 增加必填 `code`、说明 `desc`、默认 `dataSource`，并继续支持可选 `apiRef`；
- Rule 增加可选 `dataSource`，覆盖 RuleView 默认值；
- DataSource 可以表示 MySQL、Redis、MongoDB、第三方系统、微服务等统一外部能力边界；
- BusinessScope `desc` 映射 `<business-config desc="...">`。

这些扩展必须通过对应 schema、validator 和 converter 一起维护；不能只在文字规范中声明。

## 设计与实现分离

设计与实现分离在 Skill 中落为硬规则：

- YAML 只表达 DEC 设计事实；
- Java/Spring/MyBatis/Kafka SDK 等实现细节不能进入 DEC YAML，除非它们本身就是 DataSource 的逻辑类型/能力声明；
- 实现类、代码 symbol、测试 symbol 进入 `dec-implementation` 的 `implementation-binding.yaml`，而不是污染设计 YAML；
- 如果当前 DEC grammar 无法表达需求，登记 `DESIGN_GAP`，不能把 Java 代码细节塞进 YAML 作为替代。

## 规范文件的按需读取

为降低 AI 上下文开销，字段规范不再集中在单个大文件：

- `dec-yaml-spec.md` 只做 YAML 规范路由；
- 具体字段规则位于 `yaml-spec/*.md`；
- `xml-compatibility-spec.md` 只做 XML 映射路由；
- 具体映射位于 `xml-mapping/*.md`。

读取规则是“common + 当前 kind + 必要依赖”，而不是全量加载。
