# Order payment demo in depth

[Back to documentation home](README.md)

This demo is the most direct entry point for reading the whole project: start from the [canonical design](../../demo/order-payment-yaml), go through generated code and handwritten wiring, and finally run an order payment to completion inside a Spring/MySQL application. The payment platform, MongoDB, and SMS still use local stubs, so what it demonstrates is wiring and transaction boundaries, not a pretense that production services are already connected.

## 1. Suggested starting points

| Entry point | Contents |
| --- | --- |
| [canonical design](../../demo/order-payment-yaml) | Data, Views, rules, Information trees, APIs, and flows |
| [design home page](../../demo/design-html/index.html) | Browse the design from a System or a Business |
| [order Information tree](../../demo/design-html/system/order/information.html) | Facts such as ordered/payable/waitPay and their rules |
| [order payment flow](../../demo/design-html/directory/order-payment.html) | Action, case, Produce, Change, Back, and data sources |
| [design index](../../demo/order-payment-app/DEC-DESIGN-INDEX.md) | Locate code and design positions by Design ID |
| [Implementation Binding](../../demo/order-payment-implementation-binding.yaml) | Implementation symbols, Runtime nodes, tests, and statuses |
| [application README](../../demo/order-payment-app/README.md) | More complete run and scenario instructions |

## 2. How generated code and handwritten code work together

The Java paths below are relative to `demo/order-payment-app/src/main/java/com/example/orderpayment/`.

| Class/directory | Source | Responsibility |
| --- | --- | --- |
| `order/controller/OrdersController` | Generated | HTTP APIs for the six order resources |
| `order/service/OrdersService` | Generated | Service interface matching the APIs |
| `*/dto`, `*/entity`, `*/view`, `*/enums` | Generated | Transfer objects, data entities, aggregate projections, and enums |
| Managed interfaces in `*/dao`, `*/mapper` | Generated | Typed data access contracts |
| `*/information/*Information` | Generated | Read-only design navigation of the Information tree |
| `business/OrderPaymentFlow` | Generated | Flow node, Action, case, and Back constants/metadata |
| Managed configuration in `config/*Configuration` | Generated | Collects CustomAction, Listener, and DataSourceAdapter beans |
| `order/service/impl/OrdersServiceImpl` | Handwritten | Adapts APIs to the Workflow/query services |
| `order/workflow/OrderWorkflow` | Handwritten | Transactions, input, Runtime invocation, completion write back, and error mapping |
| `order/workflow/DecViewModelBridge` | Handwritten | Conversion between `OrderInfo` and the Runtime model |
| `order/view/OrderViewAssembler` | Handwritten | Assembles/persists Views through the DAO |
| `order/store/LocalOrderStore`, Factory | Handwritten | DAO access, row locking, and assembly |
| `order/workflow/LocalRuleAdapter` and others | Handwritten | Maps DEC operations to real local resources |
| `payment/action/SmsNotifyAction` | Handwritten | Custom SMS action integration |
| `business/OrderPaymentListener` | Handwritten | Before/after handling of Actions across the whole flow |
| `order/service/OrderQueryService` | Handwritten | Business query runtime and MySQL wiring |
| `order/query/OrderQueryExamples` | Handwritten | Examples of four kinds of business query calls |

For accurate generation ownership, [dec-generated-manifest.txt](../../demo/order-payment-app/dec-generated-manifest.txt) is authoritative; do not assume a whole directory is overwritable just because a class sits in a certain package. The generator never produces fake-success ServiceImpls or external service implementations.

## 3. What each of the six APIs does

The entry point is [OrdersController.java](../../demo/order-payment-app/src/main/java/com/example/orderpayment/order/controller/OrdersController.java).

| Method and path | Purpose | Main wiring |
| --- | --- | --- |
| `POST /api/orders/{orderId}/submit` | Submit an order | `OrderWorkflow.submitOrder` |
| `POST /api/orders/{orderId}/payment` | Start a payment | `OrderWorkflow.startPayment` |
| `POST /api/orders/{orderId}/payment/result` | Receive the payment result | `OrderWorkflow.receivePaymentResult` |
| `POST /api/orders/{orderId}/payment/back` | Roll back to paying | `OrderWorkflow.backToPaying` |
| `GET /api/orders/{orderId}` | Query a single order | `OrderWorkflow.getOrder` |
| `GET /api/orders` | Stage/range/relation queries | `OrderQueryService` |

These methods are grouped into a single Controller/Service around the `order + orders` resources rather than one file per method. Request DTOs are still generated according to each API's own contract.

