# 可运行的订单支付 Demo

本工程由 canonical DEC YAML 生成 Controller、DTO、Entity、View、Enum、DAO 和 Mapper，并生成 `order/information/OrderInformation`、`payment/information/PaymentInformation`、`user/information/UserInformation` 与 `business/OrderPaymentFlow` 设计投影。Information 类对应信息树，保留识别表达式、依赖、change-data 和 View/Rule 引用；Business Flow 类对应 Directory，保留依赖、Action、Produce、case、Change 和 Back。它们是只读导航元数据，不替代运行时执行。`src/main/java/com/example/orderpayment/order/`、`payment/` 和 `user/` 下的应用包包含手写绑定，不在 `dec-generated-manifest.txt` 中；重新生成不会覆盖它。运行时加载 `src/main/resources/dec/design/` 中由 `dec-design` 正式转换器生成的 XML，编译 RuleView、Information 和 Business Directory，并执行完整订单支付路径。

订单资源的 5 个 API 集中在 `order/controller/OrdersController.java` 和 `order/service/OrdersService.java`，由 `order/service/impl/OrdersServiceImpl.java` 统一委托给 `OrderWorkflow`。分组依据是 System 与 URL 主资源（`order` + `orders`）；方法各自保留 Design ID、请求 DTO 和路由。

从代码查设计时，先看生成类中的 `Design ID + YAML#节点路径 + XML#节点路径` 注释，再用 [DEC-DESIGN-INDEX.md](DEC-DESIGN-INDEX.md) 交叉定位设计和代码；手写实现的 Design ID、类和验证用例记录在 `../order-payment-implementation-binding.yaml`。`OrderWorkflow` 使用 `OrderPaymentFlow` 的 Directory 名称常量，负责 API 输入、View/Entity 转换、DAO 完成回写和错误映射；通用 `DirectoryWorkflowRunner.advance(...)` 负责 Directory、RuleView、Information、Action 和 Session 编排，`OrderViewAssembler` 通过生成 DAO 读取 Entity 并装配 View，`LocalRuleAdapter` 执行外部 Rule 的 DAO 写入，`DecViewModelBridge` 适配 DEC Runtime 的模型，`LocalOrderStoreFactory` 注入已有 DAO 并创建 `LocalOrderStore`。Store 是普通对象，不是 Spring Bean，也不在自身内部持有锁。

在仓库根目录运行：

```sh
./dec-java-lite/mvnw -f dec-java-lite/pom.xml install -DskipTests
./dec-java-lite/mvnw -f demo/order-payment-app/pom.xml test
./dec-java-lite/mvnw -f demo/order-payment-app/pom.xml spring-boot:run
```

Demo 默认连接 `jdbc:mysql://127.0.0.1:3306/demo-test2`，用户名为 `root`，密码为 `mysqldb`。也可以通过 `DEC_DB_URL`、`DEC_DB_USERNAME` 和 `DEC_DB_PASSWORD` 覆盖配置。启动时 `schema.sql` 会创建 `dec_demo_` 前缀的业务演示表；这些表按当前 DEC YAML 字段映射设计，不修改数据库中已有的业务表。Directory 状态直接从订单和支付实体状态推导，不单独建状态表。

接口执行顺序：

```sh
curl -X POST http://localhost:8080/api/orders/101/submit \
  -H 'Content-Type: application/json' \
  -d '{"status":"1","type":"1","userId":1,"productId":7,"productName":"Demo item","productPrice":14.95,"productCount":2,"remark":"demo-order"}'
curl -X POST http://localhost:8080/api/orders/101/payment
curl -X POST http://localhost:8080/api/orders/101/payment/result \
  -H 'Content-Type: application/json' -d '{"resultCode":2}'
curl http://localhost:8080/api/orders/101
curl -X POST http://localhost:8080/api/orders/101/payment/back
curl -X POST http://localhost:8080/api/orders/101/payment/result \
  -H 'Content-Type: application/json' -d '{"resultCode":1}'
```

`userId=2` 是本地未激活用户样例，支付依赖检查会失败且订单维持已下单状态。其他正整数用户 ID 在本地适配器中视为已激活且已认证。一个提交请求创建一条订单明细，数量由 `productCount` 表示。

