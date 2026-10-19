# 生成与验证

从 canonical YAML 生成 Spring Boot/MyBatis 项目；XML 前端用于导入和对等检查，设计修改仍回到 YAML。

```sh
./mvnw clean package
./mvnw install -DskipTests
java -jar dec-lite-cli/target/dec-lite-cli-0.1.0.jar inspect --yaml fixtures/mix
java -jar dec-lite-cli/target/dec-lite-cli-0.1.0.jar generate \
  --yaml fixtures/mix --output examples/order-service/generated \
  --base-package com.example.order
./mvnw -f examples/order-service/generated/pom.xml test
```

`config` 提供 DataSource；`data`、`view`、`api`、`enum` 生成 Application、Entity、嵌套 View DTO、Controller、Service、Request DTO、DAO、Mapper 和 Enum。`systems` 额外生成按系统组织的只读 Information 投影，`business` 生成只读 Directory/Action/Produce/Back 流程投影，`rule` 仍由运行时编译。投影类只保留 Design ID、YAML 路径、依赖、表达式和 change-data 等导航元数据，不复制 Information、RuleView、Action 或 Directory 的执行逻辑；真正执行仍由 DEC Runtime 负责。`RuntimeCatalog` 用注册的 RuleView 和 Custom Action 实现验证运行时就绪。

Request DTO 的 Jakarta Validation 注解、错误信息和业务校验边界见[《Jakarta Validation 映射》](jakarta-validation.md)。

生成代码按设计节点的 `system` 字段分包。实体、DAO、Mapper、View、Controller、Service 和 Request DTO 使用 `<base-package>.<system>.<layer>`；例如 `system: order` 的 API 位于 `com.example.order.order.controller`，订单数据位于 `com.example.order.order.entity`。Enum 根据其 Data 归属系统进入对应的 `<system>.enums`，被多个系统的 Data 共同拥有时才进入 `<base-package>.common.enums`。View 中的嵌套投影跟随所属顶层 View 的系统包，跨系统引用通过显式 Java import 保持可读性。

Controller 和 Service 默认按 `System + URL 主资源` 归组，可通过独立 `--generation-config` 覆盖指定 API：跳过开头的 `api`、`v数字` 段，以首个静态资源段转换为类名前缀，保留复数。例如当前订单 Demo 的 6 个 `/api/orders` 及其子路径接口合并到 `OrdersController/OrdersService`，手写实现使用一个 `OrdersServiceImpl`。不同 System 或资源分别生成；同资源跨 YAML 文件合并。每个方法保留 Design ID 和完整路由，DTO 仍按 API 请求契约生成。无法识别资源段或归组后类名/方法名冲突时在覆盖文件前报错。

生成的 `config.CustomActionConfiguration` 统一收集 Spring `CustomAction` Bean 并创建冻结的注册表；Workflow 注入 `CustomActionRegistry`，新增 Action 无需改 Workflow 构造器。实现仍由应用提供，生成器不生成假成功的短信等业务动作。设计中无 `ruleRef` 的 Action 默认按名称注册，Business Flow 的 `ActionNode.customType()`/`isCustom()` 可用于导航；详细接入和事务时序见 [Action Runtime](action-runtime.md)。

生成的 Service 是带 Design ID 注释的接口；Controller 依赖该接口，应用通过实现接口接入实际 RuleView 执行器、事务和业务依赖。生成器不会伪造业务实现。

生成的 `<system>.information.*Information` 类对应信息树中的 System 和 Information 节点，可查看 `key`、识别方式、依赖、change-data 及 View/Rule 引用。生成的 `business.*Flow` 类对应 Directory 流程，可查看 Directory 的依赖、execution/case 边、Action、Produce 和 Back；例如 `OrderPaymentFlow.PAY_RESULT` 展示支付结果分支及其回退边。它们是只读设计投影，应用可以用其中的名称常量与运行时对齐，但不应把投影当作业务状态机重新实现。

