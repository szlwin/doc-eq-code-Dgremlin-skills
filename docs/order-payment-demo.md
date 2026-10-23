# 订单支付示例详解

[返回文档总览](README.md)

这份 Demo 是阅读整个项目最直接的入口：从 [canonical 设计](../demo/order-payment-yaml) 出发，经过生成代码和手写接线，最后在 Spring/MySQL 应用里跑完订单支付。支付平台、MongoDB 和短信仍使用本地替身，所以它展示的是接线和事务边界，不是假装已经接通生产服务。

## 1. 建议先看这几处

| 入口 | 内容 |
| --- | --- |
| [canonical 设计](../demo/order-payment-yaml) | 数据、View、规则、信息树、API 和流程 |
| [设计首页](../demo/design-html/index.html) | 从 System 或 Business 浏览设计 |
| [订单信息树](../demo/design-html/system/order/information.html) | ordered/payable/waitPay 等事实及规则 |
| [订单支付流程](../demo/design-html/directory/order-payment.html) | Action、case、Produce、Change、Back 和数据源 |
| [设计索引](../demo/order-payment-app/DEC-DESIGN-INDEX.md) | 按 Design ID 找到代码及设计定位 |
| [Implementation Binding](../demo/order-payment-implementation-binding.yaml) | 实现符号、Runtime 节点、测试与状态 |
| [应用 README](../demo/order-payment-app/README.md) | 更完整的运行与场景说明 |

## 2. 生成代码和手写代码怎样配合

以下 Java 路径相对 `demo/order-payment-app/src/main/java/com/example/orderpayment/`。

| 类/目录 | 来源 | 职责 |
| --- | --- | --- |
| `order/controller/OrdersController` | 生成 | 六个订单资源 HTTP API |
| `order/service/OrdersService` | 生成 | API 对应 Service 接口 |
| `*/dto`、`*/entity`、`*/view`、`*/enums` | 生成 | 传输对象、数据实体、聚合投影及枚举 |
| `*/dao`、`*/mapper` 中受管接口 | 生成 | 类型化数据访问契约 |
| `*/information/*Information` | 生成 | 信息树的只读设计导航 |
| `business/OrderPaymentFlow` | 生成 | 流程节点、Action、case、Back 常量/元数据 |
| `config/*Configuration` 中受管配置 | 生成 | 收集 CustomAction、Listener、DataSourceAdapter Bean |
| `order/service/impl/OrdersServiceImpl` | 手写 | API 到 Workflow/查询服务的适配 |
| `order/workflow/OrderWorkflow` | 手写 | 事务、输入、Runtime 调用、完成回写与错误映射 |
| `order/workflow/DecViewModelBridge` | 手写 | `OrderInfo` 与 Runtime 模型转换 |
| `order/view/OrderViewAssembler` | 手写 | 经 DAO 装配/持久化 View |
| `order/store/LocalOrderStore`、Factory | 手写 | DAO 访问、行锁与装配 |
| `order/workflow/LocalRuleAdapter` 等 | 手写 | DEC 操作到真实本地资源的映射 |
| `payment/action/SmsNotifyAction` | 手写 | 自定义短信动作接入 |
| `business/OrderPaymentListener` | 手写 | 整个流程的 Action 前后处理 |
| `order/service/OrderQueryService` | 手写 | 业务查询运行时与 MySQL 接线 |
| `order/query/OrderQueryExamples` | 手写 | 四种业务查询调用示例 |

准确的生成归属以 [dec-generated-manifest.txt](../demo/order-payment-app/dec-generated-manifest.txt) 为准，不能因为类在某个 package 就认定整个目录都可覆盖。生成器不会生成假成功的 ServiceImpl 或外部服务实现。

## 3. 六个接口分别做什么

入口为 [OrdersController.java](../demo/order-payment-app/src/main/java/com/example/orderpayment/order/controller/OrdersController.java)。

| 方法与路径 | 用途 | 主要接入 |
| --- | --- | --- |
| `POST /api/orders/{orderId}/submit` | 提交订单 | `OrderWorkflow.submitOrder` |
| `POST /api/orders/{orderId}/payment` | 发起支付 | `OrderWorkflow.startPayment` |
| `POST /api/orders/{orderId}/payment/result` | 接收支付结果 | `OrderWorkflow.receivePaymentResult` |
| `POST /api/orders/{orderId}/payment/back` | 回退至支付中 | `OrderWorkflow.backToPaying` |
| `GET /api/orders/{orderId}` | 单订单查询 | `OrderWorkflow.getOrder` |
| `GET /api/orders` | 阶段/范围/关联查询 | `OrderQueryService` |