此 Demo 的 `data1` 通过 MySQL JDBC 适配器访问 `dec_demo_` 表；`paymentService`、`mongo1` 与短信动作仍使用本地替身。订单和支付实体通过生成 DAO/Mapper 读写。设计中的 `RULE-RECORD-PAY-ERROR` 指向 `mongo1`；本地 `LocalPaymentErrorAdapter` 用 `dec_demo_payment_error` 表模拟其写入，并在 Rule 执行时参与当前 Spring 事务，`DirectoryWorkflowRunner.assertDirectoryMatches` 用 canonical Information 核对回读后的持久化 View，数据库事务由 `OrderWorkflow` 的 Spring `@Transactional` 提交或回滚。短信由 `SmsNotifyAction` 调用 `SmsSender` 扩展点，数据库提交后发送；数据库事务回滚时不发送，提交后的短信发送失败不能撤销订单。

自定义短信 Action 的接入链路是 `business-order-payment.yaml` 的 `ACT-SMS-NOTIFY / smsNotify` → `payment/action/SmsNotifyAction` → `SmsSender`。`business/OrderPaymentFlow.ERROR` 的 Action 投影显示 `customType = "smsNotify"`。生成的 `config/CustomActionConfiguration` 自动收集 `CustomAction` Bean 并注册，`OrderWorkflow` 只注入注册表；新增自定义 Action 需增加设计声明和实现 Bean，然后重新生成设计产物。初始化时检查缺失注册，重复名称也会报错。

订单已发起支付后，`POST /api/orders/{orderId}/payment/result` 携带 `{"resultCode":2}` 会进入失败分支并安排短信通知。当前 `LoggingSmsSender` 只打印日志；接入真实平台时用自己的 `SmsSender` Bean 替换它，确保注入唯一明确。`afterCommit` 不提供持久重试保证；需要可靠投递时按项目约定接入相应机制。


业务事实以 canonical YAML 为准：`common.ordered` 同时要求订单/明细已下单且无支付单；`common.paying` 要求订单/明细支付中、支付单处理中且无结果。`LocalOrderStore` 只负责 DAO 和行锁，`OrderWorkflow` 用 Runtime 恢复状态、按流程边校验单步命令，并在写回后重新读取 View 验证。订单/明细初始化状态来自已校验的请求，DataOperationAdapter 不再重复写状态常量。请求字段约束使用生成 DTO 的 Jakarta Validator；字符串空白按设计的 length 约束处理，不额外添加未声明的非空白规则。Demo 支付 provider 创建 PROCESSING 返回值和 demo-user 初始化仍是本地替身行为。


事务接入使用 `dec-lite-spring` 的 `SpringWorkflowTransactions.externallyManaged()`，由方法上的 `@Transactional` 拥有真实 JDBC 事务。Runtime 检查外部事务有效性；Directory Actions 与最终写回/回读校验均处于该事务内，失败标记回滚。Demo 不再传空 `ActionTransaction`。Runtime 自管模式的完整数据库验证位于框架 `dec-lite-spring` 测试，Demo 保持外层 Spring 托管。


设计资源通过生成的 `dec/design-files.txt` 和框架 `DecXmlParser.parseClasspath(...)` 直接加载；Workflow 不再维护文件列表或临时目录。应用启动构建一个 Runner，RuleView 编译结果由 Runner 保存，每个请求只绑定自己的 adapter、模型和事务。更新设计需要重新构建 Runner。`LocalOrderStore` 直接暴露 DAO 访问器，已移除仅转发 DAO 的 UnitOfWork/begin 包装；Store 生命周期不代表事务生命周期。


新增通用性回归覆盖同订单并发支付、重复并发及乱序回调、多订单明细、事务回滚和提交后短信失败。明细按 orderId/payId 外键完整读取，并逐条写回状态；不再以“明细主键等于订单主键”或 get(0) 代表集合。支付替身为每条订单明细创建对应支付明细，测试同时验证其他订单不受影响。