## 4. Where the payment success and failure branches execute

[OrderWorkflow](../../demo/order-payment-app/src/main/java/com/example/orderpayment/order/workflow/OrderWorkflow.java) does not re-implement the payment result branching rules. It passes the callback value in and asks the Runtime to advance to `OrderPaymentFlow.PAY_RESULT`.

The execution chain is:

```text
receivePaymentResult
  → Read and evaluate the current aggregate; check whether it can advance
  → The receivePayResult Action of PayResult
  → Evaluate payment.hasResult and the case Information
      payment.success → success → confirmPaySuccess → Change
      payment.error   → error   → recordPayError → smsNotify → confirmPayError → Change
  → Final persistence and read-back verification
  → Spring commits
```

Aggregate facts such as `common.ordered` and `common.paying` check the order, the details, and the payment information together. The Store only reads/writes data and takes database row locks; it does not copy these predicates. The target Information of a Change describes how the state is materialized; the Flow class only presents the definition, and real execution happens in the Runtime.

Back executes fallback actions declared in the design, such as `resetPayResult`, and then restores the corresponding facts. It is a new operation in the current transaction; it does not "cancel a database transaction that was already committed".

## 5. Failure notification and the flow listener

`ACT-SMS-NOTIFY / smsNotify` in the design has no `ruleRef`, so it is looked up by name as [SmsNotifyAction](../../demo/order-payment-app/src/main/java/com/example/orderpayment/payment/action/SmsNotifyAction.java). The generated configuration collects CustomAction beans, and the Workflow injects the registry instead of calling the class directly.

The action registers `afterCommit` with the Spring transaction synchronization and calls `SmsSender.sendPaymentError` once the commit succeeds. The current `LoggingSmsSender` only logs; when replacing it with a real platform, provide a single, unambiguous `SmsSender` implementation.

SMS timing involves three distinct events:

1. The Action executed successfully: the post-commit callback has been registered.
2. The database transaction committed successfully: the callback starts executing.
3. The SMS platform send succeeded: determined by the concrete Sender.

Nothing is sent when the database rolls back. A send failure after commit cannot undo the order, and repeated business callbacks will not automatically resend it; there is currently no persistent Outbox or reliable retry guarantee.

[OrderPaymentListener](../../demo/order-payment-app/src/main/java/com/example/orderpayment/business/OrderPaymentListener.java) binds to `BUS-ORDER-PAYMENT` through `businessId()`, receives before/after for every Action in the flow, and distinguishes handling by Action ID within the class. It can call `context.view(OrderInfo.class)` directly to read the current model snapshot and use `context.write` when it needs to write.

A listener's after does not mean the SMS was delivered, and it should not send the same SMS again. Listener instances hold no request state and do not copy the state machine.

## 6. How design data sources connect to local implementations

The flow's default logical source is `data1`. The start-payment Action explicitly specifies `paymentService`, the error-recording Action specifies `mongo1`, and the fallback reset specifies `data1`. Effective routes are resolved along Action→Rule→RuleView→Business, and the HTML shows these declarations and their sources.

The application registers local DEC adapters through [OrderDataSourceConfiguration](../../demo/order-payment-app/src/main/java/com/example/orderpayment/order/workflow/OrderDataSourceConfiguration.java). Java callers can select other registered implementations per call:

```java
var selection = new java.util.HashMap<>(OrderWorkflow.DEFAULT_DATA_SOURCES);
selection.put("data1", "my-database-adapter"); // This DEC Adapter name must be registered first.
workflow.receivePaymentResult(orderId, PayResultCode.SUCCESS, selection);
```

The existing HTTP APIs use the default selection. The selection only affects how DEC external operations are dispatched; it does not automatically swap the Store/DAO JDBC DataSource, and custom implementations must still wire transaction resources correctly.

The local `data1` accesses `dec_demo_*` tables over MySQL JDBC. `paymentService` is a local payment stub, `mongo1`'s error records are simulated with local tables, and SMS is a logging implementation. They can verify flow wiring, but they do not mean real third-party platforms are integrated.

## 7. Query orders by business stage

For Java examples see [OrderQueryExamples](../../demo/order-payment-app/src/main/java/com/example/orderpayment/order/query/OrderQueryExamples.java); for the service wiring see [OrderQueryService](../../demo/order-payment-app/src/main/java/com/example/orderpayment/order/service/OrderQueryService.java).