生成类、字段、方法和参数会保留 `Design ID + YAML 文件#节点路径 + 派生 XML 文件#节点路径`；项目根目录的 `DEC-DESIGN-INDEX.md` 提供每个 Design ID 到 YAML/XML 定位和 Java 文件的索引。阅读代码时先从索引或注释定位设计节点，再回到 YAML 核对语义。

`dec-lite-directory` 提供 `DirectoryWorkflowRunner` 复用 Information、RuleView、Action、Session 和 Directory 编排。应用 Workflow 应优先调用通用 `advance(...)`，只提供本次请求的 ModelContext 转换、DataSource adapter、Custom Action、payload、显式 WorkflowTransaction 模式和应用完成回调（`CompletionHandler.complete`），不应重复创建这些 Runtime 组件或重复编写失败判断。

DAO/Mapper 组成的 DataSource Store 默认由 Factory 组装，也可沿用项目的 Spring @Bean/@Component 或既有 DI 方式；实际装配入口注入 DAO、Mapper、数据库或连接资源，并明确应用、事务或请求作用域；Store 承载类型化读写和行锁；只包装 DAO 的 UnitOfWork 和 begin() 应删除，直接使用 Store；仅当存在真实事务或暂存变更时保留工作单元生命周期。持久化 View 的 Directory 识别与校验使用 Runtime 的 `recognizeDirectory(...)` / `assertDirectoryMatches(...)`，不能在 Store 中按枚举重写 Information，Spring JDBC 的提交和回滚由 Workflow 的 `@Transactional` 管理。Store 不使用 `synchronized`、`ReentrantLock` 或方法级锁，提交冲突、回滚和重试由事务、数据库约束或 DAO/DataSource 边界负责。工程接入 JDBC 时还必须提供驱动、连接池、schema/migration 和事务配置；内存适配器只能放在显式测试 profile。

应用绑定 Service 或编排 DEC Workflow 时，不得在 Service、Workflow、Controller 方法上添加 Java `synchronized`。请求并发与一致性必须接入 `ExecutionSession`、事务协调器、DAO/Repository 或 DataSource 的边界；生成器保留接口 seam，由应用实现真实事务、冲突和回滚语义。

API body 参数生成到 Request DTO 时只承载字段级传输校验。`required`、`min/max`、长度和正则约束会生成带 `message` 的 Jakarta Validation 注解；跨字段的订单状态、订单类型、库存或权限条件必须写入 RuleView/Information/Business，在 DTO 校验后执行，不会生成到 Request DTO 的 `@AssertTrue`。

生成目录由 `.dec-generated` 与 `dec-generated-manifest.txt` 管理。非空且没有标记的目录会拒绝覆盖；旧 manifest 只清理自身列出的安全相对路径；符号链接与越界路径会拒绝。手工文件保留在目录外，生成文件不可手改。`GeneratorPipelineTest` 从 [`fixtures/mix`](../fixtures/mix) 重新生成并与 [`examples/order-service/generated`](../examples/order-service/generated) 逐文件比较。


生成的 Spring 工程依赖 `dec-lite-spring`（传递依赖 Directory Runtime）。Workflow 使用外层 `@Transactional` 时传入 `SpringWorkflowTransactions.externallyManaged()`；由 Runtime 开始事务时，在无外部事务的入口传入 `SpringWorkflowTransactions.runtimeManaged(route, transactionManager)`。完成回调是 `(completed, execution, transaction) -> ...`，在物理提交前执行。不得传入空 `ActionTransaction` 假装接入事务；具体限制见 [Directory Runtime](directory-runtime.md)。


## 生成职责与 classpath 设计资源