这些方法按 `order + orders` 资源归为一个 Controller/Service，而不是每个方法一个文件。请求 DTO 仍按各自 API 契约生成。

## 4. 支付成功和失败的分支在哪里执行

[OrderWorkflow](../demo/order-payment-app/src/main/java/com/example/orderpayment/order/workflow/OrderWorkflow.java) 不重新维护支付结果分支规则。它传入回调值并请求 Runtime 推进到 `OrderPaymentFlow.PAY_RESULT`。

运行链路为：

```text
receivePaymentResult
  → 读取并识别当前聚合、校验能否推进
  → PayResult 的 receivePayResult Action
  → payment.hasResult 与 case Information 求值
      payment.success → success → confirmPaySuccess → Change
      payment.error   → error   → recordPayError → smsNotify → confirmPayError → Change
  → 最终持久化、回读校验
  → Spring 提交
```

`common.ordered`、`common.paying` 等聚合事实同时核对订单、明细和支付信息。Store 只读取/写入数据及加数据库行锁，不复制这些谓词。Change 的目标 Information 描述状态如何物化；Flow 类仅展示定义，真正执行在 Runtime。

Back 执行设计中的 `resetPayResult` 等回退动作，再恢复对应事实。它是当前事务的一次新操作，不会“取消曾经提交过的数据库事务”。

## 5. 失败通知和流程监听器

设计中的 `ACT-SMS-NOTIFY / smsNotify` 没有 `ruleRef`，因此按名称查找 [SmsNotifyAction](../demo/order-payment-app/src/main/java/com/example/orderpayment/payment/action/SmsNotifyAction.java)。生成的配置收集 CustomAction Bean，Workflow 注入注册表，不直接调用该类。

该动作在 Spring 事务同步中注册 `afterCommit`，提交成功后调用 `SmsSender.sendPaymentError`。当前 `LoggingSmsSender` 只打印日志；替换为真实平台时应提供明确唯一的 `SmsSender` 实现。

短信时序有三个不同事件：

1. Action 执行成功：提交后回调已注册。
2. 数据库事务提交成功：回调开始执行。
3. 短信平台发送成功：由具体 Sender 确定。

数据库回滚时不发送。提交后发送失败不能撤销订单，也不会由重复业务回调自动补发；当前没有持久化 Outbox 和可靠重试承诺。

[OrderPaymentListener](../demo/order-payment-app/src/main/java/com/example/orderpayment/business/OrderPaymentListener.java) 通过 `businessId()` 绑定 `BUS-ORDER-PAYMENT`，接收该流程全部 Action 的 before/after，在类内按 Action ID 区分处理。它可以直接调用 `context.view(OrderInfo.class)` 读取当前模型快照，需要写入时使用 `context.write`。

监听器 after 不等于短信已送达，也不应再次发送同一短信。监听器实例不存请求状态，不抄写状态机。

## 6. 设计里的数据源怎样接到本地实现

流程默认逻辑源为 `data1`。发起支付 Action 显式指定 `paymentService`，记录错误指定 `mongo1`，回退重置指定 `data1`。有效路由按 Action→Rule→RuleView→Business 解析，HTML 可查看这些声明和来源。

应用通过 [OrderDataSourceConfiguration](../demo/order-payment-app/src/main/java/com/example/orderpayment/order/workflow/OrderDataSourceConfiguration.java) 注册本地 DEC 适配器。Java 调用方可按次选择已注册的其他实现：

```java
var selection = new java.util.HashMap<>(OrderWorkflow.DEFAULT_DATA_SOURCES);
selection.put("data1", "my-database-adapter"); // 必须先注册这个 DEC Adapter 名称。
workflow.receivePaymentResult(orderId, PayResultCode.SUCCESS, selection);
```

现有 HTTP API 使用默认选择。该选择只影响 DEC 外部操作分派，不会自动更换 Store/DAO 的 JDBC DataSource；自定义实现还需正确接入事务资源。

本地 `data1` 经 MySQL JDBC 访问 `dec_demo_*` 表。`paymentService` 是本地支付替身，`mongo1` 的错误记录由本地表模拟，短信是日志实现。它们能验证流程接线，但不代表真实第三方平台已经集成。

## 7. 用业务阶段查询订单

Java 示例见 [OrderQueryExamples](../demo/order-payment-app/src/main/java/com/example/orderpayment/order/query/OrderQueryExamples.java)，服务接线见 [OrderQueryService](../demo/order-payment-app/src/main/java/com/example/orderpayment/order/service/OrderQueryService.java)。

