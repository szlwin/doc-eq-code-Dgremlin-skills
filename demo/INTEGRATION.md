# dec-design → dec-implementation → dec-java-lite 验证

设计输入是 `order-payment-yaml/` 的 10 份 canonical YAML。当前设计有 107 个稳定 Design ID、5 个 API、16 个 Information。YAML 是唯一设计源；XML 和 HTML 均由正式转换脚本生成。生成器负责传输/模型骨架，`order-payment-app/src/main/java/com/example/orderpayment/demo/` 负责本地适配和运行时接线。

| 环节 | 当前状态 |
| --- | --- |
| 设计校验 | 10 文件、107 个 Design ID，跨文件引用通过 |
| YAML → XML/HTML | 10 份 XML、38 个 HTML 页面，可重新生成 |
| Java 生成 | 5 组 Controller/Service，DTO、View、Enum、DAO、Mapper 受 manifest 管理 |
| 应用执行 | 下单、支付发起、结果成功/失败、短信记录、回退、查询由 DEC Runtime 驱动 |
| 验证 | 直接 Runtime 场景、Spring HTTP、DAO/Entity 状态与 Enum JSON 测试；Binding 107/107 完整，103 VERIFIED、4 PLANNED |

应用按同一 canonical 设计加载 `dec/design/*.xml`，使用 `RuleViewCompiler`、`InformationEngine`、`DirectoryEngine` 与显式 `DataOperationAdapter`/`CustomActionRegistry`。生成的 Service 保留 `DESIGN_GAP` 缺省行为，手写 `@Primary` 绑定实现实际调用。失败时应用不发布快照或本地短信记录。

生成文件顶部的 Design ID 注释和 `order-payment-app/DEC-DESIGN-INDEX.md` 提供设计到生成代码的入口；索引中的 YAML 文件位于 `order-payment-yaml/`。运行时节点与手写代码的对应位置由 `order-payment-implementation-binding.yaml` 指定。业务状态由生成的 View/Entity 承载：`OrderViewAssembler` 经生成 DAO 读取，`LocalRuleAdapter` 经 DAO 写入，`DecViewModelBridge` 负责 DEC Runtime 的 Map 边界。

本地适配器模拟 MySQL、支付服务、MongoDB 与短信；它证明 Demo 的业务流程可执行，不构成外部系统接入或持久化能力验证。真实适配器、物理事务与跨系统补偿仍应在生产集成时按 Binding 单独验证。