`GeneratorPipeline` 只协调生成阶段。`GenerationContextBuilder` 解析文档归属和分包，`DataModelBuilder` 构建及校验 Data/Enum/View 模型，`ApiModelBuilder` 校验 API 契约并按资源归组，`JavaModelSupport` 处理 Java 命名和类型，`DesignIndexWriter` 维护设计索引，`GeneratedProjectWriter` 处理模板、路径安全、旧文件清理和生成清单。业务 Information/Flow 投影仍由 `DesignProjectionGenerator` 负责。输入和资源重名校验在清理旧产物之前完成。

生成器新增 `src/main/resources/dec/design-files.txt`，从本次 DecProject 的文档生成排序后的 XML 清单。文件名必须为 ASCII 字母、数字、`_ $ . -`，不同目录的同名设计文件被拒绝。清单属于生成器管理文件，不手工维护；XML 仍由官方 YAML→XML converter 生成，发布时将同版本完整导出复制到 `src/main/resources/dec/design/`。清单与 XML 分开存放，已有 XML 镜像校验仍有效。生成器不从项目任意目录猜测或复制 XML。

应用启动时使用：

```java
var project = new DecXmlParser().parseClasspath(
        OrderWorkflow.class.getClassLoader(), "dec/design-files.txt");
var runner = new DirectoryWorkflowRunner(project, customActions);
```

加载器支持目录和 JAR，通过流直接解析，不创建临时文件。清单中的路径相对清单所在目录；拒绝空清单、重复项、路径逃逸、缺失或重复的 classpath 资源，XML 仍禁止 DTD/外部实体且限制 8 MB，清单限制 1 MB。重新发布设计时重建 Runner；不要按请求重新加载设计。


## 独立生成配置：API 子资源与业务能力分组

业务设计描述 API 契约和 System 语义归属；Java 类布局属于实现选择。Store 的 Factory 装配和 `<base-package>.<system>.<layer>` 是默认工程约定，不是 DEC 语义约束。生成器当前可配置 API 分组，其余布局/DI 选择由项目接入实现。

将 UTF-8 properties 保存于 canonical 目录和生成输出目录之外，例如 `demo/order-payment-generation.properties`：

```properties
version=dec-generation/v1
api.group.API-ORDER-START-PAYMENT=OrderPayments
api.group.API-ORDER-RECEIVE-PAYMENT-RESULT=OrderPayments
api.group.API-ORDER-BACK-TO-PAYING=OrderPayments
```

从仓库根目录生成到独立目录：

```sh
java -jar dec-java-lite/dec-lite-cli/target/dec-lite-cli-0.1.0.jar generate --yaml demo/order-payment-yaml --output /private/tmp/dec-configured-demo --base-package com.example.orderpayment --generation-config demo/order-payment-generation.properties
```

这会生成 `OrdersController/Service`（提交、查询）及 `OrderPaymentsController/Service`（支付相关操作）。未配置 API 沿用默认分组；同 System 内跨 URL 资源可归入同一显式能力组，不同 System 不合并。根路径等缺少静态资源段的 API 可指定显式组。类名前缀必须匹配 `[A-Z][A-Za-z0-9]*`，生成器追加 Controller/Service；URL、HTTP method、DTO、校验和方法名均不改变。

Java 调用可传 `new GenerationOptions(Map.of("API-ORDER-START-PAYMENT", "OrderPayments"))` 作为 `generate(project, output, basePackage, options)` 的第四参数；旧重载保持默认行为。重复键、未知键、版本不符、未知 API ID、非法类名及类/方法冲突在输出清理前报错。显式组与默认组若映射到同一类名，不会静默合并；将该组所有成员显式配置后才允许合并。

非空配置输出排序后的 `dec-generation-options.properties` 作为可重现记录并纳入 generated manifest；切回默认会清理该记录及旧分组文件，默认产物保持字节兼容。源配置应版本管理，并纳入 Binding 证据 `--input`；不要使用生成记录代替源配置管理。配置只管理生成文件，手写 ServiceImpl、Bean 注册和 Binding 符号必须由接入者同步迁移。