```java
// orders 是注入的 OrderQueryService；返回 QueryResult。
orders.find(BusinessQuery.find("订单").eq("支付结果"));
orders.find(BusinessQuery.find("订单").start("已订购"));
orders.find(BusinessQuery.find("订单").start("已订购").end("支付成功"));
orders.find(BusinessQuery.find("订单").with("用户")
        .start("已订购").end("支付成功"));
```

中文名称通过显式别名映射到现有 View/Directory ID。“支付结果”是 success/error 两类的并集；“已订购到支付成功”包含已订购、支付中、支付成功，排除不在通向成功路径上的失败分支。

这些查询判断**当前事实**。支付成功后若回退并重置了结果，它可能不再匹配支付结果查询；这里没有查询“曾经支付成功”的历史记录。

对应 HTTP 调用：

```sh
curl --get 'http://localhost:8080/api/orders' --data-urlencode 'eq=支付结果'
curl --get 'http://localhost:8080/api/orders' --data-urlencode 'start=已订购'
curl --get 'http://localhost:8080/api/orders' \
  --data-urlencode 'start=已订购' --data-urlencode 'end=支付成功'
curl --get 'http://localhost:8080/api/orders' \
  --data-urlencode 'start=已订购' --data-urlencode 'end=支付成功' \
  --data-urlencode 'with=用户,明细' --data-urlencode 'offset=0' --data-urlencode 'size=20'
```

返回 `OrderInfo` JSON 数组。`with` 支持逗号分隔关系；分页默认 offset=0、size=50，size 范围 1～500。未知阶段、关联、不可达范围、eq 与范围混用以及非法分页参数返回 400；数据库/Runtime 失败不会伪装成空数组。

服务自动补入识别规则需要的 `payInfo`、`orderDetailList`，额外的 `with("用户")` 加载可选用户关系。缺少可选用户不删除订单，多明细不重复返回根订单。逻辑表映射到 `dec_demo_*` 物理表，条件仍来自设计。

查询使用 Spring 只读事务及事务感知连接；新事务请求 REPEATABLE_READ，加入已有事务时以外层事务的隔离级别为准。不能绕过 Spring 代理手动 `new OrderQueryService` 后调用查询。

## 8. 怎样运行和验证这个例子

环境与构建命令见 [开发与验证手册](development-and-verification.md)。数据库测试须使用专用测试库，因为测试会清理其中的 `dec_demo_*` 数据。

[OrderQueryTest](../demo/order-payment-app/src/test/java/com/example/orderpayment/demo/OrderQueryTest.java) 通过真实 Workflow 创建 701～704 四个订单，分别为已订购、支付中、支付成功、支付失败。四类查询预期结果依次为 `[703,704]`、`[701,702,703,704]`、`[701,702,703]`、`[701,702,703]`，同时覆盖用户缺失、多明细、参数绑定和 Back 后的变化。

其他场景覆盖同订单并发支付、重复/乱序回调、最终写回失败、回读不一致、监听器失败和提交后短信失败。跨领域的费用审批示例位于 [expense-approval](../dec-java-lite/examples/expense-approval/README.md)，用于检查框架是否过度依赖订单领域。

## 9. 这个 Demo 没有承诺什么

- submit API 按设计一次提交一个商品；多明细测试通过 DAO 准备聚合，验证一对多模型与 Runtime，而不是宣称已有批量下单 API。
- 重复/非法推进被拒绝，但现有设计没有 callbackId/paymentAttemptId，不能可靠区分 Back 后不同支付尝试的迟到回调。
- 当前查询不是事件历史查询；短信不是可靠消息投递；多适配器不是跨源原子事务。
- 四个此前未触发的可选节点已补入 Demo 场景并通过真实运行验证：`RV-ORDER-IS-WAIT-PAY`、`RULE-ORDER-IS-WAIT-PAY`、`INFO-ORDER-WAIT-PAY`、`INFO-PAYMENT-COMPLETED`。`OrderQueryTest#optionalInformationBranchesAreEvaluatedFromTheDemoDesign` 从当前 XML 编译 RuleView/Information，并对等待态、支付成功和支付失败模型执行识别断言；Binding 现为 118/118 `VERIFIED`，`--require-ready` 通过。

这些边界是继续扩展设计、适配和验证的起点，详见 [通用性场景矩阵](../dec-java-lite/docs/generality-scenarios.md)。
