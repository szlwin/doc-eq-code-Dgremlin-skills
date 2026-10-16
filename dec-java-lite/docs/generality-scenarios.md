# 通用性场景回归

这些回归由常规 reactor 与 Demo 测试执行，重点检查持久化结果、动作次数及设计驱动的行为变化。

| 场景 | 入口与断言 |
|---|---|
| 同订单并发发起支付 | `OrderWorkflowTest.concurrentPaymentStartsCreateExactlyOnePayment`：两个线程屏障同时进入 Spring Workflow，恰好一个成功、一个 409；MySQL 仅一条支付及对应明细。 |
| 重复、乱序回调 | `duplicateConcurrentCallbacksAndLateConflictingResultDoNotRepeatSideEffects`：发起前回调拒绝；两个失败回调竞争仅一个成功；重复失败和迟到成功均拒绝，错误记录与短信均一次。既有测试也覆盖成功后迟到失败。 |
| 多明细 | `multipleDetailsAreLoadedPaidChangedAndRolledBackAsOneOrder`：第二条明细主键不同于订单；完整读取、生成支付明细、金额汇总、失败回滚、Back、重试成功及其他订单隔离。第二条明细状态损坏时 Information 校验必须拒绝。 |
| Action 失败、超时 | `ExpenseApprovalTest`：审批使用真实 H2 数据库；外部托管和 Runtime 自管两种事务分别覆盖资格失败、最后 Action 失败、请求已到期、最后 Action 返回时到期和完成回写到期，检查数据库回滚及 afterCommit 不发生。使用测试 Clock，无等待时间断言。 |
| 提交后短信失败 | `afterCommitSmsFailureLeavesCommittedStateAndRetryDoesNotResend`：发送器抛异常，MySQL 仍为支付失败终态；重复业务回调 409，不再次发送。 |
| Information 变更 | `ExpenseDesignEvolutionTest`：审批门槛从 1000 改为 500，重生成、JDK 编译生成投影，800 的请求由允许变为拒绝；旧设计实例仍允许。 |
| Directory 变更 | 同一测试类删除中间提交节点并调整边，重新生成及编译 Flow；路径从两个动作变为一个，单步合法性同步改变。 |
| 数值语义 | `NumericComparisonTest`：金额临界值、高精度小数、大整数相等、非法类型、缺失值和集合内数值比较。 |

费用审批设计位于 `fixtures/expense-approval`；可编译工程和执行说明位于 `examples/expense-approval`。它使用费用、审核资格与审批状态，不依赖订单、支付或短信类。模型 ruleData 的数值比较扩展由这个场景发现并验证。

## 覆盖边界

订单 submit API 的设计仍为单商品输入，多明细由 DAO 准备，未新增批量下单 API。支付服务和短信仍使用替身，并未证明真实提供方的网络行为。重复回调的当前契约是拒绝重复推进，不是返回同一成功响应的幂等协议。

现有回调缺少支付尝试/事件标识，不能判断 Back 重试后的迟到回调属于哪次尝试；这一点需要明确的业务契约后再实现。afterCommit 失败不能撤销已提交数据库，也不会自动补发短信。本次没有引入可靠消息投递协议。

TIMEOUT 在同步调用前后及提交前检查，无法强行中断不返回的客户端，也无法撤销已发送的外部副作用。Custom Action 仍需依据 deadline 设置 I/O 超时。H2 审批测试证明两种事务接入的回滚语义，MySQL 订单测试证明当前 JDBC 接线；二者不代表所有数据库和外部服务都已验证。