当前 submit API 仍按 canonical 契约提交一个商品；多明细测试通过 DAO 准备完整聚合，验证已声明的一对多 View 和 Runtime every(...) 行为。回调重复/非法推进仍返回 409；没有 callbackId/paymentAttemptId 的现有设计无法识别 Back 后跨支付尝试的迟到回调，也不提供恰好一次投递。短信 afterCommit 抛异常时数据库保持已提交，重复业务回调不会补发短信；可靠投递仍需独立 Outbox/消息契约。完整覆盖矩阵见 `../../dec-java-lite/docs/generality-scenarios.md`。


`../order-payment-generation.properties` 提供可选的子资源分组示例，可将支付操作生成到 OrderPaymentsController/Service。当前已提交 Demo 继续使用默认 OrdersController/Service 和 Factory 装配；样例配置没有自动启用。若采用配置，应在独立输出目录验证，然后迁移手写 ServiceImpl、更新 Binding 和证据。Store 的 Factory 与具体类布局是本 Demo 的工程选择，不是 DEC 语义要求。

## 业务流程监听器

一个业务流程绑定一个监听器。`business.OrderPaymentListener` 通过 `businessId()` 返回 `OrderPaymentFlow.ID`（`BUS-ORDER-PAYMENT`），统一接收该流程所有 Action 的 before/after 事件，包括 success/error 分支及 Back Action。在同一个类内按 `context.action().id()` 编写不同处理逻辑，不为每个 Action 单独建立监听类。

生成的 `config.ActionListenerConfiguration` 收集监听器 Bean；`OrderWorkflow` 注入注册表并传给 Runner。同一流程重复注册或引用不存在的 Business ID 会报错；未配置监听器的流程保持原有行为。before/after 按实际 Action 执行顺序逐个触发，失败/跳过的 Action 不调用 after。

读取当前工作模型使用 `context.read(path)`，写入使用 `context.write(path, value)` 保留权限检查和变更追踪。监听器异常使流程失败并按事务契约回滚；实例不保存请求状态，不复制设计业务规则。after 仍在提交前，不等于短信已经送达；短信继续由 `SmsNotifyAction` 注册的 Spring 提交回调发送。

框架接口与非 Spring 接入见 `../../dec-java-lite/docs/action-runtime.md`。

## 类型化 View 与 DataSource 选择

监听器可直接读取当前 Action 的 View：

```java
OrderInfo order = context.view(OrderInfo.class);
```

它由流程入口的 ModelCodec 自动转换，before/after 分别看到当前状态。返回值是快照；setter 不自动写回，需要修改时用 `context.write(path, value)`。

`order.workflow.OrderDataSourceConfiguration` 手动注入名为 local 的 DEC 数据源适配器；增加其他实现时声明 `DataSourceAdapter` Bean，生成的配置会统一收集。Java 调用方可按本次执行选择实现：

```java
var selection = new HashMap<>(OrderWorkflow.DEFAULT_DATA_SOURCES);
selection.put("data1", "my-database-adapter"); // 需要先注册同名 DataSourceAdapter Bean
workflow.receivePaymentResult(orderId, PayResultCode.SUCCESS, selection);
```

selection 左侧是设计逻辑数据源，右侧是适配器实现名。submitOrder/startPayment/receivePaymentResult/backToPaying/getOrder 均提供对应重载；原 HTTP API 继续使用默认选择。选择不改变下一次请求，缺失或错误路由在流程动作执行前拒绝。

此功能只分派 DEC Rule 的外部数据操作，不自动更换应用 DAO 的 JDBC 数据库。自定义实现应遵循原有事务资源接入契约。

### 流程默认与 Action 覆盖

订单流程在 `business-order-payment.yaml` 声明默认 `dataSource: data1`。saveOrder 的 RuleView 不再重复配置，继承流程默认；startPay 显式使用 paymentService，recordPyaError 显式使用 mongo1，Back resetPayResult 显式使用 data1。优先级为 Action → Rule → RuleView → Business。逻辑名称仍通过工作流执行参数选择 `local` / 手工注册的其他实现。生成的 `OrderPaymentFlow.DATA_SOURCE` 与 ActionNode 可查看声明，`demo/design-html/directory/order-payment.html` 显示流程默认、Action 覆盖和逐 Rule 有效来源。
