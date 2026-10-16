# Runtime 初始化与请求接线

## 设计加载

Java 生成器从 DecProject 生成 `src/main/resources/dec/design-files.txt`。清单与生成代码一起更新，不在 Workflow 中手写设计文件列表。将同版本官方 converter 导出的 XML 打包到 `src/main/resources/dec/design/`；清单不能代替 XML 本体，构建/CI 必须验证资源镜像和生成结果。

使用 `DecXmlParser.parseClasspath(classLoader, "dec/design-files.txt")` 在启动时读取。加载器支持目录和 JAR，不需要复制到临时目录。清单使用相对路径；不忽略缺失、重复或不安全的资源。保留加载器的 XML 安全及大小限制，不引入不受限 DOM 解析或空设计 fallback。

## 编译结果与请求状态

同一设计版本复用一个 `DirectoryWorkflowRunner`。Runner 在构造时保存不可变 RuleView 编译结果，并供 Directory 就绪检查复用；请求只绑定 DataOperationAdapter。应用不应在每次 API 调用中重建 Runner。

需要 Action 前后监听时，将生成配置收集的 `ActionListenerRegistry` 作为 Runner 构造参数传入；注册表在初始化时验证 Business Design ID 并拒绝同一流程重复绑定，并随 Runner 复用，监听上下文由 Runtime 按次创建。旧构造函数默认无监听器。详细时序与失败语义见 `rule-action-implementation.md`。

直接接入 RuleView 时使用 `compile(project)` 后按请求 `bind(compiled, adapter)`；旧 `register(project, adapter)` 每次都会编译，只用于兼容入口。不要缓存绑定后的 adapter、ModelContext、MutationSet、payload、InformationEngine、Session 或事务对象，也不要把它们保存在全局静态变量里。设计更新需创建新的 Runner；不允许旧编译结果继续代表新设计。

复用范围仅是 canonical RuleView 编译结果，不暗示解释器的全部表达式/DSL 预编译。性能结论要有测量范围和数据；接线微测量不能推出数据库或端到端吞吐收益。

## 应用 Store

默认由 Factory 组装 Store，Workflow、ViewAssembler 和 DataOperationAdapter 直接使用 DAO 访问器。项目可用 Spring Bean 或既有装配方式替代；重点验证生命周期和事务，不以是否有 Factory 判定正确性。建议删除只有 DAO getter 的 UnitOfWork/begin()；有接口兼容理由时可保留包装，但不得伪造提交。真正拥有暂存变更或资源的工作单元可保留，并显式说明作用域、提交与回滚职责。

Store 复用不代表共享请求状态或事务；提交边界仍遵循 `datasource-implementation.md` 的两种事务模式。

## 修改生成器时

保持入口薄，按模型组装、API 分组、Data/View/Enum 模型、Java 类型与命名、设计索引、模板和文件管理分工；不要把新职责全部放回 GeneratorPipeline。输入校验先于旧生成文件清理，保留 generated manifest 的所有权和路径安全检查。

结构重构应对照已有生成产物；有意新增产物应说明差异。验证 JAR/classpath 加载、并发请求隔离和设计更新后的重新编译，更新 Demo、Binding 的符号与测试引用，并重新生成当前设计/代码的测试证据。

业务正确性验证还应按风险选择 `scenario-validation.md` 中的并发、回调、集合和失败场景；不把单一路径通过作为通用性证明。