```java
// orders is the injected OrderQueryService; it returns a QueryResult.
orders.find(BusinessQuery.find("订单").eq("支付结果"));
orders.find(BusinessQuery.find("订单").start("已订购"));
orders.find(BusinessQuery.find("订单").start("已订购").end("支付成功"));
orders.find(BusinessQuery.find("订单").with("用户")
        .start("已订购").end("支付成功"));
```

The Chinese names are mapped to existing View/Directory IDs through explicit aliases. "支付结果" is the union of the success and error categories; the range from 已订购 to 支付成功 covers 已订购, 支付中, and 支付成功, excluding failure branches that are not on the path toward success.

These queries evaluate the **current facts**. If an order is rolled back and its result reset after a successful payment, it may no longer match the payment-result query; this does not query the history of "ever paid successfully".

The corresponding HTTP calls:

```sh
curl --get 'http://localhost:8080/api/orders' --data-urlencode 'eq=支付结果'
curl --get 'http://localhost:8080/api/orders' --data-urlencode 'start=已订购'
curl --get 'http://localhost:8080/api/orders' \
  --data-urlencode 'start=已订购' --data-urlencode 'end=支付成功'
curl --get 'http://localhost:8080/api/orders' \
  --data-urlencode 'start=已订购' --data-urlencode 'end=支付成功' \
  --data-urlencode 'with=用户,明细' --data-urlencode 'offset=0' --data-urlencode 'size=20'
```

The response is a JSON array of `OrderInfo`. `with` accepts comma-separated relations; pagination defaults to offset=0 and size=50, with size ranging from 1–500. Unknown stages or relations, unreachable ranges, mixing eq with ranges, and invalid pagination parameters return 400; database/Runtime failures are not disguised as empty arrays.

The service automatically loads `payInfo` and `orderDetailList` as required by the evaluation rules, and an extra `with("用户")` loads the optional user relation. A missing optional user does not drop the order, and multiple details do not return duplicate root orders. Logical tables map to `dec_demo_*` physical tables, while the conditions still come from the design.

Queries use a Spring read-only transaction and transaction-aware connections; a new transaction requests REPEATABLE_READ, and when joining an existing transaction the outer transaction's isolation level prevails. Do not bypass the Spring proxy by manually creating `new OrderQueryService` and then invoking queries on it.

## 8. How to run and verify this example

See the [development and verification handbook](development-and-verification.md) for environment and build commands. Database tests must use a dedicated test database, because the tests clean up the `dec_demo_*` data in it.

[OrderQueryTest](../../demo/order-payment-app/src/test/java/com/example/orderpayment/demo/OrderQueryTest.java) creates four orders, 701–704, through the real Workflow, in the states 已订购, 支付中, 支付成功, and 支付失败 respectively. The expected results of the four query types are `[703,704]`, `[701,702,703,704]`, `[701,702,703]`, and `[701,702,703]` in order, also covering a missing user, multiple details, parameter binding, and changes after Back.

Other scenarios cover concurrent payments on the same order, duplicate/out-of-order callbacks, final write-back failures, read-back inconsistency, listener failures, and post-commit SMS failures. The cross-domain expense approval example lives at [expense-approval](../../dec-java-lite/examples/expense-approval/README.md) and checks whether the framework over-fits to the order domain.

## 9. What this demo does not promise

- The submit API submits one product at a time, as designed; multi-detail tests prepare aggregates through the DAO to verify the one-to-many model and the Runtime, not to claim a bulk ordering API exists.
- Duplicate/illegal advances are rejected, but the current design has no callbackId/paymentAttemptId, so late callbacks from different payment attempts after a Back cannot be reliably distinguished.
- Current queries are not event-history queries; SMS is not reliable message delivery; multiple adapters are not cross-source atomic transactions.
- Four optional nodes that had never been triggered before have been added to the demo scenarios and verified by real runs: `RV-ORDER-IS-WAIT-PAY`, `RULE-ORDER-IS-WAIT-PAY`, `INFO-ORDER-WAIT-PAY`, and `INFO-PAYMENT-COMPLETED`. `OrderQueryTest#optionalInformationBranchesAreEvaluatedFromTheDemoDesign` compiles RuleView/Information from the current XML and runs evaluation assertions against waiting-state, payment-success, and payment-failure models; the Binding now stands at 118/118 `VERIFIED`, and `--require-ready` passes.

These boundaries are the starting point for further extending the design, adaptation, and verification; see the [generality scenario matrix](../../dec-java-lite/docs/generality-scenarios.md) for details.
