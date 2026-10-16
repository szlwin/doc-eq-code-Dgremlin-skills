# dec-design → dec-implementation → dec-java-lite 验证

设计输入是 `order-payment-yaml/` 的 10 份 canonical YAML。当前设计有 111 个稳定 Design ID、5 个 API、19 个 Information。YAML 是唯一设计源；XML 和 HTML 均由正式转换脚本生成。生成器负责传输/模型骨架及 Information/Business 只读设计投影，`order-payment-app/src/main/java/com/example/orderpayment/order/`、`payment/` 和 `user/` 负责本地适配和运行时接线。

| 环节 | 当前状态 |
| --- | --- |
| 设计校验 | 10 文件、111 个 Design ID，跨文件引用通过 |
| YAML → XML/HTML | 10 份 XML、38 个 HTML 页面，可重新生成 |
| Java 生成 | 1 组 OrdersController/OrdersService 承载 5 个 API，DTO、View、Enum、DAO、Mapper 受 manifest 管理 |
| 应用执行 | 下单、支付发起、结果成功/失败、短信 Action、回退、查询由 DEC Runtime 驱动 |
| 验证 | 直接 Runtime 场景、Spring HTTP、DAO/Entity 状态与 Enum JSON 测试；Binding 111/111 完整，107 VERIFIED、4 PLANNED |

应用按同一 canonical 设计加载 `dec/design/*.xml`，使用 `RuleViewCompiler`、`InformationEngine`、`DirectoryEngine` 与显式 `DataOperationAdapter`/`CustomActionRegistry`。生成的 Information 类和 Business Flow 类提供信息树与 Directory 流程的只读导航投影；它们列出依赖、Action、Produce、case 和 Back，但不执行状态转换。生成的 `OrdersService` 是接口，手写 `OrdersServiceImpl` 统一实现 5 个 API 方法。失败时应用不发布快照或本地短信 Action。

生成文件顶部的 Design ID 注释、Information 投影、Business Flow 投影和 `order-payment-app/DEC-DESIGN-INDEX.md` 提供设计到生成代码的入口；索引中的 YAML 文件位于 `order-payment-yaml/`。运行时节点与手写代码的对应位置由 `order-payment-implementation-binding.yaml` 指定。业务状态由生成的 View/Entity 承载：`OrderViewAssembler` 经生成 DAO 读取，`LocalRuleAdapter` 经 DAO 写入，`DecViewModelBridge` 负责 DEC Runtime 的 Map 边界。

MySQL 通过实际 JDBC 访问 Demo 表；支付服务、MongoDB 与短信由本地适配器模拟；它证明 Demo 的业务流程可执行，不构成外部系统接入或持久化能力验证。真实适配器、物理事务与跨系统补偿仍应在生产集成时按 Binding 单独验证。


业务事实去重后，Store 不再维护状态 switch，Workflow 不再维护允许前置节点的名单。Runtime 提供稳定节点识别、基于 canonical 流程边的单步校验和持久化回读校验。新增的 3 个 Information 明确承载此前 Store 中隐藏的支付一致性条件。验证覆盖设计谓词/边变化、无匹配/多匹配/未决状态、重复/跳步拒绝、明细与支付不一致，以及最终写回校验失败时订单、明细和支付写入整体回滚。


事务框架新增显式外部托管/Runtime 自管模式和 `dec-lite-spring` 桥接。Demo 使用外部托管，最终写回与一致性校验位于事务完成前。框架通过 H2 实际数据库验证两种模式的写回失败、校验失败、提交失败、rollback-only、误接入拒绝和提交结果不确定；Demo 的 MySQL 场景验证最终写回/校验失败时订单与支付整体回滚。短信仍按现有 afterCommit 契约处理，未新增可靠投递机制。


Binding 验证增加 Java AST 符号检查、JUnit 实际执行证据和设计/Binding/源码/报告摘要关联。使用 `run_binding_verification.py` 包装本次 `clean test`，将证据写入 `order-payment-app/target/dec-binding-evidence.json`。`--require-complete --require-evidence` 证明当前 107 个 VERIFIED 的声明与测试证据匹配并覆盖全部 111 个 ID；4 个 PLANNED 仍未完成，`--require-ready` 必须拒绝发布就绪。证据位于构建目录，修改输入后须重新生成，不将其视为永久认证。
